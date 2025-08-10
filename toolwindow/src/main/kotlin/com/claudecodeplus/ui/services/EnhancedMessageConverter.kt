package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.serialization.MessageDisplayAdapter
import com.claudecodeplus.sdk.serialization.DisplayableMessage
import com.claudecodeplus.sdk.serialization.UserDisplayMessage
import com.claudecodeplus.sdk.serialization.AssistantDisplayMessage
import com.claudecodeplus.sdk.serialization.SystemDisplayMessage
import com.claudecodeplus.sdk.serialization.SummaryDisplayMessage
import com.claudecodeplus.sdk.serialization.ErrorDisplayMessage
import com.claudecodeplus.sdk.serialization.MessageDisplayRole
import com.claudecodeplus.sdk.serialization.ClaudeMessageParser
import com.claudecodeplus.sdk.session.ClaudeFileMessage
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageRole
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.MessageStatus
import com.claudecodeplus.ui.models.AiModel
import mu.KotlinLogging

/**
 * 增强的消息转换器
 * 
 * 使用新的Claude序列化系统，提供从Claude JSONL到UI EnhancedMessage的完整转换
 * 替换原有的MessageFlowConverter，提供更准确和完整的消息处理
 */
class EnhancedMessageConverter {
    private val logger = KotlinLogging.logger {}
    
    /**
     * 从JSONL字符串转换为EnhancedMessage
     * 
     * @param jsonLine Claude CLI输出的JSONL行
     * @return 转换后的EnhancedMessage，如果不应显示则返回null
     */
    fun convertFromJsonLine(jsonLine: String): EnhancedMessage? {
        return try {
            // 使用新的解析器解析JSONL
            val parsed = ClaudeMessageParser.parseMessage(jsonLine)
            if (parsed?.message == null) {
                logger.trace { "Failed to parse message from JSONL: ${jsonLine.take(100)}" }
                return null
            }
            
            // 转换为显示消息
            val displayMessage = MessageDisplayAdapter.toDisplayMessage(parsed.message)
            if (displayMessage == null) {
                logger.trace { "Message not displayable: ${parsed.message.type}" }
                return null
            }
            
            // 转换为UI EnhancedMessage
            convertDisplayMessageToEnhanced(displayMessage)
            
        } catch (e: Exception) {
            logger.error(e) { "Error converting JSONL to EnhancedMessage: ${jsonLine.take(100)}" }
            null
        }
    }
    
    /**
     * 从ClaudeFileMessage转换为EnhancedMessage
     * 兼容现有的文件消息处理流程
     */
    fun convertFromFileMessage(fileMessage: ClaudeFileMessage): EnhancedMessage? {
        return try {
            // 将ClaudeFileMessage转换为JSONL格式
            val jsonLine = fileMessageToJsonLine(fileMessage)
            convertFromJsonLine(jsonLine)
        } catch (e: Exception) {
            logger.error(e) { "Error converting ClaudeFileMessage: ${fileMessage.type}" }
            null
        }
    }
    
    /**
     * 批量转换JSONL行
     */
    fun convertFromJsonLines(jsonlContent: String): List<EnhancedMessage> {
        return jsonlContent.lines()
            .filter { it.trim().isNotEmpty() }
            .mapNotNull { convertFromJsonLine(it) }
    }
    
    /**
     * 创建错误消息
     */
    fun createErrorMessage(
        content: String,
        errorType: String = "general",
        details: String? = null
    ): EnhancedMessage {
        val errorDisplayMessage = MessageDisplayAdapter.createErrorMessage(content, errorType, details)
        return convertDisplayMessageToEnhanced(errorDisplayMessage)
    }
    
    /**
     * 将DisplayableMessage转换为UI的EnhancedMessage
     */
    private fun convertDisplayMessageToEnhanced(displayMessage: DisplayableMessage): EnhancedMessage {
        return when (displayMessage) {
            is UserDisplayMessage -> convertUserDisplay(displayMessage)
            is AssistantDisplayMessage -> convertAssistantDisplay(displayMessage)
            is SystemDisplayMessage -> convertSystemDisplay(displayMessage)
            is SummaryDisplayMessage -> convertSummaryDisplay(displayMessage)
            is ErrorDisplayMessage -> convertErrorDisplay(displayMessage)
            else -> convertGenericDisplay(displayMessage)
        }
    }
    
    /**
     * 转换用户显示消息
     */
    private fun convertUserDisplay(userDisplay: UserDisplayMessage): EnhancedMessage {
        return EnhancedMessage(
            id = userDisplay.displayId,
            role = MessageRole.USER,
            content = userDisplay.displayContent,
            timestamp = userDisplay.displayTimestamp,
            status = MessageStatus.COMPLETE,
            isStreaming = false,
            toolCalls = emptyList()
        )
    }
    
    /**
     * 转换助手显示消息
     */
    private fun convertAssistantDisplay(assistantDisplay: AssistantDisplayMessage): EnhancedMessage {
        // 转换工具调用
        val toolCalls = assistantDisplay.toolCalls.map { toolCallInfo ->
            ToolCall(
                id = toolCallInfo.id,
                name = toolCallInfo.name,
                displayName = toolCallInfo.name,
                parameters = toolCallInfo.parameters,
                status = convertToolCallStatus(toolCallInfo.status),
                result = null, // 结果在后续消息中提供
                startTime = toolCallInfo.startTime,
                endTime = toolCallInfo.endTime
            )
        }
        
        // 转换Token使用信息
        val tokenUsage = assistantDisplay.tokenUsage?.let { usage ->
            EnhancedMessage.TokenUsage(
                inputTokens = usage.input_tokens,
                outputTokens = usage.output_tokens,
                cacheCreationTokens = usage.cache_creation_input_tokens,
                cacheReadTokens = usage.cache_read_input_tokens
            )
        }
        
        // 推断使用的模型
        val model = inferModelFromMessage(assistantDisplay)
        
        return EnhancedMessage(
            id = assistantDisplay.displayId,
            role = MessageRole.ASSISTANT,
            content = assistantDisplay.displayContent,
            timestamp = assistantDisplay.displayTimestamp,
            toolCalls = toolCalls,
            model = model,
            status = if (assistantDisplay.isStreaming) MessageStatus.STREAMING else MessageStatus.COMPLETE,
            isStreaming = assistantDisplay.isStreaming,
            tokenUsage = tokenUsage
        )
    }
    
    /**
     * 转换系统显示消息
     */
    private fun convertSystemDisplay(systemDisplay: SystemDisplayMessage): EnhancedMessage {
        return EnhancedMessage(
            id = systemDisplay.displayId,
            role = MessageRole.SYSTEM,
            content = systemDisplay.displayContent,
            timestamp = systemDisplay.displayTimestamp,
            status = MessageStatus.COMPLETE,
            isStreaming = false
        )
    }
    
    /**
     * 转换摘要显示消息
     */
    private fun convertSummaryDisplay(summaryDisplay: SummaryDisplayMessage): EnhancedMessage {
        return EnhancedMessage(
            id = summaryDisplay.displayId,
            role = MessageRole.SYSTEM,
            content = summaryDisplay.displayContent,
            timestamp = summaryDisplay.displayTimestamp,
            status = MessageStatus.COMPLETE,
            isStreaming = false,
            isCompactSummary = summaryDisplay.isCompactSummary
        )
    }
    
    /**
     * 转换错误显示消息
     */
    private fun convertErrorDisplay(errorDisplay: ErrorDisplayMessage): EnhancedMessage {
        val content = buildString {
            appendLine("❌ ${errorDisplay.displayContent}")
            errorDisplay.errorDetails?.let { details ->
                appendLine()
                appendLine("详细信息:")
                appendLine(details)
            }
        }
        
        return EnhancedMessage(
            id = errorDisplay.displayId,
            role = MessageRole.ERROR,
            content = content.trim(),
            timestamp = errorDisplay.displayTimestamp,
            status = MessageStatus.COMPLETE,
            isStreaming = false,
            isError = true
        )
    }
    
    /**
     * 转换通用显示消息
     */
    private fun convertGenericDisplay(displayMessage: DisplayableMessage): EnhancedMessage {
        val role = when (displayMessage.displayRole) {
            MessageDisplayRole.USER -> MessageRole.USER
            MessageDisplayRole.ASSISTANT -> MessageRole.ASSISTANT
            MessageDisplayRole.SYSTEM -> MessageRole.SYSTEM
            MessageDisplayRole.ERROR -> MessageRole.ERROR
        }
        
        return EnhancedMessage(
            id = displayMessage.displayId,
            role = role,
            content = displayMessage.displayContent,
            timestamp = displayMessage.displayTimestamp,
            status = MessageStatus.COMPLETE,
            isStreaming = displayMessage.isStreamable && (displayMessage as? AssistantDisplayMessage)?.isStreaming == true
        )
    }
    
    /**
     * 转换工具调用状态
     */
    private fun convertToolCallStatus(status: com.claudecodeplus.sdk.serialization.ToolCallStatus): ToolCallStatus {
        return when (status) {
            com.claudecodeplus.sdk.serialization.ToolCallStatus.PENDING -> ToolCallStatus.PENDING
            com.claudecodeplus.sdk.serialization.ToolCallStatus.RUNNING -> ToolCallStatus.RUNNING
            com.claudecodeplus.sdk.serialization.ToolCallStatus.SUCCESS -> ToolCallStatus.SUCCESS
            com.claudecodeplus.sdk.serialization.ToolCallStatus.FAILED -> ToolCallStatus.FAILED
            com.claudecodeplus.sdk.serialization.ToolCallStatus.CANCELLED -> ToolCallStatus.CANCELLED
        }
    }
    
    /**
     * 从助手消息推断使用的模型
     */
    private fun inferModelFromMessage(assistantDisplay: AssistantDisplayMessage): AiModel? {
        // 尝试从原生消息中获取模型信息
        val modelName = (assistantDisplay as? com.claudecodeplus.sdk.serialization.ClaudeNativeMessage)?.let { native ->
            // 如果能获取到原生消息，尝试从中提取模型信息
            if (native.type == "assistant") {
                // 可以从更详细的原生消息中提取模型信息
                null // 暂时返回null，后续可以改进
            } else null
        }
        
        // 根据模型名称映射到UI的AiModel枚举
        return when {
            modelName?.contains("opus", ignoreCase = true) == true -> AiModel.OPUS
            modelName?.contains("sonnet", ignoreCase = true) == true -> AiModel.SONNET
            else -> null // 使用默认模型
        }
    }
    
    /**
     * 将ClaudeFileMessage转换为JSONL格式
     * 这是一个临时解决方案，用于兼容现有的文件消息格式
     */
    private fun fileMessageToJsonLine(fileMessage: ClaudeFileMessage): String {
        // 构建基本的JSONL格式
        return buildString {
            append("""{"type":"${fileMessage.type}"""")
            append(""","timestamp":"${fileMessage.timestamp}"""")
            
            // 添加消息内容
            append(""","message":""")
            append("""{"role":"${fileMessage.message.role}"""")
            append(""","content":""")
            
            // 处理内容格式
            when (val content = fileMessage.message.content) {
                is String -> {
                    append("""[{"type":"text","text":""")
                    append(content.replace("\"", "\\\"").replace("\n", "\\n"))
                    append(""""}]""")
                }
                is List<*> -> {
                    // 如果已经是列表格式，直接序列化
                    append(kotlinx.serialization.json.Json.encodeToString(
                        kotlinx.serialization.json.JsonArray.serializer(),
                        kotlinx.serialization.json.JsonArray(
                            content.map { item ->
                                when (item) {
                                    is Map<*, *> -> {
                                        kotlinx.serialization.json.JsonObject(
                                            item.entries.associate { (k, v) ->
                                                k.toString() to kotlinx.serialization.json.JsonPrimitive(v.toString())
                                            }
                                        )
                                    }
                                    else -> kotlinx.serialization.json.JsonPrimitive(item.toString())
                                }
                            }
                        )
                    ))
                }
                else -> {
                    append("""[{"type":"text","text":"${content.toString().replace("\"", "\\\"")}"}]""")
                }
            }
            append("}")
            append("}")
        }
    }
}