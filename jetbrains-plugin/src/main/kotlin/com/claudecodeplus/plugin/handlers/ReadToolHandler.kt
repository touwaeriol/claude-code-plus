package com.claudecodeplus.plugin.handlers

import com.claudecodeplus.sdk.types.ReadToolUse
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationGroupManager
import java.io.File
import java.nio.file.Paths

/**
 * Read 工具点击处理器
 * 在 IDEA 编辑器中打开文件，支持行号定位和文本选择
 */
class ReadToolHandler : ToolClickHandler {
    
    companion object {
        private val logger = Logger.getInstance(ReadToolHandler::class.java)
        private val NOTIFICATION_GROUP_ID = "Claude Code Plus"
    }
    
    override fun canHandle(toolCall: ToolCall): Boolean {
        return toolCall.specificTool is ReadToolUse &&
            toolCall.status == ToolCallStatus.SUCCESS
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
            val fileInfo = parseReadToolCall(toolCall)
            if (fileInfo != null) {
                openFileInEditor(project, fileInfo, config)
                true
            } else {
                logger.warn("ReadToolHandler: 无法解析工具调用参数")
                false
            }
        } catch (e: Exception) {
            logger.error("ReadToolHandler: 处理失败", e)
            if (config.fallbackBehavior == FallbackBehavior.SHOW_ERROR) {
                showErrorNotification(project, "打开文件失败: ${e.message}")
            }
            config.fallbackBehavior == FallbackBehavior.EXPAND
        }
    }
    
    /**
     * 解析 Read 工具调用参数
     */
    private fun parseReadToolCall(toolCall: ToolCall): ReadFileInfo? {
        val specificTool = toolCall.specificTool as? ReadToolUse ?: return null

        return ReadFileInfo(
            filePath = specificTool.filePath,
            startLine = specificTool.offset,
            lineCount = specificTool.limit
        )
    }
    
    /**
     * 在编辑器中打开文件
     */
    private fun openFileInEditor(
        project: Project,
        fileInfo: ReadFileInfo,
        config: ToolClickConfig
    ) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val virtualFile = findVirtualFile(project, fileInfo.filePath)
                if (virtualFile == null) {
                    logger.warn("ReadToolHandler: 找不到文件: ${fileInfo.filePath}")
                    if (config.showNotifications) {
                        showWarningNotification(project, "找不到文件: ${fileInfo.filePath}")
                    }
                    return@invokeLater
                }
                
                val fileEditorManager = FileEditorManager.getInstance(project)

                val descriptor = if (fileInfo.startLine != null && fileInfo.startLine > 0) {
                    val lineNumber = fileInfo.startLine - 1 // IDEA 使用 0 基索引
                    OpenFileDescriptor(project, virtualFile, lineNumber, 0)
                } else {
                    OpenFileDescriptor(project, virtualFile)
                }

                val editor = fileEditorManager.openTextEditor(descriptor, true)

                // 选择文本范围（如果指定了行数限制）
                if (editor != null && fileInfo.startLine != null && fileInfo.lineCount != null) {
                    selectTextRange(editor, fileInfo.startLine, fileInfo.lineCount)
                }

                if (config.showNotifications) {
                    val message = if (fileInfo.startLine != null) {
                        "已打开文件并定位到第 ${fileInfo.startLine} 行"
                    } else {
                        "已打开文件: ${virtualFile.name}"
                    }
                    showInfoNotification(project, message)
                }
                
            } catch (e: Exception) {
                logger.error("ReadToolHandler: 打开文件失败", e)
                if (config.showNotifications) {
                    showErrorNotification(project, "打开文件失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 查找虚拟文件
     */
    private fun findVirtualFile(project: Project, filePath: String): VirtualFile? {
        return try {
            // 先尝试作为绝对路径
            val file = File(filePath)
            if (file.isAbsolute && file.exists()) {
                LocalFileSystem.getInstance().findFileByPath(file.canonicalPath)
            } else {
                // 尝试作为项目相对路径
                val projectBasePath = project.basePath
                if (projectBasePath != null) {
                    val absolutePath = Paths.get(projectBasePath, filePath).toString()
                    val absoluteFile = File(absolutePath)
                    if (absoluteFile.exists()) {
                        LocalFileSystem.getInstance().findFileByPath(absoluteFile.canonicalPath)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("ReadToolHandler: 查找文件失败: $filePath", e)
            null
        }
    }
    
    /**
     * 选择文本范围
     */
    private fun selectTextRange(editor: Editor, startLine: Int, lineCount: Int) {
        try {
            val document = editor.document
            if (document.lineCount == 0) {
                return
            }

            val startLineIndex = (startLine - 1).coerceIn(0, document.lineCount - 1)
            val endLineIndex = (startLineIndex + lineCount - 1)
                .coerceIn(startLineIndex, document.lineCount - 1)

            val startOffset = document.getLineStartOffset(startLineIndex)
            val endOffset = document.getLineEndOffset(endLineIndex)

            val selectionModel = editor.selectionModel
            selectionModel.setSelection(startOffset, endOffset)

            val caretModel = editor.caretModel
            caretModel.moveToOffset(startOffset)
            editor.scrollingModel.scrollTo(caretModel.logicalPosition, ScrollType.CENTER)

            val startLineDisplay = startLine.coerceAtLeast(1)
            val endLineDisplay = endLineIndex + 1
            logger.info("ReadToolHandler: 已选择第 $startLineDisplay-$endLineDisplay 行")
        } catch (e: Exception) {
            logger.warn("ReadToolHandler: 选择文本范围失败", e)
        }
    }

    /**
     * 显示通知
     */
    private fun showInfoNotification(project: Project, message: String) {
        showNotification(project, message, NotificationType.INFORMATION)
    }
    
    private fun showWarningNotification(project: Project, message: String) {
        showNotification(project, message, NotificationType.WARNING)
    }
    
    private fun showErrorNotification(project: Project, message: String) {
        showNotification(project, message, NotificationType.ERROR)
    }
    
    private fun showNotification(project: Project, message: String, type: NotificationType) {
        try {
            val notificationGroup = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
            
            val notification = notificationGroup.createNotification(
                "Claude Code Plus",
                message,
                type
            )
            
            Notifications.Bus.notify(notification, project)
        } catch (e: Exception) {
            logger.warn("ReadToolHandler: 显示通知失败", e)
        }
    }
}

/**
 * Read 文件信息数据类
 */
private data class ReadFileInfo(
    val filePath: String,
    val startLine: Int? = null,
    val lineCount: Int? = null
)
