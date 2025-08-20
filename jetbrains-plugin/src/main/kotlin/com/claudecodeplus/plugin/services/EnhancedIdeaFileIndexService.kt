package com.claudecodeplus.plugin.services

import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.IndexedFileInfo
import com.claudecodeplus.ui.services.IndexedSymbolInfo
import com.claudecodeplus.ui.services.IndexStats
import com.claudecodeplus.ui.models.SymbolType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 增强的 IDEA 文件索引服务实现
 * 支持 @ 上下文引用功能
 */
class EnhancedIdeaFileIndexService(
    private val project: Project
) : FileIndexService {
    
    private val projectBasePath = project.basePath ?: ""
    private val psiManager = PsiManager.getInstance(project)
    
    override suspend fun initialize(rootPath: String) {
        // IDEA 自动管理索引，无需手动初始化
    }
    
    override suspend fun indexPath(path: String, recursive: Boolean) {
        // IDEA 自动管理索引
    }
    
    override suspend fun searchFiles(
        query: String,
        maxResults: Int,
        fileTypes: List<String>
    ): List<IndexedFileInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<IndexedFileInfo>()
        
        ReadAction.compute<Unit, Exception> {
            val scope = GlobalSearchScope.projectScope(project)
            
            // 1. 精确匹配文件名
            val exactMatches = FilenameIndex.getFilesByName(project, query, scope)
            exactMatches.forEach { psiFile ->
                if (results.size >= maxResults) return@forEach
                psiFile.virtualFile?.let { vf ->
                    results.add(createFileInfo(vf))
                }
            }
            
            // 2. 模糊匹配文件名
            if (results.size < maxResults) {
                val allFilenames = FilenameIndex.getAllFilenames(project)
                allFilenames.forEach { filename ->
                    if (results.size >= maxResults) return@forEach
                    
                    if (filename.contains(query, ignoreCase = true)) {
                        val files = FilenameIndex.getFilesByName(project, filename, scope)
                        files.forEach { psiFile ->
                            if (results.size >= maxResults) return@forEach
                            psiFile.virtualFile?.let { vf ->
                                val fileInfo = createFileInfo(vf)
                                if (!results.any { it.relativePath == fileInfo.relativePath }) {
                                    results.add(fileInfo)
                                }
                            }
                        }
                    }
                }
            }
            
            // 3. 按文件类型过滤
            if (fileTypes.isNotEmpty()) {
                results.removeAll { file ->
                    !fileTypes.any { type ->
                        file.name.endsWith(".$type", ignoreCase = true)
                    }
                }
            }
        }
        
        results
    }
    
    override suspend fun findFilesByName(fileName: String, maxResults: Int): List<IndexedFileInfo> = 
        withContext(Dispatchers.IO) {
            val results = mutableListOf<IndexedFileInfo>()
            
            ReadAction.compute<List<IndexedFileInfo>, Exception> {
                val scope = GlobalSearchScope.projectScope(project)
                val files = FilenameIndex.getFilesByName(project, fileName, scope)
                
                files.take(maxResults).mapNotNull { psiFile ->
                    psiFile.virtualFile?.let { createFileInfo(it) }
                }
            }
        }
    
    override suspend fun searchSymbols(
        query: String,
        symbolTypes: List<SymbolType>,
        maxResults: Int
    ): List<IndexedSymbolInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<IndexedSymbolInfo>()
        
        ReadAction.compute<Unit, Exception> {
            val cache = PsiShortNamesCache.getInstance(project)
            val scope = GlobalSearchScope.projectScope(project)
            
            // 搜索类
            if (symbolTypes.isEmpty() || SymbolType.CLASS in symbolTypes) {
                cache.getClassesByName(query, scope).forEach { psiClass ->
                    if (results.size >= maxResults) return@forEach
                    results.add(createSymbolInfo(psiClass))
                }
            }
            
            // 搜索方法
            if (symbolTypes.isEmpty() || SymbolType.FUNCTION in symbolTypes) {
                cache.getMethodsByName(query, scope).forEach { method ->
                    if (results.size >= maxResults) return@forEach
                    results.add(createSymbolInfo(method))
                }
            }
            
            // 搜索字段
            if (symbolTypes.isEmpty() || SymbolType.VARIABLE in symbolTypes) {
                cache.getFieldsByName(query, scope).forEach { field ->
                    if (results.size >= maxResults) return@forEach
                    results.add(createSymbolInfo(field))
                }
            }
        }
        
        results
    }
    
    override suspend fun getRecentFiles(maxResults: Int): List<IndexedFileInfo> = 
        withContext(Dispatchers.IO) {
            val results = mutableListOf<IndexedFileInfo>()
            
            ReadAction.compute<List<IndexedFileInfo>, Exception> {
                val editorManager = FileEditorManager.getInstance(project)
                val openFiles = editorManager.openFiles
                
                // 先添加当前打开的文件
                openFiles.take(maxResults).forEach { vf ->
                    results.add(createFileInfo(vf))
                }
                
                // 如果不够，添加最近编辑的文件
                if (results.size < maxResults) {
                    val recentFiles = editorManager.selectedFiles
                    recentFiles.forEach { vf ->
                        if (results.size >= maxResults) return@forEach
                        val fileInfo = createFileInfo(vf)
                        if (!results.any { it.relativePath == fileInfo.relativePath }) {
                            results.add(fileInfo)
                        }
                    }
                }
                
                results
            }
        }
    
    override suspend fun getRecentlyModifiedFiles(projectPath: String, limit: Int): List<IndexedFileInfo> = 
        withContext(Dispatchers.IO) {
            val results = mutableListOf<IndexedFileInfo>()
            
            ReadAction.compute<List<IndexedFileInfo>, Exception> {
                val projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://$projectPath")
                if (projectRoot != null) {
                    val files = mutableListOf<VirtualFile>()
                    collectFiles(projectRoot, files, limit * 2) // 收集更多文件用于排序
                    
                    files.sortedByDescending { it.timeStamp }
                        .take(limit)
                        .forEach { vf ->
                            results.add(createFileInfo(vf))
                        }
                }
                results
            }
        }
    
    override suspend fun getFileContent(filePath: String): String? = withContext(Dispatchers.IO) {
        ReadAction.compute<String?, Exception> {
            val absolutePath = if (File(filePath).isAbsolute) {
                filePath
            } else {
                File(projectBasePath, filePath).absolutePath
            }
            
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$absolutePath")
            virtualFile?.let { vf ->
                String(vf.contentsToByteArray(), Charsets.UTF_8)
            }
        }
    }
    
    override suspend fun getFileSymbols(filePath: String): List<IndexedSymbolInfo> = 
        withContext(Dispatchers.IO) {
            val results = mutableListOf<IndexedSymbolInfo>()
            
            ReadAction.compute<List<IndexedSymbolInfo>, Exception> {
                val absolutePath = if (File(filePath).isAbsolute) {
                    filePath
                } else {
                    File(projectBasePath, filePath).absolutePath
                }
                
                val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$absolutePath")
                virtualFile?.let { vf ->
                    val psiFile = psiManager.findFile(vf)
                    psiFile?.let {
                        collectSymbols(it, results)
                    }
                }
                results
            }
        }
    
    override fun isIndexReady(): Boolean {
        // IDEA 索引通常总是准备好的
        return true
    }
    
    override suspend fun getIndexStats(): IndexStats {
        // 返回基本的索引统计信息
        return IndexStats(
            totalFiles = 0,
            indexedFiles = 0,
            totalSymbols = 0,
            lastIndexTime = System.currentTimeMillis()
        )
    }
    
    override suspend fun refreshIndex() {
        // IDEA 自动管理索引，无需手动刷新
    }
    
    override suspend fun cleanup() {
        // IDEA 自动管理索引清理
    }
    
    // ========== 辅助方法 ==========
    
    private fun createFileInfo(virtualFile: VirtualFile): IndexedFileInfo {
        val relativePath = getRelativePath(virtualFile.path)
        val absolutePath = virtualFile.path
        return IndexedFileInfo(
            name = virtualFile.name,
            relativePath = relativePath,
            absolutePath = absolutePath,
            fileType = virtualFile.extension ?: "",
            size = virtualFile.length,
            lastModified = virtualFile.timeStamp,
            isDirectory = virtualFile.isDirectory
        )
    }
    
    private fun createSymbolInfo(element: PsiNamedElement): IndexedSymbolInfo {
        val containingFile = element.containingFile
        val virtualFile = containingFile.virtualFile
        val relativePath = virtualFile?.let { getRelativePath(it.path) } ?: ""
        
        return IndexedSymbolInfo(
            name = element.name ?: "",
            type = getSymbolType(element),
            filePath = relativePath,
            line = getLineNumber(element),
            signature = getSignature(element),
            documentation = getDocumentation(element)
        )
    }
    
    private fun getSymbolType(element: PsiElement): SymbolType {
        return when (element) {
            is PsiClass -> when {
                element.isInterface -> SymbolType.INTERFACE
                element.isEnum -> SymbolType.ENUM
                else -> SymbolType.CLASS
            }
            is PsiMethod -> SymbolType.FUNCTION
            is PsiField -> when {
                element.hasModifierProperty(PsiModifier.FINAL) -> SymbolType.CONSTANT
                else -> SymbolType.VARIABLE
            }
            else -> SymbolType.VARIABLE
        }
    }
    
    private fun getLineNumber(element: PsiElement): Int {
        val file = element.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return 0
        val offset = element.textOffset
        return document.getLineNumber(offset) + 1
    }
    
    private fun getSignature(element: PsiElement): String? {
        return when (element) {
            is PsiMethod -> {
                val params = element.parameterList.parameters.joinToString(", ") { param ->
                    "${param.type.presentableText} ${param.name}"
                }
                "${element.returnType?.presentableText ?: "void"} ${element.name}($params)"
            }
            is PsiField -> "${element.type.presentableText} ${element.name}"
            else -> null
        }
    }
    
    private fun getDocumentation(element: PsiElement): String? {
        return when (element) {
            is PsiDocCommentOwner -> element.docComment?.text
            else -> null
        }
    }
    
    private fun collectSymbols(psiFile: PsiFile, results: MutableList<IndexedSymbolInfo>) {
        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is PsiNamedElement && element !is PsiFile) {
                    when (element) {
                        is PsiClass, is PsiMethod, is PsiField -> {
                            results.add(createSymbolInfo(element))
                        }
                    }
                }
                super.visitElement(element)
            }
        })
    }
    
    private fun collectFiles(dir: VirtualFile, files: MutableList<VirtualFile>, limit: Int) {
        if (files.size >= limit) return
        
        dir.children.forEach { child ->
            if (files.size >= limit) return
            
            if (child.isDirectory && !child.name.startsWith(".")) {
                collectFiles(child, files, limit)
            } else if (!child.isDirectory) {
                files.add(child)
            }
        }
    }
    
    private fun getRelativePath(absolutePath: String): String {
        return if (absolutePath.startsWith(projectBasePath)) {
            absolutePath.substring(projectBasePath.length).trimStart('/', '\\')
        } else {
            absolutePath
        }
    }
    
}