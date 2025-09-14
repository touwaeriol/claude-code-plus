/*
 * CliOutputParser.kt
 *
 * 专门用于解析Claude CLI实时输出的解析器
 * 处理CLI输出流中的JSON行，支持session_id等下划线格式字段
 */

package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.UniversalClaudeMessage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import mu.KotlinLogging

/**
 * CLI输出解析器
 *
 * 专门处理Claude CLI实时输出的反序列化
 * 特点：
 * - 预处理字段名：将session_id等下划线格式转换为驼峰格式
 * - 处理event嵌套结构
 * - 支持流式输出的不完整行处理
 * - 宽松的错误处理
 */
class CliOutputParser {
    private val logger = KotlinLogging.logger {}

    /**
     * JSON配置：专门用于CLI输出
     */
    private val json = Json {
        ignoreUnknownKeys = true    // CLI可能有未定义字段
        isLenient = true           // 支持宽松格式
        coerceInputValues = true   // 强制转换输入值
        allowSpecialFloatingPointValues = true
    }

    /**
     * 解析单行CLI JSON输出
     *
     * @param jsonString CLI输出的单行JSON字符串
     * @return 解析结果，失败时返回null
     */
    fun parseCliLine(jsonString: String): UniversalClaudeMessage? {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        return try {
            // 预处理JSON：将CLI下划线字段转换为驼峰格式
            val preprocessedJson = preprocessCliFieldNames(trimmed)
            val message = json.decodeFromString<UniversalClaudeMessage>(preprocessedJson)
            logger.debug("[CliParser] 解析成功: type=${message.type}, uuid=${message.uuid}")
            message
        } catch (e: SerializationException) {
            logger.warn("[CliParser] CLI JSON序列化失败: ${e.message}")
            logger.debug("[CliParser] 失败的JSON: $trimmed")
            null
        } catch (e: Exception) {
            logger.error("[CliParser] CLI JSON解析异常", e)
            null
        }
    }

    /**
     * 预处理CLI字段名：将下划线格式转换为驼峰格式
     *
     * @param jsonString 原CLI JSON字符串
     * @return 预处理后的JSON字符串
     */
    private fun preprocessCliFieldNames(jsonString: String): String {
        var processed = jsonString

        // 定义字段名映射：CLI下划线 → 驼峰格式
        val fieldMappings = mapOf(
            "session_id" to "sessionId",
            "parent_uuid" to "parentUuid",
            "mcp_servers" to "mcpServers",
            "duration_ms" to "durationMs",
            "duration_api_ms" to "durationApiMs",
            "num_turns" to "numTurns",
            "is_error" to "isError",
            "total_cost_usd" to "totalCostUsd",
            "input_tokens" to "inputTokens",
            "output_tokens" to "outputTokens",
            "cache_creation_input_tokens" to "cacheCreationInputTokens",
            "cache_read_input_tokens" to "cacheReadInputTokens",
            "service_tier" to "serviceTier"
        )

        // 逐个替换字段名
        fieldMappings.forEach { (underscoreField, camelField) ->
            // 使用正则表达式确保只替换字段名，不替换字符串值
            val regex = "\"$underscoreField\"\\s*:".toRegex()
            processed = processed.replace(regex, "\"$camelField\":")
        }

        logger.debug("[CliParser] 字段名预处理完成")
        return processed
    }

    /**
     * 解析CLI流输出
     * 处理可能包含多行JSON的CLI输出流
     *
     * @param streamContent CLI输出流内容
     * @return 成功解析的消息列表
     */
    fun parseCliStream(streamContent: String): List<UniversalClaudeMessage> {
        val results = mutableListOf<UniversalClaudeMessage>()
        val lines = streamContent.split('\n')

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && trimmed.startsWith('{') && trimmed.endsWith('}')) {
                parseCliLine(trimmed)?.let { message ->
                    results.add(message)
                    logger.debug("[CliParser] 流解析第${index + 1}行成功: type=${message.type}")
                }
            }
        }

        logger.info("[CliParser] CLI流解析完成: 总行数=${lines.size}, 成功解析=${results.size}")
        return results
    }

    /**
     * 获取CLI解析统计信息
     */
    fun getCliParsingStats(messages: List<UniversalClaudeMessage>): CliParsingStats {
        val streamEvents = messages.count { it.event != null }
        val systemMessages = messages.count { it.isSystemInit() }
        val userMessages = messages.count { it.isUserMessage() }
        val assistantMessages = messages.count { it.isAssistantMessage() }
        val sessionIds = messages.mapNotNull { it.sessionId }.distinct()

        return CliParsingStats(
            totalMessages = messages.size,
            streamEvents = streamEvents,
            systemMessages = systemMessages,
            userMessages = userMessages,
            assistantMessages = assistantMessages,
            sessionIds = sessionIds
        )
    }
}

/**
 * CLI解析统计信息
 */
data class CliParsingStats(
    val totalMessages: Int,
    val streamEvents: Int,
    val systemMessages: Int,
    val userMessages: Int,
    val assistantMessages: Int,
    val sessionIds: List<String>
) {
    override fun toString(): String {
        return buildString {
            appendLine("=== CLI解析器统计 ===")
            appendLine("总消息数: $totalMessages")
            appendLine("流事件: $streamEvents")
            appendLine("系统消息: $systemMessages")
            appendLine("用户消息: $userMessages")
            appendLine("助手消息: $assistantMessages")
            appendLine("会话数: ${sessionIds.size}")
            if (sessionIds.isNotEmpty()) {
                appendLine("会话ID:")
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