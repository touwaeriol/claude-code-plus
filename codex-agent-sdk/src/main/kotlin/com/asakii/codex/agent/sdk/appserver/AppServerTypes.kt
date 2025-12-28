package com.asakii.codex.agent.sdk.appserver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC 2.0 协议类型定义 (用于 codex app-server)
 *
 * 参考: external/openai-codex/codex-rs/app-server/README.md
 */

// ============== JSON-RPC 基础类型 ==============

@Serializable
data class JsonRpcRequest(
    val method: String,
    val id: String,
    val params: JsonElement? = null
)

@Serializable
data class JsonRpcResponse(
    val id: String,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcNotification(
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

// ============== 初始化 ==============

@Serializable
data class ClientInfo(
    val name: String,
    val title: String? = null,
    val version: String
)

@Serializable
data class InitializeParams(
    val clientInfo: ClientInfo
)

@Serializable
data class InitializeResult(
    val userAgent: String? = null
)

// ============== Thread 相关 ==============

@Serializable
data class ThreadStartParams(
    val model: String? = null,
    val cwd: String? = null,
    val approvalPolicy: String? = null,
    val sandbox: String? = null
)

@Serializable
data class ThreadStartResult(
    val thread: ThreadInfo
)

@Serializable
data class ThreadInfo(
    val id: String,
    val preview: String? = null,
    val modelProvider: String? = null,
    val createdAt: Long? = null
)

@Serializable
data class ThreadResumeParams(
    val threadId: String
)

@Serializable
data class ThreadListParams(
    val cursor: String? = null,
    val limit: Int? = null,
    val modelProviders: List<String>? = null
)

@Serializable
data class ThreadListResult(
    val data: List<ThreadInfo>,
    val nextCursor: String? = null
)

// ============== Turn 相关 ==============

@Serializable
sealed class UserInput {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : UserInput()

    @Serializable
    @SerialName("image")
    data class Image(val url: String) : UserInput()

    @Serializable
    @SerialName("localImage")
    data class LocalImage(val path: String) : UserInput()
}

@Serializable
data class SandboxPolicy(
    val mode: String? = null,
    val writableRoots: List<String>? = null,
    val networkAccess: Boolean? = null
)

@Serializable
data class TurnStartParams(
    val threadId: String,
    val input: List<UserInput>,
    val cwd: String? = null,
    val approvalPolicy: String? = null,
    val sandboxPolicy: SandboxPolicy? = null,
    val model: String? = null,
    val effort: String? = null,
    val summary: String? = null
)

@Serializable
data class TurnStartResult(
    val turn: TurnInfo
)

@Serializable
data class TurnInfo(
    val id: String,
    val status: String,  // inProgress, completed, interrupted, failed
    val items: List<ThreadItem> = emptyList(),
    val error: TurnError? = null
)

@Serializable
data class TurnError(
    val message: String,
    val codexErrorInfo: String? = null
)

@Serializable
data class TurnInterruptParams(
    val threadId: String,
    val turnId: String
)

// ============== Item 类型 ==============

@Serializable
data class ThreadItem(
    val type: String,
    val id: String,
    // userMessage
    val content: List<UserInput>? = null,
    // agentMessage
    val text: String? = null,
    // reasoning
    val summary: String? = null,
    // commandExecution
    val command: String? = null,
    val cwd: String? = null,
    val status: String? = null,
    val aggregatedOutput: String? = null,
    val exitCode: Int? = null,
    val durationMs: Long? = null,
    // fileChange
    val changes: List<FileChange>? = null,
    // mcpToolCall
    val server: String? = null,
    val tool: String? = null,
    val arguments: JsonElement? = null,
    val result: JsonElement? = null,
    val error: McpError? = null,
    // webSearch
    val query: String? = null,
    // imageView
    val path: String? = null,
    // review
    val review: String? = null
)

@Serializable
data class FileChange(
    val path: String,
    val kind: String,  // create, modify, delete
    val diff: String? = null
)

@Serializable
data class McpError(
    val message: String
)

// ============== 通知事件 ==============

@Serializable
data class TurnStartedNotification(
    val turn: TurnInfo
)

@Serializable
data class TurnCompletedNotification(
    val turn: TurnInfo
)

@Serializable
data class ItemStartedNotification(
    val item: ThreadItem
)

@Serializable
data class ItemCompletedNotification(
    val item: ThreadItem
)

@Serializable
data class AgentMessageDeltaNotification(
    val itemId: String,
    val delta: String
)

@Serializable
data class ReasoningSummaryDeltaNotification(
    val itemId: String,
    val summaryIndex: Int,
    val delta: String
)

@Serializable
data class CommandOutputDeltaNotification(
    val itemId: String,
    val delta: String
)

// ============== 审批请求 (Server → Client) ==============

@Serializable
data class CommandApprovalRequest(
    val itemId: String,
    val threadId: String,
    val turnId: String,
    val command: String,
    val cwd: String? = null,
    val reason: String? = null,
    val risk: String? = null
)

@Serializable
data class FileChangeApprovalRequest(
    val itemId: String,
    val threadId: String,
    val turnId: String,
    val changes: List<FileChange>,
    val reason: String? = null
)

@Serializable
data class ApprovalResponse(
    val decision: String,  // "accept" or "decline"
    val acceptSettings: AcceptSettings? = null
)

@Serializable
data class AcceptSettings(
    val forSession: Boolean = false
)

// ============== 账户相关 ==============

@Serializable
data class AccountInfo(
    val type: String,  // "apiKey" or "chatgpt"
    val email: String? = null,
    val planType: String? = null
)

@Serializable
data class AccountReadResult(
    val account: AccountInfo? = null,
    val requiresOpenaiAuth: Boolean = false
)

@Serializable
data class RateLimits(
    val primary: RateLimitInfo? = null,
    val secondary: RateLimitInfo? = null
)

@Serializable
data class RateLimitInfo(
    val usedPercent: Int,
    val windowDurationMins: Int,
    val resetsAt: Long
)

// ============== 使用量统计 ==============

@Serializable
data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedInputTokens: Int = 0
)

@Serializable
data class TokenUsageUpdatedNotification(
    val threadId: String,
    val usage: TokenUsage
)
