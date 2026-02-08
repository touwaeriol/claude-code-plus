import * as crypto from 'crypto'
import { spawn } from 'child_process'
import * as path from 'path'
import * as vscode from 'vscode'

import type { HistoryStore } from '../history/historyStore'
import type { SnapshotStore } from '../rollback/snapshotStore'
import type { TerminalTaskManager } from '../terminal/terminalTaskManager'
import type { WsUpgradeRouter } from '../wsUpgradeRouter'
import { isAllowedWebviewOrigin } from '../webviewOrigin'
import { ClientCaller, ClientCallerRegistry } from '../rpc'

import { create, fromBinary, toBinary } from '@bufbuild/protobuf'
import {
  AssistantMessageSchema,
  CapabilitiesSchema,
  ConnectOptionsSchema,
  ConnectResultSchema,
  BashBackgroundResultSchema,
  BashRunToBackgroundRequestSchema,
  ContentBlockDeltaEventSchema,
  ContentBlockStartEventSchema,
  ContentBlockStopEventSchema,
  ContentBlockSchema,
  ContentStatus,
  DeltaSchema,
  GetMcpToolsRequestSchema,
  GetMcpToolsResultSchema,
  HasIdeEnvironmentResponseSchema,
  HistorySchema,
  AskUserQuestionRequestSchema,
  AskUserQuestionResponseSchema,
  RequestPermissionResponseSchema,
  McpStatusResultSchema,
  MessageStartEventSchema,
  MessageStopEventSchema,
  MessageContentSchema,
  MessageStartInfoSchema,
  PermissionMode,
  Provider,
  QueryRequestSchema,
  QueryWithContentRequestSchema,
  ReconnectMcpRequestSchema,
  ReconnectMcpResultSchema,
  ResultMessageSchema,
  RequestPermissionRequestSchema,
  RpcMessageSchema,
  RunToBackgroundRequestSchema,
  ServerCallRequestSchema,
  ServerCallResponseSchema,
  TextBlockSchema,
  ThinkingBlockSchema,
  ThinkingDeltaSchema,
  SessionStatus,
  SetMaxThinkingTokensRequestSchema,
  SetMaxThinkingTokensResultSchema,
  SetModelRequestSchema,
  SetModelResultSchema,
  SetPermissionModeRequestSchema,
  SetPermissionModeResultSchema,
  SetSandboxModeRequestSchema,
  SetSandboxModeResultSchema,
  StatusResultSchema,
  StreamEventDataSchema,
  StreamEventSchema,
  TextDeltaSchema,
  ToolResultBlockSchema,
  ToolUseBlockSchema,
  InputJsonDeltaSchema,
  TruncateHistoryRequestSchema,
  TruncateHistoryResultSchema,
  UnifiedBackgroundResultSchema,
  UserMessageSchema,
  type RpcMessage,
} from '@proto'

import type {
  OnExtensionSubscriber,
  OnNextSubscriber,
  OnTerminalSubscriber,
  Payload,
  RSocket,
  SetupPayload,
} from 'rsocket-core'
import { RSocketServer } from 'rsocket-core'
import { WebsocketServerTransport } from 'rsocket-websocket-server'

import { ClaudeCliSessionManager, type ToolPermissionResult } from '../../sdk/claude/claudeCli'
import { buildMcpConfig, writeSystemPromptAppendix, cleanupTempFiles, type McpServerSettings } from '../../sdk/claude/mcpConfigBuilder'
import { getMcpHttpGateway } from '../mcp'
import { McpConfigurable, type McpServerEntry, type CustomMcpServerConfig } from '../../ide/settings/configurables/McpConfigurable'
import { toMcpServerName, CONTEXT7_CONFIG } from '../../ide/mcp/constants'
import { getDefaultInstructions } from '../../ide/mcp/defaults/mcpInstructions'
import { CodexSession, type CodexSessionOptions } from '../../sdk/codex/session'
import { CodexAppServerStreamAdapter } from '../../sdk/codex/adapter/streamAdapter'
import type { AppServerEvent } from '../../sdk/codex/appServer/client'

type WsServerCtor = new (...args: any[]) => any

function resolveWsServerCtor(): WsServerCtor {
  // rsocket-websocket-server bundles its own `ws`. If we instantiate the server with a
  // different `ws` copy/version, createWebSocketStream() can break and close the socket (1006).
  try {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    return require('rsocket-websocket-server/node_modules/ws').Server
  } catch {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    return require('ws').Server
  }
}

const WsServer: WsServerCtor = resolveWsServerCtor()

type Responder = Partial<RSocket>

export class AgentRSocketServer implements vscode.Disposable {
  private closeable: { close(error?: Error): void } | undefined
  private readonly claudeCli: ClaudeCliSessionManager

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly token: string,
    private readonly historyStore: HistoryStore,
    private readonly snapshotStore: SnapshotStore,
    private readonly terminalTaskManager: TerminalTaskManager,
    private readonly wsUpgradeRouter: WsUpgradeRouter,
    private readonly log?: (message: string) => void
  ) {
    this.claudeCli = new ClaudeCliSessionManager((msg) => this.log?.(msg))
  }

  async start(): Promise<void> {
    if (this.closeable) return

    const transport = new WebsocketServerTransport({
      wsCreator: () =>
        (() => {
          const wss = new WsServer({
            noServer: true,
            path: '/rsocket',
            verifyClient: (info: any, done: any) => {
              try {
                const req = info?.req
                const remote = req?.socket?.remoteAddress ?? (req as any)?.connection?.remoteAddress
                  const isLoopback =
                    !remote ||
                    remote === '127.0.0.1' ||
                    remote === '::1' ||
                    (typeof remote === 'string' && remote.endsWith('127.0.0.1'))
                  if (!isLoopback) {
                    this.log?.(`[rsocket/ws] reject: non-loopback remote=${String(remote)}`)
                    return done(false, 401, 'Unauthorized')
                  }

                const origin = req?.headers?.origin
                if (typeof origin === 'string' && !isAllowedWebviewOrigin(origin)) {
                  this.log?.(`[rsocket/ws] reject: origin=${origin}`)
                  return done(false, 401, 'Unauthorized')
                }

                const url = new URL(String(req?.url ?? ''), 'http://127.0.0.1')
                const token = url.searchParams.get('token')
                if (!token || token !== this.token) {
                  this.log?.(`[rsocket/ws] reject: bad token url=${String(req?.url ?? '')} token=${String(token)}`)
                  return done(false, 401, 'Unauthorized')
                }

                this.log?.(`[rsocket/ws] accept url=${String(req?.url ?? '')} origin=${String(origin ?? '')} remote=${String(remote)}`)
                return done(true)
              } catch (_error) {
                this.log?.('[rsocket/ws] reject: exception in verifyClient')
                return done(false, 401, 'Unauthorized')
              }
            },
          } as any)

          this.wsUpgradeRouter.register('/rsocket', wss as any)

          wss.on('connection', (socket: any, req: any) => {
            try {
              if (socket?._socket) (socket._socket as any).__ccp_isWebSocket = true
            } catch {
              // ignore
            }
            const remote = req?.socket?.remoteAddress
            const origin = req?.headers?.origin
            this.log?.(
              `[rsocket/ws] connection url=${String(req?.url ?? '')} origin=${String(origin ?? '')} remote=${String(remote)}`
            )
            socket.on('message', (data: any, isBinary: boolean) => {
              const bytes = Buffer.isBuffer(data) ? data.length : typeof data?.byteLength === 'number' ? data.byteLength : 0
              const head = Buffer.isBuffer(data) ? data.subarray(0, 12).toString('hex') : ''
              this.log?.(`[rsocket/ws] message isBinary=${String(isBinary)} bytes=${String(bytes)} head=${head}`)
            })
            socket.on('close', (code: number, reason: Buffer) => {
              const text = reason ? reason.toString('utf8') : ''
              this.log?.(`[rsocket/ws] close code=${code} reason=${text}`)
            })
            socket.on('error', (err: any) => {
              this.log?.(`[rsocket/ws] socket error: ${err instanceof Error ? err.message : String(err)}`)
            })
          })
          wss.on('error', (err: any) => {
            this.log?.(`[rsocket/ws] wss error: ${err instanceof Error ? err.stack || err.message : String(err)}`)
          })
          wss.on('close', () => {
            this.log?.('[rsocket/ws] wss close')
          })

          // rsocket-websocket-server waits for the ws server "listening" event.
          // When ws.Server is attached to an existing http.Server, it doesn't emit it,
          // so we emit it manually once the event listeners are registered.
          setImmediate(() => (wss as any).emit('listening'))
          return wss
        })(),
    })

    const server = new RSocketServer({
      transport,
      acceptor: {
        accept: async (setupPayload: SetupPayload, remotePeer: RSocket): Promise<Responder> => {
          this.log?.(
            `[rsocket] accept setup dataMimeType=${setupPayload.dataMimeType} metadataMimeType=${setupPayload.metadataMimeType}`
          )
          try {
            ;(remotePeer as any).onClose?.((err?: Error) => {
              this.log?.(`[rsocket] peer closed ${err ? `error=${err.message}` : '(normal)'}`)
              // 注意：connectId 在此时可能还未设置，清理由 responder 内部处理
            })
          } catch {
            // ignore
          }
          return createResponder(
            this.context,
            setupPayload,
            remotePeer,
            this.historyStore,
            this.snapshotStore,
            this.terminalTaskManager,
            this.claudeCli,
            (msg) => this.log?.(msg),
            // 传入 onDispose 回调，在连接关闭时清理
            (cid) => {
              if (cid) {
                ClientCallerRegistry.unregister(cid)
                this.log?.(`[rsocket] ClientCaller unregistered on peer close: connectId=${cid}`)
              }
            }
          )
        },
      },
    })

    this.closeable = await server.bind()
  }

  dispose() {
    this.closeable?.close()
    this.closeable = undefined
    this.claudeCli.dispose()
  }
}

function createResponder(
  context: vscode.ExtensionContext,
  _setup: SetupPayload,
  remotePeer: RSocket,
  historyStore: HistoryStore,
  snapshotStore: SnapshotStore,
  terminalTaskManager: TerminalTaskManager,
  claudeCli: ClaudeCliSessionManager,
  log?: (message: string) => void,
  onDispose?: (connectId: string | undefined) => void
): Responder {
  let connectId: string | undefined
  let sessionId: string | undefined
  let currentStreamCancel: (() => void) | undefined

  // Per-RSocket-connection global event subscribers. This mirrors the JetBrains backend where
  // subscribeGlobalEvents() is scoped to the RSocket connection / service instance.
  const globalEventSubscribers = new Set<OnTerminalSubscriber<Payload, Error>>()

  const broadcastGlobalPayload = (p: Payload) => {
    if (globalEventSubscribers.size === 0) return

    // Avoid sharing the same Buffer instance across multiple streams.
    const data = p.data ? Buffer.from(p.data as any) : undefined
    const metadata = p.metadata ? Buffer.from(p.metadata as any) : undefined
    const cloned: Payload = { data, metadata } as any

    for (const sub of Array.from(globalEventSubscribers)) {
      try {
        sub.onNext(cloned, false)
      } catch {
        globalEventSubscribers.delete(sub)
      }
    }
  }

  // 设置连接关闭时的清理回调
  try {
    ;(remotePeer as any).onClose?.((err?: Error) => {
      log?.(`[rsocket] peer closed in responder ${err ? `error=${err.message}` : '(normal)'}`)
      globalEventSubscribers.clear()
      onDispose?.(connectId)
    })
  } catch {
    // ignore
  }

  const getWorkspaceRoot = (): string => {
    const folder = vscode.workspace.workspaceFolders?.[0]
    return folder?.uri.fsPath ?? ''
  }

  const getAdditionalDirs = (): string[] => {
    const folders = vscode.workspace.workspaceFolders ?? []
    if (folders.length <= 1) return []
    return folders.slice(1).map(f => f.uri.fsPath)
  }

  /**
   * 从 VS Code 设置中读取 MCP 服务器配置
   * 
   * 1. 读取所有内置 MCP 服务器配置（User Interaction, VS Code LSP, VS Code File, Context7, Terminal, Git）
   * 2. 读取自定义 MCP 服务器配置
   * 3. 转换为 McpServerSettings 格式供 buildMcpConfig() 使用
   * 
   * 修复说明：之前的实现试图从不存在的 claudeCodePlus.mcp.servers 读取配置，
   * 现在正确地从 McpConfigurable 读取配置。
   */
  const getMcpServersFromSettings = (): McpServerSettings[] => {
    const settings: McpServerSettings[] = []
    
    try {
      // 1. 读取内置服务器配置
      const builtInServers = McpConfigurable.getAllBuiltInServers()
      for (const entry of builtInServers) {
        settings.push(convertBuiltInToSettings(entry))
      }
      
      // 2. 读取自定义服务器配置
      const customServers = McpConfigurable.getCustomServers()
      for (const custom of customServers) {
        settings.push(convertCustomToSettings(custom))
      }
      
      log?.(`[MCP] Loaded ${settings.length} MCP servers (${builtInServers.length} built-in, ${customServers.length} custom)`)
    } catch (e) {
      log?.(`[MCP] Failed to load MCP servers from settings: ${e}`)
    }
    
    return settings
  }
  
  /**
   * 将内置 MCP 服务器条目转换为 McpServerSettings
   */
  function convertBuiltInToSettings(entry: McpServerEntry): McpServerSettings {
    // 将 UI 名称转换为 MCP 服务器名称
    const mcpName = toMcpServerName(entry.name)
    
    // 格式化 backends：["all"] -> "All", ["claude", "codex"] -> "Claude,Codex"
    let backends = formatBackendsArray(entry.enabledBackends)

    // User Interaction MCP: Claude 后端使用 canUseTool + updatedInput.answers，
    // 不再通过 MCP 中转，因此强制限定为 Codex only
    if (mcpName === 'user-interaction') {
      backends = 'Codex'
    }

    // 获取默认提示词
    const defaultInstructions = getDefaultInstructions(mcpName)
    
    // 特殊处理 Context7：它是内置配置但使用外部 HTTP URL
    if (entry.name === 'Context7') {
      return {
        name: mcpName,
        enabled: entry.enabled,
        backends,
        level: entry.level,
        isBuiltIn: false,  // Context7 作为外部 HTTP 服务器处理
        type: 'http',
        url: CONTEXT7_CONFIG.URL,
        headers: entry.apiKey ? { [CONTEXT7_CONFIG.API_KEY_HEADER]: entry.apiKey } : undefined,
        instructions: entry.instructions || undefined,
        instructionsClaude: entry.instructionsClaude || undefined,
        instructionsCodex: entry.instructionsCodex || undefined,
        defaultInstructions,
      }
    }
    
    // 其他内置服务器通过 MCP HTTP Gateway 暴露
    return {
      name: mcpName,
      enabled: entry.enabled,
      backends,
      level: entry.level,
      isBuiltIn: true,
      type: 'http',  // 内置服务器通过 MCP HTTP Gateway 暴露
      instructions: entry.instructions || undefined,
      instructionsClaude: entry.instructionsClaude || undefined,
      instructionsCodex: entry.instructionsCodex || undefined,
      defaultInstructions,
    }
  }
  
  /**
   * 将自定义 MCP 服务器配置转换为 McpServerSettings
   */
  function convertCustomToSettings(custom: CustomMcpServerConfig): McpServerSettings {
    const backends = formatBackendsArray(custom.backends)
    
    return {
      name: custom.name,
      enabled: custom.enabled,
      backends,
      level: 'project',
      isBuiltIn: false,
      type: custom.config.type || 'stdio',
      url: custom.config.url,
      headers: custom.config.headers,
      command: custom.config.command,
      args: custom.config.args,
      env: custom.config.env,
      instructions: custom.instructions || undefined,
    }
  }
  
  /**
   * 格式化后端配置数组为字符串
   * ["all"] -> "All"
   * ["claude"] -> "Claude"
   * ["codex"] -> "Codex"
   * ["claude", "codex"] -> "Claude,Codex"
   */
  function formatBackendsArray(backends: string[]): string {
    if (!backends || backends.length === 0) return 'All'
    
    const normalized = backends.map(b => b.toLowerCase())
    if (normalized.includes('all')) return 'All'
    
    const parts: string[] = []
    if (normalized.includes('claude')) parts.push('Claude')
    if (normalized.includes('codex')) parts.push('Codex')
    
    return parts.length > 0 ? parts.join(',') : 'All'
  }

  let provider: Provider = Provider.CLAUDE
  let model: string = 'claude-opus-4-5-20251101'
  let permissionMode: PermissionMode = PermissionMode.DEFAULT
  let includePartialMessages = true
  let dangerouslySkipPermissions = false

  /**
   * 创建 ClientCaller（用于服务器向客户端发起请求）
   * 
   * 使用 Protobuf 序列化，通过 client.call 路由发送类型化请求。
   * 与 JetBrains 版本的 RSocketHandler.createClientCaller 对应。
   */
  const createClientCaller = (): ClientCaller => {
    let callIdCounter = 0

    return {
      async callAskUserQuestion(request) {
        const callId = `srv-${++callIdCounter}`
        log?.(`📤 [RSocket] [${connectId}] → AskUserQuestion: callId=${callId}, questions=${request.questions.length}`)

        try {
          // 构建 ServerCallRequest
          const serverRequest = create(ServerCallRequestSchema, {
            callId,
            method: 'AskUserQuestion',
            params: { case: 'askUserQuestion', value: request },
          })

          const { promise } = requestClientCall(remotePeer, serverRequest)
          const responseBytes = await promise

          // 解析 ServerCallResponse
          const serverResponse = fromBinary(ServerCallResponseSchema, responseBytes)

          if (!serverResponse.success) {
            const errorMsg = serverResponse.error || 'Unknown error'
            log?.(`📥 [RSocket] ← AskUserQuestion 失败: callId=${callId}, error=${errorMsg}`)
            throw new Error(`AskUserQuestion failed: ${errorMsg}`)
          }

          if (serverResponse.result.case !== 'askUserQuestion') {
            throw new Error('AskUserQuestion response missing askUserQuestion field')
          }

          const response = serverResponse.result.value
          log?.(`📥 [RSocket] [${connectId}] ← AskUserQuestion 成功: callId=${callId}, answers=${response.answers.length}`)
          return response

        } catch (e) {
          const errorMsg = e instanceof Error ? e.message : String(e)
          log?.(`📥 [RSocket] [${connectId}] ← AskUserQuestion 失败: callId=${callId}, error=${errorMsg}`)
          throw e
        }
      },

      async callRequestPermission(request) {
        const callId = `srv-${++callIdCounter}`
        log?.(`📤 [RSocket] [${connectId}] → RequestPermission: callId=${callId}, toolName=${request.toolName}`)

        try {
          // 构建 ServerCallRequest
          const serverRequest = create(ServerCallRequestSchema, {
            callId,
            method: 'RequestPermission',
            params: { case: 'requestPermission', value: request },
          })

          const { promise } = requestClientCall(remotePeer, serverRequest)
          const responseBytes = await promise

          // 解析 ServerCallResponse
          const serverResponse = fromBinary(ServerCallResponseSchema, responseBytes)

          if (!serverResponse.success) {
            const errorMsg = serverResponse.error || 'Unknown error'
            log?.(`📥 [RSocket] ← RequestPermission 失败: callId=${callId}, error=${errorMsg}`)
            throw new Error(`RequestPermission failed: ${errorMsg}`)
          }

          if (serverResponse.result.case !== 'requestPermission') {
            throw new Error('RequestPermission response missing requestPermission field')
          }

          const response = serverResponse.result.value
          log?.(`📥 [RSocket] [${connectId}] ← RequestPermission 成功: callId=${callId}, approved=${response.approved}`)
          return response

        } catch (e) {
          const errorMsg = e instanceof Error ? e.message : String(e)
          log?.(`📥 [RSocket] [${connectId}] ← RequestPermission 失败: callId=${callId}, error=${errorMsg}`)
          throw e
        }
      }
    }
  }

  // 创建 ClientCaller 实例
  const clientCaller = createClientCaller()

  return {
    requestResponse: (payload, responderStream) => {
      const route = extractRoute(payload)
      log?.(`[rsocket] requestResponse route=${route || '(empty)'} dataBytes=${payload.data ? payload.data.byteLength : 0}`)
      const data = payload.data ? new Uint8Array(payload.data) : new Uint8Array()

      try {
        switch (route) {
          case 'agent.connect': {
            const options = data.length > 0 ? fromBinary(ConnectOptionsSchema, data) : undefined

            // Align with JetBrains semantics: connectId is backend-assigned and collision-checked.
            // Older clients may still send connectId; ignore it to avoid hijacking MCP routing.
            if (options?.connectId) {
              log?.(`[rsocket] agent.connect: ignoring options.connectId (backend-assigned), value=${options.connectId}`)
            }

            if (!connectId) {
              let allocated = crypto.randomUUID()
              while (ClientCallerRegistry.contains(allocated)) {
                allocated = crypto.randomUUID()
              }
              connectId = allocated
            }

            sessionId = options?.sessionId || crypto.randomUUID()
            log?.(`[rsocket] agent.connect: connectId=${connectId}, sessionId=${sessionId}`)
            historyStore.ensureSession(sessionId, getWorkspaceRoot())

            // 注册 ClientCaller 到 Registry
            ClientCallerRegistry.register(connectId, clientCaller)

            provider = options?.provider ?? provider
            model = options?.model || model
            permissionMode = options?.permissionMode ?? permissionMode
            includePartialMessages = options?.includePartialMessages ?? includePartialMessages
            dangerouslySkipPermissions = options?.dangerouslySkipPermissions ?? dangerouslySkipPermissions

            const capabilities = create(CapabilitiesSchema, {
              canInterrupt: true,
              canSwitchModel: true,
              canSwitchPermissionMode: true,
              supportedPermissionModes: [
                PermissionMode.DEFAULT,
                PermissionMode.BYPASS_PERMISSIONS,
                PermissionMode.ACCEPT_EDITS,
                PermissionMode.PLAN,
              ],
              canSkipPermissions: true,
              canSendRichContent: true,
              canThink: true,
              canResumeSession: false,
              canRunInBackground: true,
            })

            const result = create(ConnectResultSchema, {
              sessionId,
              provider,
              status: SessionStatus.CONNECTED,
              model,
              capabilities,
              cwd: getWorkspaceRoot(),
              connectId,
            })

            const bytes = toBinary(ConnectResultSchema, result)
            // For requestResponse: `isComplete=true` already terminates the stream.
            responderStream.onNext({ data: Buffer.from(bytes) }, true)
            break
          }
          case 'agent.disconnect':
          case 'agent.disposeSession': {
            // 从 Registry 注销 ClientCaller
            if (connectId) {
              ClientCallerRegistry.unregister(connectId)
            }
            responderStream.onNext({ data: Buffer.from(encodeStatus(SessionStatus.DISCONNECTED)) }, true)
            break
          }
          case 'agent.interrupt': {
            currentStreamCancel?.()
            currentStreamCancel = undefined
            responderStream.onNext({ data: Buffer.from(encodeStatus(SessionStatus.INTERRUPTED)) }, true)
            break
          }
          case 'agent.setModel': {
            const req = data.length > 0 ? fromBinary(SetModelRequestSchema, data) : undefined
            model = req?.model || model
            const result = create(SetModelResultSchema, {
              status: SessionStatus.MODEL_CHANGED,
              model,
            })
            responderStream.onNext({ data: Buffer.from(toBinary(SetModelResultSchema, result)) }, true)
            break
          }
          case 'agent.setPermissionMode': {
            const req = data.length > 0 ? fromBinary(SetPermissionModeRequestSchema, data) : undefined
            permissionMode = req?.mode ?? permissionMode
            const result = create(SetPermissionModeResultSchema, {
              mode: permissionMode,
              success: true,
            })
            responderStream.onNext({ data: Buffer.from(toBinary(SetPermissionModeResultSchema, result)) }, true)
            break
          }
          case 'agent.setSandboxMode': {
            const req = data.length > 0 ? fromBinary(SetSandboxModeRequestSchema, data) : undefined
            const result = create(SetSandboxModeResultSchema, {
              mode: req?.mode ?? 0,
              success: true,
            })
            responderStream.onNext({ data: Buffer.from(toBinary(SetSandboxModeResultSchema, result)) }, true)
            break
          }
          case 'agent.setMaxThinkingTokens': {
            const req = data.length > 0 ? fromBinary(SetMaxThinkingTokensRequestSchema, data) : undefined
            const result = create(SetMaxThinkingTokensResultSchema, {
              status: SessionStatus.CONNECTED,
              maxThinkingTokens: req?.maxThinkingTokens,
            })
            responderStream.onNext({ data: Buffer.from(toBinary(SetMaxThinkingTokensResultSchema, result)) }, true)
            break
          }
          case 'agent.runInBackground': {
            const sid = sessionId || 'default'
            log?.(`[rsocket] runInBackground: sessionId=${sid}`)
            
            // Only Claude provider supports runInBackground
            if (provider !== Provider.CLAUDE) {
              responderStream.onNext({ data: Buffer.from(encodeStatus(SessionStatus.CONNECTED)) }, true)
              break
            }
            
            // Call the Claude CLI session manager to run all tasks in background (batch mode)
            claudeCli.runToBackground(sid)
              .then((bgResult) => {
                log?.(`[rsocket] runInBackground result: success=${bgResult.success}, error=${bgResult.error || 'none'}`)
                responderStream.onNext({ data: Buffer.from(encodeStatus(SessionStatus.CONNECTED)) }, true)
              })
              .catch((err) => {
                log?.(`[rsocket] runInBackground error: ${err instanceof Error ? err.message : String(err)}`)
                // Still return CONNECTED status even on error, as per JetBrains implementation
                responderStream.onNext({ data: Buffer.from(encodeStatus(SessionStatus.CONNECTED)) }, true)
              })
            break
          }
          case 'agent.bashRunToBackground': {
            const req = data.length > 0 ? fromBinary(BashRunToBackgroundRequestSchema, data) : undefined
            const taskId = req?.taskId || ''
            const sid = sessionId || 'default'
            
            log?.(`[rsocket] bashRunToBackground: sessionId=${sid}, taskId=${taskId}`)
            
            if (!taskId) {
              const result = create(BashBackgroundResultSchema, {
                success: false,
                taskId: '',
                error: 'taskId is required for bashRunToBackground',
              })
              responderStream.onNext({ data: Buffer.from(toBinary(BashBackgroundResultSchema, result)) }, true)
              break
            }
            
            // Only Claude provider supports bashRunToBackground
            if (provider !== Provider.CLAUDE) {
              const result = create(BashBackgroundResultSchema, {
                success: false,
                taskId,
                error: `bashRunToBackground not supported for provider: ${String(provider)}`,
              })
              responderStream.onNext({ data: Buffer.from(toBinary(BashBackgroundResultSchema, result)) }, true)
              break
            }
            
            // Call the Claude CLI session manager to run the bash task in background
            claudeCli.runToBackground(sid, taskId)
              .then((bgResult) => {
                log?.(`[rsocket] bashRunToBackground result: success=${bgResult.success}, error=${bgResult.error || 'none'}`)
                const result = create(BashBackgroundResultSchema, {
                  success: bgResult.success,
                  taskId: bgResult.taskId,
                  command: bgResult.command,
                  error: bgResult.error,
                })
                responderStream.onNext({ data: Buffer.from(toBinary(BashBackgroundResultSchema, result)) }, true)
              })
              .catch((err) => {
                log?.(`[rsocket] bashRunToBackground error: ${err instanceof Error ? err.message : String(err)}`)
                const result = create(BashBackgroundResultSchema, {
                  success: false,
                  taskId,
                  error: err instanceof Error ? err.message : String(err),
                })
                responderStream.onNext({ data: Buffer.from(toBinary(BashBackgroundResultSchema, result)) }, true)
              })
            break
          }
          case 'agent.runToBackground': {
            const req = data.length > 0 ? fromBinary(RunToBackgroundRequestSchema, data) : undefined
            const taskId = req?.taskId || undefined
            const sid = sessionId || 'default'
            
            log?.(`[rsocket] runToBackground: sessionId=${sid}, taskId=${taskId || 'batch'}`)
            
            // Only Claude provider supports runToBackground
            if (provider !== Provider.CLAUDE) {
              const result = create(UnifiedBackgroundResultSchema, {
                success: false,
                isBash: undefined,
                taskId,
                bashCount: 0,
                agentCount: 0,
                backgroundedBashIds: [],
                backgroundedAgentIds: [],
                error: `runToBackground not supported for provider: ${String(provider)}`,
              })
              responderStream.onNext({ data: Buffer.from(toBinary(UnifiedBackgroundResultSchema, result)) }, true)
              break
            }
            
            // Call the Claude CLI session manager to run the task in background
            claudeCli.runToBackground(sid, taskId)
              .then((bgResult) => {
                log?.(`[rsocket] runToBackground result: success=${bgResult.success}, error=${bgResult.error || 'none'}`)
                const result = create(UnifiedBackgroundResultSchema, {
                  success: bgResult.success,
                  isBash: bgResult.isBash,
                  taskId: bgResult.taskId,
                  command: bgResult.command,
                  bashCount: bgResult.bashCount ?? 0,
                  agentCount: bgResult.agentCount ?? 0,
                  backgroundedBashIds: bgResult.backgroundedBashIds ?? [],
                  backgroundedAgentIds: bgResult.backgroundedAgentIds ?? [],
                  error: bgResult.error,
                })
                responderStream.onNext({ data: Buffer.from(toBinary(UnifiedBackgroundResultSchema, result)) }, true)
              })
              .catch((err) => {
                log?.(`[rsocket] runToBackground error: ${err instanceof Error ? err.message : String(err)}`)
                const result = create(UnifiedBackgroundResultSchema, {
                  success: false,
                  isBash: undefined,
                  taskId,
                  bashCount: 0,
                  agentCount: 0,
                  backgroundedBashIds: [],
                  backgroundedAgentIds: [],
                  error: err instanceof Error ? err.message : String(err),
                })
                responderStream.onNext({ data: Buffer.from(toBinary(UnifiedBackgroundResultSchema, result)) }, true)
              })
            break
          }
          case 'agent.getHistory': {
            const sid = sessionId || ''
            const history = create(HistorySchema, { messages: sid ? historyStore.getSessionMessages(sid) : [] })
            responderStream.onNext({ data: Buffer.from(toBinary(HistorySchema, history)) }, true)
            break
          }
          case 'agent.getMcpStatus': {
            // Get MCP status from the registry
            void (async () => {
              try {
                const { mcpRegistry } = await import('../../ide/mcp')
                const statusList = await mcpRegistry.getMcpStatus()
                const result = create(McpStatusResultSchema, {
                  servers: statusList.map(s => ({
                    name: s.name,
                    status: s.status,
                    serverInfo: s.serverInfo,
                  })),
                })
                responderStream.onNext({ data: Buffer.from(toBinary(McpStatusResultSchema, result)) }, true)
              } catch (error) {
                log?.(`[rsocket] getMcpStatus error: ${error instanceof Error ? error.message : String(error)}`)
                const result = create(McpStatusResultSchema, { servers: [] })
                responderStream.onNext({ data: Buffer.from(toBinary(McpStatusResultSchema, result)) }, true)
              }
            })()
            break
          }
          case 'agent.reconnectMcp': {
            const req = data.length > 0 ? fromBinary(ReconnectMcpRequestSchema, data) : undefined
            void (async () => {
              try {
                const { mcpRegistry } = await import('../../ide/mcp')
                const reconnectResult = await mcpRegistry.reconnectMcp(req?.serverName || '')
                const result = create(ReconnectMcpResultSchema, {
                  success: reconnectResult.success,
                  serverName: reconnectResult.serverName,
                  status: reconnectResult.status || 'unknown',
                  toolsCount: reconnectResult.toolsCount,
                  error: reconnectResult.error,
                })
                responderStream.onNext({ data: Buffer.from(toBinary(ReconnectMcpResultSchema, result)) }, true)
              } catch (error) {
                log?.(`[rsocket] reconnectMcp error: ${error instanceof Error ? error.message : String(error)}`)
                const result = create(ReconnectMcpResultSchema, {
                  success: false,
                  serverName: req?.serverName || '',
                  status: 'error',
                  toolsCount: 0,
                  error: error instanceof Error ? error.message : 'Unknown error',
                })
                responderStream.onNext({ data: Buffer.from(toBinary(ReconnectMcpResultSchema, result)) }, true)
              }
            })()
            break
          }
          case 'agent.getMcpTools': {
            const req = data.length > 0 ? fromBinary(GetMcpToolsRequestSchema, data) : undefined
            void (async () => {
              try {
                const { mcpRegistry } = await import('../../ide/mcp')
                const toolsResult = await mcpRegistry.getMcpTools(req?.serverName || undefined)
                const result = create(GetMcpToolsResultSchema, {
                  serverName: toolsResult.serverName,
                  tools: toolsResult.tools.map(t => ({
                    name: t.name,
                    description: t.description,
                    inputSchema: t.inputSchema ? JSON.stringify(t.inputSchema) : undefined,
                  })),
                  count: toolsResult.count,
                })
                responderStream.onNext({ data: Buffer.from(toBinary(GetMcpToolsResultSchema, result)) }, true)
              } catch (error) {
                log?.(`[rsocket] getMcpTools error: ${error instanceof Error ? error.message : String(error)}`)
                const result = create(GetMcpToolsResultSchema, {
                  serverName: req?.serverName,
                  tools: [],
                  count: 0,
                })
                responderStream.onNext({ data: Buffer.from(toBinary(GetMcpToolsResultSchema, result)) }, true)
              }
            })()
            break
          }
          case 'agent.truncateHistory': {
            const req = data.length > 0 ? fromBinary(TruncateHistoryRequestSchema, data) : undefined
            const sid = req?.sessionId || sessionId || ''
            const uuid = req?.messageUuid || ''

            const result = !sid || !uuid
              ? create(TruncateHistoryResultSchema, { success: false, remainingLines: 0, error: 'Missing sessionId/messageUuid' })
              : create(TruncateHistoryResultSchema, historyStore.truncateHistory(sid, uuid))

            responderStream.onNext({ data: Buffer.from(toBinary(TruncateHistoryResultSchema, result)) }, true)
            break
          }
          case 'agent.hasIdeEnvironment': {
            // VS Code 扩展始终有 IDE 环境
            const response = create(HasIdeEnvironmentResponseSchema, { hasIde: true })
            responderStream.onNext({ data: Buffer.from(toBinary(HasIdeEnvironmentResponseSchema, response)) }, true)
            break
          }
          default: {
            log?.(`[rsocket] unsupported requestResponse route=${route || '(empty)'}`)
            responderStream.onError(new Error(`Unsupported route: ${route}`))
          }
        }
      } catch (error) {
        log?.(`[rsocket] requestResponse error route=${route || '(empty)'} err=${error instanceof Error ? error.message : String(error)}`)
        responderStream.onError(error instanceof Error ? error : new Error(String(error)))
      }

      return createCancellable()
    },

    requestStream: (payload, _initialRequestN, responderStream) => {
      const route = extractRoute(payload)
      const data = payload.data ? new Uint8Array(payload.data) : new Uint8Array()
      let cancelled = false
      let cancelStream: (() => void) | undefined

      const safeOnNext = (p: Payload, isComplete: boolean) => {
        if (cancelled) return
        responderStream.onNext(p, isComplete)
        // Broadcast query/tool events to `agent.events` subscribers.
        if (route !== 'agent.events') {
          broadcastGlobalPayload(p)
        }
      }
      const safeOnComplete = () => {
        responderStream.onComplete()
      }
      const safeOnError = (err: Error) => {
        if (cancelled) return
        responderStream.onError(err)
      }

      const startProviderQueryStream = (userMessage: string) => {
        // Codex Provider 实现
        if (provider === Provider.CODEX) {
          const codexSessionOptions: CodexSessionOptions = {
            workingDirectory: getWorkspaceRoot(),
            configOverrides: {},
            permissionRequester: async (req) => {
              // 调用前端权限请求
              const call = create(ServerCallRequestSchema, {
                callId: `srv-${req.toolUseId}`,
                method: 'RequestPermission',
                params: {
                  case: 'requestPermission',
                  value: create(RequestPermissionRequestSchema, {
                    toolName: req.toolName,
                    inputJson: Buffer.from(JSON.stringify(req.input ?? {}), 'utf8'),
                    toolUseId: req.toolUseId,
                  }),
                },
              })

              try {
                const { promise } = requestClientCall(remotePeer, call)
                const responseBytes = await promise
                const response = fromBinary(ServerCallResponseSchema, responseBytes)
                const perm = response.result.case === 'requestPermission' ? response.result.value : undefined

                return {
                  approved: perm?.approved ?? false,
                  denyReason: perm?.denyReason || undefined,
                }
              } catch (e) {
                return {
                  approved: false,
                  denyReason: e instanceof Error ? e.message : String(e),
                }
              }
            },
          }

          const codexSession = new CodexSession(codexSessionOptions)
          const streamAdapter = new CodexAppServerStreamAdapter(() => sessionId || 'unknown')

          let codexAborted = false
          let turnCompletedResolve: (() => void) | null = null
          const turnCompletedPromise = new Promise<void>((resolve) => {
            turnCompletedResolve = resolve
          })

          codexSession.on('event', (event: AppServerEvent) => {
            if (codexAborted) return

            const normalizedEvents = streamAdapter.convert(event)
            for (const normalized of normalizedEvents) {
              const rpcMsg = convertNormalizedEventToRpcMessage(normalized, provider, sessionId || 'unknown')
              if (rpcMsg) {
                safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, rpcMsg)) }, false)
                if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), rpcMsg)
              }

              // 检测 turn 完成或失败
              if (normalized.type === 'turnCompleted' || normalized.type === 'turnFailed' || normalized.type === 'resultSummary') {
                if (turnCompletedResolve) {
                  turnCompletedResolve()
                  turnCompletedResolve = null
                }
              }
            }
          })

          codexSession.on('error', (err: Error) => {
            if (codexAborted) return
            safeOnError(err)
            // 错误时也要resolve，避免永久等待
            if (turnCompletedResolve) {
              turnCompletedResolve()
              turnCompletedResolve = null
            }
          })

          const runCodexSession = async () => {
            try {
              await codexSession.connect()
              await codexSession.sendMessage({ text: userMessage, sessionId: sessionId || undefined })
              // 等待 turn 完成
              await turnCompletedPromise
            } catch (e) {
              if (!codexAborted) {
                safeOnError(e instanceof Error ? e : new Error(String(e)))
              }
            } finally {
              if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
              safeOnComplete()
            }
          }

          cancelStream = () => {
            codexAborted = true
            // 取消时也要resolve，避免永久等待
            if (turnCompletedResolve) {
              turnCompletedResolve()
              turnCompletedResolve = null
            }
            codexSession.interrupt().catch(() => {})
            codexSession.disconnect().catch(() => {})
          }
          currentStreamCancel = cancelStream

          runCodexSession()
          return
        }

        // 其他非 Claude Provider 占位符
        if (provider !== Provider.CLAUDE) {
          const done = () => {
            if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
            safeOnComplete()
          }

          cancelStream = streamText(
            { provider, sessionId: sessionId || 'unknown' },
            `VS Code backend for provider=${String(provider)} is not implemented yet.\n\nYou sent: ${userMessage}`,
            safeOnNext,
            done,
            (msg) => {
              if (!sessionId) return
              historyStore.appendMessage(sessionId, getWorkspaceRoot(), msg)
            }
          )
          currentStreamCancel = cancelStream
          return
        }

        // Claude CLI streaming (real chat loop)
        const queryStartedAt = Date.now()
        let streamFinalized = false
        let resultSent = false
        let abortRequested = false
        let innerCancel: (() => void) | undefined

        // Translate Claude CLI stream-json events into RPC StreamEvent messages.
        // The frontend expects message_start/content_block_start before any delta events.
        // 
        // 关键：使用Claude API返回的index字段，而不是自己计算！
        // Claude API会确保thinking块（如果存在）的索引在text块之前。
        const streamUuid = crypto.randomUUID()
        const streamSessionId = sessionId || 'unknown'
        
        // 块索引映射：从Claude API的原始索引到已发送的块索引
        // key = Claude API的index, value = 我们发送的content_block_start的索引
        let blockIndexMap = new Map<number, { type: 'text' | 'thinking' | 'tool'; started: boolean; stopped: boolean }>()

        let didStartMessage = false
        let didStopMessage = false
        let currentMessageId: string | undefined
        let messageSeq = 0
        const deliveredToolResultSignatures = new Map<string, string>()

        const emitStreamEvent = (event: any) => {
          const streamEvent = create(StreamEventSchema, {
            uuid: streamUuid,
            sessionId: streamSessionId,
            event: create(StreamEventDataSchema, { event }),
          })
          const rpcMsg = create(RpcMessageSchema, {
            provider,
            message: { case: 'streamEvent', value: streamEvent },
          } as any)

          safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, rpcMsg)) }, false)
          if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), rpcMsg)
        }

        const emitToolResultToFrontend = (toolUseId: string, contentValue: unknown, isError: boolean) => {
          if (!toolUseId) return

          let normalizedContent = ''
          try {
            normalizedContent = JSON.stringify(contentValue ?? '')
          } catch {
            normalizedContent = String(contentValue ?? '')
          }

          const signature = `${isError ? '1' : '0'}:${normalizedContent}`
          const lastSignature = deliveredToolResultSignatures.get(toolUseId)
          if (lastSignature === signature) return
          deliveredToolResultSignatures.set(toolUseId, signature)

          const toolResultMsg = createUserToolResultMessage(provider, toolUseId, contentValue, isError)
          safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolResultMsg)) }, false)
          if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolResultMsg)
        }

        const startNewMessage = (messageId?: string) => {
          // Reset per-message state
          blockIndexMap = new Map()
          didStartMessage = true
          didStopMessage = false
          currentMessageId = messageId || `${streamUuid}-${++messageSeq}`

          const info = create(MessageStartInfoSchema, { id: currentMessageId, model, content: [] })
          emitStreamEvent({
            case: 'messageStart',
            value: create(MessageStartEventSchema, { messageInfo: info }),
          })
        }

        const ensureMessageStart = () => {
          // Fallback only: normally Claude CLI emits message_start.
          if (!didStartMessage || didStopMessage) {
            startNewMessage(undefined)
          }
        }

        // 处理 content_block_start 事件，使用Claude API返回的原始索引
        const handleContentBlockStart = (index: number, blockType: 'text' | 'thinking' | 'tool', blockData?: any) => {
          ensureMessageStart()
          
          // 检查是否已经为这个索引发送过start
          if (blockIndexMap.has(index)) {
            const existing = blockIndexMap.get(index)!
            if (existing.started) return // 已经发送过了
          }
          
          blockIndexMap.set(index, { type: blockType, started: true, stopped: false })
          
          let block: any
          if (blockType === 'thinking') {
            block = create(ContentBlockSchema, {
              block: { case: 'thinking', value: create(ThinkingBlockSchema, { thinking: '', signature: '' }) },
            })
          } else if (blockType === 'text') {
            block = create(ContentBlockSchema, {
              block: { case: 'text', value: create(TextBlockSchema, { text: '' }) },
            })
          } else {
            // tool type - 使用传入的blockData
            block = blockData
          }
          
          emitStreamEvent({
            case: 'contentBlockStart',
            value: create(ContentBlockStartEventSchema, { index, contentBlock: block }),
          })
        }

        // 确保某个索引的块已经start（用于delta事件到达时）
        const ensureBlockStarted = (index: number, blockType: 'text' | 'thinking') => {
          if (!blockIndexMap.has(index) || !blockIndexMap.get(index)!.started) {
            handleContentBlockStart(index, blockType)
          }
        }

        const handleContentBlockStop = (index: number) => {
          const blockInfo = blockIndexMap.get(index)
          if (!blockInfo || !blockInfo.started || blockInfo.stopped) return
          
          blockInfo.stopped = true
          emitStreamEvent({
            case: 'contentBlockStop',
            value: create(ContentBlockStopEventSchema, { index }),
          })
        }

        const ensureStreamStopped = () => {
          if (!didStartMessage || didStopMessage) return

          // 停止所有已开始但未停止的块
          for (const [index, blockInfo] of blockIndexMap.entries()) {
            if (blockInfo.started && !blockInfo.stopped) {
              handleContentBlockStop(index)
            }
          }

          didStopMessage = true
          emitStreamEvent({ case: 'messageStop', value: create(MessageStopEventSchema, {}) })
        }

        const emitResult = (subtype: string, isError: boolean, resultText?: string) => {
          if (resultSent) return
          resultSent = true

          const durationMs = BigInt(Math.max(0, Date.now() - queryStartedAt))
          const result = create(ResultMessageSchema, {
            subtype,
            durationMs,
            isError,
            numTurns: 1,
            sessionId: sessionId || undefined,
            result: resultText || undefined,
          })

          const rpcMsg = create(RpcMessageSchema, {
            provider,
            message: { case: 'result', value: result },
          } as any)

          safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, rpcMsg)) }, false)
          if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), rpcMsg)
        }

        const finalizeStream = (subtype: string, isError: boolean, resultText?: string) => {
          if (streamFinalized) return
          streamFinalized = true

          try {
            if (includePartialMessages) ensureStreamStopped()
          } catch {
            // ignore best-effort shutdown
          }

          emitResult(subtype, isError, resultText)

          if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
          safeOnComplete()
        }

        const cancelOp = () => {
          abortRequested = true
          innerCancel?.()
          finalizeStream('interrupted', false)
          cancelled = true
        }
        cancelStream = cancelOp
        currentStreamCancel = cancelOp

        void (async () => {
          try {
            const cfg = vscode.workspace.getConfiguration('claudeCodePlus')
            const defaultBypassPermissions = Boolean(cfg.get('defaultBypassPermissions') ?? false)

            // 构建 MCP 配置
            const mcpServers = getMcpServersFromSettings()
            const mcpGateway = getMcpHttpGateway()
            const gatewayPort = mcpGateway?.getPort()
            const gatewayStarted = mcpGateway?.isStarted?.() ?? false
            log?.(`[rsocket] agent.query: MCP Gateway status: instance=${mcpGateway ? 'exists' : 'null'}, started=${gatewayStarted}, port=${gatewayPort}`)
            
            // 警告：如果 Gateway 端口无效，内置 MCP 服务器将无法工作
            if (!gatewayPort || gatewayPort === 0) {
              log?.(`[rsocket] WARNING: MCP Gateway port is invalid (${gatewayPort}). Built-in MCP servers will NOT be configured!`)
              log?.(`[rsocket] This may happen if: 1) MCP Gateway failed to start, 2) No MCP servers were registered`)
            }
            
            log?.(`[rsocket] agent.query: building MCP config, connectId=${connectId}, mcpGatewayPort=${gatewayPort}, mcpServersCount=${mcpServers.length}`)
            const mcpResult = buildMcpConfig(mcpServers, 'claude', {
              connectId,
              mcpGatewayPort: gatewayPort
            })
            
            // 将 instructions 写入临时文件
            let appendSystemPromptFilePath: string | undefined
            if (mcpResult.systemPromptAppendix) {
              appendSystemPromptFilePath = writeSystemPromptAppendix(mcpResult.systemPromptAppendix) ?? undefined
              if (appendSystemPromptFilePath) {
                mcpResult.tempFiles.push(appendSystemPromptFilePath)
              }
            }

            const cliSession = await claudeCli.getOrCreate({
              sessionId: sessionId || 'default',
              cwd: getWorkspaceRoot(),
              model,
              permissionMode: toClaudePermissionMode(permissionMode),
              includePartialMessages,
              dangerouslySkipPermissions: dangerouslySkipPermissions || defaultBypassPermissions,
              addDirs: getAdditionalDirs(),
              connectId,
              clientCaller,
              mcpConfigFilePath: mcpResult.configFilePath ?? undefined,
              appendSystemPromptFilePath,
            })

            // 注册临时文件清理（会话结束时）
            if (mcpResult.tempFiles.length > 0) {
              cliSession.onClose(() => {
                cleanupTempFiles(mcpResult.tempFiles)
              })
            }

            if (includePartialMessages && !abortRequested) {
              // 不再预先发送content_block_start，而是等待Claude API的事件
              // Claude API会按正确顺序发送thinking和text的content_block_start
              ensureMessageStart()
            }

            const handle = cliSession.startQuery(userMessage, {
              onJsonMessage: (rawMsg) => {
                if (abortRequested) return
                const msg: any = rawMsg
                if (!msg || typeof msg !== 'object') return
                if (msg.type === 'stream_event') {
                  if (!includePartialMessages) return
                  const ev = msg.event
                  const evType = typeof ev?.type === 'string' ? ev.type : ''
                  const evIndex = typeof ev?.index === 'number' ? ev.index : 0

                  // message_start may occur multiple times in a single query when tools are used.
                  // We must propagate it so the frontend can create a new streaming message.
                  if (evType === 'message_start') {
                    const messageId = typeof (ev as any)?.message?.id === 'string' ? (ev as any).message.id : undefined
                    startNewMessage(messageId)
                    return
                  }

                  // 处理 content_block_start 事件 - 使用Claude API返回的原始索引
                  if (evType === 'content_block_start') {
                    const contentBlock = ev.content_block
                    const blockType = contentBlock?.type
                    if (blockType === 'thinking') {
                      handleContentBlockStart(evIndex, 'thinking')
                    } else if (blockType === 'text') {
                      handleContentBlockStart(evIndex, 'text')
                    } else if (blockType === 'tool_use') {
                      // 构建 tool_use block 并发送
                      const toolBlock = create(ContentBlockSchema, {
                        block: {
                          case: 'toolUse',
                          value: create(ToolUseBlockSchema, {
                            id: contentBlock.id || '',
                            toolName: contentBlock.name || '',
                            toolType: contentBlock.name || '',
                            inputJson: Buffer.from(JSON.stringify(contentBlock.input ?? {}), 'utf8'),
                            status: ContentStatus.IN_PROGRESS,
                          }),
                        },
                      })
                      handleContentBlockStart(evIndex, 'tool', toolBlock)
                    }
                    return
                  }

                  // 处理 content_block_stop 事件
                  if (evType === 'content_block_stop') {
                    handleContentBlockStop(evIndex)
                    return
                  }

                  // 处理 text_delta - 使用Claude API返回的index
                  if (evType === 'content_block_delta' && ev?.delta?.type === 'text_delta') {
                    const text = typeof ev.delta.text === 'string' ? ev.delta.text : ''
                    if (!text) return

                    // 确保该索引的块已经开始（如果收到delta但没收到start）
                    ensureBlockStarted(evIndex, 'text')
                    emitStreamEvent({
                      case: 'contentBlockDelta',
                      value: create(ContentBlockDeltaEventSchema, {
                        index: evIndex,  // 使用Claude API的索引
                        delta: create(DeltaSchema, {
                          delta: { case: 'textDelta', value: create(TextDeltaSchema, { text }) },
                        }),
                      }),
                    })
                    return
                  }

                  // 处理 thinking_delta - 使用Claude API返回的index
                  if (evType === 'content_block_delta' && ev?.delta?.type === 'thinking_delta') {
                    const thinking = typeof ev.delta.thinking === 'string' ? ev.delta.thinking : ''
                    if (!thinking) return

                    // 确保该索引的块已经开始（如果收到delta但没收到start）
                    ensureBlockStarted(evIndex, 'thinking')
                    emitStreamEvent({
                      case: 'contentBlockDelta',
                      value: create(ContentBlockDeltaEventSchema, {
                        index: evIndex,  // 使用Claude API的索引
                        delta: create(DeltaSchema, {
                          delta: { case: 'thinkingDelta', value: create(ThinkingDeltaSchema, { thinking }) },
                        }),
                      }),
                    })
                    return
                  }

                  // 处理 input_json_delta - 工具输入的流式更新
                  if (evType === 'content_block_delta' && ev?.delta?.type === 'input_json_delta') {
                    const partialJson = typeof ev.delta.partial_json === 'string' ? ev.delta.partial_json : ''
                    if (!partialJson) return

                    emitStreamEvent({
                      case: 'contentBlockDelta',
                      value: create(ContentBlockDeltaEventSchema, {
                        index: evIndex,
                        delta: create(DeltaSchema, {
                          delta: { case: 'inputJsonDelta', value: create(InputJsonDeltaSchema, { partialJson }) },
                        }),
                      }),
                    })
                    return
                  }

                  // 处理 tool_result delta（用于把工具结果回灌到前端工具卡）
                  if (evType === 'content_block_delta' && ev?.delta?.type === 'tool_result') {
                    const toolUseId =
                      typeof ev?.delta?.tool_use_id === 'string'
                        ? ev.delta.tool_use_id
                        : typeof ev?.delta?.toolUseId === 'string'
                          ? ev.delta.toolUseId
                          : ''

                    if (!toolUseId) return

                    const contentValue =
                      ev?.delta?.content !== undefined
                        ? ev.delta.content
                        : ev?.delta?.result !== undefined
                          ? ev.delta.result
                          : ''

                    const isError =
                      ev?.delta?.is_error === true ||
                      ev?.delta?.isError === true

                    emitToolResultToFrontend(toolUseId, contentValue, isError)
                    return
                  }

                  if (evType === 'message_stop') {
                    ensureStreamStopped()
                  }
                  return
                }

                // Claude CLI 可能直接产出 user/tool_result 消息（而非 stream_event delta）。
                // 在 partial 模式下也要转发给前端，用于更新工具卡结果。
                if (msg.type === 'user') {
                  const contentArr = Array.isArray(msg.message?.content)
                    ? msg.message.content
                    : Array.isArray(msg.content)
                      ? msg.content
                      : []

                  for (const block of contentArr) {
                    if (!block || typeof block !== 'object' || block.type !== 'tool_result') continue

                    const toolUseId =
                      typeof (block as any).tool_use_id === 'string'
                        ? (block as any).tool_use_id
                        : typeof (block as any).toolUseId === 'string'
                          ? (block as any).toolUseId
                          : ''

                    const contentValue =
                      (block as any).content !== undefined
                        ? (block as any).content
                        : (block as any).result !== undefined
                          ? (block as any).result
                          : ''

                    const isError =
                      (block as any).is_error === true ||
                      (block as any).isError === true

                    emitToolResultToFrontend(toolUseId, contentValue, isError)
                  }
                  return
                }

                // No partial messages: translate the final assistant message into a single assistant RpcMessage.
                if (!includePartialMessages && msg.type === 'assistant') {
                  const contentArr = Array.isArray(msg.message?.content)
                    ? msg.message.content
                    : Array.isArray(msg.content)
                      ? msg.content
                      : []
                  const text = contentArr
                    .filter((b: any) => b && typeof b === 'object' && b.type === 'text' && typeof b.text === 'string')
                    .map((b: any) => b.text)
                    .join('')
                  if (!text) return

                  const block = create(ContentBlockSchema, { block: { case: 'text', value: create(TextBlockSchema, { text }) } })
                  const content = create(MessageContentSchema, { content: [block] })
                  const assistant = create(AssistantMessageSchema, { message: content, uuid: crypto.randomUUID() })
                  const assistantMsg = create(RpcMessageSchema, { provider, message: { case: 'assistant', value: assistant } } as any)

                  safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, assistantMsg)) }, false)
                  if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), assistantMsg)
                }
              },
              requestPermission: async (req): Promise<ToolPermissionResult> => {
                const toolUseId = req.toolUseId || crypto.randomUUID()
                const request = create(RequestPermissionRequestSchema, {
                  toolName: req.toolName,
                  inputJson: Buffer.from(JSON.stringify(req.input ?? {}), 'utf8'),
                  toolUseId,
                  permissionSuggestions: [],
                })
                const call = create(ServerCallRequestSchema, {
                  callId: `srv-${toolUseId}`,
                  method: 'RequestPermission',
                  params: { case: 'requestPermission', value: request },
                })

                const { promise } = requestClientCall(remotePeer, call)
                const responseBytes = await promise
                const response = fromBinary(ServerCallResponseSchema, responseBytes)
                const perm = response.result.case === 'requestPermission' ? response.result.value : undefined

                const approved = perm?.approved ?? false
                const denyReason = perm?.denyReason || undefined

                // If not approved, return early
                if (!approved) {
                  return { approved, denyReason }
                }

                // Check if this is a write file tool
                const writeToolNames = [
                  'Write',
                  'Edit',
                  'MultiEdit',
                  'mcp__jetbrains-file__WriteFile',
                  'mcp__jetbrains-file__EditFile',
                ]
                const isWriteTool = writeToolNames.includes(req.toolName)

                if (!isWriteTool) {
                  return { approved, denyReason }
                }

                // Extract filePath from input
                const input = req.input as Record<string, unknown> | undefined
                const filePath =
                  (input?.file_path as string | undefined) ||
                  (input?.filePath as string | undefined)

                if (!filePath) {
                  return { approved, denyReason }
                }

                // Save snapshot before the write operation
                try {
                  const uri = toWorkspaceFileUri(filePath)
                  const canRollback = vscode.workspace.getWorkspaceFolder(uri) != null
                  const historyTs = canRollback ? Date.now() : 0

                  let isNewFile = false
                  let isOverwrite = false

                  if (canRollback) {
                    try {
                      const rawOld = await vscode.workspace.fs.readFile(uri)
                      const original = Buffer.from(rawOld).toString('utf8')
                      isOverwrite = true
                      snapshotStore.save({ toolUseId, filePath: uri.fsPath, timestamp: historyTs, content: original })
                    } catch {
                      // File does not exist yet
                      isNewFile = true
                    }
                  } else {
                    // Not in workspace, check if file exists
                    try {
                      await vscode.workspace.fs.stat(uri)
                      isOverwrite = true
                    } catch {
                      isNewFile = true
                    }
                  }

                  return {
                    approved,
                    denyReason,
                    snapshotMeta: {
                      historyTs,
                      canRollback,
                      isNewFile,
                      isOverwrite,
                    },
                  }
                } catch (err) {
                  // If snapshot fails, still allow the operation but without rollback support
                  log?.(`[rsocket] Failed to save snapshot for ${filePath}: ${err}`)
                  return { approved, denyReason }
                }
              },
            })

            innerCancel = handle.cancel
            await handle.done
            if (!abortRequested) finalizeStream('success', false)
          } catch (err) {
            if (abortRequested) return
            const e = err instanceof Error ? err : new Error(String(err))
            finalizeStream('error_during_execution', true, e.message)
          }
        })()
      }

      try {
        switch (route) {
          case 'agent.query': {
            const req = data.length > 0 ? fromBinary(QueryRequestSchema, data) : undefined
            const userMessage = req?.message || ''
            currentStreamCancel?.()
            currentStreamCancel = undefined

            if (sessionId) {
              historyStore.appendMessage(sessionId, getWorkspaceRoot(), createUserTextMessage(provider, userMessage))
            }

            const trimmed = userMessage.trim()

            if (trimmed.startsWith('/perm')) {
              const toolUseId = crypto.randomUUID()
              const request = create(RequestPermissionRequestSchema, {
                toolName: 'Bash',
                inputJson: Buffer.from(JSON.stringify({ command: 'echo hello' }), 'utf8'),
                toolUseId,
                permissionSuggestions: [],
              })
              const call = create(ServerCallRequestSchema, {
                callId: `srv-${toolUseId}`,
                method: 'RequestPermission',
                params: { case: 'requestPermission', value: request },
              })

              const { promise, cancel } = requestClientCall(remotePeer, call)
              currentStreamCancel = cancel

              promise
                .then((responseBytes) => {
                  if (cancelled) return

                  const response = fromBinary(ServerCallResponseSchema, responseBytes)
                  const perm = response.result.case === 'requestPermission' ? response.result.value : undefined
                  const approved = perm?.approved ?? false
                  const denyReason = perm?.denyReason ? `（原因：${perm.denyReason}）` : ''

                  const done = () => {
                    if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
                    safeOnComplete()
                  }

                  cancelStream = streamText(
                    {
                      provider,
                      sessionId: sessionId || 'unknown',
                    },
                    approved ? `权限已批准：${toolUseId}` : `权限被拒绝：${toolUseId}${denyReason}`,
                    safeOnNext,
                    done,
                    (msg) => {
                      if (!sessionId) return
                      historyStore.appendMessage(sessionId, getWorkspaceRoot(), msg)
                    }
                  )
                  currentStreamCancel = cancelStream
                })
                .catch((err) => {
                  if (cancelled) return
                  if (err instanceof Error && err.message === 'client.call cancelled') {
                    safeOnComplete()
                    return
                  }
                  safeOnError(err instanceof Error ? err : new Error(String(err)))
                })

              break
            }

            if (trimmed.startsWith('/ask')) {
              const callId = crypto.randomUUID()
              const askReq = create(AskUserQuestionRequestSchema, {
                questions: [
                  {
                    header: 'VS Code Mock',
                    question: '请选择一个选项',
                    multiSelect: false,
                    options: [
                      { label: '选项 A', description: 'A' },
                      { label: '选项 B', description: 'B' },
                    ],
                  },
                ],
              })
              const call = create(ServerCallRequestSchema, {
                callId: `srv-${callId}`,
                method: 'AskUserQuestion',
                params: { case: 'askUserQuestion', value: askReq },
              })

              const { promise, cancel } = requestClientCall(remotePeer, call)
              currentStreamCancel = cancel

              promise
                .then((responseBytes) => {
                  if (cancelled) return

                  const response = fromBinary(ServerCallResponseSchema, responseBytes)
                  const answers =
                    response.result.case === 'askUserQuestion'
                      ? response.result.value.answers.map((a) => `${a.question} -> ${a.answer}`).join('\n')
                      : '(no answers)'

                  const done = () => {
                    if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
                    safeOnComplete()
                  }

                  cancelStream = streamText(
                    {
                      provider,
                      sessionId: sessionId || 'unknown',
                    },
                    `AskUserQuestion 已回答：\n${answers}`,
                    safeOnNext,
                    done,
                    (msg) => {
                      if (!sessionId) return
                      historyStore.appendMessage(sessionId, getWorkspaceRoot(), msg)
                    }
                  )
                  currentStreamCancel = cancelStream
                })
                .catch((err) => {
                  if (cancelled) return
                  if (err instanceof Error && err.message === 'client.call cancelled') {
                    safeOnComplete()
                    return
                  }
                  safeOnError(err instanceof Error ? err : new Error(String(err)))
                })

              break
            }

            if (trimmed.startsWith('/read')) {
              const match = trimmed.match(/^\/read\s+(\S+)(?:\s+(\d+))?(?:\s+(\d+))?\s*$/)
              if (!match) {
                const done = () => {
                  if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
                  safeOnComplete()
                }
                cancelStream = streamText(
                  { provider, sessionId: sessionId || 'unknown' },
                  '用法：/read <path> [startLine] [endLine]',
                  safeOnNext,
                  done
                )
                currentStreamCancel = cancelStream
                break
              }

              const filePath = match[1]
              const startLine = match[2] ? Number(match[2]) : undefined
              const endLine = match[3] ? Number(match[3]) : undefined
              const toolUseId = crypto.randomUUID()

              const input: Record<string, unknown> = { filePath }
              if (startLine && startLine > 0) input.offset = startLine
              if (startLine && endLine && endLine >= startLine) input.maxLines = endLine - startLine + 1

              const toolName = 'mcp__jetbrains-file__ReadFile'
              const toolUseMsg = createAssistantToolUseMessage(provider, toolUseId, toolName, toolName, input)
              safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolUseMsg)) }, false)
              if (sessionId) {
                historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolUseMsg)
              }

              let finished = false
              const finishStream = () => {
                if (finished) return
                finished = true
                if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
                safeOnComplete()
              }

              const cancelOp = () => {
                cancelled = true
                finishStream()
              }
              cancelStream = cancelOp
              currentStreamCancel = cancelOp

              void (async () => {
                let isError = false
                let resultContent: unknown = ''

                try {
                  const uri = toWorkspaceFileUri(filePath)
                  const raw = await vscode.workspace.fs.readFile(uri)
                  let text = Buffer.from(raw).toString('utf8')

                  if (startLine || endLine) {
                    const lines = text.split(/\r?\n/)
                    const startIdx = Math.max((startLine ?? 1) - 1, 0)
                    const endIdx = Math.min(endLine ?? lines.length, lines.length)
                    text = lines.slice(startIdx, endIdx).join('\n')
                  }

                  resultContent = text
                } catch (err) {
                  isError = true
                  resultContent = err instanceof Error ? err.message : String(err)
                }

                if (cancelled) {
                  finishStream()
                  return
                }

                const toolResultMsg = createUserToolResultMessage(provider, toolUseId, resultContent, isError)
                safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolResultMsg)) }, false)
                if (sessionId) {
                  historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolResultMsg)
                }

                finishStream()
              })()

              break
            }

            if (trimmed.startsWith('/write')) {
              const match = userMessage.match(/^\/write\s+(\S+)\s*([\s\S]*)$/)
              if (!match) {
                const done = () => {
                  if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
                  safeOnComplete()
                }
                cancelStream = streamText(
                  { provider, sessionId: sessionId || 'unknown' },
                  '用法：/write <path> <content...>',
                  safeOnNext,
                  done
                )
                currentStreamCancel = cancelStream
                break
              }

              const filePath = match[1]
              const contentToWrite = (match[2] ?? '').replace(/^\s+/, '')
              const toolUseId = crypto.randomUUID()
              const input: Record<string, unknown> = { filePath, content: contentToWrite }
              const toolName = 'mcp__jetbrains-file__WriteFile'

              const toolUseMsg = createAssistantToolUseMessage(provider, toolUseId, toolName, toolName, input)
              safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolUseMsg)) }, false)
              if (sessionId) {
                historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolUseMsg)
              }

              const request = create(RequestPermissionRequestSchema, {
                toolName,
                inputJson: Buffer.from(JSON.stringify(input), 'utf8'),
                toolUseId,
                permissionSuggestions: [],
              })
              const call = create(ServerCallRequestSchema, {
                callId: `srv-${toolUseId}`,
                method: 'RequestPermission',
                params: { case: 'requestPermission', value: request },
              })

              const { promise, cancel } = requestClientCall(remotePeer, call)
              cancelStream = cancel
              currentStreamCancel = cancel

              promise
                .then(async (responseBytes) => {
                  if (cancelled) return

                  const response = fromBinary(ServerCallResponseSchema, responseBytes)
                  const perm = response.result.case === 'requestPermission' ? response.result.value : undefined
                  const approved = perm?.approved ?? false

                  if (!approved) {
                    const denyReason = perm?.denyReason ? `Denied: ${perm.denyReason}` : 'Denied'
                    const toolResultMsg = createUserToolResultMessage(provider, toolUseId, denyReason, true)
                    safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolResultMsg)) }, false)
                    if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolResultMsg)

                    if (currentStreamCancel === cancel) currentStreamCancel = undefined
                    safeOnComplete()
                    return
                  }

                  try {
                    const uri = toWorkspaceFileUri(filePath)
                    const canRollback = vscode.workspace.getWorkspaceFolder(uri) != null
                    const historyTs = canRollback ? Date.now() : undefined

                    let isNewFile = false
                    let isOverwrite = false

                    if (canRollback) {
                      try {
                        const rawOld = await vscode.workspace.fs.readFile(uri)
                        const original = Buffer.from(rawOld).toString('utf8')
                        isOverwrite = true
                        snapshotStore.save({ toolUseId, filePath: uri.fsPath, timestamp: historyTs!, content: original })
                      } catch {
                        isNewFile = true
                      }
                    } else {
                      try {
                        await vscode.workspace.fs.stat(uri)
                        isOverwrite = true
                      } catch {
                        isNewFile = true
                      }
                    }

                    await vscode.workspace.fs.createDirectory(vscode.Uri.file(path.dirname(uri.fsPath)))
                    await vscode.workspace.fs.writeFile(uri, Buffer.from(contentToWrite, 'utf8'))

                    const output: string[] = []
                    if (historyTs !== undefined) output.push(`[jb:historyTs=${historyTs}]`)
                    output.push(`[jb:isOverwrite=${isOverwrite}]`)
                    output.push(`[jb:isNewFile=${isNewFile}]`)
                    output.push(`[jb:canRollback=${canRollback}]`)
                    output.push('')
                    output.push(`${isNewFile ? 'Created' : 'Updated'} File: \`${filePath}\``)
                    output.push(`Wrote ${contentToWrite.length} chars`)

                    const toolResultMsg = createUserToolResultMessage(
                      provider,
                      toolUseId,
                      output.join('\n'),
                      false
                    )
                    safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolResultMsg)) }, false)
                    if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolResultMsg)
                  } catch (err) {
                    const toolResultMsg = createUserToolResultMessage(
                      provider,
                      toolUseId,
                      err instanceof Error ? err.message : String(err),
                      true
                    )
                    safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolResultMsg)) }, false)
                    if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolResultMsg)
                  } finally {
                    if (currentStreamCancel === cancel) currentStreamCancel = undefined
                    safeOnComplete()
                  }
                })
                .catch((err) => {
                  if (cancelled) return
                  if (err instanceof Error && err.message === 'client.call cancelled') {
                    safeOnComplete()
                    return
                  }
                  safeOnError(err instanceof Error ? err : new Error(String(err)))
                })

              break
            }

            if (trimmed.startsWith('/edit')) {
              const match = userMessage.match(/^\/edit\s+(\S+)\s+([\s\S]+)$/)
              if (!match) {
                const done = () => {
                  if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
                  safeOnComplete()
                }
                cancelStream = streamText(
                  { provider, sessionId: sessionId || 'unknown' },
                  '用法：/edit <path> {\"oldString\":\"...\",\"newString\":\"...\",\"replaceAll\":false}',
                  safeOnNext,
                  done
                )
                currentStreamCancel = cancelStream
                break
              }

              const filePath = match[1]
              const jsonPart = match[2].trim()
              let args: Record<string, unknown>
              try {
                args = JSON.parse(jsonPart)
              } catch {
                const done = () => {
                  if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
                  safeOnComplete()
                }
                cancelStream = streamText(
                  { provider, sessionId: sessionId || 'unknown' },
                  'edit 参数必须是 JSON，例如：/edit README.md {\"oldString\":\"foo\",\"newString\":\"bar\",\"replaceAll\":false}',
                  safeOnNext,
                  done
                )
                currentStreamCancel = cancelStream
                break
              }

              const oldStr = String((args as any).oldString ?? (args as any).old_string ?? '')
              const newStr = String((args as any).newString ?? (args as any).new_string ?? '')
              const replaceAll = Boolean((args as any).replaceAll ?? (args as any).replace_all)

              const toolUseId = crypto.randomUUID()
              const toolName = 'mcp__jetbrains-file__EditFile'
              const input: Record<string, unknown> = {
                filePath,
                oldString: oldStr,
                newString: newStr,
                replaceAll,
              }

              const toolUseMsg = createAssistantToolUseMessage(provider, toolUseId, toolName, toolName, input)
              safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolUseMsg)) }, false)
              if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolUseMsg)

              const request = create(RequestPermissionRequestSchema, {
                toolName,
                inputJson: Buffer.from(JSON.stringify(input), 'utf8'),
                toolUseId,
                permissionSuggestions: [],
              })
              const call = create(ServerCallRequestSchema, {
                callId: `srv-${toolUseId}`,
                method: 'RequestPermission',
                params: { case: 'requestPermission', value: request },
              })

              const { promise, cancel } = requestClientCall(remotePeer, call)
              cancelStream = cancel
              currentStreamCancel = cancel

              promise
                .then(async (responseBytes) => {
                  if (cancelled) return

                  const response = fromBinary(ServerCallResponseSchema, responseBytes)
                  const perm = response.result.case === 'requestPermission' ? response.result.value : undefined
                  const approved = perm?.approved ?? false

                  if (!approved) {
                    const denyReason = perm?.denyReason ? `Denied: ${perm.denyReason}` : 'Denied'
                    const toolResultMsg = createUserToolResultMessage(provider, toolUseId, denyReason, true)
                    safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolResultMsg)) }, false)
                    if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolResultMsg)

                    if (currentStreamCancel === cancel) currentStreamCancel = undefined
                    safeOnComplete()
                    return
                  }

                  try {
                    const uri = toWorkspaceFileUri(filePath)
                    const canRollback = vscode.workspace.getWorkspaceFolder(uri) != null
                    const historyTs = Date.now()
                    const raw = await vscode.workspace.fs.readFile(uri)
                    const original = Buffer.from(raw).toString('utf8')
                    snapshotStore.save({ toolUseId, filePath: uri.fsPath, timestamp: historyTs, content: original })

                    let nextText = original
                    if (replaceAll) {
                      nextText = original.split(oldStr).join(newStr)
                    } else {
                      const idx = original.indexOf(oldStr)
                      if (idx < 0) throw new Error('oldString not found')
                      nextText = original.slice(0, idx) + newStr + original.slice(idx + oldStr.length)
                    }

                    await vscode.workspace.fs.writeFile(uri, Buffer.from(nextText, 'utf8'))

                    const output: string[] = []
                    output.push(`[jb:historyTs=${historyTs}]`)
                    output.push(`[jb:canRollback=${canRollback}]`)
                    output.push('')
                    output.push(`Edited File: \`${filePath}\``)
                    output.push(`Mode: ${replaceAll ? 'Replace All' : 'Replace First'}`)

                    const toolResultMsg = createUserToolResultMessage(provider, toolUseId, output.join('\n'), false)
                    safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolResultMsg)) }, false)
                    if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolResultMsg)
                  } catch (err) {
                    const toolResultMsg = createUserToolResultMessage(
                      provider,
                      toolUseId,
                      err instanceof Error ? err.message : String(err),
                      true
                    )
                    safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolResultMsg)) }, false)
                    if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolResultMsg)
                  } finally {
                    if (currentStreamCancel === cancel) currentStreamCancel = undefined
                    safeOnComplete()
                  }
                })
                .catch((err) => {
                  if (cancelled) return
                  if (err instanceof Error && err.message === 'client.call cancelled') {
                    safeOnComplete()
                    return
                  }
                  safeOnError(err instanceof Error ? err : new Error(String(err)))
                })

              break
            }

            if (trimmed.startsWith('/bash')) {
              const match = userMessage.match(/^\/bash\s+([\s\S]+)$/)
              if (!match) {
                const done = () => {
                  if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
                  safeOnComplete()
                }
                cancelStream = streamText(
                  { provider, sessionId: sessionId || 'unknown' },
                  '用法：/bash <command...>',
                  safeOnNext,
                  done
                )
                currentStreamCancel = cancelStream
                break
              }

              const command = match[1].trim()
              const cwd = getWorkspaceRoot()
              const toolUseId = crypto.randomUUID()
              const input: Record<string, unknown> = { command, cwd }

              const toolUseMsg = createAssistantToolUseMessage(provider, toolUseId, 'Bash', 'Bash', input)
              safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolUseMsg)) }, false)
              if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolUseMsg)

              const request = create(RequestPermissionRequestSchema, {
                toolName: 'Bash',
                inputJson: Buffer.from(JSON.stringify(input), 'utf8'),
                toolUseId,
                permissionSuggestions: [],
              })
              const call = create(ServerCallRequestSchema, {
                callId: `srv-${toolUseId}`,
                method: 'RequestPermission',
                params: { case: 'requestPermission', value: request },
              })

              const { promise, cancel } = requestClientCall(remotePeer, call)
              cancelStream = cancel
              currentStreamCancel = cancel

              promise
                .then((responseBytes) => {
                  if (cancelled) return

                  const response = fromBinary(ServerCallResponseSchema, responseBytes)
                  const perm = response.result.case === 'requestPermission' ? response.result.value : undefined
                  const approved = perm?.approved ?? false

                  if (!approved) {
                    const denyReason = perm?.denyReason ? `Denied: ${perm.denyReason}` : 'Denied'
                    const toolResultMsg = createUserToolResultMessage(provider, toolUseId, denyReason, true)
                    safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolResultMsg)) }, false)
                    if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolResultMsg)

                    if (currentStreamCancel === cancel) currentStreamCancel = undefined
                    safeOnComplete()
                    return
                  }

                   const child = spawn(command, { cwd: cwd || undefined, shell: true, windowsHide: true })
                   terminalTaskManager.recordTaskStart(sessionId || 'unknown', toolUseId, command)
                   let stdout = ''
                   let stderr = ''
                   let finished = false

                   const finish = (isError: boolean, contentValue: unknown) => {
                     if (finished) return
                     finished = true
                     terminalTaskManager.recordTaskComplete(toolUseId)
                     if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
                     cancelStream = undefined

                    const toolResultMsg = createUserToolResultMessage(provider, toolUseId, contentValue, isError)
                    safeOnNext({ data: Buffer.from(toBinary(RpcMessageSchema, toolResultMsg)) }, false)
                    if (sessionId) historyStore.appendMessage(sessionId, getWorkspaceRoot(), toolResultMsg)
                    safeOnComplete()
                  }

                  const kill = () => {
                    try {
                      child.kill()
                    } catch {
                      // ignore
                    }
                  }

                  const cancelRun = () => {
                    cancelled = true
                    kill()
                    finish(true, 'Cancelled')
                  }

                  cancelStream = cancelRun
                  currentStreamCancel = cancelRun

                  child.stdout?.on('data', (d) => {
                    stdout += d.toString()
                  })
                  child.stderr?.on('data', (d) => {
                    stderr += d.toString()
                  })

                  child.on('error', (err) => {
                    if (cancelled) return
                    finish(true, err.message)
                  })
                  child.on('close', (code) => {
                    if (cancelled) return
                    const isError = code !== 0
                    const contentValue = isError ? stderr || stdout || `exit code: ${code}` : stdout
                    finish(isError, contentValue)
                  })
                })
                .catch((err) => {
                  if (cancelled) return
                  if (err instanceof Error && err.message === 'client.call cancelled') {
                    safeOnComplete()
                    return
                  }
                  safeOnError(err instanceof Error ? err : new Error(String(err)))
                })

              break
            }

            startProviderQueryStream(userMessage)
            break
          }
          case 'agent.queryWithContent': {
            const req = data.length > 0 ? fromBinary(QueryWithContentRequestSchema, data) : undefined
            const blocks = req?.content ?? []
            const extracted = blocks
              .filter((b) => b?.block?.case === 'text' && typeof (b as any).block?.value?.text === 'string')
              .map((b: any) => b.block.value.text)
              .join('')
            const userMessage = extracted || `（rich content blocks=${blocks.length}）`
            currentStreamCancel?.()
            currentStreamCancel = undefined

            if (sessionId) {
              historyStore.appendMessage(sessionId, getWorkspaceRoot(), createUserTextMessage(provider, userMessage))
            }

            startProviderQueryStream(userMessage)
            break
          }
          case 'agent.events': {
            // 全局事件流：必须保持长连接，不可立即 complete（前端会订阅并期望持续存在）。
            // 这里订阅的是“连接级别”的全局事件（JetBrains 版同样如此）。
            globalEventSubscribers.add(responderStream)
            cancelStream = () => {
              globalEventSubscribers.delete(responderStream)
            }
            break
          }
          default: {
            safeOnError(new Error(`Unsupported route: ${route}`))
          }
        }
      } catch (error) {
        safeOnError(error instanceof Error ? error : new Error(String(error)))
      }

      return {
        request: (_n: number) => {},
        cancel: () => {
          cancelled = true
          cancelStream?.()
          currentStreamCancel?.()
          if (currentStreamCancel === cancelStream) currentStreamCancel = undefined
        },
        onExtension: () => {},
      }
    },
  }
}

function streamText(
  ctx: { provider: Provider; sessionId: string },
  text: string,
  onNext: (payload: Payload, isComplete: boolean) => void,
  onComplete: () => void,
  onRpcMessage?: (message: RpcMessage) => void
): () => void {
  const startedAt = Date.now()
  const uuid = crypto.randomUUID()
  const chunkSize = 240
  const delayMs = 30

  let offset = 0
  let stopped = false
  let timer: NodeJS.Timeout | undefined
  let didStart = false

  const emit = (event: any) => {
    const streamEvent = create(StreamEventSchema, {
      uuid,
      sessionId: ctx.sessionId,
      event: create(StreamEventDataSchema, { event }),
    })

    const msg = create(RpcMessageSchema, {
      provider: ctx.provider,
      message: { case: 'streamEvent', value: streamEvent },
    } as any)
    onRpcMessage?.(msg)

    onNext({ data: Buffer.from(toBinary(RpcMessageSchema, msg)) }, false)
  }

  const ensureStarted = () => {
    if (didStart) return
    didStart = true

    const info = create(MessageStartInfoSchema, { id: uuid, content: [] })
    emit({ case: 'messageStart', value: create(MessageStartEventSchema, { messageInfo: info }) })

    const block = create(ContentBlockSchema, { block: { case: 'text', value: create(TextBlockSchema, { text: '' }) } })
    emit({ case: 'contentBlockStart', value: create(ContentBlockStartEventSchema, { index: 0, contentBlock: block }) })
  }

  const sendTextDelta = (chunk: string) => {
    ensureStarted()
    emit({
      case: 'contentBlockDelta',
      value: create(ContentBlockDeltaEventSchema, {
        index: 0,
        delta: create(DeltaSchema, {
          delta: {
            case: 'textDelta',
            value: create(TextDeltaSchema, { text: chunk }),
          },
        }),
      }),
    })
  }

  const sendStop = () => {
    ensureStarted()
    emit({ case: 'contentBlockStop', value: create(ContentBlockStopEventSchema, { index: 0 }) })
    emit({ case: 'messageStop', value: create(MessageStopEventSchema, {}) })
  }

  const sendResult = (subtype: string, isError: boolean, resultText?: string) => {
    const durationMs = BigInt(Math.max(0, Date.now() - startedAt))
    const result = create(ResultMessageSchema, {
      subtype,
      durationMs,
      isError,
      numTurns: 1,
      sessionId: ctx.sessionId,
      result: resultText || undefined,
    })

    const msg = create(RpcMessageSchema, { provider: ctx.provider, message: { case: 'result', value: result } } as any)
    onRpcMessage?.(msg)
    onNext({ data: Buffer.from(toBinary(RpcMessageSchema, msg)) }, false)
  }

  const finish = () => {
    if (stopped) return
    stopped = true
    if (timer) clearTimeout(timer)
    sendStop()
    sendResult('success', false)
    onComplete()
  }

  const pump = () => {
    if (stopped) return
    if (offset >= text.length) {
      finish()
      return
    }
    const chunk = text.slice(offset, offset + chunkSize)
    offset += chunkSize
    sendTextDelta(chunk)
    timer = setTimeout(pump, delayMs)
  }

  pump()
  return finish
}

function createUserTextMessage(provider: Provider, text: string): RpcMessage {
  const block = create(ContentBlockSchema, { block: { case: 'text', value: create(TextBlockSchema, { text }) } })
  const content = create(MessageContentSchema, { content: [block] })
  const user = create(UserMessageSchema, { message: content, uuid: crypto.randomUUID() })
  return create(RpcMessageSchema, { provider, message: { case: 'user', value: user } } as any)
}

function createAssistantToolUseMessage(
  provider: Provider,
  toolUseId: string,
  toolName: string,
  toolType: string,
  input: Record<string, unknown>
): RpcMessage {
  const toolUse = create(ToolUseBlockSchema, {
    id: toolUseId,
    toolName,
    toolType,
    inputJson: Buffer.from(JSON.stringify(input), 'utf8'),
    status: ContentStatus.IN_PROGRESS,
  })
  const block = create(ContentBlockSchema, { block: { case: 'toolUse', value: toolUse } })
  const content = create(MessageContentSchema, { content: [block] })
  const assistant = create(AssistantMessageSchema, { message: content, uuid: crypto.randomUUID() })
  return create(RpcMessageSchema, { provider, message: { case: 'assistant', value: assistant } } as any)
}

function createUserToolResultMessage(
  provider: Provider,
  toolUseId: string,
  contentValue: unknown,
  isError: boolean
): RpcMessage {
  const toolResult = create(ToolResultBlockSchema, {
    toolUseId,
    contentJson: Buffer.from(JSON.stringify(contentValue ?? ''), 'utf8'),
    isError,
  })
  const block = create(ContentBlockSchema, { block: { case: 'toolResult', value: toolResult } })
  const content = create(MessageContentSchema, { content: [block] })
  const user = create(UserMessageSchema, { message: content, uuid: crypto.randomUUID() })
  return create(RpcMessageSchema, { provider, message: { case: 'user', value: user } } as any)
}

function extractRoute(payload: Payload): string {
  if (!payload.metadata) return ''
  const metadata = new Uint8Array(payload.metadata)
  if (metadata.length === 0) return ''
  const len = metadata[0] ?? 0
  return Buffer.from(metadata.slice(1, 1 + len)).toString('utf8')
}

function toWorkspaceFileUri(filePath: string): vscode.Uri {
  if (/^file:/i.test(filePath)) {
    return vscode.Uri.parse(filePath)
  }

  if (path.isAbsolute(filePath)) {
    return vscode.Uri.file(filePath)
  }

  const folders = vscode.workspace.workspaceFolders ?? []
  if (folders.length === 0) return vscode.Uri.file(filePath)

  if (folders.length === 1) {
    return vscode.Uri.file(path.join(folders[0].uri.fsPath, filePath))
  }

  // Multi-root: allow "<workspaceFolderName>/<relativePath>" to disambiguate
  const normalized = filePath.replace(/\\/g, '/')
  const parts = normalized.split('/').filter(Boolean)
  if (parts.length >= 2) {
    const folderName = parts[0]
    const folder = folders.find((f) => f.name === folderName)
    if (folder) {
      return vscode.Uri.file(path.join(folder.uri.fsPath, ...parts.slice(1)))
    }
  }

  // Fallback: resolve against the first workspace folder
  return vscode.Uri.file(path.join(folders[0].uri.fsPath, filePath))
}

function encodeRoute(route: string): Buffer {
  const routeBytes = Buffer.from(route, 'utf8')
  const metadata = Buffer.alloc(1 + routeBytes.length)
  metadata[0] = routeBytes.length
  routeBytes.copy(metadata, 1)
  return metadata
}

function requestClientCall(
  remotePeer: RSocket,
  request: any,
  timeoutMs: number = 5 * 60 * 1000
): { promise: Promise<Uint8Array>; cancel: () => void } {
  const payload: Payload = {
    data: Buffer.from(toBinary(ServerCallRequestSchema, request)),
    metadata: encodeRoute('client.call'),
  }

  let settled = false
  let timer: NodeJS.Timeout | undefined
  let cancel: () => void = () => {}

  const promise = new Promise<Uint8Array>((resolve, reject) => {
    const finish = (fn: () => void) => {
      if (settled) return
      settled = true
      if (timer) clearTimeout(timer)
      fn()
    }

    if (timeoutMs > 0) {
      timer = setTimeout(() => {
        finish(() => reject(new Error(`client.call timeout: ${timeoutMs}ms`)))
      }, timeoutMs)
    }

    const stream = remotePeer.requestResponse(payload, {
      onNext: (p) => {
        const bytes = p.data ? new Uint8Array(p.data) : new Uint8Array()
        finish(() => resolve(bytes))
      },
      onComplete: () => {
        finish(() => resolve(new Uint8Array()))
      },
      onError: (err) => {
        finish(() => reject(err))
      },
      onExtension: () => {},
    })

    cancel = () => {
      finish(() => {
        try {
          stream.cancel()
        } catch {
          // ignore
        }
        reject(new Error('client.call cancelled'))
      })
    }
  })

  return { promise, cancel }
}

function encodeStatus(status: SessionStatus): Uint8Array {
  const msg = create(StatusResultSchema, { status })
  return toBinary(StatusResultSchema, msg)
}

function toClaudePermissionMode(mode: PermissionMode): 'default' | 'acceptEdits' | 'plan' | 'bypassPermissions' {
  switch (mode) {
    case PermissionMode.BYPASS_PERMISSIONS:
      return 'bypassPermissions'
    case PermissionMode.ACCEPT_EDITS:
      return 'acceptEdits'
    case PermissionMode.PLAN:
      return 'plan'
    case PermissionMode.DEFAULT:
    default:
      return 'default'
  }
}

function createCancellable(): { cancel(): void; onExtension(): void } {
  return { cancel: () => {}, onExtension: () => {} }
}

/**
 * 将 NormalizedStreamEvent 转换为 RpcMessage
 * 用于 Codex 事件流处理
 */
function convertNormalizedEventToRpcMessage(
  event: import('../../sdk/codex/adapter/streamAdapter').NormalizedStreamEvent,
  provider: Provider,
  sessionId: string
): RpcMessage | null {
  const streamUuid = crypto.randomUUID()

  const emitStreamEvent = (eventData: any): RpcMessage => {
    const streamEvent = create(StreamEventSchema, {
      uuid: streamUuid,
      sessionId,
      event: create(StreamEventDataSchema, { event: eventData }),
    })
    return create(RpcMessageSchema, {
      provider,
      message: { case: 'streamEvent', value: streamEvent },
    } as any)
  }

  switch (event.type) {
    case 'messageStarted': {
      const info = create(MessageStartInfoSchema, {
        id: event.messageId,
        model: 'codex',
        content: [],
      })
      return emitStreamEvent({
        case: 'messageStart',
        value: create(MessageStartEventSchema, { messageInfo: info }),
      })
    }

    case 'turnStarted':
      // turnStarted 不需要单独的 RPC 消息
      return null

    case 'contentStarted': {
      let block
      if (event.contentType === 'thinking') {
        block = create(ContentBlockSchema, {
          block: { case: 'thinking', value: create(ThinkingBlockSchema, { thinking: '', signature: '' }) },
        })
      } else if (event.contentType === 'tool_use' && event.content) {
        const toolContent = event.content as { id?: string; name?: string; input?: unknown }
        block = create(ContentBlockSchema, {
          block: {
            case: 'toolUse',
            value: create(ToolUseBlockSchema, {
              id: toolContent.id || '',
              toolName: toolContent.name || event.toolName || '',
              toolType: toolContent.name || event.toolName || '',
              inputJson: Buffer.from(JSON.stringify(toolContent.input ?? {}), 'utf8'),
              status: ContentStatus.IN_PROGRESS,
            }),
          },
        })
      } else {
        block = create(ContentBlockSchema, {
          block: { case: 'text', value: create(TextBlockSchema, { text: '' }) },
        })
      }
      return emitStreamEvent({
        case: 'contentBlockStart',
        value: create(ContentBlockStartEventSchema, { index: event.index, contentBlock: block }),
      })
    }

    case 'contentDelta': {
      let delta
      if (event.delta.type === 'thinking' && event.delta.thinking) {
        delta = create(DeltaSchema, {
          delta: { case: 'thinkingDelta', value: create(ThinkingDeltaSchema, { thinking: event.delta.thinking }) },
        })
      } else if (event.delta.type === 'text' && event.delta.text) {
        delta = create(DeltaSchema, {
          delta: { case: 'textDelta', value: create(TextDeltaSchema, { text: event.delta.text }) },
        })
      } else {
        return null
      }
      return emitStreamEvent({
        case: 'contentBlockDelta',
        value: create(ContentBlockDeltaEventSchema, { index: event.index, delta }),
      })
    }

    case 'contentCompleted': {
      return emitStreamEvent({
        case: 'contentBlockStop',
        value: create(ContentBlockStopEventSchema, { index: event.index }),
      })
    }

    case 'turnCompleted': {
      return emitStreamEvent({
        case: 'messageStop',
        value: create(MessageStopEventSchema, {}),
      })
    }

    case 'turnFailed': {
      // 错误时也发送 messageStop
      return emitStreamEvent({
        case: 'messageStop',
        value: create(MessageStopEventSchema, {}),
      })
    }

    case 'assistantMessage': {
      // 构建完整的 AssistantMessage
      const contentBlocks = (event.content as any[]).map((block) => {
        if (block.type === 'text') {
          return create(MessageContentSchema, {
            content: { case: 'text', value: create(TextBlockSchema, { text: block.text || '' }) },
          })
        } else if (block.type === 'thinking') {
          return create(MessageContentSchema, {
            content: {
              case: 'thinking',
              value: create(ThinkingBlockSchema, { thinking: block.thinking || '', signature: '' }),
            },
          })
        } else if (block.type === 'tool_use') {
          return create(MessageContentSchema, {
            content: {
              case: 'toolUse',
              value: create(ToolUseBlockSchema, {
                id: block.id || '',
                toolName: block.name || '',
                toolType: block.name || '',
                inputJson: Buffer.from(JSON.stringify(block.input ?? {}), 'utf8'),
                status: ContentStatus.IN_PROGRESS,
              }),
            },
          })
        } else if (block.type === 'tool_result') {
          return create(MessageContentSchema, {
            content: {
              case: 'toolResult',
              value: create(ToolResultBlockSchema, {
                toolUseId: block.tool_use_id || '',
                contentJson: Buffer.from(JSON.stringify(block.content ?? ''), 'utf8'),
                isError: block.is_error || false,
              }),
            },
          })
        }
        // 默认返回文本块
        return create(MessageContentSchema, {
          content: { case: 'text', value: create(TextBlockSchema, { text: '' }) },
        })
      })

      const assistantMsg = create(AssistantMessageSchema, {
        id: event.id || '',
        model: 'codex',
        content: contentBlocks,
        stopReason: 'end_turn',
      })

      return create(RpcMessageSchema, {
        provider,
        message: { case: 'assistantMessage', value: assistantMsg },
      } as any)
    }

    case 'resultSummary': {
      const result = create(ResultMessageSchema, {
        subtype: event.subtype,
        costUsd: 0,
        durationMs: event.durationMs,
        durationApiMs: event.durationMs,
        isError: event.isError,
        turnCount: 1,
        sessionId: event.sessionId,
        result: event.result || '',
      })

      return create(RpcMessageSchema, {
        provider,
        message: { case: 'result', value: result },
      } as any)
    }

    default:
      return null
  }
}
