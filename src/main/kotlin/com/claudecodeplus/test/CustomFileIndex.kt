package com.claudecodeplus.test

import com.claudecodeplus.core.interfaces.FileIndexEntry
import com.claudecodeplus.core.interfaces.FileIndexService
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

/**
 * 自定义文件索引实现，用于测试环境
 * 使用内存索引提供快速搜索
 */
class CustomFileIndex(
    private val projectPath: String,
    private val excludePatterns: List<String> = DEFAULT_EXCLUDE_PATTERNS
) : FileIndexService {
    
    companion object {
        private val DEFAULT_EXCLUDE_PATTERNS = listOf(
            ".git",
            ".idea",
            ".gradle",
            "build",
            "out",
            "target",
            "node_modules",
            ".DS_Store",
            "*.class",
            "*.jar"
        )
    }
    
    // 文件索引存储
    private val fileIndex = ConcurrentHashMap<String, FileIndexEntry>()
    private val nameIndex = ConcurrentHashMap<String, MutableSet<String>>()
    private var indexReady = false
    
    init {
        // 初始化时启动索引构建
        GlobalScope.launch {
            buildIndex()
        }
    }
    
    override suspend fun searchByName(query: String, limit: Int): List<String> {
        if (!indexReady) {
            // 如果索引未就绪，回退到实时搜索
            return searchFilesRealtime(query, limit)
        }
        
        val lowerQuery = query.lowercase()
        val results = mutableListOf<String>()
        
        // 精确匹配
        nameIndex[lowerQuery]?.let { paths ->
            results.addAll(paths.take(limit))
        }
        
        // 如果精确匹配不够，进行模糊匹配
        if (results.size < limit) {
            val fuzzyResults = fileIndex.values
                .filter { entry ->
                    val nameLower = entry.name.lowercase()
                    // 包含匹配
                    nameLower.contains(lowerQuery) ||
                    // 模糊匹配（如 "ctw" 匹配 "ClaudeToolWindow"）
                    fuzzyMatch(nameLower, lowerQuery)
                }
                .sortedBy { entry ->
                    // 优先级排序：精确匹配 > 开头匹配 > 包含匹配 > 模糊匹配
                    when {
                        entry.name.lowercase() == lowerQuery -> 0
                        entry.name.lowercase().startsWith(lowerQuery) -> 1
                        entry.name.lowercase().contains(lowerQuery) -> 2
                        else -> 3
                    }
                }
                .map { it.path }
                .filter { it !in results }
                .take(limit - results.size)
            
            results.addAll(fuzzyResults)
        }
        
        return results.map { getRelativePath(it) }
    }
    
    override suspend fun searchByContent(query: String, filePattern: String?, limit: Int): List<String> {
        // 简单实现：只搜索文本文件的内容
        return fileIndex.values
            .filter { entry ->
                !entry.isDirectory &&
                isTextFile(entry.name) &&
                (filePattern == null || matchesPattern(entry.name, filePattern))
            }
            .filter { entry ->
                try {
                    File(entry.path).readText().contains(query, ignoreCase = true)
                } catch (e: Exception) {
                    false
                }
            }
            .take(limit)
            .map { getRelativePath(it.path) }
    }
    
    override suspend fun getRecentFiles(limit: Int): List<String> {
        return fileIndex.values
            .filter { !it.isDirectory }
            .sortedByDescending { it.lastModified }
            .take(limit)
            .map { getRelativePath(it.path) }
    }
    
    override suspend fun refreshIndex(path: String?) {
        if (path == null) {
            // 重建整个索引
            buildIndex()
        } else {
            // 更新特定路径
            updatePath(path)
        }
    }
    
    override fun isIndexReady(): Boolean = indexReady
    
    /**
     * 构建文件索引
     */
    private suspend fun buildIndex() = withContext(Dispatchers.IO) {
        println("Building file index for: $projectPath")
        val startTime = System.currentTimeMillis()
        
        fileIndex.clear()
        nameIndex.clear()
        
        try {
            Files.walkFileTree(Paths.get(projectPath), object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val dirName = dir.fileName?.toString() ?: ""
                    
                    // 跳过排除的目录
                    if (shouldExclude(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    
                    // 添加目录到索引
                    addToIndex(dir, attrs, true)
                    return FileVisitResult.CONTINUE
                }
                
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val fileName = file.fileName?.toString() ?: ""
                    
                    // 跳过排除的文件
                    if (!shouldExclude(fileName)) {
                        addToIndex(file, attrs, false)
                    }
                    
                    return FileVisitResult.CONTINUE
                }
            })
            
            indexReady = true
            val endTime = System.currentTimeMillis()
            println("Index built in ${endTime - startTime}ms. Total files: ${fileIndex.size}")
            
        } catch (e: Exception) {
            println("Error building index: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 添加文件到索引
     */
    private fun addToIndex(path: Path, attrs: BasicFileAttributes, isDirectory: Boolean) {
        val absolutePath = path.toAbsolutePath().toString()
        val fileName = path.fileName?.toString() ?: ""
        val extension = if (isDirectory) "" else fileName.substringAfterLast('.', "")
        
        val entry = FileIndexEntry(
            path = absolutePath,
            name = fileName,
            extension = extension,
            size = attrs.size(),
            lastModified = attrs.lastModifiedTime().toMillis(),
            isDirectory = isDirectory
        )
        
        fileIndex[absolutePath] = entry
        
        // 更新名称索引
        val nameLower = fileName.lowercase()
        nameIndex.computeIfAbsent(nameLower) { ConcurrentHashMap.newKeySet() }.add(absolutePath)
        
        // 添加文件名的各个部分到索引（支持部分匹配）
        if (!isDirectory) {
            val parts = fileName.split("[._-]".toRegex())
            parts.forEach { part ->
                if (part.length > 2) {
                    val partLower = part.lowercase()
                    nameIndex.computeIfAbsent(partLower) { ConcurrentHashMap.newKeySet() }.add(absolutePath)
                }
            }
        }
    }
    
    /**
     * 更新特定路径的索引
     */
    private suspend fun updatePath(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists()) {
            val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            addToIndex(file.toPath(), attrs, file.isDirectory)
        } else {
            // 从索引中移除
            fileIndex.remove(path)
            nameIndex.values.forEach { it.remove(path) }
        }
    }
    
    /**
     * 实时搜索文件（索引未就绪时使用）
     */
    private suspend fun searchFilesRealtime(query: String, limit: Int): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        val lowerQuery = query.lowercase()
        
        File(projectPath).walkTopDown()
            .filter { file ->
                !shouldExclude(file.name) &&
                file.name.lowercase().contains(lowerQuery)
            }
            .take(limit)
            .forEach { file ->
                results.add(getRelativePath(file.absolutePath))
            }
        
        results
    }
    
    /**
     * 判断是否应该排除
     */
    private fun shouldExclude(name: String): Boolean {
        return excludePatterns.any { pattern ->
            when {
                pattern.startsWith("*") -> name.endsWith(pattern.substring(1))
                pattern.endsWith("*") -> name.startsWith(pattern.substring(0, pattern.length - 1))
                else -> name == pattern
            }
        }
    }
    
    /**
     * 模糊匹配算法
     */
    private fun fuzzyMatch(text: String, pattern: String): Boolean {
        if (pattern.isEmpty()) return true
        if (pattern.length > text.length) return false
        
        var patternIndex = 0
        
        for (char in text) {
            if (patternIndex < pattern.length && char == pattern[patternIndex]) {
                patternIndex++
            }
        }
        
        return patternIndex == pattern.length
    }
    
    /**
     * 判断是否是文本文件
     */
    private fun isTextFile(fileName: String): Boolean {
        val textExtensions = setOf(
            "txt", "md", "kt", "java", "js", "ts", "jsx", "tsx",
            "py", "rb", "go", "rs", "cpp", "c", "h", "hpp",
            "xml", "json", "yaml", "yml", "properties", "gradle",
            "sh", "bat", "ps1", "sql", "html", "css", "scss"
        )
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in textExtensions
    }
    
    /**
     * 匹配文件模式
     */
    private fun matchesPattern(fileName: String, pattern: String): Boolean {
        return when {
            pattern.startsWith("*") -> fileName.endsWith(pattern.substring(1))
            pattern.endsWith("*") -> fileName.startsWith(pattern.substring(0, pattern.length - 1))
            else -> fileName == pattern
        }
    }
    
    /**
     * 获取相对路径
     */
    private fun getRelativePath(absolutePath: String): String {
        return when {
            absolutePath == projectPath -> "."
            absolutePath.startsWith("$projectPath/") -> absolutePath.substring(projectPath.length + 1)
            absolutePath.startsWith(projectPath) && projectPath.endsWith("/") -> absolutePath.substring(projectPath.length)
            else -> absolutePath
        }
    }
}