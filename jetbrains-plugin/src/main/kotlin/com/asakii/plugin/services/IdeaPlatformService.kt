package com.asakii.plugin.services



import com.intellij.diff.DiffContentFactory

import com.intellij.diff.DiffManager

import com.intellij.diff.requests.SimpleDiffRequest

import com.intellij.notification.NotificationGroupManager

import com.intellij.notification.NotificationType

import com.intellij.notification.Notifications

import com.intellij.openapi.application.ApplicationManager

import com.intellij.openapi.diagnostic.Logger

import com.intellij.openapi.editor.Editor

import com.intellij.openapi.editor.ScrollType

import com.intellij.openapi.fileEditor.FileDocumentManager

import com.intellij.openapi.fileEditor.FileEditorManager

import com.intellij.openapi.fileEditor.OpenFileDescriptor

import com.intellij.openapi.project.Project

import com.intellij.openapi.vfs.LocalFileSystem

import com.intellij.openapi.vfs.VirtualFile

import com.intellij.util.FileContentUtil

import java.io.File

import java.nio.file.Paths



/**

 * IDEA 平台统一服务

 *

 * 封装所有 IntelliJ IDEA 平台操作，提供统一的接口

 * 便于维护、测试和后续扩展

 */

class IdeaPlatformService(private val project: Project) {



    companion object {

        private val logger = Logger.getInstance(IdeaPlatformService::class.java)

        private const val NOTIFICATION_GROUP_ID = "Claude Code Plus"

    }



    data class SelectionRange(val startOffset: Int, val endOffset: Int) {

        init {

            require(startOffset <= endOffset) { "startOffset must be <= endOffset" }

        }

    }



    // ====== 文件操作 ======



    /**

     * 在编辑器中打开文件

     *

     * @param filePath 文件路径（支持绝对路径和相对路径）

     * @param line 行号（从1开始，可选）

     * @param column 列号（从1开始，可选）

     * @param selectContent 是否选择内容（如果提供了 content）

     * @param content 要选择的内容（可选）

     * @param selectionRange 选择范围（偏移量）

     * @param focusEditor 是否聚焦编辑器（默认 false，不抢占焦点）

     * @return true 表示成功打开

     */

    fun openFile(

        filePath: String,

        line: Int? = null,

        column: Int? = null,

        selectContent: Boolean = true,

        content: String? = null,

        selectionRange: SelectionRange? = null,

        focusEditor: Boolean = false

    ): Boolean {

        return try {

            val virtualFile = findVirtualFile(filePath)

            if (virtualFile == null) {

                logger.warn("找不到文件: $filePath")

                showWarning("找不到文件: $filePath")

                return false

            }



            ApplicationManager.getApplication().invokeLater {

                try {

                    val fileEditorManager = FileEditorManager.getInstance(project)

                    val descriptor = OpenFileDescriptor(project, virtualFile)

                    // focusEditor = false: 打开文件但不聚焦到 IDEA 窗口

                    val editor = fileEditorManager.openTextEditor(descriptor, focusEditor)



                    editor?.let { textEditor ->

                        when {

                            selectionRange != null -> selectRangeInEditor(textEditor, selectionRange)

                            selectContent && content != null -> selectTextInEditor(textEditor, content)

                            line != null -> navigateToLine(textEditor, line, column)

                        }

                    }



                    logger.info("成功打开文件: ${virtualFile.name}")

                } catch (e: Exception) {

                    logger.error("打开文件失败: $filePath", e)

                    showError("打开文件失败: ${e.message}")

                }

            }



            true

        } catch (e: Exception) {

            logger.error("openFile 失败", e)

            showError("打开文件失败: ${e.message}")

            false
        }
    }

    private fun selectRangeInEditor(editor: Editor, range: SelectionRange) {
        try {
            val document = editor.document
            val length = document.textLength
            val start = range.startOffset.coerceIn(0, length)
            val end = range.endOffset.coerceIn(start, length)

            editor.selectionModel.setSelection(start, end)
            editor.caretModel.moveToOffset(start)
            editor.scrollingModel.scrollTo(editor.caretModel.logicalPosition, ScrollType.CENTER)
            logger.info("成功根据范围选择文本: $start-$end")
        } catch (e: Exception) {
            logger.warn("根据范围选择文本失败", e)
        }
    }

    /**
     * 显示文件差异对比
     *

     * @param filePath 文件路径

     * @param oldContent 原始内容

     * @param newContent 新内容

     * @param title 对比标题（可选）

     * @return true 表示成功显示

     */

    fun showDiff(

        filePath: String,

        oldContent: String,

        newContent: String,

        title: String? = null

    ): Boolean {

        return try {

            ApplicationManager.getApplication().invokeLater {

                try {

                    val diffContentFactory = DiffContentFactory.getInstance()

                    val oldDiffContent = diffContentFactory.create(oldContent)

                    val newDiffContent = diffContentFactory.create(newContent)



                    val fileName = File(filePath).name

                    val diffTitle = title ?: "文件变更: $fileName"


                    val diffRequest = SimpleDiffRequest(

                        diffTitle,

                        oldDiffContent,

                        newDiffContent,

                        "$fileName (原始)",

                        "$fileName (修改后)"

                    )



                    DiffManager.getInstance().showDiff(project, diffRequest)

                    logger.info("成功显示文件差异: $fileName")

                } catch (e: Exception) {

                    logger.error("显示差异失败: $filePath", e)

                    showError("显示文件差异失败: ${e.message}")

                }

            }



            true

        } catch (e: Exception) {

            logger.error("showDiff 失败", e)

            showError("显示差异失败: ${e.message}")

            false

        }

    }



    /**

     * 查找虚拟文件

     *

     * @param filePath 文件路径（支持绝对路径和相对路径）

     * @return VirtualFile 或 null

     */

    fun findVirtualFile(filePath: String): VirtualFile? {

        return try {

            val file = File(filePath)



            // 尝试作为绝对路径

            if (file.isAbsolute && file.exists()) {

                return LocalFileSystem.getInstance().findFileByPath(file.canonicalPath)

            }



            // 尝试作为项目相对路径

            val projectBasePath = project.basePath

            if (projectBasePath != null) {

                val absolutePath = Paths.get(projectBasePath, filePath).toString()

                val absoluteFile = File(absolutePath)

                if (absoluteFile.exists()) {

                    return LocalFileSystem.getInstance().findFileByPath(absoluteFile.canonicalPath)

                }

            }



            null

        } catch (e: Exception) {

            logger.warn("查找文件失败: $filePath", e)

            null

        }

    }



    /**

     * 刷新文件系统，确保 IDEA 能立刻看到文件变化

     *

     * - 新创建的文件会立刻出现在文件列表

     * - 修改的文件内容会立刻更新

     * - 已打开的文件会重新加载

     *

     * @param filePath 文件路径

     * @return 刷新后的 VirtualFile 或 null

     */

    fun refreshFile(filePath: String): VirtualFile? {

        val file = File(filePath)

        if (!file.exists()) {

            return null

        }



        return try {

            // 使用 refreshAndFindFileByPath 刷新并获取文件

            // 这会同时刷新父目录（新文件出现）和文件内容（编辑更新）

            val virtualFile = LocalFileSystem.getInstance()

                .refreshAndFindFileByPath(file.canonicalPath)



            // 如果文件已经在编辑器中打开，需要重新加载 Document 并刷新索引

            virtualFile?.let { vf ->

                ApplicationManager.getApplication().invokeLater {

                    // 重新加载编辑器中的文档

                    FileDocumentManager.getInstance().reloadFiles(vf)

                    // 强制重新解析文件，触发 IDEA 索引更新

                    FileContentUtil.reparseFiles(project, listOf(vf), true)

                }

            }



            virtualFile

        } catch (e: Exception) {

            logger.warn("刷新文件失败: $filePath", e)

            null

        }

    }



    /**

     * 保存指定文件的文档到磁盘

     *

     * @param filePath 文件路径

     * @return true 表示保存成功或文件无需保存

     */

    fun saveDocument(filePath: String): Boolean {

        return try {

            val virtualFile = findVirtualFile(filePath) ?: return true // 文件不存在，无需保存

            val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return true



            if (FileDocumentManager.getInstance().isDocumentUnsaved(document)) {

                ApplicationManager.getApplication().invokeAndWait {

                    FileDocumentManager.getInstance().saveDocument(document)

                }

                logger.info("已保存文档: $filePath")

            }

            true

        } catch (e: Exception) {

            logger.warn("保存文档失败: $filePath", e)

            false

        }

    }



    // ====== 通知操作 ======



    /**

     * 显示信息通知

     */

    fun showInfo(message: String) {

        showNotification(message, NotificationType.INFORMATION)

    }



    /**

     * 显示警告通知

     */

    fun showWarning(message: String) {

        showNotification(message, NotificationType.WARNING)

    }



    /**

     * 显示错误通知

     */

    fun showError(message: String) {

        showNotification(message, NotificationType.ERROR)

    }



    /**

     * 显示通知

     */

    fun showNotification(message: String, type: NotificationType) {

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

            logger.warn("显示通知失败: $message", e)

        }

    }



    // ====== 私有辅助方法 ======



    /**

     * 在编辑器中定位到指定行

     */

    private fun navigateToLine(editor: Editor, line: Int, column: Int?) {

        try {

            val document = editor.document

            if (document.lineCount == 0) {

                return

            }



            val lineIndex = (line - 1).coerceIn(0, document.lineCount - 1)

            var offset = document.getLineStartOffset(lineIndex)



            if (column != null && column > 0) {

                val lineEndOffset = document.getLineEndOffset(lineIndex)

                offset = (offset + column - 1).coerceAtMost(lineEndOffset)

            }



            editor.caretModel.moveToOffset(offset)

            editor.scrollingModel.scrollTo(editor.caretModel.logicalPosition, ScrollType.CENTER)



            logger.info("成功定位到第 $line 行")

        } catch (e: Exception) {

            logger.warn("定位失败", e)

        }

    }



    /**

     * 在编辑器中选择文本

     */

    private fun selectTextInEditor(editor: Editor, content: String) {

        try {

            val document = editor.document

            val documentText = document.text



            // 尝试在文档中查找内容

            val startIndex = documentText.indexOf(content)

            if (startIndex >= 0) {

                val endIndex = startIndex + content.length

                editor.selectionModel.setSelection(startIndex, endIndex)

                editor.caretModel.moveToOffset(startIndex)

                editor.scrollingModel.scrollTo(editor.caretModel.logicalPosition, ScrollType.CENTER)

                logger.info("成功选择文本内容")

            } else {

                logger.warn("在文档中找不到指定内容")

            }

        } catch (e: Exception) {

            logger.warn("选择文本失败", e)

        }

    }

}

