package com.asakii.server.tools

import com.asakii.rpc.api.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.Locale
import java.util.logging.Logger

/**
 * IDE 工具默认实现（用于独立运行模式和浏览器环境）
 *
 * - 提供基于文件系统的默认实现（搜索文件、获取内容等）
 * - IDEA 特有功能（openFile、showDiff）返回 UnsupportedOperationException
 * - jetbrains-plugin 模块中的 IdeToolsImpl 继承此类，覆盖 IDEA 特有方法
 */
open class IdeToolsDefault(
    private val _projectPath: String? = null
) : IdeTools {

    private val logger = Logger.getLogger(IdeToolsDefault::class.java.name)
    private var defaultLocale: String? = null

    override open fun openFile(path: String, line: Int, column: Int): Result<Unit> {
        logger.info("[Default] Opening file: $path (line=$line, column=$column)")
        return Result.failure(UnsupportedOperationException("openFile is not supported in browser mode. Please use IDEA plugin."))
    }
    
    override open fun showDiff(request: DiffRequest): Result<Unit> {
        logger.info("[Default] Showing diff for: ${request.filePath}")
        return Result.failure(UnsupportedOperationException("showDiff is not supported in browser mode. Please use IDEA plugin."))
    }
    
    override open fun searchFiles(query: String, maxResults: Int): Result<List<FileInfo>> {
        logger.info("[Default] Searching files for: '$query' (maxResults=$maxResults)")
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
    
    override open fun getFileContent(path: String, lineStart: Int?, lineEnd: Int?): Result<String> {
        logger.info("[Default] Getting file content: $path (lines=${lineStart?.let { "$it-$lineEnd" } ?: "all"})")
        
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
        logger.info("[Default] Getting recent files (maxResults=$maxResults)")
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
            logger.warning("[Default] Failed to get recent files: ${e.message}")
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
        logger.info("[Default] Locale set to: $locale")
        return Result.success(Unit)
    }

    override open fun getHistorySessions(maxResults: Int): Result<List<HistorySession>> {
        logger.info("[Default] Getting history sessions (maxResults=$maxResults)")
        return try {
            val projectPath = getProjectPath()
            val sessions = scanHistorySessions(projectPath, maxResults)
            Result.success(sessions)
        } catch (e: Exception) {
            logger.warning("[Default] Failed to get history sessions: ${e.message}")
            Result.success(emptyList())
        }
    }

    /**
     * 扫描历史会话文件
     */
    protected fun scanHistorySessions(projectPath: String, maxResults: Int): List<HistorySession> {
        val claudeDir = getClaudeDir()
        val projectId = encodeProjectPath(projectPath)
        val projectDir = File(claudeDir, "projects/$projectId")

        if (!projectDir.exists() || !projectDir.isDirectory) {
            logger.info("[Default] Project history directory not found: ${projectDir.absolutePath}")
            return emptyList()
        }

        val jsonlFiles = projectDir.listFiles { file -> file.extension == "jsonl" } ?: emptyArray()
        logger.info("[Default] Found ${jsonlFiles.size} session files in ${projectDir.absolutePath}")

        val sessions = jsonlFiles.mapNotNull { file ->
            try {
                extractSessionMetadata(file, projectPath)
            } catch (e: Exception) {
                logger.warning("[Default] Failed to parse session file ${file.name}: ${e.message}")
                null
            }
        }

        // 按时间戳降序排序，取前 maxResults 条
        return sessions
            .sortedByDescending { it.timestamp }
            .take(maxResults)
    }

    /**
     * 获取 Claude 配置目录
     */
    private fun getClaudeDir(): File {
        val homeDir = System.getProperty("user.home") ?: System.getenv("USERPROFILE") ?: "."
        return File(homeDir, ".claude")
    }

    /**
     * 编码项目路径为项目 ID
     * 例如：C:\Users\16790\IdeaProjects\claude-code-plus -> C--Users-16790-IdeaProjects-claude-code-plus
     */
    protected fun encodeProjectPath(path: String): String {
        return path
            .replace("\\", "-")
            .replace("/", "-")
            .replace(":", "-")
            .replace("--", "-")
            .trimStart('-')
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }

    /**
     * 从 JSONL 文件提取会话元数据
     */
    private fun extractSessionMetadata(file: File, projectPath: String): HistorySession? {
        var firstUserMessage: String? = null
        var lastTimestamp = 0L
        var messageCount = 0
        var isSubagent = false

        file.forEachLine { line ->
            if (line.isBlank()) return@forEachLine

            try {
                val json = jsonParser.parseToJsonElement(line).jsonObject

                // 检查是否为子代理会话
                if (json["isSidechain"]?.jsonPrimitive?.booleanOrNull == true) {
                    isSubagent = true
                    return@forEachLine
                }
                if (json["userType"]?.jsonPrimitive?.contentOrNull == "internal") {
                    isSubagent = true
                    return@forEachLine
                }

                val type = json["type"]?.jsonPrimitive?.contentOrNull ?: return@forEachLine

                // 统计消息数量
                if (type == "user" || type == "assistant") {
                    messageCount++
                }

                // 提取第一条用户消息
                if (firstUserMessage == null && type == "user") {
                    val content = extractMessageContent(json)
                    if (content != null && !content.contains("Caveat:") && !content.contains("<command-")) {
                        firstUserMessage = content.take(100)
                    }
                }

                // 更新时间戳
                val timestamp = json["timestamp"]?.jsonPrimitive?.contentOrNull
                if (timestamp != null) {
                    val ts = parseTimestamp(timestamp)
                    if (ts > lastTimestamp) {
                        lastTimestamp = ts
                    }
                }
            } catch (e: Exception) {
                // 跳过无效行
            }
        }

        // 过滤子代理会话和无效会话
        if (isSubagent || firstUserMessage == null || messageCount == 0) {
            return null
        }

        // 如果没有解析到时间戳，使用文件修改时间
        if (lastTimestamp == 0L) {
            lastTimestamp = file.lastModified()
        }

        return HistorySession(
            sessionId = file.nameWithoutExtension,
            firstUserMessage = firstUserMessage,
            timestamp = lastTimestamp,
            messageCount = messageCount,
            projectPath = projectPath
        )
    }

    /**
     * 从 JSON 对象中提取消息内容
     * 支持两种格式：
     * 1. message.content = "string"
     * 2. message.content = [{"type": "text", "text": "..."}]
     */
    private fun extractMessageContent(json: JsonObject): String? {
        val message = json["message"]?.jsonObject ?: return null
        val content = message["content"] ?: return null

        return when {
            // 格式 1：content 是字符串
            content is JsonPrimitive && content.isString -> content.content

            // 格式 2：content 是数组
            content is JsonArray -> {
                content.firstOrNull()?.let { item ->
                    if (item is JsonObject) {
                        item["text"]?.jsonPrimitive?.contentOrNull
                    } else null
                }
            }

            else -> null
        }
    }

    /**
     * 解析 ISO 8601 时间戳
     */
    private fun parseTimestamp(timestamp: String): Long {
        return try {
            java.time.Instant.parse(timestamp).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }
}

