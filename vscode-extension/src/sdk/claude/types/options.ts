/**
 * Options types for Claude Agent SDK
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/Options.kt
 */

import type { JsonValue, JsonObject } from './common';
import type { HookEvent, HookMatcher } from './hooks';
import type { PermissionMode, CanUseTool } from './permissions';

/**
 * System prompt preset configuration.
 * Allows using pre-defined system prompts like "claude_code".
 */
export interface SystemPromptPreset {
  type: 'preset';
  preset: string;
  append?: string;
}

/**
 * Agent definition for programmatic subagents.
 * Agents can be defined inline in code using this structure.
 */
export interface AgentDefinition {
  description: string;
  prompt: string;
  tools?: string[];
  /** Model: "sonnet" | "opus" | "haiku" | "inherit" */
  model?: string;
}

/**
 * Setting sources to load from filesystem.
 * Controls which configuration files are read.
 */
export type SettingSource = 'user' | 'project' | 'local';

/**
 * Union type for MCP server entries.
 */
export type McpServerSpec = McpServerConfig | unknown;

/**
 * Union type for MCP server configurations.
 */
export type McpServerConfig = McpStdioServerConfig | McpSSEServerConfig | McpHttpServerConfig;

/**
 * MCP stdio server configuration.
 */
export interface McpStdioServerConfig {
  type: 'stdio';
  command: string;
  args?: string[];
  env?: Record<string, string>;
}

/**
 * MCP SSE server configuration.
 */
export interface McpSSEServerConfig {
  type: 'sse';
  url: string;
  headers?: Record<string, string>;
}

/**
 * MCP HTTP server configuration.
 * Used for connecting to MCP servers exposed via HTTP (including SSE-based servers).
 * Claude CLI uses "http" type to connect to MCP servers exposed via mcp{} Ktor plugin.
 */
export interface McpHttpServerConfig {
  type: 'http';
  url: string;
  headers?: Record<string, string>;
}

/**
 * Claude Agent SDK options.
 * Based on Python SDK v0.1.0 ClaudeAgentOptions.
 *
 * Breaking changes from previous versions:
 * - Renamed from ClaudeAgentOptions to ClaudeAgentOptions
 * - systemPrompt now supports String or SystemPromptPreset
 * - appendSystemPrompt merged into systemPrompt
 * - No default system prompt or settings loaded (explicit configuration required)
 */
export interface ClaudeAgentOptions {
  // Tool configuration
  allowedTools?: string[];
  disallowedTools?: string[];

  /**
   * System prompt - unified field supporting string or preset.
   * Use SystemPromptPreset with preset = "claude_code" for default Claude Code behavior.
   */
  systemPrompt?: string | SystemPromptPreset;

  /**
   * Append system prompt file - for MCP scenarios.
   * Uses --append-system-prompt-file parameter, doesn't replace default prompt.
   */
  appendSystemPromptFile?: string;

  // MCP servers (can be McpServerConfig or McpServer instances)
  mcpServers?: Record<string, McpServerSpec>;

  // Permission settings
  permissionMode?: PermissionMode;
  dangerouslySkipPermissions?: boolean;
  allowDangerouslySkipPermissions?: boolean;
  permissionPromptToolName?: string;
  canUseTool?: CanUseTool;

  // Session control
  continueConversation?: boolean;
  resume?: string;
  /** Fork session when resuming */
  forkSession?: boolean;
  /** Replay user messages when resuming session */
  replayUserMessages?: boolean;
  /** Disable session persistence (--no-session-persistence) */
  noSessionPersistence?: boolean;
  maxTurns?: number;

  // Streaming configuration
  /** Enable partial message streaming */
  includePartialMessages?: boolean;

  /** Programmatic subagents */
  agents?: Record<string, AgentDefinition>;

  /** Control which settings files to load */
  settingSources?: SettingSource[];

  // Model configuration
  model?: string;

  // Environment
  cwd?: string;
  settings?: string;
  addDirs?: string[];
  env?: Record<string, string>;
  user?: string;

  /** Specify custom Claude CLI binary path */
  cliPath?: string;

  /**
   * Node.js path - Specify custom Node.js executable path.
   * If not set, uses "node" from system PATH.
   */
  nodePath?: string;

  // Hook configurations
  hooks?: Partial<Record<HookEvent, HookMatcher[]>>;

  // Extra CLI arguments
  extraArgs?: Record<string, string | undefined>;
  /** Max bytes when buffering CLI stdout */
  maxBufferSize?: number;

  // Debug settings
  /** @deprecated Use stderr callback instead */
  debugStderr?: unknown;
  /** Callback for stderr output */
  stderr?: (data: string) => void;

  // Advanced options
  /** Timeout in milliseconds */
  timeout?: number;
  verbose?: boolean;
  print?: boolean;
  compact?: boolean;
  maxTokens?: number;
  maxThinkingTokens?: number;
  temperature?: number;
  topP?: number;
  stopSequences?: string[];

  // Legacy streaming (consider using includePartialMessages instead)
  stream?: boolean;
  streamingCallback?: (data: string) => void;

  /**
   * Chrome integration.
   * When true, passes --chrome to CLI; when false, passes --no-chrome.
   * null means use CLI default (respects user config).
   */
  chromeEnabled?: boolean;
}

// ============================================================================
// Control Request Types
// ============================================================================

/**
 * Base type for control requests.
 */
export type ControlRequest =
  | InterruptRequest
  | RunInBackgroundRequest
  | PermissionRequest
  | InitializeRequest
  | SetPermissionModeRequest
  | SetModelRequest
  | SetMaxThinkingTokensRequest
  | HookCallbackRequest
  | McpMessageRequest
  | McpStatusRequest
  | McpSetServersRequest;

export interface InterruptRequest {
  subtype: 'interrupt';
}

export interface RunInBackgroundRequest {
  subtype: 'run_in_background';
}

export interface PermissionRequest {
  subtype: 'can_use_tool';
  toolName: string;
  input: JsonValue;
  permissionSuggestions?: JsonValue[];
  blockedPath?: string;
  toolUseId?: string;
  agentId?: string;
}

export interface InitializeRequest {
  subtype: 'initialize';
  hooks?: Record<string, JsonValue>;
}

export interface SetPermissionModeRequest {
  subtype: 'set_permission_mode';
  mode: string;
}

export interface SetModelRequest {
  subtype: 'set_model';
  model?: string;
}

export interface SetMaxThinkingTokensRequest {
  subtype: 'set_max_thinking_tokens';
  /** null means disable/use default, 0 also means disable */
  max_thinking_tokens?: number;
}

export interface HookCallbackRequest {
  subtype: 'hook_callback';
  callbackId: string;
  input: JsonValue;
  toolUseId?: string;
}

export interface McpMessageRequest {
  subtype: 'mcp_message';
  serverName: string;
  message: JsonValue;
}

export interface McpStatusRequest {
  subtype: 'mcp_status';
}

export interface McpSetServersRequest {
  subtype: 'mcp_set_servers';
  servers: Record<string, McpStdioServerDto>;
}

/**
 * MCP stdio server configuration DTO for mcp_set_servers request.
 * This is the format expected by CLI's mcp_set_servers control command.
 */
export interface McpStdioServerDto {
  command: string;
  args?: string[];
  env?: Record<string, string>;
}

// ============================================================================
// Control Response Types
// ============================================================================

/**
 * Control response types.
 */
export interface ControlResponse {
  subtype: string;
  requestId: string;
  response?: JsonValue;
  error?: string;
}

/**
 * MCP server status info returned by mcp_status control request.
 */
export interface McpServerStatusInfo {
  name: string;
  /** Status: "connected" | "failed" | "sdk" */
  status: string;
  serverInfo?: JsonValue;
}

/**
 * Response from mcp_set_servers request.
 */
export interface McpSetServersResponse {
  added: string[];
  removed: string[];
  errors: Record<string, string>;
}

/**
 * Response from mcp_reconnect request.
 */
export interface McpReconnectResponse {
  /** Whether the reconnect was successful */
  success: boolean;
  /** The server name that was reconnected */
  serverName: string;
  /** Server status after reconnect: "connected" | "failed" | "needs-auth" | etc. */
  status?: string;
  /** Number of tools available after reconnect */
  toolsCount: number;
  /** Error message if reconnect failed */
  error?: string;
}

/**
 * MCP tool information.
 */
export interface McpToolInfo {
  /** Tool name (original MCP tool name, e.g., "FileIndex") */
  name: string;
  /** Tool description */
  description: string;
  /** Input JSON Schema */
  inputSchema?: JsonValue;
}

/**
 * Response from mcp_tools request.
 */
export interface McpToolsResponse {
  /** Server name filter (null if all servers) */
  serverName?: string;
  /** List of tools */
  tools: McpToolInfo[];
  /** Total count of tools */
  count: number;
}

/**
 * Response from mcp_disable/mcp_enable request.
 */
export interface McpDisableEnableResponse {
  /** Whether the operation was successful */
  success: boolean;
  /** The server name that was disabled/enabled */
  serverName: string;
  /** Server status after operation: "disabled" | "connected" | "pending" | "failed" | etc. */
  status?: string;
  /** Number of tools available (0 if disabled) */
  toolsCount: number;
  /** Error message if operation failed */
  error?: string;
}

/**
 * Chrome extension status - matches official /chrome command display format.
 *
 * Data sources:
 * 1. installed - Local file system check (Chrome Extensions directory)
 * 2. enabled/connected/mcpServerStatus - From mcp_status control command
 * 3. serverInfo - Full MCP server info (contains name, version, etc.)
 *
 * Official /chrome display format:
 * ```
 * Status: Connected/Disabled     ← connected field
 * Extension: Installed/Not detected  ← installed field
 * ```
 */
export interface ChromeStatus {
  /** Whether the Chrome extension is installed (checks NativeMessagingHost config file) */
  installed: boolean;
  /** Whether Chrome integration is enabled by default (k1().claudeInChromeDefaultEnabled) */
  enabled: boolean;
  /** Whether the MCP server "claude-in-chrome" is currently connected */
  connected: boolean;
  /** MCP server status: "connected" | "failed" | "pending" | "needs-auth" | "disabled" | null */
  mcpServerStatus?: string;
  /** Extension version (e.g., "1.0.36") when connected */
  extensionVersion?: string;
}
