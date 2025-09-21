/*
 * ClaudeMessage.kt
 *
 * 基于Kotlinx Serialization的Claude CLI消息类型定义
 * 使用混合方案：注解 + 智能转换序列化器
 * 替换手动Gson解析，支持多字段组合的智能类型判断
 */

package com.claudecodeplus.ui.models

import com.claudecodeplus.core.logging.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalSerializationApi::class)

/**
 * Claude CLI输出的所有消息类型的密封类
 *
 * 简化版本：直接基于@JsonClassDiscriminator自动判断类型
 */
@Serializable
@JsonClassDiscriminator("type")
sealed class ClaudeMessage {
    abstract val uuid: String?
    abstract val timestamp: String?
    abstract val sessionId: String?

    /**
     * 用户消息
     * 判断条件: type="user" 或 message.role="user"
     */
    @Serializable
    @SerialName("user")
    data class UserMessage(
        override val uuid: String? = null,
        override val timestamp: String? = null,
        override val sessionId: String? = null,
        val parentUuid: String? = null,
        val message: ClaudeMessageContent? = null,
        val cwd: String? = null,
        val version: String? = null,
        val gitBranch: String? = null,
        val userType: String? = null,
        val isSidechain: Boolean? = null,
        val toolUseResult: JsonObject? = null
    ) : ClaudeMessage()

    /**
     * 助手消息
     * 判断条件: type="assistant" 或 message.role="assistant"
     */
    @Serializable
    @SerialName("assistant")
    data class AssistantMessage(
        override val uuid: String? = null,
        override val timestamp: String? = null,
        override val sessionId: String? = null,
        val parentUuid: String? = null,
        val message: AssistantMessageContent? = null,
        val requestId: String? = null,
        val cwd: String? = null,
        val version: String? = null,
        val gitBranch: String? = null,
        val userType: String? = null,
        val isSidechain: Boolean? = null
    ) : ClaudeMessage()

    /**
     * 系统初始化消息
     * 判断条件: type="system" + subtype="init"
     */
    @Serializable
    @SerialName("system_init")
    data class SystemInitMessage(
        override val uuid: String? = null,
        override val timestamp: String? = null,
        override val sessionId: String? = null,
        val subtype: String? = null,
        val apiKeySource: String? = null,
        val cwd: String? = null,
        val version: String? = null,
        val tools: List<String>? = null,
        val mcp_servers: List<JsonObject>? = null,
        val model: String? = null,
        val permissionMode: String? = null
    ) : ClaudeMessage()

    /**
     * 会话结果汇总消息
     * 判断条件: type="result"
     */
    @Serializable
    @SerialName("result")
    data class ResultMessage(
        override val uuid: String? = null,
        override val timestamp: String? = null,
        override val sessionId: String? = null,
        val subtype: String? = null,
        val duration_ms: Long? = null,
        val duration_api_ms: Long? = null,
        val num_turns: Int? = null,
        val is_error: Boolean? = null,
        val result: String? = null,
        val total_cost_usd: Double? = null,
        val usage: ClaudeTokenUsageData? = null
    ) : ClaudeMessage()

    /**
     * 会话摘要消息
     * 判断条件: type="summary"
     */
    @Serializable
    @SerialName("summary")
    data class SummaryMessage(
        override val uuid: String? = null,
        override val timestamp: String? = null,
        override val sessionId: String? = null,
        val summary: String? = null,
        val leafUuid: String? = null
    ) : ClaudeMessage()

    /**
     * 未知消息类型（解析失败时的降级处理）
     */
    @Serializable
    @SerialName("unknown")
    data class UnknownMessage(
        override val uuid: String? = null,
        override val timestamp: String? = null,
        override val sessionId: String? = null,
        val rawType: String? = null,
        val rawSubtype: String? = null,
        val rawJson: JsonObject? = null
    ) : ClaudeMessage()
}

/**
 * 智能转换序列化器
 *
 * 核心功能：
 * 1. 分析JSON中的多个字段组合
 * 2. 生成统一的 _discriminator 字段
 * 3. 让 @JsonClassDiscriminator 注解能正确工作
 */
object SmartClaudeMessageSerializer : JsonTransformingSerializer<ClaudeMessage>(
    ClaudeMessage.serializer()
) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        val type = obj["type"]?.jsonPrimitive?.content
        val subtype = obj["subtype"]?.jsonPrimitive?.content
        val messageRole = obj["message"]?.jsonObject?.get("role")?.jsonPrimitive?.content

        // 🎯 核心判断逻辑：基于多字段组合生成discriminator
        val discriminator = when {
            // 用户消息: type="user" 或者 message.role="user"
            type == "user" || messageRole == "user" -> "user"

            // 助手消息: type="assistant" 或者 message.role="assistant"
            type == "assistant" || messageRole == "assistant" -> "assistant"

            // 系统初始化: type="system" + subtype="init"
            type == "system" && subtype == "init" -> "system_init"

            // 结果消息: type="result"
            type == "result" -> "result"

            // 摘要消息: type="summary"
            type == "summary" -> "summary"

            // 未知类型：降级处理
            else -> {
    //                 logD("⚠️  [SmartClaudeMessageSerializer] 未知消息类型: type='$type', subtype='$subtype', message.role='$messageRole'")
                "unknown"
            }
        }

        // 添加 _discriminator 字段供 @JsonClassDiscriminator 使用
        val enhancedObj = obj.toMutableMap()
        enhancedObj["_discriminator"] = JsonPrimitive(discriminator)

        // 对于unknown类型，保存原始信息便于调试
        if (discriminator == "unknown") {
            enhancedObj["rawType"] = JsonPrimitive(type ?: "")
            enhancedObj["rawSubtype"] = JsonPrimitive(subtype ?: "")
            enhancedObj["rawJson"] = obj
        }

        return JsonObject(enhancedObj)
    }
}

// === 辅助数据类 ===

/**
 * Claude用户消息内容
 */
@Serializable
data class ClaudeMessageContent(
    val role: String? = null,
    val content: String? = null
)

/**
 * Claude Token使用量数据
 * 与Claude CLI输出格式完全一致
 */
@Serializable
data class ClaudeTokenUsageData(
    val input_tokens: Int? = null,
    val output_tokens: Int? = null,
    val cache_creation_input_tokens: Int? = null,
    val cache_read_input_tokens: Int? = null,
    val service_tier: String? = null
) {
    /**
     * 计算总token数（按opcode方式）
     */
    val totalTokens: Int
        get() = (input_tokens ?: 0) + (output_tokens ?: 0) +
                (cache_creation_input_tokens ?: 0) + (cache_read_input_tokens ?: 0)

    /**
     * 转换为现有的TokenUsage格式
     */
    fun toTokenUsage(): EnhancedMessage.TokenUsage {
        return EnhancedMessage.TokenUsage(
            inputTokens = input_tokens ?: 0,
            outputTokens = output_tokens ?: 0,
            cacheCreationTokens = cache_creation_input_tokens ?: 0,
            cacheReadTokens = cache_read_input_tokens ?: 0
        )
    }
}

/**
 * 助手消息内容
 */
@Serializable
data class AssistantMessageContent(
    val id: String? = null,
    val type: String? = null,
    val role: String? = null,
    val model: String? = null,
    val content: List<JsonObject>? = null,
    val stop_reason: String? = null,
    val stop_sequence: String? = null,
    val usage: ClaudeTokenUsageData? = null
)
