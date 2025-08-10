package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.SDKMessage
import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
            isStreaming = false // 事件流中的消息都是完整的
        )
    }
    
    /**
     * 提取消息内容
     */
    private fun extractMessageContent(contentJson: JsonObject?, rawContent: String): String {
        return try {
            when {
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
                rawContent.isNotBlank() -> {
                    rawContent
                }
                
                else -> ""
            }
        } catch (e: Exception) {
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
     */
    private fun extractToolCalls(contentJson: JsonObject?): List<com.claudecodeplus.ui.models.ToolCall> {
        // TODO: 实现工具调用提取逻辑
        return emptyList()
    }
    
}