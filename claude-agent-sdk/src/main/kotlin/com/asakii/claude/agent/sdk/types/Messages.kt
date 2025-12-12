package com.asakii.claude.agent.sdk.types

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
    val sessionId: String = "default",
    /**
     * 是否是回放消息（用于区分压缩摘要和确认消息）
     * - isReplay = false: 压缩摘要（新生成的上下文）
     * - isReplay = true: 确认消息（如 "Compacted"）
     */
    val isReplay: Boolean? = null
) : Message

/**
 * Assistant message containing Claude's response.
 */
@Serializable
@SerialName("assistant")
data class AssistantMessage(
    val id: String? = null,
    val content: List<ContentBlock>,
    val model: String,
    @SerialName("token_usage")
    val tokenUsage: TokenUsage? = null,
    /**
     * 父工具调用 ID（用于子代理消息路由）
     * - null: 主会话消息
     * - 非 null: 子代理消息，值为触发该子代理的 Task 工具调用 ID
     */
    @SerialName("parent_tool_use_id")
    val parentToolUseId: String? = null
) : Message

/**
 * System message with metadata.
 *
 * 注意：这是通用的系统消息类型，需要有 data 字段。
 * 对于特殊的系统消息（如 status, compact_boundary），使用专门的类型。
 */
@Serializable
@SerialName("system")
data class SystemMessage(
    val subtype: String,
    val data: JsonElement
) : Message

/**
 * 状态系统消息 - 用于通知客户端状态变化
 *
 * 示例：{"type":"system","subtype":"status","status":"compacting","session_id":"..."}
 */
@Serializable
data class StatusSystemMessage(
    val subtype: String = "status",
    val status: String?,  // 如 "compacting" 或 null
    @SerialName("session_id")
    val sessionId: String,
    val uuid: String? = null
) : Message

/**
 * 压缩边界消息 - 标记会话压缩的边界
 *
 * 示例：{"type":"system","subtype":"compact_boundary","session_id":"...","compact_metadata":{"trigger":"manual","pre_tokens":33767}}
 */
@Serializable
data class CompactBoundaryMessage(
    val subtype: String = "compact_boundary",
    @SerialName("session_id")
    val sessionId: String,
    val uuid: String? = null,
    @SerialName("compact_metadata")
    val compactMetadata: CompactMetadata? = null
) : Message

/**
 * 压缩元数据
 */
@Serializable
data class CompactMetadata(
    val trigger: String? = null,  // "manual" 或 "auto"
    @SerialName("pre_tokens")
    val preTokens: Int? = null    // 压缩前的 token 数
)

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