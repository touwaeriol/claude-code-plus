package com.asakii.plugin.handlers

import com.asakii.plugin.services.IdeaPlatformService
import com.asakii.plugin.types.EditToolDetail
import com.asakii.plugin.types.MultiEditToolDetail
import com.asakii.plugin.types.LegacyToolCall
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Paths

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

    override fun canHandle(toolCall: LegacyToolCall): Boolean {
        val toolDetail = toolCall.viewModel as? Any
        return false  // 临时禁用，待重构
    }

    override fun handleToolClick(
        toolCall: LegacyToolCall,
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
            // 临时简化处理
            false
        } catch (e: Exception) {
            logger.error("EditToolHandler: 处理失败", e)
            if (config.fallbackBehavior == FallbackBehavior.SHOW_ERROR) {
                IdeaPlatformService(project).showError("显示差异失败: ${e.message}")
            }
            config.fallbackBehavior == FallbackBehavior.EXPAND
        }
    }

    private fun handleSingleEdit(
        editTool: EditToolDetail,
        project: Project,
        config: ToolClickConfig
    ): Boolean {
        val platformService = IdeaPlatformService(project)
        platformService.refreshFile(editTool.filePath)

        val afterContent = loadFileContent(editTool.filePath)
        val operations = listOf(
            EditOperation(
                oldString = editTool.oldString,
                newString = editTool.newString,
                replaceAll = editTool.replaceAll
            )
        )
        val beforeContent = afterContent?.let { rebuildBeforeContent(it, operations) }

        val diffSuccess = when {
            beforeContent != null && afterContent != null -> platformService.showDiff(
                filePath = editTool.filePath,
                oldContent = beforeContent,
                newContent = afterContent
            )
            else -> platformService.showDiff(
                filePath = editTool.filePath,
                oldContent = editTool.oldString,
                newContent = editTool.newString
            )
        }

        val success = diffSuccess

        if (success && config.showNotifications) {
            val fileName = editTool.filePath.substringAfterLast('/').substringAfterLast('\\')
            platformService.showInfo("已显示文件差异: $fileName")
        }

        return success
    }

    private fun handleMultiEdit(
        multiEditTool: MultiEditToolDetail,
        project: Project,
        config: ToolClickConfig
    ): Boolean {
        val platformService = IdeaPlatformService(project)

        if (multiEditTool.edits.isEmpty()) {
            logger.warn("EditToolHandler: MultiEdit 没有编辑内容")
            return false
        }

        platformService.refreshFile(multiEditTool.filePath)

        val afterContent = loadFileContent(multiEditTool.filePath)
        val operations = multiEditTool.edits.map {
            EditOperation(
                oldString = it.oldString,
                newString = it.newString,
                replaceAll = it.replaceAll
            )
        }
        val beforeContent = afterContent?.let { rebuildBeforeContent(it, operations) }

        val firstEdit = multiEditTool.edits.first()
        val diffSuccess = when {
            beforeContent != null && afterContent != null -> platformService.showDiff(
                filePath = multiEditTool.filePath,
                oldContent = beforeContent,
                newContent = afterContent,
                title = "文件变更: ${multiEditTool.filePath} (${multiEditTool.edits.size} 处修改)"
            )
            else -> platformService.showDiff(
                filePath = multiEditTool.filePath,
                oldContent = firstEdit.oldString,
                newContent = firstEdit.newString,
                title = "文件变更: ${multiEditTool.filePath} (${multiEditTool.edits.size} 处修改)"
            )
        }

        val success = diffSuccess

        if (success && config.showNotifications) {
            val fileName = multiEditTool.filePath.substringAfterLast('/').substringAfterLast('\\')
            platformService.showInfo("已显示文件差异: $fileName (${multiEditTool.edits.size} 处修改)")
        }

        return success
    }

    private fun loadFileContent(filePath: String): String? = runCatching {
        Files.readString(Paths.get(filePath))
    }.getOrNull()

    private fun rebuildBeforeContent(
        afterContent: String,
        operations: List<EditOperation>
    ): String? {
        var content = afterContent
        for (operation in operations.asReversed()) {
            if (operation.replaceAll) {
                if (!content.contains(operation.newString)) return null
                content = content.replace(operation.newString, operation.oldString)
            } else {
                val index = content.indexOf(operation.newString)
                if (index < 0) return null
                content = buildString {
                    append(content.substring(0, index))
                    append(operation.oldString)
                    append(content.substring(index + operation.newString.length))
                }
            }
        }
        return content
    }

    private data class EditOperation(
        val oldString: String,
        val newString: String,
        val replaceAll: Boolean
    )
}
