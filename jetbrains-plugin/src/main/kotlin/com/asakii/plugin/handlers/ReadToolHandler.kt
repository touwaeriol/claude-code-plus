package com.asakii.plugin.handlers

import com.asakii.plugin.services.IdeaPlatformService
import com.asakii.plugin.types.ReadToolDetail
import com.asakii.plugin.types.LegacyToolCall
import com.asakii.plugin.types.ToolResult
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

    override fun canHandle(toolCall: LegacyToolCall): Boolean {
        return false  // 临时禁用
    }

    override fun handleToolClick(
        toolCall: LegacyToolCall,
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

        // 临时简化处理 - 待重构后重新实现
        return false
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
