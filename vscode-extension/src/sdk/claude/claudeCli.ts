import * as crypto from 'crypto'
import { spawn, type ChildProcessWithoutNullStreams } from 'child_process'
import * as readline from 'readline'
import * as vscode from 'vscode'

export type ClaudeCliSessionConfig = {
  sessionId: string
  cwd: string
  model: string
  permissionMode: 'default' | 'acceptEdits' | 'plan' | 'bypassPermissions'
  includePartialMessages: boolean
  dangerouslySkipPermissions: boolean
  /** Additional directories for multi-root workspace support */
  addDirs?: string[]
}

export type ToolPermissionRequest = {
  toolName: string
  input: unknown
  toolUseId?: string
  permissionSuggestions?: unknown[]
  blockedPath?: string
}

export type ToolPermissionResult = {
  approved: boolean
  denyReason?: string
  /** Snapshot metadata to inject into tool_result (for Write/Edit tools) */
  snapshotMeta?: {
    historyTs: number
    canRollback: boolean
    isNewFile?: boolean
    isOverwrite?: boolean
  }
}

export type ClaudeCliQueryCallbacks = {
  onJsonMessage: (msg: unknown) => void
  requestPermission: (req: ToolPermissionRequest) => Promise<ToolPermissionResult>
}

export class ClaudeCliSessionManager implements vscode.Disposable {
  private readonly sessions = new Map<string, ClaudeCliSession>()

  constructor(private readonly log?: (message: string) => void) {}

  async getOrCreate(config: ClaudeCliSessionConfig): Promise<ClaudeCliSession> {
    const existing = this.sessions.get(config.sessionId)
    if (existing && existing.matches(config)) return existing

    existing?.dispose()

    const created = new ClaudeCliSession(config, this.log)
    this.sessions.set(config.sessionId, created)
    await created.ensureStarted()
    return created
  }

  dispose() {
    this.sessions.forEach(s => s.dispose());
    this.sessions.clear()
  }
}

class ClaudeCliSession implements vscode.Disposable {
  private proc: ChildProcessWithoutNullStreams | undefined
  private stdoutRl: readline.Interface | undefined
  private stderrRl: readline.Interface | undefined

  private readyPromise: Promise<void> | undefined
  private readyResolve: (() => void) | undefined
  private readyReject: ((err: Error) => void) | undefined

  private disposed = false

  /** Tracks snapshot metadata by toolUseId for injecting [jb:*] markers into tool_result */
  private readonly snapshotMetaMap = new Map<string, NonNullable<ToolPermissionResult['snapshotMeta']>>()

  private activeQuery:
    | {
        generation: number
        onJsonMessage: (msg: unknown) => void
        requestPermission: ClaudeCliQueryCallbacks['requestPermission']
        skipPermissions: boolean
        resolve: () => void
        reject: (err: Error) => void
        done: Promise<void>
      }
    | undefined

  private queryGeneration = 0

  constructor(
    private readonly config: ClaudeCliSessionConfig,
    private readonly log?: (message: string) => void
  ) {}

  matches(config: ClaudeCliSessionConfig): boolean {
    const addDirsMatch = 
      (this.config.addDirs ?? []).length === (config.addDirs ?? []).length &&
      (this.config.addDirs ?? []).every((d, i) => d === (config.addDirs ?? [])[i])
    
    return (
      config.sessionId === this.config.sessionId &&
      config.cwd === this.config.cwd &&
      config.model === this.config.model &&
      config.permissionMode === this.config.permissionMode &&
      config.includePartialMessages === this.config.includePartialMessages &&
      config.dangerouslySkipPermissions === this.config.dangerouslySkipPermissions &&
      addDirsMatch
    )
  }

  async ensureStarted(): Promise<void> {
    if (this.disposed) throw new Error('Claude CLI session disposed')
    if (this.proc && !this.proc.killed) return
    if (this.readyPromise) return this.readyPromise

    this.readyPromise = new Promise<void>((resolve, reject) => {
      this.readyResolve = resolve
      this.readyReject = reject
    })

    const args = buildClaudeArgs(this.config)
    this.log?.(`[claude] spawn: claude ${args.join(' ')}`)

    this.proc = spawn('claude', args, {
      cwd: this.config.cwd || undefined,
      shell: true, // Windows installs claude as a shim; `shell` makes it resolvable
      windowsHide: true,
      env: {
        ...process.env,
        CLAUDE_CODE_ENTRYPOINT: 'vscode-extension',
      },
      stdio: ['pipe', 'pipe', 'pipe'],
    })

    // The CLI may not emit any stdout until it receives the first user message. We only need to
    // ensure the process is spawned + streams are wired up before callers can write to stdin.
    this.proc.once('spawn', () => {
      this.readyResolve?.()
      this.readyResolve = undefined
      this.readyReject = undefined
    })

    this.proc.on('error', (err) => {
      this.readyReject?.(err instanceof Error ? err : new Error(String(err)))
      this.readyReject = undefined
    })

    this.proc.on('exit', (code, signal) => {
      const msg = `Claude CLI exited code=${String(code)} signal=${String(signal)}`
      this.log?.(`[claude] ${msg}`)
      this.readyReject?.(new Error(msg))
      this.readyReject = undefined

      const q = this.activeQuery
      this.activeQuery = undefined
      q?.reject(new Error(msg))
    })

    this.stdoutRl = readline.createInterface({ input: this.proc.stdout, crlfDelay: Infinity })
    this.stdoutRl.on('line', (line) => this.onStdoutLine(line))

    this.stderrRl = readline.createInterface({ input: this.proc.stderr, crlfDelay: Infinity })
    this.stderrRl.on('line', (line) => {
      const trimmed = line.trim()
      if (!trimmed) return
      this.log?.(`[claude:stderr] ${trimmed}`)
    })

    return this.readyPromise
  }

  startQuery(prompt: string, callbacks: ClaudeCliQueryCallbacks): { cancel: () => void; done: Promise<void> } {
    const generation = ++this.queryGeneration

    const previous = this.activeQuery
    if (previous) {
      // If a query is still running, interrupt it and reject to unblock the caller.
      this.sendInterrupt()
      previous.reject(new Error('Query superseded by a new request'))
      this.activeQuery = undefined
    }

    let resolveDone: (() => void) | undefined
    let rejectDone: ((err: Error) => void) | undefined

    const done = new Promise<void>((resolve, reject) => {
      resolveDone = resolve
      rejectDone = reject
    })

    const skipPermissions = this.config.dangerouslySkipPermissions

    this.activeQuery = {
      generation,
      onJsonMessage: callbacks.onJsonMessage,
      requestPermission: callbacks.requestPermission,
      skipPermissions,
      resolve: resolveDone!,
      reject: rejectDone!,
      done,
    }

    void (async () => {
      try {
        await this.ensureStarted()
        if (!this.proc || !this.proc.stdin.writable) throw new Error('Claude CLI not writable')
        const msg = buildUserMessage(prompt, this.config.sessionId)
        this.proc.stdin.write(JSON.stringify(msg) + '\n', 'utf8')
      } catch (err) {
        const error = err instanceof Error ? err : new Error(String(err))
        this.activeQuery?.reject(error)
      }
    })()

    const cancel = () => {
      if (this.activeQuery?.generation !== generation) return
      this.sendInterrupt()
      this.activeQuery.reject(new Error('Query cancelled'))
      this.activeQuery = undefined
    }

    return { cancel, done }
  }

  dispose() {
    this.disposed = true
    try {
      this.stdoutRl?.close()
      this.stderrRl?.close()
    } catch {
      // ignore
    }
    try {
      this.proc?.kill()
    } catch {
      // ignore
    }
    this.proc = undefined
    this.stdoutRl = undefined
    this.stderrRl = undefined
    this.readyPromise = undefined
    this.readyResolve = undefined
    this.readyReject = undefined
    this.activeQuery = undefined
  }

  private onStdoutLine(raw: string) {
    const line = raw.trim()
    if (!line) return

    let parsed: any
    try {
      parsed = JSON.parse(line)
    } catch {
      // Claude --verbose logs go to stderr, but be defensive.
      this.log?.(`[claude] non-json stdout: ${line.slice(0, 200)}`)
      return
    }

    const type = typeof parsed?.type === 'string' ? parsed.type : ''

    if (type === 'system' && parsed?.subtype === 'init') {
      // The CLI sends this once and then starts streaming events.
      this.readyResolve?.()
      this.readyResolve = undefined
      this.readyReject = undefined
      return
    }

    if (type === 'control_request') {
      void this.handleControlRequest(parsed)
      return
    }

    // Inject [jb:*] markers into tool_result content blocks
    this.injectSnapshotMetaIntoToolResults(parsed)

    if (type === 'result') {
      const q = this.activeQuery
      if (q) {
        q.onJsonMessage(parsed)
        q.resolve()
        this.activeQuery = undefined
      }
      return
    }

    const q = this.activeQuery
    if (q) q.onJsonMessage(parsed)
  }

  /**
   * Injects [jb:*] markers into tool_result content blocks.
   * Mutates the parsed object in place.
   */
  private injectSnapshotMetaIntoToolResults(parsed: any): void {
    if (!parsed || typeof parsed !== 'object') return

    // Handle assistant message with content array
    // Format: { "type": "assistant", "message": { "content": [...] } }
    if (parsed.type === 'assistant' && Array.isArray(parsed.message?.content)) {
      for (const block of parsed.message.content) {
        this.injectMetaIntoToolResultBlock(block)
      }
    }

    // Handle stream_event with content_block_delta
    // Format: { "type": "stream_event", "event": { "type": "content_block_delta", "delta": { "type": "tool_result", ... } } }
    if (parsed.type === 'stream_event' && parsed.event?.type === 'content_block_delta') {
      const delta = parsed.event?.delta
      if (delta) {
        this.injectMetaIntoToolResultBlock(delta)
      }
    }

    // Handle result message with content array
    // Format: { "type": "result", "result": { "content": [...] } }
    if (parsed.type === 'result' && Array.isArray(parsed.result?.content)) {
      for (const block of parsed.result.content) {
        this.injectMetaIntoToolResultBlock(block)
      }
    }
  }

  /**
   * Injects [jb:*] markers into a single tool_result block if snapshot metadata exists.
   * Mutates the block in place.
   */
  private injectMetaIntoToolResultBlock(block: any): void {
    if (!block || typeof block !== 'object') return
    if (block.type !== 'tool_result') return

    const toolUseId = block.tool_use_id
    if (typeof toolUseId !== 'string') return

    const meta = this.snapshotMetaMap.get(toolUseId)
    if (!meta) return

    // Build the [jb:*] marker prefix
    const markers = [
      `[jb:historyTs=${meta.historyTs}]`,
      `[jb:isOverwrite=${meta.isOverwrite ?? false}]`,
      `[jb:isNewFile=${meta.isNewFile ?? false}]`,
      `[jb:canRollback=${meta.canRollback}]`,
    ].join('\n')

    // Inject markers at the beginning of content
    if (typeof block.content === 'string') {
      block.content = markers + '\n' + block.content
    } else if (block.content === undefined || block.content === null) {
      block.content = markers
    }
    // If content is an array or other type, we don't inject (uncommon case)

    // Remove the entry from the map after injection
    this.snapshotMetaMap.delete(toolUseId)
    this.log?.(`[claude] Injected snapshot meta for tool_use_id=${toolUseId}`)
  }

  private async handleControlRequest(parsed: any): Promise<void> {
    const requestId = typeof parsed?.request_id === 'string' ? parsed.request_id : ''
    const request = parsed?.request
    const subtype = typeof request?.subtype === 'string' ? request.subtype : ''

    if (!requestId) return

    const q = this.activeQuery

    try {
      switch (subtype) {
        case 'can_use_tool': {
          const toolName = typeof request?.tool_name === 'string' ? request.tool_name : ''
          const input = request?.input
          const toolUseId = typeof request?.tool_use_id === 'string' ? request.tool_use_id : undefined
          const suggestions = Array.isArray(request?.permission_suggestions) ? request.permission_suggestions : undefined
          const blockedPath = typeof request?.blocked_path === 'string' ? request.blocked_path : undefined

          if (!toolName) {
            await this.sendControlResponse(requestId, 'error', undefined, 'Missing tool_name')
            return
          }

          if (!q || q.skipPermissions) {
            await this.sendControlResponse(
              requestId,
              'success',
              { behavior: 'allow', updatedInput: input ?? {} },
              undefined
            )
            return
          }

          const decision = await q.requestPermission({
            toolName,
            input,
            toolUseId,
            permissionSuggestions: suggestions,
            blockedPath,
          })

          if (decision.approved) {
            // Store snapshot metadata for later injection into tool_result
            if (toolUseId && decision.snapshotMeta) {
              this.snapshotMetaMap.set(toolUseId, decision.snapshotMeta)
            }
            await this.sendControlResponse(
              requestId,
              'success',
              { behavior: 'allow', updatedInput: input ?? {} },
              undefined
            )
          } else {
            await this.sendControlResponse(
              requestId,
              'success',
              { behavior: 'deny', message: decision.denyReason || 'Denied' },
              undefined
            )
          }
          return
        }
        case 'hook_callback': {
          // Hooks are optional for now; return an empty success payload.
          await this.sendControlResponse(requestId, 'success', {}, undefined)
          return
        }
        case 'mcp_message': {
          await this.sendControlResponse(requestId, 'error', undefined, 'MCP is not implemented in VS Code extension yet')
          return
        }
        case 'interrupt': {
          // The CLI can reply with interrupt requests in some flows; acknowledge.
          await this.sendControlResponse(requestId, 'success', {}, undefined)
          return
        }
        default: {
          await this.sendControlResponse(requestId, 'error', undefined, `Unsupported control_request subtype: ${subtype}`)
          return
        }
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      await this.sendControlResponse(requestId, 'error', undefined, msg)
    }
  }

  private async sendControlResponse(
    requestId: string,
    subtype: 'success' | 'error',
    response: unknown,
    error: string | undefined
  ): Promise<void> {
    if (!this.proc || !this.proc.stdin.writable) return

    const payload: any = {
      type: 'control_response',
      response: {
        subtype,
        request_id: requestId,
      },
    }
    if (response !== undefined) payload.response.response = response
    if (error) payload.response.error = error

    this.proc.stdin.write(JSON.stringify(payload) + '\n', 'utf8')
  }

  private sendInterrupt(): void {
    if (!this.proc || !this.proc.stdin.writable) return
    const requestId = `req_${crypto.randomUUID()}`
    const payload = {
      type: 'control_request',
      request_id: requestId,
      request: { subtype: 'interrupt' },
    }
    try {
      this.proc.stdin.write(JSON.stringify(payload) + '\n', 'utf8')
    } catch {
      // ignore
    }
  }
}

function buildUserMessage(text: string, sessionId: string) {
  return {
    type: 'user',
    message: { role: 'user', content: text },
    parent_tool_use_id: null,
    session_id: sessionId,
  }
}

function buildClaudeArgs(config: ClaudeCliSessionConfig): string[] {
  const model = sanitizeCliArg(config.model, 'model')
  const sessionId = sanitizeCliArg(config.sessionId, 'sessionId')
  const permissionMode = sanitizeCliArg(config.permissionMode, 'permissionMode')

  const args = [
    '--print',
    '--verbose',
    '--output-format',
    'stream-json',
    '--input-format',
    'stream-json',
    '--session-id',
    sessionId,
    '--model',
    model,
    '--permission-mode',
    permissionMode,
  ]

  if (config.includePartialMessages) {
    args.push('--include-partial-messages')
  }

  if (config.dangerouslySkipPermissions) {
    args.push('--dangerously-skip-permissions')
  }

  // Additional directories for multi-root workspace
  if (config.addDirs && config.addDirs.length > 0) {
    for (const dir of config.addDirs) {
      args.push('--add-dir', dir)
    }
  }

  return args
}

function sanitizeCliArg(value: string, name: string): string {
  const v = String(value || '').trim()
  if (!v) throw new Error(`Invalid Claude CLI arg: ${name} is empty`)
  // Keep this conservative since we spawn with `shell: true` on Windows.
  if (!/^[A-Za-z0-9._:-]+$/.test(v)) {
    throw new Error(`Invalid Claude CLI arg: ${name} contains unsupported characters`)
  }
  return v
}
