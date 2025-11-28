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
                UiAssistantMessage(event.content)
            )
            is UserMessageEvent -> listOf(
                UiUserMessage(event.content)
            )
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
            val toolName = event.toolName ?: event.contentType
            val toolTypeEnum = ToolType.fromToolName(toolName)

            // 🔧 调试日志
            println("📦 [UiStreamAdapter] convertContentStart: contentType=${event.contentType}, event.toolName=${event.toolName}, resolvedToolName=$toolName, toolType=${toolTypeEnum.type}")

            listOf(
                UiToolStart(
                    toolId = event.id,
                    // 使用实际的工具名称（如 "TodoWrite"），如果没有则回退到 contentType
                    toolName = toolName,
                    // 类型标识: "CLAUDE_READ", "CLAUDE_WRITE", "MCP" 等
                    toolType = toolTypeEnum.type
                )
            )
        } else {
            emptyList()
        }
    }

    private fun convertContentComplete(event: ContentCompletedEvent): List<UiStreamEvent> {
        return when (val content = event.content) {
            // TextContent 和 ThinkingContent：不再单独发送，因为 AssistantMessageEvent 已包含完整内容
            // 避免前端重复显示消息
            is TextContent -> emptyList()
            is ThinkingContent -> emptyList()
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


