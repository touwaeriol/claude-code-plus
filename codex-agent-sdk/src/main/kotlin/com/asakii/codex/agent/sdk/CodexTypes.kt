package com.asakii.codex.agent.sdk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ThreadEvent(
    val type: String,
    @SerialName("thread_id") val threadId: String? = null,
    val item: ThreadItem? = null,
    val usage: Usage? = null,
    val error: ThreadError? = null,
)

@Serializable
data class ThreadItem(
    val id: String? = null,
    val type: String,
    val text: String? = null,
    val command: String? = null,
    @SerialName("aggregated_output") val aggregatedOutput: String? = null,
    @SerialName("exit_code") val exitCode: Int? = null,
    val status: String? = null,
    val changes: List<FileChange>? = null,
    val result: McpToolCallResult? = null,
    val error: McpToolCallError? = null,
    val query: String? = null,
    val items: List<TodoEntry>? = null,
    val arguments: JsonElement? = null,
    val content: JsonElement? = null,
)

@Serializable
data class FileChange(
    val path: String,
    val kind: String,
)

@Serializable
data class McpToolCallResult(
    val content: JsonElement? = null,
    @SerialName("structured_content") val structuredContent: JsonElement? = null,
)

@Serializable
data class McpToolCallError(
    val message: String,
)

@Serializable
data class TodoEntry(
    val text: String,
    val completed: Boolean,
)

@Serializable
data class Usage(
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("cached_input_tokens") val cachedInputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int,
)

@Serializable
data class ThreadError(
    val message: String,
)

/**
 * 回合执行结果。
 *
 * Java 使用示例：
 * ```java
 * TurnResult result = session.run("Hello!");
 * System.out.println("Response: " + result.finalResponse);
 * System.out.println("Items count: " + result.items.size());
 * ```
 */
data class TurnResult(
    /** 回合中产生的所有事件项 */
    @JvmField val items: List<ThreadItem>,
    /** 最终的文本响应 */
    @JvmField val finalResponse: String,
    /** Token 使用统计 */
    @JvmField val usage: Usage?,
)

/**
 * 流式回合对象，包含事件流。
 *
 * Java 使用示例：
 * ```java
 * StreamedTurn turn = session.runStreamed("Hello!");
 * FlowKt.collect(turn.events, event -> {
 *     System.out.println("Event type: " + event.type);
 *     return Unit.INSTANCE;
 * });
 * ```
 */
data class StreamedTurn(
    /** 事件流 */
    @JvmField val events: kotlinx.coroutines.flow.Flow<ThreadEvent>,
)

class CodexExecutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class CodexTurnFailedException(val threadError: ThreadError) : RuntimeException(threadError.message)


