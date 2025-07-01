package com.claudecodeplus.plugin.adapters

import com.claudecodeplus.ui.services.*
import com.claudecodeplus.ui.models.SymbolType
import com.claudecodeplus.idea.IdeaFileSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 将 IdeaFileSearchService 适配到新的 FileIndexService 接口
 */
class IdeaFileIndexServiceAdapter(
    private val ideaFileSearchService: IdeaFileSearchService
) : FileIndexService {
    
    private var isInitialized = false
    
    override suspend fun initialize(rootPath: String) {
        // IDEA的文件搜索服务不需要显式初始化，依赖IDEA平台的索引
        isInitialized = true
    }
    
    override suspend fun indexPath(path: String, recursive: Boolean) {
        // IDEA自动管理索引，不需要手动索引
    }
    
    override suspend fun searchFiles(
        query: String, 
        maxResults: Int,
        fileTypes: List<String>
    ): List<IndexedFileInfo> = withContext(Dispatchers.IO) {
        return@withContext try {
            val results = ideaFileSearchService.searchFiles(query, maxResults)
            results.map { fileResult ->
                IndexedFileInfo(
                    name = fileResult.fileName,
                    relativePath = fileResult.relativePath,
                    absolutePath = fileResult.filePath,
                    fileType = fileResult.fileName.substringAfterLast('.', ""),
                    size = 0L, // 简化处理
                    lastModified = 0L, // 简化处理
                    isDirectory = false,
                    language = detectLanguage(fileResult.fileName.substringAfterLast('.', "")),
                    encoding = "UTF-8"
                )
            }.filter { file ->
                // 应用文件类型过滤
                fileTypes.isEmpty() || fileTypes.contains(file.fileType.lowercase())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun findFilesByName(fileName: String, maxResults: Int): List<IndexedFileInfo> {
        return searchFiles(fileName, maxResults).filter { 
            it.name.equals(fileName, ignoreCase = true)
        }
    }
    
    override suspend fun searchSymbols(
        query: String,
        symbolTypes: List<SymbolType>,
        maxResults: Int
    ): List<IndexedSymbolInfo> {
        // TODO: 实现符号搜索，需要使用IDEA的PSI API
        return emptyList()
    }
    
    override suspend fun getRecentFiles(maxResults: Int): List<IndexedFileInfo> = withContext(Dispatchers.IO) {
        return@withContext try {
            val recentFiles = ideaFileSearchService.getRecentFiles(maxResults)
            recentFiles.map { filePath ->
                val fileName = filePath.substringAfterLast('/')
                IndexedFileInfo(
                    name = fileName,
                    relativePath = filePath,
                    absolutePath = filePath,
                    fileType = fileName.substringAfterLast('.', ""),
                    size = 0L,
                    lastModified = System.currentTimeMillis(),
                    isDirectory = false,
                    language = detectLanguage(fileName.substringAfterLast('.', "")),
                    encoding = "UTF-8"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun getFileContent(filePath: String): String? {
        return ideaFileSearchService.getFileContent(filePath)
    }
    
    override suspend fun getFileSymbols(filePath: String): List<IndexedSymbolInfo> {
        // TODO: 实现文件符号提取
        return emptyList()
    }
    
    override fun isIndexReady(): Boolean = isInitialized
    
    override suspend fun getIndexStats(): IndexStats {
        return IndexStats(
            totalFiles = 0, // 简化处理
            indexedFiles = 0,
            totalSymbols = 0,
            lastIndexTime = System.currentTimeMillis(),
            indexSizeBytes = 0,
            supportedFileTypes = listOf("kt", "java", "js", "ts", "py", "md", "json", "xml")
        )
    }
    
    override suspend fun refreshIndex() {
        // IDEA自动管理索引刷新
    }
    
    override suspend fun cleanup() {
        // 不需要清理
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
            "yml", "yaml" -> "YAML"
            "html", "htm" -> "HTML"
            "css" -> "CSS"
            else -> null
        }
    }
} 