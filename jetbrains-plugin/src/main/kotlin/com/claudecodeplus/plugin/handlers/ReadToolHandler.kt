package com.claudecodeplus.plugin.handlers

import com.claudecodeplus.plugin.services.IdeaPlatformService
import com.claudecodeplus.sdk.types.ReadToolUse
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Read 工具点击处理器
 * 在 IDEA 编辑器中打开文件，支持行号定位和文本选择
 *
 * 使用 IdeaPlatformService 统一服务，简化实现
 */
class ReadToolHandler : ToolClickHandler {

    companion object {
        private val logger = Logger.getInstance(ReadToolHandler::class.java)
    }

    override fun canHandle(toolCall: ToolCall): Boolean {
        // 只有 SUCCESS 状态才能打开文件
        // RUNNING/FAILED 状态不处理
        return toolCall.specificTool is ReadToolUse &&
            toolCall.status == com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS
    }

    override fun handleToolClick(
        toolCall: ToolCall,
        project: Project?,
        config: ToolClickConfig
    ): Boolean {
        if (project == null) {
            logger.info("ReadToolHandler: Project is null, 无法使用 IDE 集成")
            return false
        }

        if (config.alwaysExpand) {
            logger.info("ReadToolHandler: 配置为总是展开，跳过 IDE 集成")
            return false
        }

        return try {
            val readTool = toolCall.specificTool as? ReadToolUse
            if (readTool == null) {
                logger.warn("ReadToolHandler: specificTool 不是 ReadToolUse")
                return false
            }

            // 创建平台服务
            val platformService = IdeaPlatformService(project)

            // 获取结果内容
            val content = (toolCall.result as? ToolResult.Success)?.output

            // 使用平台服务打开文件
            val success = platformService.openFile(
                filePath = readTool.filePath,
                selectContent = true,
                content = content
            )

            if (success && config.showNotifications) {
                val fileName = readTool.filePath.substringAfterLast('/').substringAfterLast('\\')
                platformService.showInfo("已打开文件: $fileName")
            }

            success
        } catch (e: Exception) {
            logger.error("ReadToolHandler: 处理失败", e)
            if (config.fallbackBehavior == FallbackBehavior.SHOW_ERROR) {
                IdeaPlatformService(project).showError("打开文件失败: ${e.message}")
            }
            config.fallbackBehavior == FallbackBehavior.EXPAND
        }
    }
}