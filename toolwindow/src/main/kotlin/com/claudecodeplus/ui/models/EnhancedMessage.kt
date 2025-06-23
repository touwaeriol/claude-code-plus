package com.claudecodeplus.ui.models

/**
 * 增强的消息模型
 * 支持上下文引用、工具调用展示等高级功能
 */

data class EnhancedMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val contexts: List<ContextReference> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val model: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.COMPLETE
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    ERROR
}

enum class MessageStatus {
    SENDING,      // 正在发送
    STREAMING,    // 正在接收流式响应
    COMPLETE,     // 完成
    FAILED        // 失败
}

// 工具调用信息
data class ToolCall(
    val id: String = java.util.UUID.randomUUID().toString(),
    val tool: ToolType,
    val displayName: String,
    val parameters: Map<String, Any> = emptyMap(),
    val status: ToolCallStatus = ToolCallStatus.PENDING,
    val result: ToolResult? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null
)

enum class ToolType {
    SEARCH_FILES,     // 搜索文件
    READ_FILE,        // 读取文件
    EDIT_FILE,        // 编辑文件
    RUN_COMMAND,      // 运行命令
    SEARCH_SYMBOLS,   // 搜索符号
    GET_PROBLEMS,     // 获取问题
    GIT_OPERATION,    // Git 操作
    WEB_SEARCH,       // 网络搜索
    OTHER            // 其他工具
}

enum class ToolCallStatus {
    PENDING,      // 等待执行
    RUNNING,      // 正在执行
    SUCCESS,      // 执行成功
    FAILED,       // 执行失败
    CANCELLED     // 已取消
}

// 工具执行结果
sealed class ToolResult {
    data class Success(
        val summary: String,
        val details: String? = null,
        val affectedFiles: List<String> = emptyList()
    ) : ToolResult()
    
    data class Failure(
        val error: String,
        val details: String? = null
    ) : ToolResult()
    
    data class FileSearchResult(
        val files: List<FileContext>,
        val totalCount: Int
    ) : ToolResult()
    
    data class FileReadResult(
        val content: String,
        val lineCount: Int,
        val language: String? = null
    ) : ToolResult()
    
    data class FileEditResult(
        val oldContent: String,
        val newContent: String,
        val changedLines: IntRange
    ) : ToolResult()
    
    data class CommandResult(
        val output: String,
        val exitCode: Int,
        val duration: Long
    ) : ToolResult()
}

// 模型信息
data class AIModel(
    val id: String,
    val displayName: String,
    val description: String,
    val capabilities: Set<ModelCapability> = emptySet()
)

enum class ModelCapability {
    DEEP_REASONING,      // 深度推理
    CODE_GENERATION,     // 代码生成
    CODE_REVIEW,        // 代码审查
    REFACTORING,        // 重构
    DEBUGGING,          // 调试
    FAST_RESPONSE       // 快速响应
}

// 预定义的模型
object AIModels {
    val CLAUDE_4_OPUS = AIModel(
        id = "claude-3-opus-20240229",
        displayName = "Claude 4 Opus",
        description = "最强大的模型，适合复杂任务和深度推理",
        capabilities = setOf(
            ModelCapability.DEEP_REASONING,
            ModelCapability.CODE_GENERATION,
            ModelCapability.CODE_REVIEW,
            ModelCapability.REFACTORING,
            ModelCapability.DEBUGGING
        )
    )
    
    val CLAUDE_4_SONNET = AIModel(
        id = "claude-3-5-sonnet-20241022",
        displayName = "Claude 4 Sonnet",
        description = "平衡的模型，适合日常编码任务",
        capabilities = setOf(
            ModelCapability.CODE_GENERATION,
            ModelCapability.CODE_REVIEW,
            ModelCapability.REFACTORING,
            ModelCapability.FAST_RESPONSE
        )
    )
    
    val CLAUDE_3_5_SONNET = AIModel(
        id = "claude-3-5-sonnet-20240620",
        displayName = "Claude 3.5 Sonnet",
        description = "快速响应模型，适合简单任务",
        capabilities = setOf(
            ModelCapability.CODE_GENERATION,
            ModelCapability.FAST_RESPONSE
        )
    )
    
    val DEFAULT_MODELS = listOf(
        CLAUDE_4_OPUS,
        CLAUDE_4_SONNET,
        CLAUDE_3_5_SONNET
    )
}