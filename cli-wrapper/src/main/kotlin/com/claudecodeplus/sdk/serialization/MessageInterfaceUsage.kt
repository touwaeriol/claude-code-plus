package com.claudecodeplus.sdk.serialization

/**
 * 消息接口使用示例和说明
 * 
 * 演示如何使用接口来区分不同类型的消息
 */
object MessageInterfaceUsage {
    
    /**
     * 处理 Claude 原生消息的示例
     */
    fun handleNativeMessage(message: ClaudeNativeMessage) {
        println("处理原生消息: type=${message.type}, session=${message.sessionId}")
        
        when (message) {
            is UserMessage -> {
                println("  用户消息: ${message.message?.content?.size} 个内容块")
            }
            is AssistantMessage -> {
                println("  助手消息: model=${message.message?.model}")
                // 可以安全地访问助手消息特有的字段
            }
            is SystemMessage -> {
                println("  系统消息: subtype=${message.subtype}, tools=${message.tools}")
            }
            is ResultMessage -> {
                println("  结果消息: cost=${message.total_cost_usd}")
            }
            is SummaryMessage -> {
                println("  摘要消息: compact=${message.isCompactSummary}")
            }
        }
    }
    
    /**
     * 处理界面可显示消息的示例
     */
    fun handleDisplayableMessage(message: DisplayableMessage) {
        println("显示消息: role=${message.displayRole}, content=${message.displayContent.take(50)}")
        
        // 检查是否支持工具调用
        if (message is ToolCallMessage && message.toolCalls.isNotEmpty()) {
            println("  包含 ${message.toolCalls.size} 个工具调用")
            message.toolCalls.forEach { toolCall ->
                println("    - ${toolCall.name} (${toolCall.status})")
            }
        }
        
        // 检查是否支持流式更新
        if (message is StreamableMessage && message.isStreamable) {
            println("  支持流式更新: streaming=${message.isStreaming}")
        }
        
        // 检查是否包含统计信息
        if (message is StatisticsMessage && message.tokenUsage != null) {
            val usage = message.tokenUsage!!
            println("  Token使用: input=${usage.input_tokens}, output=${usage.output_tokens}")
        }
    }
    
    /**
     * 消息转换流程示例
     */
    fun messageProcessingFlow() {
        // 1. 从 JSONL 解析原生消息
        val jsonLine = """{"type":"user","uuid":"123","sessionId":"sess-1","timestamp":"2025-01-01T00:00:00Z","message":{"role":"user","content":[{"type":"text","text":"Hello"}]}}"""
        val nativeMessage = ClaudeMessageParser.parseMessage(jsonLine)?.message
        
        if (nativeMessage != null) {
            println("=== 原生消息处理 ===")
            handleNativeMessage(nativeMessage)
            
            // 2. 转换为界面显示消息
            val displayMessage = MessageDisplayAdapter.toDisplayMessage(nativeMessage)
            if (displayMessage != null) {
                println("\n=== 界面消息处理 ===")
                handleDisplayableMessage(displayMessage)
            }
        }
    }
    
    /**
     * 接口类型检查的实用工具函数
     */
    fun getMessageCategories(message: Any): List<String> {
        val categories = mutableListOf<String>()
        
        when (message) {
            is ClaudeNativeMessage -> categories.add("Claude原生消息")
            is DisplayableMessage -> categories.add("界面可显示消息")
        }
        
        if (message is ToolCallMessage) categories.add("工具调用消息")
        if (message is StreamableMessage) categories.add("流式消息")
        if (message is StatisticsMessage) categories.add("统计信息消息")
        
        return categories
    }
    
    /**
     * 消息过滤示例
     */
    fun filterMessages(messages: List<Any>): Map<String, List<Any>> {
        return mapOf(
            "Claude原生消息" to messages.filterIsInstance<ClaudeNativeMessage>(),
            "界面显示消息" to messages.filterIsInstance<DisplayableMessage>(),
            "工具调用消息" to messages.filterIsInstance<ToolCallMessage>(),
            "流式消息" to messages.filterIsInstance<StreamableMessage>(),
            "统计消息" to messages.filterIsInstance<StatisticsMessage>()
        )
    }
}

/**
 * 消息接口总结
 * 
 * ## Claude CLI 原生消息类型 (实现 ClaudeNativeMessage 接口)
 * - UserMessage - 用户输入消息
 * - AssistantMessage - AI助手回复消息  
 * - SystemMessage - 系统配置消息
 * - ResultMessage - 会话结果统计消息
 * - SummaryMessage - 对话摘要消息
 * 
 * ## 界面显示消息类型 (实现 DisplayableMessage 接口)  
 * - UserDisplayMessage - 用户消息的显示版本
 * - AssistantDisplayMessage - 助手消息的显示版本 (支持工具调用、流式更新、统计信息)
 * - SystemDisplayMessage - 系统消息的显示版本
 * - SummaryDisplayMessage - 摘要消息的显示版本  
 * - ErrorDisplayMessage - 错误消息 (非原生消息)
 * 
 * ## 功能性接口
 * - ToolCallMessage - 包含工具调用的消息
 * - StreamableMessage - 支持流式更新的消息  
 * - StatisticsMessage - 包含统计信息的消息
 * 
 * ## 使用场景
 * 1. **Claude CLI 集成**: 使用 ClaudeNativeMessage 接口处理所有原生消息
 * 2. **UI 渲染**: 使用 DisplayableMessage 接口渲染聊天界面
 * 3. **工具调用**: 使用 ToolCallMessage 接口处理和显示工具执行
 * 4. **流式更新**: 使用 StreamableMessage 接口实现实时内容更新
 * 5. **统计展示**: 使用 StatisticsMessage 接口显示token使用和成本
 * 
 * ## 转换流程
 * Claude JSONL → ClaudeMessageParser → ClaudeNativeMessage → MessageDisplayAdapter → DisplayableMessage → UI渲染
 */