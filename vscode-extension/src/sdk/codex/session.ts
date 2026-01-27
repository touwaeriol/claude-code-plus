/**
 * Codex 会话管理
 *
 * 翻译自: ai-agent-sdk/.../client/CodexAgentClientImpl.kt
 *
 * 实现 UnifiedAgentClient 接口:
 * - 管理 Codex 会话生命周期
 * - 处理权限请求转发
 * - 事件流处理
 */

import { EventEmitter } from 'events'
import { CodexAppServerClient, AppServerEvent, type SpawnOptions } from './appServer'
import { CodexAppServerStreamAdapter, NormalizedStreamEvent } from './adapter'
import type {
  SandboxPolicy,
  ReasoningSummary,
  ThreadInfo,
  TurnInfo,
  ApprovalDecision,
  McpServerStatus,
} from './types'

export type PermissionMode = 'default' | 'acceptEdits' | 'bypassPermissions' | 'plan'
export type ApprovalMode = 'always' | 'never' | 'auto-edit'

export interface PermissionRequest {
  toolName: string
  toolUseId: string
  input?: unknown
  command?: string
  cwd?: string
  reason?: string
}

export interface PermissionResult {
  approved: boolean
  denyReason?: string
}

export type PermissionRequester = (request: PermissionRequest) => Promise<PermissionResult>

// MCP 相关类型
export interface McpServerStatusInfo {
  name: string
  status: string
  serverInfo?: unknown
}

export interface McpToolInfo {
  name: string
  description: string
  inputSchema?: unknown
}

export interface McpToolsResponse {
  serverName?: string
  tools: McpToolInfo[]
  count: number
}

export interface McpReconnectResponse {
  success: boolean
  serverName: string
  status?: string
  toolsCount: number
  error?: string
}

export interface CodexSessionOptions {
  workingDirectory?: string
  configOverrides?: Record<string, string>
  permissionRequester?: PermissionRequester
  model?: string
  sandboxMode?: 'off' | 'read-only' | 'full'
  approvalPolicy?: ApprovalMode
  developerInstructions?: string
  reasoningEffort?: string
  reasoningSummary?: ReasoningSummary
  webSearchEnabled?: boolean
}

export interface AgentMessageInput {
  text: string
  sessionId?: string
  images?: string[]
}

export class CodexSession extends EventEmitter {
  private client: CodexAppServerClient | null = null
  private threadId: string | null = null
  private currentTurnId: string | null = null
  private options: CodexSessionOptions
  private permissionMode: PermissionMode = 'default'
  private currentModel: string | undefined
  private streamAdapter: CodexAppServerStreamAdapter | null = null

  constructor(options: CodexSessionOptions = {}) {
    super()
    this.options = options
    this.currentModel = options.model
  }

  async connect(): Promise<ThreadInfo> {
    if (this.client) {
      throw new Error('Already connected')
    }

    const spawnOptions: SpawnOptions = {
      workingDirectory: this.options.workingDirectory,
      configOverrides: this.options.configOverrides,
    }

    this.client = CodexAppServerClient.create(spawnOptions)

    // 设置事件处理
    this.client.on('event', (event: AppServerEvent) => {
      this.handleAppServerEvent(event)
    })

    // 初始化
    await this.client.initialize('claude-code-plus', 'Claude Code Plus', '1.0.0')

    // 创建线程
    const sandboxPolicy = this.resolveSandboxMode()
    const thread = await this.client.startThread({
      model: this.currentModel,
      cwd: this.options.workingDirectory,
      approvalPolicy: this.resolveApprovalPolicy(),
      sandbox: sandboxPolicy,
      developerInstructions: this.options.developerInstructions,
      config: this.buildThreadConfig(),
    })

    this.threadId = thread.id

    // 初始化 StreamAdapter
    this.streamAdapter = new CodexAppServerStreamAdapter(() => this.threadId || undefined)

    return thread
  }

  async sendMessage(input: AgentMessageInput): Promise<void> {
    if (!this.client || !this.threadId) {
      throw new Error('Not connected')
    }

    const turn = await this.client.startTurn({
      threadId: this.threadId,
      message: input.text,
      images: input.images,
      cwd: this.options.workingDirectory,
      model: this.currentModel,
      approvalPolicy: this.resolveApprovalPolicy(),
      sandboxPolicy: this.resolveSandboxPolicy(),
      effort: this.options.reasoningEffort,
      summary: this.options.reasoningSummary,
    })

    this.currentTurnId = turn.id
  }

  async interrupt(): Promise<void> {
    if (!this.client || !this.threadId || !this.currentTurnId) {
      return
    }

    try {
      await this.client.interruptTurn(this.threadId, this.currentTurnId)
    } catch {
      // ignore interrupt errors
    }
  }

  async disconnect(): Promise<void> {
    if (this.client) {
      this.client.close()
      this.client = null
    }
    this.threadId = null
    this.currentTurnId = null
    this.streamAdapter = null
  }

  setModel(model: string): void {
    this.currentModel = model
  }

  setPermissionMode(mode: PermissionMode): void {
    this.permissionMode = mode
  }

  get isConnected(): boolean {
    return this.client !== null && this.client.isAlive
  }

  // ============== MCP 相关方法 ==============

  async getMcpStatus(): Promise<McpServerStatusInfo[]> {
    if (!this.client) return []
    const statuses = await this.fetchMcpServerStatuses()
    return statuses.map(status => ({
      name: status.name,
      status: this.toDisplayStatus(status),
      serverInfo: this.toServerInfoJson(status),
    }))
  }

  async getMcpTools(serverName?: string): Promise<McpToolsResponse> {
    if (!this.client) return { serverName, tools: [], count: 0 }
    const statuses = await this.fetchMcpServerStatuses()
    const filtered = serverName ? statuses.filter(s => s.name === serverName) : statuses
    const tools = filtered.flatMap(status =>
      Object.entries(status.tools || {}).map(([toolName, tool]) => ({
        name: tool.name || toolName,
        description: tool.description || '',
        inputSchema: tool.inputSchema,
      }))
    )
    return { serverName, tools, count: tools.length }
  }

  async reconnectMcp(serverName: string): Promise<McpReconnectResponse> {
    if (!this.client) {
      return { success: false, serverName, toolsCount: 0, error: 'Not connected' }
    }

    try {
      const statuses = await this.fetchMcpServerStatuses()
      const serverStatus = statuses.find(s => s.name === serverName)
      if (!serverStatus) {
        return { success: false, serverName, toolsCount: 0, error: 'MCP server not found' }
      }

      // 如果需要认证，启动 OAuth
      if (serverStatus.authStatus?.toLowerCase() === 'notloggedin') {
        await this.client.startMcpOauthLogin(serverName)
      }

      // 重启 AppServer
      await this.restartAppServer()

      const refreshedStatuses = await this.fetchMcpServerStatuses()
      const refreshedStatus = refreshedStatuses.find(s => s.name === serverName)

      return {
        success: true,
        serverName,
        status: refreshedStatus ? this.toDisplayStatus(refreshedStatus) : undefined,
        toolsCount: Object.keys(refreshedStatus?.tools || {}).length,
      }
    } catch (e) {
      return {
        success: false,
        serverName,
        toolsCount: 0,
        error: e instanceof Error ? e.message : String(e),
      }
    }
  }

  async startMcpOauthLogin(serverName: string): Promise<string | undefined> {
    if (!this.client) return undefined
    const statuses = await this.fetchMcpServerStatuses()
    const status = statuses.find(s => s.name === serverName)
    if (!status || status.authStatus?.toLowerCase() !== 'notloggedin') return undefined

    const result = await this.client.startMcpOauthLogin(serverName)
    return result?.authorizationUrl
  }

  setSandboxMode(mode: 'off' | 'read-only' | 'full'): void {
    this.options.sandboxMode = mode
  }

  getCurrentSandboxMode(): 'off' | 'read-only' | 'full' | undefined {
    return this.options.sandboxMode
  }

  getCapabilities(): { canSwitchModel: boolean; canSwitchPermissionMode: boolean } {
    return {
      canSwitchModel: true,
      canSwitchPermissionMode: true,
    }
  }

  getProviderSessionId(): string | undefined {
    return this.threadId || undefined
  }

  // ============== 私有 MCP 辅助方法 ==============

  private async fetchMcpServerStatuses(): Promise<McpServerStatus[]> {
    if (!this.client) return []
    const result: McpServerStatus[] = []
    let cursor: string | undefined
    do {
      const page = await this.client.listMcpServerStatus(cursor)
      result.push(...page.data)
      cursor = page.nextCursor || undefined
    } while (cursor)
    return result
  }

  private async restartAppServer(): Promise<void> {
    const threadIdToResume = this.threadId
    await this.disconnect()

    // 重新创建连接
    const spawnOptions: SpawnOptions = {
      workingDirectory: this.options.workingDirectory,
      configOverrides: this.options.configOverrides,
    }

    this.client = CodexAppServerClient.create(spawnOptions)
    this.client.on('event', (event: AppServerEvent) => this.handleAppServerEvent(event))

    await this.client.initialize('claude-code-plus', 'Claude Code Plus', '1.0.0')

    if (threadIdToResume) {
      const thread = await this.client.resumeThread(threadIdToResume)
      this.threadId = thread.id
    }

    this.streamAdapter = new CodexAppServerStreamAdapter(() => this.threadId || undefined)
  }

  private toDisplayStatus(status: McpServerStatus): string {
    return status.authStatus?.toLowerCase() === 'notloggedin' ? 'needs-auth' : 'connected'
  }

  private toServerInfoJson(status: McpServerStatus): unknown {
    return {
      authStatus: status.authStatus,
      toolsCount: Object.keys(status.tools || {}).length,
      resourcesCount: status.resources?.length || 0,
      resourceTemplatesCount: status.resourceTemplates?.length || 0,
    }
  }

  private handleAppServerEvent(event: AppServerEvent): void {
    // 处理审批请求
    if (event.type === 'commandApprovalRequired') {
      this.handleCommandApproval(event)
      return
    }
    if (event.type === 'fileChangeApprovalRequired') {
      this.handleFileChangeApproval(event)
      return
    }

    // 转换并转发事件
    if (this.streamAdapter) {
      const normalizedEvents = this.streamAdapter.convert(event)
      for (const normalized of normalizedEvents) {
        this.emit('normalizedEvent', normalized)
      }
    }

    // 同时发送原始事件
    this.emit('event', event)

    // 处理 turn 完成
    if (event.type === 'turnCompleted') {
      this.currentTurnId = null
    }
  }

  private async handleCommandApproval(event: AppServerEvent & { type: 'commandApprovalRequired' }): Promise<void> {
    const bypass = this.permissionMode === 'bypassPermissions'

    let approved = bypass

    if (!bypass && this.options.permissionRequester) {
      const result = await this.options.permissionRequester({
        toolName: 'Bash',
        toolUseId: event.itemId,
        command: event.command,
        cwd: event.cwd,
        reason: event.reason,
      })
      approved = result.approved
    }

    // 默认行为
    const approveByDefault = bypass || this.options.approvalPolicy === 'never'
    const finalApproved = approved || approveByDefault

    if (this.client) {
      if (finalApproved) {
        await this.client.acceptCommand(event.rawId, false)
      } else {
        await this.client.declineCommand(event.rawId)
      }
    }
  }

  private async handleFileChangeApproval(
    event: AppServerEvent & { type: 'fileChangeApprovalRequired' }
  ): Promise<void> {
    const bypass = this.permissionMode === 'bypassPermissions'
    const acceptEdits = this.permissionMode === 'acceptEdits'

    let approved = bypass || acceptEdits

    if (!approved && this.options.permissionRequester) {
      const result = await this.options.permissionRequester({
        toolName: 'Edit',
        toolUseId: event.itemId,
        input: { changes: event.changes },
        reason: event.reason,
      })
      approved = result.approved
    }

    // 默认行为
    const approveByDefault = bypass || this.options.approvalPolicy === 'never'
    const finalApproved = approved || approveByDefault

    if (this.client) {
      if (finalApproved) {
        await this.client.acceptFileChange(event.rawId)
      } else {
        await this.client.declineFileChange(event.rawId)
      }
    }
  }

  private resolveSandboxMode(): string | undefined {
    switch (this.options.sandboxMode) {
      case 'off':
        return undefined
      case 'read-only':
        return 'read-only'
      case 'full':
        return 'full'
      default:
        return undefined
    }
  }

  private resolveSandboxPolicy(): SandboxPolicy | undefined {
    switch (this.options.sandboxMode) {
      case 'off':
        return { type: 'dangerFullAccess' }
      case 'read-only':
        return { type: 'readOnly' }
      case 'full':
        return {
          type: 'workspaceWrite',
          writableRoots: this.options.workingDirectory ? [this.options.workingDirectory] : [],
          networkAccess: this.options.webSearchEnabled ?? false,
        }
      default:
        return undefined
    }
  }

  private resolveApprovalPolicy(): string | undefined {
    switch (this.permissionMode) {
      case 'bypassPermissions':
        return 'never'
      case 'acceptEdits':
        return 'auto-edit'
      case 'plan':
        return 'always'
      default:
        return this.options.approvalPolicy
    }
  }

  private buildThreadConfig(): Record<string, unknown> | undefined {
    const config: Record<string, unknown> = {}

    if (this.options.webSearchEnabled !== undefined) {
      config['features.web_search_request'] = this.options.webSearchEnabled
    }

    return Object.keys(config).length > 0 ? config : undefined
  }
}
