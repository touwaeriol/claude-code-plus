package com.claudecodeplus.core.services.impl

import com.claudecodeplus.core.logging.logD
import com.claudecodeplus.core.logging.logE
import com.claudecodeplus.core.logging.logW
import com.claudecodeplus.core.models.ParseResult
import com.claudecodeplus.core.models.parseResultOf
import com.claudecodeplus.core.services.MessageProcessor
import com.claudecodeplus.session.models.ClaudeSessionMessage
import com.claudecodeplus.session.models.toEnhancedMessage
import com.claudecodeplus.ui.models.*
import kotlinx.serialization.json.*
import java.util.*

/**
 * 消息处理服务实现
 * 负责解析Claude CLI的各种消息格式
 */
class MessageProcessorImpl : MessageProcessor {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true 
    }
    
    override fun parseRealtimeMessage(jsonLine: String): ParseResult<EnhancedMessage> {
        return parseResultOf {
    //             logD("开始解析实时消息: ${jsonLine.take(100)}...")
            
            // 先检查是否为JSON格式
            if (!jsonLine.trim().startsWith("{")) {
    //                 logD("非JSON格式消息，忽略")
                return ParseResult.Ignored("非JSON内容")
            }
            
            val jsonObject = json.parseToJsonElement(jsonLine).jsonObject
            val messageType = jsonObject["type"]?.jsonPrimitive?.content
            val messageSubtype = jsonObject["subtype"]?.jsonPrimitive?.content
            
    //             logD("消息类型: type=$messageType, subtype=$messageSubtype")
            
            // 过滤系统消息
            when {
                messageType == "system" && messageSubtype == "init" -> {
    //                     logD("系统初始化消息，忽略")
                    return ParseResult.Ignored("系统初始化消息")
                }
                messageType == "result" -> {
    //                     logD("结果摘要消息，忽略")
                    return ParseResult.Ignored("结果摘要消息")
                }
                messageType == "error" -> {
                    val errorMessage = jsonObject["message"]?.jsonPrimitive?.content ?: "未知错误"
                    logW("收到错误消息: $errorMessage")
                    return ParseResult.Error("CLI错误: $errorMessage")
                }
                messageType == "system" && messageSubtype != null && messageSubtype != "init" -> {
    //                     logD("系统子消息，忽略: $messageSubtype")
                    return ParseResult.Ignored("系统消息: $messageSubtype")
                }
            }
            
            // 检查是否为工具结果消息
            if (isToolResultMessage(jsonLine)) {
    //                 logD("工具结果消息，需要特殊处理")
                return ParseResult.Ignored("工具结果消息，需要在上层处理")
            }
            
            // 解析实际的消息内容
            when (messageType) {
                "assistant" -> parseAssistantMessage(jsonObject)
                "user" -> parseUserMessage(jsonObject)
                else -> {
    //                     logD("未知消息类型或无需处理: $messageType")
                    return ParseResult.Ignored("未知消息类型: $messageType")
                }
            }
        }
    }
    
    override fun parseHistoryMessage(sessionMessage: ClaudeSessionMessage): ParseResult<EnhancedMessage> {
        return parseResultOf {
            val enhanced = sessionMessage.toEnhancedMessage()
                ?: return ParseResult.Ignored("鍘嗗彶娑堟伅蹇界暐: ${sessionMessage.type}")

            enhanced.copy(
                id = "history_${sessionMessage.uuid ?: System.nanoTime()}",
                isStreaming = false
            )
        }
    }

    
    override fun validateMessage(message: EnhancedMessage): Boolean {
        return when {
            message.content.isBlank() && message.toolCalls.isEmpty() -> {
                logW("消息验证失败: 内容和工具调用都为空")
                false
            }
            message.role == MessageRole.USER && message.content.isBlank() -> {
                logW("用户消息验证失败: 内容为空")
                false
            }
            else -> true
        }
    }
    
    override fun extractSessionId(jsonLine: String): String? {
        return try {
            if (!jsonLine.trim().startsWith("{")) return null
            
            val jsonObject = json.parseToJsonElement(jsonLine).jsonObject
            
            // 尝试从各种可能的字段中提取会话ID
            jsonObject["sessionId"]?.jsonPrimitive?.content
                ?: jsonObject["session_id"]?.jsonPrimitive?.content
                ?: jsonObject["message"]?.jsonObject?.get("sessionId")?.jsonPrimitive?.content
                ?: jsonObject["message"]?.jsonObject?.get("session_id")?.jsonPrimitive?.content
        } catch (e: Exception) {
            logE("提取会话ID失败", e)
            null
        }
    }
    
    override fun isSystemMessage(jsonLine: String): Boolean {
        return try {
            if (!jsonLine.trim().startsWith("{")) return false
            
            val jsonObject = json.parseToJsonElement(jsonLine).jsonObject
            val messageType = jsonObject["type"]?.jsonPrimitive?.content
            
            messageType == "system" || messageType == "result" || messageType == "error"
        } catch (e: Exception) {
            false
        }
    }
    
    override fun isToolResultMessage(jsonLine: String): Boolean {
        return try {
            if (!jsonLine.trim().startsWith("{")) return false
            
            val jsonObject = json.parseToJsonElement(jsonLine).jsonObject
            if (jsonObject["type"]?.jsonPrimitive?.content != "user") return false
            
            val messageObj = jsonObject["message"]?.jsonObject ?: return false
            val contentElement = messageObj["content"] ?: return false
            
            // 检查content数组中是否包含tool_result
            if (contentElement is JsonArray) {
                return contentElement.any { element ->
                    element.jsonObject["type"]?.jsonPrimitive?.content == "tool_result"
                }
            }
            
            false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 解析助手消息
     */
    private fun parseAssistantMessage(jsonObject: JsonObject): EnhancedMessage {
        val messageObj = jsonObject["message"]?.jsonObject
        val contentArray = messageObj?.get("content")?.jsonArray
        val role = messageObj?.get("role")?.jsonPrimitive?.content ?: "assistant"
        
    //         logD("解析助手消息，内容数组大小: ${contentArray?.size ?: 0}")
        
        // 提取文本内容
        val textContent = contentArray?.mapNotNull { contentElement ->
            val contentObj = contentElement.jsonObject
            val type = contentObj["type"]?.jsonPrimitive?.content
            if (type == "text") {
                contentObj["text"]?.jsonPrimitive?.content
            } else null
        }?.joinToString("") ?: ""
        
        // 提取工具调用
        val toolCalls = extractToolCalls(contentArray)
        
        // 提取token使用信息
        val tokenUsage = extractTokenUsage(messageObj)
        
    //         logD("助手消息解析完成: 文本长度=${textContent.length}, 工具调用=${toolCalls.size}")
        
        return EnhancedMessage.create(
            id = UUID.randomUUID().toString(),
            role = MessageRole.ASSISTANT,
            text = textContent,
            timestamp = System.currentTimeMillis(),
            toolCalls = toolCalls,
            tokenUsage = tokenUsage,
            isStreaming = false
        )
    }
    
    /**
     * 解析用户消息
     */
    private fun parseUserMessage(jsonObject: JsonObject): EnhancedMessage {
        val messageObj = jsonObject["message"]?.jsonObject
        val contentArray = messageObj?.get("content")?.jsonArray
        
        val textContent = contentArray?.mapNotNull { contentElement ->
            val contentObj = contentElement.jsonObject
            val type = contentObj["type"]?.jsonPrimitive?.content
            if (type == "text") {
                contentObj["text"]?.jsonPrimitive?.content
            } else null
        }?.joinToString("") ?: ""
        
    //         logD("用户消息解析完成: 文本长度=${textContent.length}")
        
        return EnhancedMessage.create(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            text = textContent,
            timestamp = System.currentTimeMillis(),
            toolCalls = emptyList(),
            tokenUsage = null,
            isStreaming = false
        )
    }
    
    /**
     * 提取工具调用列表
     */
    private fun extractToolCalls(contentArray: JsonArray?): List<ToolCall> {
        if (contentArray == null) return emptyList()
        
        return contentArray.mapNotNull { contentElement ->
            val contentObj = contentElement.jsonObject
            val type = contentObj["type"]?.jsonPrimitive?.content
            if (type == "tool_use") {
                val toolId = contentObj["id"]?.jsonPrimitive?.content ?: ""
                val toolName = contentObj["name"]?.jsonPrimitive?.content ?: ""
                val inputObj = contentObj["input"]?.jsonObject
                
                // 将输入参数转换为 Map
                val parameters = inputObj?.mapValues { (_, value) ->
                    convertJsonElementToAny(value)
                } ?: emptyMap()
                
                ToolCall.createGeneric(
                    id = toolId,
                    name = toolName,
                    parameters = parameters,
                    status = ToolCallStatus.RUNNING,
                    result = null,
                    startTime = System.currentTimeMillis(),
                    endTime = null
                )
            } else null
        }
    }
    
    /**
     * 提取token使用信息
     */
    private fun extractTokenUsage(messageObj: JsonObject?): EnhancedMessage.TokenUsage? {
        val usageObj = messageObj?.get("usage")?.jsonObject ?: return null
        
        val inputTokens = usageObj["input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val outputTokens = usageObj["output_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val cacheCreationTokens = usageObj["cache_creation_input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val cacheReadTokens = usageObj["cache_read_input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        
        return if (inputTokens > 0 || outputTokens > 0 || cacheCreationTokens > 0 || cacheReadTokens > 0) {
            EnhancedMessage.TokenUsage(
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                cacheCreationTokens = cacheCreationTokens,
                cacheReadTokens = cacheReadTokens
            )
        } else null
    }
    
    /**
     * 将JsonElement转换为对应的Kotlin类型
     */
    private fun convertJsonElementToAny(element: JsonElement): Any {
        return when (element) {
            is JsonPrimitive -> element.content
            is JsonArray -> element.map { convertJsonElementToAny(it) }
            is JsonObject -> element.mapValues { (_, v) -> convertJsonElementToAny(v) }
        }
    }
    
    /**
     * 将历史消息格式转换为实时消息格式
     */

    
    /**
     * 转换content item为JSON
     */

}
