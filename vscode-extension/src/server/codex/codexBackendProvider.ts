import { EventEmitter } from 'events'

import { CodexAppServerClient } from '../../sdk/codex/appServer'

export interface ThreadConfig {
  model?: string | null
  cwd?: string | null
  approvalPolicy?: string | null
  sandbox?: string | null
}

export interface ThreadState {
  threadId: string
  config: ThreadConfig
  isActive: boolean
  currentTurnId: string | null
}

/**
 * VS Code-side Codex backend provider (app-server mode), aligned with
 * `ai-agent-server/.../codex/CodexBackendProvider.kt`.
 *
 * This is primarily used by HTTP endpoints under `/api/codex/*` to provide a stable,
 * testable contract. It intentionally keeps a minimal surface area for now.
 */
export class CodexBackendProvider extends EventEmitter {
  private client: CodexAppServerClient | null = null
  private readonly threads = new Map<string, ThreadState>()
  private starting: Promise<void> | null = null
  private started = false

  constructor(
    private readonly workingDirectory: string,
    private readonly log?: (msg: string) => void
  ) {
    super()
  }

  get running(): boolean {
    return Boolean(this.started && this.client?.isAlive)
  }

  async start(): Promise<void> {
    if (this.running) return
    if (this.starting) return this.starting

    this.starting = this.doStart()
    try {
      await this.starting
    } finally {
      this.starting = null
    }
  }

  private async doStart(): Promise<void> {
    this.log?.(`[CodexBackendProvider] starting (cwd=${this.workingDirectory})`)

    const client = CodexAppServerClient.create({
      workingDirectory: this.workingDirectory,
    })

    try {
      client.on('event', (event) => this.emit('event', event))
      await client.initialize('claude-code-plus', 'Claude Code Plus', '1.0.0')
      this.client = client
      this.started = true
      this.log?.('[CodexBackendProvider] started')
    } catch (e) {
      try {
        client.close()
      } catch {
        // ignore
      }
      this.client = null
      this.started = false
      throw e
    }
  }

  stop(): void {
    try {
      this.client?.close()
    } finally {
      this.client = null
      this.started = false
      this.threads.clear()
    }
  }

  private ensureRunning(): CodexAppServerClient {
    if (!this.running || !this.client) {
      throw new Error('CodexBackendProvider is not running')
    }
    return this.client
  }

  async createThread(config: ThreadConfig = {}): Promise<string> {
    await this.start()
    const client = this.ensureRunning()

    const thread = await client.startThread({
      model: config.model ?? undefined,
      cwd: config.cwd ?? this.workingDirectory,
      approvalPolicy: config.approvalPolicy ?? undefined,
      sandbox: config.sandbox ?? undefined,
    })

    this.threads.set(thread.id, {
      threadId: thread.id,
      config,
      isActive: true,
      currentTurnId: null,
    })

    return thread.id
  }

  async resumeThread(threadId: string): Promise<void> {
    await this.start()
    const client = this.ensureRunning()

    await client.resumeThread(threadId)

    const existing = this.threads.get(threadId)
    if (existing) {
      existing.isActive = true
    } else {
      this.threads.set(threadId, {
        threadId,
        config: {},
        isActive: true,
        currentTurnId: null,
      })
    }
  }

  async archiveThread(threadId: string): Promise<void> {
    await this.start()
    const client = this.ensureRunning()

    await client.archiveThread(threadId)

    const existing = this.threads.get(threadId)
    if (existing) existing.isActive = false
  }

  async startTurn(threadId: string, input: string): Promise<string> {
    await this.start()
    const client = this.ensureRunning()

    const thread = this.threads.get(threadId)
    if (!thread) throw new Error(`Thread not found: ${threadId}`)
    if (!thread.isActive) throw new Error(`Thread is not active: ${threadId}`)

    const turn = await client.startTurn({
      threadId,
      message: input,
      cwd: this.workingDirectory,
    })

    thread.currentTurnId = turn.id
    return turn.id
  }

  async interruptTurn(threadId: string): Promise<void> {
    await this.start()
    const client = this.ensureRunning()

    const thread = this.threads.get(threadId)
    if (!thread) throw new Error(`Thread not found: ${threadId}`)

    const turnId = thread.currentTurnId
    if (!turnId) throw new Error(`No active turn for thread: ${threadId}`)

    await client.interruptTurn(threadId, turnId)
  }

  getThreadState(threadId: string): ThreadState | undefined {
    return this.threads.get(threadId)
  }
}

