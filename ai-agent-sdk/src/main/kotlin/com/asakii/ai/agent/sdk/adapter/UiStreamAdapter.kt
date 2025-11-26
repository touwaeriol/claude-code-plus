package com.asakii.ai.agent.sdk.adapter

import com.asakii.ai.agent.sdk.model.*

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
            is TurnStartedEvent -> emptyList()
        }

    private fun convertDelta(event: ContentDeltaEvent): List<UiStreamEvent> =
        when (val delta = event.delta) {
            is TextDeltaPayload -> listOf(UiTextDelta(delta.text))
            is ThinkingDeltaPayload -> listOf(UiThinkingDelta(delta.thinking))
            is ToolDeltaPayload -> listOf(
                UiToolProgress(
                    toolId = event.id,
                    status = ContentStatus.IN_PROGRESS,
                    outputPreview = delta.partialJson
                )
            )
            is CommandDeltaPayload -> listOf(
                UiToolProgress(
                    toolId = event.id,
                    status = ContentStatus.IN_PROGRESS,
                    outputPreview = delta.output
                )
            )
        }

    private fun convertContentStart(event: ContentStartedEvent): List<UiStreamEvent> {
        return if (event.contentType.contains("tool") || event.contentType.contains("command")) {
            listOf(
                UiToolStart(
                    toolId = event.id,
                    toolName = event.contentType
                )
            )
        } else {
            emptyList()
        }
    }

    private fun convertContentComplete(event: ContentCompletedEvent): List<UiStreamEvent> {
        return when (val content = event.content) {
            // TextContent 和 ThinkingContent：发送完整消息用于校验
            // 使用 UiAssistantMessage 而不是 UiTextDelta，避免前端重复追加
            is TextContent -> listOf(UiAssistantMessage(listOf(content)))
            is ThinkingContent -> listOf(UiAssistantMessage(listOf(content)))
            is ToolUseContent,
            is CommandExecutionContent,
            is ToolResultContent,
            is McpToolCallContent -> listOf(
                UiToolComplete(
                    toolId = event.id,
                    result = content
                )
            )
            else -> emptyList()
        }
    }
}


