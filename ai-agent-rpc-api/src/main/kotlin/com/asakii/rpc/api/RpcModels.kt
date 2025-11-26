package com.asakii.rpc.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Provider 标识 - 与统一 SDK 的 AiAgentProvider 对应
 */
@Serializable
enum class RpcProvider {
    @SerialName("claude")
    CLAUDE,

    @SerialName("codex")
    CODEX
}

/**
 * 会话状态枚举，用于统一 RPC 返回值。
 */
@Serializable
enum class RpcSessionStatus {
    @SerialName("connected")
    CONNECTED,

    @SerialName("disconnected")
    DISCONNECTED,

    @SerialName("interrupted")
    INTERRUPTED,

    @SerialName("model_changed")
    MODEL_CHANGED
}

/**
 * 工具/内容执行状态
 */
@Serializable
enum class RpcContentStatus {
    @SerialName("in_progress")
    IN_PROGRESS,

    @SerialName("completed")
    COMPLETED,

    @SerialName("failed")
    FAILED
}

/**
 * Claude 权限模式
 */
@Serializable
enum class RpcPermissionMode {
    @SerialName("default")
    DEFAULT,

    @SerialName("bypassPermissions")
    BYPASS_PERMISSIONS,

    @SerialName("acceptEdits")
    ACCEPT_EDITS,

    @SerialName("plan")
    PLAN,

    @SerialName("dontAsk")
    DONT_ASK
}

/**
 * Codex Sandbox 模式
 */
@Serializable
enum class RpcSandboxMode {
    @SerialName("read-only")
    READ_ONLY,

    @SerialName("workspace-write")
    WORKSPACE_WRITE,

    @SerialName("danger-full-access")
    DANGER_FULL_ACCESS
}

/**
 * Connect 请求参数
 */
@Serializable
data class RpcConnectOptions(
    val provider: RpcProvider? = null,
    val model: String? = null,
    val systemPrompt: String? = null,
    val initialPrompt: String? = null,
    val sessionId: String? = null,
    val resumeSessionId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val claude: RpcClaudeOptions? = null,
    val codex: RpcCodexOptions? = null
)

/**
 * Claude 专属配置
 */
@Serializable
data class RpcClaudeOptions(
    val permissionMode: RpcPermissionMode? = null,
    val dangerouslySkipPermissions: Boolean? = null,
    val allowDangerouslySkipPermissions: Boolean? = null,
    val includePartialMessages: Boolean? = null,
    val continueConversation: Boolean? = null,
    val resume: String? = null,
    val thinkingEnabled: Boolean? = null
)

/**
 * Codex 专属配置
 */
@Serializable
data class RpcCodexOptions(
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val sandboxMode: RpcSandboxMode? = null
)

/**
 * Connect 返回结果
 */
@Serializable
data class RpcConnectResult(
    val sessionId: String,
    val provider: RpcProvider,
    val status: RpcSessionStatus = RpcSessionStatus.CONNECTED,
    val model: String? = null
)

/**
 * 通用状态返回（interrupt / disconnect）
 */
@Serializable
data class RpcStatusResult(
    val status: RpcSessionStatus
)

/**
 * 切换模型结果
 */
@Serializable
data class RpcSetModelResult(
    val status: RpcSessionStatus = RpcSessionStatus.MODEL_CHANGED,
    val model: String
)

/**
 * 历史消息
 */
@Serializable
data class RpcHistory(
    val messages: List<RpcUiEvent>
)

/**
 * 流式 UI 事件
 */
@Serializable
sealed interface RpcUiEvent {
    val provider: RpcProvider?
}

@Serializable
@SerialName("message_start")
data class RpcMessageStart(
    val messageId: String,
    val content: List<RpcContentBlock>? = null,
    override val provider: RpcProvider?
) : RpcUiEvent

@Serializable
@SerialName("text_delta")
data class RpcTextDelta(
    val text: String,
    override val provider: RpcProvider?
) : RpcUiEvent

@Serializable
@SerialName("thinking_delta")
data class RpcThinkingDelta(
    val thinking: String,
    override val provider: RpcProvider?
) : RpcUiEvent

@Serializable
@SerialName("tool_start")
data class RpcToolStart(
    val toolId: String,
    val toolName: String,
    val inputPreview: String? = null,
    override val provider: RpcProvider?
) : RpcUiEvent

@Serializable
@SerialName("tool_progress")
data class RpcToolProgress(
    val toolId: String,
    val status: RpcContentStatus,
    val outputPreview: String? = null,
    override val provider: RpcProvider?
) : RpcUiEvent

@Serializable
@SerialName("tool_complete")
data class RpcToolComplete(
    val toolId: String,
    val result: RpcContentBlock,
    override val provider: RpcProvider?
) : RpcUiEvent

@Serializable
@SerialName("message_complete")
data class RpcMessageComplete(
    val usage: RpcUsage? = null,
    override val provider: RpcProvider?
) : RpcUiEvent

@Serializable
@SerialName("error")
data class RpcError(
    val message: String,
    override val provider: RpcProvider?
) : RpcUiEvent

/**
 * 完整的助手消息（用于校验流式响应）
 * 在流式增量响应结束后发送，包含完整的内容块列表
 */
@Serializable
@SerialName("assistant")
data class RpcAssistantMessage(
    val content: List<RpcContentBlock>,
    override val provider: RpcProvider?
) : RpcUiEvent

/**
 * 统一 Usage 统计
 */
@Serializable
data class RpcUsage(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val cachedInputTokens: Int? = null,
    val provider: RpcProvider? = null,
    val raw: JsonElement? = null
)

/**
 * 工具/内容块定义
 */
@Serializable
sealed interface RpcContentBlock

@Serializable
@SerialName("text")
data class RpcTextBlock(
    val text: String
) : RpcContentBlock

@Serializable
@SerialName("thinking")
data class RpcThinkingBlock(
    val thinking: String,
    val signature: String? = null
) : RpcContentBlock

@Serializable
@SerialName("tool_use")
data class RpcToolUseBlock(
    val id: String,
    val name: String,
    val input: JsonElement? = null,
    val status: RpcContentStatus = RpcContentStatus.IN_PROGRESS
) : RpcContentBlock

@Serializable
@SerialName("tool_result")
data class RpcToolResultBlock(
    @SerialName("tool_use_id")
    val toolUseId: String,
    val content: JsonElement? = null,
    @SerialName("is_error")
    val isError: Boolean = false
) : RpcContentBlock

@Serializable
@SerialName("image")
data class RpcImageBlock(
    val source: RpcImageSource
) : RpcContentBlock

@Serializable
data class RpcImageSource(
    val type: String,
    @SerialName("media_type")
    val mediaType: String,
    val data: String? = null,
    val url: String? = null
)

@Serializable
@SerialName("command_execution")
data class RpcCommandExecutionBlock(
    val command: String,
    val output: String? = null,
    val exitCode: Int? = null,
    val status: RpcContentStatus
) : RpcContentBlock

@Serializable
@SerialName("file_change")
data class RpcFileChangeBlock(
    val status: RpcContentStatus,
    val changes: List<RpcFileChange>
) : RpcContentBlock

@Serializable
data class RpcFileChange(
    val path: String,
    val kind: String
)

@Serializable
@SerialName("mcp_tool_call")
data class RpcMcpToolCallBlock(
    val server: String? = null,
    val tool: String? = null,
    val arguments: JsonElement? = null,
    val result: JsonElement? = null,
    val status: RpcContentStatus
) : RpcContentBlock

@Serializable
@SerialName("web_search")
data class RpcWebSearchBlock(
    val query: String
) : RpcContentBlock

@Serializable
@SerialName("todo_list")
data class RpcTodoListBlock(
    val items: List<RpcTodoItem>
) : RpcContentBlock

@Serializable
data class RpcTodoItem(
    val text: String,
    val completed: Boolean
)

@Serializable
@SerialName("error")
data class RpcErrorBlock(
    val message: String
) : RpcContentBlock

/**
 * 兜底内容块，保证协议不会因未知类型而崩溃
 */
@Serializable
@SerialName("unknown")
data class RpcUnknownBlock(
    val type: String,
    val data: String
) : RpcContentBlock
