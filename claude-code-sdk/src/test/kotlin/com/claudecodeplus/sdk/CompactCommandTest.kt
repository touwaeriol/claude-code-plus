package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class CompactCommandTest {
    
    @Test
    fun `test compact command response`() = runBlocking {
        println("=== /compact 命令测试 ===")
        
        val options = ClaudeCodeOptions(
            model = "claude-sonnet-4-20250514"
        )
        val client = ClaudeCodeSdkClient(options)
        
        try {
            println("1. 连接到 Claude CLI...")
            client.connect()
            assertTrue(client.isConnected(), "应该成功连接到Claude")
            
            println("\n2. 发送 /compact 命令...")
            client.query("/compact")
            
            // 收集所有响应消息
            val allMessages = mutableListOf<String>()
            var hasAssistantMessage = false
            
            withTimeout(15000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            hasAssistantMessage = true
                            val text = message.content.filterIsInstance<TextBlock>()
                                .joinToString("") { it.text }
                            allMessages.add("AssistantMessage: $text")
                            println("Claude 回复: $text")
                        }
                        is ResultMessage -> {
                            allMessages.add("ResultMessage: ${message.subtype}")
                            println("结果消息: ${message.subtype}")
                        }
                        is SystemMessage -> {
                            allMessages.add("SystemMessage: ${message.subtype} - ${message.data}")
                            println("系统消息: ${message.subtype} - ${message.data}")
                        }
                        is UserMessage -> {
                            allMessages.add("UserMessage: ${message.content}")
                            println("用户消息: ${message.content}")
                        }
                        else -> {
                            allMessages.add("其他消息: ${message::class.simpleName}")
                            println("收到其他类型消息：${message::class.simpleName}")
                        }
                    }
                }
            }
            
            println("\n3. /compact 命令返回的消息总结:")
            allMessages.forEach { msg ->
                println("  - $msg")
            }
            
            if (hasAssistantMessage) {
                println("\n✅ /compact 命令有 Claude 的文字回复")
            } else {
                println("\n⚠️ /compact 命令没有 Claude 的文字回复，只有系统消息")
            }
            
        } catch (e: Exception) {
            println("❌ 测试失败：${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            try {
                client.disconnect()
                println("已断开连接")
            } catch (e: Exception) {
                println("断开连接时出错：${e.message}")
            }
        }
    }
}