package com.claudecodeplus.plugin.types

import kotlinx.serialization.Serializable

/**
 * UI 模型类型定义（向后兼容）
 * 
 * 这些类型用于 JetBrains 插件中的 UI 层
 */

/**
 * AI 模型枚举
 */
enum class AiModel {
    SONNET,
    OPUS,
    HAIKU,
    OPUS_PLAN
}

/**
 * 权限模式枚举（插件 UI 用）
 */
enum class UiPermissionMode {
    DEFAULT,
    ACCEPT,
    BYPASS,
    PLAN
}

/**
 * 消息角色
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    ERROR
}

/**
 * 消息状态
 */
enum class MessageStatus {
    PENDING,
    STREAMING,
    COMPLETE,
    ERROR
}

/**
 * Token 使用信息（插件 UI 用，与 SDK 的 TokenUsage 不同）
 */
@Serializable
data class UiTokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheCreationTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val totalTokens: Int = 0
)

/**
 * 会话对象（旧版，向后兼容）
 */
data class SessionObject(
    val id: String,
    val name: String,
    val createdAt: Long,
    val modelId: String?
)

/**
 * 增强消息（旧版，向后兼容）
 */
data class EnhancedMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val contexts: List<ContextReference> = emptyList(),
    val tokenUsage: UiTokenUsage? = null
)

/**
 * 旧版 ToolCall（向后兼容）
 * 现在是 ToolCallItem 的别名
 */
typealias ToolCall = ToolCallItem

/**
 * 旧版工具调用数据类（用于适配器层）
 */
data class LegacyToolCall(
    val name: String,
    val id: String,
    val status: String,
    val input: Map<String, Any?> = emptyMap(),
    val result: String? = null,
    val viewModel: Any? = null
)

