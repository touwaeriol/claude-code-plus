package com.asakii.server.tools

import com.asakii.bridge.IdeTheme
import java.io.File
import java.util.Locale
import java.util.logging.Logger

/**
 * IDE工具Mock实现（用于浏览器模式和测试）
 * 
 * 对于需要IDEA Platform API的功能（如openFile、showDiff），返回错误
 * 对于通用功能（如getProjectPath），提供降级实现
 */
class IdeToolsMock(
    private val projectPath: String? = null
) : IdeTools {
    
    private val logger = Logger.getLogger(IdeToolsMock::class.java.name)
    private var mockLocale: String? = null
    
    override fun openFile(path: String, line: Int, column: Int): Result<Unit> {
        logger.info("[Mock] Opening file: $path (line=$line, column=$column)")
        return Result.failure(UnsupportedOperationException("openFile is not supported in browser mode. Please use IDEA plugin."))
    }
    
    override fun showDiff(request: DiffRequest): Result<Unit> {
        logger.info("[Mock] Showing diff for: ${request.filePath}")
        return Result.failure(UnsupportedOperationException("showDiff is not supported in browser mode. Please use IDEA plugin."))
    }
    
    override fun searchFiles(query: String, maxResults: Int): Result<List<FileInfo>> {
        logger.info("[Mock] Searching files for: '$query' (maxResults=$maxResults)")
        // 在浏览器模式下，可以尝试搜索项目目录
        val basePath = getProjectPath()
        val results = mutableListOf<FileInfo>()
        
        try {
            val baseDir = File(basePath)
            if (baseDir.exists() && baseDir.isDirectory) {
                searchFilesRecursive(baseDir, query, results, maxResults)
            }
        } catch (e: Exception) {
            logger.warning("Failed to search files: ${e.message}")
        }
        
        return Result.success(results.take(maxResults))
    }
    
    private fun searchFilesRecursive(
        dir: File,
        query: String,
        results: MutableList<FileInfo>,
        maxResults: Int
    ) {
        if (results.size >= maxResults) return
        
        try {
            dir.listFiles()?.forEach { file ->
                if (results.size >= maxResults) return
                
                if (file.isDirectory) {
                    // 跳过常见的不需要搜索的目录
                    if (!file.name.startsWith(".") && 
                        file.name != "node_modules" && 
                        file.name != "build" &&
                        file.name != "target" &&
                        file.name != ".idea") {
                        searchFilesRecursive(file, query, results, maxResults)
                    }
                } else {
                    // 检查文件名是否匹配查询
                    if (file.name.contains(query, ignoreCase = true)) {
                        results.add(FileInfo(file.absolutePath))
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略权限错误等
        }
    }
    
    override fun getFileContent(path: String, lineStart: Int?, lineEnd: Int?): Result<String> {
        logger.info("[Mock] Getting file content: $path (lines=${lineStart?.let { "$it-$lineEnd" } ?: "all"})")
        
        return try {
            val file = File(path)
            if (!file.exists()) {
                return Result.failure(IllegalArgumentException("File not found: $path"))
            }
            
            val lines = file.readLines()
            val content = if (lineStart != null && lineEnd != null) {
                lines.subList(
                    (lineStart - 1).coerceAtLeast(0),
                    lineEnd.coerceAtMost(lines.size)
                ).joinToString("\n")
            } else {
                lines.joinToString("\n")
            }
            
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getRecentFiles(maxResults: Int): Result<List<FileInfo>> {
        logger.info("[Mock] Getting recent files (maxResults=$maxResults)")
        // 浏览器模式下无法获取最近打开的文件
        return Result.success(emptyList())
    }
    
    override fun getTheme(): IdeTheme {
        // 返回默认暗色主题
        return IdeTheme(
            isDark = true,
            background = "#2b2b2b",
            foreground = "#a9b7c6",
            borderColor = "#3c3f41",
            panelBackground = "#3c3f41",
            textFieldBackground = "#45494a",
            selectionBackground = "#214283",
            selectionForeground = "#ffffff",
            linkColor = "#4e9a06",
            errorColor = "#cc0000",
            warningColor = "#f57900",
            successColor = "#4e9a06",
            separatorColor = "#515658",
            hoverBackground = "#4e5254",
            accentColor = "#4e9a06",
            infoBackground = "#3c3f41",
            codeBackground = "#2b2b2b",
            secondaryForeground = "#808080"
        )
    }
    
    override fun getProjectPath(): String {
        return projectPath ?: System.getProperty("user.dir") ?: "."
    }
    
    override fun getLocale(): String {
        if (mockLocale != null) {
            return mockLocale!!
        }
        val locale = Locale.getDefault()
        return "${locale.language}-${locale.country}"
    }
    
    override fun setLocale(locale: String): Result<Unit> {
        mockLocale = locale
        logger.info("[Mock] Locale set to: $locale")
        return Result.success(Unit)
    }
}

