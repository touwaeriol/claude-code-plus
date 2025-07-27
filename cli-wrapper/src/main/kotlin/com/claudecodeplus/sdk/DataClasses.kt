package com.claudecodeplus.sdk

/**
 * 消息类型枚举
 */
enum class MessageType {
    TEXT,
    ERROR,
    TOOL_USE,
    TOOL_RESULT,
    START,
    END
}

/**
 * SDK 消息数据类
 */
data class SDKMessage(
    val type: MessageType,
    val data: MessageData
)

/**
 * 消息数据内容
 */
data class MessageData(
    val text: String? = null,
    val error: String? = null,
    val toolName: String? = null,
    val toolInput: Any? = null,
    val toolResult: Any? = null,
    val sessionId: String? = null,  // 从 system 消息中获取
    val toolCallId: String? = null,  // 工具调用ID，用于匹配工具调用和结果
    val model: String? = null  // 模型信息，用于历史会话加载
)