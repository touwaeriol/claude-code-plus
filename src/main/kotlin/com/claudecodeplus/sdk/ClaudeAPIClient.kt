package com.claudecodeplus.sdk

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

/**
 * Claude API 客户端，通过 Unix Domain Socket 与 Node.js 服务通信
 */
class ClaudeAPIClient(
    private val nodeServicePath: String
) {
    companion object {
        private val logger = Logger.getInstance(ClaudeAPIClient::class.java)
    }
    
    private val socketClient = UnixSocketClient(nodeServicePath)
    
    /**
     * 初始化客户端，启动 Node 服务进程
     */
    suspend fun initialize(): Boolean {
        return socketClient.start()
    }
    
    /**
     * 健康检查
     */
    suspend fun checkHealth(): HealthStatus {
        return socketClient.checkHealth()
    }
    
    /**
     * 发送流式消息
     */
    fun streamMessage(
        message: String,
        options: ClaudeOptions = ClaudeOptions(),
        onChunk: (String) -> Unit
    ): Flow<String> {
        return socketClient.streamMessage(message, options, onChunk)
    }
    
    /**
     * 中止当前请求
     */
    suspend fun abortCurrentRequest(): Boolean {
        return socketClient.abort()
    }
    
    /**
     * 检查客户端是否存活
     */
    fun isAlive(): Boolean {
        return socketClient.isAlive()
    }
    
    /**
     * 停止客户端
     */
    fun stop() {
        socketClient.stop()
    }
}

/**
 * 健康状态
 */
data class HealthStatus(
    val isHealthy: Boolean,
    val isProcessing: Boolean,
    val activeSessions: Int
)

/**
 * Claude 选项
 */
data class ClaudeOptions(
    val model: String? = null,
    val system: String? = null,
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val mcp: Map<String, Any>? = null
)