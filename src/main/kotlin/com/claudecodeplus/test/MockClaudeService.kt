package com.claudecodeplus.test

import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.sdk.SDKMessage
import com.claudecodeplus.sdk.MessageData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * 用于测试的 Claude 服务模拟
 * 提供基本的响应功能，不依赖真实的 Claude CLI
 */
class MockClaudeService {
    
    private var sessionId = UUID.randomUUID().toString()
    
    fun query(prompt: String, projectPath: String): Flow<SDKMessage> = flow {
        // 发送会话开始消息
        emit(SDKMessage(
            type = MessageType.START,
            data = MessageData(sessionId = sessionId)
        ))
        
        // 模拟响应延迟
        delay(300)
        
        // 根据输入生成响应
        val response = when {
            prompt.contains("@") -> {
                "我看到你引用了文件。在实际使用中，我可以读取和分析这些文件的内容。\n\n" +
                "文件搜索功能正常工作！你可以：\n" +
                "- 使用上下箭头选择文件\n" +
                "- 按回车确认选择\n" +
                "- 按 ESC 关闭文件列表"
            }
            
            prompt.contains("测试", ignoreCase = true) || prompt.contains("test", ignoreCase = true) -> {
                "这是一个测试响应。所有功能都在正常工作！\n\n" +
                "你可以尝试：\n" +
                "1. 输入 @ 来测试文件搜索\n" +
                "2. 使用工具栏上的按钮\n" +
                "3. 发送任何消息来测试对话功能"
            }
            
            prompt.length < 10 -> {
                "你好！请随意提问或测试功能。"
            }
            
            else -> {
                "收到你的消息：\"$prompt\"\n\n" +
                "在实际环境中，这里会显示 Claude 的智能响应。\n" +
                "当前是测试模式，主要用于验证 UI 交互功能。"
            }
        }
        
        // 模拟流式响应
        val words = response.split(" ")
        val chunks = words.chunked(3) // 每3个词一组
        
        for (chunk in chunks) {
            val text = chunk.joinToString(" ") + " "
            emit(SDKMessage(
                type = MessageType.TEXT,
                data = MessageData(text = text)
            ))
            delay(50) // 模拟打字效果
        }
        
        // 结束消息
        emit(SDKMessage(
            type = MessageType.END,
            data = MessageData()
        ))
    }
    
    /**
     * 获取当前会话ID
     */
    fun getCurrentSessionId(): String = sessionId
    
    /**
     * 开始新会话
     */
    fun startNewSession() {
        sessionId = UUID.randomUUID().toString()
    }
}