/**
 * Claude Agent SDK Types
 *
 * This module exports all types for the Claude Agent SDK.
 * Translated from Kotlin SDK: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/
 */

// Common types
export type { JsonValue, JsonObject, JsonArray } from './common';

// Options types
export type {
  SystemPromptPreset,
  AgentDefinition,
  SettingSource,
  McpServerSpec,
  McpServerConfig,
  McpStdioServerConfig,
  McpSSEServerConfig,
  McpHttpServerConfig,
  ClaudeAgentOptions,
  ControlRequest,
  InterruptRequest,
  PermissionRequest,
  InitializeRequest,
  SetPermissionModeRequest,
  SetModelRequest,
  SetMaxThinkingTokensRequest,
  HookCallbackRequest,
  McpMessageRequest,
  McpStatusRequest,
  McpSetServersRequest,
  McpStdioServerDto,
  ControlResponse,
  McpServerStatusInfo,
  McpSetServersResponse,
  McpReconnectResponse,
  McpToolInfo,
  McpDisableEnableResponse,
} from './options';

// Message types
export type {
  Message,
  UserMessage,
  AssistantMessage,
  SystemMessage,
  StatusSystemMessage,
  CompactBoundaryMessage,
  CompactMetadata,
  PermissionDenial,
  ResultMessage,
  StreamEvent,
  TokenUsage,
  SystemInitMessage,
  CliMcpServerInfo,
} from './messages';

// Content block types
export type {
  ContentBlock,
  ToolUseLike,
  TextBlock,
  ThinkingBlock,
  ToolUseBlock,
  ToolResultBlock,
  ImageBlock,
  UserInputContent,
  TextInput,
  ImageSource,
  ImageInput,
  ToolResultInput,
  StreamJsonUserMessage,
  UserMessagePayload,
} from './contentBlocks';
export { createImageInput, createTextPayload } from './contentBlocks';

// Tool types
export type {
  ToolTypeInfo,
  ToolTypeName,
  SpecificToolUse,
  BashToolUse,
  EditToolUse,
  EditOperation,
  MultiEditToolUse,
  ReadToolUse,
  WriteToolUse,
  GlobToolUse,
  GrepToolUse,
  WebFetchToolUse,
  WebSearchToolUse,
  TodoItem,
  TodoWriteToolUse,
  TaskToolUse,
  NotebookEditToolUse,
  McpToolUse,
  BashOutputToolUse,
  KillShellToolUse,
  ExitPlanModeToolUse,
  ListMcpResourcesToolUse,
  ReadMcpResourceToolUse,
  SkillToolUse,
  UnknownToolUse,
  AnyToolUse,
} from './toolTypes';
export { ToolType, getToolType } from './toolTypes';

// Stream event types
export type {
  StreamEventType,
  ContentBlockDeltaEvent,
  ContentBlockStartEvent,
  ContentBlockStopEvent,
  MessageDeltaEvent,
  MessageStartEvent,
  MessageStopEvent,
  TextDelta,
  InputJsonDelta,
  ThinkingDelta,
  DeltaType,
} from './streamEvents';
export { isTextDelta, isInputJsonDelta, isThinkingDelta } from './streamEvents';

// Hook types
export type {
  HookEvent,
  HookJSONOutput,
  HookContext,
  HookCallback,
  HookMatcher,
  HookResult,
  HookResultAllow,
  HookResultBlock,
  HookResultModify,
  HookExecutionEnvironment,
} from './hooks';
export { HookEvents, HookResult as HookResultFactory, HookRegistry } from './hooks';

// Permission types
export type {
  PermissionMode,
  PermissionBehavior,
  PermissionUpdateDestination,
  PermissionRuleValue,
  PermissionUpdateType,
  PermissionUpdate,
  ToolPermissionContext,
  PermissionResult,
  PermissionResultAllow,
  PermissionResultDeny,
  CanUseTool,
} from './permissions';
export {
  PermissionModes,
  PermissionBehaviors,
  PermissionUpdateDestinations,
  PermissionUpdateTypes,
  PermissionResult as PermissionResultFactory,
} from './permissions';

// MCP types
export type {
  SdkMcpTool,
  McpServerStatus,
  McpServerInstance,
  McpResource,
  McpToolRequest,
  McpToolResponse,
  McpPrompt,
  McpPromptArgument,
  McpServerCapabilities,
  McpLoggingCapability,
  McpPromptsCapability,
  McpResourcesCapability,
  McpToolsCapability,
  McpClientCapabilities,
  McpInitializeResult,
  McpServerInfo,
  McpClientInfo,
} from './mcpTypes';
export { McpServerStatuses, defaultMcpClientInfo } from './mcpTypes';

// Error types
export {
  ClaudeSDKError,
  CLINotFoundError,
  CLIConnectionError,
  ProcessError,
  CLIJSONDecodeError,
  MCPServerError,
  ToolExecutionError,
  PermissionDeniedError,
  SessionError,
  ConversationTimeoutError,
  isClaudeSDKError,
  isCLINotFoundError,
  isCLIConnectionError,
  isProcessError,
  isCLIJSONDecodeError,
  isMCPServerError,
  isToolExecutionError,
  isPermissionDeniedError,
  isSessionError,
  isConversationTimeoutError,
} from './errors';
