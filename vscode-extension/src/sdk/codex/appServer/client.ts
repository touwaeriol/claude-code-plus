/**
 * Codex App-Server 高层 API 客户端
 *
 * 翻译自: codex-agent-sdk/.../appserver/CodexAppServerClient.kt
 *
 * 提供与 codex app-server 交互的高层 API:
 * - 初始化握手
 * - 线程管理 (创建、恢复、列表)
 * - 回合管理 (开始、中断)
 * - 事件流处理
 * - 审批流程
 */

import { EventEmitter } from 'events'
import { CodexAppServerProcess, CodexAppServerException, SpawnOptions } from './process'
import { CodexJsonRpcClient } from './jsonRpcClient'
import type {
  InitializeParams,
  InitializeResult,
  ThreadInfo,
  ThreadStartResult,
  ThreadListParams,
  ThreadListResult,
  TurnInfo,
  TurnStartParams,
  TurnStartResult,
  UserInput,
  SandboxPolicy,
  ReasoningSummary,
  ThreadItem,
  ThreadItemCommandExecution,
  ThreadItemFileChange,
  ServerRequest,
  ApprovalDecision,
  CommandExecutionRequestApprovalResponse,
  FileChangeRequestApprovalResponse,
  ThreadStartedNotification,
  TurnStartedNotification,
  TurnCompletedNotification,
  ItemStartedNotification,
  ItemCompletedNotification,
  AgentMessageDeltaNotification,
  ReasoningTextDeltaNotification,
  CommandExecutionOutputDeltaNotification,
  ThreadTokenUsageUpdatedNotification,
  ErrorNotification,
  ThreadTokenUsage,
  ExecPolicyAmendment,
  FileUpdateChange,
  ListMcpServerStatusResponse,
  encodeApprovalDecision,
} from '../types'

// AppServerEvent 类型定义
export type AppServerEvent =
  | { type: 'threadStarted'; thread: ThreadInfo }
  | { type: 'turnStarted'; threadId: string; turn: TurnInfo }
  | { type: 'turnCompleted'; threadId: string; turn: TurnInfo }
  | { type: 'itemStarted'; threadId: string; turnId: string; item: ThreadItem }
  | { type: 'itemCompleted'; threadId: string; turnId: string; item: ThreadItem }
  | { type: 'agentMessageDelta'; threadId: string; turnId: string; itemId: string; delta: string }
  | { type: 'reasoningDelta'; threadId: string; turnId: string; itemId: string; delta: string }
  | { type: 'commandOutputDelta'; threadId: string; turnId: string; itemId: string; delta: string }
  | {
      type: 'commandApprovalRequired'
      requestId: string
      rawId: unknown
      itemId: string
      threadId: string
      turnId: string
      command?: string
      cwd?: string
      reason?: string
      proposedExecpolicyAmendment?: ExecPolicyAmendment
    }
  | {
      type: 'fileChangeApprovalRequired'
      requestId: string
      rawId: unknown
      itemId: string
      threadId: string
      turnId: string
      changes: FileUpdateChange[]
      reason?: string
      grantRoot?: string
    }
  | { type: 'tokenUsageUpdated'; threadId: string; turnId: string; usage: ThreadTokenUsage }
  | { type: 'error'; threadId: string; turnId: string; message: string; willRetry: boolean }

export class CodexAppServerClient extends EventEmitter {
  private _process: CodexAppServerProcess
  private rpc: CodexJsonRpcClient
  private initialized = false
  private itemCache = new Map<string, ThreadItem>()

  private constructor(proc: CodexAppServerProcess) {
    super()
    this._process = proc
    this.rpc = proc.client
    this.startEventProcessing()
  }

  private startEventProcessing(): void {
    // 处理通知事件
    this.rpc.on('notification', (notification: { method: string; params?: unknown }) => {
      this.processNotification(notification)
    })

    // 处理服务器请求 (审批)
    this.rpc.on('serverRequest', (request: ServerRequest) => {
      this.processServerRequest(request)
    })
  }

  private processNotification(notification: { method: string; params?: unknown }): void {
    let event: AppServerEvent | null = null

    try {
      switch (notification.method) {
        case 'thread/started': {
          const params = notification.params as ThreadStartedNotification
          event = { type: 'threadStarted', thread: params.thread }
          break
        }
        case 'turn/started': {
          const params = notification.params as TurnStartedNotification
          event = { type: 'turnStarted', threadId: params.threadId, turn: params.turn }
          break
        }
        case 'turn/completed': {
          const params = notification.params as TurnCompletedNotification
          event = { type: 'turnCompleted', threadId: params.threadId, turn: params.turn }
          break
        }
        case 'item/started': {
          const params = notification.params as ItemStartedNotification
          this.itemCache.set(params.item.id, params.item)
          event = { type: 'itemStarted', threadId: params.threadId, turnId: params.turnId, item: params.item }
          break
        }
        case 'item/completed': {
          const params = notification.params as ItemCompletedNotification
          this.itemCache.set(params.item.id, params.item)
          event = { type: 'itemCompleted', threadId: params.threadId, turnId: params.turnId, item: params.item }
          break
        }
        case 'item/agentMessage/delta': {
          const params = notification.params as AgentMessageDeltaNotification
          event = { type: 'agentMessageDelta', ...params }
          break
        }
        case 'item/reasoning/summaryTextDelta':
        case 'item/reasoning/textDelta': {
          const params = notification.params as ReasoningTextDeltaNotification
          event = {
            type: 'reasoningDelta',
            threadId: params.threadId,
            turnId: params.turnId,
            itemId: params.itemId,
            delta: params.delta,
          }
          break
        }
        case 'item/commandExecution/outputDelta': {
          const params = notification.params as CommandExecutionOutputDeltaNotification
          event = { type: 'commandOutputDelta', ...params }
          break
        }
        case 'thread/tokenUsage/updated': {
          const params = notification.params as ThreadTokenUsageUpdatedNotification
          event = { type: 'tokenUsageUpdated', threadId: params.threadId, turnId: params.turnId, usage: params.tokenUsage }
          break
        }
        case 'error': {
          const params = notification.params as ErrorNotification
          event = {
            type: 'error',
            threadId: params.threadId,
            turnId: params.turnId,
            message: params.error.message,
            willRetry: params.willRetry,
          }
          break
        }
      }
    } catch (e) {
      console.warn(`[CodexAppServerClient] Failed to parse notification ${notification.method}: ${e}`)
    }

    if (event) {
      this.emit('event', event)
    }
  }

  private processServerRequest(request: ServerRequest): void {
    let event: AppServerEvent | null = null

    if (request.type === 'commandApproval') {
      const cached = this.itemCache.get(request.params.itemId) as ThreadItemCommandExecution | undefined
      event = {
        type: 'commandApprovalRequired',
        requestId: request.requestId,
        rawId: request.rawId,
        itemId: request.params.itemId,
        threadId: request.params.threadId,
        turnId: request.params.turnId,
        command: cached?.command,
        cwd: cached?.cwd,
        reason: request.params.reason,
        proposedExecpolicyAmendment: request.params.proposedExecpolicyAmendment,
      }
    } else if (request.type === 'fileChangeApproval') {
      const cached = this.itemCache.get(request.params.itemId) as ThreadItemFileChange | undefined
      event = {
        type: 'fileChangeApprovalRequired',
        requestId: request.requestId,
        rawId: request.rawId,
        itemId: request.params.itemId,
        threadId: request.params.threadId,
        turnId: request.params.turnId,
        changes: cached?.changes || [],
        reason: request.params.reason,
        grantRoot: request.params.grantRoot,
      }
    }

    if (event) {
      this.emit('event', event)
    }
  }

  // ============== 初始化 ==============

  async initialize(
    clientName = 'claude-code-plus',
    clientTitle = 'Claude Code Plus',
    clientVersion = '1.0.0'
  ): Promise<InitializeResult> {
    if (this.initialized) {
      throw new CodexAppServerException('Already initialized')
    }

    const params: InitializeParams = {
      clientInfo: { name: clientName, title: clientTitle, version: clientVersion },
    }

    const result = await this.rpc.request<InitializeResult>('initialize', params)
    await this.rpc.notify('initialized')
    this.initialized = true

    return result
  }

  // ============== 线程管理 ==============

  async startThread(
    options: {
      model?: string
      modelProvider?: string
      cwd?: string
      approvalPolicy?: string
      sandbox?: string
      config?: Record<string, unknown>
      baseInstructions?: string
      developerInstructions?: string
    } = {}
  ): Promise<ThreadInfo> {
    this.checkInitialized()
    const result = await this.rpc.request<ThreadStartResult>('thread/start', options)
    return result.thread
  }

  async resumeThread(threadId: string): Promise<ThreadInfo> {
    this.checkInitialized()
    const result = await this.rpc.request<ThreadStartResult>('thread/resume', { threadId })
    return result.thread
  }

  async archiveThread(threadId: string): Promise<void> {
    this.checkInitialized()
    await this.rpc.request<void>('thread/archive', { threadId })
  }

  async listThreads(params: ThreadListParams = {}): Promise<ThreadListResult> {
    this.checkInitialized()
    return this.rpc.request<ThreadListResult>('thread/list', params)
  }

  // ============== 回合管理 ==============

  async startTurn(options: {
    threadId: string
    message: string
    images?: string[]
    cwd?: string
    model?: string
    approvalPolicy?: string
    sandboxPolicy?: SandboxPolicy
    effort?: string
    summary?: ReasoningSummary
  }): Promise<TurnInfo> {
    this.checkInitialized()

    const input: UserInput[] = [{ type: 'text', text: options.message }]
    if (options.images) {
      for (const imagePath of options.images) {
        input.push({ type: 'localImage', path: imagePath })
      }
    }

    const params: TurnStartParams = {
      threadId: options.threadId,
      input,
      cwd: options.cwd,
      model: options.model,
      approvalPolicy: options.approvalPolicy,
      sandboxPolicy: options.sandboxPolicy,
      effort: options.effort,
      summary: options.summary,
    }

    const result = await this.rpc.request<TurnStartResult>('turn/start', params)
    return result.turn
  }

  async interruptTurn(threadId: string, turnId: string): Promise<void> {
    this.checkInitialized()
    await this.rpc.request<void>('turn/interrupt', { threadId, turnId })
  }

  // ============== 审批响应 ==============

  async acceptCommand(rawId: unknown, forSession = false): Promise<void> {
    const decision: ApprovalDecision = forSession ? { type: 'acceptForSession' } : { type: 'accept' }
    const response: CommandExecutionRequestApprovalResponse = { decision }
    await this.rpc.respondToServerRequest(rawId, { decision: encodeApprovalDecision(decision) })
  }

  async declineCommand(rawId: unknown): Promise<void> {
    const decision: ApprovalDecision = { type: 'decline' }
    await this.rpc.respondToServerRequest(rawId, { decision: encodeApprovalDecision(decision) })
  }

  async acceptFileChange(rawId: unknown): Promise<void> {
    const decision: ApprovalDecision = { type: 'accept' }
    await this.rpc.respondToServerRequest(rawId, { decision: encodeApprovalDecision(decision) })
  }

  async declineFileChange(rawId: unknown): Promise<void> {
    const decision: ApprovalDecision = { type: 'decline' }
    await this.rpc.respondToServerRequest(rawId, { decision: encodeApprovalDecision(decision) })
  }

  // ============== MCP ==============

  async listMcpServerStatus(cursor?: string, limit?: number): Promise<ListMcpServerStatusResponse> {
    this.checkInitialized()
    return this.rpc.request('mcpServerStatus/list', { cursor, limit })
  }

  async startMcpOauthLogin(
    name: string,
    scopes?: string[],
    timeoutSecs?: number
  ): Promise<{ authorizationUrl: string }> {
    this.checkInitialized()
    return this.rpc.request('mcpServer/oauth/login', { name, scopes, timeoutSecs })
  }

  // ============== 辅助方法 ==============

  private checkInitialized(): void {
    if (!this.initialized) {
      throw new CodexAppServerException('Not initialized. Call initialize() first.')
    }
  }

  get isAlive(): boolean {
    return this._process.isAlive
  }

  close(): void {
    this._process.close()
  }

  static create(options: SpawnOptions = {}): CodexAppServerClient {
    const proc = CodexAppServerProcess.spawn(options)
    return new CodexAppServerClient(proc)
  }
}
