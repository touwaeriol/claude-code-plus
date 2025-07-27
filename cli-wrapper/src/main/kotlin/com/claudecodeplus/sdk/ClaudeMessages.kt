package com.claudecodeplus.sdk

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.descriptors.*

/**
 * Claude CLI 消息类型定义
 * 使用 kotlinx.serialization 实现多态序列化
 */

// === JSON 配置 ===
val claudeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    classDiscriminator = "type" // 使用 type 字段作为类型判别器
}

// === 顶层消息类型 ===
/**
 * Claude CLI 消息基类
 * 根据 type 字段自动反序列化为具体子类
 */
@Serializable
sealed class ClaudeMessage

/**
 * 用户消息
 */
@Serializable
@SerialName("user")
data class UserMessage(
    val uuid: String? = null,
    val parentUuid: String? = null,
    val sessionId: String? = null,
    val timestamp: String? = null,
    val isSidechain: Boolean? = false,
    val userType: String? = null,
    val cwd: String? = null,
    val version: String? = null,
    val gitBranch: String? = null,
    val message: UserMessageData? = null,
    val toolUseResult: JsonObject? = null
) : ClaudeMessage()

/**
 * 助手消息
 */
@Serializable
@SerialName("assistant")
data class AssistantMessage(
    val uuid: String? = null,
    val parentUuid: String? = null,
    val sessionId: String? = null,
    val timestamp: String? = null,
    val isSidechain: Boolean? = false,
    val userType: String? = null,
    val cwd: String? = null,
    val version: String? = null,
    val gitBranch: String? = null,
    val message: AssistantMessageData? = null,
    val requestId: String? = null,
    val isApiErrorMessage: Boolean? = false
) : ClaudeMessage()

/**
 * 系统消息
 */
@Serializable
@SerialName("system")
data class SystemMessage(
    val uuid: String? = null,
    val sessionId: String? = null,
    val timestamp: String? = null,
    val subtype: String? = null,
    val apiKeySource: String? = null,
    val cwd: String? = null,
    val version: String? = null,
    val tools: List<String>? = null,
    @SerialName("mcp_servers")
    val mcpServers: List<McpServer>? = null,
    val model: String? = null,
    val permissionMode: String? = null
) : ClaudeMessage()

/**
 * 结果消息
 */
@Serializable
@SerialName("result")
data class ResultMessage(
    val uuid: String? = null,
    val sessionId: String? = null,
    val timestamp: String? = null,
    val subtype: String? = null,
    @SerialName("duration_ms")
    val durationMs: Long? = null,
    @SerialName("duration_api_ms")
    val durationApiMs: Long? = null,
    @SerialName("num_turns")
    val numTurns: Int? = null,
    @SerialName("is_error")
    val isError: Boolean? = false,
    val result: String? = null,
    @SerialName("total_cost_usd")
    val totalCostUsd: Double? = null,
    val usage: Usage? = null
) : ClaudeMessage()

/**
 * 摘要消息
 */
@Serializable
@SerialName("summary")
data class SummaryMessage(
    val summary: String? = null,
    val leafUuid: String? = null,
    val uuid: String? = null,
    val sessionId: String? = null,
    val timestamp: String? = null,
    val isCompactSummary: Boolean? = null  // 标记是否为 /compact 命令生成的摘要
) : ClaudeMessage()

// === 消息数据类型 ===
/**
 * 用户消息数据
 */
@Serializable
data class UserMessageData(
    val role: String = "user",
    val content: ContentOrList? = null
)

/**
 * 助手消息数据
 */
@Serializable
data class AssistantMessageData(
    val id: String? = null,
    val type: String = "message",
    val role: String = "assistant",
    val model: String? = null,
    val content: List<ContentBlock>? = null,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    @SerialName("stop_sequence")
    val stopSequence: String? = null,
    val usage: Usage? = null
)

// === 内容块类型 ===
/**
 * 内容块基类
 * 根据 type 字段自动反序列化为具体子类
 */
@Serializable
sealed class ContentBlock

/**
 * 文本块
 */
@Serializable
@SerialName("text")
data class TextBlock(
    val text: String
) : ContentBlock()

/**
 * 工具使用块
 */
@Serializable
@SerialName("tool_use")
data class ToolUseBlock(
    val id: String,
    val name: String,
    val input: JsonObject
) : ContentBlock()

/**
 * 工具结果块
 */
@Serializable
@SerialName("tool_result")
data class ToolResultBlock(
    @SerialName("tool_use_id")
    val toolUseId: String,
    val content: ContentOrString? = null,
    @SerialName("is_error")
    val isError: Boolean? = false
) : ContentBlock()

// === 辅助类型 ===
/**
 * 使用统计
 */
@Serializable
data class Usage(
    @SerialName("input_tokens")
    val inputTokens: Int? = null,
    @SerialName("output_tokens")
    val outputTokens: Int? = null,
    @SerialName("cache_creation_input_tokens")
    val cacheCreationInputTokens: Int? = null,
    @SerialName("cache_read_input_tokens")
    val cacheReadInputTokens: Int? = null,
    @SerialName("service_tier")
    val serviceTier: String? = null,
    @SerialName("server_tool_use")
    val serverToolUse: ServerToolUse? = null
)

/**
 * 服务器工具使用统计
 */
@Serializable
data class ServerToolUse(
    @SerialName("web_search_requests")
    val webSearchRequests: Int? = null
)

/**
 * MCP 服务器信息
 */
@Serializable
data class McpServer(
    val name: String? = null,
    val status: String? = null
)

// === 自定义序列化器 ===
/**
 * 处理 content 既可能是字符串也可能是内容块列表的情况
 */
@Serializable(with = ContentOrListSerializer::class)
sealed class ContentOrList {
    data class StringContent(val value: String) : ContentOrList()
    data class ListContent(val value: List<ContentBlock>) : ContentOrList()
}

object ContentOrListSerializer : KSerializer<ContentOrList> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ContentOrList")
    
    override fun serialize(encoder: Encoder, value: ContentOrList) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("This serializer can be used only with Json format")
        val element = when (value) {
            is ContentOrList.StringContent -> JsonPrimitive(value.value)
            is ContentOrList.ListContent -> jsonEncoder.json.encodeToJsonElement(value.value)
        }
        jsonEncoder.encodeJsonElement(element)
    }
    
    override fun deserialize(decoder: Decoder): ContentOrList {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("This serializer can be used only with Json format")
        val element = jsonDecoder.decodeJsonElement()
        
        return when (element) {
            is JsonPrimitive -> ContentOrList.StringContent(element.content)
            is JsonArray -> {
                val blocks = jsonDecoder.json.decodeFromJsonElement<List<ContentBlock>>(element)
                ContentOrList.ListContent(blocks)
            }
            else -> throw SerializationException("Unknown content type: $element")
        }
    }
}

/**
 * 处理工具结果的 content 字段
 */
@Serializable(with = ContentOrStringSerializer::class)
sealed class ContentOrString {
    data class StringValue(val value: String) : ContentOrString()
    data class JsonValue(val value: JsonObject) : ContentOrString()
}

object ContentOrStringSerializer : KSerializer<ContentOrString> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ContentOrString")
    
    override fun serialize(encoder: Encoder, value: ContentOrString) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("This serializer can be used only with Json format")
        val element = when (value) {
            is ContentOrString.StringValue -> JsonPrimitive(value.value)
            is ContentOrString.JsonValue -> value.value
        }
        jsonEncoder.encodeJsonElement(element)
    }
    
    override fun deserialize(decoder: Decoder): ContentOrString {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("This serializer can be used only with Json format")
        val element = jsonDecoder.decodeJsonElement()
        
        return when (element) {
            is JsonPrimitive -> ContentOrString.StringValue(element.content)
            is JsonObject -> ContentOrString.JsonValue(element)
            else -> throw SerializationException("Unknown content type: $element")
        }
    }
}

// === 扩展函数 ===
/**
 * 从 JSON 字符串解析 Claude 消息
 */
fun parseClaudeMessage(jsonLine: String): ClaudeMessage? {
    return try {
        claudeJson.decodeFromString<ClaudeMessage>(jsonLine)
    } catch (e: Exception) {
        null // 忽略解析失败的行
    }
}

/**
 * 获取消息的会话 ID
 */
fun ClaudeMessage.getSessionId(): String? = when (this) {
    is UserMessage -> sessionId
    is AssistantMessage -> sessionId
    is SystemMessage -> sessionId
    is ResultMessage -> sessionId
    is SummaryMessage -> sessionId
}

/**
 * 获取消息的时间戳
 */
fun ClaudeMessage.getTimestamp(): String? = when (this) {
    is UserMessage -> timestamp
    is AssistantMessage -> timestamp
    is SystemMessage -> timestamp
    is ResultMessage -> timestamp
    is SummaryMessage -> timestamp
}