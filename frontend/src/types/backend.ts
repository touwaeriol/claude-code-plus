/**
 * Multi-Backend Type Definitions
 *
 * This module defines types for supporting multiple AI backends (Claude, Codex).
 * Each backend has its own capabilities, configuration, and event types.
 */

// ============================================================================
// Backend Type Enum
// ============================================================================

/**
 * Supported backend types
 */
export type BackendType = 'claude' | 'codex'

/**
 * Backend type constants for type-safe usage
 */
export const BackendTypes = {
  CLAUDE: 'claude' as const,
  CODEX: 'codex' as const,
}

// ============================================================================
// Backend Capabilities
// ============================================================================

/**
 * Features that may or may not be supported by a backend
 */
export interface BackendCapabilities {
  /** Backend type identifier */
  type: BackendType

  /** Display name for UI */
  displayName: string

  /** Whether this backend supports extended thinking */
  supportsThinking: boolean

  /** Type of thinking configuration */
  thinkingConfigType: 'token_budget' | 'effort_level'

  /** Whether this backend supports sub-agent tasks */
  supportsSubAgents: boolean

  /** Whether this backend supports MCP tools */
  supportsMcp: boolean

  /** Whether this backend supports sandbox modes */
  supportsSandbox: boolean

  /** Available sandbox modes (if supported) */
  sandboxModes?: SandboxMode[]

  /** Whether this backend supports prompt caching */
  supportsPromptCaching: boolean

  /** Whether this backend exposes token usage statistics */
  exposesTokenUsage: boolean

  /** List of supported tool types */
  supportedTools: string[]

  /** Available models for this backend */
  availableModels: BackendModelInfo[]
}

/**
 * Sandbox mode options (Codex-specific)
 */
export type SandboxMode = 'read-only' | 'workspace-write' | 'full-access'

/**
 * Model information for a specific backend
 */
export interface BackendModelInfo {
  /** Model ID used in API calls */
  id: string

  /** Display name for UI */
  displayName: string

  /** Whether this is the default model */
  isDefault?: boolean

  /** Whether this model supports thinking/reasoning */
  supportsThinking?: boolean

  /** Model description */
  description?: string
}

// ============================================================================
// Backend Configuration
// ============================================================================

/**
 * Base configuration shared by all backends
 */
export interface BaseBackendConfig {
  /** Backend type */
  type: BackendType

  /** Selected model ID */
  modelId: string

  /** System prompt override */
  systemPrompt?: string | null

  /** Permission mode */
  permissionMode: string

  /** Skip permission confirmations */
  skipPermissions: boolean

  /** Maximum conversation turns */
  maxTurns?: number | null
}

/**
 * Claude-specific configuration
 */
export interface ClaudeBackendConfig extends BaseBackendConfig {
  type: 'claude'

  /** API key (optional, may use environment) */
  apiKey?: string | null

  /** Whether thinking is enabled */
  thinkingEnabled: boolean

  /** Thinking token budget (0 = disabled) */
  thinkingTokenBudget: number

  /** Maximum output tokens */
  maxTokens?: number | null

  /** Temperature for generation */
  temperature?: number | null

  /** Include partial messages in stream */
  includePartialMessages: boolean
}

/**
 * Codex-specific configuration
 */
export interface CodexBackendConfig extends BaseBackendConfig {
  type: 'codex'

  /** Model provider (openai, ollama, etc.) */
  modelProvider: string

  /** Reasoning effort level */
  reasoningEffort: CodexReasoningEffort | null

  /** Reasoning summary mode */
  reasoningSummary: CodexReasoningSummary

  /** Sandbox mode */
  sandboxMode: SandboxMode

  /** Working directory for sandbox */
  sandboxWritableRoots?: string[]

  /** Allow network access in sandbox */
  networkAccess?: boolean
}

/**
 * Codex reasoning effort levels
 */
export type CodexReasoningEffort = 'minimal' | 'low' | 'medium' | 'high' | 'xhigh'

/**
 * Codex reasoning summary modes
 */
export type CodexReasoningSummary = 'auto' | 'concise' | 'detailed' | 'none'

/**
 * Union type for all backend configs
 */
export type BackendConfig = ClaudeBackendConfig | CodexBackendConfig

// ============================================================================
// Backend Events
// ============================================================================

/**
 * Base event interface
 */
export interface BaseBackendEvent {
  /** Event type */
  type: string

  /** Session/thread ID */
  sessionId: string

  /** Timestamp */
  timestamp: number
}

/**
 * Text content delta event
 */
export interface TextDeltaEvent extends BaseBackendEvent {
  type: 'text_delta'
  itemId: string
  text: string
}

/**
 * Thinking/reasoning delta event
 */
export interface ThinkingDeltaEvent extends BaseBackendEvent {
  type: 'thinking_delta'
  itemId: string
  text: string
}

/**
 * Tool execution started event
 */
export interface ToolStartedEvent extends BaseBackendEvent {
  type: 'tool_started'
  itemId: string
  toolType: string
  toolName: string
  parameters: Record<string, unknown>
}

/**
 * Tool output delta event
 */
export interface ToolOutputEvent extends BaseBackendEvent {
  type: 'tool_output'
  itemId: string
  output: string
}

/**
 * Tool execution completed event
 */
export interface ToolCompletedEvent extends BaseBackendEvent {
  type: 'tool_completed'
  itemId: string
  success: boolean
  result?: unknown
  error?: string
}

/**
 * Turn/response completed event
 */
export interface TurnCompletedEvent extends BaseBackendEvent {
  type: 'turn_completed'
  turnId: string
  status: 'completed' | 'interrupted' | 'error'
}

/**
 * Approval request event (server requesting client approval)
 */
export interface ApprovalRequestEvent extends BaseBackendEvent {
  type: 'approval_request'
  requestId: string
  approvalType: 'command' | 'file_change' | 'other'
  details: Record<string, unknown>
}

/**
 * Error event
 */
export interface ErrorEvent extends BaseBackendEvent {
  type: 'error'
  code: string
  message: string
  details?: unknown
}

/**
 * Union type for all backend events
 */
export type BackendEvent =
  | TextDeltaEvent
  | ThinkingDeltaEvent
  | ToolStartedEvent
  | ToolOutputEvent
  | ToolCompletedEvent
  | TurnCompletedEvent
  | ApprovalRequestEvent
  | ErrorEvent

// ============================================================================
// Backend Session State
// ============================================================================

/**
 * Connection status
 */
export type BackendConnectionStatus =
  | 'disconnected'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'error'

/**
 * Session state
 */
export interface BackendSessionState {
  /** Connection status */
  connectionStatus: BackendConnectionStatus

  /** Session/thread ID (backend-assigned) */
  sessionId: string | null

  /** Whether currently generating a response */
  isGenerating: boolean

  /** Last error message */
  lastError: string | null

  /** Backend type */
  backendType: BackendType

  /** Current configuration */
  config: BackendConfig
}

// ============================================================================
// Type Guards
// ============================================================================

/**
 * Check if config is Claude config
 */
export function isClaudeConfig(config: BackendConfig): config is ClaudeBackendConfig {
  return config.type === 'claude'
}

/**
 * Check if config is Codex config
 */
export function isCodexConfig(config: BackendConfig): config is CodexBackendConfig {
  return config.type === 'codex'
}

/**
 * Check if backend type is Claude
 */
export function isClaude(type: BackendType): type is 'claude' {
  return type === 'claude'
}

/**
 * Check if backend type is Codex
 */
export function isCodex(type: BackendType): type is 'codex' {
  return type === 'codex'
}

// ============================================================================
// Default Values
// ============================================================================

/**
 * Default Claude configuration
 */
export const DEFAULT_CLAUDE_CONFIG: ClaudeBackendConfig = {
  type: 'claude',
  modelId: 'claude-opus-4-5-20251101',
  permissionMode: 'default',
  skipPermissions: false,
  maxTurns: 10,
  thinkingEnabled: true,
  thinkingTokenBudget: 8096,
  includePartialMessages: true,
}

/**
 * Default Codex configuration
 */
export const DEFAULT_CODEX_CONFIG: CodexBackendConfig = {
  type: 'codex',
  modelId: 'gpt-5.1-codex-max',
  modelProvider: 'openai',
  permissionMode: 'default',
  skipPermissions: false,
  maxTurns: 10,
  reasoningEffort: 'medium',
  reasoningSummary: 'auto',
  sandboxMode: 'workspace-write',
}

/**
 * Get default config for a backend type
 */
export function getDefaultConfig(type: BackendType): BackendConfig {
  return type === 'claude' ? { ...DEFAULT_CLAUDE_CONFIG } : { ...DEFAULT_CODEX_CONFIG }
}
