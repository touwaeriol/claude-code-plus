package com.asakii.ai.agent.sdk.adapter

import com.asakii.ai.agent.sdk.model.*
import com.asakii.claude.agent.sdk.types.ToolType

/**
 * 将归一化事件转换为前端直接使用的 UI 事件。
 */
class UiStreamAdapter {

    fun convert(event: NormalizedStreamEvent): List<UiStreamEvent> =
        when (event) {
            is MessageStartedEvent -> listOf(UiMessageStart(event.messageId, event.initialContent))
            is ContentDeltaEvent -> convertDelta(event)
            is ContentStartedEvent -> convertContentStart(event)
            is ContentCompletedEvent -> convertContentComplete(event)
            is TurnCompletedEvent -> listOf(UiMessageComplete(event.usage))
            is TurnFailedEvent -> listOf(UiError(event.error))
            is ResultSummaryEvent -> listOf(
                UiResultMessage(
                    subtype = event.subtype,
                    durationMs = event.durationMs,
                    durationApiMs = event.durationApiMs,
                    isError = event.isError,
                    numTurns = event.numTurns,
                    sessionId = event.sessionId,
                    totalCostUsd = event.totalCostUsd,
                    usage = event.usage,
                    result = event.result
                )
            )
            is AssistantMessageEvent -> listOf(
                UiAssistantMessage(id = event.id, content = event.content)
            )
            is UserMessageEvent -> listOf(
                UiUserMessage(event.content)
            )
            is TurnStartedEvent -> emptyList()
        }

    private fun convertDelta(event: ContentDeltaEvent): List<UiStreamEvent> =
        when (val delta = event.delta) {
            is TextDeltaPayload -> listOf(UiTextDelta(delta.text, index = event.index))
            is ThinkingDeltaPayload -> listOf(UiThinkingDelta(delta.thinking, index = event.index))
            is ToolDeltaPayload -> listOf(
                UiToolProgress(
                    toolId = event.index.toString(),
                    status = ContentStatus.IN_PROGRESS,
                    outputPreview = delta.partialJson
                )
            )
            is CommandDeltaPayload -> listOf(
                UiToolProgress(
                    toolId = event.index.toString(),
                    status = ContentStatus.IN_PROGRESS,
                    outputPreview = delta.output
                )
            )
        }

    // 追踪内容块索引
    private var contentIndexCounter = 0

    private fun convertContentStart(event: ContentStartedEvent): List<UiStreamEvent> {
        return when {
            event.contentType.contains("tool") || event.contentType.contains("command") -> {
                val toolName = event.toolName ?: event.contentType
                val toolTypeEnum = ToolType.fromToolName(toolName)
                // 对于工具调用，从 content 中获取原生 id（如果有）
                val toolId = (event.content as? ToolUseContent)?.id ?: event.index.toString()
                listOf(
                    UiToolStart(
                        toolId = toolId,
                        toolName = toolName,
                        toolType = toolTypeEnum.type
                    )
                )
            }
            event.contentType.contains("text") -> listOf(UiTextStart(event.index))
            event.contentType.contains("thinking") -> listOf(UiThinkingStart(event.index))
            else -> emptyList()
        }
    }

    // 重置索引计数器（在 message_start 时调用）
    fun resetContentIndex() {
        contentIndexCounter = 0
    }

    private fun convertContentComplete(event: ContentCompletedEvent): List<UiStreamEvent> {
        return when (val content = event.content) {
            // TextContent 和 ThinkingContent：不再单独发送，因为 AssistantMessageEvent 已包含完整内容
            // 避免前端重复显示消息
            is TextContent -> emptyList()
            is ThinkingContent -> emptyList()
            is ToolUseContent -> listOf(
                UiToolComplete(
                    toolId = content.id,
                    result = content
                )
            )
            is CommandExecutionContent,
            is ToolResultContent,
            is McpToolCallContent -> listOf(
                UiToolComplete(
                    toolId = event.index.toString(),
                    result = content
                )
            )
            else -> emptyList()
        }
    }
}


