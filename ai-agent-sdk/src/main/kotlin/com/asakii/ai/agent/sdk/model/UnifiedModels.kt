package com.asakii.ai.agent.sdk.model

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.codex.agent.sdk.FileChange
import com.asakii.codex.agent.sdk.TodoEntry
import kotlinx.serialization.json.JsonElement

/**
 * 统一后的消息模型，供前端或上层逻辑直接消费。
 */
data class UnifiedMessage(
    val id: String,
    val type: MessageRole,
    val timestamp: Long,
    val content: List<UnifiedContentBlock>,
    val metadata: MessageMetadata = MessageMetadata()
)

enum class MessageRole {
    USER, ASSISTANT, SYSTEM, RESULT
}

data class MessageMetadata(
    val model: String? = null,
    val usage: UnifiedUsage? = null,
    val sessionId: String? = null,
    val threadId: String? = null,
    val provider: AiAgentProvider? = null,
    val raw: JsonElement? = null
)

sealed interface UnifiedContentBlock {
    val type: String
}

data class TextContent(
    val text: String
) : UnifiedContentBlock {
    override val type: String = "text"
}

/**
 * 图片内容块，用于用户输入
 */
data class ImageContent(
    val data: String,      // base64 encoded
    val mediaType: String  // e.g., "image/png", "image/jpeg"
) : UnifiedContentBlock {
    override val type: String = "image"
}

data class ThinkingContent(
    val thinking: String,
    val signature: String? = null
) : UnifiedContentBlock {
    override val type: String = "thinking"
}

data class ToolUseContent(
    val id: String,
    val name: String,
    val input: JsonElement?,
    val status: ContentStatus = ContentStatus.IN_PROGRESS
) : UnifiedContentBlock {
    override val type: String = "tool_use"
}

data class ToolResultContent(
    val toolUseId: String,
    val content: JsonElement?,
    val isError: Boolean = false
) : UnifiedContentBlock {
    override val type: String = "tool_result"
}

data class CommandExecutionContent(
    val command: String,
    val output: String?,
    val exitCode: Int?,
    val status: ContentStatus
) : UnifiedContentBlock {
    override val type: String = "command_execution"
}

data class FileChangeContent(
    val changes: List<FileChange>,
    val status: ContentStatus
) : UnifiedContentBlock {
    override val type: String = "file_change"
}

data class McpToolCallContent(
    val server: String?,
    val tool: String?,
    val arguments: JsonElement?,
    val result: JsonElement?,
    val status: ContentStatus
) : UnifiedContentBlock {
    override val type: String = "mcp_tool_call"
}

data class WebSearchContent(
    val query: String
) : UnifiedContentBlock {
    override val type: String = "web_search"
}

data class TodoListContent(
    val items: List<TodoEntry>
) : UnifiedContentBlock {
    override val type: String = "todo_list"
}

data class ErrorContent(
    val message: String
) : UnifiedContentBlock {
    override val type: String = "error"
}

enum class ContentStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

data class UnifiedUsage(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val cachedInputTokens: Int? = null,
    val provider: AiAgentProvider,
    val raw: JsonElement? = null
)






























