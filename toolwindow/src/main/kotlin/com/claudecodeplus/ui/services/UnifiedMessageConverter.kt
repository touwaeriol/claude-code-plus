package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageRole
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.ui.models.ContextReference
import com.claudecodeplus.ui.models.SymbolType
import com.claudecodeplus.ui.models.GitRefType
import com.claudecodeplus.sdk.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import org.slf4j.LoggerFactory

/**
 * 统一消息转换器
 * 
 * 提供统一的消息转换API，支持：
 * 1. ClaudeMessage (实时CLI输出) → EnhancedMessage 
 * 2. SDKMessage (历史会话) → EnhancedMessage
 * 3. 确保两个路径转换结果一致
 */
object UnifiedMessageConverter {
    
    private val logger = LoggerFactory.getLogger(UnifiedMessageConverter::class.java)
    
    /**
     * 从 ClaudeMessage 转换为 EnhancedMessage
     * 处理实时CLI输出的完整类型化消息
     */
    fun fromClaudeMessage(claudeMessage: ClaudeMessage): EnhancedMessage? {
        return try {
            when (claudeMessage) {
                is UserMessage -> convertUserMessage(claudeMessage)
                is AssistantMessage -> convertAssistantMessage(claudeMessage)
                is SystemMessage -> convertSystemMessage(claudeMessage)
                is ResultMessage -> convertResultMessage(claudeMessage)
                is SummaryMessage -> convertSummaryMessage(claudeMessage)
            }
        } catch (e: Exception) {
            logger.error("Failed to convert ClaudeMessage: ${e.message}", e)
            null
        }
    }
    
    /**
     * 从 SDKMessage 转换为 EnhancedMessage  
     * 处理历史会话的简化格式
     */
    fun fromSDKMessage(sdkMessage: SDKMessage): EnhancedMessage? {
        return try {
            logger.debug("Converting SDKMessage: type=${sdkMessage.type}, messageId=${sdkMessage.messageId}")
            
            // 优先使用 content 字段，然后是 data.text
            val rawContent = sdkMessage.content ?: sdkMessage.data.text ?: ""
            
            // 解析原始JSON内容
            val contentJson = parseRawContent(rawContent)
            
            // 检查是否是中断消息
            val isInterruptMessage = rawContent.contains("用户已中断请求") || 
                                    rawContent.contains("Request interrupted by user")
            
            // 提取消息角色
            val role = extractMessageRole(contentJson, sdkMessage.type)
            
            // 提取消息内容
            val messageContent = if (isInterruptMessage) {
                "⏹️ 请求已被用户中断"
            } else {
                extractSDKMessageContent(contentJson, rawContent)
            }
            
            // 提取工具调用
            val toolCalls = extractSDKToolCalls(contentJson)
            
            // 提取Token使用信息
            val tokenUsage = extractSDKTokenUsage(contentJson)
            
            // 解析时间戳
            val timestampMillis = parseTimestamp(sdkMessage.timestamp)
            
            val enhancedMessage = EnhancedMessage(
                id = sdkMessage.messageId ?: generateMessageId(),
                role = if (role == "user") MessageRole.USER else MessageRole.ASSISTANT,
                content = messageContent,
                timestamp = timestampMillis,
                toolCalls = toolCalls,
                tokenUsage = tokenUsage,
                isStreaming = false
            )
            
            // 增强工具调用的类型安全性
            enhanceToolCalls(enhancedMessage)
            
        } catch (e: Exception) {
            logger.error("Failed to convert SDKMessage: ${e.message}", e)
            null
        }
    }
    
    /**
     * 转换用户消息
     */
    private fun convertUserMessage(message: UserMessage): EnhancedMessage {
        val rawContent = extractUserContent(message.message?.content)
        val toolResults = extractToolResults(message.toolUseResult)
        
        // 解析 markdown front matter 中的 contexts 信息
        val (cleanContent, contexts) = parseContextsFromContent(rawContent)
        
        return EnhancedMessage(
            id = message.uuid ?: generateMessageId(),
            role = MessageRole.USER,
            content = cleanContent,
            timestamp = parseTimestamp(message.timestamp),
            contexts = contexts,
            isStreaming = false
        )
    }
    
    /**
     * 转换助手消息
     */
    private fun convertAssistantMessage(message: AssistantMessage): EnhancedMessage {
        val apiMessage = message.message
        val content = extractAssistantContent(apiMessage?.content)
        val toolCalls = extractToolCallsFromContent(apiMessage?.content)
        
        return EnhancedMessage(
            id = message.uuid ?: generateMessageId(),
            role = MessageRole.ASSISTANT,
            content = content,
            timestamp = parseTimestamp(message.timestamp),
            toolCalls = toolCalls,
            tokenUsage = apiMessage?.usage?.let { convertTokenUsage(it) },
            model = parseModel(apiMessage?.model),
            isStreaming = false
        )
    }
    
    /**
     * 转换系统消息
     */
    private fun convertSystemMessage(message: SystemMessage): EnhancedMessage {
        return EnhancedMessage(
            id = message.uuid ?: generateMessageId(),
            role = MessageRole.SYSTEM,
            content = "System: ${message.subtype ?: "初始化"} - Model: ${message.model ?: "Unknown"}",
            timestamp = parseTimestamp(message.timestamp),
            isStreaming = false
        )
    }
    
    /**
     * 转换结果消息
     */
    private fun convertResultMessage(message: ResultMessage): EnhancedMessage {
        val content = buildString {
            append("对话完成")
            message.durationMs?.let { append(" (${it}ms)") }
            message.totalCostUsd?.let { append(", 费用: $${it}") }
            message.numTurns?.let { append(", ${it}轮对话") }
        }
        
        return EnhancedMessage(
            id = message.uuid ?: generateMessageId(),
            role = MessageRole.SYSTEM,
            content = content,
            timestamp = parseTimestamp(message.timestamp),
            tokenUsage = message.usage?.let { convertTokenUsage(it) },
            isStreaming = false
        )
    }
    
    /**
     * 转换摘要消息
     */
    private fun convertSummaryMessage(message: SummaryMessage): EnhancedMessage {
        return EnhancedMessage(
            id = message.uuid ?: generateMessageId(),
            role = MessageRole.SYSTEM,
            content = message.summary ?: "对话摘要",
            timestamp = parseTimestamp(message.timestamp),
            isCompactSummary = message.isCompactSummary ?: false,
            isStreaming = false
        )
    }
    
    /**
     * 从用户消息内容中提取文本
     */
    private fun extractUserContent(content: ContentOrList?): String {
        return when (content) {
            is ContentOrList.StringContent -> content.value
            is ContentOrList.ListContent -> {
                content.value.filterIsInstance<TextBlock>()
                    .joinToString("") { it.text }
            }
            null -> ""
        }
    }
    
    /**
     * 从助手消息内容中提取文本
     */
    private fun extractAssistantContent(content: List<ContentBlock>?): String {
        return content?.filterIsInstance<TextBlock>()
            ?.joinToString("") { it.text }
            ?: ""
    }
    
    /**
     * 从助手消息内容中提取工具调用
     */
    private fun extractToolCallsFromContent(content: List<ContentBlock>?): List<ToolCall> {
        return content?.filterIsInstance<ToolUseBlock>()
            ?.map { toolUseBlock ->
                // 使用强类型工具解析
                val tool = toolUseBlock.tool
                val parameters = convertJsonObjectToMap(toolUseBlock.input)
                
                ToolCall(
                    id = toolUseBlock.id,
                    name = toolUseBlock.name,
                    parameters = parameters,
                    status = ToolCallStatus.RUNNING,
                    result = null,
                    startTime = System.currentTimeMillis(),
                    endTime = null,
                    tool = tool // 新增：强类型工具对象
                )
            } ?: emptyList()
    }
    
    /**
     * 从工具结果JSON中提取工具结果
     */
    private fun extractToolResults(toolUseResult: JsonObject?): Map<String, ToolResult> {
        return try {
            val toolResults = mutableMapOf<String, ToolResult>()
            
            val messageObj = toolUseResult?.get("message")?.jsonObject
            val contentArray = messageObj?.get("content")?.jsonArray
            
            contentArray?.forEach { contentElement ->
                val contentObj = contentElement.jsonObject
                val type = contentObj["type"]?.jsonPrimitive?.content
                
                if (type == "tool_result") {
                    val toolUseId = contentObj["tool_use_id"]?.jsonPrimitive?.content ?: ""
                    val resultContent = contentObj["content"]?.jsonPrimitive?.content ?: ""
                    val isError = contentObj["is_error"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    
                    val result = if (isError) {
                        ToolResult.Failure(resultContent)
                    } else {
                        ToolResult.Success(resultContent)
                    }
                    
                    toolResults[toolUseId] = result
                }
            }
            
            toolResults
        } catch (e: Exception) {
            logger.error("Failed to extract tool results: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * 转换Token使用信息
     */
    private fun convertTokenUsage(usage: Usage): EnhancedMessage.TokenUsage {
        return EnhancedMessage.TokenUsage(
            inputTokens = usage.inputTokens ?: 0,
            outputTokens = usage.outputTokens ?: 0,
            cacheCreationTokens = usage.cacheCreationInputTokens ?: 0,
            cacheReadTokens = usage.cacheReadInputTokens ?: 0
        )
    }
    
    /**
     * 解析模型信息
     */
    private fun parseModel(modelString: String?): com.claudecodeplus.ui.models.AiModel? {
        return when {
            modelString?.contains("opus", ignoreCase = true) == true -> com.claudecodeplus.ui.models.AiModel.OPUS
            modelString?.contains("sonnet", ignoreCase = true) == true -> com.claudecodeplus.ui.models.AiModel.SONNET
            modelString?.contains("haiku", ignoreCase = true) == true -> com.claudecodeplus.ui.models.AiModel.SONNET // 暂时映射到 Sonnet
            else -> null
        }
    }
    
    /**
     * 增强现有EnhancedMessage的工具调用类型安全性
     */
    private fun enhanceToolCalls(message: EnhancedMessage): EnhancedMessage {
        val enhancedToolCalls = message.toolCalls.map { toolCall ->
            // 重新解析工具参数，生成强类型工具对象
            val jsonObject = convertMapToJsonObject(toolCall.parameters)
            val tool = ToolParser.parse(toolCall.name, jsonObject)
            
            toolCall.copy(tool = tool)
        }
        
        return message.copy(toolCalls = enhancedToolCalls)
    }
    
    // === 辅助工具方法 ===
    
    private fun parseTimestamp(timestampString: String?): Long {
        return try {
            timestampString?.let {
                java.time.Instant.parse(it).toEpochMilli()
            } ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    
    private fun generateMessageId(): String {
        return java.util.UUID.randomUUID().toString()
    }
    
    private fun convertJsonObjectToMap(jsonObject: JsonObject): Map<String, Any> {
        return jsonObject.entries.associate { (key, value) ->
            key to when {
                value is kotlinx.serialization.json.JsonPrimitive && value.isString -> value.content
                value is kotlinx.serialization.json.JsonPrimitive -> value.content
                else -> value.toString()
            }
        }
    }
    
    private fun convertMapToJsonObject(map: Map<String, Any>): JsonObject {
        val jsonMap = map.mapValues { (_, value) ->
            kotlinx.serialization.json.JsonPrimitive(value.toString())
        }
        return JsonObject(jsonMap)
    }
    
    // === SDKMessage 专用辅助方法 ===
    
    private fun parseRawContent(rawContent: String): JsonObject? {
        return try {
            if (rawContent.startsWith("{")) {
                val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(rawContent).jsonObject
                parsed
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractMessageRole(contentJson: JsonObject?, messageType: com.claudecodeplus.sdk.MessageType): String {
        return contentJson?.get("message")?.jsonObject?.get("role")?.jsonPrimitive?.content 
            ?: when (messageType) {
                com.claudecodeplus.sdk.MessageType.TEXT -> "assistant"
                com.claudecodeplus.sdk.MessageType.TOOL_USE -> "assistant"
                com.claudecodeplus.sdk.MessageType.TOOL_RESULT -> "user"
                else -> "system"
            }
    }
    
    private fun extractSDKMessageContent(contentJson: JsonObject?, rawContent: String): String {
        return try {
            when {
                // 检查是否是 message.content 数组格式
                contentJson?.get("message")?.jsonObject?.get("content")?.jsonArray != null -> {
                    val contentArray = contentJson.get("message")!!.jsonObject.get("content")!!.jsonArray
                    extractTextFromSDKContentArray(contentArray)
                }
                
                // 直接的文本消息
                contentJson?.get("message")?.jsonObject?.get("content")?.jsonPrimitive != null -> {
                    contentJson.get("message")!!.jsonObject.get("content")!!.jsonPrimitive.content
                }
                
                // 简单的文本内容
                contentJson?.get("content")?.jsonPrimitive != null -> {
                    contentJson.get("content")!!.jsonPrimitive.content
                }
                
                // Claude CLI 结果消息格式
                contentJson?.get("type")?.jsonPrimitive?.content == "result" -> {
                    contentJson.get("result")?.jsonPrimitive?.content ?: ""
                }
                
                // 如果无法从 JSON 中提取，使用原始内容
                rawContent.isNotBlank() && !rawContent.startsWith("{") -> {
                    rawContent
                }
                
                else -> ""
            }
        } catch (e: Exception) {
            logger.error("Failed to extract SDK message content: ${e.message}", e)
            ""
        }
    }
    
    private fun extractTextFromSDKContentArray(contentArray: JsonArray): String {
        return try {
            contentArray.mapNotNull { contentElement ->
                val contentObj = contentElement.jsonObject
                val type = contentObj["type"]?.jsonPrimitive?.content
                
                if (type == "text") {
                    contentObj["text"]?.jsonPrimitive?.content
                } else {
                    null
                }
            }.joinToString("")
        } catch (e: Exception) {
            logger.error("Failed to extract text from SDK content array: ${e.message}", e)
            ""
        }
    }
    
    private fun extractSDKToolCalls(contentJson: JsonObject?): List<ToolCall> {
        return try {
            val toolCalls = mutableListOf<ToolCall>()
            
            val messageObj = contentJson?.get("message")?.jsonObject
            val contentArray = messageObj?.get("content")?.jsonArray
            
            contentArray?.forEach { contentElement ->
                val contentObj = contentElement.jsonObject
                val type = contentObj["type"]?.jsonPrimitive?.content
                
                if (type == "tool_use") {
                    val toolId = contentObj["id"]?.jsonPrimitive?.content ?: ""
                    val toolName = contentObj["name"]?.jsonPrimitive?.content ?: ""
                    val inputJson = contentObj["input"]?.jsonObject
                    
                    // 转换参数为Map
                    val parameters = inputJson?.mapValues { (_, value) ->
                        when {
                            value is kotlinx.serialization.json.JsonPrimitive -> value.content
                            else -> value.toString()
                        }
                    } ?: emptyMap()
                    
                    // 解析强类型工具对象
                    val tool = ToolParser.parse(toolName, inputJson ?: JsonObject(emptyMap()))
                    
                    val toolCall = ToolCall(
                        id = toolId,
                        name = toolName,
                        parameters = parameters,
                        status = ToolCallStatus.RUNNING,
                        result = null,
                        startTime = System.currentTimeMillis(),
                        endTime = null,
                        tool = tool
                    )
                    
                    toolCalls.add(toolCall)
                }
            }
            
            toolCalls
        } catch (e: Exception) {
            logger.error("Failed to extract SDK tool calls: ${e.message}", e)
            emptyList()
        }
    }
    
    private fun extractSDKTokenUsage(contentJson: JsonObject?): EnhancedMessage.TokenUsage? {
        return try {
            val usageObj = contentJson?.get("message")?.jsonObject?.get("usage")?.jsonObject
            
            if (usageObj != null) {
                val inputTokens = usageObj["input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val outputTokens = usageObj["output_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val cacheCreationTokens = usageObj["cache_creation_input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val cacheReadTokens = usageObj["cache_read_input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                
                if (inputTokens > 0 || outputTokens > 0 || cacheCreationTokens > 0 || cacheReadTokens > 0) {
                    EnhancedMessage.TokenUsage(
                        inputTokens = inputTokens,
                        outputTokens = outputTokens,
                        cacheCreationTokens = cacheCreationTokens,
                        cacheReadTokens = cacheReadTokens
                    )
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to extract SDK token usage: ${e.message}", e)
            null
        }
    }
    
    /**
     * 从消息内容中解析 contexts 信息
     * 支持 markdown front matter 格式：
     * ---
     * contexts:
     *   - type: file
     *     path: /path/to/file.txt
     *   - type: web
     *     url: https://example.com
     * ---
     * 实际消息内容...
     */
    private fun parseContextsFromContent(content: String): Pair<String, List<ContextReference>> {
        if (!content.startsWith("---\n")) {
            return content to emptyList()
        }
        
        try {
            val endIndex = content.indexOf("\n---\n", 4)
            if (endIndex == -1) {
                return content to emptyList()
            }
            
            val frontMatter = content.substring(4, endIndex)
            val actualContent = content.substring(endIndex + 5).trimStart()
            
            val contexts = parseFrontMatterContexts(frontMatter)
            return actualContent to contexts
            
        } catch (e: Exception) {
            logger.error("Failed to parse contexts from content: ${e.message}", e)
            return content to emptyList()
        }
    }
    
    /**
     * 解析 YAML front matter 中的 contexts
     */
    private fun parseFrontMatterContexts(frontMatter: String): List<ContextReference> {
        val contexts = mutableListOf<ContextReference>()
        
        try {
            val lines = frontMatter.lines()
            var inContexts = false
            
            for (line in lines) {
                val trimmedLine = line.trim()
                
                if (trimmedLine == "contexts:") {
                    inContexts = true
                    continue
                }
                
                if (inContexts && trimmedLine.startsWith("- ")) {
                    val contextLine = trimmedLine.substring(2).trim()
                    
                    // 解析不同类型的 context 引用
                    when {
                        contextLine.startsWith("file:") -> {
                            val rawPath = contextLine.substring(5).trim()
                            // 处理 file:@path 格式，移除 @ 符号
                            val path = if (rawPath.startsWith("@")) rawPath.substring(1) else rawPath
                            contexts.add(ContextReference.FileReference(path = path))
                        }
                        contextLine.startsWith("web:") -> {
                            val url = contextLine.substring(4).trim()
                            contexts.add(ContextReference.WebReference(url = url))
                        }
                        contextLine.startsWith("folder:") -> {
                            val path = contextLine.substring(7).trim()
                            contexts.add(ContextReference.FolderReference(path = path))
                        }
                        contextLine.startsWith("symbol:") -> {
                            val name = contextLine.substring(7).trim()
                            contexts.add(ContextReference.SymbolReference(
                                name = name,
                                type = SymbolType.OBJECT,
                                file = "",
                                line = 0
                            ))
                        }
                        contextLine.startsWith("image:") -> {
                            val filename = contextLine.substring(6).trim()
                            contexts.add(ContextReference.ImageReference(filename = filename, path = filename))
                        }
                        contextLine.startsWith("git:") -> {
                            val content = contextLine.substring(4).trim()
                            contexts.add(ContextReference.GitReference(
                                type = GitRefType.DIFF,
                                content = content
                            ))
                        }
                        contextLine.startsWith("problems:") -> {
                            // 简化处理，创建空的问题列表
                            contexts.add(ContextReference.ProblemsReference(
                                problems = emptyList()
                            ))
                        }
                        contextLine.startsWith("selection:") -> {
                            contexts.add(ContextReference.SelectionReference)
                        }
                        contextLine.startsWith("terminal:") -> {
                            val content = contextLine.substring(9).trim()
                            contexts.add(ContextReference.TerminalReference(
                                content = content
                            ))
                        }
                        contextLine.startsWith("workspace:") -> {
                            contexts.add(ContextReference.WorkspaceReference)
                        }
                    }
                } else if (inContexts && !trimmedLine.startsWith(" ") && trimmedLine.isNotEmpty()) {
                    // 遇到新的顶级键，退出 contexts 解析
                    break
                }
            }
            
        } catch (e: Exception) {
            logger.error("Failed to parse front matter contexts: ${e.message}", e)
        }
        
        return contexts
    }
}