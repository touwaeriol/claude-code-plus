package com.claudecodeplus.ui.services

/**
 * 文件搜索服务接口
 * 提供文件搜索功能的抽象，不依赖具体的 IDE 实现
 */
interface FileSearchService {
    /**
     * 根据文件名搜索文件
     * @param query 搜索关键词
     * @param limit 返回结果数量限制
     * @return 匹配的文件列表
     */
    suspend fun searchFiles(query: String, limit: Int = 20): List<SimpleFileInfo>
    
    /**
     * 获取文件内容
     * @param filePath 文件路径
     * @return 文件内容，如果文件不存在返回 null
     */
    suspend fun getFileContent(filePath: String): String?
}

/**
 * 简单文件信息（用于文件搜索服务）
 */
data class SimpleFileInfo(
    val name: String,
    val path: String,
    val relativePath: String,
    val isDirectory: Boolean = false
)