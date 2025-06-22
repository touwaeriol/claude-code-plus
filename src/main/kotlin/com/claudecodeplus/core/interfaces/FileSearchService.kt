package com.claudecodeplus.core.interfaces

/**
 * 文件搜索服务接口
 */
interface FileSearchService {
    /**
     * 搜索文件
     * @param searchText 搜索文本
     * @return 文件路径列表
     */
    suspend fun searchFiles(searchText: String): List<String>
    
    /**
     * 获取最近打开的文件
     * @param limit 限制数量
     * @return 文件路径列表
     */
    suspend fun getRecentFiles(limit: Int = 10): List<String>
    
    /**
     * 检查文件是否存在
     */
    fun fileExists(path: String): Boolean
    
    /**
     * 读取文件内容
     */
    fun readFileContent(path: String): String?
    
    /**
     * 获取索引服务（如果支持）
     */
    fun getIndexService(): FileIndexService? = null
}