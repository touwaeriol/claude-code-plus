package com.claudecodeplus.ui.models

import java.util.UUID

/**
 * AI 模型枚举
 */
enum class AiModel(val displayName: String, val cliName: String, val description: String) {
    OPUS("Claude 4 Opus", "opus", "深度推理，复杂任务"),
    SONNET("Claude 4 Sonnet", "sonnet", "平衡性能，日常编码")
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
    SENDING,      // 正在发送
    STREAMING,    // 正在接收流式响应
    COMPLETE,     // 完成
    FAILED        // 失败
}

/**
 * 上下文显示类型
 */
enum class ContextDisplayType {
    TAG,     // 显示为标签（Add Context按钮添加）
    INLINE   // 内联显示（@符号触发添加）
}

/**
 * 内联文件引用 - 用于管理@符号添加的文件引用
 */
data class InlineFileReference(
    val displayName: String,    // 显示名称：ContextSelectorTestApp.kt
    val fullPath: String,       // 完整路径：toolwindow-test/src/main/kotlin/com/claudecodeplus/test/ContextSelectorTestApp.kt
    val relativePath: String    // 相对路径：src/main/kotlin/com/claudecodeplus/test/ContextSelectorTestApp.kt
) {
    /**
     * 获取用于插入到文本中的显示文本
     */
    fun getInlineText(): String = "@$displayName"
    
    /**
     * 获取发送时的完整路径文本
     */
    fun getFullPathText(): String = "@$relativePath"
}

/**
 * 内联引用管理器 - 管理消息中的@符号引用
 */
class InlineReferenceManager {
    private val referenceMap = mutableMapOf<String, InlineFileReference>()
    
    /**
     * 添加内联引用
     */
    fun addReference(reference: InlineFileReference) {
        referenceMap[reference.getInlineText()] = reference
    }
    
    /**
     * 移除内联引用
     */
    fun removeReference(inlineText: String) {
        referenceMap.remove(inlineText)
    }
    
    /**
     * 获取所有引用
     */
    fun getAllReferences(): Map<String, InlineFileReference> = referenceMap.toMap()
    
    /**
     * 清空所有引用
     */
    fun clear() {
        referenceMap.clear()
    }
    
    /**
     * 展开消息中的内联引用为完整路径
     */
    fun expandInlineReferences(message: String): String {
        val pattern = "@([\\w.-]+(?:\\.\\w+)?)".toRegex()
        return pattern.replace(message) { matchResult ->
            val inlineText = matchResult.value
            val reference = referenceMap[inlineText]
            reference?.getFullPathText() ?: inlineText
        }
    }
    
    /**
     * 从消息中提取所有@符号引用
     */
    fun extractInlineReferences(message: String): List<String> {
        val pattern = "@([\\w.-]+(?:\\.\\w+)?)".toRegex()
        return pattern.findAll(message).map { it.value }.toList()
    }
    
    /**
     * 检查消息中是否包含未知的内联引用
     */
    fun hasUnknownReferences(message: String): List<String> {
        val extracted = extractInlineReferences(message)
        return extracted.filter { !referenceMap.containsKey(it) }
    }
}

/**
 * 上下文引用类型
 */
sealed class ContextReference {
    abstract val displayType: ContextDisplayType
    abstract val uri: String
    
    /**
     * 文件引用
     * @param path 文件路径（可能是相对路径或绝对路径）
     * @param fullPath 完整路径（用于悬停提示）
     * @param displayType 显示类型
     */
    data class FileReference(
        val path: String,
        val fullPath: String = path,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "file://$fullPath"
    }
    
    /**
     * Web引用
     * @param url 完整URL
     * @param title 网页标题（可选，用于悬停提示）
     * @param displayType 显示类型
     */
    data class WebReference(
        val url: String,
        val title: String? = null,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = url
    }
    
    // 保留原有类型兼容性（暂时未使用）
    data class FolderReference(
        val path: String,
        val fileCount: Int = 0,
        val totalSize: Long = 0,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "folder://$path"
    }
    
    data class SymbolReference(
        val name: String,
        val type: SymbolType,
        val file: String,
        val line: Int,
        val preview: String? = null,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "symbol:$file#$name"
    }
    
    data class TerminalReference(
        val content: String,
        val lines: Int = 50,
        val timestamp: Long = System.currentTimeMillis(),
        val isError: Boolean = false,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "terminal:output?lines=$lines&ts=$timestamp"
    }
    
    data class ProblemsReference(
        val problems: List<Problem>,
        val severity: ProblemSeverity? = null,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "problems:list?count=${problems.size}"
    }
    
    data class GitReference(
        val type: GitRefType,
        val content: String,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "git:${type.name.lowercase()}"
    }
    
    /**
     * 图片引用
     * @param path 图片文件路径
     * @param filename 文件名
     * @param size 文件大小（字节）
     * @param mimeType MIME类型（如 image/png）
     * @param displayType 显示类型
     */
    data class ImageReference(
        val path: String,
        val filename: String,
        val size: Long = 0,
        val mimeType: String = "image/*",
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "file://$path"
    }
    
    object SelectionReference : ContextReference() {
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
        override val uri: String = "selection:current"
    }
    
    object WorkspaceReference : ContextReference() {
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
        override val uri: String = "workspace:root"
    }
}

/**
 * 符号类型
 */
enum class SymbolType {
    CLASS, INTERFACE, FUNCTION, PROPERTY, VARIABLE, CONSTANT, ENUM, OBJECT
}

/**
 * 问题严重程度
 */
enum class ProblemSeverity {
    ERROR, WARNING, INFO, HINT
}

/**
 * Git 引用类型
 */
enum class GitRefType {
    DIFF, STAGED, COMMITS, BRANCHES, STATUS
}

/**
 * 问题信息
 */
data class Problem(
    val severity: ProblemSeverity,
    val message: String,
    val file: String,
    val line: Int,
    val column: Int? = null
)

/**
 * 工具调用状态
 */
enum class ToolCallStatus {
    PENDING, RUNNING, SUCCESS, FAILED, CANCELLED
}

/**
 * 工具类型
 */
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

/**
 * 工具调用信息
 */
data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tool: ToolType = ToolType.OTHER,
    val displayName: String = name,
    val parameters: Map<String, Any> = emptyMap(),
    val status: ToolCallStatus = ToolCallStatus.PENDING,
    val result: ToolResult? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null
)

/**
 * 消息时间线元素 - 按时间顺序排列的消息组件
 */
sealed class MessageTimelineItem {
    abstract val timestamp: Long
    
    /**
     * 工具调用元素
     */
    data class ToolCallItem(
        val toolCall: ToolCall,
        override val timestamp: Long = toolCall.startTime
    ) : MessageTimelineItem()
    
    /**
     * 文本内容元素
     */
    data class ContentItem(
        val content: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : MessageTimelineItem()
    
    /**
     * 状态元素（如"正在生成..."）
     */
    data class StatusItem(
        val status: String,
        val isStreaming: Boolean = false,
        override val timestamp: Long = System.currentTimeMillis()
    ) : MessageTimelineItem()
}

/**
 * 工具执行结果
 */
sealed class ToolResult {
    data class Success(
        val output: String,
        val summary: String = output,
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
    val status: MessageStatus = MessageStatus.COMPLETE,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val orderedElements: List<MessageTimelineItem> = emptyList(),
    val tokenUsage: TokenUsage? = null
) {
    // Backward compatibility properties
    val modelName: String? get() = model?.cliName
    
    /**
     * Token 使用信息
     */
    data class TokenUsage(
        val inputTokens: Int,
        val outputTokens: Int,
        val cacheCreationTokens: Int = 0,
        val cacheReadTokens: Int = 0
    ) {
        val totalTokens: Int get() = inputTokens + outputTokens
    }
}

// Context data classes for providers
data class FileContext(
    val path: String,
    val name: String,
    val extension: String,
    val size: Long,
    val lastModified: Long,
    val preview: String? = null
)

data class SymbolContext(
    val name: String,
    val type: SymbolType,
    val file: String,
    val line: Int,
    val signature: String? = null,
    val documentation: String? = null
)

data class TerminalContext(
    val output: String,
    val timestamp: Long,
    val hasErrors: Boolean,
    val command: String? = null
)

data class GitContext(
    val type: GitRefType,
    val content: String,
    val files: List<String> = emptyList(),
    val stats: GitStats? = null
)

data class GitStats(
    val additions: Int,
    val deletions: Int,
    val filesChanged: Int
)

data class FolderContext(
    val path: String,
    val fileCount: Int,
    val folderCount: Int,
    val totalSize: Long,
    val files: List<FileContext>
)