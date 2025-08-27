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
        println("[MessageConverter] 开始转换消息: type=${this.type}, messageId=${this.messageId}")
        
        // 优先使用 content 字段，然后是 data.text
        val rawContent = content ?: data.text ?: ""
        // 分析原始内容
        
        // 解析原始 JSON 内容
        val contentJson = try {
            if (rawContent.startsWith("{")) {
                val parsed = json.parseToJsonElement(rawContent).jsonObject
                // 成功解析JSON
                parsed
            } else {
                // 原始内容不是JSON格式
                null
            }
        } catch (e: Exception) {
            // JSON解析失败
            null
        }
        
        // 检查是否是用户中断消息
        val isInterruptMessage = rawContent.contains("用户已中断请求") || 
                                rawContent.contains("Request interrupted by user")
        
        // 提取消息角色和内容
        val role = contentJson?.get("message")?.jsonObject?.get("role")?.jsonPrimitive?.content 
            ?: when (type) {
                MessageType.TEXT -> "assistant"
                MessageType.TOOL_USE -> "assistant"  // 工具调用来自助手
                MessageType.TOOL_RESULT -> "user"    // 工具结果来自用户
                else -> "system"
            }
        // 解析出的角色: $role
        
        val messageContent = if (isInterruptMessage) {
            "⏹️ 请求已被用户中断"
        } else {
            extractMessageContent(contentJson, rawContent)
        }
        // 最终消息内容长度: ${messageContent.length}
        
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
        
        val enhancedMessage = EnhancedMessage(
            id = this.messageId ?: java.util.UUID.randomUUID().toString(),
            role = if (role == "user") MessageRole.USER else MessageRole.ASSISTANT,
            content = messageContent,
            timestamp = timestampMillis,
            toolCalls = extractToolCalls(contentJson),
            tokenUsage = extractTokenUsage(contentJson), // 提取真实token信息
            isStreaming = false // 事件流中的消息都是完整的
        )
        
        // 转换完成: ${enhancedMessage.role}
        return enhancedMessage
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
                
                // Claude CLI 结果消息格式 (type: "result")
                contentJson?.get("type")?.jsonPrimitive?.content == "result" -> {
                    contentJson.get("result")?.jsonPrimitive?.content ?: ""
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
     * 现在正确处理分离的工具调用事件：
     * 1. tool_use 事件 -> 创建 RUNNING 状态的工具调用
     * 2. tool_result 事件 -> 忽略（后续由 SessionObject 关联）
     * 3. 普通消息事件 -> 无工具调用
     */
    private fun extractToolCalls(contentJson: JsonObject?): List<com.claudecodeplus.ui.models.ToolCall> {
        return try {
            val toolCalls = mutableListOf<com.claudecodeplus.ui.models.ToolCall>()
            
            // 开始解析工具调用
            
            // 从 message.content 数组中提取工具调用和结果
            val messageObj = contentJson?.get("message")?.jsonObject
            val contentArray = messageObj?.get("content")?.jsonArray
            
            println("[MessageConverter] messageObj存在: ${messageObj != null}, contentArray存在: ${contentArray != null}, 数组大小: ${contentArray?.size ?: 0}")
            
            // 打印contentArray的详细结构
            contentArray?.forEachIndexed { index, element ->
                val obj = element.jsonObject
                val type = obj["type"]?.jsonPrimitive?.content
                println("  [$index] type: $type, keys: ${obj.keys}")
            }
            
            // 只提取 tool_use 类型，创建 RUNNING 状态的工具调用
            contentArray?.forEach { contentElement ->
                val contentObj = contentElement.jsonObject
                val type = contentObj["type"]?.jsonPrimitive?.content
                
                if (type == "tool_use") {
                    val toolId = contentObj["id"]?.jsonPrimitive?.content ?: ""
                    val toolName = contentObj["name"]?.jsonPrimitive?.content ?: ""
                    val inputJson = contentObj["input"]?.jsonObject
                    
                    println("[MessageConverter] 🔧 发现工具调用: $toolName (ID: $toolId)")
                    
                    // 将输入参数转换为 Map
                    val parameters = inputJson?.mapValues { (_, value) ->
                        value.jsonPrimitive?.content ?: value.toString()
                    } ?: emptyMap()
                    
                    // 创建 RUNNING 状态的工具调用（结果将在后续事件中更新）
                    val toolCall = com.claudecodeplus.ui.models.ToolCall(
                        id = toolId,
                        name = toolName,
                        parameters = parameters,
                        status = com.claudecodeplus.ui.models.ToolCallStatus.RUNNING,
                        result = null,
                        startTime = System.currentTimeMillis(),
                        endTime = null
                    )
                    
                    toolCalls.add(toolCall)
                }
            }
            
            // 工具调用解析完成，共 ${toolCalls.size} 个
            // 工具调用详情已记录
            
            toolCalls
        } catch (e: Exception) {
            // 提取工具调用失败
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 提取工具结果信息
     * 从 tool_result 事件中提取结果，用于在 SessionObject 中关联到对应的工具调用
     */
    fun extractToolResults(contentJson: JsonObject?): Map<String, com.claudecodeplus.ui.models.ToolResult> {
        return try {
            val toolResults = mutableMapOf<String, com.claudecodeplus.ui.models.ToolResult>()
            
            val messageObj = contentJson?.get("message")?.jsonObject
            val contentArray = messageObj?.get("content")?.jsonArray
            
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
                    println("[MessageConverter] 🔧 发现工具结果: toolId=$toolUseId, isError=$isError")
                }
            }
            
            println("[MessageConverter] ✅ 工具结果解析完成，共 ${toolResults.size} 个结果")
            toolResults
        } catch (e: Exception) {
            println("[MessageConverter] ❌ 工具结果解析失败: ${e.message}")
            emptyMap()
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