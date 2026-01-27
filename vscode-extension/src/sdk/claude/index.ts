/**
 * Claude Agent SDK for VS Code
 * 
 * Complete TypeScript SDK for interacting with Claude CLI.
 * Translated from: claude-agent-sdk (Kotlin)
 */

// Main client
export {
  ClaudeCodeSdkClient,
  createSdkClient,
  claudeQuery,
  ClientNotConnectedException,
  type SdkClientOptions,
  type ServerInfo,
  type PermissionMode,
  type UserInputContent,
  type TextInput,
  type ImageInput,
  type StreamJsonUserMessage,
} from './claudeCodeSdkClient';

// Legacy client (for backward compatibility)
export {
  ClaudeCliSessionManager,
  type ClaudeCliSessionConfig,
  type ToolPermissionRequest,
  type ToolPermissionResult,
  type ClaudeCliQueryCallbacks,
} from './claudeCli';

// Types
export * from './types';

// Protocol
export {
  ControlProtocol,
  ControlProtocolException,
  type Transport,
  type ControlProtocolOptions,
} from './protocol';

// Transport
export {
  SubprocessTransport,
  TransportError,
  CLINotFoundError,
  CLIConnectionError,
  ProcessError,
  JSONDecodeError,
  NodeNotFoundError,
  type ClaudeAgentOptions,
  type TransportEvents,
  type JsonMessage,
} from './transport';

// MCP
export {
  McpServerBase,
  ToolResultHelper,
  withToolUseContext,
  McpTool,
  ToolParam,
  McpServerConfig,
  type McpServer,
  type ToolDefinition,
  type ToolResult,
  type ToolUseContextData,
} from './mcp';

// Callback
export {
  ToolCallbackRegistry,
  BaseToolCallback,
  FunctionToolCallback,
  createToolCallback,
  getGlobalRegistry,
  type ToolCallback,
  type ToolCallbackResult,
} from './callback';

// Builders
export {
  OptionsBuilder,
  HookBuilder,
  ToolCall,
  type ContentItem,
} from './builders';

// Utils
export {
  projectPathToDirectoryName,
  getProjectName,
  generateProjectId,
  isValidProjectPath,
  getClaudeDir,
  getProjectSessionDir,
  scanHistorySessions,
  getSessionIds,
  sessionExists,
  getSessionFilePath,
  deleteSession,
  isExtensionInstalled,
  getExtensionInfo,
  EXTENSION_ID,
  type SessionMetadata,
  type ScanOptions,
  type ExtensionInfo,
} from './utils';
