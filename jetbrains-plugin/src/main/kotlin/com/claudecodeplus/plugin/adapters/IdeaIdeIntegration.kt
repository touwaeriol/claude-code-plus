package com.claudecodeplus.plugin.adapters

import com.claudecodeplus.plugin.handlers.ToolClickManager
import com.claudecodeplus.plugin.handlers.ToolClickConfig
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.viewmodels.tool.*
import com.claudecodeplus.ui.services.IdeIntegration
import com.claudecodeplus.ui.services.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.fileEditor.FileDocumentManager
import java.io.File
import java.util.*

/**
 * IntelliJ IDEA 的 IDE 集成实现
 */
class IdeaIdeIntegration(
    private val project: Project
) : IdeIntegration {
    
    companion object {
        private val logger = Logger.getInstance(IdeaIdeIntegration::class.java)
    }
    
    override fun handleToolClick(toolCall: ToolCall): Boolean {
        logger.info("🔧 [IdeaIdeIntegration] 处理工具点击: ${toolCall.name}")
        logger.info("- 工具ID: ${toolCall.id}")
        logger.info("- 工具状态: ${toolCall.status}")
        logger.info("- 有结果: ${toolCall.result != null}")
        val parameterSummary = toolCall.viewModel?.toolDetail?.getKeyParameters().orEmpty()
        logger.info("- 参数: $parameterSummary")

        return try {
            val result = ToolClickManager.handleToolClick(toolCall, project, ToolClickConfig())
            logger.info("✅ [IdeaIdeIntegration] ToolClickManager处理结果: $result")
            result
        } catch (e: Exception) {
            logger.error("❌ [IdeaIdeIntegration] 处理工具点击失败", e)
            false
        }
    }
    
    override fun openFile(filePath: String, line: Int?, column: Int?): Boolean {
        return try {
            val (offsetHint, limitHint) = computeOffsetHints(filePath, line, column)
            val tempId = "temp_read_${UUID.randomUUID()}"

            // 创建 ReadToolDetail ViewModel
            val toolDetail = ReadToolDetail(
                filePath = filePath,
                offset = offsetHint,
                limit = limitHint
            )

            // 创建 ToolCallViewModel
            val viewModel = ToolCallViewModel(
                id = tempId,
                name = "Read",
                toolDetail = toolDetail,
                status = com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS,
                result = null,
                startTime = System.currentTimeMillis(),
                endTime = null
            )

            // 创建 ToolCall
            val fakeToolCall = ToolCall(
                id = tempId,
                name = "Read",
                viewModel = viewModel,
                status = com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS,
                result = null,
                startTime = viewModel.startTime,
                endTime = viewModel.endTime
            )

            handleToolClick(fakeToolCall)
        } catch (e: Exception) {
            logger.error("打开文件失败", e)
            false
        }
    }
    
    override fun showDiff(filePath: String, oldContent: String, newContent: String): Boolean {
        return try {
            val tempId = "temp_edit_${UUID.randomUUID()}"

            // 创建 EditToolDetail ViewModel
            val toolDetail = EditToolDetail(
                filePath = filePath,
                oldString = oldContent,
                newString = newContent,
                replaceAll = false
            )

            // 创建 ToolCallViewModel
            val viewModel = ToolCallViewModel(
                id = tempId,
                name = "Edit",
                toolDetail = toolDetail,
                status = com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS,
                result = null,
                startTime = System.currentTimeMillis(),
                endTime = null
            )

            // 创建临时的 Edit 工具调用
            val fakeToolCall = ToolCall(
                id = tempId,
                name = "Edit",
                viewModel = viewModel,
                status = com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS,
                result = null,
                startTime = viewModel.startTime,
                endTime = viewModel.endTime
            )

            handleToolClick(fakeToolCall)
        } catch (e: Exception) {
            logger.error("显示差异失败", e)
            false
        }
    }

    private fun computeOffsetHints(filePath: String, line: Int?, column: Int?): Pair<Int?, Int?> {
        val virtualFile = resolveVirtualFile(filePath) ?: return null to null
        val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return null to null

        if (document.lineCount == 0 || line == null || line <= 0) {
            return null to null
        }

        val lineIndex = (line - 1).coerceAtMost(document.lineCount - 1)
        var startOffset = document.getLineStartOffset(lineIndex)
        val lineEndOffset = document.getLineEndOffset(lineIndex)

        if (column != null && column > 0) {
            startOffset = (startOffset + column).coerceAtMost(lineEndOffset)
        }

        val length = (lineEndOffset - startOffset).coerceAtLeast(0)
        return startOffset to length
    }

    private fun resolveVirtualFile(filePath: String): com.intellij.openapi.vfs.VirtualFile? {
        val file = File(filePath)
        val localFileSystem = LocalFileSystem.getInstance()

        if (file.isAbsolute && file.exists()) {
            return localFileSystem.findFileByPath(file.canonicalPath)
        }

        val basePath = project.basePath ?: return null
        val absoluteFile = File(basePath, filePath)
        return if (absoluteFile.exists()) {
            localFileSystem.findFileByPath(absoluteFile.canonicalPath)
        } else {
            null
        }
    }
    
    override fun showNotification(message: String, type: NotificationType) {
        try {
            val intellijType = when (type) {
                NotificationType.INFO -> com.intellij.notification.NotificationType.INFORMATION
                NotificationType.WARNING -> com.intellij.notification.NotificationType.WARNING
                NotificationType.ERROR -> com.intellij.notification.NotificationType.ERROR
            }
            
            val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("Claude Code Plus")
            
            val notification = notificationGroup.createNotification(
                "Claude Code Plus",
                message,
                intellijType
            )
            
            com.intellij.notification.Notifications.Bus.notify(notification, project)
        } catch (e: Exception) {
            logger.warn("显示通知失败", e)
        }
    }
    
    override fun isSupported(): Boolean = true
    
    /**
     * 获取IntelliJ IDEA的界面语言设置
     * @return IDE的Locale设置
     */
    override fun getIdeLocale(): Locale {
        return try {
            // 使用IntelliJ IDEA的LocalizationUtil获取界面语言设置
            val ideLocale = LocalizationUtil.getLocale()
            logger.info("🌐 获取IDE界面语言设置: $ideLocale (language=${ideLocale.language}, country=${ideLocale.country})")
            ideLocale
        } catch (e: Exception) {
            logger.warn("获取IDE界面语言设置失败，使用英语作为默认", e)
            Locale.ENGLISH
        }
    }
}
