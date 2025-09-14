/*
 * UniversalMessageParser.kt
 *
 * 统一的Claude消息解析器
 * 同时支持CLI输出和JSONL文件格式的解析
 */

package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.UniversalClaudeMessage
import com.claudecodeplus.ui.models.UniversalTokenUsage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import mu.KotlinLogging

/**
 * 基础消息解析工具类
 *
 * 提供最基本的JSON解析功能，不做任何格式判断
 * 具体格式请使用专门的 CliOutputParser 或 JsonlFileParser
 */
class UniversalMessageParser {
    private val logger = KotlinLogging.logger {}

    /**
     * JSON配置：宽松模式，支持字段映射
     */
    private val json = Json {
        ignoreUnknownKeys = true    // 忽略未定义的字段
        isLenient = true           // 宽松模式，支持注释等
        coerceInputValues = true   // 强制转换输入值
        allowSpecialFloatingPointValues = true // 支持NaN, Infinity等
        allowStructuredMapKeys = true // 支持复杂的Map键
        prettyPrint = false        // 压缩输出，提高性能
    }

    /**
     * 解析单行JSON字符串
     *
     * @param jsonString JSON字符串
     * @param lineNumber 行号（用于错误日志）
     * @return 解析结果，失败时返回null
     */
    fun parseJsonLine(jsonString: String, lineNumber: Int = -1): UniversalClaudeMessage? {
        if (jsonString.trim().isEmpty()) {
            return null
        }

        return try {
            val message = json.decodeFromString<UniversalClaudeMessage>(jsonString)

            // 记录解析成功的调试信息
            logParseSuccess(message, lineNumber)

            message
        } catch (e: SerializationException) {
            logger.warn("[UniversalParser] 行 $lineNumber 序列化失败: ${e.message}")
            logger.debug("[UniversalParser] 失败的JSON: $jsonString")
            null
        } catch (e: Exception) {
            logger.error("[UniversalParser] 行 $lineNumber 解析异常", e)
            logger.debug("[UniversalParser] 异常JSON: $jsonString")
            null
        }
    }

    /**
     * 简单的批量解析功能
     * 注意：推荐使用专门的 CliOutputParser 或 JsonlFileParser
     *
     * @param content 多行JSON内容
     * @return 解析结果列表
     */
    fun parseMultilineContent(content: String): List<UniversalClaudeMessage> {
        val results = mutableListOf<UniversalClaudeMessage>()
        var lineNumber = 0

        content.lines().forEach { line ->
            lineNumber++
            val result = parseJsonLine(line.trim(), lineNumber)
            result?.let { results.add(it) }
        }

        logger.info("[UniversalParser] 批量解析完成: 总行数=$lineNumber, 成功解析=${results.size}")
        return results
    }


    /**
     * 获取基础统计信息
     */
    fun getBasicStats(messages: List<UniversalClaudeMessage>): BasicParsingStats {
        val messageTypeCount = messages.groupBy { it.type }.mapValues { it.value.size }
        val sessionIds = messages.mapNotNull { it.sessionId }.distinct()

        return BasicParsingStats(
            totalMessages = messages.size,
            messageTypeCount = messageTypeCount,
            sessionIds = sessionIds
        )
    }

    /**
     * 记录解析成功的调试信息
     */
    private fun logParseSuccess(message: UniversalClaudeMessage, lineNumber: Int) {
        when {
            message.isSystemInit() -> {
                logger.debug("[UniversalParser] 行 $lineNumber: 系统初始化消息, session=${message.sessionId}")
            }
            message.isUserMessage() -> {
                logger.debug("[UniversalParser] 行 $lineNumber: 用户消息, uuid=${message.uuid}")
            }
            message.isAssistantMessage() -> {
                logger.debug("[UniversalParser] 行 $lineNumber: 助手消息, uuid=${message.uuid}")
            }
            message.isStreamEvent() -> {
                val eventType = message.event?.get("type")?.toString()?.trim('"')
                logger.debug("[UniversalParser] 行 $lineNumber: 流事件, type=$eventType")
            }
            message.isSummaryMessage() -> {
                logger.debug("[UniversalParser] 行 $lineNumber: 摘要消息, summary=${message.summary?.take(50)}")
            }
            message.isResultMessage() -> {
                logger.debug("[UniversalParser] 行 $lineNumber: 结果消息, result=${message.result}")
            }
            else -> {
                logger.debug("[UniversalParser] 行 $lineNumber: 其他消息类型, type=${message.type}")
            }
        }
    }
}

/**
 * 基础解析统计信息
 */
data class BasicParsingStats(
    val totalMessages: Int,
    val messageTypeCount: Map<String?, Int>,
    val sessionIds: List<String>
) {
    override fun toString(): String {
        return buildString {
            appendLine("=== 基础解析统计 ===")
            appendLine("总消息数: $totalMessages")
            appendLine("会话数: ${sessionIds.size}")
            appendLine("消息类型分布:")
            messageTypeCount.toSortedMap(nullsFirst()).forEach { (type, count) ->
                appendLine("  ${type ?: "unknown"}: $count")
            }
            if (sessionIds.isNotEmpty()) {
                appendLine("会话ID列表:")
                sessionIds.take(5).forEach { sessionId ->
                    appendLine("  $sessionId")
                }
                if (sessionIds.size > 5) {
                    appendLine("  ... 还有 ${sessionIds.size - 5} 个会话")
                }
            }
        }
    }
}

/**
 * 通用解析结果包装器
 * 提供消息的基本信息，不做格式判断
 */
data class UniversalParseResult(
    val message: UniversalClaudeMessage,
    val sessionId: String?,
    val messageType: UniversalMessageType
) {
    companion object {
        fun from(message: UniversalClaudeMessage): UniversalParseResult {
            return UniversalParseResult(
                message = message,
                sessionId = message.sessionId,
                messageType = when {
                    message.isUserMessage() -> UniversalMessageType.USER
                    message.isAssistantMessage() -> UniversalMessageType.ASSISTANT
                    message.isSystemInit() -> UniversalMessageType.SYSTEM_INIT
                    message.isResultMessage() -> UniversalMessageType.RESULT
                    message.isSummaryMessage() -> UniversalMessageType.SUMMARY
                    message.isStreamEvent() -> UniversalMessageType.STREAM_EVENT
                    else -> UniversalMessageType.UNKNOWN
                }
            )
        }
    }
}

/**
 * 通用消息类型枚举
 */
enum class UniversalMessageType {
    USER,           // 用户消息
    ASSISTANT,      // 助手消息
    SYSTEM_INIT,    // 系统初始化
    RESULT,         // 结果汇总
    SUMMARY,        // 会话摘要
    STREAM_EVENT,   // 流事件
    UNKNOWN         // 未知类型
}