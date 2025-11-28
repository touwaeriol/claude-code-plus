package com.asakii.ai.agent.sdk.model

import com.asakii.ai.agent.sdk.AiAgentProvider
import kotlinx.serialization.json.JsonElement

/**
 * 原始流事件，保留底层 SDK 的原始 payload，便于调试。
 */
sealed interface RawStreamEvent {
    val provider: AiAgentProvider
}

data class ClaudeRawEvent(
    val payload: Any
) : RawStreamEvent {
    override val provider: AiAgentProvider = AiAgentProvider.CLAUDE
}

data class CodexRawEvent(
    val payload: Any
) : RawStreamEvent {
    override val provider: AiAgentProvider = AiAgentProvider.CODEX
}

/**
 * 归一化后的语义事件。
 */
sealed interface NormalizedStreamEvent {
    val provider: AiAgentProvider
}

data class MessageStartedEvent(
    override val provider: AiAgentProvider,
    val sessionId: String,
    val messageId: String,
    val initialContent: List<UnifiedContentBlock>? = null
) : NormalizedStreamEvent

data class TurnStartedEvent(
    override val provider: AiAgentProvider
) : NormalizedStreamEvent

data class ContentStartedEvent(
    override val provider: AiAgentProvider,
    val id: String,
    val contentType: String,
    /** 工具名称（仅当 contentType 为 tool_use 类型时有值） */
    val toolName: String? = null
) : NormalizedStreamEvent

data class ContentDeltaEvent(
    override val provider: AiAgentProvider,
    val id: String,
    val delta: ContentDeltaPayload
) : NormalizedStreamEvent

data class ContentCompletedEvent(
    override val provider: AiAgentProvider,
    val id: String,
    val content: UnifiedContentBlock
) : NormalizedStreamEvent

data class TurnCompletedEvent(
    override val provider: AiAgentProvider,
    val usage: UnifiedUsage?
) : NormalizedStreamEvent

data class TurnFailedEvent(
    override val provider: AiAgentProvider,
    val error: String
) : NormalizedStreamEvent

data class ResultSummaryEvent(
    override val provider: AiAgentProvider,
    val durationMs: Long,
    val durationApiMs: Long,
    val isError: Boolean,
    val numTurns: Int,
    val sessionId: String,
    val totalCostUsd: Double? = null,
    val usage: JsonElement? = null,
    val result: String? = null
) : NormalizedStreamEvent

data class AssistantMessageEvent(
    override val provider: AiAgentProvider,
    val content: List<UnifiedContentBlock>,
    val model: String? = null,
    val tokenUsage: UnifiedUsage? = null
) : NormalizedStreamEvent

data class UserMessageEvent(
    override val provider: AiAgentProvider,
    val content: List<UnifiedContentBlock>
) : NormalizedStreamEvent

/**
 * UI 直接消费的事件。
 */
sealed interface UiStreamEvent

data class UiMessageStart(
    val messageId: String,
    val content: List<UnifiedContentBlock>? = null
) : UiStreamEvent

data class UiTextDelta(
    val text: String
) : UiStreamEvent

data class UiThinkingDelta(
    val thinking: String
) : UiStreamEvent

data class UiToolStart(
    val toolId: String,
    val toolName: String,      // 显示名称: "Read", "Write", "mcp__xxx"
    val toolType: String,      // 类型标识: "CLAUDE_READ", "CLAUDE_WRITE", "MCP"
    val inputPreview: String? = null
) : UiStreamEvent

data class UiToolProgress(
    val toolId: String,
    val status: ContentStatus,
    val outputPreview: String? = null
) : UiStreamEvent

data class UiToolComplete(
    val toolId: String,
    val result: UnifiedContentBlock
) : UiStreamEvent

data class UiMessageComplete(
    val usage: UnifiedUsage?
) : UiStreamEvent

data class UiError(
    val message: String
) : UiStreamEvent

/**
 * 完整的助手消息（用于校验流式响应）
 * 在流式响应结束后发送，包含完整的内容块列表
 */
data class UiAssistantMessage(
    val content: List<UnifiedContentBlock>
) : UiStreamEvent

data class UiResultMessage(
    val durationMs: Long,
    val durationApiMs: Long,
    val isError: Boolean,
    val numTurns: Int,
    val sessionId: String,
    val totalCostUsd: Double? = null,
    val usage: JsonElement? = null,
    val result: String? = null
) : UiStreamEvent

data class UiUserMessage(
    val content: List<UnifiedContentBlock>
) : UiStreamEvent

/**
 * 内容增量，对应 text/thinking/command 等不同形态。
 */
sealed interface ContentDeltaPayload {
    val type: String
}

data class TextDeltaPayload(
    val text: String
) : ContentDeltaPayload {
    override val type: String = "text"
}

data class ThinkingDeltaPayload(
    val thinking: String
) : ContentDeltaPayload {
    override val type: String = "thinking"
}

data class ToolDeltaPayload(
    val partialJson: String
) : ContentDeltaPayload {
    override val type: String = "tool"
}

data class CommandDeltaPayload(
    val output: String
) : ContentDeltaPayload {
    override val type: String = "command"
}

