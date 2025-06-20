package com.claudecodeplus.sdk

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.Flow

/**
 * Claude API 客户端 V5 - 使用端口通信的 WebSocket
 */
class ClaudeAPIClientV5(private val port: Int = 9925) {
    companion object {
        private val logger = Logger.getInstance(ClaudeAPIClientV5::class.java)
    }
    
    private val wsClient = SimpleWebSocketClient(port)
    
    /**
     * 连接到服务
     */
    suspend fun connect(): Boolean {
        return try {
            val connected = wsClient.connect()
            if (connected) {
                logger.info("Successfully connected to Claude service on port $port")
                // 发送初始介绍消息
                sendIntroduction()
            }
            connected
        } catch (e: Exception) {
            logger.error("Failed to connect to Claude service", e)
            false
        }
    }
    
    /**
     * 发送初始介绍消息
     */
    private suspend fun sendIntroduction() {
        try {
            val introMessage = """
                你好！我是 Claude Code Plus 插件。
                这是一个为 IntelliJ IDEA 提供 AI 辅助功能的插件。
                请问有什么可以帮助您的吗？
            """.trimIndent()
            
            wsClient.streamChat(introMessage) { chunk ->
                logger.info("Introduction response: $chunk")
            }.collect { }
            
        } catch (e: Exception) {
            logger.warn("Failed to send introduction message", e)
        }
    }
    
    /**
     * 健康检查
     */
    suspend fun health(): HealthStatus {
        return wsClient.health()
    }
    
    /**
     * 发送消息（流式）
     */
    fun streamMessage(
        message: String,
        options: ClaudeOptions? = null,
        onChunk: (String) -> Unit
    ): Flow<String> {
        return wsClient.streamChat(message, options, onChunk)
    }
    
    /**
     * 中止当前会话
     */
    suspend fun abort(sessionId: String? = null) {
        wsClient.abort(sessionId)
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        wsClient.disconnect()
    }
    
    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean {
        return wsClient.isConnected()
    }
}