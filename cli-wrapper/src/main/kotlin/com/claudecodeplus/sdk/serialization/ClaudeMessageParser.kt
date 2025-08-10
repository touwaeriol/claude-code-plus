package com.claudecodeplus.sdk.serialization

import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Claude 消息解析器
 * 
 * 基于 Claudia 项目的成功实现，提供完整的 JSONL 消息解析功能
 * 支持所有 Claude CLI 消息类型的解析和工具调用映射
 */
object ClaudeMessageParser {
    
    private val logger = LoggerFactory.getLogger(ClaudeMessageParser::class.java)
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    /**
     * 解析单条 JSONL 消息
     * 
     * @param jsonLine JSONL 行内容
     * @return 解析结果或 null（如果解析失败）
     */
    fun parseMessage(jsonLine: String): ParsedMessage? {
        if (jsonLine.trim().isEmpty()) {
            return null
        }
        
        return try {
            val jsonElement = json.parseToJsonElement(jsonLine)
            val jsonObject = jsonElement.jsonObject
            
            // 识别消息类型
            val messageType = identifyMessageType(jsonObject)
            
            // 解析基础消息
            val message = when (messageType) {
                ClaudeMessageType.USER -> parseUserMessage(jsonObject)
                ClaudeMessageType.ASSISTANT -> parseAssistantMessage(jsonObject)
                ClaudeMessageType.SYSTEM -> parseSystemMessage(jsonObject)
                ClaudeMessageType.RESULT -> parseResultMessage(jsonObject)
                ClaudeMessageType.SUMMARY -> parseSummaryMessage(jsonObject)
                ClaudeMessageType.UNKNOWN -> {
                    logger.warn("Unknown message type in: ${jsonLine.take(100)}...")
                    return null
                }
            }
            
            // 提取工具调用信息
            val toolRequests = extractToolUseRequests(jsonObject)
            val toolResponses = extractToolUseResponses(jsonObject)
            
            ParsedMessage(
                message = message,
                toolUseRequests = toolRequests,
                toolUseResponses = toolResponses,
                rawJson = jsonLine
            )
            
        } catch (e: Exception) {
            logger.error("Failed to parse JSONL message: ${e.message}")
            logger.debug("Failed message content: ${jsonLine.take(200)}...")
            null
        }
    }
    
    /**
     * 批量解析 JSONL 内容
     * 
     * @param jsonlContent 完整的 JSONL 内容
     * @return 解析结果列表
     */
    fun parseMessages(jsonlContent: String): List<ParsedMessage> {
        val results = mutableListOf<ParsedMessage>()
        
        jsonlContent.lines().forEach { line ->
            parseMessage(line)?.let { parsed ->
                results.add(parsed)
            }
        }
        
        return results
    }
    
    /**
     * 识别消息类型
     */
    private fun identifyMessageType(jsonObject: JsonObject): ClaudeMessageType {
        val type = jsonObject["type"]?.jsonPrimitive?.content
        
        return when (type?.lowercase()) {
            "user" -> ClaudeMessageType.USER
            "assistant" -> ClaudeMessageType.ASSISTANT
            "system" -> ClaudeMessageType.SYSTEM
            "result" -> ClaudeMessageType.RESULT
            "summary" -> ClaudeMessageType.SUMMARY
            else -> ClaudeMessageType.UNKNOWN
        }
    }
    
    /**
     * 解析用户消息
     */
    private fun parseUserMessage(jsonObject: JsonObject): UserMessage {
        return UserMessage(
            type = "user",
            uuid = jsonObject["uuid"]?.jsonPrimitive?.content,
            sessionId = jsonObject["sessionId"]?.jsonPrimitive?.content,
            timestamp = jsonObject["timestamp"]?.jsonPrimitive?.content,
            parentUuid = jsonObject["parentUuid"]?.jsonPrimitive?.content,
            isSidechain = jsonObject["isSidechain"]?.jsonPrimitive?.boolean ?: false,
            userType = jsonObject["userType"]?.jsonPrimitive?.content,
            cwd = jsonObject["cwd"]?.jsonPrimitive?.content,
            version = jsonObject["version"]?.jsonPrimitive?.content,
            message = parseMessageContent(jsonObject["message"]?.jsonObject),
            toolUseResult = jsonObject["toolUseResult"]?.jsonObject
        )
    }
    
    /**
     * 解析助手消息
     */
    private fun parseAssistantMessage(jsonObject: JsonObject): AssistantMessage {
        return AssistantMessage(
            type = "assistant",
            uuid = jsonObject["uuid"]?.jsonPrimitive?.content,
            sessionId = jsonObject["sessionId"]?.jsonPrimitive?.content,
            timestamp = jsonObject["timestamp"]?.jsonPrimitive?.content,
            parentUuid = jsonObject["parentUuid"]?.jsonPrimitive?.content,
            isSidechain = jsonObject["isSidechain"]?.jsonPrimitive?.boolean ?: false,
            userType = jsonObject["userType"]?.jsonPrimitive?.content,
            cwd = jsonObject["cwd"]?.jsonPrimitive?.content,
            version = jsonObject["version"]?.jsonPrimitive?.content,
            message = parseAPIAssistantMessage(jsonObject["message"]?.jsonObject),
            requestId = jsonObject["requestId"]?.jsonPrimitive?.content,
            isApiErrorMessage = jsonObject["isApiErrorMessage"]?.jsonPrimitive?.boolean ?: false
        )
    }
    
    /**
     * 解析系统消息
     */
    private fun parseSystemMessage(jsonObject: JsonObject): SystemMessage {
        val mcpServers = jsonObject["mcp_servers"]?.jsonArray?.map { serverElement ->
            val serverObj = serverElement.jsonObject
            McpServer(
                name = serverObj["name"]?.jsonPrimitive?.content ?: "",
                status = serverObj["status"]?.jsonPrimitive?.content ?: ""
            )
        } ?: emptyList()
        
        val tools = jsonObject["tools"]?.jsonArray?.map { 
            it.jsonPrimitive.content 
        } ?: emptyList()
        
        return SystemMessage(
            type = "system",
            subtype = jsonObject["subtype"]?.jsonPrimitive?.content,
            uuid = jsonObject["uuid"]?.jsonPrimitive?.content,
            sessionId = jsonObject["sessionId"]?.jsonPrimitive?.content,
            timestamp = jsonObject["timestamp"]?.jsonPrimitive?.content,
            apiKeySource = jsonObject["apiKeySource"]?.jsonPrimitive?.content,
            cwd = jsonObject["cwd"]?.jsonPrimitive?.content,
            version = jsonObject["version"]?.jsonPrimitive?.content,
            tools = tools,
            mcp_servers = mcpServers,
            model = jsonObject["model"]?.jsonPrimitive?.content,
            permissionMode = jsonObject["permissionMode"]?.jsonPrimitive?.content
        )
    }
    
    /**
     * 解析结果消息
     */
    private fun parseResultMessage(jsonObject: JsonObject): ResultMessage {
        return ResultMessage(
            type = "result",
            subtype = jsonObject["subtype"]?.jsonPrimitive?.content,
            uuid = jsonObject["uuid"]?.jsonPrimitive?.content,
            sessionId = jsonObject["sessionId"]?.jsonPrimitive?.content,
            timestamp = jsonObject["timestamp"]?.jsonPrimitive?.content,
            duration_ms = jsonObject["duration_ms"]?.jsonPrimitive?.longOrNull,
            duration_api_ms = jsonObject["duration_api_ms"]?.jsonPrimitive?.longOrNull,
            num_turns = jsonObject["num_turns"]?.jsonPrimitive?.intOrNull,
            is_error = jsonObject["is_error"]?.jsonPrimitive?.boolean ?: false,
            result = jsonObject["result"]?.jsonPrimitive?.content,
            total_cost_usd = jsonObject["total_cost_usd"]?.jsonPrimitive?.doubleOrNull,
            usage = parseTokenUsage(jsonObject["usage"]?.jsonObject)
        )
    }
    
    /**
     * 解析摘要消息
     */
    private fun parseSummaryMessage(jsonObject: JsonObject): SummaryMessage {
        return SummaryMessage(
            type = "summary",
            uuid = jsonObject["uuid"]?.jsonPrimitive?.content,
            sessionId = jsonObject["sessionId"]?.jsonPrimitive?.content,
            timestamp = jsonObject["timestamp"]?.jsonPrimitive?.content,
            summary = jsonObject["summary"]?.jsonPrimitive?.content ?: "",
            leafUuid = jsonObject["leafUuid"]?.jsonPrimitive?.content,
            isCompactSummary = jsonObject["isCompactSummary"]?.jsonPrimitive?.boolean ?: false
        )
    }
    
    /**
     * 解析消息内容
     */
    private fun parseMessageContent(messageObj: JsonObject?): MessageContent? {
        if (messageObj == null) return null
        
        val role = messageObj["role"]?.jsonPrimitive?.content ?: return null
        val contentArray = messageObj["content"]?.jsonArray ?: return null
        
        val contentBlocks = contentArray.mapNotNull { contentElement ->
            parseContentBlock(contentElement.jsonObject)
        }
        
        return MessageContent(role = role, content = contentBlocks)
    }
    
    /**
     * 解析 API 助手消息内容
     */
    private fun parseAPIAssistantMessage(messageObj: JsonObject?): APIAssistantMessage? {
        if (messageObj == null) return null
        
        val id = messageObj["id"]?.jsonPrimitive?.content ?: return null
        val model = messageObj["model"]?.jsonPrimitive?.content ?: return null
        val contentArray = messageObj["content"]?.jsonArray ?: JsonArray(emptyList())
        
        val contentBlocks = contentArray.mapNotNull { contentElement ->
            parseContentBlock(contentElement.jsonObject)
        }
        
        val usage = parseTokenUsage(messageObj["usage"]?.jsonObject) ?: TokenUsage(0, 0)
        
        return APIAssistantMessage(
            id = id,
            type = messageObj["type"]?.jsonPrimitive?.content ?: "message",
            role = messageObj["role"]?.jsonPrimitive?.content ?: "assistant",
            model = model,
            content = contentBlocks,
            stop_reason = messageObj["stop_reason"]?.jsonPrimitive?.content,
            stop_sequence = messageObj["stop_sequence"]?.jsonPrimitive?.content,
            usage = usage
        )
    }
    
    /**
     * 解析内容块
     */
    private fun parseContentBlock(contentObj: JsonObject): ContentBlock? {
        val type = contentObj["type"]?.jsonPrimitive?.content ?: return null
        
        return when (type) {
            "text" -> {
                val text = contentObj["text"]?.jsonPrimitive?.content ?: return null
                TextBlock(text = text)
            }
            "tool_use" -> {
                val id = contentObj["id"]?.jsonPrimitive?.content ?: return null
                val name = contentObj["name"]?.jsonPrimitive?.content ?: return null
                val input = contentObj["input"]?.jsonObject ?: JsonObject(emptyMap())
                ToolUseBlock(id = id, name = name, input = input)
            }
            "tool_result" -> {
                val toolUseId = contentObj["tool_use_id"]?.jsonPrimitive?.content ?: return null
                val content = contentObj["content"] ?: JsonPrimitive("")
                val isError = contentObj["is_error"]?.jsonPrimitive?.boolean ?: false
                ToolResultBlock(tool_use_id = toolUseId, content = content, is_error = isError)
            }
            else -> null
        }
    }
    
    /**
     * 解析 Token 使用统计
     */
    private fun parseTokenUsage(usageObj: JsonObject?): TokenUsage? {
        if (usageObj == null) return null
        
        val inputTokens = usageObj["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val outputTokens = usageObj["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        
        val serverToolUse = usageObj["server_tool_use"]?.jsonObject?.let { serverObj ->
            ServerToolUse(
                web_search_requests = serverObj["web_search_requests"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }
        
        return TokenUsage(
            input_tokens = inputTokens,
            output_tokens = outputTokens,
            cache_creation_input_tokens = usageObj["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            cache_read_input_tokens = usageObj["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            service_tier = usageObj["service_tier"]?.jsonPrimitive?.content,
            server_tool_use = serverToolUse
        )
    }
    
    /**
     * 提取工具使用请求
     */
    private fun extractToolUseRequests(jsonObject: JsonObject): List<ToolUseRequest> {
        val requests = mutableListOf<ToolUseRequest>()
        
        // 从 assistant 消息的 content 中提取工具调用
        val message = jsonObject["message"]?.jsonObject
        val contentArray = message?.get("content")?.jsonArray
        
        contentArray?.forEach { contentElement ->
            val contentObj = contentElement.jsonObject
            if (contentObj["type"]?.jsonPrimitive?.content == "tool_use") {
                val id = contentObj["id"]?.jsonPrimitive?.content
                val name = contentObj["name"]?.jsonPrimitive?.content
                val input = contentObj["input"]?.jsonObject
                
                if (id != null && name != null && input != null) {
                    requests.add(ToolUseRequest(id = id, name = name, input = input))
                }
            }
        }
        
        return requests
    }
    
    /**
     * 提取工具使用响应
     */
    private fun extractToolUseResponses(jsonObject: JsonObject): List<ToolUseResponse> {
        val responses = mutableListOf<ToolUseResponse>()
        
        // 从 user 消息的 content 中提取工具结果
        val message = jsonObject["message"]?.jsonObject
        val contentArray = message?.get("content")?.jsonArray
        
        contentArray?.forEach { contentElement ->
            val contentObj = contentElement.jsonObject
            if (contentObj["type"]?.jsonPrimitive?.content == "tool_result") {
                val toolUseId = contentObj["tool_use_id"]?.jsonPrimitive?.content
                val content = contentObj["content"]
                val isError = contentObj["is_error"]?.jsonPrimitive?.boolean ?: false
                
                if (toolUseId != null && content != null) {
                    responses.add(ToolUseResponse(
                        toolUseId = toolUseId, 
                        content = content, 
                        isError = isError
                    ))
                }
            }
        }
        
        return responses
    }
}