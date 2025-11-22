package com.claudecodeplus.ui.services

import com.claudecodeplus.core.logging.*
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.logging.Logger

/**
 * SDK 消息转换器
 *
 * 负责在 claude-code-sdk 消息类型和 UI 层的 EnhancedMessage 之间进行双向转换。
 *
 * 主要功能：
 * - SDK Message → EnhancedMessage：解析 SDK 返回的消息并转换为 UI 可用格式
 * - EnhancedMessage → SDK Message：构建发送给 SDK 的消息
 * - ContentBlock 解析：处理文本、工具调用、思考等不同类型的内容块
 * - Token 统计转换：映射 Token 使用信息
 */
object SdkMessageConverter {
    private val logger = Logger.getLogger(SdkMessageConverter::class.java.name)

    /**
     * 将 SDK 消息转换为 EnhancedMessage
     */
    fun fromSdkMessage(sdkMessage: Message): EnhancedMessage {
        logger.info("🔄 [SdkMessageConverter] 转换SDK消息: ${sdkMessage::class.simpleName}")
        return when (sdkMessage) {
            is UserMessage -> convertUserMessage(sdkMessage)
            is AssistantMessage -> convertAssistantMessage(sdkMessage, null) // 使用统一的方法，传递null作为sessionObject
            is SystemMessage -> convertSystemMessage(sdkMessage)
            is ResultMessage -> convertResultMessage(sdkMessage)
            is StreamEvent -> convertStreamEvent(sdkMessage)
        }
    }

    /**
     * 将 SDK 消息转换为 EnhancedMessage，支持跨消息工具调用关联
     */
    fun fromSdkMessage(sdkMessage: Message, sessionObject: SessionObject?): EnhancedMessage {
        logger.info("🔄 [SdkMessageConverter] 转换SDK消息（带会话上下文）: ${sdkMessage::class.simpleName}")
        return when (sdkMessage) {
            is UserMessage -> convertUserMessage(sdkMessage, sessionObject)  // 🔧 关键修复：传递sessionObject处理工具结果
            is AssistantMessage -> convertAssistantMessage(sdkMessage, sessionObject)
            is SystemMessage -> convertSystemMessage(sdkMessage)
            is ResultMessage -> convertResultMessage(sdkMessage)
            is StreamEvent -> convertStreamEvent(sdkMessage)
        }
    }

    /**
     * 将用户消息转换为 EnhancedMessage
     */
    private fun convertUserMessage(message: UserMessage): EnhancedMessage {
        return convertUserMessage(message, null)
    }

    /**
     * 将用户消息转换为 EnhancedMessage，支持工具结果处理
     */
    private fun convertUserMessage(message: UserMessage, sessionObject: SessionObject?): EnhancedMessage {
        val content = when (val contentElement = message.content) {
            is JsonPrimitive -> contentElement.content
            is JsonArray -> {
                // 处理复杂内容数组，可能包含工具结果
                val textParts = mutableListOf<String>()

                contentElement.jsonArray.forEach { element ->
                    when (element) {
                        is JsonPrimitive -> {
                            textParts.add(element.content)
                        }
                        is JsonObject -> {
                            val blockType = element.jsonObject["type"]?.jsonPrimitive?.content
                            when (blockType) {
                                "tool_result" -> {
                                    // 🎯 关键修复：处理User消息中的ToolResultBlock
                                    val toolUseId = element.jsonObject["tool_use_id"]?.jsonPrimitive?.content
                                    val resultContent = element.jsonObject["content"]
                                    val isError = element.jsonObject["is_error"]?.jsonPrimitive?.booleanOrNull

                                    if (toolUseId != null && sessionObject != null) {
                                        try {
                                            val hasError = isError == true
                                            val outputContent = resultContent.toString()

                                            val toolResult = if (hasError) {
                                                ToolResult.Failure(error = outputContent, details = null)
                                            } else {
                                                ToolResult.Success(
                                                    output = outputContent,
                                                    summary = if (outputContent.length > 100) "${outputContent.take(100)}..." else outputContent
                                                )
                                            }
                                            val status = if (hasError) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS

                                            sessionObject.updateToolCallStatus(toolUseId, status, toolResult)
                                            logD("[SdkMessageConverter] 🔧 处理User消息中的工具结果: $toolUseId -> $status")
                                        } catch (e: Exception) {
    //                                             logD("[SdkMessageConverter] ⚠️ 处理User消息工具结果失败: ${e.message}")
                                        }
                                    }

                                    // 不在用户消息中显示工具结果内容
                                }
                                "text" -> {
                                    textParts.add(element.jsonObject["text"]?.jsonPrimitive?.content ?: "")
                                }
                                else -> {
                                    // 其他类型的内容块，提取可能的文本
                                    element.jsonObject["text"]?.jsonPrimitive?.content?.let { text ->
                                        textParts.add(text)
                                    }
                                }
                            }
                        }
                        else -> textParts.add(element.toString())
                    }
                }

                textParts.joinToString("")
            }
            else -> contentElement.toString()
        }

        return EnhancedMessage.create(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            text = content,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.COMPLETE
        )
    }


    /**
     * 将助手消息转换为 EnhancedMessage，支持跨消息工具调用关联
     */
    private fun convertAssistantMessage(message: AssistantMessage, sessionObject: SessionObject?): EnhancedMessage {
        // 解析内容块
        val textSegments = mutableListOf<String>()
        val toolCalls = mutableListOf<ToolCall>()
        val orderedElements = mutableListOf<MessageTimelineItem>()

        message.content.forEach { contentBlock ->
            when (contentBlock) {
                is TextBlock -> {
                    val text = contentBlock.text
                    logD("[SdkMessageConverter] 📝 处理TextBlock，内容长度: ${text.length}")
                    logD("[SdkMessageConverter] 📝 TextBlock内容预览: ${text.take(200)}")

                    // 直接添加文本内容，不需要任何过滤
                    if (text.isNotBlank()) {
                        textSegments.add(text)
                        orderedElements.add(
                            MessageTimelineItem.ContentItem(
                                content = text,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
    //                     logD("[SdkMessageConverter] ✅ 添加文本到content: ${textContent.length} 字符")
                }
                is ImageBlock -> {
                    // 图片块暂时忽略，因为 AI 回复中不应该包含图片
                    logD("[SdkMessageConverter] 🖼️ 忽略ImageBlock（AI回复不应包含图片）")
                }
                is ThinkingBlock -> {
                    // 思考过程作为特殊的文本项添加
                    orderedElements.add(
                        MessageTimelineItem.ContentItem(
                            content = "思考: ${contentBlock.thinking}",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                is SpecificToolUse -> {
                    // 处理具体的工具类型
                    val toolCall = convertSpecificToolUse(contentBlock)
                    toolCalls.add(toolCall)
                    orderedElements.add(
                        MessageTimelineItem.ToolCallItem(
                            toolCall = toolCall,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    logD("[SdkMessageConverter] 🔧 记录工具调用: ${toolCall.name}")
                }
                is ToolUseBlock -> {
                    // 向后兼容：处理可能仍然存在的旧ToolUseBlock类型
    //                     logD("[SdkMessageConverter] ⚠️ 遇到旧的ToolUseBlock类型，建议检查MessageParser配置")
                    val toolCall = convertToolUseBlock(contentBlock)
                    toolCalls.add(toolCall)
                    orderedElements.add(
                        MessageTimelineItem.ToolCallItem(
                            toolCall = toolCall,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    logD("[SdkMessageConverter] 🔧 记录工具调用: ${toolCall.name}")
                }
                is ToolResultBlock -> {
                    // 处理工具结果：查找对应的工具调用并更新其结果
                    val targetToolCall = toolCalls.find { it.id == contentBlock.toolUseId }
                    if (targetToolCall != null) {
                        val hasError = contentBlock.isError == true
                        val outputContent = contentBlock.content.toString()

                        val updatedToolCall = targetToolCall.copy(
                            result = if (hasError) {
                                ToolResult.Failure(error = outputContent, details = null)
                            } else {
                                ToolResult.Success(
                                    output = outputContent,
                                    summary = outputContent.take(100)
                                )
                            },
                            status = if (hasError) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
                            endTime = System.currentTimeMillis()
                        )

                        val listIndex = toolCalls.indexOf(targetToolCall)
                        if (listIndex >= 0) {
                            toolCalls[listIndex] = updatedToolCall
                        }

                        val timelineIndex = orderedElements.indexOfLast { element ->
                            element is MessageTimelineItem.ToolCallItem && element.toolCall.id == targetToolCall.id
                        }
                        if (timelineIndex >= 0) {
                            val existingItem = orderedElements[timelineIndex] as MessageTimelineItem.ToolCallItem
                            orderedElements[timelineIndex] = existingItem.copy(toolCall = updatedToolCall)
                        }

                        if (sessionObject != null) {
                            try {
                                sessionObject.updateToolCallStatus(
                                    contentBlock.toolUseId,
                                    updatedToolCall.status,
                                    updatedToolCall.result
                                )
                                logD("[SdkMessageConverter] 🔧 已调用SessionObject.updateToolCallStatus: ${contentBlock.toolUseId} -> ${updatedToolCall.status}")
                            } catch (e: Exception) {
//                             logD("[SdkMessageConverter] ⚠️ 调用SessionObject.updateToolCallStatus失败: ${e.message}")
                            }
                        }
                    } else {
                        logD("[SdkMessageConverter] ⚠️ 未找到对应的工具调用: ${contentBlock.toolUseId}")
                    }

                    logD("[SdkMessageConverter] 🔧 记录工具结果")
                }
            }
        }

        // 转换 Token 使用信息
        val tokenUsage = message.tokenUsage?.let { usage ->
            logD("🔍 [SdkMessageConverter] Token使用详情:")
    //             logD("  - inputTokens: ${usage.inputTokens}")
    //             logD("  - outputTokens: ${usage.outputTokens}")
    //             logD("  - cacheCreationInputTokens: ${usage.cacheCreationInputTokens}")
    //             logD("  - cacheReadInputTokens: ${usage.cacheReadInputTokens}")

            EnhancedMessage.TokenUsage(
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                cacheCreationTokens = usage.cacheCreationInputTokens ?: 0,
                cacheReadTokens = usage.cacheReadInputTokens ?: 0
            )
        }

        // 直接使用原始内容，不做任何过滤
        val finalContent = textSegments.joinToString(separator = "")

        logD("[SdkMessageConverter] 📊 最终消息内容长度: ${finalContent.length}")
        if (finalContent.isNotEmpty()) {
            logD("[SdkMessageConverter] 📊 最终消息内容预览: ${finalContent.take(200)}")
        } else {
    //             logD("[SdkMessageConverter] ⚠️ 最终消息内容为空！")
        }

        return EnhancedMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.ASSISTANT,
            timestamp = System.currentTimeMillis(),
            orderedElements = orderedElements,
            tokenUsage = tokenUsage,
            status = MessageStatus.COMPLETE
        )
    }

    /**
     * 将系统消息转换为 EnhancedMessage
     */
    private fun convertSystemMessage(message: SystemMessage): EnhancedMessage {
        val content = when (message.subtype) {
            "init" -> formatModelSystemMessage("Initialized model", message)
            "model_changed" -> formatModelSystemMessage("Set model to", message)
            "session_started" -> "会话已开始"
            else -> formatGenericSystemMessage(message)
        }

        return EnhancedMessage.create(
            id = UUID.randomUUID().toString(),
            role = MessageRole.SYSTEM,
            text = content,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.COMPLETE
        )
    }

    private fun formatGenericSystemMessage(message: SystemMessage): String {
        val dataObject = message.data.jsonObject
        val modelId = dataObject["model"]?.jsonPrimitive?.contentOrNull
        val displayNameOverride = dataObject["model_display_name"]?.jsonPrimitive?.contentOrNull
        return if (modelId.isNullOrBlank()) {
            "系统消息: ${message.subtype}"
        } else {
            val alias = AiModel.fromIdentifier(modelId) ?: AiModel.fromIdentifier(displayNameOverride)
            val aliasLabel = displayNameOverride ?: alias?.displayName ?: alias?.shortName ?: modelId
            "系统消息(${message.subtype}): $aliasLabel ($modelId)"
        }
    }

    private fun formatModelSystemMessage(prefix: String, message: SystemMessage): String {
        val dataObject = message.data.jsonObject
        val modelId = dataObject["model"]?.jsonPrimitive?.contentOrNull
        val displayNameOverride = dataObject["model_display_name"]?.jsonPrimitive?.contentOrNull
        val alias = AiModel.fromIdentifier(modelId) ?: AiModel.fromIdentifier(displayNameOverride)
        val aliasLabel = displayNameOverride ?: alias?.displayName ?: alias?.shortName
        return when {
            modelId != null && aliasLabel != null -> "$prefix $aliasLabel ($modelId)"
            modelId != null -> "$prefix $modelId"
            aliasLabel != null -> "$prefix $aliasLabel"
            else -> "$prefix"
        }
    }

    /**
     * 将结果消息转换为 EnhancedMessage
     * 结果消息通常包含会话统计信息，不直接显示在 UI 中
     */
    private fun convertResultMessage(message: ResultMessage): EnhancedMessage {
        val content = buildString {
            append("会话结束")
            message.result?.let { append(": $it") }
            if (message.isError) {
                append(" (错误)")
            }
        }

        return EnhancedMessage.create(
            id = UUID.randomUUID().toString(),
            role = MessageRole.SYSTEM,
            text = content,
            timestamp = System.currentTimeMillis(),
            status = if (message.isError) MessageStatus.FAILED else MessageStatus.COMPLETE,
            isError = message.isError
        )
    }

    /**
     * 转换流事件为 EnhancedMessage
     */
    private fun convertStreamEvent(event: StreamEvent): EnhancedMessage {
        return EnhancedMessage.create(
            id = event.uuid,
            role = MessageRole.SYSTEM,
            text = "流事件: ${event.uuid}",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.STREAMING
        )
    }

    /**
     * 转换具体工具使用类型为 ToolCall
     * 🎯 核心改进：利用具体工具类型的强类型属性，无需手动解析JSON参数
     */
    private fun convertSpecificToolUse(specificTool: SpecificToolUse): ToolCall {
        // 🎯 关键改进：直接使用具体工具类型的强类型参数
        val parameters = specificTool.getTypedParameters()
        val toolName = specificTool.toolType.toolName

        // 🔍 TodoWrite 工具专用调试日志
        if (specificTool is TodoWriteToolUse) {
            logD("[SdkMessageConverter] 🔍 TodoWrite工具（强类型）:")
    //             logD("[SdkMessageConverter] - 工具类型: ${specificTool::class.simpleName}")
    //             logD("[SdkMessageConverter] - 任务数量: ${specificTool.todos.size}")
    //             logD("[SdkMessageConverter] - 强类型参数: $parameters")
            specificTool.todos.forEachIndexed { index, todo ->
    //                 logD("[SdkMessageConverter] - Todo[$index]: ${todo.content} (${todo.status})")
            }
        }

        // 🎯 演示instanceof检查的强大功能
        when (specificTool) {
            is BashToolUse -> {
                logD("[SdkMessageConverter] 🔧 Bash工具: ${specificTool.command}")
                specificTool.description?.let { logD("  描述: $it") }
            }
            is EditToolUse -> {
    //                 logD("[SdkMessageConverter] ✏️ 编辑工具: ${specificTool.filePath}")
    //                 logD("  替换: ${specificTool.oldString} → ${specificTool.newString}")
            }
            is ReadToolUse -> {
                logD("[SdkMessageConverter] 📖 读取工具: ${specificTool.filePath}")
                specificTool.offset?.let { logD("  偏移: $it") }
                specificTool.limit?.let { logD("  限制: $it") }
            }
            is McpToolUse -> {
                logD("[SdkMessageConverter] 🔌 MCP工具: ${specificTool.serverName}.${specificTool.functionName}")
    //                 logD("  参数: ${specificTool.parameters}")
            }
            is TodoWriteToolUse -> {
                // 已在上面专门处理
            }
            else -> {
                logD("[SdkMessageConverter] 🔧 其他工具: ${specificTool::class.simpleName}")
            }
        }


        // 🎯 新架构：使用 Mapper 创建 ViewModel
        val viewModel = com.claudecodeplus.ui.mappers.ToolCallMapper.fromSdkToolUse(
            id = specificTool.id,
            name = toolName,
            sdkTool = specificTool,
            status = ToolCallStatus.RUNNING,
            result = null,
            startTime = System.currentTimeMillis(),
            endTime = null
        )

        return ToolCall(
            id = specificTool.id,
            name = toolName,
            viewModel = viewModel,
            status = ToolCallStatus.RUNNING,
            result = null,
            startTime = System.currentTimeMillis()
        )
    }

    /**
     * 转换工具使用块为 ToolCall (向后兼容)
     */
    private fun convertToolUseBlock(toolUse: ToolUseBlock): ToolCall {
        val parameters = parseToolParameters(toolUse.input)

        // 创建通用的 ViewModel
        val toolDetail = com.claudecodeplus.ui.viewmodels.tool.GenericToolDetail(
            toolType = com.claudecodeplus.ui.viewmodels.tool.UiToolType.UNKNOWN,
            parameters = parameters
        )
        val viewModel = com.claudecodeplus.ui.viewmodels.tool.ToolCallViewModel(
            id = toolUse.id,
            name = toolUse.name,
            toolDetail = toolDetail,
            status = ToolCallStatus.RUNNING,
            result = null,
            startTime = System.currentTimeMillis(),
            endTime = null
        )

        return ToolCall(
            id = toolUse.id,
            name = toolUse.name,
            viewModel = viewModel,
            status = ToolCallStatus.RUNNING,
            result = null,
            startTime = System.currentTimeMillis()
        )
    }

    /**
     * 解析工具参数 - 支持嵌套的JSON结构
     */
    private fun parseToolParameters(input: JsonElement): Map<String, Any> {
        return try {
            when (input) {
                is JsonObject -> {
                    input.jsonObject.mapValues { (_, value) ->
                        parseJsonElement(value)
                    }
                }
                else -> mapOf("input" to parseJsonElement(input))
            }
        } catch (e: Exception) {
            mapOf("input" to input.toString())
        }
    }

    /**
     * 递归解析JsonElement，保持嵌套结构
     */
    private fun parseJsonElement(element: JsonElement): Any {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is JsonArray -> {
                // 递归解析数组中的每个元素
                element.map { parseJsonElement(it) }
            }
            is JsonObject -> {
                // 递归解析对象中的每个字段
                element.mapValues { (_, value) ->
                    parseJsonElement(value)
                }
            }
        }
    }

    /**
     * 根据工具名称和参数创建工具实例
     * 这里创建简化的工具表示，主要用于 UI 显示
     */
    private fun createToolInstance(toolName: String, input: JsonElement): Any? {
        // 创建一个简单的工具表示对象，主要用于 UI 显示
        // 实际的工具执行由 SDK 内部处理
        return null // 暂时返回 null，主要依赖工具名称进行显示
    }

    /**
     * 将 EnhancedMessage 转换为用户消息（用于发送）
     */
    fun toSdkUserMessage(message: EnhancedMessage, sessionId: String = "default"): UserMessage {
        return UserMessage(
            content = JsonPrimitive(message.content),
            sessionId = sessionId
        )
    }


    /**
     * 从 ClaudeAgentOptions 构建配置
     */
    fun buildClaudeAgentOptions(
        sessionObject: SessionObject,
        project: Project? = null
    ): ClaudeAgentOptions {
        return ClaudeAgentOptions(
            model = sessionObject.selectedModel?.cliName,
            permissionMode = sessionObject.selectedPermissionMode?.let { mode ->
                when (mode) {
                    com.claudecodeplus.ui.models.PermissionMode.DEFAULT -> com.claudecodeplus.sdk.types.PermissionMode.DEFAULT
                    com.claudecodeplus.ui.models.PermissionMode.ACCEPT -> com.claudecodeplus.sdk.types.PermissionMode.ACCEPT_EDITS
                    com.claudecodeplus.ui.models.PermissionMode.BYPASS -> com.claudecodeplus.sdk.types.PermissionMode.BYPASS_PERMISSIONS
                    com.claudecodeplus.ui.models.PermissionMode.PLAN -> com.claudecodeplus.sdk.types.PermissionMode.PLAN
                }
            },
            cwd = project?.let { java.nio.file.Paths.get(it.path) },
            // 可以根据需要添加更多配置
            allowedTools = listOf("Read", "Write", "Edit", "Bash", "TodoWrite", "Glob", "Grep"),
        )
    }
}

// MessageTimelineItem 在 UnifiedModels.kt 中已定义，这里移除重复定义
