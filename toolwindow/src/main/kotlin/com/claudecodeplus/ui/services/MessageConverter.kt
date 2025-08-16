package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.SDKMessage
import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import java.time.Instant

/**
 * 消息转换器
 * 将 SDK 消息转换为 UI 消息格式
 */
object MessageConverter {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 将 SDKMessage 转换为 EnhancedMessage
     * 处理来自事件流的消息和历史加载的消息
     */
    fun SDKMessage.toEnhancedMessage(): EnhancedMessage {
        // 优先使用 content 字段，然后是 data.text
        val rawContent = content ?: data.text ?: ""
        
        // 解析原始 JSON 内容
        val contentJson = try {
            if (rawContent.startsWith("{")) {
                json.parseToJsonElement(rawContent).jsonObject
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
        
        // 提取消息角色和内容
        val role = contentJson?.get("message")?.jsonObject?.get("role")?.jsonPrimitive?.content 
            ?: if (type == MessageType.TEXT) "assistant" else "system"
        val messageContent = extractMessageContent(contentJson, rawContent)
        
        // 提取时间戳 - 转换为 Long 毫秒时间戳
        val timestampMillis = try {
            if (this.timestamp.isNotEmpty()) {
                Instant.parse(this.timestamp).toEpochMilli()
            } else {
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        
        return EnhancedMessage(
            id = this.messageId ?: java.util.UUID.randomUUID().toString(),
            role = if (role == "user") MessageRole.USER else MessageRole.ASSISTANT,
            content = messageContent,
            timestamp = timestampMillis,
            toolCalls = extractToolCalls(contentJson),
            tokenUsage = extractTokenUsage(contentJson), // 提取真实token信息
            isStreaming = false // 事件流中的消息都是完整的
        )
    }
    
    /**
     * 提取消息内容
     */
    private fun extractMessageContent(contentJson: JsonObject?, rawContent: String): String {
        return try {
            when {
                // 检查是否是 message.content 数组格式（Claude CLI 常用格式）
                contentJson?.get("message")?.jsonObject?.get("content")?.jsonArray != null -> {
                    val contentArray = contentJson.get("message")!!.jsonObject.get("content")!!.jsonArray
                    extractTextFromContentArray(contentArray)
                }
                
                // 直接的文本消息
                contentJson?.get("message")?.jsonObject?.get("content")?.jsonPrimitive != null -> {
                    contentJson.get("message")!!.jsonObject.get("content")!!.jsonPrimitive.content
                }
                
                // 复杂的消息内容（包含工具调用等）
                contentJson?.get("message")?.jsonObject?.get("content")?.jsonObject != null -> {
                    val content = contentJson.get("message")!!.jsonObject.get("content")!!.jsonObject
                    extractTextFromComplexContent(content)
                }
                
                // 简单的文本内容
                contentJson?.get("content")?.jsonPrimitive != null -> {
                    contentJson.get("content")!!.jsonPrimitive.content
                }
                
                // 如果无法从 JSON 中提取，使用原始内容
                rawContent.isNotBlank() && !rawContent.startsWith("{") -> {
                    rawContent
                }
                
                else -> {
                    // 调试：输出无法解析的内容格式
                    println("[MessageConverter] 无法提取内容，rawContent前200字符: ${rawContent.take(200)}")
                    if (contentJson != null) {
                        println("[MessageConverter] contentJson结构:")
                        println("  - 顶层keys: ${contentJson.keys}")
                        contentJson.forEach { (key, value) ->
                            println("  - $key: ${value.toString().take(100)}")
                        }
                    }
                    ""
                }
            }
        } catch (e: Exception) {
            println("[MessageConverter] 提取消息内容失败: ${e.message}")
            println("[MessageConverter] rawContent: ${rawContent.take(200)}")
            ""
        }
    }
    
    /**
     * 从content数组中提取文本内容
     * 处理 Claude CLI 的标准消息格式
     */
    private fun extractTextFromContentArray(contentArray: JsonArray): String {
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
            println("[MessageConverter] 从content数组提取文本失败: ${e.message}")
            ""
        }
    }
    
    /**
     * 从复杂内容中提取文本
     */
    private fun extractTextFromComplexContent(content: JsonObject): String {
        return try {
            content.entries.mapNotNull { (key, value) ->
                if (key == "text" || key.contains("text")) {
                    value.jsonPrimitive?.content
                } else {
                    null
                }
            }.joinToString("\n")
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 提取工具调用信息
     * 处理工具调用和工具结果的关联
     */
    private fun extractToolCalls(contentJson: JsonObject?): List<com.claudecodeplus.ui.models.ToolCall> {
        return try {
            val toolCalls = mutableListOf<com.claudecodeplus.ui.models.ToolCall>()
            val toolResults = mutableMapOf<String, com.claudecodeplus.ui.models.ToolResult>()
            
            // 从 message.content 数组中提取工具调用和结果
            val messageObj = contentJson?.get("message")?.jsonObject
            val contentArray = messageObj?.get("content")?.jsonArray
            
            // 首先提取所有工具结果
            contentArray?.forEach { contentElement ->
                val contentObj = contentElement.jsonObject
                val type = contentObj["type"]?.jsonPrimitive?.content
                
                if (type == "tool_result") {
                    val toolUseId = contentObj["tool_use_id"]?.jsonPrimitive?.content ?: ""
                    val resultContent = contentObj["content"]?.jsonPrimitive?.content ?: ""
                    val isError = contentObj["is_error"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    
                    val result = if (isError) {
                        com.claudecodeplus.ui.models.ToolResult.Failure(resultContent)
                    } else {
                        com.claudecodeplus.ui.models.ToolResult.Success(resultContent)
                    }
                    
                    toolResults[toolUseId] = result
                }
            }
            
            // 然后提取工具调用并关联结果
            contentArray?.forEach { contentElement ->
                val contentObj = contentElement.jsonObject
                val type = contentObj["type"]?.jsonPrimitive?.content
                
                if (type == "tool_use") {
                    val toolId = contentObj["id"]?.jsonPrimitive?.content ?: ""
                    val toolName = contentObj["name"]?.jsonPrimitive?.content ?: ""
                    val inputJson = contentObj["input"]?.jsonObject
                    
                    // 将输入参数转换为 Map
                    val parameters = inputJson?.mapValues { (_, value) ->
                        value.jsonPrimitive?.content ?: value.toString()
                    } ?: emptyMap()
                    
                    // 查找对应的工具结果
                    val result = toolResults[toolId]
                    val status = when {
                        result is com.claudecodeplus.ui.models.ToolResult.Success -> com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS
                        result is com.claudecodeplus.ui.models.ToolResult.Failure -> com.claudecodeplus.ui.models.ToolCallStatus.FAILED
                        else -> com.claudecodeplus.ui.models.ToolCallStatus.RUNNING
                    }
                    
                    val toolCall = com.claudecodeplus.ui.models.ToolCall(
                        id = toolId,
                        name = toolName,
                        parameters = parameters,
                        status = status,
                        result = result,
                        startTime = System.currentTimeMillis(),
                        endTime = if (result != null) System.currentTimeMillis() else null
                    )
                    
                    toolCalls.add(toolCall)
                }
            }
            
            toolCalls
        } catch (e: Exception) {
            println("[MessageConverter] 提取工具调用失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 提取Token使用信息
     * 解析Claude CLI提供的精确token统计数据
     */
    private fun extractTokenUsage(contentJson: JsonObject?): EnhancedMessage.TokenUsage? {
        return try {
            // 从 message.usage 中提取token信息
            val usageObj = contentJson?.get("message")?.jsonObject?.get("usage")?.jsonObject
            
            if (usageObj != null) {
                val inputTokens = usageObj["input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val outputTokens = usageObj["output_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val cacheCreationTokens = usageObj["cache_creation_input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val cacheReadTokens = usageObj["cache_read_input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                
                // 只有当至少有一个token数大于0时才创建TokenUsage对象
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
            println("[MessageConverter] 提取Token使用信息失败: ${e.message}")
            null
        }
    }
    
}