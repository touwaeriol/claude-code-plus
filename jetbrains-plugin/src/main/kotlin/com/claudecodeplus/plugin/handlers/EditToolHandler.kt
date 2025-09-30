package com.claudecodeplus.plugin.handlers

import com.claudecodeplus.plugin.services.IdeaPlatformService
import com.claudecodeplus.sdk.types.EditToolUse
import com.claudecodeplus.sdk.types.MultiEditToolUse
import com.claudecodeplus.ui.models.ToolCall
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Edit 工具点击处理器
 * 使用 IDEA 的 Diff 视图显示文件变更
 *
 * 使用 IdeaPlatformService 统一服务，简化实现
 */
class EditToolHandler : ToolClickHandler {

    companion object {
        private val logger = Logger.getInstance(EditToolHandler::class.java)
    }

    override fun canHandle(toolCall: ToolCall): Boolean {
        // 只有 SUCCESS 状态才能显示 diff
        // RUNNING/FAILED 状态不处理
        val specificTool = toolCall.specificTool
        val isEditTool = specificTool is EditToolUse || specificTool is MultiEditToolUse
        return isEditTool && toolCall.status == com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS
    }

    override fun handleToolClick(
        toolCall: ToolCall,
        project: Project?,
        config: ToolClickConfig
    ): Boolean {
        if (project == null) {
            logger.info("EditToolHandler: Project is null, 无法使用 IDE 集成")
            return false
        }

        if (config.alwaysExpand) {
            logger.info("EditToolHandler: 配置为总是展开，跳过 IDE 集成")
            return false
        }

        return try {
            when (val specificTool = toolCall.specificTool) {
                is EditToolUse -> handleSingleEdit(specificTool, project, config)
                is MultiEditToolUse -> handleMultiEdit(specificTool, project, config)
                else -> {
                    logger.warn("EditToolHandler: specificTool 类型不支持")
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("EditToolHandler: 处理失败", e)
            if (config.fallbackBehavior == FallbackBehavior.SHOW_ERROR) {
                IdeaPlatformService(project).showError("显示差异失败: ${e.message}")
            }
            config.fallbackBehavior == FallbackBehavior.EXPAND
        }
    }

    private fun handleSingleEdit(
        editTool: EditToolUse,
        project: Project,
        config: ToolClickConfig
    ): Boolean {
        val platformService = IdeaPlatformService(project)

        // 显示差异
        val success = platformService.showDiff(
            filePath = editTool.filePath,
            oldContent = editTool.oldString,
            newContent = editTool.newString
        )

        if (success && config.showNotifications) {
            val fileName = editTool.filePath.substringAfterLast('/').substringAfterLast('\\')
            platformService.showInfo("已显示文件差异: $fileName")
        }

        return success
    }

    private fun handleMultiEdit(
        multiEditTool: MultiEditToolUse,
        project: Project,
        config: ToolClickConfig
    ): Boolean {
        val platformService = IdeaPlatformService(project)

        // MultiEdit: 合并所有变更显示
        if (multiEditTool.edits.isEmpty()) {
            logger.warn("EditToolHandler: MultiEdit 没有编辑内容")
            return false
        }

        // 简单处理：显示第一个编辑的 diff
        // 未来可以扩展为显示所有编辑的合并 diff
        val firstEdit = multiEditTool.edits.first()
        val success = platformService.showDiff(
            filePath = multiEditTool.filePath,
            oldContent = firstEdit.oldString,
            newContent = firstEdit.newString,
            title = "文件变更: ${multiEditTool.filePath} (${multiEditTool.edits.size} 处修改)"
        )

        if (success && config.showNotifications) {
            val fileName = multiEditTool.filePath.substringAfterLast('/').substringAfterLast('\\')
            platformService.showInfo("已显示文件差异: $fileName (${multiEditTool.edits.size} 处修改)")
        }

        return success
    }
}