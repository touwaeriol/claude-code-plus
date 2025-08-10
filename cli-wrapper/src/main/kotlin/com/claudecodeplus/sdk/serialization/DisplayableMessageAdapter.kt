package com.claudecodeplus.sdk.serialization

import java.util.*

/**
 * 界面显示消息适配器
 * 
 * 将 Claude 原生消息转换为界面可显示的消息
 * 负责提取和格式化显示所需的信息
 */

/**
 * 用户显示消息
 * 
 * 用户输入消息的界面适配版本
 */
data class UserDisplayMessage(
    private val nativeMessage: UserMessage,
    override val displayId: String = nativeMessage.uuid ?: UUID.randomUUID().toString(),
    override val displayContent: String = extractUserContent(nativeMessage),
    override val displayTimestamp: Long = parseTimestamp(nativeMessage.timestamp)
) : DisplayableMessage, ClaudeNativeMessage by nativeMessage {
    
    override val displayRole: MessageDisplayRole = MessageDisplayRole.USER
    override val isStreamable: Boolean = false
    
    companion object {
        private fun extractUserContent(message: UserMessage): String {
            return message.message?.content?.filterIsInstance<TextBlock>()
                ?.joinToString("\n") { it.text } ?: ""
        }
    }
}

/**
 * 助手显示消息
 * 
 * AI 助手消息的界面适配版本，支持工具调用和流式更新
 */
data class AssistantDisplayMessage(
    private val nativeMessage: AssistantMessage,
    override val displayId: String = nativeMessage.uuid ?: UUID.randomUUID().toString(),
    override val displayContent: String = extractAssistantContent(nativeMessage),
    override val displayTimestamp: Long = parseTimestamp(nativeMessage.timestamp),
    override val isStreamable: Boolean = true,
    override var isStreaming: Boolean = false,
    override var streamingContent: String = displayContent,
    override val toolCalls: List<ToolCallInfo> = extractToolCalls(nativeMessage),
    override val tokenUsage: TokenUsage? = nativeMessage.message?.usage,
    override val costInfo: CostInfo? = null // 可以从 tokenUsage 计算
) : DisplayableMessage, 
    ClaudeNativeMessage by nativeMessage,
    ToolCallMessage, 
    StreamableMessage,
    StatisticsMessage {
    
    override val displayRole: MessageDisplayRole = MessageDisplayRole.ASSISTANT
    
    override fun updateStreamingContent(newContent: String) {
        streamingContent = newContent
    }
    
    override fun completeStreaming() {
        isStreaming = false
        streamingContent = displayContent
    }
    
    companion object {
        private fun extractAssistantContent(message: AssistantMessage): String {
            return message.message?.content?.filterIsInstance<TextBlock>()
                ?.joinToString("\n") { it.text } ?: ""
        }
        
        private fun extractToolCalls(message: AssistantMessage): List<ToolCallInfo> {
            val toolBlocks = message.message?.content?.filterIsInstance<ToolUseBlock>() ?: return emptyList()
            
            return toolBlocks.map { toolBlock ->
                ToolCallInfo(
                    id = toolBlock.id,
                    name = toolBlock.name,
                    status = ToolCallStatus.PENDING,
                    parameters = toolBlock.input.entries.associate { (key, value) -> 
                        key to value.toString() 
                    },
                    startTime = parseTimestamp(message.timestamp)
                )
            }
        }
    }
}

/**
 * 系统显示消息
 * 
 * 系统消息的界面适配版本（如会话开始、工具配置等）
 */
data class SystemDisplayMessage(
    private val nativeMessage: SystemMessage,
    override val displayId: String = nativeMessage.uuid ?: UUID.randomUUID().toString(),
    override val displayContent: String = formatSystemContent(nativeMessage),
    override val displayTimestamp: Long = parseTimestamp(nativeMessage.timestamp)
) : DisplayableMessage, ClaudeNativeMessage by nativeMessage {
    
    override val displayRole: MessageDisplayRole = MessageDisplayRole.SYSTEM
    override val isStreamable: Boolean = false
    
    companion object {
        private fun formatSystemContent(message: SystemMessage): String {
            return buildString {
                message.subtype?.let { appendLine("系统: $it") }
                if (message.tools.isNotEmpty()) {
                    appendLine("可用工具: ${message.tools.joinToString(", ")}")
                }
                message.model?.let { appendLine("模型: $it") }
                message.permissionMode?.let { appendLine("权限: $it") }
            }.trim()
        }
    }
}

/**
 * 摘要显示消息
 * 
 * 对话摘要的界面适配版本
 */
data class SummaryDisplayMessage(
    private val nativeMessage: SummaryMessage,
    override val displayId: String = nativeMessage.uuid ?: UUID.randomUUID().toString(),
    override val displayContent: String = nativeMessage.summary,
    override val displayTimestamp: Long = parseTimestamp(nativeMessage.timestamp)
) : DisplayableMessage, ClaudeNativeMessage by nativeMessage {
    
    override val displayRole: MessageDisplayRole = MessageDisplayRole.SYSTEM
    override val isStreamable: Boolean = false
    
    val isCompactSummary: Boolean = nativeMessage.isCompactSummary
}

/**
 * 错误显示消息
 * 
 * 用于显示错误信息的消息类型（非Claude原生消息）
 */
data class ErrorDisplayMessage(
    override val displayId: String = UUID.randomUUID().toString(),
    override val displayContent: String,
    override val displayTimestamp: Long = System.currentTimeMillis(),
    val errorType: String = "general",
    val errorDetails: String? = null
) : DisplayableMessage {
    
    override val displayRole: MessageDisplayRole = MessageDisplayRole.ERROR
    override val isStreamable: Boolean = false
}

/**
 * 消息适配器工厂
 * 
 * 负责创建适当的显示消息类型
 */
object MessageDisplayAdapter {
    
    /**
     * 将 Claude 原生消息转换为显示消息
     */
    fun toDisplayMessage(nativeMessage: ClaudeNativeMessage): DisplayableMessage? {
        return when (nativeMessage) {
            is UserMessage -> UserDisplayMessage(nativeMessage)
            is AssistantMessage -> AssistantDisplayMessage(nativeMessage)
            is SystemMessage -> SystemDisplayMessage(nativeMessage)
            is SummaryMessage -> SummaryDisplayMessage(nativeMessage)
            is ResultMessage -> null // ResultMessage 通常不直接显示
            else -> null
        }
    }
    
    /**
     * 创建错误显示消息
     */
    fun createErrorMessage(
        content: String,
        errorType: String = "general",
        details: String? = null
    ): ErrorDisplayMessage {
        return ErrorDisplayMessage(
            displayContent = content,
            errorType = errorType,
            errorDetails = details
        )
    }
    
    /**
     * 批量转换消息列表
     */
    fun toDisplayMessages(nativeMessages: List<ClaudeNativeMessage>): List<DisplayableMessage> {
        return nativeMessages.mapNotNull { toDisplayMessage(it) }
    }
}

/**
 * 时间戳解析工具函数
 */
private fun parseTimestamp(timestamp: String?): Long {
    return if (timestamp.isNullOrEmpty()) {
        System.currentTimeMillis()
    } else {
        try {
            java.time.Instant.parse(timestamp).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}