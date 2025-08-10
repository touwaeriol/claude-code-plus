package com.claudecodeplus.sdk.serialization

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Claude 消息序列化器
 * 
 * 提供将内部消息对象序列化为 Claude CLI 兼容 JSONL 格式的功能
 * 支持完整的消息类型和工具调用序列化
 */
object ClaudeMessageSerializer {
    
    private val logger = LoggerFactory.getLogger(ClaudeMessageSerializer::class.java)
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        prettyPrint = false
    }
    
    /**
     * 将解析的消息序列化回 JSONL 格式
     * 
     * @param parsedMessage 已解析的消息
     * @return JSONL 行字符串
     */
    fun serializeMessage(parsedMessage: ParsedMessage): String {
        return try {
            when (val message = parsedMessage.message) {
                is UserMessage -> serializeUserMessage(message)
                is AssistantMessage -> serializeAssistantMessage(message)
                is SystemMessage -> serializeSystemMessage(message)
                is ResultMessage -> serializeResultMessage(message)
                is SummaryMessage -> serializeSummaryMessage(message)
            }
        } catch (e: Exception) {
            logger.error("Failed to serialize message: ${e.message}")
            // 如果序列化失败，返回原始 JSON
            parsedMessage.rawJson
        }
    }
    
    /**
     * 批量序列化消息列表
     * 
     * @param messages 消息列表
     * @return JSONL 内容
     */
    fun serializeMessages(messages: List<ParsedMessage>): String {
        return messages.joinToString("\n") { serializeMessage(it) }
    }
    
    /**
     * 序列化用户消息
     */
    private fun serializeUserMessage(message: UserMessage): String {
        val jsonObject = buildJsonObject {
            put("type", "user")
            message.uuid?.let { put("uuid", it) }
            message.sessionId?.let { put("sessionId", it) }
            message.timestamp?.let { put("timestamp", it) }
            message.parentUuid?.let { put("parentUuid", it) }
            put("isSidechain", message.isSidechain)
            message.userType?.let { put("userType", it) }
            message.cwd?.let { put("cwd", it) }
            message.version?.let { put("version", it) }
            
            message.message?.let { msgContent ->
                put("message", serializeMessageContent(msgContent))
            }
            
            message.toolUseResult?.let { put("toolUseResult", it) }
        }
        
        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }
    
    /**
     * 序列化助手消息
     */
    private fun serializeAssistantMessage(message: AssistantMessage): String {
        val jsonObject = buildJsonObject {
            put("type", "assistant")
            message.uuid?.let { put("uuid", it) }
            message.sessionId?.let { put("sessionId", it) }
            message.timestamp?.let { put("timestamp", it) }
            message.parentUuid?.let { put("parentUuid", it) }
            put("isSidechain", message.isSidechain)
            message.userType?.let { put("userType", it) }
            message.cwd?.let { put("cwd", it) }
            message.version?.let { put("version", it) }
            
            message.message?.let { apiMsg ->
                put("message", serializeAPIAssistantMessage(apiMsg))
            }
            
            message.requestId?.let { put("requestId", it) }
            put("isApiErrorMessage", message.isApiErrorMessage)
        }
        
        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }
    
    /**
     * 序列化系统消息
     */
    private fun serializeSystemMessage(message: SystemMessage): String {
        val jsonObject = buildJsonObject {
            put("type", "system")
            message.subtype?.let { put("subtype", it) }
            message.uuid?.let { put("uuid", it) }
            message.sessionId?.let { put("sessionId", it) }
            message.timestamp?.let { put("timestamp", it) }
            message.apiKeySource?.let { put("apiKeySource", it) }
            message.cwd?.let { put("cwd", it) }
            message.version?.let { put("version", it) }
            
            if (message.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    message.tools.forEach { add(it) }
                }
            }
            
            if (message.mcp_servers.isNotEmpty()) {
                putJsonArray("mcp_servers") {
                    message.mcp_servers.forEach { server ->
                        addJsonObject {
                            put("name", server.name)
                            put("status", server.status)
                        }
                    }
                }
            }
            
            message.model?.let { put("model", it) }
            message.permissionMode?.let { put("permissionMode", it) }
        }
        
        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }
    
    /**
     * 序列化结果消息
     */
    private fun serializeResultMessage(message: ResultMessage): String {
        val jsonObject = buildJsonObject {
            put("type", "result")
            message.subtype?.let { put("subtype", it) }
            message.uuid?.let { put("uuid", it) }
            message.sessionId?.let { put("sessionId", it) }
            message.timestamp?.let { put("timestamp", it) }
            message.duration_ms?.let { put("duration_ms", it) }
            message.duration_api_ms?.let { put("duration_api_ms", it) }
            message.num_turns?.let { put("num_turns", it) }
            put("is_error", message.is_error)
            message.result?.let { put("result", it) }
            message.total_cost_usd?.let { put("total_cost_usd", it) }
            
            message.usage?.let { usage ->
                put("usage", serializeTokenUsage(usage))
            }
        }
        
        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }
    
    /**
     * 序列化摘要消息
     */
    private fun serializeSummaryMessage(message: SummaryMessage): String {
        val jsonObject = buildJsonObject {
            put("type", "summary")
            message.uuid?.let { put("uuid", it) }
            message.sessionId?.let { put("sessionId", it) }
            message.timestamp?.let { put("timestamp", it) }
            put("summary", message.summary)
            message.leafUuid?.let { put("leafUuid", it) }
            put("isCompactSummary", message.isCompactSummary)
        }
        
        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }
    
    /**
     * 序列化消息内容
     */
    private fun serializeMessageContent(content: MessageContent): JsonObject {
        return buildJsonObject {
            put("role", content.role)
            putJsonArray("content") {
                content.content.forEach { block ->
                    add(serializeContentBlock(block))
                }
            }
        }
    }
    
    /**
     * 序列化 API 助手消息内容
     */
    private fun serializeAPIAssistantMessage(message: APIAssistantMessage): JsonObject {
        return buildJsonObject {
            put("id", message.id)
            put("type", message.type)
            put("role", message.role)
            put("model", message.model)
            
            putJsonArray("content") {
                message.content.forEach { block ->
                    add(serializeContentBlock(block))
                }
            }
            
            message.stop_reason?.let { put("stop_reason", it) }
            message.stop_sequence?.let { put("stop_sequence", it) }
            put("usage", serializeTokenUsage(message.usage))
        }
    }
    
    /**
     * 序列化内容块
     */
    private fun serializeContentBlock(block: ContentBlock): JsonObject {
        return when (block) {
            is TextBlock -> buildJsonObject {
                put("type", "text")
                put("text", block.text)
            }
            is ToolUseBlock -> buildJsonObject {
                put("type", "tool_use")
                put("id", block.id)
                put("name", block.name)
                put("input", block.input)
            }
            is ToolResultBlock -> buildJsonObject {
                put("type", "tool_result")
                put("tool_use_id", block.tool_use_id)
                put("content", block.content)
                put("is_error", block.is_error)
            }
        }
    }
    
    /**
     * 序列化 Token 使用统计
     */
    private fun serializeTokenUsage(usage: TokenUsage): JsonObject {
        return buildJsonObject {
            put("input_tokens", usage.input_tokens)
            put("output_tokens", usage.output_tokens)
            
            if (usage.cache_creation_input_tokens > 0) {
                put("cache_creation_input_tokens", usage.cache_creation_input_tokens)
            }
            if (usage.cache_read_input_tokens > 0) {
                put("cache_read_input_tokens", usage.cache_read_input_tokens)
            }
            
            usage.service_tier?.let { put("service_tier", it) }
            
            usage.server_tool_use?.let { serverToolUse ->
                put("server_tool_use", buildJsonObject {
                    put("web_search_requests", serverToolUse.web_search_requests)
                })
            }
        }
    }
    
    /**
     * 创建新的用户消息（用于发送给 Claude）
     */
    fun createUserMessage(
        content: String,
        sessionId: String,
        uuid: String = generateUUID(),
        parentUuid: String? = null,
        cwd: String? = null,
        contexts: List<ContextBlock> = emptyList()
    ): String {
        val contentBlocks = mutableListOf<ContentBlock>()
        
        // 添加上下文引用（如果有）
        if (contexts.isNotEmpty()) {
            val contextText = contexts.joinToString("\n") { it.toMarkdown() }
            contentBlocks.add(TextBlock(text = contextText + "\n\n" + content))
        } else {
            contentBlocks.add(TextBlock(text = content))
        }
        
        val message = UserMessage(
            uuid = uuid,
            sessionId = sessionId,
            timestamp = generateTimestamp(),
            parentUuid = parentUuid,
            cwd = cwd ?: System.getProperty("user.dir"),
            message = MessageContent(
                role = "user",
                content = contentBlocks
            )
        )
        
        return serializeUserMessage(message)
    }
    
    /**
     * 创建工具结果消息
     */
    fun createToolResultMessage(
        toolUseId: String,
        result: String,
        isError: Boolean = false,
        sessionId: String,
        uuid: String = generateUUID(),
        parentUuid: String? = null
    ): String {
        val toolResultBlock = ToolResultBlock(
            tool_use_id = toolUseId,
            content = JsonPrimitive(result),
            is_error = isError
        )
        
        val message = UserMessage(
            uuid = uuid,
            sessionId = sessionId,
            timestamp = generateTimestamp(),
            parentUuid = parentUuid,
            message = MessageContent(
                role = "user",
                content = listOf(toolResultBlock)
            )
        )
        
        return serializeUserMessage(message)
    }
    
    private fun generateUUID(): String {
        return java.util.UUID.randomUUID().toString()
    }
    
    private fun generateTimestamp(): String {
        return java.time.Instant.now().toString()
    }
}

/**
 * 上下文块接口
 * 用于序列化不同类型的上下文信息
 */
interface ContextBlock {
    fun toMarkdown(): String
}

/**
 * 文件上下文块
 */
data class FileContextBlock(
    val filePath: String,
    val displayName: String? = null
) : ContextBlock {
    override fun toMarkdown(): String {
        val name = displayName ?: filePath.substringAfterLast('/')
        return "- 📄 `$name`"
    }
}

/**
 * Web 上下文块
 */
data class WebContextBlock(
    val url: String,
    val title: String? = null
) : ContextBlock {
    override fun toMarkdown(): String {
        return if (title != null) {
            "- 🌐 $url ($title)"
        } else {
            "- 🌐 $url"
        }
    }
}