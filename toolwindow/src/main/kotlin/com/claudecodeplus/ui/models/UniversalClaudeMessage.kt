/*
 * UniversalClaudeMessage.kt
 *
 * 统一的Claude消息对象，同时支持CLI输出和JSONL文件格式
 * 通过@SerialName注解处理字段命名差异（session_id vs sessionId）
 */

package com.claudecodeplus.ui.models

import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * 统一的Claude消息对象
 *
 * 设计目标：
 * 1. 同时支持CLI实时输出和JSONL历史文件格式
 * 2. 处理字段命名差异（下划线 vs 驼峰）
 * 3. 保持向前向后兼容性
 * 4. 简化解析和处理逻辑
 *
 * 支持的消息类型：
 * - CLI: system, stream_event, user, assistant
 * - JSONL: user, assistant, system, result, summary
 */
@Serializable
data class UniversalClaudeMessage(
    // === 通用字段 ===
    val type: String? = null,
    val subtype: String? = null,
    val uuid: String? = null,
    val timestamp: String? = null,

    // === 关键字段映射：处理CLI和JSONL的命名差异 ===

    /**
     * 会话ID - 统一驼峰格式
     * CLI输出: 通过预处理转换 "session_id" → "sessionId"
     * JSONL文件: 直接映射 "sessionId"
     */
    val sessionId: String? = null,

    /**
     * 父消息UUID - 统一驼峰格式
     * CLI输出: 通过预处理转换 "parent_uuid" → "parentUuid"
     * JSONL文件: 直接映射 "parentUuid"
     */
    val parentUuid: String? = null,

    // === 消息内容相关 ===

    /**
     * 消息内容 - 通用字段
     * 用户消息: {"role": "user", "content": "text"}
     * 助手消息: {"role": "assistant", "content": [...], "usage": {...}}
     */
    val message: JsonObject? = null,

    /**
     * CLI特有：流事件的嵌套数据
     * CLI输出: "event": {"type": "message_start", "message": {...}}
     */
    val event: JsonObject? = null,

    /**
     * 工具使用结果
     * 主要在用户消息中出现
     */
    val toolUseResult: JsonObject? = null,

    // === Token使用统计 ===

    /**
     * Token使用统计
     * CLI和JSONL都使用相同的字段结构
     */
    val usage: UniversalTokenUsage? = null,

    // === 环境信息 ===
    val cwd: String? = null,
    val version: String? = null,
    val gitBranch: String? = null,
    val userType: String? = null,
    val isSidechain: Boolean? = null,
    val requestId: String? = null,

    // === 系统初始化相关 ===
    val apiKeySource: String? = null,
    val tools: List<String>? = null,

    /**
     * MCP服务器信息
     * CLI输出: 通过预处理转换 "mcp_servers" → "mcpServers"
     * JSONL文件: 直接映射 "mcpServers"
     */
    val mcpServers: List<JsonObject>? = null,

    val model: String? = null,
    val permissionMode: String? = null,
    val slashCommands: List<String>? = null,
    val outputStyle: String? = null,

    // === 结果消息相关 ===
    /**
     * 会话持续时间（毫秒）
     * CLI输出: 通过预处理转换 "duration_ms" → "durationMs"
     */
    val durationMs: Long? = null,

    /**
     * API调用持续时间（毫秒）
     * CLI输出: 通过预处理转换 "duration_api_ms" → "durationApiMs"
     */
    val durationApiMs: Long? = null,

    /**
     * 对话轮数
     * CLI输出: 通过预处理转换 "num_turns" → "numTurns"
     */
    val numTurns: Int? = null,

    /**
     * 是否为错误状态
     * CLI输出: 通过预处理转换 "is_error" → "isError"
     */
    val isError: Boolean? = null,

    val result: String? = null,

    /**
     * 总费用（美元）
     * CLI输出: 通过预处理转换 "total_cost_usd" → "totalCostUsd"
     */
    val totalCostUsd: Double? = null,

    // === 摘要消息相关 ===
    val summary: String? = null,
    val leafUuid: String? = null
) {



    /**
     * 判断是否为系统初始化消息
     */
    fun isSystemInit(): Boolean {
        return type == "system" && subtype == "init"
    }

    /**
     * 判断是否为用户消息
     */
    fun isUserMessage(): Boolean {
        return type == "user" || message?.get("role")?.jsonPrimitive?.content == "user"
    }

    /**
     * 判断是否为助手消息
     */
    fun isAssistantMessage(): Boolean {
        return type == "assistant" || message?.get("role")?.jsonPrimitive?.content == "assistant"
    }

    /**
     * 判断是否为流事件
     */
    fun isStreamEvent(): Boolean {
        return type == "stream_event" && event != null
    }

    /**
     * 判断是否为结果汇总消息
     */
    fun isResultMessage(): Boolean {
        return type == "result"
    }

    /**
     * 判断是否为会话摘要消息
     */
    fun isSummaryMessage(): Boolean {
        return type == "summary" || summary != null
    }

    /**
     * 获取显示用的内容文本
     */
    fun getDisplayContent(): String {
        return when {
            isUserMessage() -> message?.get("content")?.jsonPrimitive?.content ?: ""
            isAssistantMessage() -> extractAssistantContent()
            isSystemInit() -> "系统初始化: ${model ?: "未知模型"}"
            isResultMessage() -> "会话结束: $result"
            isSummaryMessage() -> summary ?: ""
            isStreamEvent() -> extractStreamEventContent()
            else -> "未知消息类型: type=$type, subtype=$subtype"
        }
    }

    /**
     * 提取助手消息的文本内容
     */
    private fun extractAssistantContent(): String {
        val messageContent = message?.get("content")
        return when {
            messageContent is JsonPrimitive && messageContent.isString -> messageContent.content
            messageContent is JsonArray -> {
                messageContent.mapNotNull { element ->
                    element.jsonObject.let { block ->
                        when (block["type"]?.jsonPrimitive?.content) {
                            "text" -> block["text"]?.jsonPrimitive?.content
                            "tool_use" -> {
                                val name = block["name"]?.jsonPrimitive?.content ?: "unknown"
                                "[Tool: $name]"
                            }
                            else -> null
                        }
                    }
                }.joinToString("\n")
            }
            else -> "[助手响应]"
        }
    }

    /**
     * 提取流事件的内容
     */
    private fun extractStreamEventContent(): String {
        val eventObj = event ?: return "[流事件]"
        val eventType = eventObj["type"]?.jsonPrimitive?.content

        return when (eventType) {
            "message_start" -> "[消息开始]"
            "content_block_start" -> "[内容块开始]"
            "content_block_delta" -> {
                val delta = eventObj["delta"]?.jsonObject
                val text = delta?.get("text")?.jsonPrimitive?.content
                text ?: "[内容增量]"
            }
            "content_block_stop" -> "[内容块结束]"
            "message_stop" -> "[消息结束]"
            else -> "[流事件: $eventType]"
        }
    }


    /**
     * 获取Token使用统计
     */
    fun getTokenUsage(): UniversalTokenUsage? {
        // 优先使用顶级usage字段，然后尝试message.usage
        return usage ?: message?.get("usage")?.let { usageElement ->
            try {
                Json.decodeFromJsonElement<UniversalTokenUsage>(usageElement)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 统一的Token使用量数据
 * 支持CLI和JSONL两种格式的所有token字段
 */
@Serializable
data class UniversalTokenUsage(
    /**
     * 输入Token数
     * CLI输出: 通过预处理转换 "input_tokens" → "inputTokens"
     */
    val inputTokens: Int? = null,

    /**
     * 输出Token数
     * CLI输出: 通过预处理转换 "output_tokens" → "outputTokens"
     */
    val outputTokens: Int? = null,

    /**
     * 缓存创建Token数
     * CLI输出: 通过预处理转换 "cache_creation_input_tokens" → "cacheCreationInputTokens"
     */
    val cacheCreationInputTokens: Int? = null,

    /**
     * 缓存读取Token数
     * CLI输出: 通过预处理转换 "cache_read_input_tokens" → "cacheReadInputTokens"
     */
    val cacheReadInputTokens: Int? = null,

    /**
     * 服务等级
     * CLI输出: 通过预处理转换 "service_tier" → "serviceTier"
     */
    val serviceTier: String? = null,

    // CLI特有：缓存创建详情
    val cacheCreation: JsonObject? = null
) {
    /**
     * 计算总token数（按opcode方式）
     */
    val totalTokens: Int
        get() = (inputTokens ?: 0) + (outputTokens ?: 0) +
                (cacheCreationInputTokens ?: 0) + (cacheReadInputTokens ?: 0)

    /**
     * 转换为现有的TokenUsage格式（向后兼容）
     */
    fun toEnhancedMessageTokenUsage(): EnhancedMessage.TokenUsage {
        return EnhancedMessage.TokenUsage(
            inputTokens = inputTokens ?: 0,
            outputTokens = outputTokens ?: 0,
            cacheCreationTokens = cacheCreationInputTokens ?: 0,
            cacheReadTokens = cacheReadInputTokens ?: 0
        )
    }
}