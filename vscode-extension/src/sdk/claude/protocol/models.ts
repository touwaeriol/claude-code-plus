/**
 * Protocol Models for Claude Agent SDK
 * 
 * This file contains all data models used in the control protocol
 * for communication with Claude CLI.
 */

// ============================================================================
// Background Operation Results
// ============================================================================

/**
 * Result of agents_run_all_to_background operation.
 */
export interface AgentsBackgroundResult {
  /** Number of agents that were backgrounded */
  count: number;
  /** List of agent IDs that were backgrounded */
  backgroundedIds: string[];
}

/**
 * Result of bash_run_to_background operation.
 */
export interface BashBackgroundResult {
  /** Whether the operation succeeded */
  success: boolean;
  /** The background task ID (for tracking) */
  taskId?: string;
  /** The command that was backgrounded */
  command?: string;
}

/**
 * Result of unified run_to_background operation.
 * 
 * This represents the result of backgrounding tasks, handling both Bash and Agent types.
 * 
 * When backgrounding a specific task (taskId provided):
 * - isBash: Whether the task was a Bash command (true) or Agent (false)
 * - success: Whether the operation succeeded
 * - taskId: The ID of the backgrounded task
 * - command: The Bash command (only for Bash tasks)
 * 
 * When backgrounding all tasks (taskId not provided):
 * - bashCount: Number of Bash commands backgrounded
 * - agentCount: Number of Agents backgrounded
 * - backgroundedBashIds: List of Bash task IDs that were backgrounded
 * - backgroundedAgentIds: List of Agent IDs that were backgrounded
 */
export interface UnifiedBackgroundResult {
  success: boolean;
  /** For single task: whether it was Bash */
  isBash?: boolean;
  /** For single task: the task ID */
  taskId?: string;
  /** For single Bash task: the command */
  command?: string;
  /** For batch: number of Bash backgrounded */
  bashCount?: number;
  /** For batch: number of Agents backgrounded */
  agentCount?: number;
  /** For batch: Bash IDs */
  backgroundedBashIds?: string[];
  /** For batch: Agent IDs */
  backgroundedAgentIds?: string[];
  /** Error message if failed */
  error?: string;
}

/**
 * CLI capabilities result.
 * 
 * Contains runtime capability flags queried from the CLI.
 * Use this to check if certain features are enabled/disabled.
 */
export interface CliCapabilities {
  /** 
   * Whether background tasks are enabled.
   * False when CLAUDE_CODE_DISABLE_BACKGROUND_TASKS env var is set to 'true' or '1'.
   */
  backgroundTasksEnabled: boolean;
}

// ============================================================================
// Control Request/Response Types
// ============================================================================

/**
 * Base interface for control requests.
 */
export interface ControlRequest {
  subtype: string;
}

/**
 * Interrupt request.
 */
export interface InterruptRequest extends ControlRequest {
  subtype: 'interrupt';
}

/**
 * Run in background request.
 */
export interface RunInBackgroundRequest extends ControlRequest {
  subtype: 'run_in_background';
  task_id?: string;
}

/**
 * Permission request from CLI.
 */
export interface PermissionRequest extends ControlRequest {
  subtype: 'can_use_tool';
  tool_name: string;
  input: unknown;
  permission_suggestions?: unknown[];
  blocked_path?: string;
  tool_use_id?: string;
  agent_id?: string;
}

/**
 * Hook callback request from CLI.
 */
export interface HookCallbackRequest extends ControlRequest {
  subtype: 'hook_callback';
  callback_id: string;
  input: unknown;
  tool_use_id?: string;
}

/**
 * MCP message request from CLI.
 */
export interface McpMessageRequest extends ControlRequest {
  subtype: 'mcp_message';
  server_name: string;
  message: unknown;
}

/**
 * Control response from CLI.
 */
export interface ControlResponse {
  subtype: string;
  request_id: string;
  response?: unknown;
  error?: string;
}

// ============================================================================
// MCP Types
// ============================================================================

/**
 * MCP server status info returned by mcp_status control request.
 */
export interface McpServerStatusInfo {
  name: string;
  /** "connected" | "failed" | "sdk" */
  status: string;
  serverInfo?: unknown;
}

/**
 * MCP stdio server configuration DTO for mcp_set_servers request.
 */
export interface McpStdioServerDto {
  command: string;
  args?: string[];
  env?: Record<string, string>;
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
  inputSchema?: unknown;
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
 */
export interface ChromeStatus {
  /** Whether the Chrome extension is installed (checks NativeMessagingHost config file) */
  installed: boolean;
  /** Whether Chrome integration is enabled by default */
  enabled: boolean;
  /** Whether the MCP server "claude-in-chrome" is currently connected */
  connected: boolean;
  /** MCP server status: "connected" | "failed" | "pending" | "needs-auth" | "disabled" | null */
  mcpServerStatus?: string;
  /** Extension version (e.g., "1.0.36") when connected */
  extensionVersion?: string;
}

// ============================================================================
// Message Types
// ============================================================================

/**
 * Base interface for all message types.
 */
export interface Message {
  type?: string;
}

/**
 * User message containing user input.
 */
export interface UserMessage extends Message {
  type: 'user';
  content: unknown;
  parent_tool_use_id?: string;
  session_id?: string;
  /** Whether this is a replay message (for distinguishing compressed summaries) */
  isReplay?: boolean;
  /** Message UUID for edit/resend functionality */
  uuid?: string;
}

/**
 * Token usage information.
 */
export interface TokenUsage {
  input_tokens: number;
  output_tokens: number;
  cache_creation_input_tokens?: number;
  cache_read_input_tokens?: number;
}

/**
 * Assistant message containing Claude's response.
 */
export interface AssistantMessage extends Message {
  type: 'assistant';
  id?: string;
  content: ContentBlock[];
  model: string;
  token_usage?: TokenUsage;
  parent_tool_use_id?: string;
  uuid?: string;
}

/**
 * System message with metadata.
 */
export interface SystemMessage extends Message {
  type: 'system';
  subtype: string;
  data?: unknown;
}

/**
 * Status system message - for notifying client of status changes.
 */
export interface StatusSystemMessage extends Message {
  type: 'system';
  subtype: 'status';
  status?: string;
  session_id: string;
  uuid?: string;
}

/**
 * Compact metadata.
 */
export interface CompactMetadata {
  trigger?: string;
  pre_tokens?: number;
}

/**
 * Compact boundary message - marks session compression boundary.
 */
export interface CompactBoundaryMessage extends Message {
  type: 'system';
  subtype: 'compact_boundary';
  session_id: string;
  uuid?: string;
  compact_metadata?: CompactMetadata;
}

/**
 * System init message - sent at the start of each query from Claude CLI.
 */
export interface SystemInitMessage extends Message {
  type: 'system';
  subtype: 'init';
  session_id: string;
  cwd?: string;
  model?: string;
  permissionMode?: string;
  apiKeySource?: string;
  tools?: string[];
  mcp_servers?: CliMcpServerInfo[];
}

/**
 * MCP server info in system init message (from CLI).
 */
export interface CliMcpServerInfo {
  name: string;
  status: string;
}

/**
 * Result message with cost and usage information.
 */
export interface ResultMessage extends Message {
  type: 'result';
  subtype: string;
  duration_ms: number;
  duration_api_ms: number;
  is_error: boolean;
  num_turns: number;
  session_id: string;
  total_cost_usd?: number;
  usage?: unknown;
  result?: string;
}

/**
 * Stream event for partial message updates during streaming.
 */
export interface StreamEvent extends Message {
  type: 'stream_event';
  uuid: string;
  session_id: string;
  event: unknown;
  parent_tool_use_id?: string;
}

// ============================================================================
// Content Block Types
// ============================================================================

/**
 * Base interface for content blocks.
 */
export interface ContentBlock {
  type: string;
}

/**
 * Text content block.
 */
export interface TextBlock extends ContentBlock {
  type: 'text';
  text: string;
}

/**
 * Thinking content block.
 */
export interface ThinkingBlock extends ContentBlock {
  type: 'thinking';
  thinking: string;
  signature?: string;
}

/**
 * Tool use content block.
 */
export interface ToolUseBlock extends ContentBlock {
  type: 'tool_use';
  id: string;
  name: string;
  input: unknown;
}

/**
 * Tool result content block.
 */
export interface ToolResultBlock extends ContentBlock {
  type: 'tool_result';
  tool_use_id: string;
  content?: unknown;
  is_error?: boolean;
}

// ============================================================================
// Permission Types
// ============================================================================

/**
 * Permission modes for tool usage.
 */
export type PermissionMode = 'default' | 'acceptEdits' | 'plan' | 'bypassPermissions';

/**
 * Permission behavior types.
 */
export type PermissionBehavior = 'allow' | 'deny' | 'ask';

/**
 * Permission update destination.
 */
export type PermissionUpdateDestination = 'userSettings' | 'projectSettings' | 'localSettings' | 'session';

/**
 * Permission update type.
 */
export type PermissionUpdateType = 
  | 'addRules'
  | 'replaceRules'
  | 'removeRules'
  | 'setMode'
  | 'addDirectories'
  | 'removeDirectories';

/**
 * Permission rule value.
 */
export interface PermissionRuleValue {
  toolName: string;
  ruleContent?: string;
}

/**
 * Permission update configuration.
 */
export interface PermissionUpdate {
  type: PermissionUpdateType;
  rules?: PermissionRuleValue[];
  behavior?: PermissionBehavior;
  mode?: PermissionMode;
  directories?: string[];
  destination?: PermissionUpdateDestination;
}

/**
 * Context information for tool permission callbacks.
 */
export interface ToolPermissionContext {
  signal?: AbortSignal;
  suggestions?: PermissionUpdate[];
}

/**
 * Allow permission result.
 */
export interface PermissionResultAllow {
  behavior: 'allow';
  updatedInput?: Record<string, unknown>;
  updatedPermissions?: PermissionUpdate[];
}

/**
 * Deny permission result.
 */
export interface PermissionResultDeny {
  behavior: 'deny';
  message: string;
  interrupt?: boolean;
}

/**
 * Union type for permission results.
 */
export type PermissionResult = PermissionResultAllow | PermissionResultDeny;

/**
 * Tool permission callback function type.
 */
export type CanUseTool = (
  toolName: string,
  input: Record<string, unknown>,
  toolUseId: string | undefined,
  context: ToolPermissionContext
) => Promise<PermissionResult>;

// ============================================================================
// Hook Types
// ============================================================================

/**
 * Hook event types.
 */
export type HookEvent = 
  | 'PreToolUse'
  | 'PostToolUse'
  | 'UserPromptSubmit'
  | 'Stop'
  | 'SubagentStop'
  | 'PreCompact';

/**
 * Hook callback function type.
 */
export type HookCallback = (
  input: Record<string, unknown>,
  toolUseId: string | undefined,
  context: HookContext
) => Promise<unknown>;

/**
 * Hook context.
 */
export interface HookContext {
  // Future: Add context properties as needed
}

/**
 * Hook matcher configuration.
 */
export interface HookMatcher {
  matcher?: string;
  hooks: HookCallback[];
}
