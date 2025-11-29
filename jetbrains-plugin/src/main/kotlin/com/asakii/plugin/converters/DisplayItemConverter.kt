package com.asakii.plugin.converters

import com.asakii.plugin.types.*
import com.asakii.claude.agent.sdk.types.*
import kotlinx.serialization.json.*

/**
 * DisplayItem 转换器
 * 
 * 对应 frontend/src/utils/displayItemConverter.ts
 * 将后端的 Message 转换为前端的 DisplayItem
 */
object DisplayItemConverter {
    
    /**
     * 从 ToolUseBlock 创建 ToolCall
     * 
     * @param block 工具使用块
     * @param pendingToolCalls 待处理的工具调用 Map（用于查找已存在的工具调用）
     * @returns ToolCallItem 对象
     */
    fun createToolCall(
        block: ToolUseBlock,
        pendingToolCalls: MutableMap<String, ToolCallItem>
    ): ToolCallItem {
        // 检查是否已存在（用于更新状态）
        val existing = pendingToolCalls[block.id]
        if (existing != null) {
            // 同步更新已存在对象的 input
            // 因为 stream event 中 input_json_delta 会逐步更新 block.input
            return existing
        }
        
        val toolType = ToolConstants.TOOL_NAME_TO_TYPE[block.name] ?: block.name
        val timestamp = System.currentTimeMillis()
        
        // 将 JsonElement 转换为 Map
        val inputMap = jsonElementToMap(block.input)
        
        // 根据工具类型创建具体的 ToolCall
        val toolCall: ToolCallItem = when (toolType) {
            ToolConstants.READ -> ReadToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.WRITE -> WriteToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.EDIT -> EditToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.MULTI_EDIT -> MultiEditToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.TODO_WRITE -> TodoWriteToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.BASH -> BashToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.GREP -> GrepToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.GLOB -> GlobToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.WEB_SEARCH -> WebSearchToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.WEB_FETCH -> WebFetchToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.TASK -> TaskToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.NOTEBOOK_EDIT -> NotebookEditToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.BASH_OUTPUT -> BashOutputToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.KILL_SHELL -> KillShellToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.EXIT_PLAN_MODE -> ExitPlanModeToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.ASK_USER_QUESTION -> AskUserQuestionToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.SKILL -> SkillToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.SLASH_COMMAND -> SlashCommandToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.LIST_MCP_RESOURCES -> ListMcpResourcesToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            ToolConstants.READ_MCP_RESOURCE -> ReadMcpResourceToolCall(
                id = block.id,
                timestamp = timestamp,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
            else -> GenericToolCall(
                id = block.id,
                timestamp = timestamp,
                toolType = toolType,
                status = ToolCallStatus.RUNNING,
                startTime = timestamp,
                input = inputMap
            )
        }
        
        // 添加到 pendingToolCalls
        pendingToolCalls[block.id] = toolCall
        
        return toolCall
    }
    
    /**
     * 更新工具调用结果
     *
     * @param toolCall 工具调用对象
     * @param resultBlock 工具结果块
     */
    @Suppress("KotlinConstantConditions")
    fun updateToolCallResult(toolCall: ToolCallItem, resultBlock: ToolResultBlock): ToolCallItem {
        // 更新状态
        val newStatus = if (resultBlock.isError == true) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS
        val newEndTime = System.currentTimeMillis()

        // 解析结果（content 类型在运行时可能变化，保留分支处理）
        val content = resultBlock.content
        val newResult: ToolResult = if (resultBlock.isError == true) {
            ToolResult.Error(
                error = when (content) {
                    is String -> content
                    is JsonElement -> content.toString()
                    else -> content?.toString() ?: "Unknown error"
                }
            )
        } else {
            ToolResult.Success(
                output = when (content) {
                    is String -> content
                    is JsonElement -> content.toString()
                    else -> content?.toString() ?: ""
                }
            )
        }
        
        // 创建更新后的对象（Kotlin 数据类是不可变的）
        return when (toolCall) {
            is ReadToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is WriteToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is EditToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is MultiEditToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is TodoWriteToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is BashToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is GrepToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is GlobToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is WebSearchToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is WebFetchToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is TaskToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is NotebookEditToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is BashOutputToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is KillShellToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is ExitPlanModeToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is AskUserQuestionToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is SkillToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is SlashCommandToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is ListMcpResourcesToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is ReadMcpResourceToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
            is GenericToolCall -> toolCall.copy(status = newStatus, endTime = newEndTime, result = newResult)
        }
    }
    
    /**
     * 将 Message 数组转换为 DisplayItem 数组（初始化用）
     *
     * @param messages 原始消息数组
     * @param pendingToolCalls 待处理的工具调用 Map
     * @returns DisplayItem 数组
     */
    fun convertToDisplayItems(
        messages: List<com.asakii.claude.agent.sdk.types.Message>,
        pendingToolCalls: MutableMap<String, ToolCallItem>
    ): List<DisplayItem> {
        val displayItems = mutableListOf<DisplayItem>()
        
        for ((messageIdx, message) in messages.withIndex()) {
            when (message) {
                is UserMessage -> {
                    // 用户消息 - content 是 JsonElement
                    val content = parseUserMessageContent(message.content)
                    
                    if (content.isNotBlank()) {
                        val userMessageItem = UserMessageItem(
                            id = generateMessageId(message, messageIdx),
                            content = content,
                            timestamp = System.currentTimeMillis()
                        )
                        displayItems.add(userMessageItem)
                    }
                }
                
                is AssistantMessage -> {
                    // AI 助手消息 - 按顺序处理 content 块
                    // 收集所有文本块的索引，用于标记最后一个文本块
                    val textBlockIndices = message.content
                        .mapIndexedNotNull { idx, block -> if (block is TextBlock && block.text.trim().isNotEmpty()) idx else null }
                    val lastTextBlockIndex = textBlockIndices.lastOrNull() ?: -1
                    
                    for (blockIdx in message.content.indices) {
                        val block = message.content[blockIdx]
                        
                        if (block is TextBlock && block.text.trim().isNotEmpty()) {
                            // 文本块 -> AssistantText
                            val isLastTextBlock = blockIdx == lastTextBlockIndex
                            
                            // 构建统计信息（仅最后一个文本块有）
                            var stats: RequestStats? = null
                            if (isLastTextBlock) {
                                val usage = message.tokenUsage
                                if (usage != null) {
                                    // 查找最近的用户消息时间戳
                                    var lastUserTimestamp = 0L
                                    for (i in messageIdx - 1 downTo 0) {
                                        if (messages[i] is UserMessage) {
                                            lastUserTimestamp = System.currentTimeMillis() // 简化处理
                                            break
                                        }
                                    }
                                    val requestDuration = if (lastUserTimestamp > 0) {
                                        System.currentTimeMillis() - lastUserTimestamp
                                    } else {
                                        0L
                                    }
                                    
                                    stats = RequestStats(
                                        requestDuration = requestDuration,
                                        inputTokens = usage.inputTokens,
                                        outputTokens = usage.outputTokens
                                    )
                                }
                            }
                            
                            val assistantText = AssistantTextItem(
                                id = "${generateMessageId(message, messageIdx)}-text-${displayItems.size}",
                                content = block.text,
                                timestamp = System.currentTimeMillis(),
                                isLastInMessage = isLastTextBlock,
                                stats = stats
                            )
                            displayItems.add(assistantText)
                        } else if (block is ToolUseBlock) {
                            // 工具调用块 -> ToolCall
                            val toolCall = createToolCall(block, pendingToolCalls)
                            displayItems.add(toolCall)
                        }
                    }
                }
                
                is SystemMessage -> {
                    // 系统消息
                    val textContent = message.data.toString()
                    if (textContent.isNotBlank()) {
                        val systemMessageItem = SystemMessageItem(
                            id = generateMessageId(message, messageIdx),
                            content = textContent,
                            level = when (message.subtype) {
                                "error" -> SystemMessageLevel.ERROR
                                "warning" -> SystemMessageLevel.WARNING
                                else -> SystemMessageLevel.INFO
                            },
                            timestamp = System.currentTimeMillis()
                        )
                        displayItems.add(systemMessageItem)
                    }
                }
                
                else -> {
                    // 其他消息类型（忽略或记录日志）
                }
            }
        }
        
        return displayItems
    }
    
    /**
     * 将单个 Message 转换为 DisplayItem 数组（增量更新用）
     *
     * @param message 单个消息
     * @param pendingToolCalls 待处理的工具调用 Map
     * @returns DisplayItem 数组
     */
    fun convertMessageToDisplayItems(
        message: com.asakii.claude.agent.sdk.types.Message,
        pendingToolCalls: MutableMap<String, ToolCallItem>
    ): List<DisplayItem> {
        val displayItems = mutableListOf<DisplayItem>()
        
        when (message) {
            is UserMessage -> {
                // 用户消息 - content 是 JsonElement
                val content = parseUserMessageContent(message.content)
                
                if (content.isNotBlank()) {
                    val userMessageItem = UserMessageItem(
                        id = generateMessageId(message, 0),
                        content = content,
                        timestamp = System.currentTimeMillis()
                    )
                    displayItems.add(userMessageItem)
                }
            }
            
            is AssistantMessage -> {
                // AI 助手消息
                val textBlockIndices = message.content
                    .mapIndexedNotNull { idx, block -> if (block is TextBlock && block.text.trim().isNotEmpty()) idx else null }
                val lastTextBlockIndex = textBlockIndices.lastOrNull() ?: -1
                
                for (blockIdx in message.content.indices) {
                    val block = message.content[blockIdx]
                    
                    if (block is TextBlock && block.text.trim().isNotEmpty()) {
                        val isLastTextBlock = blockIdx == lastTextBlockIndex
                        var stats: RequestStats? = null
                        if (isLastTextBlock) {
                            val usage = message.tokenUsage
                            if (usage != null) {
                                stats = RequestStats(
                                    requestDuration = 0,
                                    inputTokens = usage.inputTokens,
                                    outputTokens = usage.outputTokens
                                )
                            }
                        }
                        
                        val assistantText = AssistantTextItem(
                            id = "${generateMessageId(message, 0)}-text-$blockIdx",
                            content = block.text,
                            timestamp = System.currentTimeMillis(),
                            isLastInMessage = isLastTextBlock,
                            stats = stats
                        )
                        displayItems.add(assistantText)
                    } else if (block is ToolUseBlock) {
                        val toolCall = createToolCall(block, pendingToolCalls)
                        displayItems.add(toolCall)
                    }
                }
            }
            
            else -> {
                // 其他消息类型
            }
        }
        
        return displayItems
    }
    
    /**
     * 生成消息 ID
     */
    private fun generateMessageId(message: com.asakii.claude.agent.sdk.types.Message, index: Int): String {
        return when (message) {
            is UserMessage -> message.sessionId
            is AssistantMessage -> "assistant-$index-${System.currentTimeMillis()}"
            is SystemMessage -> "system-${message.subtype}-$index"
            else -> "message-$index-${System.currentTimeMillis()}"
        }
    }
    
    /**
     * 将 JsonElement 转换为 Map<String, Any?>
     */
    private fun jsonElementToMap(element: JsonElement): Map<String, Any?> {
        if (element !is kotlinx.serialization.json.JsonObject) {
            return emptyMap()
        }
        
        return element.entries.associate { (key, value) ->
            key to jsonElementToAny(value)
        }
    }
    
    /**
     * 将 JsonElement 转换为 Any?
     */
    private fun jsonElementToAny(element: JsonElement): Any? {
        return when {
            element is kotlinx.serialization.json.JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.content == "true" -> true
                    element.content == "false" -> false
                    element.content.toIntOrNull() != null -> element.content.toInt()
                    element.content.toLongOrNull() != null -> element.content.toLong()
                    element.content.toDoubleOrNull() != null -> element.content.toDouble()
                    else -> element.content
                }
            }
            element is kotlinx.serialization.json.JsonArray -> {
                element.map { jsonElementToAny(it) }
            }
            element is kotlinx.serialization.json.JsonObject -> {
                jsonElementToMap(element)
            }
            else -> null
        }
    }
    
    /**
     * 解析 UserMessage.content (JsonElement) 为纯文本
     */
    private fun parseUserMessageContent(content: JsonElement): String {
        return when (content) {
            is JsonPrimitive -> {
                // 简单字符串
                content.content
            }
            is JsonArray -> {
                // ContentBlock 数组
                content.mapNotNull { element ->
                    if (element is JsonObject) {
                        val type = element["type"]?.jsonPrimitive?.content
                        when (type) {
                            "text" -> element["text"]?.jsonPrimitive?.content
                            else -> null
                        }
                    } else {
                        null
                    }
                }.joinToString("\n")
            }
            else -> ""
        }
    }
}

