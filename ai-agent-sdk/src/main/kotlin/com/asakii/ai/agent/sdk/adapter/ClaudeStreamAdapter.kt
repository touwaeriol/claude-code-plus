package com.asakii.ai.agent.sdk.adapter

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.model.*
import com.asakii.claude.agent.sdk.types.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 将 Claude Agent SDK 的消息流转换为统一的 NormalizedStreamEvent。
 */
class ClaudeStreamAdapter(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }
) {
    private val blockBuffer = mutableMapOf<Int, ContentBlockState>()

    fun convert(message: Message): List<NormalizedStreamEvent> = when (message) {
        is StreamEvent -> handleStreamEvent(message)
        is AssistantMessage -> handleAssistantMessage(message)
        is ResultMessage -> handleResultMessage(message)
        is UserMessage -> handleUserMessage(message)
        is StatusSystemMessage -> handleStatusSystemMessage(message)
        is CompactBoundaryMessage -> handleCompactBoundaryMessage(message)
        is SystemInitMessage -> handleSystemInitMessage(message)
        else -> emptyList()
    }

    private fun handleStatusSystemMessage(message: StatusSystemMessage): List<NormalizedStreamEvent> {
        return listOf(
            StatusSystemEvent(
                provider = AiAgentProvider.CLAUDE,
                status = message.status,
                sessionId = message.sessionId
            )
        )
    }

    private fun handleCompactBoundaryMessage(message: CompactBoundaryMessage): List<NormalizedStreamEvent> {
        return listOf(
            CompactBoundaryEvent(
                provider = AiAgentProvider.CLAUDE,
                sessionId = message.sessionId,
                trigger = message.compactMetadata?.trigger,
                preTokens = message.compactMetadata?.preTokens
            )
        )
    }

    private fun handleSystemInitMessage(message: SystemInitMessage): List<NormalizedStreamEvent> {
        return listOf(
            SystemInitEvent(
                provider = AiAgentProvider.CLAUDE,
                sessionId = message.sessionId,
                cwd = message.cwd,
                model = message.model,
                permissionMode = message.permissionMode,
                apiKeySource = message.apiKeySource,
                tools = message.tools,
                mcpServers = message.mcpServers?.map { McpServerInfoModel(it.name, it.status) }
            )
        )
    }

    private fun handleAssistantMessage(message: AssistantMessage): List<NormalizedStreamEvent> {
        println("🔍 [ClaudeStreamAdapter] handleAssistantMessage: content.size=${message.content.size}")
        message.content.forEachIndexed { idx, block ->
            println("🔍 [ClaudeStreamAdapter] content[$idx]: type=${block::class.simpleName}, ${if (block is SpecificToolUse) "input=${block.input.toString().take(200)}" else ""}")
        }
        val contentBlocks = message.content.map { convertContentBlock(it) }
        println("🔍 [ClaudeStreamAdapter] after convert: ${contentBlocks.map { "${it::class.simpleName}:${if (it is ToolUseContent) "input=${it.input?.toString()?.take(100)}" else ""}" }}")
        val usage = message.tokenUsage?.let {
            UnifiedUsage(
                inputTokens = it.inputTokens,
                outputTokens = it.outputTokens,
                cachedInputTokens = it.cacheReadInputTokens ?: it.cacheCreationInputTokens,
                provider = AiAgentProvider.CLAUDE,
                raw = json.encodeToJsonElement(TokenUsage.serializer(), it)
            )
        }
        return listOf(
            AssistantMessageEvent(
                provider = AiAgentProvider.CLAUDE,
                id = message.id,
                content = contentBlocks,
                model = message.model,
                tokenUsage = usage,
                parentToolUseId = message.parentToolUseId,
                uuid = message.uuid
            )
        )
    }

    private fun handleResultMessage(message: ResultMessage): List<NormalizedStreamEvent> {
        // ResultMessage 是整个查询的最终汇总消息
        // 不需要再发送 TurnCompletedEvent，因为 MessageDeltaEvent 已经发送了
        // 只有在错误情况下才需要发送 TurnFailedEvent
        val summary = ResultSummaryEvent(
            provider = AiAgentProvider.CLAUDE,
            subtype = message.subtype,  // 保留原始 subtype（如 "error_during_execution"）
            durationMs = message.durationMs,
            durationApiMs = message.durationApiMs,
            isError = message.isError,
            numTurns = message.numTurns,
            sessionId = message.sessionId,
            totalCostUsd = message.totalCostUsd,
            usage = message.usage,
            result = message.result
        )

        return if (message.isError) {
            // 错误情况下，发送 TurnFailedEvent
            val failedEvent = TurnFailedEvent(
                provider = AiAgentProvider.CLAUDE,
                error = message.result ?: "Claude CLI 返回错误"
            )
            listOf(summary, failedEvent)
        } else {
            // 正常情况下，只发送 ResultSummaryEvent
            // TurnCompletedEvent 已经在 MessageDeltaEvent 时发送了
            listOf(summary)
        }
    }

    private fun handleUserMessage(message: UserMessage): List<NormalizedStreamEvent> {
        val contentBlocks = parseUserContent(message.content)
        if (contentBlocks.isEmpty()) {
            return emptyList()
        }
        return listOf(
            UserMessageEvent(
                provider = AiAgentProvider.CLAUDE,
                content = contentBlocks,
                isReplay = message.isReplay,
                parentToolUseId = message.parentToolUseId,
                uuid = message.uuid
            )
        )
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
                    // 解析 content 作为 initialContent，但不生成 ContentStartedEvent/ContentDeltaEvent
                    // 后续的 content_block_start 和 content_block_delta 事件会自己触发处理
                    blockBuffer.clear()
                    val initialBlocks = parseInitialBlocks(parsed.message)
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
                val block = parsed.contentBlock
                val toolName = (block as? ToolUseBlock)?.name

                blockBuffer[parsed.index] = ContentBlockState(
                    block = block
                )
                events += ContentStartedEvent(
                    provider = AiAgentProvider.CLAUDE,
                    index = parsed.index,
                    contentType = block::class.simpleName?.lowercase() ?: "unknown",
                    toolName = toolName,
                    content = convertContentBlock(block),
                    parentToolUseId = message.parentToolUseId
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
                    index = parsed.index,
                    delta = delta,
                    parentToolUseId = message.parentToolUseId
                )
            }

            is ContentBlockStopEvent -> {
                val state = blockBuffer.remove(parsed.index) ?: return events
                events += ContentCompletedEvent(
                    provider = AiAgentProvider.CLAUDE,
                    index = parsed.index,
                    content = buildContentBlock(state),
                    parentToolUseId = message.parentToolUseId
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
        // SpecificToolUse 子类型（EditToolUse, ReadToolUse, TodoWriteToolUse 等）
        is SpecificToolUse -> {
            println("🔍 [convertContentBlock] SpecificToolUse: id=${block.id}, name=${block.name}, inputType=${block.input.javaClass.simpleName}, input=${block.input.toString().take(200)}")
            ToolUseContent(
                id = block.id,
                name = block.name,
                input = block.input
            )
        }
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
            // SpecificToolUse 子类型（EditToolUse, ReadToolUse, TodoWriteToolUse 等）
            is SpecificToolUse -> ToolUseContent(
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

    private fun parseUserContent(element: JsonElement): List<UnifiedContentBlock> {
        return when (element) {
            is JsonArray -> element.mapNotNull { item ->
                runCatching {
                    val block = json.decodeFromJsonElement(ContentBlock.serializer(), item)
                    convertContentBlock(block)
                }.getOrNull()
            }
            else -> {
                val text = element.jsonPrimitive.contentOrNull ?: return emptyList()
                listOf(TextContent(text))
            }
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
        val block: ContentBlock?,
        val buffer: StringBuilder = StringBuilder()
    ) {
        fun append(text: String) {
            buffer.append(text)
        }
    }

    private fun parseInitialBlocks(messageElement: JsonElement): List<ContentBlockState> {
        val contentElement = messageElement.jsonObject["content"] ?: return emptyList()

        // content 可能是数组或字符串
        return when (contentElement) {
            is JsonArray -> contentElement.mapNotNull { element ->
                val block = runCatching { json.decodeFromJsonElement(ContentBlock.serializer(), element) }.getOrNull()
                val buffer = StringBuilder()
                when (block) {
                    is TextBlock -> buffer.append(block.text)
                    is ThinkingBlock -> buffer.append(block.thinking)
                    else -> {}
                }
                ContentBlockState(
                    block = block,
                    buffer = buffer
                )
            }
            else -> {
                // content 是字符串，转换为单个 TextBlock
                val text = contentElement.jsonPrimitive.contentOrNull ?: return emptyList()
                listOf(
                    ContentBlockState(
                        block = TextBlock(text),
                        buffer = StringBuilder(text)
                    )
                )
            }
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
