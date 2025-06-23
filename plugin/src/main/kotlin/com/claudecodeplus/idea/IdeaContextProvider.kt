package com.claudecodeplus.idea

import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.ContextProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.FilenameIndex
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.Processor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * IntelliJ IDEA 平台的上下文提供者实现（简化版）
 * 提供基本的文件搜索功能
 */
class IdeaContextProvider(private val project: Project) : ContextProvider {
    
    override suspend fun searchFiles(query: String): List<FileContext> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileContext>()
        val basePath = project.basePath ?: return@withContext emptyList()
        
        // 使用 FilenameIndex 搜索文件
        val scope = GlobalSearchScope.projectScope(project)
        val fileNames = mutableSetOf<String>()
        
        // 收集所有文件名
        FilenameIndex.processAllFileNames({ name ->
            if ((name.endsWith(".kt") || name.endsWith(".java")) && name.contains(query, ignoreCase = true)) {
                fileNames.add(name)
            }
            true
        }, scope, null)
        
        // 处理找到的文件
        fileNames.take(20).forEach { fileName ->
            FilenameIndex.getVirtualFilesByName(fileName, scope).forEach { vFile ->
                results.add(
                    FileContext(
                        path = vFile.path.removePrefix("$basePath/"),
                        name = vFile.name,
                        extension = vFile.extension ?: "",
                        size = vFile.length,
                        lastModified = vFile.modificationStamp,
                        preview = readFilePreview(vFile)
                    )
                )
            }
        }
        
        results
    }
    
    override suspend fun searchSymbols(query: String): List<SymbolContext> = withContext(Dispatchers.IO) {
        // 简化实现：只搜索类名
        val results = mutableListOf<SymbolContext>()
        val psiManager = PsiManager.getInstance(project)
        
        // 搜索所有 Kotlin 和 Java 文件
        val scope = GlobalSearchScope.projectScope(project)
        val fileNames = mutableSetOf<String>()
        
        // 收集所有代码文件名
        FilenameIndex.processAllFileNames({ name ->
            if (name.endsWith(".kt") || name.endsWith(".java")) {
                fileNames.add(name)
            }
            true
        }, scope, null)
        
        // 处理文件中的符号
        fileNames.forEach { fileName ->
            FilenameIndex.getVirtualFilesByName(fileName, scope).forEach { vFile ->
                psiManager.findFile(vFile)?.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if (element is PsiNamedElement && element.name?.contains(query, ignoreCase = true) == true) {
                            when (element) {
                                is PsiClass -> results.add(createSymbolContext(element))
                                is PsiMethod -> results.add(createSymbolContext(element))
                                is PsiField -> results.add(createSymbolContext(element))
                            }
                        }
                        super.visitElement(element)
                    }
                })
            }
        }
        
        results.take(20)
    }
    
    override suspend fun getRecentFiles(limit: Int): List<FileContext> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileContext>()
        val basePath = project.basePath ?: return@withContext emptyList()
        
        // 获取最近修改的文件
        val scope = GlobalSearchScope.projectScope(project)
        val fileNames = mutableSetOf<String>()
        
        // 收集所有代码文件名
        FilenameIndex.processAllFileNames({ name ->
            if (name.endsWith(".kt") || name.endsWith(".java")) {
                fileNames.add(name)
            }
            true
        }, scope, null)
        
        // 收集所有文件并按修改时间排序
        val allFiles = mutableListOf<VirtualFile>()
        fileNames.forEach { fileName ->
            allFiles.addAll(FilenameIndex.getVirtualFilesByName(fileName, scope))
        }
        
        allFiles.sortedByDescending { it.modificationStamp }
            .take(limit)
            .forEach { vFile ->
                results.add(
                    FileContext(
                        path = vFile.path.removePrefix("$basePath/"),
                        name = vFile.name,
                        extension = vFile.extension ?: "",
                        size = vFile.length,
                        lastModified = vFile.modificationStamp,
                        preview = readFilePreview(vFile)
                    )
                )
            }
        
        results
    }
    
    override suspend fun getTerminalOutput(lines: Int): TerminalContext = withContext(Dispatchers.IO) {
        // TODO: 实现终端输出获取
        TerminalContext(
            output = "终端功能尚未实现",
            timestamp = System.currentTimeMillis(),
            hasErrors = false
        )
    }
    
    override suspend fun getProblems(filter: ProblemSeverity?): List<Problem> = withContext(Dispatchers.IO) {
        // TODO: 实现问题获取
        emptyList()
    }
    
    override suspend fun getGitInfo(type: GitRefType): GitContext = withContext(Dispatchers.IO) {
        // TODO: 实现 Git 信息获取
        GitContext(type, "Git 功能尚未实现")
    }
    
    override suspend fun getFolderInfo(path: String): FolderContext = withContext(Dispatchers.IO) {
        val basePath = project.basePath ?: return@withContext FolderContext(path, 0, 0, 0, emptyList())
        val fullPath = if (path.startsWith("/")) path else "$basePath/$path"
        
        VirtualFileManager.getInstance().refreshAndFindFileByUrl("file://$fullPath")?.let { folder ->
            if (folder.isDirectory) {
                val files = mutableListOf<FileContext>()
                var fileCount = 0
                var folderCount = 0
                var totalSize = 0L
                
                folder.children.forEach { child ->
                    if (child.isDirectory) {
                        folderCount++
                    } else {
                        fileCount++
                        totalSize += child.length
                        files.add(
                            FileContext(
                                path = child.path.removePrefix("$basePath/"),
                                name = child.name,
                                extension = child.extension ?: "",
                                size = child.length,
                                lastModified = child.modificationStamp
                            )
                        )
                    }
                }
                
                FolderContext(path, fileCount, folderCount, totalSize, files)
            } else {
                FolderContext(path, 0, 0, 0, emptyList())
            }
        } ?: FolderContext(path, 0, 0, 0, emptyList())
    }
    
    override suspend fun readFileContent(path: String, lines: IntRange?): String = withContext(Dispatchers.IO) {
        val basePath = project.basePath ?: return@withContext ""
        val fullPath = if (path.startsWith("/")) path else "$basePath/$path"
        
        VirtualFileManager.getInstance().refreshAndFindFileByUrl("file://$fullPath")?.let { file ->
            if (!file.isDirectory) {
                val content = String(file.contentsToByteArray())
                if (lines != null) {
                    content.lines().slice(lines).joinToString("\n")
                } else {
                    content
                }
            } else {
                ""
            }
        } ?: ""
    }
    
    override suspend fun getSymbolDefinition(symbol: String): SymbolContext? = withContext(Dispatchers.IO) {
        // 简化实现
        null
    }
    
    private fun createSymbolContext(psiElement: PsiNamedElement): SymbolContext {
        val containingFile = psiElement.containingFile
        val virtualFile = containingFile.virtualFile
        val basePath = project.basePath ?: ""
        
        return SymbolContext(
            name = psiElement.name ?: "未知",
            type = when (psiElement) {
                is PsiClass -> when {
                    psiElement.isInterface -> SymbolType.INTERFACE
                    psiElement.isEnum -> SymbolType.ENUM
                    else -> SymbolType.CLASS
                }
                is PsiMethod -> SymbolType.FUNCTION
                is PsiField -> if (psiElement.hasModifierProperty(PsiModifier.FINAL)) {
                    SymbolType.CONSTANT
                } else {
                    SymbolType.PROPERTY
                }
                else -> SymbolType.VARIABLE
            },
            file = virtualFile?.path?.removePrefix("$basePath/") ?: "",
            line = containingFile.viewProvider.document?.getLineNumber(psiElement.textOffset) ?: 0,
            signature = when (psiElement) {
                is PsiMethod -> psiElement.text.lines().firstOrNull()
                else -> null
            }
        )
    }
    
    private fun readFilePreview(file: VirtualFile, maxLines: Int = 5): String? {
        return try {
            val content = String(file.contentsToByteArray())
            content.lines().take(maxLines).joinToString("\n")
        } catch (e: Exception) {
            null
        }
    }
}