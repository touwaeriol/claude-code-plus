package com.asakii.ai.agent.sdk.adapter

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.model.*
import com.asakii.codex.agent.sdk.ThreadEvent
import com.asakii.codex.agent.sdk.ThreadItem
import com.asakii.codex.agent.sdk.Usage
import kotlinx.serialization.json.JsonElement
import java.util.UUID

/**
 * 将 Codex ThreadEvent 流转换为统一的 NormalizedStreamEvent。
 */
class CodexStreamAdapter(
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    private val itemsBuffer = mutableMapOf<Int, ThreadItem>()
    private var indexCounter = 0

    fun convert(event: ThreadEvent): List<NormalizedStreamEvent> {
        val result = mutableListOf<NormalizedStreamEvent>()
        when (event.type) {
            "thread.started" -> {
                val id = event.threadId?.takeIf { it.isNotBlank() } ?: idGenerator()
                result += MessageStartedEvent(
                    provider = AiAgentProvider.CODEX,
                    sessionId = id,
                    messageId = id
                )
            }

            "turn.started" -> {
                result += TurnStartedEvent(AiAgentProvider.CODEX)
            }

            "item.started" -> {
                val item = event.item ?: return result
                val index = indexCounter++
                itemsBuffer[index] = item
                result += ContentStartedEvent(
                    provider = AiAgentProvider.CODEX,
                    index = index,
                    contentType = item.type
                )
            }

            "item.updated" -> {
                val item = event.item ?: return result
                // 找到对应的 index
                val index = itemsBuffer.entries.find { it.value.id == item.id }?.key ?: return result
                itemsBuffer[index] = item
                extractItemDelta(item)?.let { delta ->
                    result += ContentDeltaEvent(
                        provider = AiAgentProvider.CODEX,
                        index = index,
                        delta = delta
                    )
                }
            }

            "item.completed" -> {
                val item = event.item ?: return result
                val index = itemsBuffer.entries.find { it.value.id == item.id }?.key
                if (index != null) {
                    itemsBuffer.remove(index)
                    result += ContentCompletedEvent(
                        provider = AiAgentProvider.CODEX,
                        index = index,
                        content = convertThreadItem(item)
                    )
                }
            }

            "turn.completed" -> {
                result += TurnCompletedEvent(
                    provider = AiAgentProvider.CODEX,
                    usage = event.usage?.toUnifiedUsage()
                )
            }

            "turn.failed" -> {
                val errorMsg = event.error?.message ?: "Codex turn failed"
                result += TurnFailedEvent(
                    provider = AiAgentProvider.CODEX,
                    error = errorMsg
                )
            }
        }
        return result
    }

    private fun convertThreadItem(item: ThreadItem): UnifiedContentBlock =
        when (item.type) {
            "agent_message" -> TextContent(item.text.orEmpty())
            "reasoning" -> ThinkingContent(item.text.orEmpty())
            "command_execution" -> CommandExecutionContent(
                command = item.command ?: "",
                output = item.aggregatedOutput,
                exitCode = item.exitCode,
                status = item.status.toContentStatus()
            )
            "file_change" -> FileChangeContent(
                changes = item.changes.orEmpty(),
                status = item.status.toContentStatus()
            )
            "mcp_tool_call" -> McpToolCallContent(
                server = null,
                tool = null,
                arguments = item.arguments,
                result = item.result?.structuredContent ?: item.result?.content,
                status = item.status.toContentStatus()
            )
            "web_search" -> WebSearchContent(item.query.orEmpty())
            "todo_list" -> TodoListContent(item.items.orEmpty())
            "error" -> ErrorContent(item.error?.message ?: "Unknown Codex error")
            else -> TextContent(item.text ?: item.content?.toString().orEmpty())
        }

    private fun extractItemDelta(item: ThreadItem): ContentDeltaPayload? =
        when (item.type) {
            "agent_message" -> item.text?.let { TextDeltaPayload(it) }
            "reasoning" -> item.text?.let { ThinkingDeltaPayload(it) }
            "command_execution" -> item.aggregatedOutput?.let { CommandDeltaPayload(it) }
            else -> null
        }

    private fun Usage.toUnifiedUsage(): UnifiedUsage =
        UnifiedUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = cachedInputTokens,
            provider = AiAgentProvider.CODEX
        )

    private fun String?.toContentStatus(): ContentStatus =
        when (this?.lowercase()) {
            "in_progress" -> ContentStatus.IN_PROGRESS
            "failed" -> ContentStatus.FAILED
            else -> ContentStatus.COMPLETED
        }
}

