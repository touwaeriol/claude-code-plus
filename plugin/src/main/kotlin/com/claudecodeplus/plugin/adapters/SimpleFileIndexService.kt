package com.claudecodeplus.plugin.adapters

import com.claudecodeplus.ui.services.*
import com.claudecodeplus.ui.models.SymbolType
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.application.ReadAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.openapi.diagnostic.Logger
import java.io.File

/**
 * 简单的 FileIndexService 实现
 * 提供基本的文件搜索功能
 */
class SimpleFileIndexService(
    private val project: Project
) : FileIndexService {
    
    companion object {
        private val logger = Logger.getInstance(SimpleFileIndexService::class.java)
    }
    
    override suspend fun initialize(rootPath: String) {
        // IntelliJ 自动管理索引，不需要手动初始化
    }
    
    override suspend fun indexPath(path: String, recursive: Boolean) {
        // IntelliJ 自动管理索引
    }
    
    override suspend fun searchFiles(
        query: String, 
        maxResults: Int,
        fileTypes: List<String>
    ): List<IndexedFileInfo> = withContext(Dispatchers.IO) {
        try {
            ReadAction.compute<List<IndexedFileInfo>, Exception> {
                val psiFiles = FilenameIndex.getFilesByName(
                    project, 
                    query, 
                    GlobalSearchScope.projectScope(project)
                )
                
                psiFiles.take(maxResults).mapNotNull { psiFile ->
                    createFileInfo(psiFile.virtualFile)
                }.filter { file ->
                    fileTypes.isEmpty() || fileTypes.contains(file.fileType.lowercase())
                }
            }
        } catch (e: Exception) {
            logger.warn("Search failed: ${e.message}")
            emptyList()
        }
    }
    
    override suspend fun findFilesByName(fileName: String, maxResults: Int): List<IndexedFileInfo> {
        return searchFiles(fileName, maxResults)
    }
    
    override suspend fun searchSymbols(
        query: String,
        symbolTypes: List<SymbolType>,
        maxResults: Int
    ): List<IndexedSymbolInfo> {
        // 暂不实现符号搜索
        return emptyList()
    }
    
    override suspend fun getRecentFiles(maxResults: Int): List<IndexedFileInfo> {
        // 暂不实现最近文件功能
        return emptyList()
    }
    
    override suspend fun getFileContent(filePath: String): String? = withContext(Dispatchers.IO) {
        try {
            File(filePath).readText()
        } catch (e: Exception) {
            logger.warn("Failed to read file: $filePath", e)
            null
        }
    }
    
    override suspend fun getFileSymbols(filePath: String): List<IndexedSymbolInfo> {
        // 暂不实现
        return emptyList()
    }
    
    override fun isIndexReady(): Boolean = true
    
    override suspend fun getIndexStats(): IndexStats {
        return IndexStats(
            totalFiles = 0,
            indexedFiles = 0,
            totalSymbols = 0,
            lastIndexTime = System.currentTimeMillis(),
            indexSizeBytes = 0,
            supportedFileTypes = listOf("kt", "java", "js", "ts", "py", "md", "json", "xml")
        )
    }
    
    override suspend fun refreshIndex() {
        // IntelliJ 自动管理
    }
    
    override suspend fun cleanup() {
        // 无需清理
    }
    
    private fun createFileInfo(virtualFile: VirtualFile): IndexedFileInfo? {
        return try {
            val projectPath = project.basePath ?: return null
            val relativePath = virtualFile.path.removePrefix(projectPath).removePrefix("/")
            
            IndexedFileInfo(
                name = virtualFile.name,
                relativePath = relativePath,
                absolutePath = virtualFile.path,
                fileType = virtualFile.extension ?: "",
                size = virtualFile.length,
                lastModified = virtualFile.modificationStamp,
                isDirectory = virtualFile.isDirectory,
                language = detectLanguage(virtualFile.extension ?: ""),
                encoding = virtualFile.charset.name()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun detectLanguage(extension: String): String? {
        return when (extension.lowercase()) {
            "kt", "kts" -> "Kotlin"
            "java" -> "Java"
            "js" -> "JavaScript"
            "ts" -> "TypeScript"
            "py" -> "Python"
            "md" -> "Markdown"
            "json" -> "JSON"
            "xml" -> "XML"
            else -> null
        }
    }
}