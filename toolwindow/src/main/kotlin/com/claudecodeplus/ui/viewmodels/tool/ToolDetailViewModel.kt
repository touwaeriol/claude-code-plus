package com.claudecodeplus.ui.viewmodels.tool

/**
 * 工具详情基类
 *
 * 使用密封类确保类型安全，每种工具类型一个子类。
 * 相比运行时 when 判断，密封类提供编译时类型检查，更加安全。
 *
 * 设计原则：
 * 1. 每个子类封装特定工具的显示逻辑
 * 2. 通过抽象方法强制子类实现显示规则
 * 3. 提供统一的参数访问接口
 */
sealed class ToolDetailViewModel {
    /**
     * 工具类型（UI 层枚举）
     */
    abstract val toolType: UiToolType

    /**
     * IDE 集成类型
     * - null: 不需要 IDE 集成（如 Bash, WebFetch）
     * - SHOW_DIFF: 显示文件差异（Edit, MultiEdit）
     * - OPEN_FILE: 打开文件（Read, Write）
     */
    abstract val ideIntegrationType: IdeIntegrationType?

    /**
     * 生成显示副标题
     *
     * 用于紧凑模式下显示工具调用的关键信息。
     * 例如：
     * - Edit: "MyFile.kt (单次替换)"
     * - Read: "config.json (offset: 100, limit: 50)"
     * - Bash: "npm install"
     *
     * @return 副标题文本，如果不需要副标题则返回 null
     */
    abstract fun generateSubtitle(): String?

    /**
     * 获取关键参数
     *
     * 用于展开模式下显示详细参数。
     * 返回的 Map 将被渲染为键值对列表。
     *
     * @return 参数名到参数值的映射
     */
    abstract fun getKeyParameters(): Map<String, Any>

    open fun compactSummary(): String? = generateSubtitle()
}

// ===================================
// 文件操作类工具（5种）
// ===================================

/**
 * Bash 命令执行
 *
 * 用于执行 shell 命令。
 * 示例：`./gradlew build`, `git status`
 */
data class BashToolDetail(
    val command: String,
    val description: String?,
    val timeout: Long?,
    val runInBackground: Boolean
) : ToolDetailViewModel() {
    override val toolType = UiToolType.BASH
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle(): String = previewSnippet(command, maxLength = 60)

    override fun getKeyParameters() = buildMap<String, Any> {
        put("command", command)
        description?.let { put("description", it) }
        timeout?.let { put("timeout", "${it}ms") }
        put("run_in_background", runInBackground)
    }
}

/**
 * 文件编辑（单次或全部替换）
 *
 * 用于修改文件内容。
 * 示例：将 "old text" 替换为 "new text"
 */
data class EditToolDetail(
    val filePath: String,
    val oldString: String,
    val newString: String,
    val replaceAll: Boolean
) : ToolDetailViewModel() {
    override val toolType = UiToolType.EDIT
    override val ideIntegrationType = IdeIntegrationType.SHOW_DIFF

    override fun generateSubtitle(): String {
        val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')
        val oldPreview = previewSnippet(oldString)
        val newPreview = previewSnippet(newString)

        return buildList {
            add(fileName)
            add("\"$oldPreview\" → \"$newPreview\"")
            if (replaceAll) {
                add("replace_all=true")
            }
        }.joinToString(" · ")
    }

    override fun getKeyParameters() = mapOf(
        "file_path" to filePath,
        "old_string" to oldString,
        "new_string" to newString,
        "replace_all" to replaceAll
    )
}

/**
 * 文件批量编辑
 *
 * 用于在同一文件中执行多次替换操作。
 * 示例：同时修改文件中的多个位置
 */
data class MultiEditToolDetail(
    val filePath: String,
    val edits: List<EditOperationVm>
) : ToolDetailViewModel() {
    override val toolType = UiToolType.MULTI_EDIT
    override val ideIntegrationType = IdeIntegrationType.SHOW_DIFF

    override fun generateSubtitle(): String {
        val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')
        val firstEdit = edits.firstOrNull()

        return buildList {
            add(fileName)
            add("${edits.size} 处")
            if (firstEdit != null) {
                val oldPreview = previewSnippet(firstEdit.oldString)
                val newPreview = previewSnippet(firstEdit.newString)
                val scopeTag = if (firstEdit.replaceAll) "replace_all=true" else null
                add("\"$oldPreview\" → \"$newPreview\"")
                scopeTag?.let { add(it) }
            }
        }.joinToString(" · ")
    }

    override fun getKeyParameters() = mapOf(
        "file_path" to filePath,
        "edits" to edits.map { it.toMap() }
    )

    /**
     * 单个编辑操作
     */
    data class EditOperationVm(
        val oldString: String,
        val newString: String,
        val replaceAll: Boolean
    ) {
        fun toMap() = mapOf(
            "old_string" to oldString,
            "new_string" to newString,
            "replace_all" to replaceAll
        )
    }
}

/**
 * 文件读取
 *
 * 用于读取文件内容。
 * 支持可选的偏移量和限制参数以读取文件的部分内容。
 */
data class ReadToolDetail(
    val filePath: String,
    val offset: Int?,
    val limit: Int?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.READ
    override val ideIntegrationType = IdeIntegrationType.OPEN_FILE

    override fun generateSubtitle(): String {
        val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')

        val rangeParts = buildList {
            offset?.let { add("offset=$it") }
            limit?.let { add("limit=$it") }
        }

        return if (rangeParts.isEmpty()) {
            fileName
        } else {
            "$fileName (${rangeParts.joinToString(", ")})"
        }
    }

    override fun getKeyParameters() = buildMap<String, Any> {
        put("file_path", filePath)
        offset?.let { put("offset", it) }
        limit?.let { put("limit", it) }
    }
}

/**
 * 文件写入
 *
 * 用于创建新文件或覆盖现有文件。
 */
data class WriteToolDetail(
    val filePath: String,
    val content: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.WRITE
    override val ideIntegrationType = IdeIntegrationType.OPEN_FILE

    override fun generateSubtitle(): String {
        val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')
        val parts = buildList {
            add(fileName)
            add("${content.length} 字符")
            val snippet = previewSnippet(content)
            if (snippet != "(空)") {
                add("\"$snippet\"")
            }
        }
        return parts.joinToString(" · ")
    }

    override fun getKeyParameters() = mapOf(
        "file_path" to filePath,
        "content" to content
    )
}

// ===================================
// 搜索类工具（2种）
// ===================================

/**
 * 文件模式匹配（Glob）
 *
 * 使用 glob 模式查找文件。
 * 示例：`*.kt`, `src/**/*.java`
 */
data class GlobToolDetail(
    val pattern: String,
    val path: String?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.GLOB
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle(): String = buildList {
        add(pattern)
        path?.let { add("path=$it") }
    }.joinToString(" · ")

    override fun getKeyParameters() = buildMap<String, Any> {
        put("pattern", pattern)
        path?.let { put("path", it) }
    }
}

/**
 * 内容搜索（Grep）
 *
 * 使用正则表达式搜索文件内容。
 * 支持文件过滤和输出模式配置。
 */
data class GrepToolDetail(
    val pattern: String,
    val path: String?,
    val glob: String?,
    val outputMode: String?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.GREP
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = pattern

    override fun getKeyParameters() = buildMap<String, Any> {
        put("pattern", pattern)
        path?.let { put("path", it) }
        glob?.let { put("glob", it) }
        outputMode?.let { put("output_mode", it) }
    }
}

// ===================================
// 任务管理类工具（1种）
// ===================================

/**
 * 待办事项管理
 *
 * 管理任务列表，跟踪任务状态。
 */
data class TodoWriteToolDetail(
    val todos: List<TodoItemVm>
) : ToolDetailViewModel() {
    override val toolType = UiToolType.TODO_WRITE
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle(): String {
        val completed = todos.count { it.status.equals("completed", ignoreCase = true) }
        return "$completed / ${todos.size} 已完成"
    }

    override fun getKeyParameters() = mapOf(
        "todos" to todos.map { it.toMap() }
    )

    /**
     * 单个待办事项
     */
    data class TodoItemVm(
        val content: String,
        val status: String,
        val activeForm: String
    ) {
        fun toMap() = mapOf(
            "content" to content,
            "status" to status,
            "activeForm" to activeForm
        )
    }
}

// ===================================
// 任务和代理类工具（1种）
// ===================================

/**
 * 子任务代理
 *
 * 创建子任务让 AI 代理执行。
 * 示例：规划任务、代码审查、测试生成等
 */
data class TaskToolDetail(
    val description: String,
    val prompt: String,
    val subagentType: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.TASK
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = description

    override fun getKeyParameters() = mapOf(
        "description" to description,
        "prompt" to prompt,
        "subagent_type" to subagentType
    )
}

// ===================================
// 网络类工具（2种）
// ===================================

/**
 * 网页抓取
 *
 * 从指定 URL 获取内容并使用 AI 处理。
 */
data class WebFetchToolDetail(
    val url: String,
    val prompt: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.WEB_FETCH
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = url

    override fun getKeyParameters() = mapOf(
        "url" to url,
        "prompt" to prompt
    )
}

/**
 * 网络搜索
 *
 * 使用搜索引擎查找信息。
 */
data class WebSearchToolDetail(
    val query: String,
    val allowedDomains: List<String>?,
    val blockedDomains: List<String>?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.WEB_SEARCH
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = query

    override fun getKeyParameters() = buildMap<String, Any> {
        put("query", query)
        allowedDomains?.let { if (it.isNotEmpty()) put("allowed_domains", it) }
        blockedDomains?.let { if (it.isNotEmpty()) put("blocked_domains", it) }
    }
}

// ===================================
// Notebook 编辑工具（1种）
// ===================================

/**
 * Jupyter Notebook 编辑
 *
 * 编辑 Jupyter Notebook 的单元格。
 */
data class NotebookEditToolDetail(
    val notebookPath: String,
    val cellId: String?,
    val newSource: String,
    val cellType: String?,
    val editMode: String?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.NOTEBOOK_EDIT
    override val ideIntegrationType = IdeIntegrationType.OPEN_FILE

    override fun generateSubtitle(): String {
        val fileName = notebookPath.substringAfterLast('/').substringAfterLast('\\')
        val mode = editMode ?: "编辑"
        return "$fileName ($mode)"
    }

    override fun getKeyParameters() = buildMap<String, Any> {
        put("notebook_path", notebookPath)
        cellId?.let { put("cell_id", it) }
        put("new_source", newSource)
        cellType?.let { put("cell_type", it) }
        editMode?.let { put("edit_mode", it) }
    }
}

// ===================================
// MCP 工具（3种）
// ===================================

/**
 * MCP 工具调用
 *
 * 调用 Model Context Protocol 服务器的工具。
 */
data class McpToolDetail(
    val serverName: String,
    val toolName: String,
    val arguments: Map<String, Any>
) : ToolDetailViewModel() {
    override val toolType = UiToolType.MCP
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = "$serverName::$toolName"

    override fun getKeyParameters() = buildMap<String, Any> {
        put("server_name", serverName)
        put("tool_name", toolName)
        put("arguments", arguments)
    }
}

/**
 * 列出 MCP 资源
 *
 * 查询 MCP 服务器提供的资源列表。
 */
data class ListMcpResourcesToolDetail(
    val server: String?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.LIST_MCP_RESOURCES
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = server ?: "所有服务器"

    override fun getKeyParameters() = buildMap<String, Any> {
        server?.let { put("server", it) }
    }
}

/**
 * 读取 MCP 资源
 *
 * 从 MCP 服务器读取指定资源。
 */
data class ReadMcpResourceToolDetail(
    val server: String,
    val uri: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.READ_MCP_RESOURCE
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = uri

    override fun getKeyParameters() = mapOf(
        "server" to server,
        "uri" to uri
    )
}

// ===================================
// 命令执行辅助工具（2种）
// ===================================

/**
 * 获取后台 Bash 输出
 *
 * 获取后台运行的 Shell 进程的输出。
 */
data class BashOutputToolDetail(
    val bashId: String,
    val filter: String?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.BASH_OUTPUT
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = "Shell: $bashId"

    override fun getKeyParameters() = buildMap<String, Any> {
        put("bash_id", bashId)
        filter?.let { put("filter", it) }
    }
}

/**
 * 终止后台 Shell 进程
 *
 * 杀死指定的后台 Shell 进程。
 */
data class KillShellToolDetail(
    val shellId: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.KILL_SHELL
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = "Shell: $shellId"

    override fun getKeyParameters() = mapOf(
        "shell_id" to shellId
    )
}

// ===================================
// 其他工具（2种）
// ===================================

/**
 * 退出计划模式
 *
 * 退出计划模式，准备执行任务。
 */
data class ExitPlanModeToolDetail(
    val plan: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.EXIT_PLAN_MODE
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = "退出计划模式"

    override fun getKeyParameters() = mapOf(
        "plan" to plan
    )
}

/**
 * 自定义斜杠命令
 *
 * 执行用户定义的斜杠命令。
 */
data class SlashCommandToolDetail(
    val command: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.SLASH_COMMAND
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = command

    override fun getKeyParameters() = mapOf(
        "command" to command
    )
}

// ===================================
// 通用工具（兜底）
// ===================================

/**
 * 通用工具详情
 *
 * 用于无法识别的工具类型或未实现专用 ViewModel 的工具。
 * 提供基本的参数显示功能。
 */
data class GenericToolDetail(
    override val toolType: UiToolType,
    val parameters: Map<String, Any>
) : ToolDetailViewModel() {
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle(): String? = formatParameterSummary(parameters)

    override fun getKeyParameters() = parameters
}

private fun previewSnippet(raw: String, maxLength: Int = 24): String {
    if (raw.isEmpty()) {
        return "(空)"
    }

    val normalized = raw
        .replace("\r\n", " ")
        .replace("\n", " ")
        .replace("\t", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    if (normalized.isEmpty()) {
        return "(空)"
    }

    return if (normalized.length <= maxLength) {
        normalized
    } else {
        normalized.take(maxLength - 1) + "…"
    }
}

private fun formatParameterSummary(
    parameters: Map<String, Any>,
    maxItems: Int = 2,
    valueMaxLength: Int = 18
): String? {
    if (parameters.isEmpty()) {
        return null
    }

    val entries = parameters.entries.take(maxItems)
    val summary = entries.joinToString(", ") { (key, value) ->
        val snippet = previewSnippet(value.toString(), valueMaxLength)
        "$key=$snippet"
    }

    return buildString {
        append(summary)
        if (parameters.size > maxItems) {
            append(", …")
        }
    }.takeIf { it.isNotEmpty() }
}
