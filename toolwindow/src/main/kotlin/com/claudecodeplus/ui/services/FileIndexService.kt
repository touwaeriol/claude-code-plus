package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.SymbolType

/**
 * 统一的文件索引服务接口
 * 提供文件和符号的索引与搜索功能
 * 支持不同的实现：测试环境使用简单工具，IDE环境使用平台API
 */
interface FileIndexService {
    /**
     * 初始化索引服务
     * @param rootPath 项目根路径
     */
    suspend fun initialize(rootPath: String)
    
    /**
     * 索引指定路径下的文件
     * @param path 要索引的路径
     * @param recursive 是否递归索引子目录
     */
    suspend fun indexPath(path: String, recursive: Boolean = true)
    
    /**
     * 搜索文件
     * @param query 搜索查询
     * @param maxResults 最大结果数
     * @param fileTypes 文件类型过滤（如 "kt", "java"）
     * @return 搜索结果列表
     */
    suspend fun searchFiles(
        query: String, 
        maxResults: Int = 50,
        fileTypes: List<String> = emptyList()
    ): List<IndexedFileInfo>
    
    /**
     * 根据文件名精确查找
     * @param fileName 文件名
     * @param maxResults 最大结果数
     */
    suspend fun findFilesByName(fileName: String, maxResults: Int = 20): List<IndexedFileInfo>
    
    /**
     * 搜索符号（类、函数、变量等）
     * @param query 搜索查询
     * @param symbolTypes 符号类型过滤
     * @param maxResults 最大结果数
     */
    suspend fun searchSymbols(
        query: String,
        symbolTypes: List<SymbolType> = emptyList(),
        maxResults: Int = 50
    ): List<IndexedSymbolInfo>
    
    /**
     * 获取最近访问的文件
     * @param maxResults 最大结果数
     */
    suspend fun getRecentFiles(maxResults: Int = 20): List<IndexedFileInfo>
    
    /**
     * 获取最近修改的文件
     * @param projectPath 项目路径
     * @param limit 限制数量
     */
    suspend fun getRecentlyModifiedFiles(projectPath: String, limit: Int = 20): List<IndexedFileInfo>
    
    /**
     * 获取文件内容
     * @param filePath 文件路径（相对路径）
     * @return 文件内容，如果文件不存在返回null
     */
    suspend fun getFileContent(filePath: String): String?
    
    /**
     * 获取文件的符号列表
     * @param filePath 文件路径
     */
    suspend fun getFileSymbols(filePath: String): List<IndexedSymbolInfo>
    
    /**
     * 检查索引是否准备就绪
     */
    fun isIndexReady(): Boolean
    
    /**
     * 获取索引统计信息
     */
    suspend fun getIndexStats(): IndexStats
    
    /**
     * 刷新索引（重新扫描文件系统）
     */
    suspend fun refreshIndex()
    
    /**
     * 清理索引
     */
    suspend fun cleanup()
}

/**
 * 索引的文件信息
 */
data class IndexedFileInfo(
    val name: String,                    // 文件名
    val relativePath: String,            // 相对于项目根的路径
    val absolutePath: String,            // 绝对路径
    val fileType: String,               // 文件扩展名
    val size: Long,                     // 文件大小
    val lastModified: Long,             // 最后修改时间
    val isDirectory: Boolean = false,    // 是否为目录
    val language: String? = null,        // 编程语言（如果可检测）
    val encoding: String? = null         // 文件编码
) {
    /**
     * 获取文件类型图标
     */
    fun getIcon(): String {
        return if (isDirectory) {
            "📁"
        } else {
            when (fileType.lowercase()) {
                "kt" -> "🔷"
                "java" -> "☕"
                "js", "ts" -> "💛"
                "py" -> "🐍"
                "md" -> "📝"
                "json" -> "📋"
                "xml" -> "🔖"
                "yml", "yaml" -> "⚙️"
                "html", "htm" -> "🌐"
                "css" -> "🎨"
                "png", "jpg", "jpeg", "gif" -> "🖼️"
                "pdf" -> "📕"
                "txt" -> "📄"
                "gradle" -> "🔧"
                "properties" -> "⚙️"
                else -> "📄"
            }
        }
    }
    
    /**
     * 转换为简单文件信息（用于向后兼容）
     */
    fun toSimpleFileInfo(): SimpleFileInfo {
        return SimpleFileInfo(
            name = name,
            path = absolutePath,
            relativePath = relativePath,
            isDirectory = isDirectory
        )
    }
}

/**
 * 索引的符号信息
 */
data class IndexedSymbolInfo(
    val name: String,                    // 符号名称
    val type: SymbolType,               // 符号类型
    val filePath: String,               // 所在文件路径
    val line: Int,                      // 行号
    val column: Int = 0,                // 列号
    val signature: String? = null,       // 符号签名
    val documentation: String? = null,   // 文档注释
    val visibility: String? = null,      // 可见性（public, private等）
    val parentSymbol: String? = null     // 父符号（如类名）
)

/**
 * 索引统计信息
 */
data class IndexStats(
    val totalFiles: Int,                 // 总文件数
    val indexedFiles: Int,              // 已索引文件数
    val totalSymbols: Int,              // 总符号数
    val lastIndexTime: Long,            // 最后索引时间
    val indexSizeBytes: Long = 0,       // 索引大小（字节）
    val supportedFileTypes: List<String> = emptyList() // 支持的文件类型
)