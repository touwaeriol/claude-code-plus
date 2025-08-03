package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.session.ClaudeFileMessage
import com.claudecodeplus.sdk.session.TimestampUtils
import com.claudecodeplus.ui.models.*
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * 消息流转换器
 * 
 * 负责将 Claude 会话文件中的消息转换为 UI 层使用的 EnhancedMessage
 * 包含消息去重、工具调用处理和消息关联等功能
 */
class MessageFlowConverter {
    private val logger = KotlinLogging.logger {}
    
    // 消息去重缓存（sessionId -> Set<messageId>）
    private val seenMessages = ConcurrentHashMap<String, MutableSet<String>>()
    
    // 工具调用追踪（sessionId -> Map<toolCallId, ToolCall>）
    private val toolCallTracking = ConcurrentHashMap<String, MutableMap<String, ToolCall>>()
    
    /**
     * 转换单个消息
     */
    fun convertMessage(
        sessionMessage: ClaudeFileMessage,
        sessionId: String
    ): EnhancedMessage? {
        // 基础转换
        val enhancedMessage = convertToEnhancedMessage(sessionMessage)
        if (enhancedMessage == null) {
            logger.trace { "Skipped non-conversational message: ${sessionMessage.type}" }
            return null
        }
        
        // 去重检查
        if (!isNewMessage(sessionId, enhancedMessage.id)) {
            logger.trace { "Duplicate message detected: ${enhancedMessage.id}" }
            return null
        }
        
        // 处理工具调用
        val processedMessage = processToolCalls(enhancedMessage, sessionMessage, sessionId)
        
        return processedMessage
    }
    
    /**
     * 批量转换消息
     */
    fun convertMessages(
        sessionMessages: List<ClaudeFileMessage>,
        sessionId: String
    ): List<EnhancedMessage> {
        return sessionMessages.mapNotNull { message ->
            convertMessage(message, sessionId)
        }
    }
    
    /**
     * 检查是否为新消息
     */
    private fun isNewMessage(sessionId: String, messageId: String): Boolean {
        val messageSet = seenMessages.getOrPut(sessionId) { 
            ConcurrentHashMap.newKeySet() 
        }
        return messageSet.add(messageId)
    }
    
    /**
     * 处理工具调用相关逻辑
     */
    private fun processToolCalls(
        message: EnhancedMessage,
        sessionMessage: ClaudeFileMessage,
        sessionId: String
    ): EnhancedMessage {
        // 检查消息内容中是否包含工具调用
        val toolCalls = extractToolCalls(sessionMessage)
        
        if (toolCalls.isNotEmpty()) {
            // 记录工具调用
            val toolCallMap = toolCallTracking.getOrPut(sessionId) { mutableMapOf() }
            toolCalls.forEach { toolCall ->
                toolCallMap[toolCall.id] = toolCall
            }
            
            return message.copy(
                toolCalls = toolCalls,
                orderedElements = createOrderedElements(message, toolCalls)
            )
        }
        
        // 检查是否为工具结果
        val toolResult = extractToolResult(sessionMessage)
        if (toolResult != null) {
            // 更新对应的工具调用状态
            updateToolCallStatus(sessionId, toolResult)
        }
        
        return message
    }
    
    /**
     * 从消息中提取工具调用
     */
    private fun extractToolCalls(sessionMessage: ClaudeFileMessage): List<ToolCall> {
        val content = sessionMessage.message.content
        if (content !is List<*>) return emptyList()
        
        return content.mapNotNull { item ->
            if (item is Map<*, *> && item["type"] == "tool_use") {
                createToolCall(item)
            } else {
                null
            }
        }
    }
    
    /**
     * 创建工具调用对象
     */
    private fun createToolCall(toolUseMap: Map<*, *>): ToolCall? {
        return try {
            val id = toolUseMap["id"] as? String ?: return null
            val name = toolUseMap["name"] as? String ?: return null
            val input = toolUseMap["input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            ToolCall(
                id = id,
                name = name,
                parameters = input.mapKeys { it.key.toString() }.mapValues { it.value ?: "" },
                status = ToolCallStatus.RUNNING
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create tool call from map" }
            null
        }
    }
    
    /**
     * 从消息中提取工具结果
     */
    private fun extractToolResult(sessionMessage: ClaudeFileMessage): ToolResult? {
        val content = sessionMessage.message.content
        if (content !is List<*>) return null
        
        val toolResultItem = content.firstOrNull { item ->
            item is Map<*, *> && item["type"] == "tool_result"
        } as? Map<*, *> ?: return null
        
        return try {
            ToolResult(
                toolCallId = toolResultItem["tool_use_id"] as? String ?: return null,
                content = toolResultItem["content"] as? String,
                isError = toolResultItem["is_error"] as? Boolean ?: false
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to extract tool result" }
            null
        }
    }
    
    /**
     * 更新工具调用状态
     */
    private fun updateToolCallStatus(sessionId: String, toolResult: ToolResult) {
        val toolCallMap = toolCallTracking[sessionId] ?: return
        val toolCall = toolCallMap[toolResult.toolCallId] ?: return
        
        toolCallMap[toolResult.toolCallId] = toolCall.copy(
            status = if (toolResult.isError) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS
        )
    }
    
    /**
     * 创建有序元素列表
     */
    private fun createOrderedElements(
        message: EnhancedMessage,
        toolCalls: List<ToolCall>
    ): List<MessageTimelineItem> {
        val elements = mutableListOf<MessageTimelineItem>()
        
        // 添加文本内容
        if (message.content.isNotBlank()) {
            elements.add(MessageTimelineItem.ContentItem(
                content = message.content,
                timestamp = message.timestamp
            ))
        }
        
        // 添加工具调用
        toolCalls.forEach { toolCall ->
            elements.add(MessageTimelineItem.ToolCallItem(
                toolCall = toolCall,
                timestamp = message.timestamp
            ))
        }
        
        return elements
    }
    
    /**
     * 清除会话缓存
     */
    fun clearSessionCache(sessionId: String) {
        seenMessages.remove(sessionId)
        toolCallTracking.remove(sessionId)
        logger.debug { "Cleared cache for session: $sessionId" }
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCaches() {
        seenMessages.clear()
        toolCallTracking.clear()
        logger.info { "Cleared all message caches" }
    }
    
    /**
     * 获取会话统计信息
     */
    fun getSessionStats(sessionId: String): SessionStats {
        return SessionStats(
            totalMessages = seenMessages[sessionId]?.size ?: 0,
            activeToolCalls = toolCallTracking[sessionId]?.values?.count { 
                it.status == ToolCallStatus.RUNNING 
            } ?: 0
        )
    }
    
    /**
     * 工具结果数据类
     */
    private data class ToolResult(
        val toolCallId: String,
        val content: String?,
        val isError: Boolean
    )
    
    /**
     * 会话统计信息
     */
    data class SessionStats(
        val totalMessages: Int,
        val activeToolCalls: Int
    )
    
    /**
     * 将 ClaudeFileMessage 转换为 EnhancedMessage
     */
    private fun convertToEnhancedMessage(fileMessage: ClaudeFileMessage): EnhancedMessage? {
        // 简化的转换逻辑，只处理基本的消息类型
        return when (fileMessage.type) {
            "assistant" -> {
                val content = extractTextContent(fileMessage) ?: ""
                EnhancedMessage(
                    id = generateMessageId(),
                    role = MessageRole.ASSISTANT,
                    content = content,
                    timestamp = TimestampUtils.parseTimestamp(fileMessage.timestamp),
                    toolCalls = emptyList(),
                    orderedElements = listOf(
                        MessageTimelineItem.ContentItem(
                            content = content,
                            timestamp = TimestampUtils.parseTimestamp(fileMessage.timestamp)
                        )
                    )
                )
            }
            "user" -> {
                val content = extractTextContent(fileMessage) ?: ""
                EnhancedMessage(
                    id = generateMessageId(),
                    role = MessageRole.USER,
                    content = content,
                    timestamp = TimestampUtils.parseTimestamp(fileMessage.timestamp),
                    toolCalls = emptyList(),
                    orderedElements = listOf(
                        MessageTimelineItem.ContentItem(
                            content = content,
                            timestamp = TimestampUtils.parseTimestamp(fileMessage.timestamp)
                        )
                    )
                )
            }
            else -> null // 忽略其他类型的消息
        }
    }
    
    /**
     * 提取文本内容
     */
    private fun extractTextContent(fileMessage: ClaudeFileMessage): String? {
        val content = fileMessage.message.content
        return when {
            content is String -> content
            content is List<*> -> {
                // 尝试从列表中提取文本
                content.mapNotNull { item ->
                    when (item) {
                        is Map<*, *> -> {
                            if (item["type"] == "text") {
                                item["text"] as? String
                            } else null
                        }
                        is String -> item
                        else -> null
                    }
                }.joinToString("")
            }
            else -> content?.toString()
        }
    }
    
    /**
     * 生成消息 ID
     */
    private fun generateMessageId(): String {
        return "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}