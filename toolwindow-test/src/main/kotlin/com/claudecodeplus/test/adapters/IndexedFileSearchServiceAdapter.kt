package com.claudecodeplus.test.adapters

import com.claudecodeplus.ui.services.FileSearchService
import com.claudecodeplus.ui.services.FileInfo
import com.claudecodeplus.test.IndexedFileSearchService

/**
 * 将 IndexedFileSearchService 适配到通用的 FileSearchService 接口
 */
class IndexedFileSearchServiceAdapter(
    private val indexedFileSearchService: IndexedFileSearchService,
    private val projectPath: String = ""
) : FileSearchService {
    
    override suspend fun searchFiles(query: String, limit: Int): List<FileInfo> {
        return indexedFileSearchService.searchFiles(query).map { filePath ->
            val file = java.io.File(filePath)
            FileInfo(
                name = file.name,
                path = filePath,
                relativePath = filePath.replace(projectPath + "/", ""),
                isDirectory = file.isDirectory
            )
        }
    }
    
    override suspend fun getFileContent(filePath: String): String? {
        return indexedFileSearchService.readFileContent(filePath)
    }
}