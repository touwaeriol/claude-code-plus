package com.claudecodeplus.desktop

import com.claudecodeplus.ui.services.*
import com.claudecodeplus.ui.models.SymbolType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

/**
 * 简单的文件索引服务实现
 * 用于测试环境，基于文件系统直接扫描
 */
class SimpleFileIndexService : FileIndexService {
    
    private var rootPath: String = ""
    private var indexedFiles = mutableListOf<IndexedFileInfo>()
    private var indexedSymbols = mutableListOf<IndexedSymbolInfo>()
    private var isReady = false
    private var lastIndexTime = 0L
    
    // 支持的文件类型
    private val supportedExtensions = setOf(
        "kt", "java", "js", "ts", "py", "md", "json", "xml", "yml", "yaml",
        "html", "htm", "css", "txt", "gradle", "properties", "kts"
    )
    
    // 要排除的目录
    private val excludedDirs = setOf(
        ".git", ".gradle", ".idea", ".vscode", "node_modules", "build", 
        "target", "dist", "out", "bin", ".DS_Store", "tmp", "temp"
    )
    
    override suspend fun initialize(rootPath: String) {
        this.rootPath = rootPath
        indexPath(rootPath, recursive = true)
    }
    
    override suspend fun indexPath(path: String, recursive: Boolean) = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        indexedFiles.clear()
        indexedSymbols.clear()
        
        try {
            val rootDir = File(if (path.isBlank()) rootPath else path)
            if (rootDir.exists() && rootDir.isDirectory) {
                scanDirectory(rootDir, recursive)
            }
            
            isReady = true
            lastIndexTime = System.currentTimeMillis()
            println("SimpleFileIndexService: 索引完成，耗时 ${lastIndexTime - startTime}ms")
            println("SimpleFileIndexService: 找到 ${indexedFiles.size} 个文件")
        } catch (e: Exception) {
            println("SimpleFileIndexService: 索引失败 - ${e.message}")
            isReady = false
        }
    }
    
    private fun scanDirectory(dir: File, recursive: Boolean) {
        if (shouldExcludeDirectory(dir.name)) return
        
        dir.listFiles()?.forEach { file ->
            try {
                when {
                    file.isDirectory && recursive -> {
                        scanDirectory(file, recursive)
                    }
                    file.isFile && shouldIncludeFile(file) -> {
                        val fileInfo = createFileInfo(file)
                        indexedFiles.add(fileInfo)
                        
                        // 简单的符号提取（仅针对代码文件）
                        if (isCodeFile(file)) {
                            indexedSymbols.addAll(extractSymbols(file, fileInfo.relativePath))
                        }
                    }
                }
            } catch (e: Exception) {
                // 忽略无法访问的文件
                println("SimpleFileIndexService: 跳过文件 ${file.absolutePath} - ${e.message}")
            }
        }
    }
    
    private fun createFileInfo(file: File): IndexedFileInfo {
        val relativePath = file.absolutePath.removePrefix("$rootPath/").removePrefix(rootPath)
        return IndexedFileInfo(
            name = file.name,
            relativePath = relativePath,
            absolutePath = file.absolutePath,
            fileType = file.extension,
            size = file.length(),
            lastModified = file.lastModified(),
            isDirectory = file.isDirectory,
            language = detectLanguage(file.extension),
            encoding = "UTF-8" // 简化处理
        )
    }
    
    private fun shouldExcludeDirectory(dirName: String): Boolean {
        return excludedDirs.contains(dirName) || dirName.startsWith(".")
    }
    
    private fun shouldIncludeFile(file: File): Boolean {
        if (file.name.startsWith(".")) return false
        return supportedExtensions.contains(file.extension.lowercase()) || 
               file.extension.isEmpty() // 包含无扩展名文件
    }
    
    private fun isCodeFile(file: File): Boolean {
        return file.extension.lowercase() in setOf("kt", "java", "js", "ts", "py")
    }
    
    private fun detectLanguage(extension: String): String? {
        return when (extension.lowercase()) {
            "kt", "kts" -> "Kotlin"
            "java" -> "Java"
            "js" -> "JavaScript"
            "ts" -> "TypeScript"
            "py" -> "Python"
            "md" -> "Markdown"
            "json" -> "JSON"
            "xml" -> "XML"
            "yml", "yaml" -> "YAML"
            "html", "htm" -> "HTML"
            "css" -> "CSS"
            else -> null
        }
    }
    
    private fun extractSymbols(file: File, relativePath: String): List<IndexedSymbolInfo> {
        val symbols = mutableListOf<IndexedSymbolInfo>()
        
        try {
            val content = file.readText()
            val lines = content.lines()
            
            lines.forEachIndexed { lineIndex, line ->
                val trimmedLine = line.trim()
                
                // 简单的符号识别（基于关键字）
                when {
                    // Kotlin/Java 类
                    trimmedLine.matches(Regex(".*\\b(class|interface|object|enum)\\s+([A-Za-z_][A-Za-z0-9_]*).*")) -> {
                        val match = Regex("\\b(class|interface|object|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)").find(trimmedLine)
                        match?.let {
                            val symbolType = when (it.groupValues[1]) {
                                "interface" -> SymbolType.INTERFACE
                                "enum" -> SymbolType.ENUM
                                "object" -> SymbolType.OBJECT
                                else -> SymbolType.CLASS
                            }
                            symbols.add(IndexedSymbolInfo(
                                name = it.groupValues[2],
                                type = symbolType,
                                filePath = relativePath,
                                line = lineIndex + 1,
                                signature = trimmedLine
                            ))
                        }
                    }
                    
                    // 函数/方法
                    trimmedLine.matches(Regex(".*\\b(fun|function|def)\\s+([A-Za-z_][A-Za-z0-9_]*).*")) -> {
                        val match = Regex("\\b(fun|function|def)\\s+([A-Za-z_][A-Za-z0-9_]*)").find(trimmedLine)
                        match?.let {
                            symbols.add(IndexedSymbolInfo(
                                name = it.groupValues[2],
                                type = SymbolType.FUNCTION,
                                filePath = relativePath,
                                line = lineIndex + 1,
                                signature = trimmedLine
                            ))
                        }
                    }
                    
                    // 属性/变量
                    trimmedLine.matches(Regex(".*\\b(val|var|let|const)\\s+([A-Za-z_][A-Za-z0-9_]*).*")) -> {
                        val match = Regex("\\b(val|var|let|const)\\s+([A-Za-z_][A-Za-z0-9_]*)").find(trimmedLine)
                        match?.let {
                            val symbolType = if (it.groupValues[1] in listOf("val", "const")) {
                                SymbolType.CONSTANT
                            } else {
                                SymbolType.VARIABLE
                            }
                            symbols.add(IndexedSymbolInfo(
                                name = it.groupValues[2],
                                type = symbolType,
                                filePath = relativePath,
                                line = lineIndex + 1,
                                signature = trimmedLine
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("SimpleFileIndexService: 无法解析符号 ${file.absolutePath} - ${e.message}")
        }
        
        return symbols
    }
    
    override suspend fun searchFiles(
        query: String, 
        maxResults: Int,
        fileTypes: List<String>
    ): List<IndexedFileInfo> = withContext(Dispatchers.IO) {
        if (!isReady || query.isBlank()) return@withContext emptyList()
        
        val queryLower = query.lowercase()
        val results = indexedFiles.filter { file ->
            // 文件类型过滤
            val typeMatches = fileTypes.isEmpty() || fileTypes.contains(file.fileType.lowercase())
            
            // 名称匹配
            val nameMatches = file.name.lowercase().contains(queryLower) ||
                             file.relativePath.lowercase().contains(queryLower)
            
            typeMatches && nameMatches
        }.sortedWith(compareBy<IndexedFileInfo> { file ->
            // 排序优先级：精确匹配 > 前缀匹配 > 包含匹配
            when {
                file.name.equals(query, ignoreCase = true) -> 0
                file.name.startsWith(query, ignoreCase = true) -> 1
                file.name.contains(query, ignoreCase = true) -> 2
                else -> 3
            }
        }.thenBy { it.name })
        
        results.take(maxResults)
    }
    
    override suspend fun findFilesByName(fileName: String, maxResults: Int): List<IndexedFileInfo> {
        return searchFiles(fileName, maxResults).filter { 
            it.name.equals(fileName, ignoreCase = true)
        }
    }
    
    override suspend fun searchSymbols(
        query: String,
        symbolTypes: List<SymbolType>,
        maxResults: Int
    ): List<IndexedSymbolInfo> = withContext(Dispatchers.IO) {
        if (!isReady || query.isBlank()) return@withContext emptyList()
        
        val queryLower = query.lowercase()
        val results = indexedSymbols.filter { symbol ->
            val typeMatches = symbolTypes.isEmpty() || symbolTypes.contains(symbol.type)
            val nameMatches = symbol.name.lowercase().contains(queryLower)
            typeMatches && nameMatches
        }.sortedWith(compareBy<IndexedSymbolInfo> { symbol ->
            when {
                symbol.name.equals(query, ignoreCase = true) -> 0
                symbol.name.startsWith(query, ignoreCase = true) -> 1
                else -> 2
            }
        }.thenBy { it.name })
        
        results.take(maxResults)
    }
    
    override suspend fun getRecentFiles(maxResults: Int): List<IndexedFileInfo> {
        return indexedFiles.sortedWith(compareByDescending { it.lastModified }).take(maxResults)
    }
    
    override suspend fun getFileContent(filePath: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val file = File(if (filePath.startsWith("/")) filePath else "$rootPath/$filePath")
            if (file.exists() && file.isFile) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            println("SimpleFileIndexService: 无法读取文件 $filePath - ${e.message}")
            null
        }
    }
    
    override suspend fun getFileSymbols(filePath: String): List<IndexedSymbolInfo> {
        return indexedSymbols.filter { it.filePath == filePath }
    }
    
    override fun isIndexReady(): Boolean = isReady
    
    override suspend fun getIndexStats(): IndexStats {
        return IndexStats(
            totalFiles = indexedFiles.size,
            indexedFiles = indexedFiles.size,
            totalSymbols = indexedSymbols.size,
            lastIndexTime = lastIndexTime,
            indexSizeBytes = 0, // 简化处理
            supportedFileTypes = supportedExtensions.toList()
        )
    }
    
    override suspend fun refreshIndex() {
        indexPath(rootPath, recursive = true)
    }
    
    override suspend fun cleanup() {
        indexedFiles.clear()
        indexedSymbols.clear()
        isReady = false
    }
}