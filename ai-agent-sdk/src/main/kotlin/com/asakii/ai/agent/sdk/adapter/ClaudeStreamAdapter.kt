package com.asakii.ai.agent.sdk.adapter

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.model.*
import com.asakii.claude.agent.sdk.types.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * 将 Claude Agent SDK 的消息流转换为统一的 NormalizedStreamEvent。
 */
class ClaudeStreamAdapter(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    private val blockBuffer = mutableMapOf<Int, ContentBlockState>()

    fun convert(message: Message): List<NormalizedStreamEvent> = when (message) {
        is StreamEvent -> handleStreamEvent(message)
        is AssistantMessage -> handleAssistantMessage(message)
        is ResultMessage -> handleResultMessage(message)
        else -> emptyList()
    }

    private fun handleAssistantMessage(message: AssistantMessage): List<NormalizedStreamEvent> {
        // AssistantMessage 是 Claude Code CLI 在流式事件结束后发送的完整消息
        // 由于内容已经通过 ContentBlockStopEvent 完成了，这里不再生成重复事件
        // 仅在调试时记录
        return emptyList()
    }

    private fun handleResultMessage(message: ResultMessage): List<NormalizedStreamEvent> {
        val usage = message.usage?.let { toUnifiedUsage(it) }
        return if (message.isError) {
            listOf(
                TurnFailedEvent(
                    provider = AiAgentProvider.CLAUDE,
                    error = message.result ?: "Claude CLI 返回错误"
                )
            )
        } else {
            listOf(
                TurnCompletedEvent(
                    provider = AiAgentProvider.CLAUDE,
                    usage = usage
                )
            )
        }
    }

    private fun handleStreamEvent(message: StreamEvent): List<NormalizedStreamEvent> {
        val events = mutableListOf<NormalizedStreamEvent>()
        val parsed = runCatching {
            json.decodeFromJsonElement(StreamEventType.serializer(), message.event)
        }.getOrNull() ?: return events

        when (parsed) {
            is MessageStartEvent -> {
                val messageId = extractMessageId(parsed.message)?.takeIf { it.isNotBlank() }
                if (messageId == null) {
                    blockBuffer.clear()
                    events += TurnFailedEvent(
                        provider = AiAgentProvider.CLAUDE,
                        error = "Claude stream 消息缺少 message_id，sessionId=${message.sessionId}"
                    )
                } else {
                    val initialBlocks = parseInitialBlocks(parsed.message)
                    initialBlocks.forEachIndexed { index, state ->
                        blockBuffer[index] = state
                        events += ContentStartedEvent(
                            provider = AiAgentProvider.CLAUDE,
                            id = state.id,
                            contentType = state.block?.let { it::class.simpleName?.lowercase() } ?: "unknown"
                        )
                        buildInitialDelta(state)?.let { delta ->
                            events += ContentDeltaEvent(
                                provider = AiAgentProvider.CLAUDE,
                                id = state.id,
                                delta = delta
                            )
                        }
                    }
                    val initialContent = initialBlocks.map { buildContentBlock(it) }.takeIf { it.isNotEmpty() }
                    events += MessageStartedEvent(
                        provider = AiAgentProvider.CLAUDE,
                        sessionId = message.sessionId,
                        messageId = messageId,
                        initialContent = initialContent
                    )
                }
            }

            is ContentBlockStartEvent -> {
                if (blockBuffer.containsKey(parsed.index)) {
                    // 已有初始块（可能来自 message_start），不重复创建
                    return events
                }
                val blockId = "${message.sessionId}-${parsed.index}-${idGenerator()}"
                val block = runCatching {
                    json.decodeFromJsonElement(ContentBlock.serializer(), parsed.contentBlock)
                }.getOrNull()
                blockBuffer[parsed.index] = ContentBlockState(
                    id = blockId,
                    block = block
                )
                events += ContentStartedEvent(
                    provider = AiAgentProvider.CLAUDE,
                    id = blockId,
                    contentType = block?.let { it::class.simpleName?.lowercase() } ?: "unknown"
                )
            }

            is ContentBlockDeltaEvent -> {
                val state = blockBuffer[parsed.index] ?: return events
                val delta = extractDelta(parsed.delta) ?: return events
                when (delta) {
                    is TextDeltaPayload -> state.append(delta.text)
                    is ThinkingDeltaPayload -> state.append(delta.thinking)
                    is ToolDeltaPayload -> state.append(delta.partialJson)
                    is CommandDeltaPayload -> state.append(delta.output)
                }
                events += ContentDeltaEvent(
                    provider = AiAgentProvider.CLAUDE,
                    id = state.id,
                    delta = delta
                )
            }

            is ContentBlockStopEvent -> {
                val state = blockBuffer.remove(parsed.index) ?: return events
                events += ContentCompletedEvent(
                    provider = AiAgentProvider.CLAUDE,
                    id = state.id,
                    content = buildContentBlock(state)
                )
            }

            is MessageDeltaEvent -> {
                val usage = parsed.usage?.let { toUnifiedUsage(it) }
                if (usage != null) {
                    events += TurnCompletedEvent(
                        provider = AiAgentProvider.CLAUDE,
                        usage = usage
                    )
                }
            }

            is MessageStopEvent -> {
                // no-op; completion will be emitted by ResultMessage
            }
        }

        return events
    }

    private fun convertContentBlock(block: ContentBlock): UnifiedContentBlock = when (block) {
        is TextBlock -> TextContent(block.text)
        is ThinkingBlock -> ThinkingContent(block.thinking, block.signature)
        is ToolUseBlock -> ToolUseContent(
            id = block.id,
            name = block.name,
            input = block.input
        )
        is ToolResultBlock -> ToolResultContent(
            toolUseId = block.toolUseId,
            content = block.content,
            isError = block.isError == true
        )
        else -> TextContent(block.toString())
    }

    private fun buildContentBlock(state: ContentBlockState): UnifiedContentBlock {
        val accumulated = state.buffer.toString()
        return when (val block = state.block) {
            is TextBlock -> TextContent(if (accumulated.isNotEmpty()) accumulated else block.text)
            is ThinkingBlock -> ThinkingContent(
                thinking = if (accumulated.isNotEmpty()) accumulated else block.thinking,
                signature = block.signature
            )
            is ToolUseBlock -> ToolUseContent(
                id = block.id,
                name = block.name,
                input = runCatching { json.parseToJsonElement(accumulated) }.getOrNull() ?: block.input,
                status = ContentStatus.COMPLETED
            )
            is ToolResultBlock -> ToolResultContent(
                toolUseId = block.toolUseId,
                content = block.content ?: runCatching { json.parseToJsonElement(accumulated) }.getOrNull(),
                isError = block.isError == true
            )
            else -> TextContent(accumulated)
        }
    }

    private fun extractMessageId(element: JsonElement): String? {
        val messageObj = element.jsonObject
        return messageObj["id"]?.jsonPrimitive?.contentOrNull
            ?: messageObj["message_id"]?.jsonPrimitive?.contentOrNull
            ?: messageObj["uuid"]?.jsonPrimitive?.contentOrNull
    }

    private fun extractDelta(delta: JsonElement): ContentDeltaPayload? {
        val type = delta.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: return null
        return when (type) {
            "text_delta" -> {
                val parsed = json.decodeFromJsonElement(TextDelta.serializer(), delta)
                TextDeltaPayload(parsed.text)
            }

            "thinking_delta" -> {
                val parsed = json.decodeFromJsonElement(ThinkingDelta.serializer(), delta)
                ThinkingDeltaPayload(parsed.thinking)
            }

            "input_json_delta" -> {
                val parsed = json.decodeFromJsonElement(InputJsonDelta.serializer(), delta)
                ToolDeltaPayload(parsed.partialJson)
            }

            else -> null
        }
    }

    private fun toUnifiedUsage(element: JsonElement): UnifiedUsage? {
        return runCatching {
            val usage = json.decodeFromJsonElement(TokenUsage.serializer(), element)
            UnifiedUsage(
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                cachedInputTokens = usage.cacheReadInputTokens ?: usage.cacheCreationInputTokens,
                provider = AiAgentProvider.CLAUDE,
                raw = element
            )
        }.getOrNull()
    }

    private data class ContentBlockState(
        val id: String,
        val block: ContentBlock?,
        val buffer: StringBuilder = StringBuilder()
    ) {
        fun append(text: String) {
            buffer.append(text)
        }
    }

    private fun parseInitialBlocks(messageElement: JsonElement): List<ContentBlockState> {
        val contentArray = messageElement.jsonObject["content"]?.jsonArray ?: return emptyList()
        val messageId = extractMessageId(messageElement) ?: "msg"
        return contentArray.mapIndexedNotNull { index, element ->
            val block = runCatching { json.decodeFromJsonElement(ContentBlock.serializer(), element) }.getOrNull()
            val buffer = StringBuilder()
            when (block) {
                is TextBlock -> buffer.append(block.text)
                is ThinkingBlock -> buffer.append(block.thinking)
                else -> {}
            }
            ContentBlockState(
                id = "$messageId-$index-${idGenerator()}",
                block = block,
                buffer = buffer
            )
        }
    }

    private fun buildInitialDelta(state: ContentBlockState): ContentDeltaPayload? {
        return when (val block = state.block) {
            is TextBlock -> if (block.text.isNotEmpty()) TextDeltaPayload(block.text) else null
            is ThinkingBlock -> if (block.thinking.isNotEmpty()) ThinkingDeltaPayload(block.thinking) else null
            else -> null
        }
    }
}
