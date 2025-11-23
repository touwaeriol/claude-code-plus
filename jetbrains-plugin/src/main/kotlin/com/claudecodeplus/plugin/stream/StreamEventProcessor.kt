package com.claudecodeplus.plugin.stream

import com.claudecodeplus.sdk.types.*
import com.claudecodeplus.plugin.types.ToolCallItem
import kotlinx.serialization.json.*
import java.util.logging.Logger

/**
 * Stream Event 处理器
 * 
 * 对应 frontend/src/utils/streamEventProcessor.ts
 * 负责处理各种 stream event 的业务逻辑
 */

/**
 * Stream Event 处理结果
 */
data class StreamEventProcessResult(
    val shouldUpdateMessages: Boolean = false,  // 是否需要更新 messages
    val shouldSetGenerating: Boolean? = null,   // 是否需要设置生成状态 (null = 不改变)
    val messageUpdated: Boolean = false,         // 消息是否被更新
    val newMessage: AssistantMessage? = null     // 新创建的消息（用于添加到 displayItems）
)

/**
 * Stream Event 处理上下文
 */
data class StreamEventContext(
    val messages: MutableList<MutableAssistantMessage>,  // 会话消息列表
    val toolInputJsonAccumulator: MutableMap<String, String>,  // JSON 累积器
    val registerToolCall: ((ToolUseBlock) -> Unit)? = null  // 注册工具调用的回调
)

object StreamEventProcessor {
    
    private val logger = Logger.getLogger(StreamEventProcessor::class.java.name)
    
    /**
     * 处理 StreamEvent
     * 
     * @param streamEvent SDK 的 StreamEvent 对象
     * @param context 处理上下文
     * @return 处理结果
     */
    fun process(
        streamEvent: StreamEvent,
        context: StreamEventContext
    ): StreamEventProcessResult {
        val event = streamEvent.event as? JsonObject ?: return createNoOpResult()
        val eventType = event["type"]?.jsonPrimitive?.content
        
        logger.info("处理 StreamEvent: type=$eventType")
        
        return when (eventType) {
            "message_start" -> processMessageStart(event, context)
            "content_block_start" -> processContentBlockStart(event, context)
            "content_block_delta" -> processContentBlockDelta(event, context)
            "content_block_stop" -> processContentBlockStop(event, context)
            "message_delta" -> processMessageDelta(event, context)
            "message_stop" -> processMessageStop(event, context)
            else -> {
                logger.warning("未知的 StreamEvent 类型: $eventType")
                createNoOpResult()
            }
        }
    }
    
    /**
     * 处理 message_start 事件
     */
    private fun processMessageStart(
        event: JsonObject,
        context: StreamEventContext
    ): StreamEventProcessResult {
        val messageObj = event["message"]?.jsonObject
        val eventMessageId = messageObj?.get("id")?.jsonPrimitive?.content
        
        logger.info("processMessageStart: id=$eventMessageId")
        
        // 查找最后一个 assistant 消息
        val lastMessage = context.messages.lastOrNull()
        
        // 情况1：有空的占位符消息，继续使用它
        if (lastMessage != null && StreamEventHandler.isMessageContentEmpty(lastMessage.content)) {
            // 更新消息 ID（如果后端返回了 ID）
            if (eventMessageId != null && lastMessage != null) {
                // 由于 AssistantMessage 是不可变的，这里简化处理
                logger.info("复用现有空消息")
            }
            
            return StreamEventProcessResult(
                shouldUpdateMessages = true,
                shouldSetGenerating = true,
                messageUpdated = true
            )
        }
        
        // 情况2：没有消息或最后一条消息已有实际内容，创建新消息
        val newMessage = MutableAssistantMessage(
            content = mutableListOf(),
            model = messageObj?.get("model")?.jsonPrimitive?.content ?: "unknown"
        )
        context.messages.add(newMessage)
        
        logger.info("创建新的 assistant 消息")
        
        // 转换为 AssistantMessage 用于返回
        val assistantMessage = AssistantMessage(
            content = newMessage.content,
            model = newMessage.model,
            tokenUsage = newMessage.tokenUsage
        )
        
        return StreamEventProcessResult(
            shouldUpdateMessages = true,
            shouldSetGenerating = true,
            messageUpdated = true,
            newMessage = assistantMessage
        )
    }
    
    /**
     * 处理 content_block_start 事件
     */
    private fun processContentBlockStart(
        event: JsonObject,
        context: StreamEventContext
    ): StreamEventProcessResult {
        val index = event["index"]?.jsonPrimitive?.int ?: return createNoOpResult()
        val contentBlock = event["content_block"]?.jsonObject ?: return createNoOpResult()
        val blockType = contentBlock["type"]?.jsonPrimitive?.content
        
        logger.info("processContentBlockStart: index=$index, type=$blockType")
        
        val lastMessage = context.messages.lastOrNull() as? MutableAssistantMessage
            ?: return createNoOpResult()
        
        when (blockType) {
            "text" -> {
                // 创建空文本块占位符
                val textBlock = TextBlock("")
                ensureContentSize(lastMessage, index)
                lastMessage.content = lastMessage.content.toMutableList().apply {
                    if (index < size) {
                        this[index] = textBlock
                    } else {
                        add(textBlock)
                    }
                }
            }
            
            "tool_use" -> {
                // 创建工具使用块
                val toolId = contentBlock["id"]?.jsonPrimitive?.content ?: "unknown"
                val toolName = contentBlock["name"]?.jsonPrimitive?.content ?: "unknown"
                
                val toolUseBlock = ToolUseBlock(
                    id = toolId,
                    name = toolName,
                    input = JsonObject(emptyMap())
                )
                
                ensureContentSize(lastMessage, index)
                lastMessage.content = lastMessage.content.toMutableList().apply {
                    if (index < size) {
                        this[index] = toolUseBlock
                    } else {
                        add(toolUseBlock)
                    }
                }
                
                // 注册工具调用
                context.registerToolCall?.invoke(toolUseBlock)
            }
            
            "thinking" -> {
                // 创建 thinking 块
                val thinkingBlock = ThinkingBlock(
                    thinking = "",
                    signature = ""
                )
                
                ensureContentSize(lastMessage, index)
                lastMessage.content = lastMessage.content.toMutableList().apply {
                    if (index < size) {
                        this[index] = thinkingBlock
                    } else {
                        add(thinkingBlock)
                    }
                }
            }
        }
        
        return StreamEventProcessResult(
            shouldUpdateMessages = true,
            shouldSetGenerating = true,
            messageUpdated = true
        )
    }
    
    /**
     * 处理 content_block_delta 事件
     */
    private fun processContentBlockDelta(
        event: JsonObject,
        context: StreamEventContext
    ): StreamEventProcessResult {
        val index = event["index"]?.jsonPrimitive?.int ?: return createNoOpResult()
        val delta = event["delta"]?.jsonObject ?: return createNoOpResult()
        
        val lastMessage = context.messages.lastOrNull() as? MutableAssistantMessage
            ?: return createNoOpResult()
        
        var success = false
        
        when {
            StreamEventHandler.isTextDelta(delta) -> {
                // 处理文本增量
                success = StreamEventHandler.applyTextDelta(lastMessage, index, delta)
            }
            StreamEventHandler.isInputJsonDelta(delta) -> {
                // 处理工具输入 JSON 增量
                success = StreamEventHandler.applyInputJsonDelta(
                    lastMessage,
                    index,
                    delta,
                    context.toolInputJsonAccumulator
                )
            }
            StreamEventHandler.isThinkingDelta(delta) -> {
                // 处理 Thinking 增量
                success = StreamEventHandler.applyThinkingDelta(lastMessage, index, delta)
            }
        }
        
        return StreamEventProcessResult(
            shouldUpdateMessages = true,
            shouldSetGenerating = true,
            messageUpdated = success
        )
    }
    
    /**
     * 处理 content_block_stop 事件
     */
    private fun processContentBlockStop(
        event: JsonObject,
        context: StreamEventContext
    ): StreamEventProcessResult {
        logger.info("processContentBlockStop")
        
        return StreamEventProcessResult(
            shouldUpdateMessages = true,
            shouldSetGenerating = true,
            messageUpdated = false
        )
    }
    
    /**
     * 处理 message_delta 事件
     */
    private fun processMessageDelta(
        event: JsonObject,
        context: StreamEventContext
    ): StreamEventProcessResult {
        // 处理消息级别的增量（如 token usage 更新）
        val delta = event["delta"]?.jsonObject
        val usage = delta?.get("usage")?.jsonObject
        
        if (usage != null) {
            val lastMessage = context.messages.lastOrNull() as? MutableAssistantMessage
            if (lastMessage != null) {
                val inputTokens = usage["input_tokens"]?.jsonPrimitive?.int ?: 0
                val outputTokens = usage["output_tokens"]?.jsonPrimitive?.int ?: 0
                
                lastMessage.tokenUsage = TokenUsage(
                    inputTokens = inputTokens,
                    outputTokens = outputTokens
                )
            }
        }
        
        return StreamEventProcessResult(
            shouldUpdateMessages = true,
            shouldSetGenerating = true,
            messageUpdated = true
        )
    }
    
    /**
     * 处理 message_stop 事件
     */
    private fun processMessageStop(
        event: JsonObject,
        context: StreamEventContext
    ): StreamEventProcessResult {
        logger.info("processMessageStop: 消息完成")
        
        return StreamEventProcessResult(
            shouldUpdateMessages = true,
            shouldSetGenerating = false,
            messageUpdated = false
        )
    }
    
    /**
     * 创建空操作结果
     */
    private fun createNoOpResult(): StreamEventProcessResult {
        return StreamEventProcessResult()
    }
    
    /**
     * 确保 content 列表有足够的大小
     */
    private fun ensureContentSize(message: MutableAssistantMessage, index: Int) {
        while (message.content.size <= index) {
            message.content = message.content + TextBlock("")
        }
    }
}

