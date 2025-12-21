package com.asakii.server.tools

import com.asakii.rpc.api.*
import mu.KotlinLogging
import java.io.File
import java.util.Locale

/**
 * IDE 工具默认实现（用于独立运行模式和浏览器环境）
 *
 * - 提供基于文件系统的默认实现（搜索文件、获取内容等）
 * - IDEA 特有功能（openFile、showDiff）返回 UnsupportedOperationException
 * - jetbrains-plugin 模块中的 IdeToolsImpl 继承此类，覆盖 IDEA 特有方法
 */
private val logger = KotlinLogging.logger {}

open class IdeToolsDefault(
    private val _projectPath: String? = null
) : IdeTools {
    private var defaultLocale: String? = null

    override open fun openFile(path: String, line: Int, column: Int): Result<Unit> {
        logger.info { "[Default] Opening file: $path (line=$line, column=$column)" }
        return Result.failure(UnsupportedOperationException("openFile is not supported in browser mode. Please use IDEA plugin."))
    }
    
    override open fun showDiff(request: DiffRequest): Result<Unit> {
        logger.info { "[Default] Showing diff for: ${request.filePath}" }
        return Result.failure(UnsupportedOperationException("showDiff is not supported in browser mode. Please use IDEA plugin."))
    }
    
    override open fun searchFiles(query: String, maxResults: Int): Result<List<FileInfo>> {
        logger.info { "[Default] Searching files for: '$query' (maxResults=$maxResults)" }
        // 在浏览器模式下，可以尝试搜索项目目录
        val basePath = getProjectPath()
        val results = mutableListOf<FileInfo>()
        
        try {
            val baseDir = File(basePath)
            if (baseDir.exists() && baseDir.isDirectory) {
                searchFilesRecursive(baseDir, query, results, maxResults)
            }
        } catch (e: Exception) {
            logger.warn { "Failed to search files: ${e.message}" }
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
    
    override open fun getFileContent(path: String, lineStart: Int?, lineEnd: Int?): Result<String> {
        logger.info { "[Default] Getting file content: $path (lines=${lineStart?.let { "$it-$lineEnd" } ?: "all"})" }
        
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
    
    override open fun getRecentFiles(maxResults: Int): Result<List<FileInfo>> {
        logger.info { "[Default] Getting recent files (maxResults=$maxResults)" }
        return try {
            // 简单实现：返回项目目录下最近修改的文件
            val projectDir = java.io.File(_projectPath ?: System.getProperty("user.dir") ?: ".")
            val files = projectDir.walkTopDown()
                .filter { it.isFile }
                .filter { !it.path.contains(".git") && !it.path.contains("node_modules") && !it.path.contains("build") }
                .filter { it.extension in listOf("kt", "java", "ts", "vue", "js", "json", "md", "xml", "gradle") }
                .sortedByDescending { it.lastModified() }
                .take(maxResults)
                .map { FileInfo(it.absolutePath) }
                .toList()
            Result.success(files)
        } catch (e: Exception) {
            logger.warn { "[Default] Failed to get recent files: ${e.message}" }
            Result.success(emptyList())
        }
    }
    
    override open fun getTheme(): IdeTheme {
        // 返回默认主题
        return IdeTheme(
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
    
    override open fun getProjectPath(): String {
        return _projectPath ?: System.getProperty("user.dir") ?: "."
    }
    
    override open fun getLocale(): String {
        if (defaultLocale != null) {
            return defaultLocale!!
        }
        val locale = Locale.getDefault()
        return "${locale.language}-${locale.country}"
    }

    override open fun setLocale(locale: String): Result<Unit> {
        defaultLocale = locale
        logger.info { "[Default] Locale set to: $locale" }
        return Result.success(Unit)
    }

    override open fun detectNode(): NodeDetectionResult {
        logger.info { "[Default] Detecting Node.js installation..." }

        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("windows")

        try {
            // 1. 检测 Node.js 路径
            val nodePath = detectNodePath(isWindows)
            if (nodePath == null) {
                return NodeDetectionResult(
                    found = false,
                    error = "Node.js not found. Please install Node.js and ensure it's in your system PATH."
                )
            }

            // 2. 检测 Node.js 版本
            val version = detectNodeVersion(nodePath, isWindows)

            return NodeDetectionResult(
                found = true,
                path = nodePath,
                version = version
            )
        } catch (e: Exception) {
            logger.error { "Failed to detect Node.js: ${e.message}" }
            return NodeDetectionResult(
                found = false,
                error = "Detection failed: ${e.message}"
            )
        }
    }

    /**
     * 检测 Node.js 路径
     */
    private fun detectNodePath(isWindows: Boolean): String? {
        try {
            val command = if (isWindows) {
                arrayOf("cmd", "/c", "where", "node")
            } else {
                val defaultShell = System.getenv("SHELL") ?: "/bin/bash"
                arrayOf(defaultShell, "-l", "-c", "which node")
            }

            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream.bufferedReader().readLine()?.trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && !result.isNullOrBlank() && File(result).exists()) {
                return result
            }
        } catch (e: Exception) {
            logger.debug { "Failed to detect Node.js path: ${e.message}" }
        }

        return null
    }

    /**
     * 检测 Node.js 版本
     */
    private fun detectNodeVersion(nodePath: String, isWindows: Boolean): String? {
        try {
            val command = if (isWindows) {
                arrayOf("cmd", "/c", nodePath, "--version")
            } else {
                val defaultShell = System.getenv("SHELL") ?: "/bin/bash"
                arrayOf(defaultShell, "-l", "-c", "$nodePath --version")
            }

            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream.bufferedReader().readLine()?.trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && !result.isNullOrBlank()) {
                return result
            }
        } catch (e: Exception) {
            logger.debug { "Failed to detect Node.js version: ${e.message}" }
        }

        return null
    }

    override open fun getActiveEditorFile(): ActiveFileInfo? {
        logger.info { "[Default] getActiveEditorFile is not supported in browser mode" }
        // 浏览器模式下没有活跃编辑器，返回 null
        return null
    }

    override open fun openUrl(url: String): Result<Unit> {
        logger.info { "[Default] Opening URL in browser: $url" }
        return try {
            if (java.awt.Desktop.isDesktopSupported()) {
                val desktop = java.awt.Desktop.getDesktop()
                if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(java.net.URI(url))
                    Result.success(Unit)
                } else {
                    Result.failure(UnsupportedOperationException("Desktop browse action is not supported"))
                }
            } else {
                Result.failure(UnsupportedOperationException("Desktop is not supported on this platform"))
            }
        } catch (e: Exception) {
            logger.error { "Failed to open URL: ${e.message}" }
            Result.failure(e)
        }
    }
}

