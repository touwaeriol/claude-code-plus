package com.claudecodeplus.plugin.adapters

import com.claudecodeplus.ui.services.FileSearchService
import com.claudecodeplus.ui.services.SimpleFileInfo
import com.claudecodeplus.idea.IdeaFileSearchService

/**
 * 将 IdeaFileSearchService 适配到通用的 FileSearchService 接口
 */
class IdeaFileSearchServiceAdapter(
    private val ideaFileSearchService: IdeaFileSearchService
) : FileSearchService {
    
    override suspend fun searchFiles(query: String, limit: Int): List<SimpleFileInfo> {
        return ideaFileSearchService.searchFiles(query, limit).map { fileResult ->
            SimpleFileInfo(
                name = fileResult.fileName,
                path = fileResult.filePath,
                relativePath = fileResult.relativePath,
                isDirectory = false
            )
        }
    }
    
    override suspend fun getFileContent(filePath: String): String? {
        return ideaFileSearchService.getFileContent(filePath)
    }
}