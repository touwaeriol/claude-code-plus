package com.claudecodeplus.sdk.session

/**
 * Claude CLI 会话文件消息数据类
 * 
 * 表示 Claude CLI 输出的 JSONL 格式消息
 */
data class ClaudeFileMessage(
    val type: String,
    val sessionId: String? = null,
    val timestamp: String? = null,  // 改为 String 类型，支持 ISO 8601 格式
    val message: MessageContent = MessageContent()
)

data class MessageContent(
    val role: String? = null,
    val content: Any? = null
)

/**
 * 时间戳解析工具
 */
object TimestampUtils {
    /**
     * 将 ISO 8601 时间戳字符串转换为毫秒时间戳
     * 支持格式：2025-08-03T08:32:15.667Z
     */
    fun parseTimestamp(timestamp: String?): Long {
        if (timestamp.isNullOrBlank()) return System.currentTimeMillis()
        
        return try {
            // 尝试直接解析为 Long（兼容旧格式）
            timestamp.toLongOrNull() ?: run {
                // 解析 ISO 8601 格式
                java.time.Instant.parse(timestamp).toEpochMilli()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}