package com.claudecodeplus.sdk.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Anthropic API Stream Event 类型定义
 * 
 * 参考：https://docs.anthropic.com/claude/reference/streaming
 * 
 * Stream events 是 Claude API 在流式响应中发送的增量更新事件。
 * 当 includePartialMessages = true 时，SDK 会将这些事件包装为 StreamEvent 消息。
 */

/**
 * Stream Event 的基础接口
 * 所有 stream event 都有一个 type 字段
 */
@Serializable
sealed interface StreamEventType {
    val type: String
}

/**
 * Content Block Delta Event
 * 
 * 当内容块的内容发生变化时发送（文本增量、工具输入增量、thinking 增量等）
 * 
 * delta 可以是以下类型之一：
 * - TextDelta: 文本内容的增量更新
 * - InputJsonDelta: 工具输入 JSON 的增量更新
 * - ThinkingDelta: Thinking 内容的增量更新
 * 
 * 示例：
 * {
 *   "type": "content_block_delta",
 *   "index": 0,
 *   "delta": {
 *     "type": "text_delta",
 *     "text": "Hello"
 *   }
 * }
 */
@Serializable
@SerialName("content_block_delta")
data class ContentBlockDeltaEvent(
    val index: Int,
    val delta: JsonElement  // 可以是 TextDelta、InputJsonDelta 或 ThinkingDelta
) : StreamEventType {
    override val type: String = "content_block_delta"
}

/**
 * Content Block Start Event
 * 
 * 当新的内容块开始时发送
 * 
 * 示例：
 * {
 *   "type": "content_block_start",
 *   "index": 0,
 *   "content_block": {
 *     "type": "text",
 *     "text": ""
 *   }
 * }
 */
@Serializable
@SerialName("content_block_start")
data class ContentBlockStartEvent(
    val index: Int,
    @SerialName("content_block")
    val contentBlock: JsonElement
) : StreamEventType {
    override val type: String = "content_block_start"
}

/**
 * Content Block Stop Event
 * 
 * 当内容块结束时发送
 * 
 * 示例：
 * {
 *   "type": "content_block_stop",
 *   "index": 0
 * }
 */
@Serializable
@SerialName("content_block_stop")
data class ContentBlockStopEvent(
    val index: Int
) : StreamEventType {
    override val type: String = "content_block_stop"
}

/**
 * Message Delta Event
 * 
 * 当消息的元数据发生变化时发送（如 usage）
 * 
 * 示例：
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
@Serializable
@SerialName("message_delta")
data class MessageDeltaEvent(
    val delta: JsonElement,
    val usage: JsonElement? = null
) : StreamEventType {
    override val type: String = "message_delta"
}

/**
 * Message Start Event
 * 
 * 当新消息开始时发送
 * 
 * 示例：
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
@Serializable
@SerialName("message_start")
data class MessageStartEvent(
    val message: JsonElement
) : StreamEventType {
    override val type: String = "message_start"
}

/**
 * Message Stop Event
 * 
 * 当消息结束时发送
 * 
 * 示例：
 * {
 *   "type": "message_stop"
 * }
 */
@Serializable
@SerialName("message_stop")
class MessageStopEvent : StreamEventType {
    override val type: String = "message_stop"
}

/**
 * Delta 类型定义
 */

/**
 * Text Delta
 * 
 * 文本内容的增量更新
 * 
 * 示例：
 * {
 *   "type": "text_delta",
 *   "text": "Hello"
 * }
 */
@Serializable
@SerialName("text_delta")
data class TextDelta(
    val text: String
) {
    val type: String = "text_delta"
}

/**
 * Input JSON Delta
 * 
 * 工具输入 JSON 的增量更新
 * 
 * 注意：partial_json 是增量字符串，需要累积后才能解析为完整 JSON
 * 
 * 示例：
 * {
 *   "type": "input_json_delta",
 *   "partial_json": "{\"file_path\":\""
 * }
 */
@Serializable
@SerialName("input_json_delta")
data class InputJsonDelta(
    @SerialName("partial_json")
    val partialJson: String
) {
    val type: String = "input_json_delta"
}

/**
 * Thinking Delta
 * 
 * Thinking 内容的增量更新
 * 
 * 当 Claude 使用 thinking 模式时，thinking 内容会通过流式传输
 * 
 * 示例：
 * {
 *   "type": "thinking_delta",
 *   "delta": "I need to"
 * }
 */
@Serializable
@SerialName("thinking_delta")
data class ThinkingDelta(
    val delta: String
) {
    val type: String = "thinking_delta"
}

