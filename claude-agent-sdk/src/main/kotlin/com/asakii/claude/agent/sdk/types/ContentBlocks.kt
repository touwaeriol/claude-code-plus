package com.asakii.claude.agent.sdk.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Base interface for all content blocks.
 */
@Serializable
sealed interface ContentBlock

/**
 * Text content block.
 */
@Serializable
@SerialName("text")
data class TextBlock(
    val text: String
) : ContentBlock

/**
 * Thinking content block for Claude's reasoning process.
 */
@Serializable
@SerialName("thinking")
data class ThinkingBlock(
    val thinking: String,
    val signature: String? = null
) : ContentBlock

/**
 * Tool use content block.
 *
 * 注意：在 content_block_start 事件中，input 可能是空对象或不存在
 */
@Serializable
@SerialName("tool_use")
data class ToolUseBlock(
    val id: String,
    val name: String,
    val input: JsonElement = kotlinx.serialization.json.JsonObject(emptyMap())  // 默认为空对象，因为 stream 开始时 input 可能为空
) : ContentBlock

/**
 * Tool result content block.
 */
@Serializable
@SerialName("tool_result")
data class ToolResultBlock(
    @SerialName("tool_use_id")
    val toolUseId: String,
    val content: JsonElement? = null,
    @SerialName("is_error")
    val isError: Boolean? = null
) : ContentBlock

/**
 * Image content block for stream-json input.
 */
@Serializable
@SerialName("image")
data class ImageBlock(
    val data: String,      // base64 encoded image data
    val mimeType: String   // e.g., "image/png", "image/jpeg"
) : ContentBlock

/**
 * User input content - can be text, image, or mixed.
 * Used for stream-json input format.
 *
 * 使用 classDiscriminator = "type" 来匹配 Claude CLI 的格式
 */
@Serializable
sealed class UserInputContent

/**
 * Simple text input.
 * 序列化为: {"type": "text", "text": "..."}
 */
@Serializable
@SerialName("text")
data class TextInput(
    val text: String
) : UserInputContent()

/**
 * Image source for Anthropic API format.
 * 序列化为: {"type": "base64", "media_type": "...", "data": "..."}
 */
@Serializable
data class ImageSource(
    val type: String = "base64",
    @SerialName("media_type")
    val mediaType: String,   // e.g., "image/png", "image/jpeg"
    val data: String         // base64 encoded image data (without data URL prefix)
)

/**
 * Image input matching Anthropic API format.
 * 序列化为: {"type": "image", "source": {"type": "base64", "media_type": "...", "data": "..."}}
 */
@Serializable
@SerialName("image")
data class ImageInput(
    val source: ImageSource
) : UserInputContent() {
    companion object {
        /**
         * 便捷构造函数：从 base64 数据和 MIME 类型创建 ImageInput
         */
        fun fromBase64(data: String, mimeType: String): ImageInput {
            // 如果 data 包含 data URL 前缀，去掉它
            val cleanData = if (data.contains(",")) {
                data.substringAfter(",")
            } else {
                data
            }
            return ImageInput(
                source = ImageSource(
                    type = "base64",
                    mediaType = mimeType,
                    data = cleanData
                )
            )
        }
    }
}

/**
 * Tool result input - used to respond to tool calls.
 *
 * 序列化为:
 * ```json
 * {
 *   "type": "tool_result",
 *   "tool_use_id": "toolu_xxx",
 *   "content": "用户选择的结果",
 *   "is_error": false
 * }
 * ```
 *
 * 用于响应 AskUserQuestion、ExitPlanMode 等需要用户交互的工具调用。
 */
@Serializable
@SerialName("tool_result")
data class ToolResultInput(
    @SerialName("tool_use_id")
    val toolUseId: String,
    val content: String,
    @SerialName("is_error")
    val isError: Boolean = false
) : UserInputContent()

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
 * @property parentUuid 编辑重发功能：指定新消息的父消息 UUID。
 *   当用户编辑重发消息时，设置此字段为要替换的消息的父消息的 UUID，
 *   CLI 会自动创建一个新的对话分支。
 *   例如：用户编辑 m3（其 parentUuid 为 m2），应设置 parentUuid = "m2"。
 */
@Serializable
data class StreamJsonUserMessage(
    val type: String = "user",
    val message: UserMessagePayload,
    @SerialName("session_id")
    val sessionId: String = "default",
    @SerialName("parent_tool_use_id")
    val parentToolUseId: String? = null,
    /**
     * 编辑重发：指定父消息 UUID，用于创建新的对话分支
     */
    val parentUuid: String? = null
)

/**
 * Inner message payload for stream-json.
 */
@Serializable
data class UserMessagePayload(
    val role: String = "user",
    val content: List<UserInputContent>
) {
    constructor(text: String) : this(content = listOf(TextInput(text)))
}