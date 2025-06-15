package com.claudecodeplus.ui

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.EditorTextField
import com.intellij.util.ProcessingContext
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Icon

/**
 * 支持 @ 文件引用的文本输入框
 */
class FileReferenceTextField(
    private val project: Project,
    private val onEnterPressed: (String) -> Unit
) : EditorTextField(project, PlainTextFileType.INSTANCE) {
    
    init {
        // 设置单行模式
        isOneLineMode = true
        
        // 添加 Enter 键处理
        addSettingsProvider { editor ->
            editor.contentComponent.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                        e.consume()
                        onEnterPressed(text)
                    }
                }
            })
            
            // 启用自动完成
            setupAutoCompletion(editor as EditorEx)
        }
    }
    
    private fun setupAutoCompletion(editor: EditorEx) {
        // 注册完成贡献者
        val contributor = FileReferenceCompletionContributor()
        
        // 当输入 @ 时触发自动完成
        editor.document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                val text = editor.document.text
                val caretOffset = editor.caretModel.offset
                
                // 检查光标前是否有 @，且 @ 前面是空格或文本开头
                if (caretOffset > 0 && text[caretOffset - 1] == '@') {
                    val hasSpaceBefore = caretOffset == 1 || (caretOffset > 1 && text[caretOffset - 2] == ' ')
                    if (hasSpaceBefore) {
                        // 触发自动完成
                        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
                    }
                }
            }
        })
    }
}

/**
 * 文件引用完成贡献者
 */
class FileReferenceCompletionContributor : CompletionContributor() {
    
    init {
        // 注册完成提供者
        extend(
            CompletionType.BASIC,
            com.intellij.patterns.PlatformPatterns.psiElement(),
            FileReferenceCompletionProvider()
        )
    }
}

/**
 * 文件引用完成提供者
 */
class FileReferenceCompletionProvider : CompletionProvider<CompletionParameters>() {
    
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position
        val text = position.text
        val offset = parameters.offset
        
        // 查找最近的 @ 符号
        var atIndex = -1
        for (i in offset - 1 downTo 0) {
            if (i < text.length && text[i] == '@') {
                atIndex = i
                break
            }
            // 如果遇到空格或其他分隔符，停止查找
            if (i < text.length && text[i] in " \n\t") {
                break
            }
        }
        
        if (atIndex == -1) return
        
        // 获取 @ 后面的查询字符串
        val query = if (offset > atIndex + 1) {
            text.substring(atIndex + 1, minOf(offset, text.length))
        } else {
            ""
        }
        
        val project = parameters.position.project
        
        // 搜索文件
        val files = searchFiles(project, query)
        
        // 添加到结果集
        files.forEach { (file, relativePath) ->
            val lookupElement = createLookupElement(file, relativePath)
            result.addElement(lookupElement)
        }
    }
    
    private fun searchFiles(project: Project, query: String): List<Pair<VirtualFile, String>> {
        val results = mutableListOf<Pair<VirtualFile, String>>()
        val projectBasePath = project.basePath ?: return results
        
        // 使用 FilenameIndex 搜索文件
        val scope = GlobalSearchScope.projectScope(project)
        
        if (query.isEmpty()) {
            // 如果没有查询字符串，显示一些常见文件
            val extensions = listOf("kt", "java", "xml", "json", "txt", "md", "py", "js", "ts")
            extensions.forEach { ext ->
                val files = FilenameIndex.getAllFilesByExt(project, ext, scope)
                files.take(5).forEach { vf ->
                    val relativePath = getRelativePath(vf, projectBasePath)
                    results.add(vf to relativePath)
                }
            }
        } else {
            // 根据查询字符串过滤文件
            val psiFiles = FilenameIndex.getFilesByName(project, query, scope)
            psiFiles.forEach { psiFile ->
                psiFile.virtualFile?.let { vf ->
                    val relativePath = getRelativePath(vf, projectBasePath)
                    results.add(vf to relativePath)
                }
            }
            
            // 如果精确匹配结果太少，进行模糊搜索
            if (results.size < 10) {
                val allFiles = mutableListOf<PsiFile>()
                FilenameIndex.processAllFileNames({ filename ->
                    if (filename.contains(query, ignoreCase = true)) {
                        val files = FilenameIndex.getFilesByName(project, filename, scope)
                        allFiles.addAll(files)
                    }
                    true
                }, scope, null)
                
                allFiles.take(20).forEach { psiFile ->
                    psiFile.virtualFile?.let { vf ->
                        val relativePath = getRelativePath(vf, projectBasePath)
                        if (!results.any { it.first == vf }) {
                            results.add(vf to relativePath)
                        }
                    }
                }
            }
        }
        
        // 按相关性排序
        return results.sortedBy { (file, path) ->
            when {
                file.name.equals(query, ignoreCase = true) -> 0
                file.name.startsWith(query, ignoreCase = true) -> 1
                file.name.contains(query, ignoreCase = true) -> 2
                path.contains(query, ignoreCase = true) -> 3
                else -> 4
            }
        }.take(20)
    }
    
    private fun getRelativePath(file: VirtualFile, projectBasePath: String): String {
        val filePath = file.path
        return if (filePath.startsWith(projectBasePath)) {
            filePath.substring(projectBasePath.length).trimStart('/', '\\')
        } else {
            file.name
        }
    }
    
    private fun createLookupElement(file: VirtualFile, relativePath: String): LookupElementBuilder {
        val icon = getFileIcon(file)
        
        return LookupElementBuilder.create(relativePath)
            .withIcon(icon)
            .withTypeText(file.extension ?: "")
            .withInsertHandler { context, item ->
                // 删除 @ 和查询字符串，插入文件路径
                val document = context.document
                val startOffset = context.startOffset
                
                // 找到 @ 的位置
                var atOffset = startOffset
                val text = document.text
                for (i in startOffset - 1 downTo 0) {
                    if (text[i] == '@') {
                        atOffset = i
                        break
                    }
                }
                
                // 替换从 @ 到当前位置的文本
                document.replaceString(atOffset, context.tailOffset, "@$relativePath")
            }
    }
    
    private fun getFileIcon(file: VirtualFile): Icon {
        return when (file.extension?.lowercase()) {
            "kt" -> AllIcons.FileTypes.Java  // 使用 Java 图标代替
            "java" -> AllIcons.FileTypes.Java
            "xml" -> AllIcons.FileTypes.Xml
            "json" -> AllIcons.FileTypes.Json
            "md" -> AllIcons.FileTypes.Text
            "py" -> AllIcons.FileTypes.Text  // 使用 Text 图标代替
            "js" -> AllIcons.FileTypes.JavaScript
            "ts" -> AllIcons.FileTypes.JavaScript  // 使用 JavaScript 图标代替
            else -> AllIcons.FileTypes.Any_type
        }
    }
}