/**
 * Stream event types for Claude Agent SDK
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/StreamEvents.kt
 */

import type { JsonValue, JsonObject } from './common';
import type { ContentBlock } from './contentBlocks';

/**
 * Anthropic API Stream Event type definitions.
 *
 * Reference: https://docs.anthropic.com/claude/reference/streaming
 *
 * Stream events are incremental update events sent by Claude API during streaming responses.
 * When includePartialMessages = true, SDK wraps these events as StreamEvent messages.
 */

/**
 * Base type for all stream events.
 * All stream events have a type field.
 */
export type StreamEventType =
  | ContentBlockDeltaEvent
  | ContentBlockStartEvent
  | ContentBlockStopEvent
  | MessageDeltaEvent
  | MessageStartEvent
  | MessageStopEvent;

/**
 * Content Block Delta Event.
 *
 * Sent when content block content changes (text delta, tool input delta, thinking delta, etc.).
 *
 * delta can be one of:
 * - TextDelta: Text content incremental update
 * - InputJsonDelta: Tool input JSON incremental update
 * - ThinkingDelta: Thinking content incremental update
 *
 * Example:
 * {
 *   "type": "content_block_delta",
 *   "index": 0,
 *   "delta": {
 *     "type": "text_delta",
 *     "text": "Hello"
 *   }
 * }
 */
export interface ContentBlockDeltaEvent {
  type: 'content_block_delta';
  index: number;
  /** Can be TextDelta, InputJsonDelta, or ThinkingDelta */
  delta: JsonValue;
}

/**
 * Content Block Start Event.
 *
 * Sent when a new content block starts.
 *
 * Example:
 * {
 *   "type": "content_block_start",
 *   "index": 0,
 *   "content_block": {
 *     "type": "text",
 *     "text": ""
 *   }
 * }
 */
export interface ContentBlockStartEvent {
  type: 'content_block_start';
  index: number;
  content_block: ContentBlock;
}

/**
 * Content Block Stop Event.
 *
 * Sent when content block ends.
 *
 * Example:
 * {
 *   "type": "content_block_stop",
 *   "index": 0
 * }
 */
export interface ContentBlockStopEvent {
  type: 'content_block_stop';
  index: number;
}

/**
 * Message Delta Event.
 *
 * Sent when message metadata changes (like usage).
 *
 * Example:
 * {
 *   "type": "message_delta",
 *   "delta": {
 *     "stop_reason": "end_turn",
 *     "stop_sequence": null
 *   },
 *   "usage": {
 *     "output_tokens": 10
 *   }
 * }
 */
export interface MessageDeltaEvent {
  type: 'message_delta';
  delta: JsonValue;
  usage?: JsonValue;
}

/**
 * Message Start Event.
 *
 * Sent when a new message starts.
 *
 * Example:
 * {
 *   "type": "message_start",
 *   "message": {
 *     "id": "msg_123",
 *     "type": "message",
 *     "role": "assistant",
 *     "content": [],
 *     "model": "claude-sonnet-4-20250514"
 *   }
 * }
 */
export interface MessageStartEvent {
  type: 'message_start';
  message: JsonValue;
}

/**
 * Message Stop Event.
 *
 * Sent when message ends.
 *
 * Example:
 * {
 *   "type": "message_stop"
 * }
 */
export interface MessageStopEvent {
  type: 'message_stop';
}

// ============================================================================
// Delta Types
// ============================================================================

/**
 * Text Delta.
 *
 * Text content incremental update.
 *
 * Example:
 * {
 *   "type": "text_delta",
 *   "text": "Hello"
 * }
 */
export interface TextDelta {
  type: 'text_delta';
  text: string;
}

/**
 * Input JSON Delta.
 *
 * Tool input JSON incremental update.
 *
 * Note: partial_json is an incremental string, needs to be accumulated before parsing as complete JSON.
 *
 * Example:
 * {
 *   "type": "input_json_delta",
 *   "partial_json": "{\"file_path\":\""
 * }
 */
export interface InputJsonDelta {
  type: 'input_json_delta';
  partial_json: string;
}

/**
 * Thinking Delta.
 *
 * Thinking content incremental update.
 *
 * When Claude uses thinking mode, thinking content is streamed.
 *
 * Example:
 * {
 *   "type": "thinking_delta",
 *   "thinking": "I need to"
 * }
 */
export interface ThinkingDelta {
  type: 'thinking_delta';
  thinking: string;
}

/**
 * Union type for all delta types.
 */
export type DeltaType = TextDelta | InputJsonDelta | ThinkingDelta;

/**
 * Type guard for TextDelta.
 */
export function isTextDelta(delta: unknown): delta is TextDelta {
  return (
    typeof delta === 'object' &&
    delta !== null &&
    'type' in delta &&
    delta.type === 'text_delta'
  );
}

/**
 * Type guard for InputJsonDelta.
 */
export function isInputJsonDelta(delta: unknown): delta is InputJsonDelta {
  return (
    typeof delta === 'object' &&
    delta !== null &&
    'type' in delta &&
    delta.type === 'input_json_delta'
  );
}

/**
 * Type guard for ThinkingDelta.
 */
export function isThinkingDelta(delta: unknown): delta is ThinkingDelta {
  return (
    typeof delta === 'object' &&
    delta !== null &&
    'type' in delta &&
    delta.type === 'thinking_delta'
  );
}
