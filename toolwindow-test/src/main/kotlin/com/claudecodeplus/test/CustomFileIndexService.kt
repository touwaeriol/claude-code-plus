package com.claudecodeplus.test

import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.FileInfo
import com.claudecodeplus.ui.services.SymbolInfo
import com.claudecodeplus.ui.models.SymbolType
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

/**
 * 自定义文件索引服务实现
 * 用于测试应用，提供基本的文件搜索功能
 */
class CustomFileIndexService : FileIndexService {
    private val fileIndex = mutableListOf<FileInfo>()
    private val symbolIndex = mutableListOf<SymbolInfo>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun indexFiles(rootPath: String) {
        scope.launch {
            fileIndex.clear()
            symbolIndex.clear()
            
            val root = Paths.get(rootPath)
            if (root.exists() && root.isDirectory()) {
                indexDirectory(root)
            }
        }
    }
    
    private suspend fun indexDirectory(dir: Path) = coroutineScope {
        try {
            Files.walk(dir)
                .filter { it.isRegularFile() }
                .filter { shouldIndexFile(it) }
                .forEach { file ->
                    val relativePath = dir.relativize(file).toString()
                    fileIndex.add(
                        FileInfo(
                            path = file.toString(),
                            name = file.fileName.toString(),
                            extension = file.extension
                        )
                    )
                    
                    // 简单的符号提取（仅适用于 Kotlin/Java）
                    if (file.extension in listOf("kt", "java")) {
                        extractSymbols(file)
                    }
                }
        } catch (e: Exception) {
            // 忽略无法访问的目录
        }
    }
    
    private fun shouldIndexFile(path: Path): Boolean {
        val fileName = path.fileName.toString()
        
        // 排除隐藏文件和系统文件
        if (fileName.startsWith(".")) return false
        
        // 排除二进制文件
        val binaryExtensions = setOf("jar", "class", "exe", "dll", "so", "dylib", "png", "jpg", "jpeg", "gif", "ico", "pdf", "zip", "tar", "gz")
        if (path.extension in binaryExtensions) return false
        
        // 排除构建目录
        val pathStr = path.toString()
        if (pathStr.contains("/build/") || pathStr.contains("/out/") || pathStr.contains("/target/")) {
            return false
        }
        
        return true
    }
    
    private fun extractSymbols(file: Path) {
        try {
            val content = file.readText()
            val lines = content.lines()
            
            lines.forEachIndexed { lineIndex, line ->
                val trimmed = line.trim()
                
                // 提取类
                if (trimmed.startsWith("class ") || trimmed.startsWith("data class ") || 
                    trimmed.startsWith("interface ") || trimmed.startsWith("object ")) {
                    val symbolName = extractSymbolName(trimmed)
                    if (symbolName != null) {
                        symbolIndex.add(
                            SymbolInfo(
                                name = symbolName,
                                type = when {
                                    trimmed.startsWith("interface") -> SymbolType.INTERFACE
                                    else -> SymbolType.CLASS
                                },
                                file = file.toString(),
                                line = lineIndex + 1
                            )
                        )
                    }
                }
                
                // 提取函数
                if (trimmed.startsWith("fun ") || trimmed.contains(" fun ")) {
                    val symbolName = extractFunctionName(trimmed)
                    if (symbolName != null) {
                        symbolIndex.add(
                            SymbolInfo(
                                name = symbolName,
                                type = SymbolType.FUNCTION,
                                file = file.toString(),
                                line = lineIndex + 1
                            )
                        )
                    }
                }
                
                // 提取属性
                if (trimmed.startsWith("val ") || trimmed.startsWith("var ") ||
                    trimmed.contains(" val ") || trimmed.contains(" var ")) {
                    val symbolName = extractPropertyName(trimmed)
                    if (symbolName != null) {
                        symbolIndex.add(
                            SymbolInfo(
                                name = symbolName,
                                type = SymbolType.PROPERTY,
                                file = file.toString(),
                                line = lineIndex + 1
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略解析错误
        }
    }
    
    private fun extractSymbolName(line: String): String? {
        val patterns = listOf(
            Regex("(?:class|data class|interface|object)\\s+(\\w+)"),
            Regex("(?:public|private|protected)?\\s*(?:class|interface)\\s+(\\w+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(line)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }
    
    private fun extractFunctionName(line: String): String? {
        val pattern = Regex("fun\\s+(?:<[^>]+>\\s+)?(?:\\w+\\.)?([\\w]+)\\s*\\(")
        val match = pattern.find(line)
        return match?.groupValues?.get(1)
    }
    
    private fun extractPropertyName(line: String): String? {
        val pattern = Regex("(?:val|var)\\s+(\\w+)\\s*[:=]")
        val match = pattern.find(line)
        return match?.groupValues?.get(1)
    }
    
    override fun searchFiles(query: String): List<FileInfo> {
        val lowerQuery = query.lowercase()
        return fileIndex.filter { file ->
            file.name.lowercase().contains(lowerQuery) ||
            file.path.lowercase().contains(lowerQuery)
        }.take(50) // 限制结果数量
    }
    
    override fun getFileContent(path: String): String {
        return try {
            File(path).readText()
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }
    
    override fun findSymbols(query: String): List<SymbolInfo> {
        val lowerQuery = query.lowercase()
        return symbolIndex.filter { symbol ->
            symbol.name.lowercase().contains(lowerQuery)
        }.take(50) // 限制结果数量
    }
}