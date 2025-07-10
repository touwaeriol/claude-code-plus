package com.claudecodeplus.ui.models

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.util.UUID

/**
 * 聊天标签页
 */
data class ChatTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val sessionId: String? = null,
    val createdAt: Instant = Instant.now(),
    val messages: List<ChatMessage> = emptyList(),
    val context: List<ContextItem> = emptyList(),
    val groupId: String? = null,
    val tags: List<ChatTag> = emptyList(),
    val status: TabStatus = TabStatus.ACTIVE,
    val lastModified: Instant = Instant.now(),
    val summary: String? = null
) {
    enum class TabStatus {
        ACTIVE,
        INTERRUPTED,
        COMPLETED,
        ARCHIVED
    }
}

/**
 * 聊天消息
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Instant = Instant.now(),
    val attachments: List<MessageAttachment> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 消息附件
 */
sealed class MessageAttachment {
    data class File(val path: String, val mimeType: String? = null) : MessageAttachment()
    data class Image(val path: String, val alt: String? = null) : MessageAttachment()
    data class CodeBlock(val code: String, val language: String) : MessageAttachment()
}

/**
 * 增强的上下文项
 */
sealed class ContextItem {
    abstract val id: String
    abstract val addedAt: Instant
    
    data class File(
        override val id: String = UUID.randomUUID().toString(),
        val path: String,
        override val addedAt: Instant = Instant.now()
    ) : ContextItem()
    
    data class Folder(
        override val id: String = UUID.randomUUID().toString(),
        val path: String,
        val includePattern: String? = null,
        val excludePattern: String? = null,
        override val addedAt: Instant = Instant.now()
    ) : ContextItem()
    
    data class CodeBlock(
        override val id: String = UUID.randomUUID().toString(),
        val content: String,
        val language: String,
        val description: String? = null,
        override val addedAt: Instant = Instant.now()
    ) : ContextItem()
}

/**
 * 聊天分组
 */
data class ChatGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val color: Color,
    val icon: String? = null,
    val parentId: String? = null, // 支持嵌套分组
    val order: Int = 0,
    val isCollapsed: Boolean = false
)

/**
 * 聊天标签
 */
data class ChatTag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Color,
    val description: String? = null
)

/**
 * 上下文模板
 */
data class ContextTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val icon: String? = null,
    val items: List<ContextTemplateItem>,
    val tags: List<String> = emptyList(),
    val category: String,
    val isBuiltIn: Boolean = false,
    val usageCount: Int = 0,
    val lastUsed: Instant? = null
)

/**
 * 上下文模板项
 */
data class ContextTemplateItem(
    val type: TemplateItemType,
    val value: String,
    val description: String? = null,
    val optional: Boolean = false,
    val parameters: Map<String, String> = emptyMap()
) {
    enum class TemplateItemType {
        FILE,
        FOLDER,
        PATTERN,
        GLOB
    }
}

/**
 * 提示词模板
 */
data class PromptTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val template: String,
    val variables: List<TemplateVariable>,
    val category: String,
    val tags: List<String> = emptyList(),
    val icon: String? = null,
    val isBuiltIn: Boolean = false,
    val isFavorite: Boolean = false,
    val usageCount: Int = 0,
    val lastUsed: Instant? = null,
    val author: String? = null,
    val version: String = "1.0",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

/**
 * 模板变量
 */
data class TemplateVariable(
    val name: String,
    val description: String,
    val type: VariableType,
    val defaultValue: String? = null,
    val required: Boolean = true,
    val options: List<String>? = null, // 用于 SELECT 类型
    val validation: String? = null, // 正则表达式
    val placeholder: String? = null
) {
    enum class VariableType {
        TEXT,
        MULTILINE_TEXT,
        NUMBER,
        BOOLEAN,
        FILE,
        FOLDER,
        SELECT,
        MULTI_SELECT,
        DATE,
        COLOR
    }
}

/**
 * 搜索结果
 */
data class ChatSearchResult(
    val chatId: String,
    val chatTitle: String,
    val matchedMessages: List<MessageMatch>,
    val relevanceScore: Float,
    val lastModified: Instant
)

/**
 * 消息匹配
 */
data class MessageMatch(
    val messageId: String,
    val snippet: String,
    val highlights: List<IntRange>, // 高亮范围
    val matchType: MatchType
) {
    enum class MatchType {
        TITLE,
        CONTENT,
        TAG,
        CONTEXT,
        METADATA
    }
}

/**
 * 上下文推荐
 */
data class ContextSuggestion(
    val item: ContextItem,
    val reason: String,
    val confidence: Float,
    val source: SuggestionSource
) {
    enum class SuggestionSource {
        FILE_ASSOCIATION,
        CONVERSATION_ANALYSIS,
        USAGE_PATTERN,
        PROJECT_STRUCTURE,
        USER_HISTORY
    }
}

/**
 * 批量问题队列项
 */
data class QueuedQuestion(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val context: List<ContextItem> = emptyList(),
    val priority: Int = 0,
    val status: QuestionStatus = QuestionStatus.PENDING,
    val result: String? = null,
    val error: String? = null,
    val createdAt: Instant = Instant.now(),
    val processedAt: Instant? = null
) {
    enum class QuestionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}

/**
 * 会话中断状态
 */
data class InterruptedSession(
    val sessionId: String,
    val chatTabId: String,
    val lastMessageId: String,
    val pendingInput: String?,
    val context: List<ContextItem>,
    val timestamp: Instant,
    val reason: InterruptReason
) {
    enum class InterruptReason {
        USER_CANCELLED,
        ERROR,
        TIMEOUT,
        SYSTEM_SHUTDOWN
    }
}

/**
 * 导出配置
 */
data class ExportConfig(
    val format: ExportFormat,
    val includeContext: Boolean = true,
    val includeTimestamps: Boolean = true,
    val includeMetadata: Boolean = false,
    val customCss: String? = null,
    val pageSize: String? = "A4", // for PDF
    val theme: String = "light"
)

/**
 * 导出格式
 */
enum class ExportFormat(val displayName: String) {
    MARKDOWN("Markdown"),
    PDF("PDF"),
    HTML("HTML"),
    JSON("JSON")
}