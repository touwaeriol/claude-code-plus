package com.claudecodeplus.test

import com.claudecodeplus.ui.services.*
import kotlinx.coroutines.runBlocking

/**
 * 测试用的FileIndexService适配器
 * 将SimpleFileIndexService适配到FileSearchService接口，用于向后兼容
 */
class TestFileIndexServiceAdapter(
    private val fileIndexService: SimpleFileIndexService,
    private val rootPath: String
) : FileSearchService {
    
    init {
        // 初始化索引
        runBlocking {
            fileIndexService.initialize(rootPath)
        }
    }
    
    override suspend fun searchFiles(query: String, limit: Int): List<SimpleFileInfo> {
        return fileIndexService.searchFiles(query, limit).map { indexedFile ->
            indexedFile.toSimpleFileInfo()
        }
    }
    
    override suspend fun getFileContent(filePath: String): String? {
        return fileIndexService.getFileContent(filePath)
    }
} 