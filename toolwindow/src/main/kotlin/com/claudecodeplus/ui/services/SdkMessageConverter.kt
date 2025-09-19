package com.claudecodeplus.ui.services

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
                                            println("[SdkMessageConverter] 🔧 处理User消息中的工具结果: $toolUseId -> $status")
                                        } catch (e: Exception) {
                                            println("[SdkMessageConverter] ⚠️ 处理User消息工具结果失败: ${e.message}")
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

        return EnhancedMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.COMPLETE
        )
    }


    /**
     * 将助手消息转换为 EnhancedMessage，支持跨消息工具调用关联
     */
    private fun convertAssistantMessage(message: AssistantMessage, sessionObject: SessionObject?): EnhancedMessage {
        // 解析内容块
        val textContent = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()
        val orderedElements = mutableListOf<MessageTimelineItem>()

        message.content.forEach { contentBlock ->
            when (contentBlock) {
                is TextBlock -> {
                    // 过滤掉TodoWrite工具的标准结果文本
                    val text = contentBlock.text
                    val isTodoWriteResult = text.contains("Todos have been modified successfully") ||
                                           text.contains("todo list has been updated") ||
                                           text.contains("任务列表已更新")

                    if (!isTodoWriteResult) {
                        textContent.append(text)
                        orderedElements.add(
                            MessageTimelineItem.ContentItem(
                                content = text,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    } else {
                        println("[SdkMessageConverter] 🔇 过滤TodoWrite结果文本: ${text.take(50)}...")
                    }
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
                is ToolUseBlock -> {
                    val toolCall = convertToolUseBlock(contentBlock)
                    toolCalls.add(toolCall)
                    orderedElements.add(
                        MessageTimelineItem.ToolCallItem(
                            toolCall = toolCall,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                is ToolResultBlock -> {
                    // 🎯 关键修复：确保TodoWrite工具调用在当前消息中保持可见
                    val targetToolCall = toolCalls.find { it.id == contentBlock.toolUseId }
                    val isTodoWriteTool = targetToolCall?.name?.contains("TodoWrite", ignoreCase = true) == true

                    if (isTodoWriteTool) {
                        // TodoWrite工具：在当前消息中更新状态，确保工具调用保持可见
                        if (targetToolCall != null) {
                            val hasError = contentBlock.isError == true
                            val outputContent = contentBlock.content.toString()

                            val updatedToolCall = targetToolCall.copy(
                                result = if (hasError) {
                                    ToolResult.Failure(error = outputContent, details = null)
                                } else {
                                    ToolResult.Success(
                                        output = outputContent,
                                        summary = "任务列表已更新"
                                    )
                                },
                                status = if (hasError) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
                                endTime = System.currentTimeMillis()
                            )

                            val index = toolCalls.indexOf(targetToolCall)
                            toolCalls[index] = updatedToolCall
                            println("[SdkMessageConverter] 📝 TodoWrite工具保持在当前消息可见: ${targetToolCall.id} -> ${updatedToolCall.status}")
                        }
                    }

                    // 🎯 跨消息工具调用关联：使用SessionObject的全局方法
                    if (sessionObject != null) {
                        try {
                            val hasError = contentBlock.isError == true
                            val outputContent = contentBlock.content.toString()

                            // 使用SessionObject的公有方法updateToolCallStatus
                            val toolResult = if (hasError) {
                                ToolResult.Failure(
                                    error = outputContent,
                                    details = null
                                )
                            } else {
                                ToolResult.Success(
                                    output = outputContent,
                                    summary = if (outputContent.length > 100) "${outputContent.take(100)}..." else outputContent
                                )
                            }
                            val status = if (hasError) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS
                            sessionObject.updateToolCallStatus(contentBlock.toolUseId, status, toolResult)
                            println("[SdkMessageConverter] 🔧 已调用SessionObject.updateToolCallStatus: ${contentBlock.toolUseId} -> $status")
                        } catch (e: Exception) {
                            println("[SdkMessageConverter] ⚠️ 调用SessionObject.updateToolCallStatus失败: ${e.message}")
                            // 回退到原有逻辑
                            if (!isTodoWriteTool) {
                                handleToolResultFallback(contentBlock, toolCalls)
                            }
                        }
                    } else {
                        // 没有SessionObject时，使用原有逻辑（但TodoWrite除外）
                        if (!isTodoWriteTool) {
                            handleToolResultFallback(contentBlock, toolCalls)
                        }
                    }
                }
            }
        }

        // 转换 Token 使用信息
        val tokenUsage = message.tokenUsage?.let { usage ->
            println("🔍 [SdkMessageConverter] Token使用详情:")
            println("  - inputTokens: ${usage.inputTokens}")
            println("  - outputTokens: ${usage.outputTokens}")
            println("  - cacheCreationInputTokens: ${usage.cacheCreationInputTokens}")
            println("  - cacheReadInputTokens: ${usage.cacheReadInputTokens}")

            EnhancedMessage.TokenUsage(
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                cacheCreationTokens = usage.cacheCreationInputTokens ?: 0,
                cacheReadTokens = usage.cacheReadInputTokens ?: 0
            )
        }

        return EnhancedMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.ASSISTANT,
            content = textContent.toString(),
            timestamp = System.currentTimeMillis(),
            toolCalls = toolCalls,
            orderedElements = orderedElements,
            tokenUsage = tokenUsage,
            status = MessageStatus.COMPLETE
        )
    }

    /**
     * 处理工具结果的回退逻辑（当前消息范围查找）
     */
    private fun handleToolResultFallback(contentBlock: ToolResultBlock, toolCalls: MutableList<ToolCall>) {
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
                        summary = if (outputContent.length > 100) "${outputContent.take(100)}..." else outputContent
                    )
                },
                status = if (hasError) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
                endTime = System.currentTimeMillis()
            )

            val index = toolCalls.indexOf(targetToolCall)
            toolCalls[index] = updatedToolCall

            println("[SdkMessageConverter] 🔧 回退逻辑更新工具调用: ${targetToolCall.name} (${targetToolCall.id}) -> ${updatedToolCall.status}")
        } else {
            println("[SdkMessageConverter] ⚠️ 未找到工具调用ID: ${contentBlock.toolUseId}")
        }
    }

    /**
     * 将系统消息转换为 EnhancedMessage
     */
    private fun convertSystemMessage(message: SystemMessage): EnhancedMessage {
        val content = when (message.subtype) {
            "session_started" -> "会话已开始"
            "model_changed" -> "模型已切换"
            else -> "系统消息: ${message.subtype}"
        }

        return EnhancedMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.SYSTEM,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.COMPLETE
        )
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

        return EnhancedMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.SYSTEM,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = if (message.isError) MessageStatus.FAILED else MessageStatus.COMPLETE,
            isError = message.isError
        )
    }

    /**
     * 转换工具使用块为 ToolCall
     */
    private fun convertToolUseBlock(toolUse: ToolUseBlock): ToolCall {
        val parameters = parseToolParameters(toolUse.input)

        // 🔍 TodoWrite 工具专用调试日志
        if (toolUse.name == "TodoWrite") {
            println("[SdkMessageConverter] 🔍 TodoWrite工具参数解析:")
            println("[SdkMessageConverter] - 工具名称: ${toolUse.name}")
            println("[SdkMessageConverter] - 原始输入: ${toolUse.input}")
            println("[SdkMessageConverter] - 解析后参数: $parameters")
            println("[SdkMessageConverter] - 参数键: ${parameters.keys}")
            parameters.forEach { (key, value) ->
                println("[SdkMessageConverter] - $key: ${value::class.simpleName} = $value")
            }
        }

        return ToolCall(
            id = toolUse.id,
            name = toolUse.name,
            parameters = parameters,
            status = ToolCallStatus.RUNNING,
            result = null, // 结果会在后续的 ToolResultBlock 中设置
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
                val result = element.map { parseJsonElement(it) }
                println("[SdkMessageConverter] 解析JsonArray: 包含 ${result.size} 个元素")
                result
            }
            is JsonObject -> {
                // 递归解析对象中的每个字段
                val result = element.mapValues { (key, value) ->
                    val parsedValue = parseJsonElement(value)
                    println("[SdkMessageConverter] 解析JsonObject字段: $key -> ${parsedValue::class.simpleName}")
                    parsedValue
                }
                println("[SdkMessageConverter] 解析JsonObject完成: ${result.keys}")
                result
            }
            else -> element.toString()
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
     * 从 ClaudeCodeOptions 构建配置
     */
    fun buildClaudeCodeOptions(
        sessionObject: SessionObject,
        project: Project? = null
    ): ClaudeCodeOptions {
        return ClaudeCodeOptions(
            model = sessionObject.selectedModel?.cliName,
            permissionMode = when (sessionObject.selectedPermissionMode) {
                com.claudecodeplus.ui.models.PermissionMode.DEFAULT -> com.claudecodeplus.sdk.types.PermissionMode.DEFAULT
                com.claudecodeplus.ui.models.PermissionMode.ACCEPT -> com.claudecodeplus.sdk.types.PermissionMode.ACCEPT_EDITS
                com.claudecodeplus.ui.models.PermissionMode.BYPASS -> com.claudecodeplus.sdk.types.PermissionMode.BYPASS_PERMISSIONS
                com.claudecodeplus.ui.models.PermissionMode.PLAN -> com.claudecodeplus.sdk.types.PermissionMode.PLAN
                null -> null
            },
            cwd = project?.let { java.nio.file.Paths.get(it.path) },
            // 可以根据需要添加更多配置
            allowedTools = listOf("Read", "Write", "Edit", "Bash", "TodoWrite", "Glob", "Grep"),
            appendSystemPrompt = null // 可以从 sessionObject 中获取自定义系统提示
        )
    }
}

// MessageTimelineItem 在 UnifiedModels.kt 中已定义，这里移除重复定义