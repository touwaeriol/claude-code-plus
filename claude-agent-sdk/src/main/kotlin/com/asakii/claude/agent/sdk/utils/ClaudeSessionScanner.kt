package com.asakii.claude.agent.sdk.utils

import kotlinx.serialization.json.*
import java.io.File
import mu.KotlinLogging

/**
 * 历史会话元数据
 */
data class SessionMetadata(
    val sessionId: String,           // 会话 ID（用于 --resume）
    val firstUserMessage: String,    // 首条用户消息预览
    val timestamp: Long,             // 最后更新时间（毫秒时间戳）
    val messageCount: Int,           // 消息数量
    val projectPath: String          // 项目路径
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
     */
    fun scanHistorySessions(projectPath: String, maxResults: Int): List<SessionMetadata> {
        val claudeDir = getClaudeDir()
        val projectId = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val projectDir = File(claudeDir, "projects/$projectId")

        if (!projectDir.exists() || !projectDir.isDirectory) {
            logger.info("[SessionScanner] 项目目录不存在: ${projectDir.absolutePath}")
            return emptyList()
        }

        val jsonlFiles = projectDir.listFiles { file ->
            file.extension == "jsonl"
        } ?: emptyArray()

        logger.info("[SessionScanner] 找到 ${jsonlFiles.size} 个会话文件")

        return jsonlFiles
            .mapNotNull { file -> extractSessionMetadata(file, projectPath) }
            .sortedByDescending { it.timestamp }
            .take(maxResults)
    }

    /**
     * 从 JSONL 文件提取会话元数据
     */
    private fun extractSessionMetadata(file: File, projectPath: String): SessionMetadata? {
        var firstUserMessage: String? = null
        var lastTimestamp = 0L
        var messageCount = 0
        var isSubagent = false

        file.forEachLine { line ->
            if (line.isBlank()) return@forEachLine

            try {
                val json = jsonParser.parseToJsonElement(line).jsonObject

                // 检查是否为子代理会话（需要过滤）
                if (json["isSidechain"]?.jsonPrimitive?.booleanOrNull == true ||
                    json["userType"]?.jsonPrimitive?.contentOrNull == "internal") {
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
                json["timestamp"]?.jsonPrimitive?.contentOrNull?.let { ts ->
                    parseTimestamp(ts).takeIf { it > lastTimestamp }?.let { lastTimestamp = it }
                }
            } catch (e: Exception) {
                // 跳过无效行
            }
        }

        // 过滤子代理会话和无效会话
        if (isSubagent || firstUserMessage == null || messageCount == 0) {
            return null
        }

        // 如果没有时间戳，使用文件修改时间
        if (lastTimestamp == 0L) {
            lastTimestamp = file.lastModified()
        }

        return SessionMetadata(
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
