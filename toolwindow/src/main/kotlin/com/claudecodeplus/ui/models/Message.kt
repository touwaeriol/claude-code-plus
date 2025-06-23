package com.claudecodeplus.ui.models

/**
 * UI 层的消息模型
 * 与 SDK 层的消息模型分离，提供更好的抽象
 */
data class Message(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)