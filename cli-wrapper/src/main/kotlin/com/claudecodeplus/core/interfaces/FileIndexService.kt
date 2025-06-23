package com.claudecodeplus.core.interfaces

/**
 * 文件索引服务接口
 * 提供文件快速搜索功能
 */
interface FileIndexService {
    /**
     * 根据文件名搜索
     * @param query 搜索关键词
     * @param limit 返回结果数量限制
     * @return 匹配的文件路径列表
     */
    suspend fun searchByName(query: String, limit: Int = 20): List<String>
    
    /**
     * 根据文件内容搜索
     * @param query 搜索关键词
     * @param filePattern 文件模式，如 "*.kt"
     * @param limit 返回结果数量限制
     * @return 匹配的文件路径列表
     */
    suspend fun searchByContent(query: String, filePattern: String? = null, limit: Int = 20): List<String>
    
    /**
     * 获取最近修改的文件
     * @param limit 返回结果数量限制
     * @return 文件路径列表
     */
    suspend fun getRecentFiles(limit: Int = 10): List<String>
    
    /**
     * 刷新索引
     * @param path 要刷新的路径，null表示刷新整个项目
     */
    suspend fun refreshIndex(path: String? = null)
    
    /**
     * 判断索引是否已准备好
     */
    fun isIndexReady(): Boolean
}

/**
 * 文件索引条目
 */
data class FileIndexEntry(
    val path: String,
    val name: String,
    val extension: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean
)