package com.claudecodeplus.test

import com.claudecodeplus.core.interfaces.FileIndexService
import com.claudecodeplus.core.interfaces.FileSearchService
import java.io.File

/**
 * 使用索引的文件搜索服务实现
 * 结合了索引的快速搜索和基本的文件操作
 */
class IndexedFileSearchService(
    private val projectPath: String,
    private val indexService: FileIndexService
) : FileSearchService {
    
    override suspend fun searchFiles(searchText: String): List<String> {
        return if (searchText.isBlank()) {
            getRecentFiles()
        } else {
            indexService.searchByName(searchText)
        }
    }
    
    override suspend fun getRecentFiles(limit: Int): List<String> {
        return indexService.getRecentFiles(limit)
    }
    
    override fun fileExists(path: String): Boolean {
        val fullPath = if (path.startsWith("/")) path else "$projectPath/$path"
        return File(fullPath).exists()
    }
    
    override fun readFileContent(path: String): String? {
        return try {
            val fullPath = if (path.startsWith("/")) path else "$projectPath/$path"
            File(fullPath).readText()
        } catch (e: Exception) {
            null
        }
    }
    
    override fun getIndexService(): FileIndexService = indexService
}