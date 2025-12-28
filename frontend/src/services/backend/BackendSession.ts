/**
 * Backend Session Interface
 *
 * This module defines the unified interface for backend sessions.
 * Both Claude and Codex sessions implement this interface, allowing
 * the frontend to work with any backend type transparently.
 */

import type {
  BackendType,
  BackendConfig,
  BackendCapabilities,
  BackendEvent,
  BackendConnectionStatus,
  BackendSessionState,
} from '@/types/backend'
import type { ThinkingConfig } from '@/types/thinking'

// ============================================================================
// Message Types
// ============================================================================

/**
 * User message content types
 */
export interface TextContent {
  type: 'text'
  text: string
}

export interface ImageContent {
  type: 'image'
  /** Base64 encoded image or URL */
  data: string
  /** MIME type */
  mimeType?: string
}

export interface FileContext {
  type: 'file'
  path: string
  /** Optional line range */
  startLine?: number
  endLine?: number
  /** File content (if pre-loaded) */
  content?: string
}

export type MessageContent = TextContent | ImageContent | FileContext

/**
 * User message to send to backend
 */
export interface UserMessage {
  /** Message contents */
  contents: MessageContent[]

  /** Context files to include */
  contexts?: FileContext[]

  /** Optional thinking config override for this message */
  thinkingConfig?: ThinkingConfig
}

/**
 * Approval response from user
 */
export interface ApprovalResponse {
  /** Request ID being responded to */
  requestId: string

  /** Whether approved */
  approved: boolean

  /** Optional reason for rejection */
  reason?: string

  /** Optional permission mode suggestion */
  permissionModeUpdate?: string
}

// ============================================================================
// Session Connect Options
// ============================================================================

/**
 * Options for connecting/creating a session
 */
export interface SessionConnectOptions {
  /** Backend configuration */
  config: BackendConfig

  /** Whether to continue an existing conversation */
  continueConversation?: boolean

  /** Session ID to resume (if continuing) */
  resumeSessionId?: string

  /** Project path for context */
  projectPath?: string
}

// ============================================================================
// Event Callback Types
// ============================================================================

/**
 * Callback for backend events
 */
export type BackendEventCallback = (event: BackendEvent) => void

/**
 * Unsubscribe function returned by event subscription
 */
export type UnsubscribeFn = () => void

// ============================================================================
// Backend Session Interface
// ============================================================================

/**
 * Backend Session Interface
 *
 * This is the core abstraction that both ClaudeSession and CodexSession implement.
 * It provides a unified API for:
 * - Connection lifecycle management
 * - Sending messages and receiving streaming responses
 * - Interrupting ongoing generation
 * - Handling approval requests
 * - Getting backend capabilities
 */
export interface BackendSession {
  // ===========================================================================
  // Lifecycle
  // ===========================================================================

  /**
   * Connect to the backend and start/resume a session
   *
   * @param options Connection options including configuration
   * @returns Promise that resolves when connected
   * @throws Error if connection fails
   */
  connect(options: SessionConnectOptions): Promise<void>

  /**
   * Disconnect from the backend
   *
   * This should gracefully close the connection and clean up resources.
   * It should not throw even if already disconnected.
   */
  disconnect(): void

  /**
   * Check if session is connected
   */
  isConnected(): boolean

  // ===========================================================================
  // Messaging
  // ===========================================================================

  /**
   * Send a message to the backend
   *
   * This starts a new "turn" in the conversation. Events will be emitted
   * as the response streams in.
   *
   * @param message User message to send
   * @throws Error if not connected or already generating
   */
  sendMessage(message: UserMessage): void

  /**
   * Interrupt the current generation
   *
   * This requests the backend to stop generating. The turn will complete
   * with an 'interrupted' status.
   *
   * @returns Promise that resolves when interruption is acknowledged
   */
  interrupt(): Promise<void>

  /**
   * Run current task in background (Claude-specific)
   *
   * For backends that don't support this, it should be a no-op.
   */
  runInBackground(): Promise<void>

  // ===========================================================================
  // Approval Handling
  // ===========================================================================

  /**
   * Respond to an approval request
   *
   * @param response Approval response
   */
  respondToApproval(response: ApprovalResponse): void

  // ===========================================================================
  // Configuration
  // ===========================================================================

  /**
   * Update session configuration
   *
   * Not all settings can be changed mid-session. Implementations should
   * apply what they can and ignore unsupported changes.
   *
   * @param config Partial configuration to update
   */
  updateConfig(config: Partial<BackendConfig>): Promise<void>

  /**
   * Update thinking configuration specifically
   *
   * @param thinkingConfig New thinking configuration
   */
  updateThinkingConfig(thinkingConfig: ThinkingConfig): Promise<void>

  // ===========================================================================
  // State & Capabilities
  // ===========================================================================

  /**
   * Get current session state
   */
  getState(): BackendSessionState

  /**
   * Get backend capabilities
   */
  getCapabilities(): BackendCapabilities

  /**
   * Get backend type
   */
  getBackendType(): BackendType

  /**
   * Get session ID assigned by backend
   */
  getSessionId(): string | null

  // ===========================================================================
  // Events
  // ===========================================================================

  /**
   * Subscribe to backend events
   *
   * @param callback Callback to receive events
   * @returns Unsubscribe function
   */
  onEvent(callback: BackendEventCallback): UnsubscribeFn

  /**
   * Subscribe to connection status changes
   *
   * @param callback Callback to receive status changes
   * @returns Unsubscribe function
   */
  onConnectionStatusChange(callback: (status: BackendConnectionStatus) => void): UnsubscribeFn

  // ===========================================================================
  // History
  // ===========================================================================

  /**
   * Load history messages
   *
   * @param options History loading options
   * @returns Promise with history messages
   */
  loadHistory(options: HistoryLoadOptions): Promise<HistoryLoadResult>
}

/**
 * Options for loading history
 */
export interface HistoryLoadOptions {
  /** Session ID to load history for */
  sessionId: string

  /** Project path for context */
  projectPath?: string

  /** Offset for pagination (negative = from end) */
  offset?: number

  /** Number of messages to load */
  limit?: number
}

/**
 * Result of history loading
 */
export interface HistoryLoadResult {
  /** Loaded messages */
  messages: unknown[] // Type depends on implementation

  /** Whether there are more messages to load */
  hasMore: boolean

  /** Total message count (if known) */
  totalCount?: number
}

// ============================================================================
// Abstract Base Class (Optional Helper)
// ============================================================================

/**
 * Abstract base class providing common functionality
 *
 * Implementations can extend this or implement BackendSession directly.
 */
export abstract class BaseBackendSession implements BackendSession {
  protected eventCallbacks: Set<BackendEventCallback> = new Set()
  protected statusCallbacks: Set<(status: BackendConnectionStatus) => void> = new Set()
  protected state: BackendSessionState

  constructor(backendType: BackendType, config: BackendConfig) {
    this.state = {
      connectionStatus: 'disconnected',
      sessionId: null,
      isGenerating: false,
      lastError: null,
      backendType,
      config,
    }
  }

  // Lifecycle methods - to be implemented by subclasses
  abstract connect(options: SessionConnectOptions): Promise<void>
  abstract disconnect(): void

  isConnected(): boolean {
    return this.state.connectionStatus === 'connected'
  }

  // Messaging methods - to be implemented by subclasses
  abstract sendMessage(message: UserMessage): void
  abstract interrupt(): Promise<void>

  async runInBackground(): Promise<void> {
    // Default no-op, override in Claude implementation
  }

  // Approval handling - to be implemented by subclasses
  abstract respondToApproval(response: ApprovalResponse): void

  // Configuration - to be implemented by subclasses
  abstract updateConfig(config: Partial<BackendConfig>): Promise<void>
  abstract updateThinkingConfig(thinkingConfig: ThinkingConfig): Promise<void>

  // State & Capabilities
  getState(): BackendSessionState {
    return { ...this.state }
  }

  abstract getCapabilities(): BackendCapabilities

  getBackendType(): BackendType {
    return this.state.backendType
  }

  getSessionId(): string | null {
    return this.state.sessionId
  }

  // Events
  onEvent(callback: BackendEventCallback): UnsubscribeFn {
    this.eventCallbacks.add(callback)
    return () => {
      this.eventCallbacks.delete(callback)
    }
  }

  onConnectionStatusChange(callback: (status: BackendConnectionStatus) => void): UnsubscribeFn {
    this.statusCallbacks.add(callback)
    return () => {
      this.statusCallbacks.delete(callback)
    }
  }

  // History - to be implemented by subclasses
  abstract loadHistory(options: HistoryLoadOptions): Promise<HistoryLoadResult>

  // Protected helper methods
  protected emitEvent(event: BackendEvent): void {
    for (const callback of this.eventCallbacks) {
      try {
        callback(event)
      } catch (error) {
        console.error('[BackendSession] Error in event callback:', error)
      }
    }
  }

  protected setConnectionStatus(status: BackendConnectionStatus): void {
    if (this.state.connectionStatus !== status) {
      this.state.connectionStatus = status
      for (const callback of this.statusCallbacks) {
        try {
          callback(status)
        } catch (error) {
          console.error('[BackendSession] Error in status callback:', error)
        }
      }
    }
  }

  protected setGenerating(generating: boolean): void {
    this.state.isGenerating = generating
  }

  protected setSessionId(id: string | null): void {
    this.state.sessionId = id
  }

  protected setError(error: string | null): void {
    this.state.lastError = error
  }
}
