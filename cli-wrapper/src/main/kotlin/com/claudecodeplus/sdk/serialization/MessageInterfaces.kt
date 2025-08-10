package com.claudecodeplus.sdk.serialization

/**
 * Claude CLI 原生消息接口
 * 
 * 标识从 Claude CLI JSONL 中解析出来的原生消息类型
 * 这些消息类型直接对应 Claude CLI 的输出格式
 */
interface ClaudeNativeMessage {
    val type: String
    val uuid: String?
    val sessionId: String?
    val timestamp: String?
}

/**
 * 界面可显示消息接口
 * 
 * 标识可以在聊天界面中展示给用户的消息类型
 * 包含必要的显示信息和状态
 */
interface DisplayableMessage {
    val displayId: String
    val displayRole: MessageDisplayRole
    val displayContent: String
    val displayTimestamp: Long
    val isStreamable: Boolean
}

/**
 * 消息显示角色
 * 
 * 定义消息在界面中的显示角色
 */
enum class MessageDisplayRole {
    USER,       // 用户消息
    ASSISTANT,  // AI助手消息  
    SYSTEM,     // 系统消息/摘要
    ERROR       // 错误消息
}

/**
 * 工具调用相关消息接口
 * 
 * 标识包含工具调用信息的消息
 */
interface ToolCallMessage {
    val toolCalls: List<ToolCallInfo>
}

/**
 * 流式消息接口
 * 
 * 标识支持流式更新的消息类型
 */
interface StreamableMessage {
    val isStreaming: Boolean
    val streamingContent: String
    
    fun updateStreamingContent(newContent: String)
    fun completeStreaming()
}

/**
 * 统计信息消息接口
 * 
 * 标识包含统计信息的消息（如token使用量、成本等）
 */
interface StatisticsMessage {
    val tokenUsage: TokenUsage?
    val costInfo: CostInfo?
}

/**
 * 成本信息
 */
data class CostInfo(
    val totalCostUsd: Double,
    val inputCost: Double,
    val outputCost: Double
)

/**
 * 工具调用信息
 */
data class ToolCallInfo(
    val id: String,
    val name: String,
    val status: ToolCallStatus,
    val parameters: Map<String, Any>,
    val result: String? = null,
    val startTime: Long,
    val endTime: Long? = null
)

/**
 * 工具调用状态
 */
enum class ToolCallStatus {
    PENDING,   // 等待执行
    RUNNING,   // 正在执行
    SUCCESS,   // 执行成功
    FAILED,    // 执行失败
    CANCELLED  // 已取消
}