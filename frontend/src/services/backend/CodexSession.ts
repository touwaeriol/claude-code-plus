/**
 * Codex Session Implementation
 *
 * This module implements BackendSession for OpenAI Codex App Server.
 * It uses JSON-RPC over stdio to communicate with the codex-app-server process.
 */

import type {
  BackendCapabilities,
  BackendConfig,
  CodexBackendConfig,
} from '@/types/backend'
import { isCodexConfig } from '@/types/backend'
import type { ThinkingConfig } from '@/types/thinking'
import {
  BaseBackendSession,
  type SessionConnectOptions,
  type UserMessage,
  type ApprovalResponse,
  type HistoryLoadOptions,
  type HistoryLoadResult,
} from './BackendSession'

// ============================================================================
// JSON-RPC Types
// ============================================================================

/**
 * JSON-RPC Request (Codex doesn't use "jsonrpc": "2.0")
 */
interface JsonRpcRequest {
  id: number
  method: string
  params: Record<string, unknown>
}

/**
 * JSON-RPC Response
 */
interface JsonRpcResponse {
  id: number
  result?: unknown
  error?: {
    code: number
    message: string
    data?: unknown
  }
}

/**
 * JSON-RPC Notification (no id field)
 */
interface JsonRpcNotification {
  method: string
  params: Record<string, unknown>
}

/**
 * JSON-RPC Server Request (needs response)
 */
interface JsonRpcServerRequest {
  id: number
  method: string
  params: Record<string, unknown>
}

// ============================================================================
// Codex API Types
// ============================================================================

/**
 * Thread object returned by Codex
 */
interface CodexThread {
  id: string
  preview: string
  createdAt: string
  lastModified: string
}

/**
 * Thread item types
 */
type CodexItemType =
  | 'userInput'
  | 'agentMessage'
  | 'reasoning'
  | 'commandExecution'
  | 'fileChange'
  | 'mcpToolCall'
  | 'imageView'

/**
 * Base item interface
 */
interface CodexItem {
  type: CodexItemType
  id: string
  status?: 'InProgress' | 'Completed' | 'Cancelled' | 'Applied' | 'Declined'
}

/**
 * User input types
 */
type CodexUserInput =
  | { type: 'text'; text: string }
  | { type: 'image'; data: string; mimeType?: string }
  | { type: 'localImage'; path: string }

/**
 * Approval decision
 */
type ApprovalDecision = 'Approved' | 'Declined'

/**
 * Approval policy
 */
type ApprovalPolicy = 'on-request' | 'never' | 'always'

// ============================================================================
// Codex Session Implementation
// ============================================================================

export class CodexSession extends BaseBackendSession {
  // Communication
  private rpcIdCounter = 1
  private pendingRequests = new Map<
    number,
    {
      resolve: (result: unknown) => void
      reject: (error: Error) => void
    }
  >()
  private pendingApprovals = new Map<
    number,
    {
      resolve: (decision: ApprovalDecision) => void
      reject: (error: Error) => void
    }
  >()

  // Process management
  private backendUrl: string | null = null
  private currentThreadId: string | null = null
  private currentTurnId: string | null = null

  // Capabilities
  private static readonly CAPABILITIES: BackendCapabilities = {
    type: 'codex',
    displayName: 'OpenAI Codex',
    supportsThinking: true,
    thinkingConfigType: 'effort_level',
    supportsSubAgents: false,
    supportsMcp: true,
    supportsSandbox: true,
    sandboxModes: ['read-only', 'workspace-write', 'full-access'],
    supportsPromptCaching: true, // Model-dependent
    exposesTokenUsage: false, // Not exposed in current API
    supportedTools: ['bash', 'write', 'edit', 'mcp'],
    availableModels: [
      {
        id: 'gpt-5.1-codex-max',
        displayName: 'GPT-5.1 Codex Max',
        isDefault: true,
        supportsThinking: false,
      },
      {
        id: 'o3',
        displayName: 'O3',
        supportsThinking: true,
        description: 'Advanced reasoning model',
      },
      {
        id: 'gpt-4o',
        displayName: 'GPT-4o',
        supportsThinking: false,
      },
    ],
  }

  constructor(config: BackendConfig) {
    super('codex', config)

    if (!isCodexConfig(config)) {
      throw new Error('CodexSession requires CodexBackendConfig')
    }
  }

  // ==========================================================================
  // Lifecycle
  // ==========================================================================

  async connect(options: SessionConnectOptions): Promise<void> {
    this.setConnectionStatus('connecting')

    try {
      // Get backend URL from HTTP bridge
      this.backendUrl = await this.getBackendUrl()

      // Initialize connection
      await this.sendRequest('initialize', {
        capabilities: {
          experimental_raw_events: false,
        },
      })

      // Start or resume thread
      if (options.continueConversation && options.resumeSessionId) {
        await this.resumeThread(options.resumeSessionId, options.projectPath)
      } else {
        await this.startThread(options.projectPath)
      }

      this.setConnectionStatus('connected')
      this.startEventPolling()
    } catch (error) {
      this.setConnectionStatus('error')
      this.setError(error instanceof Error ? error.message : String(error))
      throw error
    }
  }

  disconnect(): void {
    if (this.state.connectionStatus === 'disconnected') {
      return
    }

    this.stopEventPolling()
    this.setConnectionStatus('disconnected')
    this.setSessionId(null)
    this.currentThreadId = null
    this.currentTurnId = null
  }

  // ==========================================================================
  // Messaging
  // ==========================================================================

  sendMessage(message: UserMessage): void {
    if (!this.isConnected() || !this.currentThreadId) {
      throw new Error('Not connected to Codex session')
    }

    if (this.state.isGenerating) {
      throw new Error('Already generating a response')
    }

    this.setGenerating(true)

    // Start turn asynchronously
    this.startTurn(message).catch((error) => {
      this.setGenerating(false)
      this.emitEvent({
        type: 'error',
        sessionId: this.currentThreadId!,
        timestamp: Date.now(),
        code: 'TURN_START_FAILED',
        message: error instanceof Error ? error.message : String(error),
      })
    })
  }

  async interrupt(): Promise<void> {
    if (!this.currentThreadId || !this.currentTurnId) {
      return
    }

    try {
      await this.sendRequest('turn/interrupt', {
        threadId: this.currentThreadId,
        turnId: this.currentTurnId,
      })
    } catch (error) {
      console.error('[CodexSession] Failed to interrupt:', error)
      throw error
    }
  }

  // ==========================================================================
  // Approval Handling
  // ==========================================================================

  respondToApproval(response: ApprovalResponse): void {
    const requestId = Number(response.requestId)
    const pending = this.pendingApprovals.get(requestId)

    if (pending) {
      pending.resolve(response.approved ? 'Approved' : 'Declined')
      this.pendingApprovals.delete(requestId)
    } else {
      console.warn('[CodexSession] No pending approval for requestId:', requestId)
    }
  }

  // ==========================================================================
  // Configuration
  // ==========================================================================

  async updateConfig(config: Partial<BackendConfig>): Promise<void> {
    // Merge with current config (type assertion needed for spread)
    this.state.config = { ...this.state.config, ...config } as BackendConfig

    // Most config changes require reconnection
    // Only some runtime settings can be changed
    console.warn('[CodexSession] Config updated, reconnection may be needed')
  }

  async updateThinkingConfig(thinkingConfig: ThinkingConfig): Promise<void> {
    if (!isCodexConfig(this.state.config)) {
      return
    }

    // Map thinking config to Codex reasoning effort
    const effort = this.mapThinkingToEffort(thinkingConfig)

    this.state.config = {
      ...this.state.config,
      reasoningEffort: effort,
    }
  }

  // ==========================================================================
  // State & Capabilities
  // ==========================================================================

  getCapabilities(): BackendCapabilities {
    return CodexSession.CAPABILITIES
  }

  // ==========================================================================
  // History
  // ==========================================================================

  async loadHistory(options: HistoryLoadOptions): Promise<HistoryLoadResult> {
    try {
      const response = await this.sendRequest('thread/list', {})

      const threads = (response as { threads: CodexThread[] }).threads || []

      // Filter by session ID if provided
      const filtered = options.sessionId
        ? threads.filter((t) => t.id === options.sessionId)
        : threads

      // Apply pagination
      const offset = options.offset || 0
      const limit = options.limit || 50
      const start = offset < 0 ? Math.max(0, filtered.length + offset) : offset
      const messages = filtered.slice(start, start + limit)

      return {
        messages,
        hasMore: start + limit < filtered.length,
        totalCount: filtered.length,
      }
    } catch (error) {
      console.error('[CodexSession] Failed to load history:', error)
      return {
        messages: [],
        hasMore: false,
        totalCount: 0,
      }
    }
  }

  // ==========================================================================
  // Private: Thread Management
  // ==========================================================================

  private async startThread(projectPath?: string): Promise<void> {
    const config = this.state.config as CodexBackendConfig

    const response = await this.sendRequest('thread/start', {
      model: config.modelId,
      modelProvider: config.modelProvider,
      cwd: projectPath || process.cwd(),
      approvalPolicy: this.mapPermissionModeToApprovalPolicy(config.permissionMode),
      sandbox: config.sandboxMode,
      baseInstructions: config.systemPrompt || undefined,
      modelReasoningEffort: config.reasoningEffort || undefined,
    })

    const thread = (response as { thread: CodexThread }).thread
    this.currentThreadId = thread.id
    this.setSessionId(thread.id)
  }

  private async resumeThread(threadId: string, projectPath?: string): Promise<void> {
    const response = await this.sendRequest('thread/resume', {
      threadId,
      cwd: projectPath || process.cwd(),
    })

    const thread = (response as { thread: CodexThread }).thread
    this.currentThreadId = thread.id
    this.setSessionId(thread.id)
  }

  private async startTurn(message: UserMessage): Promise<void> {
    if (!this.currentThreadId) {
      throw new Error('No active thread')
    }

    const config = this.state.config as CodexBackendConfig

    const response = await this.sendRequest('turn/start', {
      threadId: this.currentThreadId,
      input: this.convertMessageToInput(message),
      effort: message.thinkingConfig
        ? this.mapThinkingToEffort(message.thinkingConfig)
        : config.reasoningEffort || undefined,
      summary: config.reasoningSummary || 'auto',
    })

    this.currentTurnId = (response as { turnId: string }).turnId
  }

  // ==========================================================================
  // Private: JSON-RPC Communication
  // ==========================================================================

  private async getBackendUrl(): Promise<string> {
    // In the actual implementation, this would query the HTTP bridge
    // to get the codex-app-server endpoint
    try {
      const response = await fetch('/api/codex/endpoint')

      if (!response.ok) {
        throw new Error(
          `Codex 后端不可用 (HTTP ${response.status})。` +
          `Codex 集成功能正在开发中，请暂时使用 Claude 后端。`
        )
      }

      const contentType = response.headers.get('content-type')
      if (!contentType?.includes('application/json')) {
        throw new Error(
          `Codex 后端返回了无效的响应格式。` +
          `Codex 集成功能正在开发中，请暂时使用 Claude 后端。`
        )
      }

      const data = await response.json()
      if (!data.url) {
        throw new Error(
          `Codex 后端配置不完整（缺少 URL）。` +
          `Codex 集成功能正在开发中，请暂时使用 Claude 后端。`
        )
      }

      return data.url
    } catch (error) {
      if (error instanceof TypeError && error.message.includes('fetch')) {
        throw new Error(
          `无法连接到服务器。请检查后端服务是否正在运行。`
        )
      }
      // Re-throw with better message if it's a JSON parse error
      if (error instanceof SyntaxError) {
        throw new Error(
          `Codex 后端 API 尚未实现。` +
          `Codex 集成功能正在开发中，请暂时使用 Claude 后端。`
        )
      }
      throw error
    }
  }

  private async sendRequest(method: string, params: Record<string, unknown>): Promise<unknown> {
    if (!this.backendUrl) {
      throw new Error('Backend URL not set')
    }

    const id = this.rpcIdCounter++
    const request: JsonRpcRequest = { id, method, params }

    return new Promise((resolve, reject) => {
      this.pendingRequests.set(id, { resolve, reject })

      // Send via HTTP bridge
      fetch(`${this.backendUrl}/rpc`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      })
        .then((res) => res.json())
        .then((response: JsonRpcResponse) => {
          this.handleRpcResponse(response)
        })
        .catch((error) => {
          const pending = this.pendingRequests.get(id)
          if (pending) {
            pending.reject(error)
            this.pendingRequests.delete(id)
          }
        })

      // Timeout after 30 seconds
      setTimeout(() => {
        const pending = this.pendingRequests.get(id)
        if (pending) {
          pending.reject(new Error('Request timeout'))
          this.pendingRequests.delete(id)
        }
      }, 30000)
    })
  }

  private handleRpcResponse(response: JsonRpcResponse): void {
    const pending = this.pendingRequests.get(response.id)
    if (!pending) {
      console.warn('[CodexSession] Unexpected RPC response:', response.id)
      return
    }

    this.pendingRequests.delete(response.id)

    if (response.error) {
      pending.reject(
        new Error(`RPC Error ${response.error.code}: ${response.error.message}`)
      )
    } else {
      pending.resolve(response.result)
    }
  }

  // ==========================================================================
  // Private: Event Polling
  // ==========================================================================

  private eventPollingInterval: number | null = null

  private startEventPolling(): void {
    if (this.eventPollingInterval) {
      return
    }

    // Poll for events every 100ms
    this.eventPollingInterval = window.setInterval(() => {
      this.pollEvents()
    }, 100)
  }

  private stopEventPolling(): void {
    if (this.eventPollingInterval) {
      window.clearInterval(this.eventPollingInterval)
      this.eventPollingInterval = null
    }
  }

  private async pollEvents(): Promise<void> {
    if (!this.backendUrl) {
      return
    }

    try {
      const response = await fetch(`${this.backendUrl}/events`)
      const events = await response.json()

      for (const event of events) {
        if ('id' in event) {
          // Server request
          this.handleServerRequest(event as JsonRpcServerRequest)
        } else {
          // Notification
          this.handleNotification(event as JsonRpcNotification)
        }
      }
    } catch (error) {
      console.error('[CodexSession] Event polling error:', error)
    }
  }

  private handleNotification(notification: JsonRpcNotification): void {
    const { method, params } = notification

    const sessionId = this.currentThreadId || 'unknown'
    const timestamp = Date.now()

    switch (method) {
      case 'item/agentMessage/delta':
        this.emitEvent({
          type: 'text_delta',
          sessionId,
          timestamp,
          itemId: params.itemId as string,
          text: params.textDelta as string,
        })
        break

      case 'item/reasoning/summaryTextDelta':
        this.emitEvent({
          type: 'thinking_delta',
          sessionId,
          timestamp,
          itemId: params.itemId as string,
          text: params.textDelta as string,
        })
        break

      case 'item/started': {
        const item = params.item as CodexItem
        if (item.type === 'commandExecution') {
          this.emitEvent({
            type: 'tool_started',
            sessionId,
            timestamp,
            itemId: item.id,
            toolType: 'bash',
            toolName: 'commandExecution',
            parameters: params as Record<string, unknown>,
          })
        } else if (item.type === 'fileChange') {
          this.emitEvent({
            type: 'tool_started',
            sessionId,
            timestamp,
            itemId: item.id,
            toolType: 'edit',
            toolName: 'fileChange',
            parameters: params as Record<string, unknown>,
          })
        } else if (item.type === 'mcpToolCall') {
          this.emitEvent({
            type: 'tool_started',
            sessionId,
            timestamp,
            itemId: item.id,
            toolType: 'mcp',
            toolName: 'mcpToolCall',
            parameters: params as Record<string, unknown>,
          })
        }
        break
      }

      case 'item/commandExecution/outputDelta':
        this.emitEvent({
          type: 'tool_output',
          sessionId,
          timestamp,
          itemId: params.itemId as string,
          output: params.outputDelta as string,
        })
        break

      case 'item/fileChange/outputDelta':
        this.emitEvent({
          type: 'tool_output',
          sessionId,
          timestamp,
          itemId: params.itemId as string,
          output: params.outputDelta as string,
        })
        break

      case 'item/completed': {
        const item = params.item as CodexItem
        this.emitEvent({
          type: 'tool_completed',
          sessionId,
          timestamp,
          itemId: item.id,
          success: item.status === 'Completed' || item.status === 'Applied',
          result: params,
        })
        break
      }

      case 'turn/completed':
        this.setGenerating(false)
        this.emitEvent({
          type: 'turn_completed',
          sessionId,
          timestamp,
          turnId: params.turnId as string,
          status: this.mapTurnStatus(params.status as string),
        })
        this.currentTurnId = null
        break

      case 'error':
        this.emitEvent({
          type: 'error',
          sessionId,
          timestamp,
          code: params.code as string,
          message: params.message as string,
          details: params.details,
        })
        break

      default:
        console.log('[CodexSession] Unhandled notification:', method)
    }
  }

  private handleServerRequest(request: JsonRpcServerRequest): void {
    const { id, method, params } = request

    switch (method) {
      case 'item/commandExecution/requestApproval':
        this.handleCommandApprovalRequest(id, params)
        break

      case 'item/fileChange/requestApproval':
        this.handleFileChangeApprovalRequest(id, params)
        break

      default:
        console.warn('[CodexSession] Unhandled server request:', method)
        this.sendApprovalResponse(id, 'Declined')
    }
  }

  private handleCommandApprovalRequest(
    requestId: number,
    params: Record<string, unknown>
  ): void {
    // Emit approval request event
    this.emitEvent({
      type: 'approval_request',
      sessionId: this.currentThreadId || 'unknown',
      timestamp: Date.now(),
      requestId: String(requestId),
      approvalType: 'command',
      details: params,
    })

    // Wait for user response via respondToApproval()
    new Promise<ApprovalDecision>((resolve, reject) => {
      this.pendingApprovals.set(requestId, { resolve, reject })

      // Timeout after 5 minutes
      setTimeout(() => {
        if (this.pendingApprovals.has(requestId)) {
          this.pendingApprovals.delete(requestId)
          reject(new Error('Approval timeout'))
        }
      }, 300000)
    })
      .then((decision) => {
        this.sendApprovalResponse(requestId, decision)
      })
      .catch((error) => {
        console.error('[CodexSession] Approval error:', error)
        this.sendApprovalResponse(requestId, 'Declined')
      })
  }

  private handleFileChangeApprovalRequest(
    requestId: number,
    params: Record<string, unknown>
  ): void {
    // Emit approval request event
    this.emitEvent({
      type: 'approval_request',
      sessionId: this.currentThreadId || 'unknown',
      timestamp: Date.now(),
      requestId: String(requestId),
      approvalType: 'file_change',
      details: params,
    })

    // Wait for user response
    new Promise<ApprovalDecision>((resolve, reject) => {
      this.pendingApprovals.set(requestId, { resolve, reject })

      // Timeout after 5 minutes
      setTimeout(() => {
        if (this.pendingApprovals.has(requestId)) {
          this.pendingApprovals.delete(requestId)
          reject(new Error('Approval timeout'))
        }
      }, 300000)
    })
      .then((decision) => {
        this.sendApprovalResponse(requestId, decision)
      })
      .catch((error) => {
        console.error('[CodexSession] Approval error:', error)
        this.sendApprovalResponse(requestId, 'Declined')
      })
  }

  private async sendApprovalResponse(
    requestId: number,
    decision: ApprovalDecision
  ): Promise<void> {
    if (!this.backendUrl) {
      return
    }

    const response: JsonRpcResponse = {
      id: requestId,
      result: { decision },
    }

    try {
      await fetch(`${this.backendUrl}/rpc`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(response),
      })
    } catch (error) {
      console.error('[CodexSession] Failed to send approval response:', error)
    }
  }

  // ==========================================================================
  // Private: Conversion Utilities
  // ==========================================================================

  private convertMessageToInput(message: UserMessage): CodexUserInput[] {
    const inputs: CodexUserInput[] = []

    // Add main content
    for (const content of message.contents) {
      if (content.type === 'text') {
        inputs.push({ type: 'text', text: content.text })
      } else if (content.type === 'image') {
        inputs.push({
          type: 'image',
          data: content.data,
          mimeType: content.mimeType,
        })
      } else if (content.type === 'file') {
        // For file context, include as text reference
        const text = content.content
          ? `File: ${content.path}\n\`\`\`\n${content.content}\n\`\`\``
          : `File: ${content.path}`
        inputs.push({ type: 'text', text })
      }
    }

    // Add context files
    if (message.contexts) {
      for (const context of message.contexts) {
        const text = context.content
          ? `Context: ${context.path}\n\`\`\`\n${context.content}\n\`\`\``
          : `Context: ${context.path}`
        inputs.push({ type: 'text', text })
      }
    }

    return inputs
  }

  private mapPermissionModeToApprovalPolicy(mode: string): ApprovalPolicy {
    switch (mode) {
      case 'bypass':
        return 'never'
      case 'plan':
      case 'default':
      case 'accept-edits':
      default:
        return 'on-request'
    }
  }

  private mapThinkingToEffort(
    config: ThinkingConfig
  ): 'minimal' | 'low' | 'medium' | 'high' | 'xhigh' | null {
    // Handle Codex config directly
    if (config.type === 'codex') {
      return config.effort
    }

    // Handle Claude config - map token budget to effort level
    if (config.type === 'claude') {
      if (!config.enabled) {
        return null
      }

      const budget = config.tokenBudget || 8096
      if (budget < 2048) return 'minimal'
      if (budget < 8096) return 'low'
      if (budget < 16000) return 'medium'
      if (budget < 32000) return 'high'
      return 'xhigh'
    }

    return null
  }

  private mapTurnStatus(status: string): 'completed' | 'interrupted' | 'error' {
    switch (status) {
      case 'Completed':
        return 'completed'
      case 'Cancelled':
        return 'interrupted'
      case 'Error':
        return 'error'
      default:
        return 'completed'
    }
  }
}
