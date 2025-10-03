package com.claudecodeplus.plugin.handlers

import com.claudecodeplus.plugin.services.IdeaPlatformService
import com.claudecodeplus.ui.viewmodels.tool.ReadToolDetail
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
        return toolCall.viewModel?.toolDetail is ReadToolDetail
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
            val readTool = toolCall.viewModel?.toolDetail as? ReadToolDetail
            if (readTool == null) {
                logger.warn("ReadToolHandler: toolDetail 不是 ReadToolDetail")
                return false
            }

            // 创建平台服务
            val platformService = IdeaPlatformService(project)

            // 获取结果内容
            val content = (toolCall.result as? ToolResult.Success)?.output
            val selectionRange = buildSelectionRange(readTool, content)

            // 使用平台服务打开文件
            val success = platformService.openFile(
                filePath = readTool.filePath,
                selectContent = selectionRange == null && content != null,
                content = content,
                selectionRange = selectionRange
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

    private fun buildSelectionRange(
        detail: ReadToolDetail,
        content: String?
    ): IdeaPlatformService.SelectionRange? {
        val offset = detail.offset ?: return null
        if (offset < 0) return null

        val limit = detail.limit
        val length = when {
            limit != null && limit > 0 -> limit
            !content.isNullOrEmpty() -> content.length
            else -> return null
        }

        if (length <= 0) return null

        val end = offset + length
        if (end <= offset) return null

        return IdeaPlatformService.SelectionRange(offset, end)
    }
}
