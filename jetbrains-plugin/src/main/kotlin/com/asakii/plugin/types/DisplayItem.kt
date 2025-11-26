package com.asakii.plugin.types

import com.asakii.claude.agent.sdk.types.ImageBlock
import kotlinx.serialization.json.JsonElement

/**
 * 前端显示层类型定义
 * 
 * 对应 frontend/src/types/display.ts
 * 这些类型用于 UI 展示，是从后端 Message 转换而来的 ViewModel
 */

// ============ 基础类型 ============

/**
 * 工具调用状态
 */
enum class ToolCallStatus {
    RUNNING,
    SUCCESS,
    FAILED
}

/**
 * 连接状态
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * 上下文引用类型
 */
enum class ContextType {
    FILE,
    WEB,
    FOLDER,
    IMAGE
}

/**
 * 上下文显示类型
 */
enum class ContextDisplayType {
    TAG,
    INLINE
}

/**
 * 上下文引用
 */
data class ContextReference(
    val type: ContextType,
    val uri: String,
    val displayType: ContextDisplayType,
    // 具体类型的额外字段
    val path: String? = null,
    val fullPath: String? = null,
    val url: String? = null,
    val title: String? = null,
    val fileCount: Int? = null,
    val totalSize: Long? = null,
    val name: String? = null,
    val mimeType: String? = null,
    val base64Data: String? = null,
    val size: Long? = null
)

// ============ DisplayItem 基础接口 ============

/**
 * 所有 DisplayItem 的基础接口
 */
sealed interface DisplayItem {
    val id: String
    val timestamp: Long
}

// ============ 消息类型 ============

/**
 * 请求统计信息
 */
data class RequestStats(
    val requestDuration: Long,  // 请求耗时（毫秒）
    val inputTokens: Int,       // 上行 tokens
    val outputTokens: Int       // 下行 tokens
)

/**
 * 用户消息
 */
data class UserMessageItem(
    override val id: String,
    override val timestamp: Long,
    val content: String,
    val images: List<ImageBlock> = emptyList(),
    val contexts: List<ContextReference> = emptyList(),
    val requestStats: RequestStats? = null,
    val isStreaming: Boolean = false
) : DisplayItem

/**
 * AI 文本回复
 */
data class AssistantTextItem(
    override val id: String,
    override val timestamp: Long,
    val content: String,
    val stats: RequestStats? = null,
    val isLastInMessage: Boolean = false
) : DisplayItem

/**
 * 系统消息级别
 */
enum class SystemMessageLevel {
    INFO,
    WARNING,
    ERROR
}

/**
 * 系统消息
 */
data class SystemMessageItem(
    override val id: String,
    override val timestamp: Long,
    val content: String,
    val level: SystemMessageLevel = SystemMessageLevel.INFO
) : DisplayItem

// ============ 工具调用结果 ============

/**
 * 工具调用结果
 */
sealed interface ToolResult {
    data class Success(
        val output: String,
        val summary: String? = null,
        val details: String? = null,
        val affectedFiles: List<String> = emptyList()
    ) : ToolResult
    
    data class Error(
        val error: String,
        val details: String? = null
    ) : ToolResult
}

// ============ 工具调用基础接口 ============

/**
 * 工具调用基础接口
 */
sealed interface ToolCallItem : DisplayItem {
    val toolType: String
    val status: ToolCallStatus
    val startTime: Long
    val endTime: Long?
    val input: Map<String, Any?>
    val result: ToolResult?
}

/**
 * Read 工具调用
 */
data class ReadToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.READ
    
    // 专用字段作为计算属性
    val filePath: String? get() = input["file_path"] as? String ?: input["path"] as? String
    val offset: Int? get() = input["offset"] as? Int
    val limit: Int? get() = input["limit"] as? Int
    val viewRange: Pair<Int, Int>? get() = (input["view_range"] as? List<*>)?.let {
        if (it.size >= 2) Pair((it[0] as Number).toInt(), (it[1] as Number).toInt()) else null
    }
}

/**
 * Write 工具调用
 */
data class WriteToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.WRITE
    
    val filePath: String? get() = input["file_path"] as? String ?: input["path"] as? String
    val content: String? get() = input["content"] as? String
}

/**
 * Edit 工具调用
 */
data class EditToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.EDIT
    
    val filePath: String get() = input["file_path"] as? String ?: ""
    val oldString: String get() = input["old_string"] as? String ?: ""
    val newString: String get() = input["new_string"] as? String ?: ""
    val replaceAll: Boolean get() = input["replace_all"] as? Boolean ?: false
}

/**
 * MultiEdit 编辑操作
 */
data class EditOperation(
    val oldString: String,
    val newString: String,
    val replaceAll: Boolean = false
)

/**
 * MultiEdit 工具调用
 */
data class MultiEditToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.MULTI_EDIT
    
    val filePath: String get() = input["file_path"] as? String ?: ""
    val edits: List<EditOperation> get() = (input["edits"] as? List<*>)?.mapNotNull { edit ->
        (edit as? Map<*, *>)?.let {
            EditOperation(
                oldString = it["old_string"] as? String ?: "",
                newString = it["new_string"] as? String ?: "",
                replaceAll = it["replace_all"] as? Boolean ?: false
            )
        }
    } ?: emptyList()
}

/**
 * TodoWrite 待办事项
 */
data class TodoItem(
    val content: String,
    val status: String,  // 'pending' | 'in_progress' | 'completed'
    val activeForm: String
)

/**
 * TodoWrite 工具调用
 */
data class TodoWriteToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.TODO_WRITE
    
    val todos: List<TodoItem> get() = (input["todos"] as? List<*>)?.mapNotNull { todo ->
        (todo as? Map<*, *>)?.let {
            TodoItem(
                content = it["content"] as? String ?: "",
                status = it["status"] as? String ?: "pending",
                activeForm = it["activeForm"] as? String ?: ""
            )
        }
    } ?: emptyList()
}

/**
 * Bash 工具调用
 */
data class BashToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.BASH
    
    val command: String get() = input["command"] as? String ?: ""
    val description: String? get() = input["description"] as? String
    val cwd: String? get() = input["cwd"] as? String
    val timeout: Int? get() = input["timeout"] as? Int
}

/**
 * Grep 工具调用
 */
data class GrepToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.GREP
    
    val pattern: String get() = input["pattern"] as? String ?: ""
    val path: String? get() = input["path"] as? String
    val glob: String? get() = input["glob"] as? String
    val type: String? get() = input["type"] as? String
    val outputMode: String? get() = input["output_mode"] as? String
}

/**
 * Glob 工具调用
 */
data class GlobToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.GLOB
    
    val pattern: String get() = input["pattern"] as? String ?: ""
    val path: String? get() = input["path"] as? String
}

/**
 * WebSearch 工具调用
 */
data class WebSearchToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.WEB_SEARCH
    
    val query: String get() = input["query"] as? String ?: ""
    val allowedDomains: List<String> get() = (input["allowed_domains"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    val blockedDomains: List<String> get() = (input["blocked_domains"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
}

/**
 * WebFetch 工具调用
 */
data class WebFetchToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.WEB_FETCH
    
    val url: String get() = input["url"] as? String ?: ""
    val prompt: String get() = input["prompt"] as? String ?: ""
}

/**
 * Task 工具调用
 */
data class TaskToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.TASK
}

/**
 * NotebookEdit 工具调用
 */
data class NotebookEditToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.NOTEBOOK_EDIT
}

/**
 * BashOutput 工具调用
 */
data class BashOutputToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.BASH_OUTPUT
}

/**
 * KillShell 工具调用
 */
data class KillShellToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.KILL_SHELL
}

/**
 * ExitPlanMode 工具调用
 */
data class ExitPlanModeToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.EXIT_PLAN_MODE
}

/**
 * AskUserQuestion 工具调用
 */
data class AskUserQuestionToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.ASK_USER_QUESTION
}

/**
 * Skill 工具调用
 */
data class SkillToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.SKILL
}

/**
 * SlashCommand 工具调用
 */
data class SlashCommandToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.SLASH_COMMAND
}

/**
 * ListMcpResources 工具调用
 */
data class ListMcpResourcesToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.LIST_MCP_RESOURCES
}

/**
 * ReadMcpResource 工具调用
 */
data class ReadMcpResourceToolCall(
    override val id: String,
    override val timestamp: Long,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem {
    override val toolType: String = ToolConstants.READ_MCP_RESOURCE
}

/**
 * 通用工具调用（用于未知或其他 MCP 工具）
 */
data class GenericToolCall(
    override val id: String,
    override val timestamp: Long,
    override val toolType: String,
    override val status: ToolCallStatus,
    override val startTime: Long,
    override val endTime: Long? = null,
    override val input: Map<String, Any?>,
    override val result: ToolResult? = null
) : ToolCallItem

