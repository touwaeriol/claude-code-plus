package com.asakii.claude.agent.sdk.protocol

import com.asakii.claude.agent.sdk.exceptions.MessageParsingException
import com.asakii.claude.agent.sdk.types.*
import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString

/**
 * Parser for converting raw JSON messages to typed objects.
 */
class MessageParser {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Parse a JSON element into a typed Message object.
     */
    fun parseMessage(data: JsonElement): Message {
        try {
            val jsonObject = data.jsonObject
            val type = jsonObject["type"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'type' field in message")
            
            return when (type) {
                "user" -> parseUserMessage(jsonObject)
                "assistant" -> parseAssistantMessage(jsonObject)
                "system" -> parseSystemMessage(jsonObject)
                "result" -> parseResultMessage(jsonObject)
                "stream_event" -> parseStreamEvent(jsonObject)
                else -> throw MessageParsingException("Unknown message type: $type")
            }
        } catch (e: Exception) {
            throw MessageParsingException("Failed to parse message: ${e.message}", data = null, cause = e)
        }
    }
    
    /**
     * Parse user message.
     *
     * 支持解析 isReplay 字段用于区分压缩摘要消息：
     * - isReplay = false: 压缩摘要（新生成的上下文）
     * - isReplay = true: 确认消息（如 "Compacted"）
     */
    private fun parseUserMessage(jsonObject: JsonObject): UserMessage {
        // 检查是否有嵌套的message结构（Claude CLI stream-json格式）
        val messageObject = jsonObject["message"]?.jsonObject
        val content = if (messageObject != null) {
            // 新格式：{"type": "user", "message": {"role": "user", "content": [...]}}
            messageObject["content"] ?: JsonPrimitive("")
        } else {
            // 旧格式：{"type": "user", "content": "..."}
            jsonObject["content"] ?: run {
                // 容错处理：某些系统消息可能被错误标记为user类型
                println("[MessageParser] ⚠️ User message missing content field, using empty content for compatibility")
                JsonPrimitive("")
            }
        }

        val parentToolUseId = jsonObject["parent_tool_use_id"]?.jsonPrimitive?.contentOrNull
        val sessionId = jsonObject["session_id"]?.jsonPrimitive?.content ?: "default"
        // 解析 isReplay 字段（用于区分压缩摘要消息）
        val isReplay = jsonObject["isReplay"]?.jsonPrimitive?.booleanOrNull

        return UserMessage(
            content = content,
            parentToolUseId = parentToolUseId,
            sessionId = sessionId,
            isReplay = isReplay
        )
    }
    
    /**
     * Parse assistant message.
     */
    private fun parseAssistantMessage(jsonObject: JsonObject): AssistantMessage {
        // Check if content and model are directly in the object (old format)
        val directContent = jsonObject["content"]?.jsonArray
        val directModel = jsonObject["model"]?.jsonPrimitive?.content

        // Or check if they're nested in a "message" object (new format)
        val messageObject = jsonObject["message"]?.jsonObject
        val nestedContent = messageObject?.get("content")?.jsonArray
        val nestedModel = messageObject?.get("model")?.jsonPrimitive?.content

        val contentArray = directContent ?: nestedContent
            ?: throw MessageParsingException("Missing 'content' array in assistant message")
        val model = directModel ?: nestedModel
            ?: throw MessageParsingException("Missing 'model' in assistant message")

        val content = contentArray.map { parseContentBlock(it) }

        // Try to get token usage from either location
        val tokenUsage = jsonObject["token_usage"]?.let { parseTokenUsage(it) }
            ?: messageObject?.get("usage")?.let { parseTokenUsage(it) }

        // Get message id from nested message object
        val id = messageObject?.get("id")?.jsonPrimitive?.contentOrNull

        // 解析 parent_tool_use_id（用于子代理消息路由）
        val parentToolUseId = jsonObject["parent_tool_use_id"]?.jsonPrimitive?.contentOrNull

        return AssistantMessage(
            id = id,
            content = content,
            model = model,
            tokenUsage = tokenUsage,
            parentToolUseId = parentToolUseId
        )
    }
    
    /**
     * Parse system message.
     *
     * 支持多种系统消息类型：
     * - 通用系统消息（有 data 字段）
     * - 状态消息（subtype=status，有 status 字段）
     * - 压缩边界消息（subtype=compact_boundary，有 compact_metadata 字段）
     */
    private fun parseSystemMessage(jsonObject: JsonObject): Message {
        val subtype = jsonObject["subtype"]?.jsonPrimitive?.content
            ?: throw MessageParsingException("Missing 'subtype' in system message")

        return when (subtype) {
            "status" -> {
                // 状态消息：{"type":"system","subtype":"status","status":"compacting","session_id":"..."}
                val status = jsonObject["status"]?.jsonPrimitive?.contentOrNull
                val sessionId = jsonObject["session_id"]?.jsonPrimitive?.content ?: "default"
                val uuid = jsonObject["uuid"]?.jsonPrimitive?.contentOrNull
                StatusSystemMessage(
                    subtype = subtype,
                    status = status,
                    sessionId = sessionId,
                    uuid = uuid
                )
            }
            "compact_boundary" -> {
                // 压缩边界消息：{"type":"system","subtype":"compact_boundary","session_id":"...","compact_metadata":{...}}
                val sessionId = jsonObject["session_id"]?.jsonPrimitive?.content ?: "default"
                val uuid = jsonObject["uuid"]?.jsonPrimitive?.contentOrNull
                val compactMetadata = jsonObject["compact_metadata"]?.let { metadata ->
                    try {
                        json.decodeFromJsonElement<CompactMetadata>(metadata)
                    } catch (e: Exception) {
                        null
                    }
                }
                CompactBoundaryMessage(
                    subtype = subtype,
                    sessionId = sessionId,
                    uuid = uuid,
                    compactMetadata = compactMetadata
                )
            }
            else -> {
                // 通用系统消息（需要有 data 字段）
                val data = jsonObject["data"]
                    ?: throw MessageParsingException("Missing 'data' in system message (subtype=$subtype)")
                SystemMessage(
                    subtype = subtype,
                    data = data
                )
            }
        }
    }
    
    /**
     * Parse stream event message.
     */
    private fun parseStreamEvent(jsonObject: JsonObject): StreamEvent {
        val uuid = jsonObject["uuid"]?.jsonPrimitive?.content
            ?: throw MessageParsingException("Missing 'uuid' in stream_event message")
        val sessionId = jsonObject["session_id"]?.jsonPrimitive?.content
            ?: throw MessageParsingException("Missing 'session_id' in stream_event message")
        val event = jsonObject["event"]
            ?: throw MessageParsingException("Missing 'event' in stream_event message")
        val parentToolUseId = jsonObject["parent_tool_use_id"]?.jsonPrimitive?.contentOrNull
        
        return StreamEvent(
            uuid = uuid,
            sessionId = sessionId,
            event = event,
            parentToolUseId = parentToolUseId
        )
    }
    
    /**
     * Parse result message.
     */
    private fun parseResultMessage(jsonObject: JsonObject): ResultMessage {
        val subtype = jsonObject["subtype"]?.jsonPrimitive?.content 
            ?: throw MessageParsingException("Missing 'subtype' in result message")
        val durationMs = jsonObject["duration_ms"]?.jsonPrimitive?.long 
            ?: throw MessageParsingException("Missing 'duration_ms' in result message")
        val durationApiMs = jsonObject["duration_api_ms"]?.jsonPrimitive?.long 
            ?: throw MessageParsingException("Missing 'duration_api_ms' in result message")
        val isError = jsonObject["is_error"]?.jsonPrimitive?.boolean 
            ?: throw MessageParsingException("Missing 'is_error' in result message")
        val numTurns = jsonObject["num_turns"]?.jsonPrimitive?.int 
            ?: throw MessageParsingException("Missing 'num_turns' in result message")
        val sessionId = jsonObject["session_id"]?.jsonPrimitive?.content 
            ?: throw MessageParsingException("Missing 'session_id' in result message")
        
        val totalCostUsd = jsonObject["total_cost_usd"]?.jsonPrimitive?.doubleOrNull
        val usage = jsonObject["usage"]
        val result = jsonObject["result"]?.jsonPrimitive?.contentOrNull
        
        return ResultMessage(
            subtype = subtype,
            durationMs = durationMs,
            durationApiMs = durationApiMs,
            isError = isError,
            numTurns = numTurns,
            sessionId = sessionId,
            totalCostUsd = totalCostUsd,
            usage = usage,
            result = result
        )
    }
    
    /**
     * Parse content block from JSON.
     */
    private fun parseContentBlock(data: JsonElement): ContentBlock {
        val jsonObject = data.jsonObject
        val type = jsonObject["type"]?.jsonPrimitive?.content 
            ?: throw MessageParsingException("Missing 'type' field in content block")
        
        return when (type) {
            "text" -> {
                val text = jsonObject["text"]?.jsonPrimitive?.content
                    ?: throw MessageParsingException("Missing 'text' in text block")
                println("[MessageParser] 📝 解析TextBlock，文本长度: ${text.length}")
                println("[MessageParser] 📝 TextBlock内容: ${text.take(200)}")
                TextBlock(text)
            }
            "thinking" -> {
                val thinking = jsonObject["thinking"]?.jsonPrimitive?.content 
                    ?: throw MessageParsingException("Missing 'thinking' in thinking block")
                val signature = jsonObject["signature"]?.jsonPrimitive?.content 
                    ?: throw MessageParsingException("Missing 'signature' in thinking block")
                ThinkingBlock(thinking, signature)
            }
            "tool_use" -> {
                val id = jsonObject["id"]?.jsonPrimitive?.content
                    ?: throw MessageParsingException("Missing 'id' in tool_use block")
                val name = jsonObject["name"]?.jsonPrimitive?.content
                    ?: throw MessageParsingException("Missing 'name' in tool_use block")
                val input = jsonObject["input"]
                    ?: throw MessageParsingException("Missing 'input' in tool_use block")

                // 🔍 调试：打印解析到的 input
                println("🔍 [MessageParser] parseContentBlock tool_use: id=$id, name=$name, inputType=${input.javaClass.simpleName}, input=${input.toString().take(200)}")

                // 创建基础的ToolUseBlock
                val basicToolUse = ToolUseBlock(id, name, input)

                // 使用ToolTypeParser将其转换为具体的工具类型
                val result = ToolTypeParser.parseToolUseBlock(basicToolUse)
                println("🔍 [MessageParser] parseToolUseBlock result: ${result::class.simpleName}, input=${result.input.toString().take(200)}")
                result
            }
            "tool_result" -> {
                val toolUseId = jsonObject["tool_use_id"]?.jsonPrimitive?.content 
                    ?: throw MessageParsingException("Missing 'tool_use_id' in tool_result block")
                val content = jsonObject["content"]
                val isError = jsonObject["is_error"]?.jsonPrimitive?.booleanOrNull
                ToolResultBlock(toolUseId, content, isError)
            }
            else -> throw MessageParsingException("Unknown content block type: $type")
        }
    }
    
    /**
     * Parse token usage information.
     * 
     * Note: In streaming responses, `output_tokens` may be missing during intermediate states.
     * In such cases, we use 0 as the default value instead of throwing an exception.
     */
    private fun parseTokenUsage(data: JsonElement): TokenUsage {
        val jsonObject = data.jsonObject
        val inputTokens = jsonObject["input_tokens"]?.jsonPrimitive?.int 
            ?: throw MessageParsingException("Missing 'input_tokens' in token usage")
        // output_tokens may be missing in streaming intermediate states, use 0 as default
        val outputTokens = jsonObject["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val cacheCreationInputTokens = jsonObject["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull
        val cacheReadInputTokens = jsonObject["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheCreationInputTokens = cacheCreationInputTokens,
            cacheReadInputTokens = cacheReadInputTokens
        )
    }
    
    /**
     * Check if a JSON element represents a control message.
     */
    fun isControlMessage(data: JsonElement): Boolean {
        val jsonObject = data.jsonObject
        val type = jsonObject["type"]?.jsonPrimitive?.content
        return type == "control_request" || type == "control_response"
    }
    
    /**
     * Parse control request message.
     */
    fun parseControlRequest(data: JsonElement): Pair<String, ControlRequest> {
        val jsonObject = data.jsonObject
        val requestId = jsonObject["request_id"]?.jsonPrimitive?.content 
            ?: throw MessageParsingException("Missing 'request_id' in control request")
        val request = jsonObject["request"]?.jsonObject 
            ?: throw MessageParsingException("Missing 'request' in control request")
        
        val subtype = request["subtype"]?.jsonPrimitive?.content 
            ?: throw MessageParsingException("Missing 'subtype' in control request")
        
        val controlRequest = when (subtype) {
            "interrupt" -> InterruptRequest()
            "can_use_tool" -> {
                val toolName = request["tool_name"]?.jsonPrimitive?.content
                    ?: throw MessageParsingException("Missing 'tool_name' in permission request")
                val input = request["input"]
                    ?: throw MessageParsingException("Missing 'input' in permission request")
                val suggestions = request["permission_suggestions"]?.jsonArray?.toList()
                val blockedPath = request["blocked_path"]?.jsonPrimitive?.contentOrNull
                val toolUseId = request["tool_use_id"]?.jsonPrimitive?.contentOrNull
                val agentId = request["agent_id"]?.jsonPrimitive?.contentOrNull
                PermissionRequest(
                    toolName = toolName,
                    input = input,
                    permissionSuggestions = suggestions,
                    blockedPath = blockedPath,
                    toolUseId = toolUseId,
                    agentId = agentId
                )
            }
            "hook_callback" -> {
                val callbackId = request["callback_id"]?.jsonPrimitive?.content 
                    ?: throw MessageParsingException("Missing 'callback_id' in hook callback request")
                val input = request["input"] 
                    ?: throw MessageParsingException("Missing 'input' in hook callback request")
                val toolUseId = request["tool_use_id"]?.jsonPrimitive?.contentOrNull
                HookCallbackRequest(callbackId = callbackId, input = input, toolUseId = toolUseId)
            }
            "mcp_message" -> {
                val serverName = request["server_name"]?.jsonPrimitive?.content 
                    ?: throw MessageParsingException("Missing 'server_name' in MCP message request")
                val message = request["message"] 
                    ?: throw MessageParsingException("Missing 'message' in MCP message request")
                McpMessageRequest(serverName = serverName, message = message)
            }
            else -> throw MessageParsingException("Unknown control request subtype: $subtype")
        }
        
        return requestId to controlRequest
    }
    
    /**
     * Parse control response message.
     */
    fun parseControlResponse(data: JsonElement): ControlResponse {
        val jsonObject = data.jsonObject
        val response = jsonObject["response"]?.jsonObject 
            ?: throw MessageParsingException("Missing 'response' in control response")
        
        val subtype = response["subtype"]?.jsonPrimitive?.content 
            ?: throw MessageParsingException("Missing 'subtype' in control response")
        val requestId = response["request_id"]?.jsonPrimitive?.content 
            ?: throw MessageParsingException("Missing 'request_id' in control response")
        
        val responseData = response["response"]
        val error = response["error"]?.jsonPrimitive?.contentOrNull
        
        return ControlResponse(
            subtype = subtype,
            requestId = requestId,
            response = responseData,
            error = error
        )
    }
}