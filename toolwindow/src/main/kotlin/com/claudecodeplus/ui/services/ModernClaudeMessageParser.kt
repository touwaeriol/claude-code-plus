/*
 * ModernClaudeMessageParser.kt
 *
 * 现代化的Claude CLI消息解析器
 * 基于Kotlinx Serialization，替换手动Gson解析
 */

package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.ClaudeMessage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import mu.KotlinLogging

/**
 * 现代化的Claude消息解析器
 *
 * 特点：
 * - 基于Kotlinx Serialization的高性能解析
 * - 智能多字段组合类型判断
 * - 类型安全的编译时检查
 * - 优雅的错误处理和降级机制
 */
class ModernClaudeMessageParser {
    private val logger = KotlinLogging.logger {}

    /**
     * JSON配置：宽松模式，忽略未知字段
     */
    private val json = Json {
        ignoreUnknownKeys = true    // 忽略未定义的字段
        isLenient = true           // 宽松模式，支持注释等
        coerceInputValues = true   // 强制转换输入值
        allowSpecialFloatingPointValues = true // 支持NaN, Infinity等特殊值
        allowStructuredMapKeys = true // 支持复杂的Map键
    }

    /**
     * 解析单行JSONL
     *
     * @param jsonString JSON字符串
     * @param lineNumber 行号（用于错误日志）
     * @return 解析结果，失败时返回null
     */
    fun parseJsonLine(jsonString: String, lineNumber: Int = -1): ParseResult? {
        if (jsonString.trim().isEmpty()) {
            return null
        }

        return try {
            val claudeMessage = json.decodeFromString<ClaudeMessage>(jsonString)

            // 记录成功解析的调试信息
            when (claudeMessage) {
                is ClaudeMessage.UserMessage -> {
                    logger.debug("[ModernParser] 成功解析用户消息: ${claudeMessage.uuid}")
                }
                is ClaudeMessage.AssistantMessage -> {
                    logger.debug("[ModernParser] 成功解析助手消息: ${claudeMessage.uuid}")
                }
                is ClaudeMessage.SystemInitMessage -> {
                    logger.debug("[ModernParser] 成功解析系统初始化: ${claudeMessage.sessionId}")
                }
                is ClaudeMessage.ResultMessage -> {
                    logger.debug("[ModernParser] 成功解析结果消息: ${claudeMessage.sessionId}")
                }
                is ClaudeMessage.SummaryMessage -> {
                    logger.debug("[ModernParser] 成功解析摘要消息: ${claudeMessage.summary}")
                }
                is ClaudeMessage.UnknownMessage -> {
                    logger.warn("[ModernParser] 解析为未知消息类型: type=${claudeMessage.rawType}, subtype=${claudeMessage.rawSubtype}")
                }
            }

            ParseResult(
                message = claudeMessage,
                sessionId = claudeMessage.sessionId
            )

        } catch (e: SerializationException) {
            logger.warn("[ModernParser] 行 $lineNumber 序列化失败: ${e.message}")
            logger.debug("[ModernParser] 失败的JSON: $jsonString")
            null
        } catch (e: Exception) {
            logger.error("[ModernParser] 行 $lineNumber 解析异常", e)
            logger.debug("[ModernParser] 异常JSON: $jsonString")
            null
        }
    }

    /**
     * 批量解析JSONL内容
     *
     * @param jsonlContent JSONL文件内容
     * @return 解析结果列表
     */
    fun parseJsonlContent(jsonlContent: String): List<ParseResult> {
        val results = mutableListOf<ParseResult>()
        var lineNumber = 0

        jsonlContent.lines().forEach { line ->
            lineNumber++
            val result = parseJsonLine(line.trim(), lineNumber)
            result?.let { results.add(it) }
        }

        logger.info("[ModernParser] JSONL解析完成: 总行数=$lineNumber, 成功解析=${results.size}")
        return results
    }

    /**
     * 获取解析统计信息
     */
    fun getParsingStats(results: List<ParseResult>): ParsingStats {
        val messageTypeCount = results.groupBy {
            it.message::class.simpleName
        }.mapValues { it.value.size }

        return ParsingStats(
            totalMessages = results.size,
            messageTypeCount = messageTypeCount,
            sessionIds = results.mapNotNull { it.sessionId }.distinct()
        )
    }
}

/**
 * 解析结果
 */
data class ParseResult(
    val message: ClaudeMessage,
    val sessionId: String?
)

/**
 * 解析统计信息
 */
data class ParsingStats(
    val totalMessages: Int,
    val messageTypeCount: Map<String?, Int>,
    val sessionIds: List<String>
) {
    override fun toString(): String {
        return buildString {
            appendLine("=== 解析统计 ===")
            appendLine("总消息数: $totalMessages")
            appendLine("会话数: ${sessionIds.size}")
            appendLine("消息类型分布:")
            messageTypeCount.forEach { (type, count) ->
                appendLine("  $type: $count")
            }
        }
    }
}