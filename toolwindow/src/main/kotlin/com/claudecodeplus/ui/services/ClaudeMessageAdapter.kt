/*
 * ClaudeMessageAdapter.kt
 *
 * 兼容性适配器：将新的ClaudeMessage转换为现有的EnhancedMessage
 * 确保现有代码100%兼容，无需修改
 */

package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * ClaudeMessage -> EnhancedMessage 适配器
 *
 * 职责：
 * 1. 类型转换：将新的类型安全消息转换为现有消息格式
 * 2. 字段映射：处理字段名称和结构的差异
 * 3. 内容提取：从复杂的嵌套结构中提取文本内容
 * 4. 兼容性保证：确保现有代码无需修改
 */
class ClaudeMessageAdapter {
    private val logger = KotlinLogging.logger {}

    /**
     * 将ClaudeMessage转换为EnhancedMessage
     */
    fun toEnhancedMessage(claudeMessage: ClaudeMessage): EnhancedMessage? {
        return try {
            when (claudeMessage) {
                is ClaudeMessage.UserMessage -> convertUserMessage(claudeMessage)
                is ClaudeMessage.AssistantMessage -> convertAssistantMessage(claudeMessage)
                is ClaudeMessage.SystemInitMessage -> {
                    // 系统初始化消息通常不需要转换为EnhancedMessage
                    logger.debug("[Adapter] 跳过系统初始化消息: ${claudeMessage.sessionId}")
                    null
                }
                is ClaudeMessage.ResultMessage -> {
                    // 结果消息通常用于统计，不转换为EnhancedMessage
                    logger.debug("[Adapter] 跳过结果消息: ${claudeMessage.sessionId}")
                    null
                }
                is ClaudeMessage.SummaryMessage -> {
                    // 摘要消息不转换为EnhancedMessage
                    logger.debug("[Adapter] 跳过摘要消息: ${claudeMessage.summary}")
                    null
                }
                is ClaudeMessage.UnknownMessage -> {
                    logger.warn("[Adapter] 跳过未知消息类型: ${claudeMessage.rawType}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("[Adapter] 转换失败", e)
            null
        }
    }

    /**
     * 批量转换
     */
    fun toEnhancedMessages(claudeMessages: List<ClaudeMessage>): List<EnhancedMessage> {
        return claudeMessages.mapNotNull { toEnhancedMessage(it) }
    }

    /**
     * 转换用户消息
     */
    private fun convertUserMessage(message: ClaudeMessage.UserMessage): EnhancedMessage {
        val content = message.message?.content ?: ""
        val timestamp = parseTimestamp(message.timestamp) ?: System.currentTimeMillis()

        return EnhancedMessage(
            id = message.uuid ?: "",
            role = MessageRole.USER,
            content = content,
            timestamp = timestamp,
            contexts = emptyList(),
            toolCalls = emptyList(), // 用户消息通常没有工具调用
            model = null, // 用户消息没有模型信息
            status = MessageStatus.COMPLETE,
            isStreaming = false,
            isError = false,
            orderedElements = emptyList(),
            tokenUsage = null, // 用户消息通常没有token统计
            isCompactSummary = false
        )
    }

    /**
     * 转换助手消息
     */
    private fun convertAssistantMessage(message: ClaudeMessage.AssistantMessage): EnhancedMessage {
        val messageContent = message.message
        val content = extractContentFromAssistantMessage(messageContent)
        val timestamp = parseTimestamp(message.timestamp) ?: System.currentTimeMillis()

        // 转换模型信息
        val aiModel = messageContent?.model?.let { modelName ->
            // 简单的模型映射，可以根据需要扩展
            when {
                modelName.contains("opus") -> AiModel.OPUS
                modelName.contains("sonnet") -> AiModel.SONNET
                else -> AiModel.DEFAULT // 使用默认模型
            }
        }

        // 转换Token使用信息
        val tokenUsage = messageContent?.usage?.toTokenUsage()

        // 提取工具调用（如果有）
        val toolCalls = extractToolCalls(messageContent)

        return EnhancedMessage(
            id = message.uuid ?: "",
            role = MessageRole.ASSISTANT,
            content = content,
            timestamp = timestamp,
            toolCalls = toolCalls,
            model = aiModel,
            status = MessageStatus.COMPLETE,
            isStreaming = false,
            isError = false,
            orderedElements = emptyList(),
            tokenUsage = tokenUsage,
            isCompactSummary = false
        )
    }

    /**
     * 从助手消息中提取文本内容
     */
    private fun extractContentFromAssistantMessage(messageContent: AssistantMessageContent?): String {
        if (messageContent?.content == null) {
            return ""
        }

        return try {
            val contentParts = mutableListOf<String>()

            messageContent.content!!.forEach { contentBlock ->
                val type = contentBlock["type"]?.jsonPrimitive?.content
                when (type) {
                    "text" -> {
                        val text = contentBlock["text"]?.jsonPrimitive?.content
                        if (!text.isNullOrBlank()) {
                            contentParts.add(text)
                        }
                    }
                    "tool_use" -> {
                        val name = contentBlock["name"]?.jsonPrimitive?.content ?: "unknown"
                        contentParts.add("[Tool: $name]")
                    }
                    // 可以添加其他类型的处理
                }
            }

            contentParts.joinToString("\n")
        } catch (e: Exception) {
            logger.warn("[Adapter] 提取助手消息内容失败", e)
            ""
        }
    }

    /**
     * 提取工具调用信息
     */
    private fun extractToolCalls(messageContent: AssistantMessageContent?): List<ToolCall> {
        if (messageContent?.content == null) {
            return emptyList()
        }

        return try {
            val toolCalls = mutableListOf<ToolCall>()

            messageContent.content!!.forEach { contentBlock ->
                val type = contentBlock["type"]?.jsonPrimitive?.content
                if (type == "tool_use") {
                    val id = contentBlock["id"]?.jsonPrimitive?.content ?: ""
                    val name = contentBlock["name"]?.jsonPrimitive?.content ?: ""
                    val inputObject = contentBlock["input"]?.jsonObject

                    // 转换input参数
                    val parameters = inputObject?.entries?.associate { (key, value) ->
                        key to when {
                            value.jsonPrimitive.isString -> value.jsonPrimitive.content
                            else -> value.toString()
                        }
                    } ?: emptyMap()

                    toolCalls.add(ToolCall(
                        id = id,
                        name = name,
                        parameters = parameters,
                        toolType = ToolType.OTHER // 使用默认工具类型
                    ))
                }
            }

            toolCalls
        } catch (e: Exception) {
            logger.warn("[Adapter] 提取工具调用失败", e)
            emptyList()
        }
    }

    /**
     * 解析时间戳字符串
     */
    private fun parseTimestamp(timestampStr: String?): Long? {
        if (timestampStr.isNullOrBlank()) {
            return null
        }

        return try {
            Instant.parse(timestampStr).toEpochMilli()
        } catch (e: DateTimeParseException) {
            logger.warn("[Adapter] 时间戳解析失败: $timestampStr", e)
            null
        }
    }

    /**
     * 提取会话ID更新信息
     */
    fun extractSessionIdUpdate(claudeMessage: ClaudeMessage): String? {
        return when (claudeMessage) {
            is ClaudeMessage.SystemInitMessage -> claudeMessage.sessionId
            else -> null
        }
    }

    /**
     * 检查是否为Token统计消息
     */
    fun isTokenStatMessage(claudeMessage: ClaudeMessage): Boolean {
        return claudeMessage is ClaudeMessage.ResultMessage && claudeMessage.usage != null
    }

    /**
     * 提取Token统计信息
     */
    fun extractTokenUsage(claudeMessage: ClaudeMessage): EnhancedMessage.TokenUsage? {
        return when (claudeMessage) {
            is ClaudeMessage.AssistantMessage -> claudeMessage.message?.usage?.toTokenUsage()
            is ClaudeMessage.ResultMessage -> claudeMessage.usage?.toTokenUsage()
            else -> null
        }
    }
}