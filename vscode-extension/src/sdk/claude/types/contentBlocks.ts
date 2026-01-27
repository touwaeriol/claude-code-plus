/**
 * Content block types for Claude Agent SDK
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/ContentBlocks.kt
 */

import type { JsonValue, JsonObject } from './common';

/**
 * Base type for all content blocks.
 */
export type ContentBlock =
  | TextBlock
  | ThinkingBlock
  | ToolUseBlock
  | ToolResultBlock
  | ImageBlock;

/**
 * Common interface for tool use blocks.
 * Unifies ToolUseBlock and SpecificToolUse for type checking.
 *
 * Background: There are two sets of tool types in the project:
 * - ToolUseBlock: For JSON serialization/deserialization, matches Claude API raw format
 * - SpecificToolUse: Strongly typed tool classes (like BashToolUse, EditToolUse), providing type-safe parameter access
 *
 * This interface unifies their common properties to avoid type conversion issues.
 */
export interface ToolUseLike {
  id: string;
  name: string;
  input: JsonValue;
}

/**
 * Text content block.
 */
export interface TextBlock {
  type: 'text';
  text: string;
}

/**
 * Thinking content block for Claude's reasoning process.
 */
export interface ThinkingBlock {
  type: 'thinking';
  thinking: string;
  signature?: string;
}

/**
 * Tool use content block.
 *
 * Note: In content_block_start event, input may be empty object or not present.
 */
export interface ToolUseBlock extends ToolUseLike {
  type: 'tool_use';
  id: string;
  name: string;
  /** Default to empty object since input may be empty when stream starts */
  input: JsonValue;
}

/**
 * Tool result content block.
 */
export interface ToolResultBlock {
  type: 'tool_result';
  tool_use_id: string;
  content?: JsonValue;
  is_error?: boolean;
}

/**
 * Image content block for stream-json input.
 */
export interface ImageBlock {
  type: 'image';
  /** Base64 encoded image data */
  data: string;
  /** e.g., "image/png", "image/jpeg" */
  mimeType: string;
}

// ============================================================================
// User Input Content Types
// ============================================================================

/**
 * User input content - can be text, image, or mixed.
 * Used for stream-json input format.
 *
 * Uses classDiscriminator = "type" to match Claude CLI format.
 */
export type UserInputContent = TextInput | ImageInput | ToolResultInput;

/**
 * Simple text input.
 * Serializes to: {"type": "text", "text": "..."}
 */
export interface TextInput {
  type: 'text';
  text: string;
}

/**
 * Image source for Anthropic API format.
 * Serializes to: {"type": "base64", "media_type": "...", "data": "..."}
 */
export interface ImageSource {
  type: 'base64';
  /** e.g., "image/png", "image/jpeg" */
  media_type: string;
  /** Base64 encoded image data (without data URL prefix) */
  data: string;
}

/**
 * Image input matching Anthropic API format.
 * Serializes to: {"type": "image", "source": {"type": "base64", "media_type": "...", "data": "..."}}
 */
export interface ImageInput {
  type: 'image';
  source: ImageSource;
}

/**
 * Helper function to create ImageInput from base64 data and MIME type.
 */
export function createImageInput(data: string, mimeType: string): ImageInput {
  // If data contains data URL prefix, remove it
  const cleanData = data.includes(',') ? data.split(',')[1] : data;
  return {
    type: 'image',
    source: {
      type: 'base64',
      media_type: mimeType,
      data: cleanData,
    },
  };
}

/**
 * Tool result input - used to respond to tool calls.
 *
 * Serializes to:
 * ```json
 * {
 *   "type": "tool_result",
 *   "tool_use_id": "toolu_xxx",
 *   "content": "User's selected result",
 *   "is_error": false
 * }
 * ```
 *
 * Used to respond to tool calls that require user interaction like AskUserQuestion, ExitPlanMode.
 */
export interface ToolResultInput {
  type: 'tool_result';
  tool_use_id: string;
  content: string;
  is_error?: boolean;
}

/**
 * Stream-JSON user message format.
 *
 * Example:
 * ```
 * {
 *   "type": "user",
 *   "message": {"role": "user", "content": [...]},
 *   "session_id": "default",
 *   "parent_tool_use_id": null,
 *   "parentUuid": null
 * }
 * ```
 *
 * @property parentUuid Edit-resend feature: specify the parent message UUID for the new message.
 *   When user edits and resends a message, set this field to the UUID of the parent of the message being replaced,
 *   CLI will automatically create a new conversation branch.
 *   Example: User edits m3 (whose parentUuid is m2), should set parentUuid = "m2".
 */
export interface StreamJsonUserMessage {
  type: 'user';
  message: UserMessagePayload;
  session_id?: string;
  parent_tool_use_id?: string;
  /**
   * Edit-resend: specify parent message UUID to create a new conversation branch.
   */
  parentUuid?: string;
}

/**
 * Inner message payload for stream-json.
 */
export interface UserMessagePayload {
  role: 'user';
  content: UserInputContent[];
}

/**
 * Helper function to create UserMessagePayload from text.
 */
export function createTextPayload(text: string): UserMessagePayload {
  return {
    role: 'user',
    content: [{ type: 'text', text }],
  };
}
