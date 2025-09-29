package com.claudecodeplus.plugin.handlers

import com.claudecodeplus.sdk.types.WriteToolUse
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationGroupManager
import java.io.File
import java.nio.file.Paths

/**
 * Write 工具点击处理器
 * 在 IDEA 编辑器中打开刚写入的文件
 */
class WriteToolHandler : ToolClickHandler {

    companion object {
        private val logger = Logger.getInstance(WriteToolHandler::class.java)
        private val NOTIFICATION_GROUP_ID = "Claude Code Plus"
    }

    override fun canHandle(toolCall: ToolCall): Boolean {
        return toolCall.specificTool is WriteToolUse &&
            toolCall.status == ToolCallStatus.SUCCESS
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
            val fileInfo = parseWriteToolCall(toolCall)
            if (fileInfo != null) {
                openFileInEditor(project, fileInfo, config)
                true
            } else {
                logger.warn("WriteToolHandler: 无法解析工具调用参数")
                false
            }
        } catch (e: Exception) {
            logger.error("WriteToolHandler: 处理失败", e)
            if (config.fallbackBehavior == FallbackBehavior.SHOW_ERROR) {
                showErrorNotification(project, "打开文件失败: ${e.message}")
            }
            config.fallbackBehavior == FallbackBehavior.EXPAND
        }
    }

    /**
     * 解析 Write 工具调用参数
     */
    private fun parseWriteToolCall(toolCall: ToolCall): WriteFileInfo? {
        val specificTool = toolCall.specificTool as? WriteToolUse ?: return null

        return WriteFileInfo(filePath = specificTool.filePath)
    }

    /**
     * 在编辑器中打开文件
     */
    private fun openFileInEditor(
        project: Project,
        fileInfo: WriteFileInfo,
        config: ToolClickConfig
    ) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val virtualFile = findOrRefreshFile(fileInfo.filePath)

                if (virtualFile == null) {
                    logger.warn("WriteToolHandler: 无法找到文件 - ${fileInfo.filePath}")
                    if (config.fallbackBehavior == FallbackBehavior.SHOW_ERROR) {
                        showErrorNotification(project, "文件未找到: ${fileInfo.filePath}")
                    }
                    return@invokeLater
                }

                // 打开文件
                val descriptor = OpenFileDescriptor(project, virtualFile)
                val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)

                if (editor != null) {
                    logger.info("WriteToolHandler: 成功打开文件 - ${fileInfo.filePath}")
                } else {
                    logger.warn("WriteToolHandler: 无法打开编辑器 - ${fileInfo.filePath}")
                }
            } catch (e: Exception) {
                logger.error("WriteToolHandler: 打开文件失败", e)
                if (config.fallbackBehavior == FallbackBehavior.SHOW_ERROR) {
                    showErrorNotification(project, "打开文件失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 查找或刷新文件
     */
    private fun findOrRefreshFile(filePath: String): VirtualFile? {
        val file = File(filePath)

        // 尝试规范化路径
        val normalizedPath = try {
            file.canonicalPath
        } catch (e: Exception) {
            logger.warn("WriteToolHandler: 无法规范化路径 - $filePath", e)
            filePath
        }

        // 查找文件
        var virtualFile = LocalFileSystem.getInstance().findFileByPath(normalizedPath)

        // 如果没找到，尝试刷新父目录并重新查找
        if (virtualFile == null && file.exists()) {
            val parentFile = file.parentFile
            if (parentFile?.exists() == true) {
                val parentVirtualFile = LocalFileSystem.getInstance().findFileByPath(parentFile.absolutePath)
                parentVirtualFile?.refresh(false, true)
                virtualFile = LocalFileSystem.getInstance().findFileByPath(normalizedPath)
            }
        }

        return virtualFile
    }

    /**
     * 显示错误通知
     */
    private fun showErrorNotification(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            val notificationGroup = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)

            val notification = notificationGroup.createNotification(
                "Write 工具",
                message,
                NotificationType.ERROR
            )

            Notifications.Bus.notify(notification, project)
        }
    }

    /**
     * 文件信息数据类
     */
    private data class WriteFileInfo(
        val filePath: String
    )
}
