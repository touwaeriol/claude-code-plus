package com.claudecodeplus.plugin.handlers

import com.claudecodeplus.plugin.services.IdeaPlatformService
import com.claudecodeplus.sdk.types.WriteToolUse
import com.claudecodeplus.ui.models.ToolCall
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Write 工具点击处理器
 * 在 IDEA 编辑器中打开刚写入的文件
 *
 * 使用 IdeaPlatformService 统一服务，简化实现
 */
class WriteToolHandler : ToolClickHandler {

    companion object {
        private val logger = Logger.getInstance(WriteToolHandler::class.java)
    }

    override fun canHandle(toolCall: ToolCall): Boolean {
        // 只有 SUCCESS 状态才能打开文件
        // RUNNING/FAILED 状态不处理
        return toolCall.specificTool is WriteToolUse &&
            toolCall.status == com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS
    }

    override fun handleToolClick(
        toolCall: ToolCall,
        project: Project?,
        config: ToolClickConfig
    ): Boolean {
        if (project == null) {
            logger.info("WriteToolHandler: Project is null, 无法使用 IDE 集成")
            return false
        }

        if (config.alwaysExpand) {
            logger.info("WriteToolHandler: 配置为总是展开，跳过 IDE 集成")
            return false
        }

        return try {
            val writeTool = toolCall.specificTool as? WriteToolUse
            if (writeTool == null) {
                logger.warn("WriteToolHandler: specificTool 不是 WriteToolUse")
                return false
            }

            // 创建平台服务
            val platformService = IdeaPlatformService(project)

            // 先刷新文件系统，确保能找到刚写入的文件
            val virtualFile = platformService.refreshFile(writeTool.filePath)
            if (virtualFile == null) {
                logger.warn("WriteToolHandler: 无法找到文件 - ${writeTool.filePath}")
                platformService.showWarning("文件未找到: ${writeTool.filePath}")
                return false
            }

            // 使用平台服务打开文件
            val success = platformService.openFile(
                filePath = writeTool.filePath
            )

            if (success && config.showNotifications) {
                val fileName = writeTool.filePath.substringAfterLast('/').substringAfterLast('\\')
                platformService.showInfo("已打开文件: $fileName")
            }

            success
        } catch (e: Exception) {
            logger.error("WriteToolHandler: 处理失败", e)
            if (config.fallbackBehavior == FallbackBehavior.SHOW_ERROR) {
                IdeaPlatformService(project).showError("打开文件失败: ${e.message}")
            }
            config.fallbackBehavior == FallbackBehavior.EXPAND
        }
    }
}