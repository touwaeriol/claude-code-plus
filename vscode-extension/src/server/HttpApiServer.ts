// Source: ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt
// Differences:
// - VS Code webview loads frontend assets directly (no static file server or HTML injection).
// - This server only exposes HTTP APIs + RSocket endpoints and relies on Webview CSP/localResourceRoots.

import * as crypto from 'crypto'
import * as fs from 'fs'
import * as http from 'http'
import * as path from 'path'
import * as vscode from 'vscode'

import { create, fromBinary, toBinary } from '@bufbuild/protobuf'

import { handleApiRequest, handleFileSearchRequest } from './apiHandlers'
import { HistoryStore } from '../ide/history/historyStore'
import { AgentRSocketServer } from './rsocket/agentRSocketServer'
import { IdeRSocketServer } from './rsocket/ideRSocketServer'
import { SnapshotStore } from '../ide/rollback/snapshotStore'
import { TerminalTaskManager } from '../ide/terminal/terminalTaskManager'
import { WsUpgradeRouter } from './wsUpgradeRouter'
import { isAllowedWebviewOrigin } from './webviewOrigin'
import type { DiffContentProvider } from '../ide/diffContentProvider'
import { McpHttpGateway, setMcpHttpGateway } from './mcp'
import { CodexBackendProvider } from './codex/codexBackendProvider'
import {
  GetHistoryMetadataRequestSchema,
  HistoryMetadataSchema,
  HistoryResultSchema,
  LoadHistoryRequestSchema,
} from '@proto'

export class HttpApiServer implements vscode.Disposable {
  private server: http.Server | undefined
  private baseUrl: string | undefined
  private readonly token: string = crypto.randomUUID()
  private rsocketServer: AgentRSocketServer | undefined
  private ideRSocketServer: IdeRSocketServer | undefined
  private wsUpgradeRouter: WsUpgradeRouter | undefined
  private readonly historyStore = new HistoryStore()
  private readonly snapshotStore = new SnapshotStore()
  private readonly terminalTaskManager = new TerminalTaskManager()
  private mcpHttpGateway: McpHttpGateway | undefined
  private codexBackendProvider: CodexBackendProvider | undefined

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly deps: { diffProvider?: DiffContentProvider } = {},
    private readonly logger?: { write(message: string): void }
  ) {}

  private log(message: string): void {
    try {
      this.logger?.write(message)
    } catch {
      // ignore
    }
  }

  async start(): Promise<void> {
    if (this.server) return

    this.server = http.createServer(async (req, res) => {
      // WebSocket upgrade requests are handled by ws.Server instances attached to this HTTP server.
      // If we write a normal HTTP response here, it corrupts the WebSocket stream and clients see 1006 / protocol errors.
      const upgradeHeader = req.headers.upgrade
      const upgrade = Array.isArray(upgradeHeader) ? upgradeHeader[0] : upgradeHeader
      if (typeof upgrade === 'string' && upgrade.toLowerCase() === 'websocket') {
        this.log(`[HttpApiServer] ignore websocket upgrade: ${String(req.method ?? '')} ${String(req.url ?? '')}`)
        return
      }

      try {
        await this.handleRequest(req, res)
      } catch (error) {
        res.statusCode = 500
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(
          JSON.stringify({
            success: false,
            error: error instanceof Error ? error.message : String(error),
          })
        )
      }
    })

    // Route websocket upgrades to the correct ws.Server instance (we have multiple endpoints).
    // Attaching multiple ws.Server({ server, path }) instances to the same http.Server breaks because
    // each server aborts non-matching paths with HTTP 400 on the same socket.
    this.wsUpgradeRouter = new WsUpgradeRouter(this.server, (msg) => this.log(msg))

    // When a socket is upgraded to WebSocket, Node's HTTP parser can still emit `clientError`
    // if it sees non-HTTP bytes (i.e., WebSocket frames). If we don't handle it, Node writes
    // a default "400 Bad Request" response *into the WebSocket stream*, which breaks RSocket.
    this.server.on('clientError', (err: any, socket: any) => {
      const isWebSocket = Boolean(socket && (socket as any).__ccp_isWebSocket)
      this.log(
        `[HttpApiServer] clientError isWebSocket=${String(isWebSocket)} code=${String(err?.code ?? '')} message=${String(
          err?.message ?? err
        )}`
      )

      // Let ws handle upgraded sockets; do not write HTTP responses to them.
      if (isWebSocket) return

      try {
        socket.end('HTTP/1.1 400 Bad Request\r\n\r\n')
      } catch {
        // ignore
      }
    })

    await new Promise<void>((resolve, reject) => {
      const server = this.server!
      server.once('error', reject)
      server.listen(0, '127.0.0.1', () => {
        const address = server.address()
        if (!address || typeof address === 'string') {
          reject(new Error('Failed to resolve server port'))
          return
        }
        this.baseUrl = `http://127.0.0.1:${address.port}`
        this.log(`[HttpApiServer] http listening ${this.baseUrl}`)
        resolve()
      })
    })

    // RSocket (/rsocket) server (WebSocket transport)
    this.rsocketServer = new AgentRSocketServer(
      this.context,
      this.token,
      this.historyStore,
      this.snapshotStore,
      this.terminalTaskManager,
      this.wsUpgradeRouter!,
      (msg: string) => this.log(msg)
    )
    await this.rsocketServer.start()

    // IDE RSocket (/ide-rsocket) server (IDE integrations + rollback)
    this.ideRSocketServer = new IdeRSocketServer(
      this.context,
      this.token,
      this.deps,
      this.snapshotStore,
      this.terminalTaskManager,
      this.wsUpgradeRouter!,
      (msg: string) => this.log(msg)
    )
    await this.ideRSocketServer.start()

    // MCP HTTP Gateway (for built-in MCP servers)
    this.mcpHttpGateway = new McpHttpGateway((msg: string) => this.log(msg))
    await this.initializeMcpGateway()
    setMcpHttpGateway(this.mcpHttpGateway)
  }

  getBaseUrl(): string {
    if (!this.baseUrl) throw new Error('Server not started')
    return this.baseUrl
  }

  getToken(): string {
    return this.token
  }

  /**
   * Get the MCP HTTP Gateway instance
   */
  getMcpHttpGateway(): McpHttpGateway | undefined {
    return this.mcpHttpGateway
  }

  /**
   * Initialize MCP HTTP Gateway with built-in MCP servers
   */
  private async initializeMcpGateway(): Promise<void> {
    if (!this.mcpHttpGateway) return

    try {
      // Import and register all built-in MCP servers
      const { mcpRegistry } = await import('../ide/mcp/mcpServerRegistry')
      
      for (const provider of mcpRegistry.getAllProviders()) {
        try {
          const server = provider.getServer()
          await this.mcpHttpGateway.registerServer(provider.name, server)
          this.log(`[HttpApiServer] Registered MCP server: ${provider.name}`)
        } catch (e) {
          this.log(`[HttpApiServer] Failed to register MCP server ${provider.name}: ${e}`)
        }
      }

      this.log(`[HttpApiServer] MCP Gateway initialized with ${this.mcpHttpGateway.getRegisteredServers().length} servers`)
    } catch (e) {
      this.log(`[HttpApiServer] Failed to initialize MCP Gateway: ${e}`)
    }
  }

  dispose() {
    try {
      // Shutdown MCP Gateway
      if (this.mcpHttpGateway) {
        this.mcpHttpGateway.shutdown().catch(e => {
          this.log(`[HttpApiServer] Error shutting down MCP Gateway: ${e}`)
        })
        setMcpHttpGateway(null)
      }
      this.codexBackendProvider?.stop()
      this.rsocketServer?.dispose()
      this.ideRSocketServer?.dispose()
      this.wsUpgradeRouter?.dispose()
      this.terminalTaskManager.dispose()
      this.server?.close()
    } finally {
      this.mcpHttpGateway = undefined
      this.rsocketServer = undefined
      this.ideRSocketServer = undefined
      this.wsUpgradeRouter = undefined
      this.codexBackendProvider = undefined
      this.server = undefined
      this.baseUrl = undefined
    }
  }

  private async ensureCodexBackendProvider(): Promise<CodexBackendProvider | null> {
    if (this.codexBackendProvider?.running) return this.codexBackendProvider

    if (!this.codexBackendProvider) {
      const cwd = getWorkspaceRootFsPath() || process.cwd()
      this.codexBackendProvider = new CodexBackendProvider(cwd, (msg) => this.log(msg))
    }

    try {
      await this.codexBackendProvider.start()
      return this.codexBackendProvider
    } catch (e) {
      this.log(`[HttpApiServer] Codex backend not available: ${e instanceof Error ? e.message : String(e)}`)
      // Allow retry on the next call.
      this.codexBackendProvider = undefined
      return null
    }
  }

  private async handleRequest(req: http.IncomingMessage, res: http.ServerResponse) {
    this.applyCors(req, res)

    if (req.method === 'OPTIONS') {
      res.statusCode = 204
      res.end()
      return
    }

    const base = this.baseUrl ?? 'http://127.0.0.1'
    const url = new URL(req.url ?? '/', base)

    if (!this.isAuthorized(req, url.pathname)) {
      res.statusCode = 401
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(JSON.stringify({ success: false, error: 'Unauthorized' }))
      return
    }

    if (req.method === 'GET' && url.pathname === '/health') {
      res.statusCode = 200
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(JSON.stringify({ success: true, data: { status: 'ok' } }))
      return
    }

    if (req.method === 'GET' && url.pathname === '/api/health') {
      // frontend/src/services/backend/BackendSessionFactory.ts expects { status: 'ok' }
      res.statusCode = 200
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(JSON.stringify({ status: 'ok' }))
      return
    }

    if (req.method === 'GET' && url.pathname === '/api/codex/health') {
      // JetBrains version: { status: 'ok' } / { status: 'unavailable' }
      const provider = await this.ensureCodexBackendProvider()
      res.statusCode = 200
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(JSON.stringify({ status: provider?.running ? 'ok' : 'unavailable' }))
      return
    }

    if (req.method === 'POST' && url.pathname === '/api/codex/thread/start') {
      try {
        const provider = await this.ensureCodexBackendProvider()
        if (!provider) {
          res.statusCode = 503
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ success: false, error: 'Codex backend not available' }))
          return
        }

        const bodyText = await readBodyText(req)
        const requestBody = bodyText ? (JSON.parse(bodyText) as any) : {}
        const threadId = await provider.createThread({
          model: requestBody.model ?? null,
          cwd: requestBody.cwd ?? null,
          approvalPolicy: requestBody.approvalPolicy ?? null,
          sandbox: requestBody.sandbox ?? null,
        })

        res.statusCode = 200
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: true, threadId }))
        return
      } catch (error) {
        res.statusCode = 500
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: error instanceof Error ? error.message : String(error) }))
        return
      }
    }

    if (req.method === 'POST' && url.pathname === '/api/codex/thread/resume') {
      try {
        const provider = await this.ensureCodexBackendProvider()
        if (!provider) {
          res.statusCode = 503
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ success: false, error: 'Codex backend not available' }))
          return
        }

        const bodyText = await readBodyText(req)
        const requestBody = bodyText ? (JSON.parse(bodyText) as any) : {}
        const threadId = requestBody.threadId as string | undefined
        if (!threadId) {
          res.statusCode = 400
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ success: false, error: 'Missing threadId' }))
          return
        }

        await provider.resumeThread(threadId)

        res.statusCode = 200
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: true }))
        return
      } catch (error) {
        res.statusCode = 500
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: error instanceof Error ? error.message : String(error) }))
        return
      }
    }

    if (req.method === 'POST' && url.pathname === '/api/codex/thread/archive') {
      try {
        const provider = await this.ensureCodexBackendProvider()
        if (!provider) {
          res.statusCode = 503
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ success: false, error: 'Codex backend not available' }))
          return
        }

        const bodyText = await readBodyText(req)
        const requestBody = bodyText ? (JSON.parse(bodyText) as any) : {}
        const threadId = requestBody.threadId as string | undefined
        if (!threadId) {
          res.statusCode = 400
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ success: false, error: 'Missing threadId' }))
          return
        }

        await provider.archiveThread(threadId)

        res.statusCode = 200
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: true }))
        return
      } catch (error) {
        res.statusCode = 500
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: error instanceof Error ? error.message : String(error) }))
        return
      }
    }

    if (req.method === 'POST' && url.pathname === '/api/codex/turn/start') {
      try {
        const provider = await this.ensureCodexBackendProvider()
        if (!provider) {
          res.statusCode = 503
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ success: false, error: 'Codex backend not available' }))
          return
        }

        const bodyText = await readBodyText(req)
        const requestBody = bodyText ? (JSON.parse(bodyText) as any) : {}
        const threadId = requestBody.threadId as string | undefined
        if (!threadId) {
          res.statusCode = 400
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ success: false, error: 'Missing threadId' }))
          return
        }
        const input = requestBody.input as string | undefined
        if (!input) {
          res.statusCode = 400
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ success: false, error: 'Missing input' }))
          return
        }

        const turnId = await provider.startTurn(threadId, input)

        res.statusCode = 200
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: true, turnId }))
        return
      } catch (error) {
        res.statusCode = 500
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: error instanceof Error ? error.message : String(error) }))
        return
      }
    }

    if (req.method === 'POST' && url.pathname === '/api/codex/turn/interrupt') {
      try {
        const provider = await this.ensureCodexBackendProvider()
        if (!provider) {
          res.statusCode = 503
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ success: false, error: 'Codex backend not available' }))
          return
        }

        const bodyText = await readBodyText(req)
        const requestBody = bodyText ? (JSON.parse(bodyText) as any) : {}
        const threadId = requestBody.threadId as string | undefined
        if (!threadId) {
          res.statusCode = 400
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ success: false, error: 'Missing threadId' }))
          return
        }

        await provider.interruptTurn(threadId)

        res.statusCode = 200
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: true }))
        return
      } catch (error) {
        res.statusCode = 500
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: error instanceof Error ? error.message : String(error) }))
        return
      }
    }

    if (req.method === 'GET' && url.pathname === '/api/codex/config') {
      const provider = await this.ensureCodexBackendProvider()
      if (!provider) {
        res.statusCode = 503
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: 'Codex backend not available' }))
        return
      }

      res.statusCode = 200
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(JSON.stringify({ success: true, available: true, version: '1.0.0' }))
      return
    }

    if (req.method === 'PUT' && url.pathname === '/api/codex/config') {
      const provider = await this.ensureCodexBackendProvider()
      if (!provider) {
        res.statusCode = 503
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: 'Codex backend not available' }))
        return
      }

      // JetBrains version: configuration is passed via startup args; runtime update is a no-op.
      res.statusCode = 200
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(JSON.stringify({ success: true, message: 'Config update not supported at runtime' }))
      return
    }

    const threadStateMatch = url.pathname.match(/^\/api\/codex\/thread\/([^/]+)\/state$/)
    if (req.method === 'GET' && threadStateMatch) {
      const provider = await this.ensureCodexBackendProvider()
      if (!provider) {
        res.statusCode = 503
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: 'Codex backend not available' }))
        return
      }

      let threadId = threadStateMatch[1] || ''
      try {
        threadId = decodeURIComponent(threadId)
      } catch {
        // keep raw
      }

      if (!threadId) {
        res.statusCode = 400
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: 'Missing threadId' }))
        return
      }

      const state = provider.getThreadState(threadId)
      if (!state) {
        res.statusCode = 404
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: 'Thread not found' }))
        return
      }

      res.statusCode = 200
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(
        JSON.stringify({
          success: true,
          state: {
            threadId: state.threadId,
            isActive: state.isActive,
            currentTurnId: state.currentTurnId,
            config: {
              model: state.config.model ?? null,
              cwd: state.config.cwd ?? null,
              approvalPolicy: state.config.approvalPolicy ?? null,
              sandbox: state.config.sandbox ?? null,
            },
          },
        })
      )
      return
    }

    if (req.method === 'GET' && url.pathname === '/api/history/sessions') {
      const offset = Number(url.searchParams.get('offset') ?? 0)
      const maxResults = Number(url.searchParams.get('maxResults') ?? 50)
      const providerParam = String(url.searchParams.get('provider') ?? '').toLowerCase().trim()
      const providerKey = providerParam === 'codex' ? 'codex' : 'claude'

      const sessions = this.historyStore.listSessions(
        Number.isFinite(offset) ? offset : 0,
        Number.isFinite(maxResults) ? maxResults : 50,
        providerKey
      )

      res.statusCode = 200
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(JSON.stringify({ sessions }))
      return
    }

    if (req.method === 'POST' && url.pathname === '/api/history/load.pb') {
      const raw = await readBodyBuffer(req)
      const parsed = raw.length > 0 ? fromBinary(LoadHistoryRequestSchema, raw) : create(LoadHistoryRequestSchema, {})

      const sessionId = parsed.sessionId || ''
      const offset = parsed.offset ?? 0
      const limit = parsed.limit ?? 200
      const providerParam = String(url.searchParams.get('provider') ?? '').toLowerCase().trim()
      const providerKey = providerParam === 'codex' ? 'codex' : 'claude'

      const loaded = sessionId
        ? this.historyStore.loadHistory(sessionId, offset, limit)
        : { messages: [], offset: offset ?? 0, count: 0, availableCount: 0 }
      const loadedFiltered = sessionId
        ? this.historyStore.loadHistory(sessionId, offset, limit, providerKey)
        : loaded

      const result = create(HistoryResultSchema, loadedFiltered)

      res.statusCode = 200
      res.setHeader('Content-Type', 'application/octet-stream')
      res.end(Buffer.from(toBinary(HistoryResultSchema, result)))
      return
    }

    if (req.method === 'POST' && url.pathname === '/api/history/metadata.pb') {
      const raw = await readBodyBuffer(req)
      const parsed =
        raw.length > 0 ? fromBinary(GetHistoryMetadataRequestSchema, raw) : create(GetHistoryMetadataRequestSchema, {})

      const projectPath = parsed.projectPath || getWorkspaceRootFsPath()
      const providerParam = String(url.searchParams.get('provider') ?? '').toLowerCase().trim()
      const providerKey = providerParam === 'codex' ? 'codex' : 'claude'

      const meta = this.historyStore.getMetadata(parsed.sessionId || '', projectPath, providerKey)
      const result = create(HistoryMetadataSchema, meta)

      res.statusCode = 200
      res.setHeader('Content-Type', 'application/octet-stream')
      res.end(Buffer.from(toBinary(HistoryMetadataSchema, result)))
      return
    }

    if (req.method === 'DELETE' && url.pathname.startsWith('/api/history/sessions/')) {
      const sessionId = decodeURIComponent(url.pathname.replace('/api/history/sessions/', ''))
      const providerParam = String(url.searchParams.get('provider') ?? '').toLowerCase().trim()
      const providerKey = providerParam === 'codex' ? 'codex' : 'claude'

      if (!sessionId) {
        res.statusCode = 400
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: 'Missing sessionId' }))
        return
      }

      // Align with JetBrains semantics: provider mismatch should behave like "not found".
      if (!this.historyStore.sessionHasProvider(sessionId, providerKey)) {
        res.statusCode = 404
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: 'Session not found or delete failed' }))
        return
      }

      const deleted = this.historyStore.deleteSession(sessionId)
      if (!deleted) {
        res.statusCode = 404
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ success: false, error: 'Session not found or delete failed' }))
        return
      }

      res.statusCode = 200
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(JSON.stringify({ success: true }))
      return
    }

    if (req.method === 'POST' && url.pathname === '/api/') {
      const bodyText = await readBodyText(req)
      const payload = bodyText ? (JSON.parse(bodyText) as unknown) : undefined
      const response = await handleApiRequest(payload, { context: this.context, deps: this.deps })
      res.statusCode = 200
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(JSON.stringify(response))
      return
    }

    if (req.method === 'GET' && url.pathname === '/api/files/search') {
      const response = await handleFileSearchRequest(url, { context: this.context })
      res.statusCode = 200
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.end(JSON.stringify(response))
      return
    }

    if (req.method === 'GET' && url.pathname.startsWith('/api/font/')) {
      // Frontend expects a raw font payload (or 404).
      // In JetBrains version the backend reads fonts from IDE JBR; in VS Code we best-effort load from system font dirs.
      const rawName = url.pathname.slice('/api/font/'.length)
      let fontFamily = rawName
      try {
        fontFamily = decodeURIComponent(rawName)
      } catch {
        // keep rawName
      }

      const found = loadSystemFontBytes(fontFamily)
      if (!found) {
        res.statusCode = 404
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ error: `Font not found: ${fontFamily}` }))
        return
      }

      res.statusCode = 200
      res.setHeader('Content-Type', found.contentType)
      res.setHeader('Content-Disposition', `attachment; filename=\"${found.fileName}\"`)
      res.end(found.bytes)
      return
    }

    res.statusCode = 404
    res.setHeader('Content-Type', 'text/plain; charset=utf-8')
    res.end('Not Found')
  }

  private applyCors(req: http.IncomingMessage, res: http.ServerResponse) {
    const origin = req.headers.origin
    if (isAllowedWebviewOrigin(origin)) {
      res.setHeader('Access-Control-Allow-Origin', origin)
      res.setHeader('Vary', 'Origin')
    }
    res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS')
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type,X-Claude-Code-Plus-Token')

    // Private Network Access preflight (Chrome)
    if (req.headers['access-control-request-private-network'] === 'true') {
      res.setHeader('Access-Control-Allow-Private-Network', 'true')
    }
  }

  private isAuthorized(req: http.IncomingMessage, pathname: string): boolean {
    // Basic sanity: only accept from loopback (should be guaranteed by listen(127.0.0.1))
    const remote = req.socket.remoteAddress
    const isLoopback =
      remote === '127.0.0.1' || remote === '::1' || (typeof remote === 'string' && remote.endsWith('127.0.0.1'))
    if (!isLoopback) return false

    // Token check: required for all endpoints
    const headerValue = req.headers['x-claude-code-plus-token']
    const token = Array.isArray(headerValue) ? headerValue[0] : headerValue
    if (typeof token !== 'string' || !token) return false
    return token === this.token
  }
}

function readBodyText(req: http.IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    let data = ''
    req.setEncoding('utf8')
    req.on('data', (chunk) => {
      data += chunk
    })
    req.on('end', () => resolve(data))
    req.on('error', reject)
  })
}

function readBodyBuffer(req: http.IncomingMessage): Promise<Uint8Array> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = []
    req.on('data', (chunk) => {
      chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk))
    })
    req.on('end', () => resolve(Buffer.concat(chunks)))
    req.on('error', reject)
  })
}

function getWorkspaceRootFsPath(): string {
  const folder = vscode.workspace.workspaceFolders?.[0]
  return folder?.uri.fsPath ?? ''
}

function normalizeFontKey(name: string): string {
  return name.toLowerCase().replace(/\s+/g, '')
}

function getSystemFontDirs(): string[] {
  const home = process.env.HOME || process.env.USERPROFILE || ''
  if (process.platform === 'win32') {
    return ['C:\\Windows\\Fonts']
  }
  if (process.platform === 'darwin') {
    return [path.join(home, 'Library', 'Fonts'), '/Library/Fonts', '/System/Library/Fonts']
  }
  return [
    path.join(home, '.fonts'),
    path.join(home, '.local', 'share', 'fonts'),
    '/usr/share/fonts',
    '/usr/local/share/fonts',
  ]
}

function guessFontCandidates(fontFamily: string): string[] {
  const key = normalizeFontKey(fontFamily)
  const mapping: Record<string, string[]> = {
    // Keep roughly aligned with JetBrains FontHelper mappings.
    jetbrainsmono: ['JetBrainsMono-Regular', 'JetBrainsMonoNL-Regular', 'JetBrainsMono[wght]'],
    firacode: ['FiraCode-Regular', 'FiraCode[wdth,wght]'],
    droidsans: ['DroidSans'],
    droidsansmono: ['DroidSansMono'],
    droidserif: ['DroidSerif-Regular'],
    inconsolata: ['Inconsolata'],
    inter: ['Inter-Regular', 'Inter[slnt,wght]'],
  }

  return mapping[key] ?? []
}

function detectFontContentType(ext: string): string {
  switch (ext.toLowerCase()) {
    case '.ttf':
      return 'font/ttf'
    case '.otf':
      return 'font/otf'
    case '.woff':
      return 'font/woff'
    case '.woff2':
      return 'font/woff2'
    default:
      return 'application/octet-stream'
  }
}

function loadSystemFontBytes(
  fontFamily: string
): { bytes: Buffer; contentType: string; fileName: string } | null {
  const candidates = guessFontCandidates(fontFamily)
  if (candidates.length === 0) return null

  const exts = ['.ttf', '.otf', '.woff', '.woff2']
  const dirs = getSystemFontDirs()

  for (const dir of dirs) {
    if (!dir) continue
    try {
      if (!fs.existsSync(dir)) continue
    } catch {
      continue
    }

    for (const base of candidates) {
      for (const ext of exts) {
        const filePath = path.join(dir, `${base}${ext}`)
        try {
          if (!fs.existsSync(filePath)) continue
          const bytes = fs.readFileSync(filePath)
          return { bytes, contentType: detectFontContentType(ext), fileName: `${base}${ext}` }
        } catch {
          // ignore and continue searching
        }
      }
    }
  }

  return null
}
