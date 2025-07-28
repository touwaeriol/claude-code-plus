package com.claudecodeplus.sdk

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 工具基类 - 所有工具类型的抽象基类
 */
sealed class Tool(
    open val name: String,
    open val icon: String,
    open val description: String
) {
    /**
     * 判断该工具的输出是否应该限制高度
     */
    open fun shouldLimitHeight(): Boolean = false
    
    /**
     * 获取工具类别
     */
    abstract fun getCategory(): ToolCategory
}

/**
 * 工具类别枚举
 */
enum class ToolCategory {
    FILE_OPERATION,     // 文件操作类
    FILE_SYSTEM,        // 文件系统类
    TERMINAL,           // 终端命令类
    GIT,                // Git 操作类
    TASK_MANAGEMENT,    // 任务管理类
    WEB,                // 网络相关类
    NOTEBOOK,           // Jupyter 笔记本类
    SPECIAL,            // 特殊工具类
    OTHER               // 其他（包括 MCP 和未知工具）
}

// === 文件操作工具 ===

data class ReadTool(
    val filePath: String? = null,
    val offset: Int? = null,
    val limit: Int? = null
) : Tool(
    name = "Read",
    icon = "📖",
    description = "读取文件内容"
) {
    override fun getCategory() = ToolCategory.FILE_OPERATION
}

data class WriteTool(
    val filePath: String? = null,
    val content: String? = null
) : Tool(
    name = "Write",
    icon = "✏️",
    description = "写入文件"
) {
    override fun getCategory() = ToolCategory.FILE_OPERATION
}

data class EditTool(
    val filePath: String? = null,
    val oldString: String? = null,
    val newString: String? = null,
    val replaceAll: Boolean = false
) : Tool(
    name = "Edit",
    icon = "✏️",
    description = "编辑文件"
) {
    override fun getCategory() = ToolCategory.FILE_OPERATION
}

data class MultiEditTool(
    val filePath: String? = null,
    val edits: List<Edit>? = null
) : Tool(
    name = "MultiEdit",
    icon = "✏️",
    description = "批量编辑文件"
) {
    override fun getCategory() = ToolCategory.FILE_OPERATION
    
    data class Edit(
        val oldString: String,
        val newString: String,
        val replaceAll: Boolean = false
    )
}

// === 文件系统工具 ===

data class LSTool(
    val path: String? = null,
    val ignore: List<String>? = null
) : Tool(
    name = "LS",
    icon = "📁",
    description = "列出目录内容"
) {
    override fun getCategory() = ToolCategory.FILE_SYSTEM
}

data class GlobTool(
    val pattern: String? = null,
    val path: String? = null
) : Tool(
    name = "Glob",
    icon = "🔍",
    description = "文件模式匹配"
) {
    override fun getCategory() = ToolCategory.FILE_SYSTEM
}

data class GrepTool(
    val pattern: String? = null,
    val path: String? = null,
    val glob: String? = null,
    val type: String? = null,
    val outputMode: String? = null
) : Tool(
    name = "Grep",
    icon = "🔍",
    description = "搜索文件内容"
) {
    override fun getCategory() = ToolCategory.FILE_SYSTEM
}

// === 终端工具 ===

data class BashTool(
    val command: String? = null,
    val commandDescription: String? = null,
    val timeout: Long? = null
) : Tool(
    name = "Bash",
    icon = "💻",
    description = "执行终端命令"
) {
    override fun getCategory() = ToolCategory.TERMINAL
}

// === 任务管理工具 ===

data class TaskTool(
    val taskDescription: String? = null,
    val prompt: String? = null,
    val subagentType: String? = null
) : Tool(
    name = "Task",
    icon = "🤖",
    description = "任务管理"
) {
    override fun getCategory() = ToolCategory.TASK_MANAGEMENT
}

data class TodoWriteTool(
    val todos: List<Any>? = null
) : Tool(
    name = "TodoWrite",
    icon = "📋",
    description = "待办事项管理"
) {
    override fun getCategory() = ToolCategory.TASK_MANAGEMENT
}

// === Web 工具 ===

data class WebFetchTool(
    val url: String? = null,
    val prompt: String? = null
) : Tool(
    name = "WebFetch",
    icon = "🌐",
    description = "获取网页内容"
) {
    override fun getCategory() = ToolCategory.WEB
    override fun shouldLimitHeight() = true
}

data class WebSearchTool(
    val query: String? = null,
    val allowedDomains: List<String>? = null,
    val blockedDomains: List<String>? = null
) : Tool(
    name = "WebSearch",
    icon = "🌐",
    description = "网络搜索"
) {
    override fun getCategory() = ToolCategory.WEB
    override fun shouldLimitHeight() = true
}

// === Jupyter 笔记本工具 ===

data class NotebookReadTool(
    val notebookPath: String? = null,
    val cellId: String? = null
) : Tool(
    name = "NotebookRead",
    icon = "📓",
    description = "读取 Jupyter 笔记本"
) {
    override fun getCategory() = ToolCategory.NOTEBOOK
}

data class NotebookEditTool(
    val notebookPath: String? = null,
    val cellId: String? = null,
    val newSource: String? = null,
    val editMode: String? = null
) : Tool(
    name = "NotebookEdit",
    icon = "📓",
    description = "编辑 Jupyter 笔记本"
) {
    override fun getCategory() = ToolCategory.NOTEBOOK
}

// === 特殊工具 ===

data class ExitPlanModeTool(
    val plan: String? = null
) : Tool(
    name = "ExitPlanMode",
    icon = "🔧",
    description = "退出计划模式"
) {
    override fun getCategory() = ToolCategory.SPECIAL
}

// === 其他工具（包括 MCP 和未知工具） ===

data class McpTool(
    val mcpName: String,
    val namespace: String? = null,
    val method: String? = null,
    val parameters: Map<String, Any>? = null
) : Tool(
    name = mcpName,
    icon = "🔌",
    description = "MCP 扩展工具"
) {
    override fun getCategory() = ToolCategory.OTHER
}

data class UnknownTool(
    val toolName: String,
    val parameters: Map<String, Any>? = null
) : Tool(
    name = toolName,
    icon = "🔧",
    description = "未识别的工具"
) {
    override fun getCategory() = ToolCategory.OTHER
}

/**
 * 工具解析器 - 将工具名称和参数解析为具体的工具类型
 */
object ToolParser {
    private val logger = org.slf4j.LoggerFactory.getLogger(ToolParser::class.java)
    
    /**
     * 解析工具
     * @param name 工具名称
     * @param input 工具输入参数（JsonObject）
     * @return 具体的工具实例
     */
    fun parse(name: String, input: JsonObject): Tool {
        return try {
            when {
                // 文件操作工具
                name.equals("Read", ignoreCase = true) -> parseReadTool(input)
                name.equals("Write", ignoreCase = true) -> parseWriteTool(input)
                name.equals("Edit", ignoreCase = true) -> parseEditTool(input)
                name.equals("MultiEdit", ignoreCase = true) -> parseMultiEditTool(input)
                
                // 文件系统工具
                name.equals("LS", ignoreCase = true) -> parseLSTool(input)
                name.equals("Glob", ignoreCase = true) -> parseGlobTool(input)
                name.equals("Grep", ignoreCase = true) -> parseGrepTool(input)
                
                // 终端工具
                name.equals("Bash", ignoreCase = true) -> parseBashTool(input)
                
                // 任务管理工具
                name.equals("Task", ignoreCase = true) -> parseTaskTool(input)
                name.equals("TodoWrite", ignoreCase = true) -> parseTodoWriteTool(input)
                
                // Web 工具
                name.equals("WebFetch", ignoreCase = true) -> parseWebFetchTool(input)
                name.equals("WebSearch", ignoreCase = true) -> parseWebSearchTool(input)
                
                // Jupyter 笔记本工具
                name.equals("NotebookRead", ignoreCase = true) -> parseNotebookReadTool(input)
                name.equals("NotebookEdit", ignoreCase = true) -> parseNotebookEditTool(input)
                
                // 特殊工具
                name.equals("ExitPlanMode", ignoreCase = true) -> parseExitPlanModeTool(input)
                
                // MCP 工具
                name.startsWith("mcp_", ignoreCase = true) || 
                name.startsWith("mcp__", ignoreCase = true) -> parseMcpTool(name, input)
                
                // 未知工具
                else -> {
                    logger.warn("Unknown tool: $name - will be displayed as-is")
                    parseUnknownTool(name, input)
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing tool $name: ${e.message}")
            UnknownTool(name = name, parameters = input.toMap())
        }
    }
    
    // === 解析辅助方法 ===
    
    private fun parseReadTool(input: JsonObject): ReadTool {
        return ReadTool(
            filePath = input["file_path"]?.jsonPrimitive?.contentOrNull,
            offset = input["offset"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
            limit = input["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        )
    }
    
    private fun parseWriteTool(input: JsonObject): WriteTool {
        return WriteTool(
            filePath = input["file_path"]?.jsonPrimitive?.contentOrNull,
            content = input["content"]?.jsonPrimitive?.contentOrNull
        )
    }
    
    private fun parseEditTool(input: JsonObject): EditTool {
        return EditTool(
            filePath = input["file_path"]?.jsonPrimitive?.contentOrNull,
            oldString = input["old_string"]?.jsonPrimitive?.contentOrNull,
            newString = input["new_string"]?.jsonPrimitive?.contentOrNull,
            replaceAll = input["replace_all"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
        )
    }
    
    private fun parseMultiEditTool(input: JsonObject): MultiEditTool {
        // TODO: 实现 MultiEdit 的解析逻辑
        return MultiEditTool(
            filePath = input["file_path"]?.jsonPrimitive?.contentOrNull
        )
    }
    
    private fun parseLSTool(input: JsonObject): LSTool {
        return LSTool(
            path = input["path"]?.jsonPrimitive?.contentOrNull
        )
    }
    
    private fun parseGlobTool(input: JsonObject): GlobTool {
        return GlobTool(
            pattern = input["pattern"]?.jsonPrimitive?.contentOrNull,
            path = input["path"]?.jsonPrimitive?.contentOrNull
        )
    }
    
    private fun parseGrepTool(input: JsonObject): GrepTool {
        return GrepTool(
            pattern = input["pattern"]?.jsonPrimitive?.contentOrNull,
            path = input["path"]?.jsonPrimitive?.contentOrNull,
            glob = input["glob"]?.jsonPrimitive?.contentOrNull,
            type = input["type"]?.jsonPrimitive?.contentOrNull,
            outputMode = input["output_mode"]?.jsonPrimitive?.contentOrNull
        )
    }
    
    private fun parseBashTool(input: JsonObject): BashTool {
        return BashTool(
            command = input["command"]?.jsonPrimitive?.contentOrNull,
            commandDescription = input["description"]?.jsonPrimitive?.contentOrNull,
            timeout = input["timeout"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        )
    }
    
    private fun parseTaskTool(input: JsonObject): TaskTool {
        return TaskTool(
            taskDescription = input["description"]?.jsonPrimitive?.contentOrNull,
            prompt = input["prompt"]?.jsonPrimitive?.contentOrNull,
            subagentType = input["subagent_type"]?.jsonPrimitive?.contentOrNull
        )
    }
    
    private fun parseTodoWriteTool(input: JsonObject): TodoWriteTool {
        // TODO: 实现 TodoWrite 的解析逻辑
        return TodoWriteTool()
    }
    
    private fun parseWebFetchTool(input: JsonObject): WebFetchTool {
        return WebFetchTool(
            url = input["url"]?.jsonPrimitive?.contentOrNull,
            prompt = input["prompt"]?.jsonPrimitive?.contentOrNull
        )
    }
    
    private fun parseWebSearchTool(input: JsonObject): WebSearchTool {
        return WebSearchTool(
            query = input["query"]?.jsonPrimitive?.contentOrNull
        )
    }
    
    private fun parseNotebookReadTool(input: JsonObject): NotebookReadTool {
        return NotebookReadTool(
            notebookPath = input["notebook_path"]?.jsonPrimitive?.contentOrNull,
            cellId = input["cell_id"]?.jsonPrimitive?.contentOrNull
        )
    }
    
    private fun parseNotebookEditTool(input: JsonObject): NotebookEditTool {
        return NotebookEditTool(
            notebookPath = input["notebook_path"]?.jsonPrimitive?.contentOrNull,
            cellId = input["cell_id"]?.jsonPrimitive?.contentOrNull,
            newSource = input["new_source"]?.jsonPrimitive?.contentOrNull,
            editMode = input["edit_mode"]?.jsonPrimitive?.contentOrNull
        )
    }
    
    private fun parseExitPlanModeTool(input: JsonObject): ExitPlanModeTool {
        return ExitPlanModeTool(
            plan = input["plan"]?.jsonPrimitive?.contentOrNull
        )
    }
    
    private fun parseMcpTool(name: String, input: JsonObject): McpTool {
        // 从名称中提取命名空间和方法
        val parts = name.removePrefix("mcp_").removePrefix("mcp__").split("__", "_")
        val namespace = if (parts.size > 1) parts[0] else null
        val method = if (parts.size > 1) parts.drop(1).joinToString("_") else parts[0]
        
        return McpTool(
            mcpName = name,
            namespace = namespace,
            method = method,
            parameters = input.toMap()
        )
    }
    
    private fun parseUnknownTool(name: String, input: JsonObject): UnknownTool {
        return UnknownTool(
            toolName = name,
            parameters = input.toMap()
        )
    }
}

// 扩展函数：将 JsonObject 转换为 Map
private fun JsonObject.toMap(): Map<String, Any> {
    return this.entries.associate { (key, value) ->
        key to when {
            value.isJsonPrimitive -> value.jsonPrimitive.contentOrNull ?: value.toString()
            value.isJsonArray -> value.toString()
            value.isJsonObject -> value.toString()
            else -> value.toString()
        }
    }
}

// JsonElement 扩展属性
private val kotlinx.serialization.json.JsonElement.isJsonPrimitive: Boolean
    get() = this is kotlinx.serialization.json.JsonPrimitive

private val kotlinx.serialization.json.JsonElement.isJsonArray: Boolean
    get() = this is kotlinx.serialization.json.JsonArray

private val kotlinx.serialization.json.JsonElement.isJsonObject: Boolean
    get() = this is kotlinx.serialization.json.JsonObject