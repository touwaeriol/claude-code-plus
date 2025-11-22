package com.claudecodeplus.sdk.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import kotlinx.serialization.json.JsonElement

/**
 * 工具类型枚举
 *
 * 定义所有已知的Claude Code工具类型。
 * 这些类型与官方文档中的工具名称保持一致。
 */
enum class ToolType(val toolName: String) {
    // 基础文件操作工具
    BASH("Bash"),
    BASH_OUTPUT("BashOutput"),
    KILL_SHELL("KillShell"),
    EDIT("Edit"),
    MULTI_EDIT("MultiEdit"),
    READ("Read"),
    WRITE("Write"),

    // 搜索和查找工具
    GLOB("Glob"),
    GREP("Grep"),

    // 网络工具
    WEB_FETCH("WebFetch"),
    WEB_SEARCH("WebSearch"),

    // 开发和任务管理工具
    TODO_WRITE("TodoWrite"),
    TASK("Task"),
    EXIT_PLAN_MODE("ExitPlanMode"),

    // Jupyter notebook工具
    NOTEBOOK_EDIT("NotebookEdit"),

    // MCP (Model Context Protocol) 工具
    MCP_TOOL("mcp__"),  // MCP工具通常以mcp__开头
    LIST_MCP_RESOURCES("ListMcpResourcesTool"),
    READ_MCP_RESOURCE("ReadMcpResourceTool"),

    // 未知工具类型
    UNKNOWN("unknown");

    companion object {
        /**
         * 根据工具名称获取对应的工具类型
         */
        fun fromToolName(name: String): ToolType {
            return when {
                name.startsWith("mcp__") -> MCP_TOOL
                else -> values().find { it.toolName == name } ?: UNKNOWN
            }
        }
    }
}

/**
 * 具体工具使用的基础接口
 *
 * 这个接口继承自ContentBlock，确保向后兼容性。
 * 所有具体的工具类都应该实现这个接口。
 *
 * 注意：
 * - 每个子类使用独特的 @SerialName 用于 Kotlin 序列化的多态性
 * - name 字段包含工具名称(如 "TodoWrite", "Edit", "Write" 等)
 * - input 字段包含原始的工具参数 (与 Claude API 格式一致)
 * - 在序列化为 JSON 发送给前端时,应该手动构建 {type: "tool_use", name: "...", id: "...", input: {...}} 格式
 */
@Serializable
sealed interface SpecificToolUse : ContentBlock {
    val id: String              // 工具调用ID
    val name: String            // 工具名称，如 "TodoWrite", "Edit", "Write" 等
    val input: JsonElement      // 原始参数 (与 Claude API 格式一致)

    // 内部使用的枚举类型，不序列化到 JSON
    @kotlinx.serialization.Transient
    val toolType: ToolType

    /**
     * 获取强类型的参数
     * 子类应该实现这个方法来提供特定工具的参数访问
     * 注意：返回类型为Any，但实际运行时类型是已知的
     */
    fun getTypedParameters(): Map<String, @Contextual Any>
}

/**
 * Bash工具使用
 * 执行shell命令
 */
@Serializable
@SerialName("bash_tool_use")
data class BashToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val command: String,
    val description: String? = null,
    val timeout: Long? = null,
    val runInBackground: Boolean = false
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.BASH

    override fun getTypedParameters(): Map<String, @Contextual Any> = buildMap {
        put("command", command)
        description?.let { put("description", it) }
        timeout?.let { put("timeout", it) }
        put("run_in_background", runInBackground)
    }
}

/**
 * Edit工具使用
 * 编辑文件内容
 */
@Serializable
@SerialName("edit_tool_use")
data class EditToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val filePath: String,
    val oldString: String,
    val newString: String,
    val replaceAll: Boolean = false
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.EDIT

    override fun getTypedParameters(): Map<String, @Contextual Any> = mapOf(
        "file_path" to filePath,
        "old_string" to oldString,
        "new_string" to newString,
        "replace_all" to replaceAll
    )
}

/**
 * MultiEdit工具使用
 * 对单个文件进行多次编辑
 */
@Serializable
@SerialName("multi_edit_tool_use")
data class MultiEditToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val filePath: String,
    val edits: List<EditOperation>
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.MULTI_EDIT

    override fun getTypedParameters(): Map<String, @Contextual Any> = mapOf(
        "file_path" to filePath,
        "edits" to edits.map { edit ->
            mapOf(
                "old_string" to edit.oldString,
                "new_string" to edit.newString,
                "replace_all" to edit.replaceAll
            )
        }
    )

    @Serializable
    data class EditOperation(
        val oldString: String,
        val newString: String,
        val replaceAll: Boolean = false
    )
}

/**
 * Read工具使用
 * 读取文件内容
 */
@Serializable
@SerialName("read_tool_use")
data class ReadToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val filePath: String,
    val offset: Int? = null,
    val limit: Int? = null
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.READ

    override fun getTypedParameters(): Map<String, @Contextual Any> = buildMap {
        put("file_path", filePath)
        offset?.let { put("offset", it) }
        limit?.let { put("limit", it) }
    }
}

/**
 * Write工具使用
 * 写入文件内容
 */
@Serializable
@SerialName("write_tool_use")
data class WriteToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val filePath: String,
    val content: String
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.WRITE

    override fun getTypedParameters(): Map<String, @Contextual Any> = mapOf(
        "file_path" to filePath,
        "content" to content
    )
}

/**
 * Glob工具使用
 * 文件模式匹配
 */
@Serializable
@SerialName("glob_tool_use")
data class GlobToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val pattern: String,
    val path: String? = null
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.GLOB

    override fun getTypedParameters(): Map<String, @Contextual Any> = buildMap {
        put("pattern", pattern)
        path?.let { put("path", it) }
    }
}

/**
 * Grep工具使用
 * 文本搜索
 */
@Serializable
@SerialName("grep_tool_use")
data class GrepToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val pattern: String,
    val path: String? = null,
    val outputMode: String? = null,
    val glob: String? = null,
    val type: String? = null,
    val caseInsensitive: Boolean = false,
    val showLineNumbers: Boolean = false,
    val contextBefore: Int? = null,
    val contextAfter: Int? = null,
    val headLimit: Int? = null
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.GREP

    override fun getTypedParameters(): Map<String, @Contextual Any> = buildMap {
        put("pattern", pattern)
        path?.let { put("path", it) }
        outputMode?.let { put("output_mode", it) }
        glob?.let { put("glob", it) }
        type?.let { put("type", it) }
        put("-i", caseInsensitive)
        put("-n", showLineNumbers)
        contextBefore?.let { put("-B", it) }
        contextAfter?.let { put("-A", it) }
        headLimit?.let { put("head_limit", it) }
    }
}

/**
 * WebFetch工具使用
 * 获取网页内容
 */
@Serializable
@SerialName("web_fetch_tool_use")
data class WebFetchToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val url: String,
    val prompt: String
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.WEB_FETCH

    override fun getTypedParameters(): Map<String, @Contextual Any> = mapOf(
        "url" to url,
        "prompt" to prompt
    )
}

/**
 * WebSearch工具使用
 * 网络搜索
 */
@Serializable
@SerialName("web_search_tool_use")
data class WebSearchToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val query: String,
    val allowedDomains: List<String>? = null,
    val blockedDomains: List<String>? = null
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.WEB_SEARCH

    override fun getTypedParameters(): Map<String, @Contextual Any> = buildMap {
        put("query", query)
        allowedDomains?.let { put("allowed_domains", it) }
        blockedDomains?.let { put("blocked_domains", it) }
    }
}

/**
 * TodoWrite工具使用
 * 管理待办事项列表
 */
@Serializable
@SerialName("todo_write_tool_use")
data class TodoWriteToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val todos: List<TodoItem>
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.TODO_WRITE

    override fun getTypedParameters(): Map<String, @Contextual Any> = mapOf(
        "todos" to todos.map { todo ->
            mapOf(
                "content" to todo.content,
                "status" to todo.status,
                "activeForm" to todo.activeForm
            )
        }
    )

    @Serializable
    data class TodoItem(
        val content: String,
        val status: String,  // pending, in_progress, completed
        val activeForm: String
    )
}

/**
 * Task工具使用
 * 启动子任务代理
 */
@Serializable
@SerialName("task_tool_use")
data class TaskToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val description: String,
    val prompt: String,
    val subagentType: String
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.TASK

    override fun getTypedParameters(): Map<String, @Contextual Any> = mapOf(
        "description" to description,
        "prompt" to prompt,
        "subagent_type" to subagentType
    )
}

/**
 * NotebookEdit工具使用
 * 编辑Jupyter Notebook
 */
@Serializable
@SerialName("notebook_edit_tool_use")
data class NotebookEditToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val notebookPath: String,
    val newSource: String,
    val cellId: String? = null,
    val cellType: String? = null,
    val editMode: String? = null
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.NOTEBOOK_EDIT

    override fun getTypedParameters(): Map<String, @Contextual Any> = buildMap {
        put("notebook_path", notebookPath)
        put("new_source", newSource)
        cellId?.let { put("cell_id", it) }
        cellType?.let { put("cell_type", it) }
        editMode?.let { put("edit_mode", it) }
    }
}

/**
 * MCP工具使用
 * Model Context Protocol工具的通用容器
 */
@Serializable
@SerialName("mcp_tool_use")
data class McpToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val fullToolName: String,  // 完整的工具名称，如 "mcp__server_name__function_name"
    val serverName: String,
    val functionName: String,
    val parameters: Map<String, @Contextual Any>
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.MCP_TOOL

    override fun getTypedParameters(): Map<String, Any> = parameters
}

/**
 * BashOutput工具使用
 * 获取Bash命令的输出
 */
@Serializable
@SerialName("bash_output_tool_use")
data class BashOutputToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val bashId: String,
    val filter: String? = null
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.BASH_OUTPUT

    override fun getTypedParameters(): Map<String, @Contextual Any> = buildMap {
        put("bash_id", bashId)
        filter?.let { put("filter", it) }
    }
}

/**
 * KillShell工具使用
 * 终止运行中的Shell进程
 */
@Serializable
@SerialName("kill_shell_tool_use")
data class KillShellToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val shellId: String
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.KILL_SHELL

    override fun getTypedParameters(): Map<String, @Contextual Any> = mapOf(
        "shell_id" to shellId
    )
}

/**
 * ExitPlanMode工具使用
 * 退出计划模式
 */
@Serializable
@SerialName("exit_plan_mode_tool_use")
data class ExitPlanModeToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val plan: String
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.EXIT_PLAN_MODE

    override fun getTypedParameters(): Map<String, @Contextual Any> = mapOf(
        "plan" to plan
    )
}

/**
 * ListMcpResources工具使用
 * 列出MCP服务器的可用资源
 */
@Serializable
@SerialName("list_mcp_resources_tool_use")
data class ListMcpResourcesToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val server: String? = null
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.LIST_MCP_RESOURCES

    override fun getTypedParameters(): Map<String, @Contextual Any> = buildMap {
        server?.let { put("server", it) }
    }
}

/**
 * ReadMcpResource工具使用
 * 读取指定的MCP资源
 */
@Serializable
@SerialName("read_mcp_resource_tool_use")
data class ReadMcpResourceToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val server: String,
    val uri: String
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.READ_MCP_RESOURCE

    override fun getTypedParameters(): Map<String, @Contextual Any> = mapOf(
        "server" to server,
        "uri" to uri
    )
}

/**
 * 未知工具使用
 * 用于处理不认识的工具类型
 */
@Serializable
@SerialName("unknown_tool_use")
data class UnknownToolUse(
    override val id: String,
    override val name: String,
    override val input: JsonElement,
    val toolName: String,
    val parameters: Map<String, @Contextual Any>
) : SpecificToolUse {
    @kotlinx.serialization.Transient
    override val toolType = ToolType.UNKNOWN

    override fun getTypedParameters(): Map<String, Any> = parameters
}