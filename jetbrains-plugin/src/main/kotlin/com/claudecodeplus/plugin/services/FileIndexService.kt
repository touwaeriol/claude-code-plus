package com.claudecodeplus.plugin.services

/**
 * 文件索引服务接口
 * 提供文件搜索和索引功能
 */
interface FileIndexService {
    /**
     * 初始化索引
     */
    suspend fun initialize(rootPath: String)
    
    /**
     * 索引指定路径
     */
    suspend fun indexPath(path: String, recursive: Boolean)
    
    /**
     * 搜索文件
     */
    suspend fun searchFiles(
        query: String,
        maxResults: Int,
        fileTypes: List<String> = emptyList()
    ): List<IndexedFileInfo>
    
    /**
     * 按文件名查找文件
     */
    suspend fun findFilesByName(fileName: String, maxResults: Int): List<IndexedFileInfo>
    
    /**
     * 搜索符号
     */
    suspend fun searchSymbols(
        query: String,
        symbolTypes: List<SymbolType>,
        maxResults: Int
    ): List<IndexedSymbolInfo>
    
    /**
     * 获取最近文件
     */
    suspend fun getRecentFiles(maxResults: Int): List<IndexedFileInfo>
    
    /**
     * 获取最近修改的文件
     */
    suspend fun getRecentlyModifiedFiles(projectPath: String, limit: Int): List<IndexedFileInfo>
    
    /**
     * 获取文件内容
     */
    suspend fun getFileContent(filePath: String): String?
    
    /**
     * 获取文件符号
     */
    suspend fun getFileSymbols(filePath: String): List<IndexedSymbolInfo>
    
    /**
     * 检查索引是否就绪
     */
    fun isIndexReady(): Boolean
    
    /**
     * 获取索引统计信息
     */
    suspend fun getIndexStats(): IndexStats
    
    /**
     * 刷新索引
     */
    suspend fun refreshIndex()
    
    /**
     * 清理索引
     */
    suspend fun cleanup()
}

/**
 * 索引文件信息
 */
data class IndexedFileInfo(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val fileType: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean = false,
    val language: String? = null,
    val encoding: String? = null
)

/**
 * 索引符号信息
 */
data class IndexedSymbolInfo(
    val name: String,
    val type: SymbolType,
    val filePath: String,
    val lineNumber: Int? = null,
    val columnNumber: Int? = null,
    val signature: String? = null
)

/**
 * 符号类型枚举
 */
enum class SymbolType {
    CLASS,
    INTERFACE,
    FUNCTION,
    METHOD,
    PROPERTY,
    FIELD,
    VARIABLE,
    ENUM,
    ENUM_VALUE,
    PACKAGE,
    MODULE,
    FILE
}

/**
 * 索引统计信息
 */
data class IndexStats(
    val totalFiles: Int,
    val indexedFiles: Int,
    val totalSymbols: Int,
    val lastIndexTime: Long,
    val indexSizeBytes: Long,
    val supportedFileTypes: List<String>
)



