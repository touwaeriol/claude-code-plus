package com.claudecodeplus.ui.models

import java.util.UUID

/**
 * AI 模型枚举
 */
enum class AiModel(val displayName: String, val cliName: String, val description: String) {
    OPUS("Claude 4 Opus", "claude-opus-4-20250514", "深度推理，复杂任务"),
    SONNET("Claude 4 Sonnet", "claude-3-5-sonnet-20241022", "平衡性能，日常编码"),
    SONNET_35("Claude 3.5 Sonnet", "claude-3-5-sonnet-20241022", "快速响应，简单任务")
}

/**
 * 上下文引用类型
 */
sealed class ContextReference {
    data class FileReference(
        val path: String,
        val lines: IntRange? = null
    ) : ContextReference()
    
    data class FolderReference(
        val path: String
    ) : ContextReference()
    
    data class SymbolReference(
        val name: String,
        val type: SymbolType,
        val location: String? = null
    ) : ContextReference()
    
    data class TerminalReference(
        val lines: Int = 50,
        val filter: String? = null
    ) : ContextReference()
    
    data class ProblemsReference(
        val severity: ProblemSeverity? = null
    ) : ContextReference()
    
    data class GitReference(
        val type: GitRefType
    ) : ContextReference()
    
    object SelectionReference : ContextReference()
    object WorkspaceReference : ContextReference()
}

enum class SymbolType {
    CLASS, INTERFACE, FUNCTION, PROPERTY, VARIABLE, CONSTANT
}

enum class ProblemSeverity {
    ERROR, WARNING, INFO
}

enum class GitRefType {
    DIFF, STAGED, COMMITS
}

/**
 * 工具调用相关
 */
data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parameters: Map<String, Any>,
    val status: ToolCallStatus = ToolCallStatus.PENDING,
    val result: ToolResult? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null
)

enum class ToolCallStatus {
    PENDING, RUNNING, SUCCESS, FAILED, CANCELLED
}

data class ToolResult(
    val output: String,
    val error: String? = null
)

/**
 * 增强的消息模型
 */
data class EnhancedMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val contexts: List<ContextReference> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val model: AiModel? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)