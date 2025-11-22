package com.claudecodeplus.ui.mappers

import com.claudecodeplus.sdk.types.*
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.ui.viewmodels.tool.*

/**
 * 工具调用转换器
 *
 * 负责将 SDK 层的工具调用转换为 UI 层的 ViewModel。
 * 这是整个架构中唯一依赖 SDK 类型的地方，UI 层通过这个 Mapper 与 SDK 解耦。
 *
 * 职责：
 * 1. SDK ToolUse → UI ToolCallViewModel 转换
 * 2. SDK SpecificToolUse → UI ToolDetailViewModel 转换
 * 3. SDK ToolType → UI UiToolType 转换
 *
 * 设计原则：
 * - 单向转换：只从 SDK → UI，不支持反向转换
 * - 兜底策略：无法识别的类型映射到 GenericToolDetail
 * - 空安全：处理所有可能的 null 情况
 */
object ToolCallMapper {

    /**
     * 从 SDK 工具调用转换为 ViewModel
     *
     * 这是主要的转换入口，接收 SDK 工具调用的所有状态信息，
     * 返回完整的 UI ViewModel。
     *
     * @param id 工具调用唯一标识符
     * @param name 工具名称（如 "Edit", "Read", "Bash"）
     * @param sdkTool SDK 工具实例（可能为 null）
     * @param status 执行状态
     * @param result 执行结果
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 转换后的 ViewModel
     */
    fun fromSdkToolUse(
        id: String,
        name: String,
        sdkTool: SpecificToolUse?,
        status: ToolCallStatus,
        result: ToolResult?,
        startTime: Long,
        endTime: Long?
    ): ToolCallViewModel {
        // 转换工具详情，如果 sdkTool 为 null 则使用 GenericToolDetail
        val toolDetail = sdkTool?.let { toToolDetail(it) }
            ?: GenericToolDetail(UiToolType.UNKNOWN, emptyMap())

        return ToolCallViewModel(
            id = id,
            name = name,
            toolDetail = toolDetail,
            status = status,
            result = result,
            startTime = startTime,
            endTime = endTime
        )
    }

    /**
     * 将 SDK 工具转换为 ToolDetailViewModel
     *
     * 根据 SDK 工具的具体类型，创建对应的 ToolDetailViewModel 子类。
     * 使用 when 表达式确保类型安全，未匹配的类型将被映射到 GenericToolDetail。
     *
     * @param sdkTool SDK 工具实例
     * @return 对应的 ToolDetailViewModel
     */
    private fun toToolDetail(sdkTool: SpecificToolUse): ToolDetailViewModel {
        return when (sdkTool) {
            // 文件操作类（5种）
            is BashToolUse -> BashToolDetail(
                command = sdkTool.command,
                description = sdkTool.description,
                timeout = sdkTool.timeout,
                runInBackground = sdkTool.runInBackground
            )

            is EditToolUse -> EditToolDetail(
                filePath = sdkTool.filePath,
                oldString = sdkTool.oldString,
                newString = sdkTool.newString,
                replaceAll = sdkTool.replaceAll
            )

            is MultiEditToolUse -> MultiEditToolDetail(
                filePath = sdkTool.filePath,
                edits = sdkTool.edits.map { edit ->
                    MultiEditToolDetail.EditOperationVm(
                        oldString = edit.oldString,
                        newString = edit.newString,
                        replaceAll = edit.replaceAll
                    )
                }
            )

            is ReadToolUse -> ReadToolDetail(
                filePath = sdkTool.filePath,
                offset = sdkTool.offset,
                limit = sdkTool.limit
            )

            is WriteToolUse -> WriteToolDetail(
                filePath = sdkTool.filePath,
                content = sdkTool.content
            )

            // 搜索类（2种）
            is GlobToolUse -> GlobToolDetail(
                pattern = sdkTool.pattern,
                path = sdkTool.path
            )

            is GrepToolUse -> GrepToolDetail(
                pattern = sdkTool.pattern,
                path = sdkTool.path,
                glob = sdkTool.glob,
                outputMode = sdkTool.outputMode
            )

            // 任务管理类（1种）
            is TodoWriteToolUse -> TodoWriteToolDetail(
                todos = sdkTool.todos.map { todo ->
                    TodoWriteToolDetail.TodoItemVm(
                        content = todo.content,
                        status = todo.status,
                        activeForm = todo.activeForm
                    )
                }
            )

            // 任务和代理类（1种）
            is TaskToolUse -> TaskToolDetail(
                description = sdkTool.description,
                prompt = sdkTool.prompt,
                subagentType = sdkTool.subagentType
            )

            // 网络类（2种）
            is WebFetchToolUse -> WebFetchToolDetail(
                url = sdkTool.url,
                prompt = sdkTool.prompt
            )

            is WebSearchToolUse -> WebSearchToolDetail(
                query = sdkTool.query,
                allowedDomains = sdkTool.allowedDomains,
                blockedDomains = sdkTool.blockedDomains
            )

            // Notebook 编辑（1种）
            is NotebookEditToolUse -> NotebookEditToolDetail(
                notebookPath = sdkTool.notebookPath,
                cellId = sdkTool.cellId,
                newSource = sdkTool.newSource,
                cellType = sdkTool.cellType,
                editMode = sdkTool.editMode
            )

            // MCP 工具（3种）
            is McpToolUse -> McpToolDetail(
                serverName = sdkTool.serverName,
                toolName = sdkTool.functionName,
                arguments = sdkTool.parameters
            )

            is ListMcpResourcesToolUse -> ListMcpResourcesToolDetail(
                server = sdkTool.server
            )

            is ReadMcpResourceToolUse -> ReadMcpResourceToolDetail(
                server = sdkTool.server,
                uri = sdkTool.uri
            )

            // 命令执行辅助工具（2种）
            is BashOutputToolUse -> BashOutputToolDetail(
                bashId = sdkTool.bashId,
                filter = sdkTool.filter
            )

            is KillShellToolUse -> KillShellToolDetail(
                shellId = sdkTool.shellId
            )

            // 其他工具（1种）
            is ExitPlanModeToolUse -> ExitPlanModeToolDetail(
                plan = sdkTool.plan
            )

            // 未知工具（兜底）
            is UnknownToolUse -> GenericToolDetail(
                toolType = UiToolType.UNKNOWN,
                parameters = sdkTool.getTypedParameters()
            )
        }
    }

    /**
     * 映射工具类型枚举
     *
     * 将 SDK 的 ToolType 枚举映射到 UI 层的 UiToolType 枚举。
     * 这样做的目的是避免 UI 层直接依赖 SDK 枚举。
     *
     * @param sdkType SDK 工具类型
     * @return UI 工具类型
     */
    private fun mapToolType(sdkType: ToolType): UiToolType {
        return when (sdkType) {
            ToolType.BASH -> UiToolType.BASH
            ToolType.EDIT -> UiToolType.EDIT
            ToolType.MULTI_EDIT -> UiToolType.MULTI_EDIT
            ToolType.READ -> UiToolType.READ
            ToolType.WRITE -> UiToolType.WRITE
            ToolType.GLOB -> UiToolType.GLOB
            ToolType.GREP -> UiToolType.GREP
            ToolType.TODO_WRITE -> UiToolType.TODO_WRITE
            ToolType.TASK -> UiToolType.TASK
            ToolType.WEB_FETCH -> UiToolType.WEB_FETCH
            ToolType.WEB_SEARCH -> UiToolType.WEB_SEARCH
            ToolType.NOTEBOOK_EDIT -> UiToolType.NOTEBOOK_EDIT
            ToolType.MCP_TOOL -> UiToolType.MCP
            ToolType.BASH_OUTPUT -> UiToolType.BASH_OUTPUT
            ToolType.KILL_SHELL -> UiToolType.KILL_SHELL
            ToolType.EXIT_PLAN_MODE -> UiToolType.EXIT_PLAN_MODE
            ToolType.LIST_MCP_RESOURCES -> UiToolType.LIST_MCP_RESOURCES
            ToolType.READ_MCP_RESOURCE -> UiToolType.READ_MCP_RESOURCE
            ToolType.UNKNOWN -> UiToolType.UNKNOWN
        }
    }
}
