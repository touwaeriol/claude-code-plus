package com.claudecodeplus.ui

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.LanguageTextField
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.Icon

/**
 * 支持 @ 文件引用的编辑器字段
 */
class FileReferenceEditorField(
    project: Project,
    private val onEnterPressed: (String) -> Unit
) : LanguageTextField(PlainTextLanguage.INSTANCE, project, "", false) {
    
    init {
        // 设置为单行
        isOneLineMode = true
        
        // 添加设置提供者
        addSettingsProvider { editor ->
            // 添加 Enter 键处理
            editor.contentComponent.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                        e.consume()
                        onEnterPressed(text)
                    }
                }
            })
            
            // 设置完成处理
            setupCompletion(editor as EditorEx)
        }
    }
    
    private fun setupCompletion(editor: EditorEx) {
        // 当输入 @ 时触发自动完成
        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val text = editor.document.text
                val caretOffset = editor.caretModel.offset
                
                // 调试日志（已禁用）
                // println("[FileReferenceEditorField] Document changed: newFragment='${event.newFragment}', caretOffset=$caretOffset")
                
                // 检查是否输入了 @ 符号
                if (event.newFragment.toString() == "@") {
                    // 检查 @ 前面的字符（如果 @ 是第一个字符也允许）
                    val hasValidPrefix = caretOffset == 1 ||  // @ 是第一个字符
                        caretOffset == 0 ||  // 空文档中输入 @
                        (caretOffset > 1 && text.length >= caretOffset - 1 && text[caretOffset - 2] in " \n\t\r")
                    
                    // println("[FileReferenceEditorField] @ detected at offset $caretOffset, hasValidPrefix=$hasValidPrefix")
                    
                    if (hasValidPrefix) {
                        // 触发自动完成
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            // println("[FileReferenceEditorField] Triggering auto-completion")
                            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
                        }
                    }
                }
            }
        })
    }
}

/**
 * 文件搜索工具
 */
object FileSearchUtil {
    
    fun searchProjectFiles(project: Project, query: String, limit: Int = 30): List<FileSearchResult> {
        val results = mutableListOf<FileSearchResult>()
        val projectBasePath = getProjectBasePath(project)
        val scope = GlobalSearchScope.projectScope(project)
        
        // 1. 精确文件名匹配
        if (query.isNotEmpty()) {
            val exactMatches = FilenameIndex.getFilesByName(project, query, scope)
            exactMatches.forEach { psiFile ->
                psiFile.virtualFile?.let { vf ->
                    results.add(FileSearchResult(
                        virtualFile = vf,
                        relativePath = getRelativePath(vf, projectBasePath),
                        matchScore = 100
                    ))
                }
            }
        }
        
        // 2. 前缀匹配
        val prefixMatches = mutableListOf<FileSearchResult>()
        FilenameIndex.processAllFileNames({ filename ->
            if (filename.startsWith(query, ignoreCase = true) && 
                !results.any { it.virtualFile.name == filename }) {
                val files = FilenameIndex.getFilesByName(project, filename, scope)
                files.forEach { psiFile ->
                    psiFile.virtualFile?.let { vf ->
                        prefixMatches.add(FileSearchResult(
                            virtualFile = vf,
                            relativePath = getRelativePath(vf, projectBasePath),
                            matchScore = 80
                        ))
                    }
                }
            }
            results.size + prefixMatches.size < limit
        }, scope, null)
        results.addAll(prefixMatches.take(limit - results.size))
        
        // 3. 包含匹配
        if (results.size < limit && query.isNotEmpty()) {
            val containsMatches = mutableListOf<FileSearchResult>()
            FilenameIndex.processAllFileNames({ filename ->
                if (filename.contains(query, ignoreCase = true) && 
                    !results.any { it.virtualFile.name == filename }) {
                    val files = FilenameIndex.getFilesByName(project, filename, scope)
                    files.forEach { psiFile ->
                        psiFile.virtualFile?.let { vf ->
                            containsMatches.add(FileSearchResult(
                                virtualFile = vf,
                                relativePath = getRelativePath(vf, projectBasePath),
                                matchScore = 60
                            ))
                        }
                    }
                }
                results.size + containsMatches.size < limit
            }, scope, null)
            results.addAll(containsMatches.take(limit - results.size))
        }
        
        // 4. 如果查询为空，显示最近的文件
        if (query.isEmpty() && results.isEmpty()) {
            val allFiles = mutableListOf<FileSearchResult>()
            val contentRoots = ProjectRootManager.getInstance(project).contentRoots
            
            contentRoots.forEach { root ->
                collectRecentFiles(root, projectBasePath, allFiles, limit)
            }
            
            results.addAll(allFiles.take(limit))
        }
        
        // 按分数和路径排序
        return results.sortedWith(compareByDescending<FileSearchResult> { it.matchScore }
            .thenBy { it.relativePath.count { it == '/' || it == '\\' } }
            .thenBy { it.relativePath })
            .take(limit)
    }
    
    private fun getProjectBasePath(project: Project): String {
        // 使用 ProjectPathDebugger 的逻辑
        val contentRoots = ProjectRootManager.getInstance(project).contentRoots
        if (contentRoots.isNotEmpty()) {
            val rootPath = contentRoots[0].path
            val rootFile = File(rootPath)
            return when {
                rootFile.isDirectory -> rootPath
                rootFile.isFile -> rootFile.parentFile?.absolutePath ?: rootPath
                else -> rootPath
            }
        }
        return project.basePath ?: ""
    }
    
    private fun getRelativePath(file: VirtualFile, basePath: String): String {
        val filePath = file.path
        return when {
            filePath.startsWith(basePath) -> {
                filePath.substring(basePath.length).trimStart('/', '\\')
            }
            else -> file.name
        }
    }
    
    private fun collectRecentFiles(
        dir: VirtualFile, 
        basePath: String, 
        results: MutableList<FileSearchResult>,
        limit: Int
    ) {
        if (results.size >= limit) return
        
        dir.children.forEach { child ->
            if (results.size >= limit) return
            
            if (child.isDirectory && !child.name.startsWith(".")) {
                collectRecentFiles(child, basePath, results, limit)
            } else if (!child.isDirectory && isRelevantFile(child)) {
                results.add(FileSearchResult(
                    virtualFile = child,
                    relativePath = getRelativePath(child, basePath),
                    matchScore = 40
                ))
            }
        }
    }
    
    private fun isRelevantFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase() ?: return false
        return extension in setOf(
            "kt", "java", "xml", "json", "txt", "md", 
            "py", "js", "ts", "html", "css", "yml", "yaml",
            "properties", "gradle", "sql", "sh", "bat"
        )
    }
    
    fun getFileIcon(file: VirtualFile): Icon {
        return when (file.extension?.lowercase()) {
            "kt", "kts" -> AllIcons.FileTypes.Java  // 使用 Java 图标代替
            "java" -> AllIcons.FileTypes.Java
            "xml" -> AllIcons.FileTypes.Xml
            "json" -> AllIcons.FileTypes.Json
            "md" -> AllIcons.FileTypes.Text
            "py" -> AllIcons.FileTypes.Text  // 使用 Text 图标代替
            "js" -> AllIcons.FileTypes.JavaScript
            "ts" -> AllIcons.FileTypes.JavaScript  // 使用 JavaScript 图标代替
            "html" -> AllIcons.FileTypes.Html
            "css" -> AllIcons.FileTypes.Css
            "yml", "yaml" -> AllIcons.FileTypes.Yaml
            else -> if (file.isDirectory) AllIcons.Nodes.Folder else AllIcons.FileTypes.Any_type
        }
    }
}

/**
 * 文件搜索结果
 */
data class FileSearchResult(
    val virtualFile: VirtualFile,
    val relativePath: String,
    val matchScore: Int
)

/**
 * 创建文件引用查找元素
 */
fun createFileLookupElement(result: FileSearchResult): LookupElement {
    return LookupElementBuilder.create(result.relativePath)
        .withIcon(FileSearchUtil.getFileIcon(result.virtualFile))
        .withTypeText(result.virtualFile.extension ?: "")
        .withLookupString(result.virtualFile.name) // 添加文件名作为额外的查找字符串
        .withInsertHandler { context, item ->
            // 处理插入
            val document = context.document
            val startOffset = context.startOffset
            
            // 找到 @ 的位置
            var atOffset = startOffset
            val text = document.text
            for (i in startOffset - 1 downTo 0) {
                if (i < text.length && text[i] == '@') {
                    atOffset = i
                    break
                }
                if (i < text.length && text[i] == ' ') {
                    break
                }
            }
            
            // 使用完整路径替换
            val fullPath = result.virtualFile.path
            if (atOffset < startOffset) {
                document.replaceString(atOffset, context.tailOffset, "@$fullPath ")
            }
        }
}