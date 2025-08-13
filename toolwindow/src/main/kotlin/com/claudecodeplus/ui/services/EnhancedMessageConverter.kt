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
import com.claudecodeplus.sdk.serialization.UserMessage
import com.claudecodeplus.sdk.serialization.ToolResultBlock
import com.claudecodeplus.sdk.session.ClaudeFileMessage
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageRole
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.MessageStatus
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.ToolResult
import mu.KotlinLogging

/**
 * 增强的消息转换器
 * 
 * 使用新的Claude序列化系统，提供从Claude JSONL到UI EnhancedMessage的完整转换
 * 替换原有的MessageFlowConverter，提供更准确和完整的消息处理
 * 
 * **关键改进**：正确处理工具调用和结果的关联映射
 */
class EnhancedMessageConverter {
    private val logger = KotlinLogging.logger {}
    
    /**
     * 工具结果缓存 - 存储所有工具调用的结果
     * Key: tool_use_id, Value: 工具结果内容
     */
    private val toolResultsCache = mutableMapOf<String, Any>()
    
    /**
     * 从JSONL字符串转换为EnhancedMessage
     * 
     * @param jsonLine Claude CLI输出的JSONL行
     * @return 转换后的EnhancedMessage，如果不应显示则返回null
     */
    fun convertFromJsonLine(jsonLine: String): EnhancedMessage? {
        return try {
            // 首先解析并缓存工具结果
            cacheToolResultsFromJsonLine(jsonLine)
            
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
            
            // 转换为UI EnhancedMessage，如果为null说明不应显示
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
            // 直接转换 ClaudeFileMessage，避免JSONL的转换开销和错误
            convertFileMessageDirectly(fileMessage)
        } catch (e: Exception) {
            logger.error(e) { "Error converting ClaudeFileMessage: ${fileMessage.type}" }
            null
        }
    }
    
    /**
     * 直接转换 ClaudeFileMessage 为 EnhancedMessage
     */
    private fun convertFileMessageDirectly(fileMessage: ClaudeFileMessage): EnhancedMessage? {
        val timestamp = try {
            java.time.Instant.parse(fileMessage.timestamp).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        
        val messageId = java.util.UUID.randomUUID().toString() // ClaudeFileMessage 没有 uuid 字段
        
        return when (fileMessage.type) {
            "user" -> {
                // 检查用户消息是否只包含工具结果（不应显示给用户）
                if (isToolResultOnlyMessage(fileMessage.message.content)) {
                    logger.debug { "Skipping tool result only user message" }
                    return null
                }
                
                EnhancedMessage(
                    id = messageId,
                    role = MessageRole.USER,
                    content = extractTextContent(fileMessage.message.content),
                    timestamp = timestamp,
                    status = MessageStatus.COMPLETE,
                    isStreaming = false,
                    toolCalls = emptyList()
                )
            }
            "assistant" -> {
                val textContent = extractTextContent(fileMessage.message.content)
                val toolCalls = extractToolCallsFromContent(fileMessage.message.content, messageId)
                
                // 如果助手消息既没有文本内容也没有工具调用，则不显示
                if (textContent.trim().isEmpty() && toolCalls.isEmpty()) {
                    logger.debug { "Skipping empty assistant message" }
                    return null
                }
                
                EnhancedMessage(
                    id = messageId,
                    role = MessageRole.ASSISTANT,
                    content = textContent,
                    timestamp = timestamp,
                    toolCalls = toolCalls,
                    status = MessageStatus.COMPLETE,
                    isStreaming = false
                )
            }
            "system", "result", "summary" -> {
                // 系统消息、结果消息和摘要消息不应显示给最终用户
                logger.debug { "Skipping non-user-facing message type: ${fileMessage.type}" }
                null
            }
            else -> {
                logger.debug { "Skipping unknown message type: ${fileMessage.type}" }
                null
            }
        }
    }
    
    /**
     * 检查消息是否只包含工具结果（这类消息不应显示给用户）
     */
    private fun isToolResultOnlyMessage(content: Any?): Boolean {
        if (content !is List<*>) return false
        
        // 检查是否所有内容块都是 tool_result 类型
        return content.all { item ->
            (item is Map<*, *> && item["type"] == "tool_result")
        }
    }
    
    /**
     * 从消息内容中提取文本
     */
    private fun extractTextContent(content: Any?): String {
        return when (content) {
            is String -> content
            is List<*> -> {
                content.mapNotNull { item ->
                    when (item) {
                        is Map<*, *> -> {
                            if (item["type"] == "text") {
                                item["text"]?.toString()
                            } else null
                        }
                        else -> null
                    }
                }.joinToString("\n")
            }
            else -> content?.toString() ?: ""
        }
    }
    
    /**
     * 从消息内容中提取工具调用
     */
    private fun extractToolCallsFromContent(content: Any?, messageId: String?): List<ToolCall> {
        if (content !is List<*>) {
            logger.debug { "Content is not a list: ${content?.javaClass?.simpleName}" }
            return emptyList()
        }
        
        logger.debug { "Extracting tool calls from content list with ${content.size} items" }
        content.forEachIndexed { index, item ->
            logger.debug { "Item $index: type=${(item as? Map<*, *>)?.get("type")}, class=${item?.javaClass?.simpleName}" }
        }
        
        return content.mapNotNull { item ->
            if (item is Map<*, *> && item["type"] == "tool_use") {
                val toolId = item["id"]?.toString() ?: return@mapNotNull null
                val toolName = item["name"]?.toString() ?: return@mapNotNull null
                val toolInput = item["input"] as? Map<*, *> ?: emptyMap<String, Any>()
                
                // 从缓存中查找对应的工具结果
                val cachedResult = toolResultsCache[toolId]
                val actualStatus = if (cachedResult != null) {
                    val isError = when (cachedResult) {
                        is Map<*, *> -> cachedResult["is_error"] == true
                        else -> false
                    }
                    if (isError) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS
                } else {
                    ToolCallStatus.PENDING
                }
                
                val toolResult = cachedResult?.let { result ->
                    val resultString = result.toString()
                    if (actualStatus == ToolCallStatus.FAILED) {
                        ToolResult.Failure(resultString)
                    } else {
                        ToolResult.Success(resultString)
                    }
                }
                
                ToolCall(
                    id = toolId,
                    name = toolName,
                    displayName = toolName,
                    parameters = toolInput.entries.associate { (key, value) -> key.toString() to value.toString() },
                    status = actualStatus,
                    result = toolResult,
                    startTime = System.currentTimeMillis(),
                    endTime = if (cachedResult != null) System.currentTimeMillis() else null
                )
            } else null
        }
    }
    
    
    /**
     * 批量转换JSONL行
     */
    fun convertFromJsonLines(jsonlContent: String): List<EnhancedMessage> {
        // 清除缓存，重新开始
        toolResultsCache.clear()
        
        // 首先预处理所有行，提取工具结果
        val lines = jsonlContent.lines().filter { it.trim().isNotEmpty() }
        lines.forEach { cacheToolResultsFromJsonLine(it) }
        
        // 然后转换所有消息
        return lines.mapNotNull { convertFromJsonLine(it) }
    }
    
    /**
     * 从JSONL行中提取并缓存工具结果
     */
    private fun cacheToolResultsFromJsonLine(jsonLine: String) {
        try {
            val parsed = ClaudeMessageParser.parseMessage(jsonLine)
            val message = parsed?.message
            
            // 如果是用户消息，检查是否包含工具结果
            if (message is UserMessage) {
                message.message?.content?.forEach { contentBlock ->
                    if (contentBlock is ToolResultBlock) {
                        toolResultsCache[contentBlock.tool_use_id] = contentBlock.content
                        logger.debug { "Cached tool result for ${contentBlock.tool_use_id}" }
                    }
                }
            }
        } catch (e: Exception) {
            logger.trace { "Failed to cache tool results from line: ${e.message}" }
        }
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
        return convertDisplayMessageToEnhanced(errorDisplayMessage) ?: run {
            // 如果转换失败，创建一个基本的错误消息
            EnhancedMessage(
                id = java.util.UUID.randomUUID().toString(),
                role = MessageRole.ERROR,
                content = "❌ $content",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.COMPLETE,
                isStreaming = false,
                isError = true
            )
        }
    }
    
    /**
     * 清除工具结果缓存
     * 在开始新的会话转换时调用
     */
    fun clearToolResultsCache() {
        toolResultsCache.clear()
        logger.debug { "Cleared tool results cache" }
    }
    
    /**
     * 获取缓存的工具结果数量（用于调试）
     */
    fun getCachedToolResultsCount(): Int = toolResultsCache.size
    
    /**
     * 兼容旧接口：批量转换 ClaudeFileMessage 列表
     */
    fun convertMessages(messages: List<ClaudeFileMessage>, sessionId: String? = null): List<EnhancedMessage> {
        // 清除缓存，重新开始
        clearToolResultsCache()
        
        // 预处理所有消息，先收集工具结果
        messages.forEach { message ->
            if (message.type == "user" && message.message.content is List<*>) {
                val contentList = message.message.content as List<*>
                contentList.forEach { contentItem ->
                    if (contentItem is Map<*, *> && contentItem["type"] == "tool_result") {
                        val toolUseId = contentItem["tool_use_id"]?.toString()
                        val content = contentItem["content"]
                        if (toolUseId != null) {
                            toolResultsCache[toolUseId] = content ?: ""
                            logger.debug { "Cached tool result for $toolUseId from historical message" }
                        }
                    }
                }
            }
        }
        
        // 转换所有消息，过滤掉不应显示的消息
        val convertedMessages = messages.mapNotNull { message ->
            val converted = convertFromFileMessage(message)
            if (converted != null) {
                logger.debug { "Converted ${message.type} message with ${converted.toolCalls.size} tool calls" }
                if (converted.toolCalls.isNotEmpty()) {
                    logger.debug { "Tool calls: ${converted.toolCalls.map { "${it.name}(${it.status})" }}" }
                }
            }
            converted
        }
        
        logger.debug { "Total converted messages: ${convertedMessages.size}, with tool calls: ${convertedMessages.count { it.toolCalls.isNotEmpty() }}" }
        return convertedMessages
    }
    
    /**
     * 将DisplayableMessage转换为UI的EnhancedMessage
     */
    private fun convertDisplayMessageToEnhanced(displayMessage: DisplayableMessage): EnhancedMessage? {
        return when (displayMessage) {
            is UserDisplayMessage -> convertUserDisplay(displayMessage)
            is AssistantDisplayMessage -> convertAssistantDisplay(displayMessage)
            is SystemDisplayMessage -> {
                // 系统消息不应显示给用户
                logger.debug { "Skipping system display message" }
                null
            }
            is SummaryDisplayMessage -> {
                // 摘要消息不应显示给用户
                logger.debug { "Skipping summary display message" }
                null
            }
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
        logger.debug { "Converting assistant display message - toolCalls.size: ${assistantDisplay.toolCalls.size}" }
        assistantDisplay.toolCalls.forEach { toolCall ->
            logger.debug { "Assistant display tool call: ${toolCall.name} (id: ${toolCall.id})" }
        }
        
        // 转换工具调用，并关联缓存的结果
        val toolCalls = assistantDisplay.toolCalls.map { toolCallInfo ->
            // 从缓存中查找对应的工具结果
            val cachedResult = toolResultsCache[toolCallInfo.id]
            val actualStatus = if (cachedResult != null) {
                // 检查是否为错误结果
                val isError = when (cachedResult) {
                    is Map<*, *> -> cachedResult["is_error"] == true
                    else -> false
                }
                if (isError) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS
            } else {
                ToolCallStatus.PENDING
            }
            
            val toolResult = cachedResult?.let { result ->
                val resultString = result.toString()
                if (actualStatus == ToolCallStatus.FAILED) {
                    ToolResult.Failure(resultString)
                } else {
                    ToolResult.Success(resultString)
                }
            }
            
            ToolCall(
                id = toolCallInfo.id,
                name = toolCallInfo.name,
                displayName = toolCallInfo.name,
                parameters = toolCallInfo.parameters,
                status = actualStatus,
                result = toolResult, // 使用正确的 ToolResult 类型
                startTime = toolCallInfo.startTime,
                endTime = if (cachedResult != null) System.currentTimeMillis() else toolCallInfo.endTime
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
    
}