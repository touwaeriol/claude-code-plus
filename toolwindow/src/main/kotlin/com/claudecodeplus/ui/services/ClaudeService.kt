package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.Message

/**
 * Claude 服务接口
 * 抽象 AI 对话功能，使 UI 组件与具体实现解耦
 */
interface ClaudeService {
    /**
     * 发送消息到 Claude
     * @param message 用户消息
     * @return AI 响应
     */
    suspend fun sendMessage(message: String): Result<String>
    
    /**
     * 获取对话历史
     */
    suspend fun getHistory(): List<Message>
    
    /**
     * 清除对话历史
     */
    suspend fun clearHistory()
}