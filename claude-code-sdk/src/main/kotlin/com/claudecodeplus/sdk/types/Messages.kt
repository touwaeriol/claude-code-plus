package com.claudecodeplus.sdk.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Base interface for all message types.
 */
@Serializable
sealed interface Message

/**
 * User message containing user input.
 */
@Serializable
@SerialName("user")
data class UserMessage(
    val content: JsonElement, // Can be string or list of ContentBlock
    @SerialName("parent_tool_use_id")
    val parentToolUseId: String? = null,
    @SerialName("session_id")
    val sessionId: String = "default"
) : Message

/**
 * Assistant message containing Claude's response.
 */
@Serializable
@SerialName("assistant")
data class AssistantMessage(
    val content: List<ContentBlock>,
    val model: String,
    @SerialName("token_usage")
    val tokenUsage: TokenUsage? = null
) : Message

/**
 * System message with metadata.
 */
@Serializable
@SerialName("system")
data class SystemMessage(
    val subtype: String,
    val data: JsonElement
) : Message

/**
 * Result message with cost and usage information.
 */
@Serializable
@SerialName("result")
data class ResultMessage(
    val subtype: String,
    @SerialName("duration_ms")
    val durationMs: Long,
    @SerialName("duration_api_ms")
    val durationApiMs: Long,
    @SerialName("is_error")
    val isError: Boolean,
    @SerialName("num_turns")
    val numTurns: Int,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("total_cost_usd")
    val totalCostUsd: Double? = null,
    val usage: JsonElement? = null,
    val result: String? = null
) : Message

/**
 * Stream event for partial message updates during streaming.
 * Only available when includePartialMessages is enabled.
 */
@Serializable
@SerialName("stream_event")
data class StreamEvent(
    val uuid: String,
    @SerialName("session_id")
    val sessionId: String,
    val event: JsonElement, // Raw Anthropic API stream event
    @SerialName("parent_tool_use_id")
    val parentToolUseId: String? = null
) : Message

/**
 * Token usage information.
 */
@Serializable
data class TokenUsage(
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int,
    @SerialName("cache_creation_input_tokens")
    val cacheCreationInputTokens: Int? = null,
    @SerialName("cache_read_input_tokens")
    val cacheReadInputTokens: Int? = null
)