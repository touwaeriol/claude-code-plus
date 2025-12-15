package com.asakii.claude.agent.sdk.utils

import kotlinx.serialization.json.*
import java.io.File
import mu.KotlinLogging
import kotlinx.coroutines.*

/**
 * 历史会话元数据
 */
data class SessionMetadata(
    val sessionId: String,           // 会话 ID（用于 --resume）
    val firstUserMessage: String,    // 首条用户消息预览
    val timestamp: Long,             // 最后更新时间（毫秒时间戳）
    val messageCount: Int,           // 消息数量
    val projectPath: String,         // 项目路径
    val customTitle: String? = null  // 自定义标题（从 /rename 命令设置）
)

/**
 * Claude 会话文件扫描器
 *
 * 负责扫描 ~/.claude/projects/ 目录下的 JSONL 会话文件
 * 并提取会话元数据
 */
object ClaudeSessionScanner {
    private val logger = KotlinLogging.logger {}
    private val jsonParser = Json { ignoreUnknownKeys = true }

    /**
     * 获取 Claude 配置目录
     */
    fun getClaudeDir(): File {
        val homeDir = System.getProperty("user.home")
            ?: System.getenv("USERPROFILE")
            ?: "."
        return File(homeDir, ".claude")
    }

    /**
     * 扫描项目的历史会话
     *
     * @param projectPath 项目路径
     * @param maxResults 最大结果数
     * @return 会话元数据列表，按时间戳降序排列
     *
     * 优化策略：分批扫描，每次扫描10个文件，找到足够的有效会话后提前返回
     */
    fun scanHistorySessions(projectPath: String, maxResults: Int, offset: Int = 0): List<SessionMetadata> {
        val claudeDir = getClaudeDir()
        val projectId = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val projectDir = File(claudeDir, "projects/$projectId")

        if (!projectDir.exists() || !projectDir.isDirectory) {
            logger.info("[SessionScanner] 项目目录不存在: ${projectDir.absolutePath}")
            return emptyList()
        }

        val jsonlFiles = projectDir.listFiles { file ->
            file.extension == "jsonl"
        }?.sortedByDescending { it.lastModified() } ?: emptyList()  // 按修改时间降序，优先扫描最新的

        logger.info("[SessionScanner] 找到 ${jsonlFiles.size} 个会话文件，开始分批扫描...")

        val batchSize = 10  // 每批扫描10个文件
        val targetCount = maxResults + offset  // 需要的有效会话数（包含offset跳过的部分）
        val collectedMetadata = mutableListOf<SessionMetadata>()

        // 分批扫描，找到足够的有效会话后提前返回
        runBlocking {
            for (batch in jsonlFiles.chunked(batchSize)) {
                val batchResults = batch.map { file ->
                    async(Dispatchers.IO) {
                        extractSessionMetadata(file, projectPath)
                    }
                }.awaitAll().filterNotNull()

                collectedMetadata.addAll(batchResults)

                logger.info("[SessionScanner] 已扫描 ${minOf(collectedMetadata.size + (jsonlFiles.indexOf(batch.last()) + 1), jsonlFiles.size)} 个文件，有效会话数: ${collectedMetadata.size}")

                // 已经找到足够的有效会话，提前返回
                if (collectedMetadata.size >= targetCount) {
                    logger.info("[SessionScanner] 已找到 ${collectedMetadata.size} 个有效会话，提前结束扫描")
                    break
                }
            }
        }

        logger.info("[SessionScanner] 扫描完成，有效会话数: ${collectedMetadata.size}")

        val sorted = collectedMetadata.sortedByDescending { it.timestamp }
        return sorted
            .drop(offset.coerceAtLeast(0))
            .take(maxResults)
    }

    /**
     * 从 JSONL 文件提取会话元数据（优化版本）
     *
     * 优化策略：
     * - messageCount: 直接统计文件总行数（不解析内容）
     * - firstUserMessage: 只读前 20 行
     * - timestamp: 只读最后一行
     */
    private fun extractSessionMetadata(file: File, projectPath: String): SessionMetadata? {
        var firstUserMessage: String? = null
        var lastTimestamp = 0L
        var isSubagent = false

        // 1. 快速统计总行数（messageCount = 文件行数）
        val messageCount = file.useLines { it.count { line -> line.isNotBlank() } }

        if (messageCount == 0) {
            return null
        }

        // 2. 分批读取行，提取首条用户消息（先读5行，没找到再继续读）
        file.bufferedReader().use { reader ->
            var linesRead = 0
            val batchSize = 5
            val maxLines = 100  // 最多读取100行

            while (linesRead < maxLines && firstUserMessage == null && !isSubagent) {
                // 读取一批行
                val batch = (0 until batchSize).mapNotNull {
                    reader.readLine()?.takeIf { it.isNotBlank() }
                }

                if (batch.isEmpty()) {
                    break  // 文件结束
                }

                // 处理这批行
                for (line in batch) {
                    linesRead++

                    try {
                        val json = jsonParser.parseToJsonElement(line).jsonObject

                        // 检查是否为子代理会话（需要过滤）
                        if (json["isSidechain"]?.jsonPrimitive?.booleanOrNull == true ||
                            json["userType"]?.jsonPrimitive?.contentOrNull == "internal") {
                            isSubagent = true
                            break
                        }

                        // 提取第一条用户消息
                        val type = json["type"]?.jsonPrimitive?.contentOrNull
                        if (type == "user") {
                            val content = extractMessageContent(json)
                            if (content != null && !content.contains("Caveat:") && !content.contains("<command-")) {
                                firstUserMessage = content.take(100)
                                break  // 找到后立即停止
                            }
                        }
                    } catch (e: Exception) {
                        // 跳过无效行
                    }
                }
            }
        }

        // 过滤子代理会话和无效会话
        if (isSubagent || firstUserMessage == null) {
            return null
        }

        // 3. 从文件末尾读取最后一行，提取时间戳（使用 RandomAccessFile 优化）
        try {
            val lastLine = readLastLine(file)
            if (lastLine != null && lastLine.isNotBlank()) {
                try {
                    val json = jsonParser.parseToJsonElement(lastLine).jsonObject
                    json["timestamp"]?.jsonPrimitive?.contentOrNull?.let { ts ->
                        lastTimestamp = parseTimestamp(ts)
                    }
                } catch (e: Exception) {
                    // 忽略解析错误
                }
            }
        } catch (e: Exception) {
            // 如果读取失败，使用文件修改时间
        }

        // 如果没有时间戳，使用文件修改时间
        val fileMtime = file.lastModified()
        // 以文件修改时间为准，保证与文件浏览器排序一致
        lastTimestamp = maxOf(lastTimestamp, fileMtime)

        // 加载自定义标题（从 JSONL 文件尾部高效查找）
        val sessionId = file.nameWithoutExtension
        val customTitle = findCustomTitleFromFile(file)

        return SessionMetadata(
            sessionId = sessionId,
            firstUserMessage = firstUserMessage,
            timestamp = lastTimestamp,
            messageCount = messageCount,
            projectPath = projectPath,
            customTitle = customTitle
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

    /**
     * 从文件末尾高效读取最后一行（不读取整个文件）
     * 使用 RandomAccessFile 从文件末尾向前查找最后一个换行符
     */
    private fun readLastLine(file: File): String? {
        if (!file.exists() || file.length() == 0L) {
            return null
        }

        java.io.RandomAccessFile(file, "r").use { raf ->
            var pos = file.length() - 1
            val sb = StringBuilder()

            // 从文件末尾向前读取，直到找到换行符
            while (pos >= 0) {
                raf.seek(pos)
                val c = raf.read().toChar()

                if (c == '\n' || c == '\r') {
                    if (sb.isNotEmpty()) {
                        // 找到完整的一行
                        return sb.reverse().toString()
                    }
                    // 跳过末尾的空行
                } else {
                    sb.append(c)
                }

                pos--

                // 限制最大回溯长度（防止超长行）
                if (sb.length > 10000) {
                    return sb.reverse().toString()
                }
            }

            // 文件只有一行
            return if (sb.isNotEmpty()) sb.reverse().toString() else null
        }
    }

    /**
     * 删除历史会话
     *
     * @param projectPath 项目路径
     * @param sessionId 会话 ID（文件名，不含扩展名）
     * @return true 删除成功，false 删除失败
     */
    fun deleteSession(projectPath: String, sessionId: String): Boolean {
        val claudeDir = getClaudeDir()
        val projectId = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val sessionFile = File(claudeDir, "projects/$projectId/$sessionId.jsonl")

        if (!sessionFile.exists()) {
            logger.warn("[SessionScanner] 会话文件不存在: ${sessionFile.absolutePath}")
            return false
        }

        return try {
            val deleted = sessionFile.delete()
            if (deleted) {
                logger.info("[SessionScanner] 已删除会话文件: ${sessionFile.absolutePath}")
            } else {
                logger.warn("[SessionScanner] 删除会话文件失败: ${sessionFile.absolutePath}")
            }
            deleted
        } catch (e: Exception) {
            logger.error("[SessionScanner] 删除会话文件异常: ${e.message}", e)
            false
        }
    }

    /**
     * 从 JSONL 文件尾部查找 customTitle
     * custom-title 记录格式: {"type":"custom-title","customTitle":"xxx","sessionId":"xxx"}
     */
    private fun findCustomTitleFromFile(file: File): String? {
        if (!file.exists() || file.length() == 0L) {
            return null
        }

        try {
            // 从文件尾部读取最后 50 行（custom-title 通常在文件末尾）
            val lines = mutableListOf<String>()
            java.io.RandomAccessFile(file, "r").use { raf ->
                var pos = file.length() - 1
                val sb = StringBuilder()
                var lineCount = 0
                val maxLines = 50

                while (pos >= 0 && lineCount < maxLines) {
                    raf.seek(pos)
                    val c = raf.read().toChar()

                    if (c == '\n' || c == '\r') {
                        if (sb.isNotEmpty()) {
                            lines.add(sb.reverse().toString())
                            sb.clear()
                            lineCount++
                        }
                    } else {
                        sb.append(c)
                    }
                    pos--
                }

                // 最后一行（文件开头）
                if (sb.isNotEmpty() && lineCount < maxLines) {
                    lines.add(sb.reverse().toString())
                }
            }

            // 从后往前查找 custom-title 记录
            for (line in lines) {
                if (line.contains("\"type\":\"custom-title\"") || line.contains("\"type\": \"custom-title\"")) {
                    try {
                        val json = jsonParser.parseToJsonElement(line).jsonObject
                        val type = json["type"]?.jsonPrimitive?.contentOrNull
                        if (type == "custom-title") {
                            val title = json["customTitle"]?.jsonPrimitive?.contentOrNull
                            if (!title.isNullOrBlank()) {
                                logger.info("[SessionScanner] 找到 custom-title: $title")
                                return title
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略解析错误
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("[SessionScanner] 查找 customTitle 失败: ${e.message}")
        }

        return null
    }
}
