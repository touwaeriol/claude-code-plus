/**
 * Claude Backend Session Implementation
 *
 * This module implements the BackendSession interface for Claude using RSocket.
 * It wraps the existing RSocketSession and maps RSocket events to BackendEvent types.
 */

import {
  BaseBackendSession,
  type BackendSession,
  type SessionConnectOptions,
  type UserMessage,
  type ApprovalResponse,
  type HistoryLoadOptions,
  type HistoryLoadResult,
  type MessageContent,
} from './BackendSession'
import type {
  BackendCapabilities,
  BackendConfig,
  ClaudeBackendConfig,
} from '@/types/backend'
import { isClaudeConfig } from '@/types/backend'
import type { ThinkingConfig } from '@/types/thinking'
import { RSocketSession } from '../rsocket/RSocketSession'
import type {
  RpcMessage,
  RpcContentBlock,
  RpcConnectOptions,
  RpcStreamEvent,
  RpcToolUseBlock,
} from '@/types/rpc'

// ============================================================================
// Claude Capabilities Constant
// ============================================================================

const CLAUDE_CAPABILITIES: BackendCapabilities = {
  type: 'claude',
  displayName: 'Claude',
  supportsThinking: true,
  thinkingConfigType: 'token_budget',
  supportsSubAgents: false,
  supportsMcp: true,
  supportsSandbox: false,
  supportsPromptCaching: true,
  exposesTokenUsage: true,
  supportedTools: ['read', 'write', 'edit', 'bash', 'mcp'],
  availableModels: [
    {
      id: 'claude-sonnet-4-5-20250929',
      displayName: 'Claude Sonnet 4.5',
      isDefault: false,
      supportsThinking: true,
      description: 'Claude Sonnet with extended thinking',
    },
    {
      id: 'claude-opus-4-5-20251101',
      displayName: 'Claude Opus 4.5',
      isDefault: true,
      supportsThinking: true,
      description: 'Most capable Claude model',
    },
    {
      id: 'claude-3-5-sonnet-20241022',
      displayName: 'Claude 3.5 Sonnet',
      supportsThinking: false,
      description: 'Previous generation Claude',
    },
  ],
}

// ============================================================================
// ClaudeSession Implementation
// ============================================================================

/**
 * Claude backend session using RSocket
 */
export class ClaudeSession extends BaseBackendSession implements BackendSession {
  private rsocket: RSocketSession | null = null
  private config: ClaudeBackendConfig
  private wsUrl: string | undefined

  // Tracking current turn state
  private currentTurnItems = new Map<string, string>() // itemId -> type
  private currentApprovalRequest: string | null = null

  constructor(config: BackendConfig, wsUrl?: string) {
    super('claude', config)

    if (!isClaudeConfig(config)) {
      throw new Error('ClaudeSession requires ClaudeBackendConfig')
    }

    this.config = config
    this.wsUrl = wsUrl
  }

  // ==========================================================================
  // Lifecycle
  // ==========================================================================

  async connect(options: SessionConnectOptions): Promise<void> {
    this.setConnectionStatus('connecting')

    try {
      // Create RSocket session
      this.rsocket = new RSocketSession(this.wsUrl)

      // Subscribe to RSocket events
      this.rsocket.onMessage(this.handleRpcMessage.bind(this))
      this.rsocket.onError(this.handleRpcError.bind(this))
      this.rsocket.onDisconnect(this.handleRpcDisconnect.bind(this))

      // Convert BackendConfig to RpcConnectOptions
      const connectOptions = this.buildConnectOptions(options)

      // Connect to backend
      const sessionId = await this.rsocket.connect(connectOptions)

      this.setSessionId(sessionId)
      this.setConnectionStatus('connected')
      this.setError(null)
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error)
      this.setError(errorMessage)
      this.setConnectionStatus('error')
      throw error
    }
  }

  disconnect(): void {
    if (this.rsocket) {
      this.rsocket.disconnect()
      this.rsocket = null
    }

    this.setConnectionStatus('disconnected')
    this.setSessionId(null)
    this.currentTurnItems.clear()
    this.currentApprovalRequest = null
  }

  // ==========================================================================
  // Messaging
  // ==========================================================================

  sendMessage(message: UserMessage): void {
    if (!this.rsocket?.isConnected) {
      throw new Error('Not connected to Claude backend')
    }

    this.setGenerating(true)

    // Convert UserMessage to RSocket format
    const content = this.convertMessageContent(message.contents)

    // Apply thinking config if provided
    if (message.thinkingConfig) {
      this.updateThinkingConfig(message.thinkingConfig).catch((error) => {
        console.error('[ClaudeSession] Failed to update thinking config:', error)
      })
    }

    // Send message
    if (content.length === 1 && content[0].type === 'text') {
      // Simple text message
      this.rsocket.sendMessage((content[0] as any).text)
    } else {
      // Rich content message
      this.rsocket.sendMessageWithContent(content)
    }
  }

  async interrupt(): Promise<void> {
    if (!this.rsocket?.isConnected) {
      throw new Error('Not connected to Claude backend')
    }

    await this.rsocket.interrupt()
    this.setGenerating(false)
  }

  async runInBackground(): Promise<void> {
    if (!this.rsocket?.isConnected) {
      throw new Error('Not connected to Claude backend')
    }

    await this.rsocket.runInBackground()
    this.setGenerating(false)
  }

  // ==========================================================================
  // Approval Handling
  // ==========================================================================

  respondToApproval(response: ApprovalResponse): void {
    // Claude uses the register() callback pattern for approvals
    // This is handled through the registered handler in handleRpcMessage
    // Store the response for the handler to pick up
    if (this.currentApprovalRequest === response.requestId) {
      // TODO: Implement approval response mechanism
      console.warn('[ClaudeSession] Approval response not yet implemented:', response)
    }
  }

  // ==========================================================================
  // Configuration
  // ==========================================================================

  async updateConfig(config: Partial<BackendConfig>): Promise<void> {
    if (!this.rsocket?.isConnected) {
      throw new Error('Not connected to Claude backend')
    }

    // Update model if changed
    if (config.modelId && config.modelId !== this.config.modelId) {
      await this.rsocket.setModel(config.modelId)
      this.config.modelId = config.modelId
    }

    // Update permission mode if changed
    if (config.permissionMode && config.permissionMode !== this.config.permissionMode) {
      await this.rsocket.setPermissionMode(config.permissionMode as any)
      this.config.permissionMode = config.permissionMode
    }

    // Update thinking config if changed (check for Claude-specific properties)
    const claudeConfig = config as Partial<ClaudeBackendConfig>
    if (claudeConfig.thinkingEnabled !== undefined || claudeConfig.thinkingTokenBudget !== undefined) {
      const thinkingEnabled = claudeConfig.thinkingEnabled ?? this.config.thinkingEnabled
      const budget = claudeConfig.thinkingTokenBudget ?? this.config.thinkingTokenBudget
      await this.rsocket.setMaxThinkingTokens(thinkingEnabled ? budget : 0)
      this.config.thinkingEnabled = thinkingEnabled
      this.config.thinkingTokenBudget = budget
    }

    // Update local config
    Object.assign(this.config, config)
    this.state.config = this.config
  }

  async updateThinkingConfig(thinkingConfig: ThinkingConfig): Promise<void> {
    if (!this.rsocket?.isConnected) {
      throw new Error('Not connected to Claude backend')
    }

    // Handle Claude thinking config
    if (thinkingConfig.type === 'claude') {
      const tokenBudget = thinkingConfig.enabled ? thinkingConfig.tokenBudget || 8096 : 0

      await this.rsocket.setMaxThinkingTokens(tokenBudget)

      // Update local config
      this.config.thinkingEnabled = thinkingConfig.enabled
      this.config.thinkingTokenBudget = tokenBudget
    } else {
      // Codex config passed to Claude - convert effort to token budget
      const effortToTokens: Record<string, number> = {
        minimal: 1024,
        low: 4096,
        medium: 8096,
        high: 16384,
        xhigh: 32768,
      }
      const effort = thinkingConfig.effort
      const tokenBudget = effort ? effortToTokens[effort] || 8096 : 0

      await this.rsocket.setMaxThinkingTokens(tokenBudget)

      this.config.thinkingEnabled = effort !== null
      this.config.thinkingTokenBudget = tokenBudget
    }
  }

  // ==========================================================================
  // State & Capabilities
  // ==========================================================================

  getCapabilities(): BackendCapabilities {
    return CLAUDE_CAPABILITIES
  }

  // ==========================================================================
  // History
  // ==========================================================================

  async loadHistory(_options: HistoryLoadOptions): Promise<HistoryLoadResult> {
    if (!this.rsocket?.isConnected) {
      throw new Error('Not connected to Claude backend')
    }

    const messages = await this.rsocket.getHistory()

    // TODO: Implement pagination using _options.offset/_options.limit
    // For now, return all messages
    return {
      messages,
      hasMore: false,
      totalCount: messages.length,
    }
  }

  // ==========================================================================
  // Private Helper Methods
  // ==========================================================================

  /**
   * Build RSocket connect options from session connect options
   */
  private buildConnectOptions(options: SessionConnectOptions): RpcConnectOptions {
    const rpcOptions: RpcConnectOptions = {
      provider: 'claude',
      model: this.config.modelId,
      systemPrompt: this.config.systemPrompt || undefined,
      permissionMode: this.config.permissionMode as any,
      dangerouslySkipPermissions: this.config.skipPermissions,
      includePartialMessages: this.config.includePartialMessages,
      continueConversation: options.continueConversation,
      resumeSessionId: options.resumeSessionId,
      thinkingEnabled: this.config.thinkingEnabled,
      metadata: options.projectPath ? { projectPath: options.projectPath } : undefined,
    }

    return rpcOptions
  }

  /**
   * Convert UserMessage content to RSocket content blocks
   */
  private convertMessageContent(contents: MessageContent[]): RpcContentBlock[] {
    return contents.map((content) => {
      switch (content.type) {
        case 'text':
          return {
            type: 'text',
            text: content.text,
          }

        case 'image':
          return {
            type: 'image',
            source: {
              type: 'base64',
              media_type: content.mimeType || 'image/png',
              data: content.data,
            },
          }

        case 'file':
          // File context is not directly supported, convert to text
          return {
            type: 'text',
            text: `[File: ${content.path}${content.startLine ? `:${content.startLine}` : ''}]${
              content.content ? `\n${content.content}` : ''
            }`,
          }

        default:
          throw new Error(`Unsupported content type: ${(content as any).type}`)
      }
    })
  }

  /**
   * Handle RSocket RPC messages and map to BackendEvent
   */
  private handleRpcMessage(message: RpcMessage): void {
    const sessionId = this.getSessionId() || 'unknown'
    const timestamp = Date.now()

    switch (message.type) {
      case 'stream_event':
        this.handleStreamEvent(message as RpcStreamEvent, sessionId, timestamp)
        break

      case 'result':
        this.handleResultMessage(message as any, sessionId, timestamp)
        break

      case 'assistant':
        // Assistant message complete
        // Already handled through stream events
        break

      case 'user':
        // User message echo (not needed for BackendEvent)
        break

      case 'error':
        this.emitEvent({
          type: 'error',
          sessionId,
          timestamp,
          code: 'BACKEND_ERROR',
          message: (message as any).message,
        })
        break

      case 'status_system':
        // Status updates (e.g., compacting)
        // Not mapped to BackendEvent currently
        break

      default:
        console.warn('[ClaudeSession] Unknown message type:', message.type)
    }
  }

  /**
   * Handle stream events from RSocket
   */
  private handleStreamEvent(event: RpcStreamEvent, sessionId: string, timestamp: number): void {
    const streamEvent = event.event

    switch (streamEvent.type) {
      case 'content_block_start': {
        const block = streamEvent.content_block
        if (block.type === 'tool_use') {
          const toolBlock = block as RpcToolUseBlock
          this.currentTurnItems.set(toolBlock.id, 'tool')

          this.emitEvent({
            type: 'tool_started',
            sessionId,
            timestamp,
            itemId: toolBlock.id,
            toolType: toolBlock.toolType,
            toolName: toolBlock.toolName,
            parameters: toolBlock.input as Record<string, unknown>,
          })
        }
        break
      }

      case 'content_block_delta': {
        const delta = streamEvent.delta
        const index = streamEvent.index

        // Generate item ID from index
        const itemId = `item-${index}`

        if (delta.type === 'text_delta') {
          this.currentTurnItems.set(itemId, 'text')
          this.emitEvent({
            type: 'text_delta',
            sessionId,
            timestamp,
            itemId,
            text: delta.text,
          })
        } else if (delta.type === 'thinking_delta') {
          this.currentTurnItems.set(itemId, 'thinking')
          this.emitEvent({
            type: 'thinking_delta',
            sessionId,
            timestamp,
            itemId,
            text: delta.thinking,
          })
        } else if (delta.type === 'input_json_delta') {
          // Tool input streaming (not mapped to BackendEvent yet)
        }
        break
      }

      case 'content_block_stop': {
        // Content block finished
        // Tool completion is handled in result message
        break
      }

      case 'message_delta': {
        // Message-level updates (e.g., usage)
        // Not mapped to BackendEvent currently
        break
      }

      case 'message_stop': {
        // Message completed - emit turn completed
        this.setGenerating(false)
        this.emitEvent({
          type: 'turn_completed',
          sessionId,
          timestamp,
          turnId: event.uuid,
          status: 'completed',
        })
        this.currentTurnItems.clear()
        break
      }

      case 'message_start': {
        // Message started
        // Not mapped to BackendEvent currently
        break
      }

      default:
        console.warn('[ClaudeSession] Unknown stream event type:', (streamEvent as any).type)
    }
  }

  /**
   * Handle result messages
   */
  private handleResultMessage(message: any, sessionId: string, timestamp: number): void {
    const status = message.is_error ? 'error' : 'completed'

    // Emit turn completed event
    this.setGenerating(false)
    this.emitEvent({
      type: 'turn_completed',
      sessionId,
      timestamp,
      turnId: message.session_id || sessionId,
      status,
    })

    if (message.is_error) {
      this.emitEvent({
        type: 'error',
        sessionId,
        timestamp,
        code: message.subtype || 'UNKNOWN_ERROR',
        message: message.result || 'Unknown error',
      })
    }

    this.currentTurnItems.clear()
  }

  /**
   * Handle RSocket errors
   */
  private handleRpcError(error: Error): void {
    const errorMessage = error.message || String(error)
    this.setError(errorMessage)

    const sessionId = this.getSessionId() || 'unknown'
    this.emitEvent({
      type: 'error',
      sessionId,
      timestamp: Date.now(),
      code: (error as any).code || 'CONNECTION_ERROR',
      message: errorMessage,
    })
  }

  /**
   * Handle RSocket disconnection
   */
  private handleRpcDisconnect(error?: Error): void {
    this.setConnectionStatus('disconnected')

    if (error) {
      const errorMessage = error.message || String(error)
      this.setError(errorMessage)

      // Emit error event
      const sessionId = this.getSessionId() || 'unknown'
      this.emitEvent({
        type: 'error',
        sessionId,
        timestamp: Date.now(),
        code: 'DISCONNECTED',
        message: `Connection lost: ${errorMessage}`,
      })
    }
  }
}

// ============================================================================
// Factory Function
// ============================================================================

/**
 * Create a new Claude session
 */
export function createClaudeSession(config: ClaudeBackendConfig, wsUrl?: string): ClaudeSession {
  return new ClaudeSession(config, wsUrl)
}
