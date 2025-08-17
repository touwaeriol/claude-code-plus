package com.claudecodeplus.core.services

import com.claudecodeplus.core.models.ParseResult
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.session.models.ClaudeSessionMessage

/**
 * 消息处理服务接口
 * 负责解析和转换不同格式的消息
 */
interface MessageProcessor {
    
    /**
     * 解析实时CLI输出消息
     * @param jsonLine Claude CLI输出的JSONL格式消息
     * @return 解析结果
     */
    fun parseRealtimeMessage(jsonLine: String): ParseResult<EnhancedMessage>
    
    /**
     * 解析历史会话消息
     * @param sessionMessage 从会话文件读取的历史消息
     * @return 解析结果
     */
    fun parseHistoryMessage(sessionMessage: ClaudeSessionMessage): ParseResult<EnhancedMessage>
    
    /**
     * 验证消息有效性
     * @param message 待验证的消息
     * @return 是否有效
     */
    fun validateMessage(message: EnhancedMessage): Boolean
    
    /**
     * 提取会话ID从消息中
     * @param jsonLine CLI输出消息
     * @return 会话ID，如果不存在返回null
     */
    fun extractSessionId(jsonLine: String): String?
    
    /**
     * 判断是否为系统消息（如初始化、结果摘要等）
     * @param jsonLine CLI输出消息
     * @return 是否为系统消息
     */
    fun isSystemMessage(jsonLine: String): Boolean
    
    /**
     * 判断是否为工具结果消息
     * @param jsonLine CLI输出消息
     * @return 是否为工具结果消息
     */
    fun isToolResultMessage(jsonLine: String): Boolean
}