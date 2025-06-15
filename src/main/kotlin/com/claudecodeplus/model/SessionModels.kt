package com.claudecodeplus.model

import java.time.LocalDateTime

/**
 * 会话配置
 */
data class SessionConfig(
    val mode: SessionMode = SessionMode.SDK,
    val pythonPath: String? = null,
    val claudePath: String? = null,
    val apiKey: String? = null,
    val model: String = "claude-3-sonnet-20240229",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val workingDirectory: String = System.getProperty("user.dir")
)

/**
 * 会话模式
 */
enum class SessionMode {
    PTY,        // 通过 PTY 运行原生 Claude CLI
    SDK,        // 通过外部 Python 进程调用 SDK
    GRAAL_PYTHON // 使用 GraalVM 内嵌 Python
}

/**
 * 会话消息
 */
data class SessionMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val metadata: Map<String, Any> = emptyMap()
)

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
 * 会话状态
 */
data class SessionState(
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val status: SessionStatus = SessionStatus.IDLE,
    val messages: List<SessionMessage> = emptyList(),
    val currentDirectory: String = System.getProperty("user.dir"),
    val environment: Map<String, String> = emptyMap(),
    val startTime: LocalDateTime? = null,
    val endTime: LocalDateTime? = null
)

/**
 * 会话状态枚举
 */
enum class SessionStatus {
    IDLE,        // 空闲
    INITIALIZING, // 初始化中
    READY,       // 就绪
    PROCESSING,  // 处理中
    ERROR,       // 错误
    TERMINATED   // 已终止
}

/**
 * 内容块 - 用于表示消息中的不同内容类型
 */
sealed class ContentBlock {
    data class Text(val text: String) : ContentBlock()
    data class Code(val language: String, val code: String) : ContentBlock()
    data class Ansi(val content: String) : ContentBlock()
    data class Selection(val options: List<SelectionOption>) : ContentBlock()
}

/**
 * 选择选项
 */
data class SelectionOption(
    val id: String,
    val label: String,
    val value: String,
    val description: String? = null,
    val isDefault: Boolean = false
)

/**
 * 选择框区域
 */
data class BoxRegion(
    val startLine: Int,
    val endLine: Int,
    val content: String,
    val options: List<SelectionOption>
)

/**
 * 选择项
 */
data class SelectionItem(
    val id: String,
    val label: String,
    val value: String,
    val selected: Boolean = false
)