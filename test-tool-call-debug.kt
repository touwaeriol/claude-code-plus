// 测试工具调用转换的简单脚本
import com.claudecodeplus.ui.services.EnhancedMessageConverter
import com.claudecodeplus.sdk.session.ClaudeFileMessage

fun main() {
    val converter = EnhancedMessageConverter()
    
    // 测试一个包含工具调用的消息
    val testMessage = ClaudeFileMessage(
        uuid = "test-message",
        type = "assistant", 
        timestamp = "2025-08-13T10:00:00Z",
        message = ClaudeFileMessage.MessageContent(
            content = listOf(
                mapOf("type" to "tool_use", "id" to "test-tool-123", "name" to "LS", "input" to mapOf("path" to ".")),
                mapOf("type" to "text", "text" to "I'll list the files in the current directory")
            )
        )
    )
    
    val converted = converter.convertFromFileMessage(testMessage)
    
    println("转换后的消息:")
    println("- role: ${converted?.role}")
    println("- content: ${converted?.content}")
    println("- toolCalls.size: ${converted?.toolCalls?.size}")
    println("- orderedElements.size: ${converted?.orderedElements?.size}")
    
    converted?.toolCalls?.forEach { toolCall ->
        println("工具调用: ${toolCall.name}, 状态: ${toolCall.status}")
    }
}