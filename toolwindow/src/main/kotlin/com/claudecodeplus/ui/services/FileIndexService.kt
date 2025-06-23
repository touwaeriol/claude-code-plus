package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.SymbolType

/**
 * 文件索引服务接口（UI专用）
 * 提供文件和符号的索引与搜索功能
 */
interface FileIndexService {
    /**
     * 索引指定路径下的文件
     */
    fun indexFiles(rootPath: String)
    
    /**
     * 搜索文件
     */
    fun searchFiles(query: String): List<FileInfo>
    
    /**
     * 获取文件内容
     */
    fun getFileContent(path: String): String
    
    /**
     * 查找符号
     */
    fun findSymbols(query: String): List<SymbolInfo>
}

/**
 * 文件信息（扩展版）
 */
data class FileInfo(
    val path: String,
    val name: String,
    val extension: String
)

/**
 * 符号信息
 */
data class SymbolInfo(
    val name: String,
    val type: SymbolType,
    val file: String,
    val line: Int
)