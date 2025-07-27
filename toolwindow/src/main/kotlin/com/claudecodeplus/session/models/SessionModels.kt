package com.claudecodeplus.session.models

import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageRole
import com.claudecodeplus.ui.models.AiModel
import java.time.Instant

/**
 * 会话信息
 */
data class SessionInfo(
    val sessionId: String,
    val filePath: String,
    val lastModified: Long,
    val messageCount: Int,
    val firstMessage: String? = null,
    val lastMessage: String? = null,
    val projectPath: String,
    val isCompactSummary: Boolean = false  // 标记是否为压缩会话
)

/**
 * Claude CLI 会话消息（简化版）
 */
data class ClaudeSessionMessage(
    val uuid: String,
    val parentUuid: String? = null,
    val sessionId: String,
    val type: String,
    val timestamp: String,
    val message: MessageContent,
    val cwd: String,
    val version: String
)

/**
 * 消息内容
 */
data class MessageContent(
    val role: String,
    val content: Any? = null,
    val id: String? = null,
    val model: String? = null,
    val usage: Map<String, Any>? = null
)

/**
 * 会话懒加载状态
 */
data class LazyLoadState(
    val loadedMessages: List<EnhancedMessage> = emptyList(),
    val totalMessageCount: Int = 0,
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val currentPage: Int = 0
)

/**
 * 将 Claude 会话消息转换为增强消息
 */
fun ClaudeSessionMessage.toEnhancedMessage(): EnhancedMessage? {
    // 跳过系统消息
    if (type != "user" && type != "assistant") return null
    
    val role = when (message.role) {
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        else -> return null
    }
    
    val content = when (val c = message.content) {
        is String -> c
        is List<*> -> {
            // 处理结构化内容
            c.filterIsInstance<Map<*, *>>()
                .mapNotNull { map ->
                    if (map["type"] == "text") {
                        map["text"] as? String
                    } else null
                }
                .joinToString("\n")
        }
        else -> ""
    }
    
    // 解析模型
    val model = message.model?.let { modelStr ->
        when {
            modelStr.contains("opus") -> AiModel.OPUS
            modelStr.contains("sonnet") -> AiModel.SONNET
            else -> AiModel.OPUS
        }
    } ?: AiModel.OPUS
    
    // 解析 token 使用信息
    val tokenUsage = message.usage?.let { usage ->
        EnhancedMessage.TokenUsage(
            inputTokens = (usage["input_tokens"] as? Number)?.toInt() ?: 0,
            outputTokens = (usage["output_tokens"] as? Number)?.toInt() ?: 0,
            cacheCreationTokens = (usage["cache_creation_input_tokens"] as? Number)?.toInt() ?: 0,
            cacheReadTokens = (usage["cache_read_input_tokens"] as? Number)?.toInt() ?: 0
        )
    }
    
    return EnhancedMessage(
        id = uuid,
        role = role,
        content = content,
        timestamp = parseTimestamp(timestamp),
        model = model,
        tokenUsage = tokenUsage
    )
}

/**
 * 解析 ISO8601 时间戳
 */
private fun parseTimestamp(timestamp: String): Long {
    return try {
        Instant.parse(timestamp).toEpochMilli()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}