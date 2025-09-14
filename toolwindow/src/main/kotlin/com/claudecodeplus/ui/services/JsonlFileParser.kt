/*
 * JsonlFileParser.kt
 *
 * 专门用于解析JSONL历史文件的解析器
 * 逐行读取和解析JSONL文件，支持sessionId等驼峰格式字段
 */

package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.UniversalClaudeMessage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.StringReader

/**
 * JSONL文件解析器
 *
 * 专门处理JSONL历史会话文件的反序列化
 * 特点：
 * - 处理sessionId等驼峰格式字段（通过@SerialName自动映射session_id）
 * - 真正的逐行文件读取和解析
 * - 支持大文件的流式处理
 * - 完整的错误处理和统计
 */
class JsonlFileParser {
    private val logger = KotlinLogging.logger {}

    /**
     * JSON配置：专门用于JSONL文件
     */
    private val json = Json {
        ignoreUnknownKeys = true    // JSONL可能有历史版本的字段
        isLenient = true           // 支持宽松格式
        coerceInputValues = true   // 强制转换输入值
        allowSpecialFloatingPointValues = true
    }

    /**
     * 解析单行JSONL记录
     *
     * @param jsonString JSONL文件中的单行JSON字符串
     * @param lineNumber 行号（用于错误日志）
     * @return 解析结果，失败时返回null
     */
    fun parseJsonlLine(jsonString: String, lineNumber: Int = -1): UniversalClaudeMessage? {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        return try {
            val message = json.decodeFromString<UniversalClaudeMessage>(trimmed)
            logger.debug("[JsonlParser] 行 $lineNumber 解析成功: type=${message.type}, uuid=${message.uuid}")
            message
        } catch (e: SerializationException) {
            logger.warn("[JsonlParser] 行 $lineNumber JSONL序列化失败: ${e.message}")
            logger.debug("[JsonlParser] 失败的JSON: $trimmed")
            null
        } catch (e: Exception) {
            logger.error("[JsonlParser] 行 $lineNumber JSONL解析异常", e)
            null
        }
    }

    /**
     * 解析完整的JSONL文件内容
     * 真正的逐行读取和解析，支持大文件
     *
     * @param fileContent JSONL文件的完整内容
     * @return 成功解析的消息列表
     */
    fun parseJsonlFile(fileContent: String): List<UniversalClaudeMessage> {
        val results = mutableListOf<UniversalClaudeMessage>()
        var lineNumber = 0
        var successCount = 0
        var failCount = 0

        try {
            BufferedReader(StringReader(fileContent)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    lineNumber++
                    line?.let { currentLine ->
                        parseJsonlLine(currentLine, lineNumber)?.let { message ->
                            results.add(message)
                            successCount++
                        } ?: run {
                            if (currentLine.trim().isNotEmpty()) {
                                failCount++
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("[JsonlParser] 文件读取异常", e)
        }

        logger.info("[JsonlParser] JSONL文件解析完成: 总行数=$lineNumber, 成功=$successCount, 失败=$failCount")
        return results
    }

    /**
     * 逐行解析JSONL内容（流式处理）
     * 当需要处理非常大的JSONL文件时使用
     *
     * @param fileContent JSONL文件内容
     * @param processLine 每解析成功一行就调用此函数处理
     */
    fun parseJsonlFileStreaming(
        fileContent: String,
        processLine: (UniversalClaudeMessage, Int) -> Unit
    ): JsonlParsingStats {
        var lineNumber = 0
        var successCount = 0
        var failCount = 0
        val sessionIds = mutableSetOf<String>()
        val messageTypes = mutableMapOf<String?, Int>()

        try {
            BufferedReader(StringReader(fileContent)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    lineNumber++
                    line?.let { currentLine ->
                        parseJsonlLine(currentLine, lineNumber)?.let { message ->
                            // 统计信息
                            successCount++
                            message.sessionId?.let { sessionIds.add(it) }
                            messageTypes[message.type] = (messageTypes[message.type] ?: 0) + 1

                            // 处理消息
                            processLine(message, lineNumber)
                        } ?: run {
                            if (currentLine.trim().isNotEmpty()) {
                                failCount++
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("[JsonlParser] 流式解析异常", e)
        }

        return JsonlParsingStats(
            totalLines = lineNumber,
            successfulMessages = successCount,
            failedMessages = failCount,
            sessionIds = sessionIds.toList(),
            messageTypeCount = messageTypes.toMap()
        )
    }

    /**
     * 获取JSONL解析统计信息
     */
    fun getJsonlParsingStats(messages: List<UniversalClaudeMessage>): JsonlParsingStats {
        val sessionIds = messages.mapNotNull { it.sessionId }.distinct()
        val messageTypeCount = messages.groupBy { it.type }.mapValues { it.value.size }

        return JsonlParsingStats(
            totalLines = messages.size,
            successfulMessages = messages.size,
            failedMessages = 0,
            sessionIds = sessionIds,
            messageTypeCount = messageTypeCount
        )
    }
}

/**
 * JSONL解析统计信息
 */
data class JsonlParsingStats(
    val totalLines: Int,
    val successfulMessages: Int,
    val failedMessages: Int,
    val sessionIds: List<String>,
    val messageTypeCount: Map<String?, Int>
) {
    val successRate: Double = if (totalLines > 0) successfulMessages.toDouble() / totalLines else 0.0

    override fun toString(): String {
        return buildString {
            appendLine("=== JSONL解析器统计 ===")
            appendLine("总行数: $totalLines")
            appendLine("成功解析: $successfulMessages")
            appendLine("失败: $failedMessages")
            appendLine("成功率: ${"%.1f".format(successRate * 100)}%")
            appendLine("会话数: ${sessionIds.size}")
            appendLine("消息类型分布:")
            messageTypeCount.toSortedMap(nullsFirst()).forEach { (type, count) ->
                appendLine("  ${type ?: "unknown"}: $count")
            }
            if (sessionIds.isNotEmpty()) {
                appendLine("会话ID样例:")
                sessionIds.take(3).forEach { sessionId ->
                    appendLine("  $sessionId")
                }
                if (sessionIds.size > 3) {
                    appendLine("  ... 还有 ${sessionIds.size - 3} 个会话")
                }
            }
        }
    }
}