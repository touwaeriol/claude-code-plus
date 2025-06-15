package com.claudecodeplus.core

import com.claudecodeplus.model.SessionConfig
import com.claudecodeplus.model.SessionMessage
import com.claudecodeplus.model.SessionState
import kotlinx.coroutines.flow.Flow

/**
 * Claude 会话接口
 */
interface ClaudeSession {
    /**
     * 会话ID
     */
    val sessionId: String
    
    /**
     * 会话配置
     */
    val config: SessionConfig
    
    /**
     * 会话状态
     */
    val state: SessionState
    
    /**
     * 初始化会话
     */
    suspend fun initialize()
    
    /**
     * 发送消息
     */
    suspend fun sendMessage(message: String): Flow<String>
    
    /**
     * 停止当前处理
     */
    suspend fun stop()
    
    /**
     * 终止会话
     */
    suspend fun terminate()
    
    /**
     * 获取会话历史
     */
    fun getHistory(): List<SessionMessage>
    
    /**
     * 清空会话历史
     */
    fun clearHistory()
    
    /**
     * 是否正在处理
     */
    fun isProcessing(): Boolean
}