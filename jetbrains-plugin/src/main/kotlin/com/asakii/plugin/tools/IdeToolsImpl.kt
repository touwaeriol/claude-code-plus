package com.asakii.plugin.tools

import com.asakii.rpc.api.*
import com.asakii.server.tools.IdeToolsDefault
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
 * IDE 工具 IDEA 实现（继承默认实现，覆盖 IDEA 特有方法）
 *
 * - 继承 IdeToolsDefault 的通用实现（文件搜索、内容读取等）
 * - 覆盖需要 IDEA Platform API 的方法（openFile、showDiff、getTheme 等）
 */
class IdeToolsImpl(
    private val project: Project
) : IdeToolsDefault(project.basePath) {
    
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
    
    /**
     * 从当前文件内容逆向重建修改前的内容
     *
     * 注意：如果文件被 linter/formatter 修改过，newString 可能无法精确匹配。
     * 此时会尝试标准化空白后再匹配，如果仍失败则抛出异常。
     */
    private fun rebuildBeforeContent(afterContent: String, operations: List<EditOperation>): String {
        var content = afterContent
        for (operation in operations.asReversed()) {
            if (operation.replaceAll) {
                if (content.contains(operation.newString)) {
                    content = content.replace(operation.newString, operation.oldString)
                } else {
                    // 尝试标准化空白后匹配
                    val normalizedNew = normalizeWhitespace(operation.newString)
                    val normalizedContent = normalizeWhitespace(content)
                    if (normalizedContent.contains(normalizedNew)) {
                        // 找到标准化匹配，使用原始 oldString 替换（保持格式）
                        content = replaceNormalized(content, operation.newString, operation.oldString)
                    } else {
                        logger.warning("⚠️ rebuildBeforeContent: newString not found (replace_all), skipping operation")
                        // 继续处理其他操作，不抛出异常
                    }
                }
            } else {
                val index = content.indexOf(operation.newString)
                if (index >= 0) {
                    content = buildString {
                        append(content.substring(0, index))
                        append(operation.oldString)
                        append(content.substring(index + operation.newString.length))
                    }
                } else {
                    // 尝试标准化空白后匹配
                    val fuzzyIndex = findNormalizedIndex(content, operation.newString)
                    if (fuzzyIndex >= 0) {
                        // 找到模糊匹配位置，计算实际结束位置
                        val actualEnd = findActualEndIndex(content, fuzzyIndex, operation.newString)
                        content = buildString {
                            append(content.substring(0, fuzzyIndex))
                            append(operation.oldString)
                            append(content.substring(actualEnd))
                        }
                    } else {
                        logger.warning("⚠️ rebuildBeforeContent: newString not found, skipping operation")
                        // 继续处理其他操作，不抛出异常
                    }
                }
            }
        }
        logger.info("✅ Successfully rebuilt before content (${operations.size} operations)")
        return content
    }

    /**
     * 标准化空白字符（用于模糊匹配）
     */
    private fun normalizeWhitespace(s: String): String {
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * 在标准化空白后查找子串位置
     */
    private fun findNormalizedIndex(content: String, target: String): Int {
        val normalizedTarget = normalizeWhitespace(target)
        val lines = content.lines()
        var charIndex = 0

        for (lineIdx in lines.indices) {
            val line = lines[lineIdx]
            // 尝试在当前行开始的多行区域中匹配
            val remainingContent = lines.drop(lineIdx).joinToString("\n")
            val normalizedRemaining = normalizeWhitespace(remainingContent)

            if (normalizedRemaining.startsWith(normalizedTarget) ||
                normalizedRemaining.contains(normalizedTarget)) {
                // 找到了匹配的起始位置
                return charIndex
            }
            charIndex += line.length + 1 // +1 for newline
        }
        return -1
    }

    /**
     * 找到实际的结束索引（考虑空白差异）
     */
    private fun findActualEndIndex(content: String, startIndex: Int, target: String): Int {
        val normalizedTarget = normalizeWhitespace(target)
        val targetNormalizedLen = normalizedTarget.length

        var normalizedCount = 0
        var actualIndex = startIndex

        while (actualIndex < content.length && normalizedCount < targetNormalizedLen) {
            val c = content[actualIndex]
            if (!c.isWhitespace() || (normalizedCount > 0 && normalizedTarget.getOrNull(normalizedCount) == ' ')) {
                normalizedCount++
            }
            actualIndex++
        }

        // 跳过尾部空白
        while (actualIndex < content.length && content[actualIndex].isWhitespace() &&
               content[actualIndex] != '\n') {
            actualIndex++
        }

        return actualIndex
    }

    /**
     * 使用标准化匹配进行替换
     */
    private fun replaceNormalized(content: String, target: String, replacement: String): String {
        val index = findNormalizedIndex(content, target)
        if (index < 0) return content

        val endIndex = findActualEndIndex(content, index, target)
        return buildString {
            append(content.substring(0, index))
            append(replacement)
            append(content.substring(endIndex))
        }
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
            val result = files.take(maxResults).map { file ->
                FileInfo(file.path)
            }
            Result.success(result)
        } catch (e: Exception) {
            logger.warning("Failed to get recent files: ${e.message}")
            Result.failure(e)
        }
    }
    
    override fun getTheme(): IdeTheme {
        return IdeTheme(
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
            hoverBackground = colorToHex(JBColor.namedColor("List.hoverBackground", UIUtil.getPanelBackground())),
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

