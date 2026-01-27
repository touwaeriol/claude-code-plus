/**
 * Message types for Claude Agent SDK
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/Messages.kt
 */

import type { JsonValue, JsonObject } from './common';
import type { ContentBlock } from './contentBlocks';

/**
 * Base type for all message types.
 */
export type Message =
  | UserMessage
  | AssistantMessage
  | SystemMessage
  | StatusSystemMessage
  | CompactBoundaryMessage
  | ResultMessage
  | StreamEvent
  | SystemInitMessage;

/**
 * User message containing user input.
 */
export interface UserMessage {
  type: 'user';
  /** Can be string or list of ContentBlock */
  content: JsonValue;
  parent_tool_use_id?: string;
  session_id?: string;
  /**
   * Whether this is a replay message (to distinguish compressed summary from confirmation message).
   * - isReplay = false: Compressed summary (newly generated context)
   * - isReplay = true: Confirmation message (e.g., "Compacted")
   */
  isReplay?: boolean;
  /** Message unique identifier (for edit-resend feature to locate JSONL truncation position) */
  uuid?: string;
}

/**
 * Assistant message containing Claude's response.
 */
export interface AssistantMessage {
  type: 'assistant';
  id?: string;
  content: ContentBlock[];
  model: string;
  token_usage?: TokenUsage;
  /**
   * Parent tool call ID (for subagent message routing).
   * - null: Main session message
   * - non-null: Subagent message, value is the Task tool call ID that triggered this subagent
   */
  parent_tool_use_id?: string;
  /** Message unique identifier (for edit-resend feature to locate JSONL truncation position) */
  uuid?: string;
}

/**
 * System message with metadata.
 *
 * Note: This is a general system message type that requires a data field.
 * For special system messages (like status, compact_boundary), use dedicated types.
 */
export interface SystemMessage {
  type: 'system';
  subtype: string;
  data: JsonValue;
}

/**
 * Status system message - to notify client of status changes.
 *
 * Example: {"type":"system","subtype":"status","status":"compacting","session_id":"..."}
 */
export interface StatusSystemMessage {
  type: 'system';
  subtype: 'status';
  /** Status like "compacting" or null */
  status?: string;
  session_id: string;
  uuid?: string;
}

/**
 * Compact boundary message - marks the boundary of session compaction.
 *
 * Example: {"type":"system","subtype":"compact_boundary","session_id":"...","compact_metadata":{"trigger":"manual","pre_tokens":33767}}
 */
export interface CompactBoundaryMessage {
  type: 'system';
  subtype: 'compact_boundary';
  session_id: string;
  uuid?: string;
  compact_metadata?: CompactMetadata;
}

/**
 * Compact metadata.
 */
export interface CompactMetadata {
  /** "manual" or "auto" */
  trigger?: string;
  /** Token count before compaction */
  pre_tokens?: number;
}

/**
 * Permission denial information for tools that were denied.
 */
export interface PermissionDenial {
  tool_name: string;
  tool_use_id?: string;
  tool_input?: JsonValue;
  reason?: string;
}

/**
 * Result message with cost and usage information.
 */
export interface ResultMessage {
  type: 'result';
  subtype: string;
  duration_ms: number;
  duration_api_ms: number;
  is_error: boolean;
  num_turns: number;
  session_id: string;
  total_cost_usd?: number;
  usage?: JsonValue;
  result?: string;
  permission_denials?: PermissionDenial[];
}

/**
 * Stream event for partial message updates during streaming.
 * Only available when includePartialMessages is enabled.
 */
export interface StreamEvent {
  type: 'stream_event';
  uuid: string;
  session_id: string;
  /** Raw Anthropic API stream event */
  event: JsonValue;
  parent_tool_use_id?: string;
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
 * System init message - sent at the start of each query from Claude CLI.
 * Contains session information that can be used for session resumption.
 *
 * Example: {"type":"system","subtype":"init","session_id":"...","model":"claude-opus-4-5-20251101",...}
 */
export interface SystemInitMessage {
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
 * Note: This is different from McpTypes.McpServerInfo which is for MCP protocol initialization.
 */
export interface CliMcpServerInfo {
  name: string;
  status: string;
}
