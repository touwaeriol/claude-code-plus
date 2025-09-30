package com.claudecodeplus.plugin.handlers

import com.claudecodeplus.sdk.types.ReadToolUse
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult
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
import com.intellij.openapi.util.text.StringUtil
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
        val resultContent = (toolCall.result as? ToolResult.Success)?.output

        return ReadFileInfo(
            filePath = specificTool.filePath,
            offset = specificTool.offset,
            limit = specificTool.limit,
            content = resultContent
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
                val descriptor = OpenFileDescriptor(project, virtualFile)
                val editor = fileEditorManager.openTextEditor(descriptor, true)

                val highlightedLine = editor?.let { applySelection(it, fileInfo) }

                if (config.showNotifications) {
                    val message = when {
                        highlightedLine != null -> "已打开文件并定位到第 $highlightedLine 行"
                        else -> "已打开文件: ${virtualFile.name}"
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
     * 根据工具调用信息在编辑器中选择读取到的范围
     */
    private fun applySelection(editor: Editor, fileInfo: ReadFileInfo): Int? {
        return try {
            val document = editor.document
            if (document.lineCount == 0) {
                return null
            }

            val documentText = document.text
            val selectionRange = findSelectionRange(documentText, fileInfo)

            if (selectionRange != null) {
                val startOffset = selectionRange.startOffset.coerceIn(0, document.textLength)
                val endOffset = selectionRange.endOffset.coerceIn(startOffset, document.textLength)

                if (endOffset > startOffset) {
                    editor.selectionModel.setSelection(startOffset, endOffset)

                    val caretModel = editor.caretModel
                    caretModel.moveToOffset(startOffset)
                    editor.scrollingModel.scrollTo(caretModel.logicalPosition, ScrollType.CENTER)

                    val startLineIndex = document.getLineNumber(startOffset)
                    val endLineIndex = document.getLineNumber(endOffset)
                    logger.info("ReadToolHandler: 已选择第 ${startLineIndex + 1}-${endLineIndex + 1} 行")
                    return startLineIndex + 1
                }
            }

            // 如果没有匹配的内容范围，尝试使用偏移量或行号定位
            val fallbackOffset = fileInfo.offset?.takeIf { it >= 0 }?.coerceAtMost(document.textLength)
            if (fallbackOffset != null) {
                val caretModel = editor.caretModel
                caretModel.moveToOffset(fallbackOffset)
                editor.selectionModel.removeSelection()
                editor.scrollingModel.scrollTo(caretModel.logicalPosition, ScrollType.CENTER)
                return document.getLineNumber(fallbackOffset) + 1
            }

            null
        } catch (e: Exception) {
            logger.warn("ReadToolHandler: 选择文本范围失败", e)
            null
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
    val offset: Int? = null,
    val limit: Int? = null,
    val content: String? = null
)

private data class SelectionRange(
    val startOffset: Int,
    val endOffset: Int
)

private fun findSelectionRange(documentText: String, fileInfo: ReadFileInfo): SelectionRange? {
    val contentRange = fileInfo.content
        ?.takeIf { it.isNotBlank() }
        ?.let { extractRangeByContent(documentText, it) }

    if (contentRange != null) {
        return contentRange
    }

    return extractRangeByOffset(documentText, fileInfo.offset, fileInfo.limit)
}

private fun extractRangeByContent(documentText: String, snippet: String): SelectionRange? {
    val normalizedSnippet = StringUtil.convertLineSeparators(snippet)
    val candidates = listOf(
        normalizedSnippet,
        normalizedSnippet.trim('\n')
    ).filter { it.isNotEmpty() }

    for (candidate in candidates) {
        val startIndex = documentText.indexOf(candidate)
        if (startIndex >= 0) {
            return SelectionRange(startIndex, startIndex + candidate.length)
        }
    }

    val nonBlankLines = normalizedSnippet.lines().filter { it.isNotBlank() }
    if (nonBlankLines.isNotEmpty()) {
        val firstLine = nonBlankLines.first()
        val startIndex = documentText.indexOf(firstLine)
        if (startIndex >= 0) {
            val lastLine = nonBlankLines.last()
            val lastIndex = documentText.indexOf(lastLine, startIndex)
            val endIndex = if (lastIndex >= 0) lastIndex + lastLine.length else startIndex + firstLine.length
            return SelectionRange(startIndex, endIndex)
        }
    }

    return null
}

private fun extractRangeByOffset(documentText: String, offset: Int?, limit: Int?): SelectionRange? {
    if (offset == null) return null
    if (documentText.isEmpty()) return null

    val textLength = documentText.length
    val offsetsToTry = buildList {
        add(offset)
        if (offset > 0) add(offset - 1)
    }

    offsetsToTry.forEach { candidate ->
        val start = candidate.coerceIn(0, textLength)
        val end = when {
            limit != null && limit > 0 -> (start + limit).coerceAtMost(textLength)
            else -> findLineEnd(documentText, start)
        }

        if (end > start) {
            return SelectionRange(start, end)
        }
    }

    // 将 offset 视为行号处理
    val lineIndex = if (offset > 0) offset - 1 else 0
    val lineStart = findLineStart(documentText, lineIndex)
    val lineEnd = if (limit != null && limit > 0) {
        findLineEndByCount(documentText, lineIndex, limit)
    } else {
        findLineEnd(documentText, lineStart)
    }

    return if (lineEnd > lineStart) SelectionRange(lineStart, lineEnd) else null
}

private fun findLineStart(text: String, lineIndex: Int): Int {
    if (lineIndex <= 0) return 0
    var remaining = lineIndex
    var index = 0
    while (index < text.length && remaining > 0) {
        if (text[index] == '\n') {
            remaining--
        }
        index++
    }
    return index.coerceAtMost(text.length)
}

private fun findLineEnd(text: String, startOffset: Int): Int {
    if (startOffset >= text.length) return text.length
    val newlineIndex = text.indexOf('\n', startOffset)
    return if (newlineIndex >= 0) newlineIndex else text.length
}

private fun findLineEndByCount(text: String, startLine: Int, lineCount: Int): Int {
    var index = findLineStart(text, startLine)
    var remaining = if (lineCount > 0) lineCount else 1

    while (index < text.length && remaining > 0) {
        val newlineIndex = text.indexOf('\n', index)
        if (newlineIndex < 0) {
            return text.length
        }
        index = newlineIndex + 1
        remaining--
    }

    return index.coerceAtMost(text.length)
}
