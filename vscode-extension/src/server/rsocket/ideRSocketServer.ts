import * as crypto from 'crypto'
import * as path from 'path'
import * as vscode from 'vscode'

import { create, fromBinary, toBinary } from '@bufbuild/protobuf'
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

type WsServerCtor = new (...args: any[]) => any

function resolveWsServerCtor(): WsServerCtor {
  // Keep the `ws` instance aligned with rsocket-websocket-server to avoid stream compatibility issues.
  try {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    return require('rsocket-websocket-server/node_modules/ws').Server
  } catch {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    return require('ws').Server
  }
}

const WsServer: WsServerCtor = resolveWsServerCtor()

import type { DiffContentProvider } from '../../ide/diffContentProvider'
import { getIdeTheme } from '../apiHandlers'
import type { SnapshotStore } from '../rollback/snapshotStore'
import type { TerminalTaskManager, TerminalTaskUpdate } from '../terminal/terminalTaskManager'
import type { WsUpgradeRouter } from '../wsUpgradeRouter'
import { isAllowedWebviewOrigin } from '../webviewOrigin'

import {
  ActiveFileChangedNotifySchema,
  GetIdeSettingsResponseSchema,
  IdeSettingsSchema,
  IdeSettingsChangedNotifySchema,
  ServerCallRequestSchema,
  TerminalTaskAction,
  TerminalTaskUpdateNotifySchema,
  ThemeChangedNotifySchema,
  type ServerCallRequest,
} from '@proto'
import {
  IdeThemeProtoSchema,
  JetBrainsBatchRollbackEventSchema,
  JetBrainsBatchRollbackRequestSchema,
  JetBrainsBackgroundableTerminalSchema,
  JetBrainsGetBackgroundableTerminalsResponseSchema,
  JetBrainsGetFileHistoryContentRequestSchema,
  JetBrainsGetFileHistoryContentResponseSchema,
  JetBrainsGetLocaleResponseSchema,
  JetBrainsGetOriginalContentResponseSchema,
  JetBrainsGetProjectPathResponseSchema,
  JetBrainsGetThemeResponseSchema,
  JetBrainsOpenFileRequestSchema,
  JetBrainsOperationResponseSchema,
  JetBrainsRollbackFileRequestSchema,
  JetBrainsRollbackFileResponseSchema,
  JetBrainsSetLocaleRequestSchema,
  JetBrainsShowDiffRequestSchema,
  JetBrainsShowEditFullDiffRequestSchema,
  JetBrainsShowEditPreviewRequestSchema,
  JetBrainsShowMarkdownRequestSchema,
  JetBrainsShowMultiEditDiffRequestSchema,
  JetBrainsTerminalBackgroundEventSchema,
  JetBrainsTerminalBackgroundRequestSchema,
  RollbackStatus,
  TerminalBackgroundStatus,
} from '@proto'

type Responder = Partial<RSocket>

export class IdeRSocketServer implements vscode.Disposable {
  private closeables: Array<{ close(error?: Error): void }> = []
  private readonly peers = new Set<RSocket>()
  private readonly disposables: vscode.Disposable[] = []
  private lastActiveFileSignature: string | undefined
  private activeFileNotifyTimer: NodeJS.Timeout | undefined

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly token: string,
    private readonly deps: { diffProvider?: DiffContentProvider },
    private readonly snapshotStore: SnapshotStore,
    private readonly terminalTaskManager: TerminalTaskManager,
    private readonly wsUpgradeRouter: WsUpgradeRouter,
    private readonly log?: (message: string) => void
  ) {}

  async start(): Promise<void> {
    if (this.closeables.length > 0) return

    const createTransport = (wsPath: string) =>
      new WebsocketServerTransport({
        wsCreator: () =>
          (() => {
            const wss = new WsServer({
              noServer: true,
              path: wsPath,
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
                    this.log?.(`[ide-rsocket/ws] reject: non-loopback remote=${String(remote)} path=${wsPath}`)
                    return done(false, 401, 'Unauthorized')
                  }

                  const origin = req?.headers?.origin
                  if (typeof origin === 'string' && !isAllowedWebviewOrigin(origin)) {
                    this.log?.(`[ide-rsocket/ws] reject: origin=${origin} path=${wsPath}`)
                    return done(false, 401, 'Unauthorized')
                  }

                  const url = new URL(String(req?.url ?? ''), 'http://127.0.0.1')
                  const token = url.searchParams.get('token')
                  if (!token || token !== this.token) {
                    this.log?.(
                      `[ide-rsocket/ws] reject: bad token url=${String(req?.url ?? '')} token=${String(token)} path=${wsPath}`
                    )
                    return done(false, 401, 'Unauthorized')
                  }

                  this.log?.(
                    `[ide-rsocket/ws] accept url=${String(req?.url ?? '')} origin=${String(origin ?? '')} remote=${String(remote)} path=${wsPath}`
                  )
                  return done(true)
                } catch (_error) {
                  this.log?.(`[ide-rsocket/ws] reject: exception path=${wsPath}`)
                  return done(false, 401, 'Unauthorized')
                }
              },
            } as any)

            this.wsUpgradeRouter.register(wsPath, wss as any)

            wss.on('connection', (socket: any, req: any) => {
              try {
                if (socket?._socket) (socket._socket as any).__ccp_isWebSocket = true
              } catch {
                // ignore
              }
              const remote = req?.socket?.remoteAddress
              const origin = req?.headers?.origin
              this.log?.(
                `[ide-rsocket/ws] connection url=${String(req?.url ?? '')} origin=${String(origin ?? '')} remote=${String(remote)} path=${wsPath}`
              )

              socket.on('message', (data: any, isBinary: boolean) => {
                const bytes = Buffer.isBuffer(data) ? data.length : typeof data?.byteLength === 'number' ? data.byteLength : 0
                const head = Buffer.isBuffer(data) ? data.subarray(0, 12).toString('hex') : ''
                this.log?.(
                  `[ide-rsocket/ws] message isBinary=${String(isBinary)} bytes=${String(bytes)} head=${head} path=${wsPath}`
                )
              })
              socket.on('close', (code: number, reason: Buffer) => {
                const text = reason ? reason.toString('utf8') : ''
                this.log?.(`[ide-rsocket/ws] close code=${String(code)} reason=${JSON.stringify(text)} path=${wsPath}`)
              })
              socket.on('error', (err: Error) => {
                this.log?.(`[ide-rsocket/ws] socket error: ${(err && (err.stack || err.message)) || String(err)} path=${wsPath}`)
              })
            })
            wss.on('error', (err: Error) => {
              this.log?.(`[ide-rsocket/ws] wss error: ${(err && (err.stack || err.message)) || String(err)} path=${wsPath}`)
            })

            // rsocket-websocket-server waits for the ws server "listening" event.
            // When ws.Server is attached to an existing http.Server, it doesn't emit it,
            // so we emit it manually once the event listeners are registered.
            setImmediate(() => (wss as any).emit('listening'))
            return wss
          })(),
      })

    const bindServer = async (wsPath: string) => {
      const server = new RSocketServer({
        transport: createTransport(wsPath),
        acceptor: {
          accept: async (setupPayload: SetupPayload, remotePeer: RSocket): Promise<Responder> => {
            this.peers.add(remotePeer)
            return createResponder(
              this.context,
              setupPayload,
              remotePeer,
              this.deps,
              this.snapshotStore,
              this.terminalTaskManager
            )
          },
        },
      })

      this.closeables.push(await server.bind())
    }

    await bindServer('/ide-rsocket')
    this.registerIdeEventForwarders()
  }

  dispose() {
    for (const c of this.closeables.splice(0, this.closeables.length)) {
      try {
        c.close()
      } catch {
        // ignore
      }
    }
    this.peers.clear()
    for (const d of this.disposables.splice(0, this.disposables.length)) {
      try {
        d.dispose()
      } catch {
        // ignore
      }
    }
  }

  private registerIdeEventForwarders(): void {
    if (this.disposables.length > 0) return

    const safeNotify = (fn: () => void) => {
      try {
        fn()
      } catch (err) {
        console.warn('[IdeRSocketServer] notify failed:', err)
      }
    }

    // Theme changes (including editor font settings that affect theme payload)
    this.disposables.push(
      vscode.window.onDidChangeActiveColorTheme(() => safeNotify(() => this.notifyThemeChanged())),
      vscode.workspace.onDidChangeConfiguration((e) => {
        if (e.affectsConfiguration('editor.fontFamily') || e.affectsConfiguration('editor.fontSize')) {
          safeNotify(() => this.notifyThemeChanged())
        }
        if (e.affectsConfiguration('claudeCodePlus')) {
          safeNotify(() => this.notifySettingsChanged())
        }
      })
    )

    // Active file changes
    this.disposables.push(
      vscode.window.onDidChangeActiveTextEditor(() => safeNotify(() => this.scheduleActiveFileNotify(0))),
      vscode.window.onDidChangeTextEditorSelection(() => safeNotify(() => this.scheduleActiveFileNotify(120)))
    )

    // Terminal task updates (bash tasks tracked in extension host)
    this.disposables.push(
      this.terminalTaskManager.onDidUpdate((update) => safeNotify(() => this.notifyTerminalTaskUpdate(update)))
    )
  }

  private notifyThemeChanged(): void {
    const theme = getIdeTheme()
    const notify = create(ThemeChangedNotifySchema, {
      background: theme.background,
      foreground: theme.foreground,
      borderColor: theme.borderColor,
      panelBackground: theme.panelBackground,
      textFieldBackground: theme.textFieldBackground,
      selectionBackground: theme.selectionBackground,
      selectionForeground: theme.selectionForeground,
      linkColor: theme.linkColor,
      errorColor: theme.errorColor,
      warningColor: theme.warningColor,
      successColor: theme.successColor,
      separatorColor: theme.separatorColor,
      hoverBackground: theme.hoverBackground,
      accentColor: theme.accentColor,
      infoBackground: theme.infoBackground,
      codeBackground: theme.codeBackground,
      secondaryForeground: theme.secondaryForeground,
      fontFamily: String(theme.fontFamily ?? 'system-ui'),
      fontSize: Number(theme.fontSize ?? 14),
      editorFontFamily: String(theme.editorFontFamily ?? 'monospace'),
      editorFontSize: Number(theme.editorFontSize ?? 14),
    })

    this.broadcastServerCall('onThemeChanged', { case: 'themeChanged', value: notify })
  }

  private notifySettingsChanged(): void {
    const settings = buildIdeSettings(this.context)
    const notify = create(IdeSettingsChangedNotifySchema, { settings })
    this.broadcastServerCall('onSettingsChanged', { case: 'settingsChanged', value: notify })
  }

  private notifyActiveFileChanged(): void {
    const notify = buildActiveFileNotify()
    const signature = this.computeActiveFileSignature(notify)
    if (signature === this.lastActiveFileSignature) return
    this.lastActiveFileSignature = signature
    this.broadcastServerCall('onActiveFileChanged', { case: 'activeFileChanged', value: notify })
  }

  private scheduleActiveFileNotify(delayMs: number): void {
    if (this.activeFileNotifyTimer) clearTimeout(this.activeFileNotifyTimer)
    this.activeFileNotifyTimer = setTimeout(() => {
      this.activeFileNotifyTimer = undefined
      try {
        this.notifyActiveFileChanged()
      } catch (err) {
        console.warn('[IdeRSocketServer] notifyActiveFileChanged failed:', err)
      }
    }, Math.max(0, delayMs))
  }

  private computeActiveFileSignature(notify: any): string {
    if (!notify?.hasActiveFile) return 'none'
    return [
      notify.path ?? '',
      notify.line ?? '',
      notify.column ?? '',
      notify.hasSelection ? '1' : '0',
      notify.startLine ?? '',
      notify.startColumn ?? '',
      notify.endLine ?? '',
      notify.endColumn ?? '',
    ].join('|')
  }

  private notifyTerminalTaskUpdate(update: TerminalTaskUpdate): void {
    const action =
      update.action === 'completed'
        ? TerminalTaskAction.TERMINAL_TASK_COMPLETED
        : update.action === 'backgrounded'
          ? TerminalTaskAction.TERMINAL_TASK_BACKGROUNDED
          : TerminalTaskAction.TERMINAL_TASK_STARTED

    const notify = create(TerminalTaskUpdateNotifySchema, {
      toolUseId: update.toolUseId,
      sessionId: update.sessionId,
      action,
      command: update.command,
      isBackground: update.isBackground,
      startTime: BigInt(update.startTime),
      elapsedMs: update.elapsedMs !== undefined ? BigInt(update.elapsedMs) : undefined,
    })

    this.broadcastServerCall('onTerminalTaskUpdate', { case: 'terminalTaskUpdate', value: notify })
  }

  private broadcastServerCall(
    method: string,
    params: ServerCallRequest['params']
  ): void {
    if (this.peers.size === 0) return

    const callId = `notify-${crypto.randomUUID()}`
    const request = create(ServerCallRequestSchema, { callId, method, params })
    const bytes = toBinary(ServerCallRequestSchema, request)

    const payload: Payload = { data: Buffer.from(bytes), metadata: encodeRoute('client.call') }

    for (const peer of Array.from(this.peers)) {
      try {
        peer.fireAndForget(payload, {
          onComplete: () => {},
          onError: (_err) => {
            // Client may be gone; best effort cleanup.
            this.peers.delete(peer)
          },
        })
      } catch {
        this.peers.delete(peer)
      }
    }
  }
}


function createResponder(
  context: vscode.ExtensionContext,
  _setup: SetupPayload,
  _remotePeer: RSocket,
  deps: { diffProvider?: DiffContentProvider },
  snapshotStore: SnapshotStore,
  terminalTaskManager: TerminalTaskManager
): Responder {
  const getWorkspaceRoot = (): string => vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? ''

  const safeOperationResponse = (success: boolean, error?: string): Uint8Array => {
    const msg = create(JetBrainsOperationResponseSchema, { success, error: error || undefined })
    return toBinary(JetBrainsOperationResponseSchema, msg)
  }

  const handleRequestResponse = async (payload: Payload): Promise<Uint8Array> => {
    const route = extractRoute(payload)
    const data = payload.data ? new Uint8Array(payload.data) : new Uint8Array()

    switch (route) {
      case 'ide.getTheme': {
        const theme = getIdeTheme()
        const protoTheme = create(IdeThemeProtoSchema, {
          background: theme.background,
          foreground: theme.foreground,
          borderColor: theme.borderColor,
          panelBackground: theme.panelBackground,
          textFieldBackground: theme.textFieldBackground,
          selectionBackground: theme.selectionBackground,
          selectionForeground: theme.selectionForeground,
          linkColor: theme.linkColor,
          errorColor: theme.errorColor,
          warningColor: theme.warningColor,
          successColor: theme.successColor,
          separatorColor: theme.separatorColor,
          hoverBackground: theme.hoverBackground,
          accentColor: theme.accentColor,
          infoBackground: theme.infoBackground,
          codeBackground: theme.codeBackground,
          secondaryForeground: theme.secondaryForeground,
          fontFamily: String(theme.fontFamily ?? ''),
          fontSize: Number(theme.fontSize ?? 14),
          editorFontFamily: String(theme.editorFontFamily ?? ''),
          editorFontSize: Number(theme.editorFontSize ?? 14),
        })
        const resp = create(JetBrainsGetThemeResponseSchema, { theme: protoTheme })
        return toBinary(JetBrainsGetThemeResponseSchema, resp)
      }

      case 'ide.getSettings': {
        const ideSettings = buildIdeSettings(context)
        const resp = create(GetIdeSettingsResponseSchema, { settings: ideSettings })
        return toBinary(GetIdeSettingsResponseSchema, resp)
      }

      case 'ide.getLocale': {
        const locale = (context.globalState.get<string>('claudeCodePlus.locale') || vscode.env.language || 'en-US').trim()
        const resp = create(JetBrainsGetLocaleResponseSchema, { locale })
        return toBinary(JetBrainsGetLocaleResponseSchema, resp)
      }

      case 'ide.setLocale': {
        const req = fromBinary(JetBrainsSetLocaleRequestSchema, data)
        const locale = String(req.locale || '').trim()
        void context.globalState.update('claudeCodePlus.locale', locale)
        return safeOperationResponse(true)
      }

      case 'ide.getProjectPath': {
        const resp = create(JetBrainsGetProjectPathResponseSchema, { projectPath: getWorkspaceRoot() })
        return toBinary(JetBrainsGetProjectPathResponseSchema, resp)
      }

      case 'ide.reportSessionState': {
        // The frontend periodically reports tab/session state; VS Code doesn't consume it (yet).
        return safeOperationResponse(true)
      }

      case 'ide.openFile': {
        const req = fromBinary(JetBrainsOpenFileRequestSchema, data)
        await openFile(req.filePath, req.line, req.column, req.startOffset, req.endOffset)
        return safeOperationResponse(true)
      }

      case 'ide.showDiff': {
        const req = fromBinary(JetBrainsShowDiffRequestSchema, data)
        await showDiff(deps.diffProvider, req.filePath, req.oldContent, req.newContent, req.title)
        return safeOperationResponse(true)
      }

      case 'ide.showMultiEditDiff': {
        const req = fromBinary(JetBrainsShowMultiEditDiffRequestSchema, data)
        const current = req.currentContent ?? (await readFileText(req.filePath))
        const next = applyEdits(current, req.edits)
        await showDiff(deps.diffProvider, req.filePath, current, next, `MultiEdit: ${path.basename(req.filePath)}`)
        return safeOperationResponse(true)
      }

      case 'ide.showEditPreviewDiff': {
        const req = fromBinary(JetBrainsShowEditPreviewRequestSchema, data)
        const current = await readFileText(req.filePath)
        const next = applyEdits(current, req.edits)
        await showDiff(deps.diffProvider, req.filePath, current, next, req.title || `Preview: ${path.basename(req.filePath)}`)
        return safeOperationResponse(true)
      }

      case 'ide.showEditFullDiff': {
        const req = fromBinary(JetBrainsShowEditFullDiffRequestSchema, data)
        const before = req.originalContent ?? ''
        const after = await tryReadFileText(req.filePath, before, req.oldString, req.newString, req.replaceAll)
        await showDiff(deps.diffProvider, req.filePath, before, after, req.title || `Edit: ${path.basename(req.filePath)}`)
        return safeOperationResponse(true)
      }

      case 'ide.showMarkdown': {
        const req = fromBinary(JetBrainsShowMarkdownRequestSchema, data)
        await showMarkdown(req.content, req.title)
        return safeOperationResponse(true)
      }

      case 'ide.getActiveFile': {
        const notify = buildActiveFileNotify()
        return toBinary(ActiveFileChangedNotifySchema, notify)
      }

      case 'ide.getOriginalContent': {
        const toolUseId = Buffer.from(data).toString('utf8')
        const content = snapshotStore.getOriginalContent(toolUseId)
        const resp = create(JetBrainsGetOriginalContentResponseSchema, {
          success: true,
          found: content !== undefined,
          content,
        })
        return toBinary(JetBrainsGetOriginalContentResponseSchema, resp)
      }

      case 'ide.getFileHistoryContent': {
        const req = fromBinary(JetBrainsGetFileHistoryContentRequestSchema, data)
        const uri = toWorkspaceFileUri(req.filePath)
        const snap = snapshotStore.getSnapshotBefore(uri.fsPath, Number(req.beforeTimestamp))
        const resp = create(JetBrainsGetFileHistoryContentResponseSchema, {
          success: true,
          found: !!snap,
          content: snap?.content,
        })
        return toBinary(JetBrainsGetFileHistoryContentResponseSchema, resp)
      }

      case 'ide.rollbackFile': {
        const req = fromBinary(JetBrainsRollbackFileRequestSchema, data)
        const uri = toWorkspaceFileUri(req.filePath)
        const ts = Number(req.beforeTimestamp)

        if (ts === 0) {
          await vscode.workspace.fs.delete(uri, { useTrash: false })
          const resp = create(JetBrainsRollbackFileResponseSchema, { success: true })
          return toBinary(JetBrainsRollbackFileResponseSchema, resp)
        }

        const snap = snapshotStore.getSnapshotBefore(uri.fsPath, ts)
        if (!snap) {
          const resp = create(JetBrainsRollbackFileResponseSchema, { success: false, error: 'Snapshot not found' })
          return toBinary(JetBrainsRollbackFileResponseSchema, resp)
        }

        await ensureParentDir(uri.fsPath)
        await vscode.workspace.fs.writeFile(uri, Buffer.from(snap.content, 'utf8'))
        const resp = create(JetBrainsRollbackFileResponseSchema, { success: true })
        return toBinary(JetBrainsRollbackFileResponseSchema, resp)
      }

      case 'ide.getBackgroundableTerminals': {
        const tasks = terminalTaskManager.getBackgroundableTasks()
        const terminals = tasks.map((t) =>
          create(JetBrainsBackgroundableTerminalSchema, {
            sessionId: t.sessionId,
            toolUseId: t.toolUseId,
            command: t.command,
            startTime: BigInt(t.startTime),
            elapsedMs: BigInt(t.elapsedMs),
          })
        )
        const resp = create(JetBrainsGetBackgroundableTerminalsResponseSchema, { success: true, terminals })
        return toBinary(JetBrainsGetBackgroundableTerminalsResponseSchema, resp)
      }

      default:
        throw new Error(`Unsupported route: ${route}`)
    }
  }

  const handleRequestStream = async (
    route: string,
    data: Uint8Array,
    onNext: (bytes: Uint8Array) => void,
    isCancelled: () => boolean
  ) => {
    if (route === 'ide.batchRollback') {
      const req = fromBinary(JetBrainsBatchRollbackRequestSchema, data)

      for (const item of req.items) {
        if (isCancelled()) return
        const started = create(JetBrainsBatchRollbackEventSchema, {
          filePath: item.filePath,
          toolUseId: item.toolUseId,
          status: RollbackStatus.ROLLBACK_STARTED,
        })
        onNext(toBinary(JetBrainsBatchRollbackEventSchema, started))

        try {
          const uri = toWorkspaceFileUri(item.filePath)
          const ts = Number(item.beforeTimestamp)

          if (ts === 0) {
            await vscode.workspace.fs.delete(uri, { useTrash: false })
          } else {
            const snap = snapshotStore.getSnapshotBefore(uri.fsPath, ts)
            if (!snap) throw new Error('Snapshot not found')
            await ensureParentDir(uri.fsPath)
            await vscode.workspace.fs.writeFile(uri, Buffer.from(snap.content, 'utf8'))
          }

          const ok = create(JetBrainsBatchRollbackEventSchema, {
            filePath: item.filePath,
            toolUseId: item.toolUseId,
            status: RollbackStatus.ROLLBACK_SUCCESS,
          })
          onNext(toBinary(JetBrainsBatchRollbackEventSchema, ok))
        } catch (err) {
          const fail = create(JetBrainsBatchRollbackEventSchema, {
            filePath: item.filePath,
            toolUseId: item.toolUseId,
            status: RollbackStatus.ROLLBACK_FAILED,
            error: err instanceof Error ? err.message : String(err),
          })
          onNext(toBinary(JetBrainsBatchRollbackEventSchema, fail))
        }
      }

      return
    }

    if (route === 'ide.terminalBackground') {
      const req = fromBinary(JetBrainsTerminalBackgroundRequestSchema, data)
      for (const item of req.items) {
        if (isCancelled()) return
        const started = create(JetBrainsTerminalBackgroundEventSchema, {
          sessionId: item.sessionId,
          toolUseId: item.toolUseId,
          status: TerminalBackgroundStatus.TERMINAL_BG_STARTED,
        })
        onNext(toBinary(JetBrainsTerminalBackgroundEventSchema, started))

        try {
          const ok = terminalTaskManager.markTaskAsBackground(item.toolUseId)
          const event = create(JetBrainsTerminalBackgroundEventSchema, {
            sessionId: item.sessionId,
            toolUseId: item.toolUseId,
            status: ok
              ? TerminalBackgroundStatus.TERMINAL_BG_SUCCESS
              : TerminalBackgroundStatus.TERMINAL_BG_FAILED,
            error: ok ? undefined : 'Task not found',
          })
          onNext(toBinary(JetBrainsTerminalBackgroundEventSchema, event))
        } catch (err) {
          const failed = create(JetBrainsTerminalBackgroundEventSchema, {
            sessionId: item.sessionId,
            toolUseId: item.toolUseId,
            status: TerminalBackgroundStatus.TERMINAL_BG_FAILED,
            error: err instanceof Error ? err.message : String(err),
          })
          onNext(toBinary(JetBrainsTerminalBackgroundEventSchema, failed))
        }
      }
      return
    }

    throw new Error(`Unsupported route: ${route}`)
  }

  return {
    requestResponse: (
      payload: Payload,
      responderStream: OnTerminalSubscriber & OnNextSubscriber & OnExtensionSubscriber
    ) => {
      void (async () => {
        try {
          const bytes = await handleRequestResponse(payload)
          responderStream.onNext({ data: Buffer.from(bytes) }, true)
        } catch (err) {
          responderStream.onError(err instanceof Error ? err : new Error(String(err)))
        }
      })()

      return createCancellable()
    },

    requestStream: (
      payload: Payload,
      _initialRequestN: number,
      responderStream: OnTerminalSubscriber & OnNextSubscriber & OnExtensionSubscriber
    ) => {
      const route = extractRoute(payload)
      const data = payload.data ? new Uint8Array(payload.data) : new Uint8Array()

      let cancelled = false

      void (async () => {
        try {
          await handleRequestStream(
            route,
            data,
            (bytes) => {
              if (cancelled) return
              responderStream.onNext({ data: Buffer.from(bytes) }, false)
            },
            () => cancelled
          )
          responderStream.onComplete()
        } catch (err) {
          responderStream.onError(err instanceof Error ? err : new Error(String(err)))
        }
      })()

      return {
        request: (_n: number) => {},
        cancel: () => {
          cancelled = true
        },
        onExtension: () => {},
      }
    },

    fireAndForget: (
      payload: Payload,
      responderStream: OnTerminalSubscriber & OnExtensionSubscriber & OnNextSubscriber
    ) => {
      // Frontend fire-and-forget calls are currently unused in VS Code.
      void payload
      responderStream.onComplete()
      return createCancellable()
    },
  }
}

function createCancellable(): { cancel(): void; onExtension(): void } {
  return { cancel: () => {}, onExtension: () => {} }
}

function encodeRoute(route: string): Buffer {
  const routeBytes = Buffer.from(route, 'utf8')
  const metadata = Buffer.alloc(1 + routeBytes.length)
  metadata[0] = routeBytes.length
  routeBytes.copy(metadata, 1)
  return metadata
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

  const normalized = filePath.replace(/\\/g, '/')
  const parts = normalized.split('/').filter(Boolean)
  if (parts.length >= 2) {
    const folderName = parts[0]
    const folder = folders.find((f) => f.name === folderName)
    if (folder) {
      return vscode.Uri.file(path.join(folder.uri.fsPath, ...parts.slice(1)))
    }
  }

  return vscode.Uri.file(path.join(folders[0].uri.fsPath, filePath))
}

async function ensureParentDir(filePath: string): Promise<void> {
  await vscode.workspace.fs.createDirectory(vscode.Uri.file(path.dirname(filePath)))
}

async function readFileText(filePath: string): Promise<string> {
  const uri = toWorkspaceFileUri(filePath)
  const raw = await vscode.workspace.fs.readFile(uri)
  return Buffer.from(raw).toString('utf8')
}

async function tryReadFileText(
  filePath: string,
  fallbackBase: string,
  oldStr: string,
  newStr: string,
  replaceAll: boolean
): Promise<string> {
  try {
    return await readFileText(filePath)
  } catch {
    if (!fallbackBase) return ''
    return replaceAll ? fallbackBase.split(oldStr).join(newStr) : applyEditOnce(fallbackBase, oldStr, newStr)
  }
}

function applyEdits(content: string, edits: Array<{ oldString: string; newString: string; replaceAll: boolean }>): string {
  let next = content
  for (const e of edits) {
    next = e.replaceAll ? next.split(e.oldString).join(e.newString) : applyEditOnce(next, e.oldString, e.newString)
  }
  return next
}

function applyEditOnce(content: string, oldStr: string, newStr: string): string {
  const idx = content.indexOf(oldStr)
  if (idx < 0) return content
  return content.slice(0, idx) + newStr + content.slice(idx + oldStr.length)
}

async function showDiff(
  diffProvider: DiffContentProvider | undefined,
  filePath: string,
  oldContent: string,
  newContent: string,
  title?: string
): Promise<void> {
  if (!diffProvider) throw new Error('Diff provider not available')

  const baseName = path.basename(filePath || 'diff')
  const left = diffProvider.createUri(oldContent, `${baseName} (before)`)
  const right = diffProvider.createUri(newContent, `${baseName} (after)`)
  await vscode.commands.executeCommand('vscode.diff', left, right, title || `Diff: ${baseName}`)
}

async function openFile(
  filePath: string,
  line?: number,
  column?: number,
  startOffset?: number,
  endOffset?: number
): Promise<void> {
  const uri = toWorkspaceFileUri(filePath)
  const doc = await vscode.workspace.openTextDocument(uri)
  const editor = await vscode.window.showTextDocument(doc, { preview: false })

  const clampLine = (n: number) => Math.max(0, Math.min(n, doc.lineCount - 1))

  if (startOffset && startOffset > 0) {
    const startLine = clampLine(startOffset - 1)
    const endLine = endOffset && endOffset > 0 ? clampLine(endOffset - 1) : startLine

    const startPos = new vscode.Position(startLine, 0)
    const endPos = new vscode.Position(endLine, doc.lineAt(endLine).text.length)
    editor.selection = new vscode.Selection(startPos, endPos)
    editor.revealRange(new vscode.Range(startPos, endPos), vscode.TextEditorRevealType.InCenterIfOutsideViewport)
    return
  }

  if (line && line > 0) {
    const l = clampLine(line - 1)
    const c = Math.max((column ?? 1) - 1, 0)
    const pos = new vscode.Position(l, c)
    editor.selection = new vscode.Selection(pos, pos)
    editor.revealRange(new vscode.Range(pos, pos), vscode.TextEditorRevealType.InCenterIfOutsideViewport)
  }
}

async function showMarkdown(content: string, title?: string): Promise<void> {
  const doc = await vscode.workspace.openTextDocument({ language: 'markdown', content })
  await vscode.window.showTextDocument(doc, { preview: false })
  if (title) {
    void vscode.window.setStatusBarMessage(title, 3000)
  }
}

function buildActiveFileNotify() {
  const editor = vscode.window.activeTextEditor
  const doc = editor?.document
  if (!editor || !doc || doc.uri.scheme !== 'file') {
    return create(ActiveFileChangedNotifySchema, { hasActiveFile: false, hasSelection: false })
  }

  const uri = doc.uri
  const selection = editor.selection
  const hasSelection = !selection.isEmpty
  const active = selection.active

  const folder = vscode.workspace.getWorkspaceFolder(uri)
  const relativePath = folder ? vscode.workspace.asRelativePath(uri, false) : undefined

  let selectedContent: string | undefined
  if (hasSelection) {
    const raw = doc.getText(selection)
    selectedContent = raw.length > 20_000 ? raw.slice(0, 20_000) + '\n\n... (truncated) ...\n' : raw
  }

  return create(ActiveFileChangedNotifySchema, {
    hasActiveFile: true,
    path: uri.fsPath,
    relativePath,
    name: path.basename(uri.fsPath),
    line: active.line + 1,
    column: active.character + 1,
    hasSelection,
    startLine: hasSelection ? selection.start.line + 1 : undefined,
    startColumn: hasSelection ? selection.start.character + 1 : undefined,
    endLine: hasSelection ? selection.end.line + 1 : undefined,
    endColumn: hasSelection ? selection.end.character + 1 : undefined,
    selectedContent,
  })
}

function buildIdeSettings(context: vscode.ExtensionContext) {
  const cfg = vscode.workspace.getConfiguration('claudeCodePlus', getConfigScopeUri())

  const defaultBypassPermissions = Boolean(cfg.get('defaultBypassPermissions') ?? false)
  const includePartialMessages = Boolean(cfg.get('includePartialMessages') ?? true)

  const claudeDefaultModelId = String(cfg.get('claude.defaultModelId') ?? 'claude-opus-4-5-20251101')
  const claudeDefaultThinkingTokens = toNumberOrDefault(cfg.get('claude.defaultThinkingTokens'), 8192)
  const claudeDefaultAutoCleanupContexts = Boolean(cfg.get('claude.defaultAutoCleanupContexts') ?? true)

  const codexDefaultModelId = String(cfg.get('codex.defaultModelId') ?? 'gpt-5.2-codex')
  const codexReasoningEffort = String(cfg.get('codex.reasoningEffort') ?? 'medium')
  const codexReasoningSummary = String(cfg.get('codex.reasoningSummary') ?? 'auto')
  const codexSandboxMode = String(cfg.get('codex.sandboxMode') ?? 'workspace-write')
  const codexDefaultAutoCleanupContexts = Boolean(cfg.get('codex.defaultAutoCleanupContexts') ?? true)

  const defaultThinkingLevelId = resolveThinkingLevelId(claudeDefaultThinkingTokens)
  const defaultThinkingLevel = defaultThinkingLevelId === 'off' ? 'OFF' : defaultThinkingLevelId === 'think' ? 'THINK' : 'ULTRA'

  // The IdeSettings proto doesn't include sensitive fields (API keys). VS Code serves them via HTTP settings.get.
  void context

  return create(IdeSettingsSchema, {
    defaultModelId: claudeDefaultModelId,
    defaultModelName: lookupClaudeModelName(claudeDefaultModelId),
    defaultBypassPermissions,
    enableUserInteractionMcp: true,
    enableJetbrainsMcp: true,
    includePartialMessages,
    defaultThinkingLevel,
    defaultThinkingTokens: claudeDefaultThinkingTokens,
    defaultThinkingLevelId,
    thinkingLevels: [
      { id: 'off', name: 'Off', tokens: 0, isCustom: false },
      { id: 'think', name: 'Think', tokens: 2048, isCustom: false },
      { id: 'ultra', name: 'Ultra', tokens: 8096, isCustom: false },
    ],
    permissionMode: 'default',
    codexDefaultModelId,
    codexDefaultReasoningEffort: codexReasoningEffort,
    codexDefaultReasoningSummary: codexReasoningSummary,
    codexDefaultSandboxMode: codexSandboxMode,
    claudeDefaultAutoCleanupContexts,
    codexDefaultAutoCleanupContexts,
    codexReasoningEffortOptions: [],
    codexReasoningSummaryOptions: [],
    codexSandboxModeOptions: [],
    permissionModeOptions: [],
  })
}

function resolveThinkingLevelId(tokens: number): string {
  if (!Number.isFinite(tokens) || tokens <= 0) return 'off'
  if (tokens <= 2048) return 'think'
  return 'ultra'
}

function lookupClaudeModelName(modelId: string): string {
  switch (modelId) {
    case 'claude-opus-4-5-20251101':
      return 'Claude Opus 4.5'
    case 'claude-sonnet-4-5-20250929':
      return 'Claude Sonnet 4.5'
    case 'claude-haiku-4-5-20251001':
      return 'Claude Haiku 4.5'
    default:
      return ''
  }
}

function getConfigScopeUri(): vscode.Uri | undefined {
  const active = vscode.window.activeTextEditor?.document?.uri
  if (active) {
    const folder = vscode.workspace.getWorkspaceFolder(active)
    if (folder) return active
  }
  return vscode.workspace.workspaceFolders?.[0]?.uri
}

function toNumberOrDefault(value: unknown, fallback: number): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}
