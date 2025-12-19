package com.asakii.plugin.hooks

import com.asakii.claude.agent.sdk.builders.HookBuilder
import com.asakii.claude.agent.sdk.builders.hookBuilder
import com.asakii.claude.agent.sdk.types.HookEvent
import com.asakii.claude.agent.sdk.types.HookMatcher
import com.asakii.claude.agent.sdk.types.ToolType
import com.asakii.plugin.services.IdeaPlatformService
import com.intellij.openapi.project.Project
import mu.KotlinLogging

/**
 * IDEA 文件同步 Hooks
 *
 * 提供 PRE_TOOL_USE 和 POST_TOOL_USE hooks，用于在 Claude 执行文件操作工具前后
 * 同步 IDEA 和磁盘之间的文件状态。
 *
 * - PRE_TOOL_USE: 保存 IDEA 中已修改的文件到磁盘（确保 Claude 读取/编辑最新内容）
 * - POST_TOOL_USE: 刷新磁盘文件到 IDEA（确保 IDEA 显示 Claude 修改后的内容）
 */
object IdeaFileSyncHooks {

    private val logger = KotlinLogging.logger {}

    /**
     * 需要文件同步的工具配置
     *
     * 使用 SDK 的 ToolType 定义，确保与 Claude CLI 一致
     */
    private enum class FileSyncTool(
        val toolType: ToolType,
        val needSaveBeforeUse: Boolean,
        val needRefreshAfterUse: Boolean
    ) {
        READ(ToolType.READ, needSaveBeforeUse = true, needRefreshAfterUse = false),
        WRITE(ToolType.WRITE, needSaveBeforeUse = true, needRefreshAfterUse = true),
        EDIT(ToolType.EDIT, needSaveBeforeUse = true, needRefreshAfterUse = true),
        MULTI_EDIT(ToolType.MULTI_EDIT, needSaveBeforeUse = true, needRefreshAfterUse = true),
        NOTEBOOK_EDIT(ToolType.NOTEBOOK_EDIT, needSaveBeforeUse = true, needRefreshAfterUse = true);

        /** 工具名称（用于 hook matcher） */
        val toolName: String get() = toolType.toolName

        /** 文件路径参数名 */
        val filePathParam: String
            get() = when (toolType) {
                ToolType.NOTEBOOK_EDIT -> "notebook_path"
                else -> "file_path"
            }

        companion object {
            /** 需要执行前保存的工具（正则匹配） */
            val preMatcher: String by lazy {
                entries.filter { it.needSaveBeforeUse }.joinToString("|") { it.toolName }
            }

            /** 需要执行后刷新的工具（正则匹配） */
            val postMatcher: String by lazy {
                entries.filter { it.needRefreshAfterUse }.joinToString("|") { it.toolName }
            }

            /** 根据工具名获取配置 */
            fun fromToolName(toolName: String): FileSyncTool? {
                return entries.find { it.toolName == toolName }
            }
        }
    }

    /**
     * 创建 IDEA 文件同步 hooks
     *
     * @param project IDEA 项目实例
     * @return hooks 配置 Map
     */
    fun create(project: Project): Map<HookEvent, List<HookMatcher>> {
        val platformService = IdeaPlatformService(project)

        return hookBuilder {
            // PRE_TOOL_USE: 保存 IDEA 文件到磁盘
            onPreToolUse(FileSyncTool.preMatcher) { toolCall ->
                logger.info { "📥 [PRE] ${toolCall.toolName}: 保存 IDEA 文件到磁盘" }
                platformService.saveAllDocuments()
                logger.info { "✅ [PRE] ${toolCall.toolName}: 文件保存完成" }
                allow()
            }

            // POST_TOOL_USE: 刷新磁盘文件到 IDEA
            onPostToolUse(FileSyncTool.postMatcher) { toolCall ->
                val filePath = extractFilePath(toolCall)
                if (filePath != null) {
                    logger.info { "📤 [POST] ${toolCall.toolName}: 刷新文件到 IDEA: $filePath" }
                    platformService.refreshFile(filePath)
                    logger.info { "✅ [POST] ${toolCall.toolName}: 已刷新文件" }
                }
                allow()
            }
        }
    }

    /**
     * 从工具调用中提取文件路径
     */
    private fun extractFilePath(toolCall: HookBuilder.ToolCall): String? {
        val tool = FileSyncTool.fromToolName(toolCall.toolName) ?: return null
        return toolCall.getStringParam(tool.filePathParam).takeIf { it.isNotBlank() }
    }
}
