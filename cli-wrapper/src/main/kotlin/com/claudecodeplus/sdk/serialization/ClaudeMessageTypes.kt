package com.claudecodeplus.sdk.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Claude CLI 消息类型系统
 * 
 * 基于 Claude Code SDK 和 Claudia 项目的消息格式设计
 * 支持完整的 JSONL 消息格式解析和序列化
 */

/**
 * Claude 消息类型枚举
 */
enum class ClaudeMessageType {
    USER,           // 用户消息
    ASSISTANT,      // AI 助手消息
    SYSTEM,         // 系统消息
    RESULT,         // 结果消息（会话结束统计）
    SUMMARY,        // 摘要消息（上下文压缩）
    UNKNOWN         // 未知类型
}

/**
 * 内容块类型枚举
 */
enum class ContentBlockType {
    TEXT,           // 文本块
    TOOL_USE,       // 工具使用块
    TOOL_RESULT,    // 工具结果块
    UNKNOWN         // 未知类型
}

/**
 * Claude 消息基类
 * 所有消息类型都继承自此基类，并实现 ClaudeNativeMessage 接口
 */
@Serializable
sealed class ClaudeMessage : ClaudeNativeMessage {
    abstract override val type: String
    abstract override val uuid: String?
    abstract override val sessionId: String?
    abstract override val timestamp: String?
}

/**
 * 用户消息
 * 包含用户输入或工具执行结果
 */
@Serializable
data class UserMessage(
    override val type: String = "user",
    override val uuid: String?,
    override val sessionId: String?,
    override val timestamp: String?,
    val parentUuid: String? = null,
    val isSidechain: Boolean = false,
    val userType: String? = null,
    val cwd: String? = null,
    val version: String? = null,
    val message: MessageContent?,
    val toolUseResult: JsonObject? = null
) : ClaudeMessage()

/**
 * AI 助手消息
 * 包含 AI 的响应或工具调用请求
 */
@Serializable
data class AssistantMessage(
    override val type: String = "assistant",
    override val uuid: String?,
    override val sessionId: String?,
    override val timestamp: String?,
    val parentUuid: String? = null,
    val isSidechain: Boolean = false,
    val userType: String? = null,
    val cwd: String? = null,
    val version: String? = null,
    val message: APIAssistantMessage?,
    val requestId: String? = null,
    val isApiErrorMessage: Boolean = false
) : ClaudeMessage()

/**
 * 系统消息
 * 包含会话初始化信息
 */
@Serializable
data class SystemMessage(
    override val type: String = "system",
    val subtype: String? = null,
    override val uuid: String?,
    override val sessionId: String?,
    override val timestamp: String?,
    val apiKeySource: String? = null,
    val cwd: String? = null,
    val version: String? = null,
    val tools: List<String> = emptyList(),
    val mcp_servers: List<McpServer> = emptyList(),
    val model: String? = null,
    val permissionMode: String? = null
) : ClaudeMessage()

/**
 * 结果消息
 * 包含会话结束时的统计信息
 */
@Serializable
data class ResultMessage(
    override val type: String = "result",
    val subtype: String? = null,
    override val uuid: String?,
    override val sessionId: String?,
    override val timestamp: String?,
    val duration_ms: Long? = null,
    val duration_api_ms: Long? = null,
    val num_turns: Int? = null,
    val is_error: Boolean = false,
    val result: String? = null,
    val total_cost_usd: Double? = null,
    val usage: TokenUsage? = null
) : ClaudeMessage()

/**
 * 摘要消息
 * 用于上下文压缩时保存对话摘要
 */
@Serializable
data class SummaryMessage(
    override val type: String = "summary",
    override val uuid: String?,
    override val sessionId: String?,
    override val timestamp: String?,
    val summary: String,
    val leafUuid: String? = null,
    val isCompactSummary: Boolean = false
) : ClaudeMessage()

/**
 * 消息内容（用于 User 和 Assistant 消息）
 */
@Serializable
data class MessageContent(
    val role: String,
    val content: List<ContentBlock> = emptyList()
)

/**
 * API 助手消息内容
 */
@Serializable
data class APIAssistantMessage(
    val id: String,
    val type: String = "message",
    val role: String = "assistant",
    val model: String,
    val content: List<ContentBlock> = emptyList(),
    val stop_reason: String? = null,
    val stop_sequence: String? = null,
    val usage: TokenUsage
)

/**
 * 内容块基类
 */
@Serializable
sealed class ContentBlock {
    abstract val type: String
}

/**
 * 文本内容块
 */
@Serializable
data class TextBlock(
    override val type: String = "text",
    val text: String
) : ContentBlock()

/**
 * 工具使用块（AI 请求调用工具）
 */
@Serializable
data class ToolUseBlock(
    override val type: String = "tool_use",
    val id: String,
    val name: String,
    val input: JsonObject
) : ContentBlock()

/**
 * 工具结果块（工具执行结果）
 */
@Serializable
data class ToolResultBlock(
    override val type: String = "tool_result",
    val tool_use_id: String,
    val content: JsonElement,
    val is_error: Boolean = false
) : ContentBlock()

/**
 * Token 使用统计
 */
@Serializable
data class TokenUsage(
    val input_tokens: Int,
    val output_tokens: Int,
    val cache_creation_input_tokens: Int = 0,
    val cache_read_input_tokens: Int = 0,
    val service_tier: String? = null,
    val server_tool_use: ServerToolUse? = null
)

/**
 * 服务器工具使用统计
 */
@Serializable
data class ServerToolUse(
    val web_search_requests: Int = 0
)

/**
 * MCP 服务器信息
 */
@Serializable
data class McpServer(
    val name: String,
    val status: String
)

/**
 * 工具调用请求（用于内部处理）
 */
data class ToolUseRequest(
    val id: String,
    val name: String,
    val input: JsonObject,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 工具调用响应（用于内部处理）
 */
data class ToolUseResponse(
    val toolUseId: String,
    val content: JsonElement,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 消息解析结果
 * 封装解析后的消息和相关元数据
 */
data class ParsedMessage(
    val message: ClaudeMessage,
    val toolUseRequests: List<ToolUseRequest> = emptyList(),
    val toolUseResponses: List<ToolUseResponse> = emptyList(),
    val rawJson: String
)