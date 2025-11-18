package com.claudecodeplus.rpc.api

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
    val signature: String
) : ContentBlock

/**
 * Tool use content block.
 */
@Serializable
@SerialName("tool_use")
data class ToolUseBlock(
    val id: String,
    val name: String,
    val input: JsonElement
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
 * Image source for image content blocks.
 */
@Serializable
data class ImageSource(
    val type: String, // "base64" or "url"
    @SerialName("media_type")
    val mediaType: String, // e.g., "image/png"
    val data: String? = null, // base64 encoded data
    val url: String? = null // URL to image
)

/**
 * Image content block.
 */
@Serializable
@SerialName("image")
data class ImageBlock(
    val source: ImageSource
) : ContentBlock

