package com.asakii.plugin.handlers

import com.asakii.plugin.services.IdeaPlatformService
import com.asakii.plugin.types.WriteToolDetail
import com.asakii.plugin.types.LegacyToolCall
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

    override fun canHandle(toolCall: LegacyToolCall): Boolean {
        return false  // 临时禁用
    }

    override fun handleToolClick(
        toolCall: LegacyToolCall,
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

        // 临时简化处理 - 待重构后重新实现
        return false
    }
}
