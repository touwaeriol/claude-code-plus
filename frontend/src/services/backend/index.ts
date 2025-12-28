/**
 * Backend Module Exports
 *
 * This module provides the unified backend abstraction layer.
 */

// Re-export types from backend.ts
export type {
  BackendType,
  BackendCapabilities,
  BackendModelInfo,
  SandboxMode,
  BaseBackendConfig,
  ClaudeBackendConfig,
  CodexBackendConfig,
  BackendConfig,
  CodexReasoningEffort,
  CodexReasoningSummary,
  BackendEvent,
  TextDeltaEvent,
  ThinkingDeltaEvent,
  ToolStartedEvent,
  ToolOutputEvent,
  ToolCompletedEvent,
  TurnCompletedEvent,
  ApprovalRequestEvent,
  ErrorEvent,
  BackendConnectionStatus,
  BackendSessionState,
} from '@/types/backend'

export {
  BackendTypes,
  isClaudeConfig,
  isCodexConfig,
  isClaude,
  isCodex,
  getDefaultConfig,
  DEFAULT_CLAUDE_CONFIG,
  DEFAULT_CODEX_CONFIG,
} from '@/types/backend'

// Re-export types from thinking.ts
export type {
  ClaudeThinkingConfig,
  CodexThinkingConfig,
  ThinkingConfig,
  CodexReasoningSummaryMode,
  ClaudeThinkingPresetId,
  CodexEffortLevelKey,
} from '@/types/thinking'

export {
  CLAUDE_THINKING_PRESETS,
  CODEX_EFFORT_LEVELS,
  CODEX_SUMMARY_MODES,
  getClaudeThinkingPresets,
  getCodexEffortLevels,
  getCodexSummaryModes,
  findClaudePresetByTokens,
  isThinkingEnabled,
  isClaudeThinking,
  isCodexThinking,
  createClaudeThinkingConfig,
  createCodexThinkingConfig,
  createThinkingConfig,
  claudeTokensToCodexEffort,
  codexEffortToClaudeTokens,
  getThinkingDisplayString,
  getThinkingShortDisplayString,
} from '@/types/thinking'

// Re-export session types and interface
export type {
  TextContent,
  ImageContent,
  FileContext,
  MessageContent,
  UserMessage,
  ApprovalResponse,
  SessionConnectOptions,
  BackendEventCallback,
  UnsubscribeFn,
  BackendSession,
  HistoryLoadOptions,
  HistoryLoadResult,
} from './BackendSession'

export { BaseBackendSession } from './BackendSession'

// Session factory - available now
export {
  BackendSessionFactory,
  createSession,
  createAndConnectSession,
  createDefaultSession,
  isBackendAvailable,
  getAvailableBackendTypes,
  getRegisteredBackendTypes,
  getDefaultBackendType,
  getDefaultBackendTypeSync,
  registerSessionImplementation,
  BackendFactoryError,
} from './BackendSessionFactory'

export { default as backendFactory } from './BackendSessionFactory'

// Session implementations
export { ClaudeSession, createClaudeSession } from './ClaudeSession'
export { CodexSession } from './CodexSession'
