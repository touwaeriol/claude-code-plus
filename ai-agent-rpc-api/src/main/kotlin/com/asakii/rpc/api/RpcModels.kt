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
 * Connect 请求参数（统一扁平结构）
 *
 * 所有配置项都在顶层，根据 provider 能力决定哪些配置生效：
 * - Claude: permissionMode, dangerouslySkipPermissions, allowDangerouslySkipPermissions,
 *           includePartialMessages, continueConversation, thinkingEnabled
 * - Codex: baseUrl, apiKey, sandboxMode
 * - 通用: provider, model, systemPrompt, initialPrompt, sessionId, resumeSessionId, metadata
 */
@Serializable
data class RpcConnectOptions(
    // === 通用配置 ===
    val provider: RpcProvider? = null,
    val model: String? = null,
    val systemPrompt: String? = null,
    val initialPrompt: String? = null,
    val sessionId: String? = null,
    val resumeSessionId: String? = null,
    val metadata: Map<String, String> = emptyMap(),

    // === Claude 相关配置（根据 provider 能力生效）===
    val permissionMode: RpcPermissionMode? = null,
    val dangerouslySkipPermissions: Boolean? = null,
    val allowDangerouslySkipPermissions: Boolean? = null,
    val includePartialMessages: Boolean? = null,
    val continueConversation: Boolean? = null,
    val thinkingEnabled: Boolean? = null,

    // === Codex 相关配置（根据 provider 能力生效）===
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val sandboxMode: RpcSandboxMode? = null
)

/**
 * Agent 能力声明 - 描述当前 provider 支持的功能
 */
@Serializable
data class RpcCapabilities(
    val canInterrupt: Boolean,
    val canSwitchModel: Boolean,
    val canSwitchPermissionMode: Boolean,
    val supportedPermissionModes: List<RpcPermissionMode>,
    val canSkipPermissions: Boolean,
    val canSendRichContent: Boolean,
    val canThink: Boolean,
    val canResumeSession: Boolean
)

/**
 * Connect 返回结果
 */
@Serializable
data class RpcConnectResult(
    val sessionId: String,
    val provider: RpcProvider,
    val status: RpcSessionStatus = RpcSessionStatus.CONNECTED,
    val model: String? = null,
    val capabilities: RpcCapabilities? = null,
    val cwd: String? = null
)

/**
 * 切换权限模式结果
 */
@Serializable
data class RpcSetPermissionModeResult(
    val mode: RpcPermissionMode,
    val success: Boolean = true
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
    val messages: List<RpcMessage>
)

/**
 * 历史加载结果（分页查询）
 */
@Serializable
data class RpcHistoryResult(
    /** 消息列表 */
    val messages: List<RpcMessage>,
    /** 当前请求的起始位置 */
    val offset: Int,
    /** 实际返回的消息数 */
    val count: Int,
    /** 当前文件中可用的总消息数（快照） */
    val availableCount: Int
)

// ============================================================================
// RPC 消息类型 - 完全对齐 Claude Agent SDK
// ============================================================================

/**
 * RPC 消息基础接口 - 对应 Claude SDK Message
 *
 * type 字段值：user | assistant | result | stream_event | error
 */
@Serializable
sealed interface RpcMessage {
    val provider: RpcProvider?
}

/**
 * 用户消息 - 对应 Claude SDK UserMessage
 */
@Serializable
@SerialName("user")
data class RpcUserMessage(
    val message: RpcMessageContent,
    @SerialName("parent_tool_use_id")
    val parentToolUseId: String? = null,
    override val provider: RpcProvider?,
    /**
     * 是否是回放消息（用于区分压缩摘要和确认消息）
     * - isReplay = false: 压缩摘要（新生成的上下文）
     * - isReplay = true: 确认消息（如 "Compacted"）
     */
    val isReplay: Boolean? = null
) : RpcMessage

/**
 * 助手消息 - 对应 Claude SDK AssistantMessage
 */
@Serializable
@SerialName("assistant")
data class RpcAssistantMessage(
    val message: RpcMessageContent,
    val id: String? = null,
    override val provider: RpcProvider?,
    /**
     * 父工具调用 ID（用于子代理消息路由）
     * - null: 主会话消息
     * - 非 null: 子代理消息，值为触发该子代理的 Task 工具调用 ID
     */
    @SerialName("parent_tool_use_id")
    val parentToolUseId: String? = null
) : RpcMessage

/**
 * 结果消息 - 对应 Claude SDK ResultMessage
 */
@Serializable
@SerialName("result")
data class RpcResultMessage(
    val subtype: String = "success",
    @SerialName("duration_ms")
    val durationMs: Long? = null,
    @SerialName("duration_api_ms")
    val durationApiMs: Long? = null,
    @SerialName("is_error")
    val isError: Boolean = false,
    @SerialName("num_turns")
    val numTurns: Int = 0,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("total_cost_usd")
    val totalCostUsd: Double? = null,
    val usage: JsonElement? = null,
    val result: String? = null,
    override val provider: RpcProvider?
) : RpcMessage

/**
 * 流式事件 - 对应 Claude SDK StreamEvent
 * 包含嵌套的 event 字段，内部是 Anthropic API 流事件
 */
@Serializable
@SerialName("stream_event")
data class RpcStreamEvent(
    val uuid: String,
    @SerialName("session_id")
    val sessionId: String,
    val event: RpcStreamEventData,
    @SerialName("parent_tool_use_id")
    val parentToolUseId: String? = null,
    override val provider: RpcProvider?
) : RpcMessage

/**
 * 错误消息
 */
@Serializable
@SerialName("error")
data class RpcErrorMessage(
    val message: String,
    override val provider: RpcProvider?
) : RpcMessage

/**
 * 状态系统消息 - 用于通知客户端状态变化（如 compacting）
 */
@Serializable
@SerialName("status_system")
data class RpcStatusSystemMessage(
    val subtype: String = "status",
    val status: String?,  // 如 "compacting" 或 null
    @SerialName("session_id")
    val sessionId: String,
    override val provider: RpcProvider?
) : RpcMessage

/**
 * 压缩边界消息 - 标记会话压缩的边界
 */
@Serializable
@SerialName("compact_boundary")
data class RpcCompactBoundaryMessage(
    val subtype: String = "compact_boundary",
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("compact_metadata")
    val compactMetadata: RpcCompactMetadata? = null,
    override val provider: RpcProvider?
) : RpcMessage

/**
 * 压缩元数据
 */
@Serializable
data class RpcCompactMetadata(
    val trigger: String? = null,    // "manual" 或 "auto"
    @SerialName("pre_tokens")
    val preTokens: Int? = null      // 压缩前的 token 数
)

/**
 * 消息内容（assistant/user 消息的 message 字段）
 */
@Serializable
data class RpcMessageContent(
    val content: List<RpcContentBlock>,
    val model: String? = null
)

// ============================================================================
// 流式事件数据 - 对应 Anthropic API 流事件
// ============================================================================

/**
 * 流式事件数据 - event 字段的类型
 */
@Serializable
sealed interface RpcStreamEventData

@Serializable
@SerialName("message_start")
data class RpcMessageStartEvent(
    val message: RpcMessageStartInfo? = null
) : RpcStreamEventData

@Serializable
data class RpcMessageStartInfo(
    val id: String? = null,
    val model: String? = null,
    val content: List<RpcContentBlock>? = null
)

@Serializable
@SerialName("content_block_start")
data class RpcContentBlockStartEvent(
    val index: Int,
    @SerialName("content_block")
    val contentBlock: RpcContentBlock
) : RpcStreamEventData

@Serializable
@SerialName("content_block_delta")
data class RpcContentBlockDeltaEvent(
    val index: Int,
    val delta: RpcDelta
) : RpcStreamEventData

@Serializable
@SerialName("content_block_stop")
data class RpcContentBlockStopEvent(
    val index: Int
) : RpcStreamEventData

@Serializable
@SerialName("message_delta")
data class RpcMessageDeltaEvent(
    val delta: JsonElement? = null,
    val usage: RpcUsage? = null
) : RpcStreamEventData

@Serializable
@SerialName("message_stop")
class RpcMessageStopEvent : RpcStreamEventData

// ============================================================================
// Delta 类型 - 内容块增量更新
// ============================================================================

/**
 * Delta 类型 - content_block_delta 中的 delta 字段
 */
@Serializable
sealed interface RpcDelta

@Serializable
@SerialName("text_delta")
data class RpcTextDelta(
    val text: String
) : RpcDelta

@Serializable
@SerialName("thinking_delta")
data class RpcThinkingDelta(
    val thinking: String
) : RpcDelta

@Serializable
@SerialName("input_json_delta")
data class RpcInputJsonDelta(
    @SerialName("partial_json")
    val partialJson: String
) : RpcDelta

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
    val toolName: String,        // 显示名称: "Read", "Write", "mcp__xxx"（原 name 字段）
    val toolType: String,        // 类型标识: "CLAUDE_READ", "CLAUDE_WRITE", "MCP"
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
    val isError: Boolean = false,
    /** 子代理 ID（仅 Task 工具使用，用于加载子代理历史） */
    @SerialName("agent_id")
    val agentId: String? = null
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

// ============================================================================
// 历史会话相关
// ============================================================================

/**
 * 历史会话元数据（RPC 传输）
 */
@Serializable
data class RpcHistorySession(
    val sessionId: String,           // 会话 ID（用于 --resume）
    val firstUserMessage: String,    // 首条用户消息预览
    val timestamp: Long,             // 最后更新时间（毫秒时间戳）
    val messageCount: Int,           // 消息数量
    val projectPath: String          // 项目路径
)

/**
 * 获取历史会话列表的结果
 */
@Serializable
data class RpcHistorySessionsResult(
    val sessions: List<RpcHistorySession>
)

/**
 * 历史会话元数据（文件信息）
 */
@Serializable
data class RpcHistoryMetadata(
    val totalLines: Int,       // JSONL 文件总行数
    val sessionId: String,      // 会话 ID
    val projectPath: String     // 项目路径
)
