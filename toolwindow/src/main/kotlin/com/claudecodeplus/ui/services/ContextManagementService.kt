package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

/**
 * 上下文管理服务
 */
class ContextManagementService {
    
    /**
     * 展开文件夹为文件列表
     */
    suspend fun expandFolder(
        folder: ContextItem.Folder,
        maxFiles: Int = 1000
    ): List<ContextItem.File> = withContext(Dispatchers.IO) {
        val path = Paths.get(folder.path)
        if (!path.exists() || !path.isDirectory()) {
            return@withContext emptyList()
        }
        
        val files = mutableListOf<ContextItem.File>()
        val includePattern = folder.includePattern?.let { Regex(it) }
        val excludePattern = folder.excludePattern?.let { Regex(it) }
        
        Files.walk(path)
            .filter { it.isRegularFile() }
            .filter { file ->
                val relativePath = path.relativize(file).toString()
                
                // 应用包含模式
                val matchesInclude = includePattern?.let { 
                    it.matches(relativePath) || it.matches(file.fileName.toString())
                } ?: true
                
                // 应用排除模式
                val matchesExclude = excludePattern?.let { 
                    it.matches(relativePath) || it.matches(file.fileName.toString())
                } ?: false
                
                matchesInclude && !matchesExclude
            }
            .limit(maxFiles.toLong())
            .forEach { file ->
                files.add(
                    ContextItem.File(
                        path = file.toString(),
                        addedAt = folder.addedAt
                    )
                )
            }
        
        files
    }
    
    /**
     * 合并上下文项，去重并保持顺序
     */
    fun mergeContextItems(
        existing: List<ContextItem>,
        new: List<ContextItem>
    ): List<ContextItem> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<ContextItem>()
        
        // 先添加现有项
        existing.forEach { item ->
            val key = getContextItemKey(item)
            if (seen.add(key)) {
                result.add(item)
            }
        }
        
        // 添加新项
        new.forEach { item ->
            val key = getContextItemKey(item)
            if (seen.add(key)) {
                result.add(item)
            }
        }
        
        return result
    }
    
    /**
     * 验证上下文项是否有效
     */
    suspend fun validateContext(
        items: List<ContextItem>
    ): ContextValidationResult = withContext(Dispatchers.IO) {
        val validItems = mutableListOf<ContextItem>()
        val invalidItems = mutableListOf<InvalidContextItem>()
        
        items.forEach { item ->
            when (item) {
                is ContextItem.File -> {
                    val file = File(item.path)
                    if (file.exists() && file.isFile) {
                        validItems.add(item)
                    } else {
                        invalidItems.add(
                            InvalidContextItem(
                                item = item,
                                reason = if (!file.exists()) "文件不存在" else "不是文件"
                            )
                        )
                    }
                }
                is ContextItem.Folder -> {
                    val folder = File(item.path)
                    if (folder.exists() && folder.isDirectory) {
                        validItems.add(item)
                    } else {
                        invalidItems.add(
                            InvalidContextItem(
                                item = item,
                                reason = if (!folder.exists()) "文件夹不存在" else "不是文件夹"
                            )
                        )
                    }
                }
                is ContextItem.CodeBlock -> {
                    // 代码块总是有效的
                    validItems.add(item)
                }
            }
        }
        
        ContextValidationResult(
            validItems = validItems,
            invalidItems = invalidItems
        )
    }
    
    /**
     * 计算上下文大小
     */
    suspend fun calculateContextSize(
        items: List<ContextItem>
    ): ContextSizeInfo = withContext(Dispatchers.IO) {
        var totalSize = 0L
        var fileCount = 0
        var folderCount = 0
        var codeBlockCount = 0
        
        items.forEach { item ->
            when (item) {
                is ContextItem.File -> {
                    val file = File(item.path)
                    if (file.exists()) {
                        totalSize += file.length()
                        fileCount++
                    }
                }
                is ContextItem.Folder -> {
                    folderCount++
                    // 估算文件夹大小（展开前）
                    val expanded = expandFolder(item, maxFiles = 100)
                    expanded.forEach { file ->
                        val f = File(file.path)
                        if (f.exists()) {
                            totalSize += f.length()
                        }
                    }
                }
                is ContextItem.CodeBlock -> {
                    totalSize += item.content.toByteArray().size.toLong()
                    codeBlockCount++
                }
            }
        }
        
        ContextSizeInfo(
            totalSizeBytes = totalSize,
            fileCount = fileCount,
            folderCount = folderCount,
            codeBlockCount = codeBlockCount,
            estimatedTokens = estimateTokens(totalSize)
        )
    }
    
    /**
     * 从文件路径列表创建上下文
     */
    fun createContextFromPaths(
        paths: List<String>,
        baseDir: String? = null
    ): List<ContextItem> {
        return paths.map { path ->
            val file = File(path)
            val displayPath = if (baseDir != null) {
                try {
                    Paths.get(baseDir).relativize(Paths.get(path)).toString()
                } catch (e: Exception) {
                    path
                }
            } else {
                path
            }
            
            when {
                file.isDirectory -> ContextItem.Folder(path = path)
                file.isFile -> ContextItem.File(path = path)
                else -> null
            }
        }.filterNotNull()
    }
    
    /**
     * 智能分组上下文项
     */
    fun groupContextItems(items: List<ContextItem>): Map<String, List<ContextItem>> {
        val groups = mutableMapOf<String, MutableList<ContextItem>>()
        
        items.forEach { item ->
            val group = when (item) {
                is ContextItem.File -> {
                    val extension = File(item.path).extension.lowercase()
                    when (extension) {
                        in listOf("kt", "java", "scala") -> "JVM 代码"
                        in listOf("js", "ts", "jsx", "tsx") -> "JavaScript/TypeScript"
                        in listOf("py", "pyw") -> "Python"
                        in listOf("md", "txt", "rst") -> "文档"
                        in listOf("json", "xml", "yaml", "yml") -> "配置文件"
                        in listOf("png", "jpg", "jpeg", "gif", "svg") -> "图片"
                        else -> "其他文件"
                    }
                }
                is ContextItem.Folder -> "文件夹"
                is ContextItem.CodeBlock -> "代码片段"
            }
            
            groups.getOrPut(group) { mutableListOf() }.add(item)
        }
        
        return groups
    }
    
    private fun getContextItemKey(item: ContextItem): String {
        return when (item) {
            is ContextItem.File -> "file:${item.path}"
            is ContextItem.Folder -> "folder:${item.path}"
            is ContextItem.CodeBlock -> "code:${item.language}:${item.content.hashCode()}"
        }
    }
    
    private fun estimateTokens(sizeInBytes: Long): Long {
        // 粗略估算：平均每个 token 约 4 字节
        return sizeInBytes / 4
    }
}

/**
 * 上下文验证结果
 */
data class ContextValidationResult(
    val validItems: List<ContextItem>,
    val invalidItems: List<InvalidContextItem>
)

/**
 * 无效的上下文项
 */
data class InvalidContextItem(
    val item: ContextItem,
    val reason: String
)

/**
 * 上下文大小信息
 */
data class ContextSizeInfo(
    val totalSizeBytes: Long,
    val fileCount: Int,
    val folderCount: Int,
    val codeBlockCount: Int,
    val estimatedTokens: Long
) {
    val totalSizeMB: Double get() = totalSizeBytes / (1024.0 * 1024.0)
    val totalSizeKB: Double get() = totalSizeBytes / 1024.0
    
    fun formatSize(): String {
        return when {
            totalSizeMB > 1 -> String.format("%.2f MB", totalSizeMB)
            totalSizeKB > 1 -> String.format("%.2f KB", totalSizeKB)
            else -> "$totalSizeBytes B"
        }
    }
}