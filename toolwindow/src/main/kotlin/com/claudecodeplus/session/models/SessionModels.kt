package com.claudecodeplus.session.models

import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageRole
import com.claudecodeplus.ui.models.AiModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant

/**
 * 会话信息
 */
@Serializable
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
@Serializable
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
@Serializable
data class MessageContent(
    val role: String,
    val content: kotlinx.serialization.json.JsonElement? = null,
    val id: String? = null,
    val model: String? = null,
    val usage: Map<String, kotlinx.serialization.json.JsonElement>? = null
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
    
    // 处理内容和工具调用
    val contentList = when (val c = message.content) {
        is kotlinx.serialization.json.JsonPrimitive -> {
            if (c.isString) {
                listOf(mapOf("type" to "text", "text" to c.content))
            } else emptyList()
        }
        is kotlinx.serialization.json.JsonArray -> {
            c.mapNotNull { element ->
                if (element is kotlinx.serialization.json.JsonObject) {
                    element.toMap().mapValues { (_, value) ->
                        when (value) {
                            is kotlinx.serialization.json.JsonPrimitive -> value.content
                            else -> value.toString()
                        }
                    }
                } else null
            }
        }
        else -> emptyList()
    }
    
    // 提取文本内容
    val textContent = contentList
        .mapNotNull { map ->
            if (map["type"] == "text") {
                map["text"] as? String
            } else null
        }
        .joinToString("\n")
    
    // 提取工具调用和结果
    val toolCalls = extractToolCallsFromContent(contentList)
    
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
            inputTokens = usage["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            outputTokens = usage["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            cacheCreationTokens = usage["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            cacheReadTokens = usage["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        )
    }
    
    return EnhancedMessage.create(
        id = uuid,
        role = role,
        text = textContent,
        timestamp = parseTimestamp(timestamp),
        model = model,
        tokenUsage = tokenUsage,
        toolCalls = toolCalls
    )
}

/**
 * 从内容列表中提取工具调用和结果
 */
private fun extractToolCallsFromContent(contentList: List<Map<*, *>>): List<com.claudecodeplus.ui.models.ToolCall> {
    val toolCalls = mutableListOf<com.claudecodeplus.ui.models.ToolCall>()
    val toolResults = mutableMapOf<String, com.claudecodeplus.ui.models.ToolResult>()
    
    // 首先提取所有工具结果
    contentList.forEach { map ->
        if (map["type"] == "tool_result") {
            val toolUseId = map["tool_use_id"] as? String ?: ""
            val resultContent = map["content"] as? String ?: ""
            val isError = map["is_error"] as? Boolean ?: false
            
            val result = if (isError) {
                com.claudecodeplus.ui.models.ToolResult.Failure(resultContent)
            } else {
                com.claudecodeplus.ui.models.ToolResult.Success(resultContent)
            }
            
            toolResults[toolUseId] = result
        }
    }
    
    // 然后提取工具调用并关联结果
    contentList.forEach { map ->
        if (map["type"] == "tool_use") {
            val toolId = map["id"] as? String ?: ""
            val toolName = map["name"] as? String ?: ""
            val inputMap = map["input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            // 将输入参数转换为 Map<String, String>
            val parameters = inputMap.mapKeys { (key, _) ->
                key?.toString() ?: ""
            }.mapValues { (_, value) ->
                value?.toString() ?: ""
            }
            
            // 查找对应的工具结果
            val result = toolResults[toolId]
            val status = when (result) {
                is com.claudecodeplus.ui.models.ToolResult.Success -> com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS
                is com.claudecodeplus.ui.models.ToolResult.Failure -> com.claudecodeplus.ui.models.ToolCallStatus.FAILED
                else -> com.claudecodeplus.ui.models.ToolCallStatus.RUNNING
            }
            
            val toolCall = com.claudecodeplus.ui.models.ToolCall.createGeneric(
                id = toolId,
                name = toolName,
                parameters = parameters,
                status = status,
                result = result,
                startTime = System.currentTimeMillis(),
                endTime = if (result != null) System.currentTimeMillis() else null
            )
            
            toolCalls.add(toolCall)
        }
    }
    
    return toolCalls
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
