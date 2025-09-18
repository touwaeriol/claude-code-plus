package com.claudecodeplus.sdk.types

import kotlinx.serialization.json.JsonObject

/**
 * 临时的兼容性类型定义，用于迁移期间的编译支持
 * TODO: 这些应该在完全迁移到新 SDK 后移除
 */

/**
 * 旧的 SDKMessage 类型别名，现在指向新的 Message 类型
 */
typealias SDKMessage = Message

/**
 * 旧的 MessageType 枚举
 */
enum class MessageType {
    TEXT, TOOL_USE, TOOL_RESULT, START, END, ERROR, USER, ASSISTANT, SYSTEM
}

/**
 * 旧的 MessageData 类
 */
data class MessageData(
    val text: String? = null,
    val sessionId: String? = null,
    val error: String? = null,
    val toolName: String? = null,
    val toolId: String? = null,
    val toolCallId: String? = null,
    val toolInput: Any? = null,
    val toolResult: String? = null,
    val parameters: Map<String, Any>? = null,
    val result: String? = null
)

/**
 * 旧的 ClaudeMessage 类
 */
data class ClaudeMessage(
    val type: MessageType,
    val data: MessageData? = null,
    val content: JsonObject? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val messageId: String? = null
)

/**
 * 为 SDKMessage (Message) 添加兼容性扩展属性
 */
val Message.type: MessageType
    get() = when (this) {
        is UserMessage -> MessageType.USER
        is AssistantMessage -> MessageType.ASSISTANT
        is SystemMessage -> MessageType.SYSTEM
        is ResultMessage -> MessageType.END
    }

val Message.content: String?
    get() = when (this) {
        is UserMessage -> when (val contentElement = this.content) {
            is kotlinx.serialization.json.JsonPrimitive -> contentElement.content
            else -> contentElement.toString()
        }
        is AssistantMessage -> this.content.joinToString("") { block ->
            when (block) {
                is TextBlock -> block.text
                is ThinkingBlock -> "思考: ${block.thinking}"
                else -> ""
            }
        }
        is SystemMessage -> "系统消息: ${this.subtype}"
        is ResultMessage -> "结果: ${this.result ?: ""}"
    }

val Message.data: MessageData?
    get() = MessageData(
        text = this.content,
        sessionId = when (this) {
            is UserMessage -> this.sessionId
            else -> null
        }
    )

val Message.timestamp: Long
    get() = System.currentTimeMillis()

val Message.messageId: String?
    get() = when (this) {
        is UserMessage -> sessionId
        is AssistantMessage -> null
        is SystemMessage -> null
        is ResultMessage -> null
    }

val Message.util: String?
    get() = null // 兼容性属性，暂时返回null

/**
 * 旧的 ContentOrList 类型
 */
sealed class ContentOrList {
    data class StringContent(val value: String) : ContentOrList()
    data class ListContent(val items: List<ContentItem>) : ContentOrList()

    data class ContentItem(
        val type: String,
        val text: String? = null
    )
}

/**
 * 旧的 Usage 类
 */
data class Usage(
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheCreationInputTokens: Int? = null,
    val cacheReadInputTokens: Int? = null
)

/**
 * 模拟旧的 ClaudeCliWrapper.QueryResult 结构
 */
data class QueryResult(
    val success: Boolean = false,
    val error: String? = null,
    val sessionId: String? = null,
    val messages: List<Message> = emptyList()
)

/**
 * 模拟旧的 ClaudeCliWrapper.QueryOptions 结构
 */
data class QueryOptions(
    val workingDirectory: String = ".",
    val model: String = "claude-3-5-sonnet-20241022",
    val contextFiles: List<String> = emptyList(),
    val customCommand: String? = null,
    val appendSystemPrompt: String? = null,
    val mcpConfig: String? = null,
    val sessionId: String? = null
)