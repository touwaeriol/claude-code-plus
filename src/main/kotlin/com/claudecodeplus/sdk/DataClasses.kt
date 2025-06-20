package com.claudecodeplus.sdk

/**
 * 健康状态
 */
data class HealthStatus(
    val isHealthy: Boolean,
    val isProcessing: Boolean
)

/**
 * Claude 选项
 */
data class ClaudeOptions(
    val model: String = "claude-3-5-sonnet-20241022",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val system: String? = null,
    val mcp: Any? = null
)

/**
 * JSON-RPC 异常
 */
class JsonRpcException(val code: Int, message: String) : Exception(message)