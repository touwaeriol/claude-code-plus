package com.claudecodeplus.plugin.stream

import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.json.*
import java.util.logging.Logger

/**
 * Stream Event 处理工具函数
 * 
 * 对应 frontend/src/utils/streamEventHandler.ts
 * 提供类型安全的 stream event 解析和处理功能
 */
object StreamEventHandler {
    
    private val logger = Logger.getLogger(StreamEventHandler::class.java.name)
    
    /**
     * 类型守卫：检查是否为 message_start 事件
     */
    fun isMessageStartEvent(event: JsonObject): Boolean {
        return event["type"]?.jsonPrimitive?.content == "message_start"
    }
    
    /**
     * 类型守卫：检查是否为 message_delta 事件
     */
    fun isMessageDeltaEvent(event: JsonObject): Boolean {
        return event["type"]?.jsonPrimitive?.content == "message_delta"
    }
    
    /**
     * 类型守卫：检查是否为 message_stop 事件
     */
    fun isMessageStopEvent(event: JsonObject): Boolean {
        return event["type"]?.jsonPrimitive?.content == "message_stop"
    }
    
    /**
     * 类型守卫：检查是否为 content_block_start 事件
     */
    fun isContentBlockStartEvent(event: JsonObject): Boolean {
        return event["type"]?.jsonPrimitive?.content == "content_block_start"
    }
    
    /**
     * 类型守卫：检查是否为 content_block_delta 事件
     */
    fun isContentBlockDeltaEvent(event: JsonObject): Boolean {
        return event["type"]?.jsonPrimitive?.content == "content_block_delta"
    }
    
    /**
     * 类型守卫：检查是否为 content_block_stop 事件
     */
    fun isContentBlockStopEvent(event: JsonObject): Boolean {
        return event["type"]?.jsonPrimitive?.content == "content_block_stop"
    }
    
    /**
     * 类型守卫：检查是否为 text_delta
     */
    fun isTextDelta(delta: JsonObject?): Boolean {
        if (delta == null) return false
        return delta["type"]?.jsonPrimitive?.content == "text_delta" &&
               delta.containsKey("text")
    }
    
    /**
     * 类型守卫：检查是否为 input_json_delta
     */
    fun isInputJsonDelta(delta: JsonObject?): Boolean {
        if (delta == null) return false
        return delta["type"]?.jsonPrimitive?.content == "input_json_delta" &&
               delta.containsKey("partial_json")
    }
    
    /**
     * 类型守卫：检查是否为 thinking_delta
     */
    fun isThinkingDelta(delta: JsonObject?): Boolean {
        if (delta == null) return false
        return delta["type"]?.jsonPrimitive?.content == "thinking_delta" &&
               delta.containsKey("delta")
    }
    
    /**
     * 处理文本增量更新
     * 
     * @param message 目标消息
     * @param index 内容块索引
     * @param delta 文本增量 JsonObject
     * @returns 是否成功更新
     */
    fun applyTextDelta(
        message: MutableAssistantMessage,
        index: Int,
        delta: JsonObject
    ): Boolean {
        val text = delta["text"]?.jsonPrimitive?.content ?: return false
        
        val existingBlock = message.content.getOrNull(index)
        
        if (existingBlock is TextBlock) {
            // 追加到现有文本块
            val updatedBlock = TextBlock(existingBlock.text + text)
            message.content = message.content.toMutableList().apply {
                this[index] = updatedBlock
            }
            return true
        } else {
            // 创建新的文本块
            val newBlock = TextBlock(text)
            
            message.content = if (index >= message.content.size) {
                // 追加到末尾
                message.content + newBlock
            } else {
                // 替换指定位置
                message.content.toMutableList().apply {
                    this[index] = newBlock
                }
            }
            return true
        }
    }
    
    /**
     * 处理工具输入 JSON 增量更新
     * 
     * @param message 目标消息
     * @param index 内容块索引
     * @param delta JSON 增量
     * @param accumulator 累积器 Map（用于存储部分 JSON 字符串）
     * @returns 是否成功更新
     */
    fun applyInputJsonDelta(
        message: MutableAssistantMessage,
        index: Int,
        delta: JsonObject,
        accumulator: MutableMap<String, String>
    ): Boolean {
        val partialJson = delta["partial_json"]?.jsonPrimitive?.content ?: return false
        
        // 查找对应的 tool_use 块
        var toolUseBlock: ToolUseBlock? = null
        var toolIndex = -1
        
        if (index < message.content.size) {
            val block = message.content[index]
            if (block is ToolUseBlock) {
                toolUseBlock = block
                toolIndex = index
            }
        }
        
        // 如果通过 index 找不到，尝试查找最后一个 tool_use 块
        if (toolUseBlock == null) {
            for (i in message.content.indices.reversed()) {
                val block = message.content[i]
                if (block is ToolUseBlock) {
                    toolUseBlock = block
                    toolIndex = i
                    break
                }
            }
        }
        
        if (toolUseBlock == null || toolIndex == -1) {
            return false
        }
        
        // 累积 partial_json
        val accumulatorKey = "tool_input_${toolUseBlock.id}"
        val accumulatedJson = (accumulator[accumulatorKey] ?: "") + partialJson
        accumulator[accumulatorKey] = accumulatedJson
        
        // 尝试解析累积的 JSON
        return try {
            val parsed = Json.parseToJsonElement(accumulatedJson)
            
            // 更新工具调用块的 input
            val updatedBlock = ToolUseBlock(
                id = toolUseBlock.id,
                name = toolUseBlock.name,
                input = parsed
            )
            
            message.content = message.content.toMutableList().apply {
                this[toolIndex] = updatedBlock
            }
            
            true
        } catch (e: Exception) {
            // JSON 可能还不完整，暂时不更新
            // 但保留累积的字符串，等待更多增量
            false
        }
    }
    
    /**
     * 处理 Thinking 增量更新
     * 
     * @param message 目标消息
     * @param index 内容块索引
     * @param delta Thinking 增量
     * @returns 是否成功更新
     */
    fun applyThinkingDelta(
        message: MutableAssistantMessage,
        index: Int,
        delta: JsonObject
    ): Boolean {
        val thinkingText = delta["delta"]?.jsonPrimitive?.content ?: return false
        
        val existingBlock = message.content.getOrNull(index)
        
        if (existingBlock is ThinkingBlock) {
            // 追加到现有 thinking 块
            val updatedBlock = ThinkingBlock(
                thinking = existingBlock.thinking + thinkingText,
                signature = existingBlock.signature
            )
            message.content = message.content.toMutableList().apply {
                this[index] = updatedBlock
            }
            return true
        } else {
            // 创建新的 thinking 块
            val newBlock = ThinkingBlock(
                thinking = thinkingText,
                signature = ""  // signature 会在后续事件中更新
            )
            
            message.content = if (index >= message.content.size) {
                message.content + newBlock
            } else {
                message.content.toMutableList().apply {
                    this[index] = newBlock
                }
            }
            return true
        }
    }
    
    /**
     * 查找或创建最后一个 assistant 消息
     */
    fun findOrCreateLastAssistantMessage(messages: MutableList<MutableAssistantMessage>): MutableAssistantMessage {
        // 查找最后一个 assistant 消息
        val lastAssistant = messages.lastOrNull()
        
        if (lastAssistant != null) {
            return lastAssistant
        }
        
        // 创建新的 assistant 消息
        val newMessage = MutableAssistantMessage(
            content = mutableListOf(),
            model = "unknown"
        )
        messages.add(newMessage)
        return newMessage
    }
    
    /**
     * 判断消息内容是否实际为空
     */
    fun isMessageContentEmpty(content: List<ContentBlock>): Boolean {
        if (content.isEmpty()) return true
        
        // 检查是否只有空文本块
        return content.all { block ->
            if (block is TextBlock) {
                block.text.trim().isEmpty()
            } else {
                false  // 其他类型的块（如 tool_use）不算空
            }
        }
    }
}

/**
 * 可变的 AssistantMessage（用于流式更新）
 */
data class MutableAssistantMessage(
    var content: List<ContentBlock>,
    var model: String,
    var tokenUsage: com.claudecodeplus.sdk.types.TokenUsage? = null
)

