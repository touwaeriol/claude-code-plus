package com.asakii.plugin.tools

import com.asakii.bridge.IdeTheme
import com.asakii.server.tools.*
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.util.PropertiesComponent
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.io.File
import java.util.Locale
import java.util.logging.Logger

/**
 * IDE工具实现（IDEA插件模式）
 * 
 * 使用IDEA Platform API实现所有IDE操作
 */
class IdeToolsImpl(
    private val project: Project
) : IdeTools {
    
    private val logger = Logger.getLogger(IdeToolsImpl::class.java.name)
    private val PREFERRED_LOCALE_KEY = "com.asakii.locale"
    
    override fun openFile(path: String, line: Int, column: Int): Result<Unit> {
        if (path.isBlank()) {
            return Result.failure(IllegalArgumentException("File path cannot be empty"))
        }
        
        return try {
            ApplicationManager.getApplication().invokeLater {
                val file = LocalFileSystem.getInstance().findFileByIoFile(File(path))
                if (file != null) {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val descriptor = if (line > 0) {
                        OpenFileDescriptor(project, file, line - 1, (column - 1).coerceAtLeast(0))
                    } else {
                        OpenFileDescriptor(project, file)
                    }
                    fileEditorManager.openTextEditor(descriptor, true)
                    logger.info("✅ Opened file: $path (line=$line, column=$column)")
                } else {
                    logger.warning("⚠️ File not found: $path")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.severe("❌ Failed to open file: ${e.message}")
            Result.failure(e)
        }
    }
    
    override fun showDiff(request: DiffRequest): Result<Unit> {
        if (request.filePath.isBlank()) {
            return Result.failure(IllegalArgumentException("File path cannot be empty"))
        }
        
        return try {
            ApplicationManager.getApplication().invokeLater {
                val fileName = File(request.filePath).name
                val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
                
                val (finalOldContent, finalNewContent, finalTitle) = if (request.rebuildFromFile) {
                    val file = LocalFileSystem.getInstance().findFileByPath(request.filePath)
                        ?: throw IllegalStateException("File not found: ${request.filePath}")
                    file.refresh(false, false)
                    val currentContent = String(file.contentsToByteArray(), Charsets.UTF_8)
                    
                    val edits = request.edits ?: listOf(
                        EditOperation(
                            oldString = request.oldContent,
                            newString = request.newContent,
                            replaceAll = false
                        )
                    )
                    
                    val rebuiltOldContent = rebuildBeforeContent(currentContent, edits)
                    
                    Triple(
                        rebuiltOldContent,
                        currentContent,
                        request.title ?: "File Changes: $fileName (${edits.size} edits)"
                    )
                } else {
                    Triple(
                        request.oldContent,
                        request.newContent,
                        request.title ?: "File Diff: $fileName"
                    )
                }
                
                val leftContent = DiffContentFactory.getInstance()
                    .create(project, finalOldContent, fileType)
                
                val rightContent = DiffContentFactory.getInstance()
                    .create(project, finalNewContent, fileType)
                
                val diffRequest = SimpleDiffRequest(
                    finalTitle,
                    leftContent,
                    rightContent,
                    "$fileName (before)",
                    "$fileName (after)"
                )
                
                DiffManager.getInstance().showDiff(project, diffRequest)
                
                logger.info("✅ Showing diff for: ${request.filePath}")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            logger.severe("❌ Failed to show diff: ${e.message}")
            Result.failure(e)
        }
    }
    
    private fun rebuildBeforeContent(afterContent: String, operations: List<EditOperation>): String {
        var content = afterContent
        for (operation in operations.asReversed()) {
            if (operation.replaceAll) {
                if (!content.contains(operation.newString)) {
                    throw IllegalStateException(
                        "Rebuild failed: newString not found (replace_all)\n" +
                        "Expected to find: ${operation.newString.take(100)}..."
                    )
                }
                content = content.replace(operation.newString, operation.oldString)
            } else {
                val index = content.indexOf(operation.newString)
                if (index < 0) {
                    throw IllegalStateException(
                        "Rebuild failed: newString not found\n" +
                        "Expected to find: ${operation.newString.take(100)}..."
                    )
                }
                content = buildString {
                    append(content.substring(0, index))
                    append(operation.oldString)
                    append(content.substring(index + operation.newString.length))
                }
            }
        }
        logger.info("✅ Successfully rebuilt before content (${operations.size} operations)")
        return content
    }
    
    override fun searchFiles(query: String, maxResults: Int): Result<List<FileInfo>> {
        if (query.isBlank()) {
            return Result.success(emptyList())
        }
        
        return try {
            val result = mutableListOf<FileInfo>()
            ApplicationManager.getApplication().runReadAction {
                val projectScope = GlobalSearchScope.projectScope(project)
                // 搜索所有文件类型，使用多个常见扩展名
                val commonExtensions = listOf("kt", "java", "js", "ts", "py", "md", "json", "xml", "html", "css", "gradle", "kts", "properties")
                val allFiles = mutableSetOf<VirtualFile>()
                
                // 对每个扩展名进行搜索
                for (ext in commonExtensions) {
                    val files = FilenameIndex.getAllFilesByExt(project, ext, projectScope)
                        .filter { it.name.contains(query, ignoreCase = true) }
                    allFiles.addAll(files)
                }
                
                result.addAll(allFiles.take(maxResults).mapNotNull { file: VirtualFile ->
                    val path = file.path
                    if (path.isNotEmpty()) FileInfo(path) else null
                })
            }
            Result.success(result)
        } catch (e: Exception) {
            logger.warning("Failed to search files: ${e.message}")
            Result.failure(e)
        }
    }
    
    override fun getFileContent(path: String, lineStart: Int?, lineEnd: Int?): Result<String> {
        if (path.isBlank()) {
            return Result.failure(IllegalArgumentException("File path cannot be empty"))
        }
        
        return try {
            val file = LocalFileSystem.getInstance().findFileByIoFile(File(path))
                ?: return Result.failure(IllegalArgumentException("File not found: $path"))
            
            val content = String(file.contentsToByteArray(), Charsets.UTF_8)
            
            val result = if (lineStart != null && lineEnd != null) {
                val lines = content.lines()
                lines.subList(
                    (lineStart - 1).coerceAtLeast(0),
                    lineEnd.coerceAtMost(lines.size)
                ).joinToString("\n")
            } else {
                content
            }
            
            Result.success(result)
        } catch (e: Exception) {
            logger.severe("Failed to get file content: ${e.message}")
            Result.failure(e)
        }
    }
    
    override fun getRecentFiles(maxResults: Int): Result<List<FileInfo>> {
        return try {
            val files = FileEditorManager.getInstance(project).openFiles
            val result = files.take(maxResults).mapNotNull { file ->
                val path = file.path
                if (path != null) FileInfo(path) else null
            }
            Result.success(result)
        } catch (e: Exception) {
            logger.warning("Failed to get recent files: ${e.message}")
            Result.failure(e)
        }
    }
    
    override fun getTheme(): IdeTheme {
        return IdeTheme(
            isDark = UIUtil.isUnderDarcula(),
            background = colorToHex(UIUtil.getPanelBackground()),
            foreground = colorToHex(UIUtil.getLabelForeground()),
            borderColor = colorToHex(JBColor.border()),
            panelBackground = colorToHex(UIUtil.getPanelBackground()),
            textFieldBackground = colorToHex(UIUtil.getTextFieldBackground()),
            selectionBackground = colorToHex(UIUtil.getListSelectionBackground(true)),
            selectionForeground = colorToHex(UIUtil.getListSelectionForeground(true)),
            linkColor = colorToHex(JBColor.namedColor("Link.foreground", JBColor.BLUE)),
            errorColor = colorToHex(JBColor.RED),
            warningColor = colorToHex(JBColor.YELLOW),
            successColor = colorToHex(JBColor.GREEN),
            separatorColor = colorToHex(JBColor.border()),
            hoverBackground = colorToHex(UIUtil.getListBackground(true)),
            accentColor = colorToHex(JBColor.namedColor("Accent.focusColor", JBColor.BLUE)),
            infoBackground = colorToHex(JBColor.namedColor("Component.infoForeground", JBColor.GRAY)),
            codeBackground = colorToHex(UIUtil.getTextFieldBackground()),
            secondaryForeground = colorToHex(JBColor.GRAY)
        )
    }
    
    private fun colorToHex(color: Color): String {
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }
    
    override fun getProjectPath(): String {
        return project.basePath ?: ""
    }
    
    override fun getLocale(): String {
        // 检查用户偏好设置
        val preferred = PropertiesComponent.getInstance().getValue(PREFERRED_LOCALE_KEY)
        if (!preferred.isNullOrBlank()) {
            return preferred
        }
        
        return try {
            val locale = LocalizationUtil.getLocale(true)
            "${locale.language}-${locale.country}"
        } catch (e: Exception) {
            val locale = Locale.getDefault()
            "${locale.language}-${locale.country}"
        }
    }
    
    override fun setLocale(locale: String): Result<Unit> {
        return try {
            PropertiesComponent.getInstance().setValue(PREFERRED_LOCALE_KEY, locale)
            logger.info("Locale preference set to: $locale")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.warning("Failed to set locale preference: ${e.message}")
            Result.failure(e)
        }
    }
}

