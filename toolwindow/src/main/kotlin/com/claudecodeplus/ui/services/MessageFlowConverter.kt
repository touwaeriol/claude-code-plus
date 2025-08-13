package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.session.ClaudeFileMessage
import com.claudecodeplus.ui.models.*
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * 消息流转换器
 * 
 * 负责将 Claude 会话文件中的消息转换为 UI 层使用的 EnhancedMessage
 * 现在使用新的增强消息转换器，提供更准确和完整的消息处理
 */
class MessageFlowConverter {
    private val logger = KotlinLogging.logger {}
    
    // 使用新的增强消息转换器
    private val enhancedConverter = EnhancedMessageConverter()
    
    // 消息去重缓存（sessionId -> Set<messageId>）
    private val seenMessages = ConcurrentHashMap<String, MutableSet<String>>()
    
    /**
     * 转换单个消息
     */
    fun convertMessage(
        sessionMessage: ClaudeFileMessage,
        sessionId: String
    ): EnhancedMessage? {
        // 使用新的增强转换器
        val enhancedMessage = enhancedConverter.convertFromFileMessage(sessionMessage)
        if (enhancedMessage == null) {
            logger.trace { "Skipped non-displayable message: ${sessionMessage.type}" }
            return null
        }
        
        // 去重检查
        if (!isNewMessage(sessionId, enhancedMessage.id)) {
            logger.trace { "Duplicate message detected: ${enhancedMessage.id}" }
            return null
        }
        
        logger.debug { "Converted message: ${sessionMessage.type} -> ${enhancedMessage.role}" }
        return enhancedMessage
    }
    
    /**
     * 批量转换消息
     */
    fun convertMessages(
        sessionMessages: List<ClaudeFileMessage>,
        sessionId: String
    ): List<EnhancedMessage> {
        // 清除转换器的工具结果缓存，开始新的批量转换
        enhancedConverter.clearToolResultsCache()
        
        return sessionMessages.mapNotNull { message ->
            convertMessage(message, sessionId)
        }
    }
    
    /**
     * 从JSONL字符串直接转换消息（新增方法）
     */
    fun convertFromJsonLine(
        jsonLine: String,
        sessionId: String
    ): EnhancedMessage? {
        val enhancedMessage = enhancedConverter.convertFromJsonLine(jsonLine)
        if (enhancedMessage == null) {
            logger.trace { "Skipped non-displayable JSONL message" }
            return null
        }
        
        // 去重检查
        if (!isNewMessage(sessionId, enhancedMessage.id)) {
            logger.trace { "Duplicate message detected: ${enhancedMessage.id}" }
            return null
        }
        
        logger.debug { "Converted JSONL message: ${enhancedMessage.role}" }
        return enhancedMessage
    }
    
    /**
     * 从JSONL内容批量转换消息
     */
    fun convertFromJsonLines(
        jsonlContent: String,
        sessionId: String
    ): List<EnhancedMessage> {
        // 使用增强转换器的批量处理，它会正确处理工具调用关联
        val enhancedMessages = enhancedConverter.convertFromJsonLines(jsonlContent)
        
        // 应用去重逻辑
        return enhancedMessages.filter { message ->
            isNewMessage(sessionId, message.id)
        }.also { filteredMessages ->
            logger.debug { "Converted ${filteredMessages.size} messages from JSONL content" }
            logger.debug { "Tool results cached: ${enhancedConverter.getCachedToolResultsCount()}" }
        }
    }
    
    /**
     * 创建错误消息
     */
    fun createErrorMessage(
        content: String,
        sessionId: String,
        errorType: String = "general"
    ): EnhancedMessage {
        return enhancedConverter.createErrorMessage(content, errorType)
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
     * 清除会话缓存
     */
    fun clearSessionCache(sessionId: String) {
        seenMessages.remove(sessionId)
        logger.debug { "Cleared cache for session: $sessionId" }
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCaches() {
        seenMessages.clear()
        logger.info { "Cleared all message caches" }
    }
    
    /**
     * 获取会话统计信息
     */
    fun getSessionStats(sessionId: String): SessionStats {
        return SessionStats(
            totalMessages = seenMessages[sessionId]?.size ?: 0,
            activeToolCalls = 0 // 工具调用现在由新转换器处理
        )
    }
    
    /**
     * 会话统计信息
     */
    data class SessionStats(
        val totalMessages: Int,
        val activeToolCalls: Int
    )
}