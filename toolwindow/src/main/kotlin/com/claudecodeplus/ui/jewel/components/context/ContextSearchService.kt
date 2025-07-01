package com.claudecodeplus.ui.jewel.components.context

import kotlinx.coroutines.flow.Flow

/**
 * 上下文搜索服务接口
 * 提供文件搜索和URL验证功能
 */
interface ContextSearchService {
    
    /**
     * 搜索文件
     * @param query 搜索查询字符串
     * @param maxResults 最大结果数，默认50
     * @return 搜索结果列表，按权重排序
     */
    suspend fun searchFiles(query: String, maxResults: Int = 50): List<FileSearchResult>
    
    /**
     * 实时搜索文件（Flow版本）
     * @param query 搜索查询字符串
     * @param maxResults 最大结果数
     * @return 搜索结果的Flow
     */
    fun searchFilesFlow(query: String, maxResults: Int = 50): Flow<List<FileSearchResult>>
    
    /**
     * 获取项目根目录文件列表
     * @param maxResults 最大结果数
     * @return 文件列表
     */
    suspend fun getRootFiles(maxResults: Int = 50): List<FileContextItem>
    
    /**
     * 验证URL格式
     * @param url URL字符串
     * @return 是否为有效URL
     */
    fun validateUrl(url: String): Boolean
    
    /**
     * 获取URL的基本信息（标题、描述等）
     * @param url URL字符串
     * @return Web上下文项，如果获取失败返回null
     */
    suspend fun getWebInfo(url: String): WebContextItem?
    
    /**
     * 获取文件详细信息
     * @param relativePath 相对路径
     * @return 文件上下文项，如果文件不存在返回null
     */
    suspend fun getFileInfo(relativePath: String): FileContextItem?
    
    /**
     * 检查文件是否应该被排除
     * @param path 文件路径
     * @return 是否应该排除
     */
    fun shouldExcludeFile(path: String): Boolean {
        val excludePatterns = listOf(
            ".git/",
            "node_modules/",
            "build/",
            "target/",
            "dist/",
            ".idea/",
            ".vscode/",
            ".gradle/",
            "out/",
            "bin/",
            ".DS_Store",
            "*.tmp",
            "*.log",
            "*.cache"
        )
        
        return excludePatterns.any { pattern ->
            when {
                pattern.endsWith("/") -> path.contains("/$pattern") || path.startsWith(pattern)
                pattern.startsWith("*.") -> path.endsWith(pattern.substring(1))
                else -> path.contains(pattern)
            }
        }
    }
    
    /**
     * 计算搜索权重
     * @param fileName 文件名
     * @param filePath 文件路径
     * @param query 搜索查询
     * @param config 权重配置
     * @return 搜索结果（包含权重和匹配类型）
     */
    fun calculateSearchWeight(
        fileName: String,
        filePath: String,
        query: String,
        config: SearchWeight = SearchWeight()
    ): FileSearchResult? {
        if (query.isEmpty()) return null
        
        val queryLower = query.lowercase()
        val fileNameLower = fileName.lowercase()
        val filePathLower = filePath.lowercase()
        
        val (weight, matchType) = when {
            fileNameLower == queryLower -> config.exactMatch to FileSearchResult.MatchType.EXACT_NAME
            fileNameLower.startsWith(queryLower) -> config.prefixMatch to FileSearchResult.MatchType.PREFIX_NAME
            fileNameLower.contains(queryLower) -> config.containsMatch to FileSearchResult.MatchType.CONTAINS_NAME
            filePathLower.contains(queryLower) -> config.pathMatch to FileSearchResult.MatchType.PATH_MATCH
            else -> return null
        }
        
        // 创建虚拟的FileContextItem用于权重计算
        val item = FileContextItem(
            name = fileName,
            relativePath = filePath,
            absolutePath = filePath,
            isDirectory = false,
            fileType = fileName.substringAfterLast('.', "")
        )
        
        return FileSearchResult(item, weight, matchType)
    }
} 