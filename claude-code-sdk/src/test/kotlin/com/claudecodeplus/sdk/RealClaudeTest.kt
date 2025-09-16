package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RealClaudeTest {
    
    @Test
    fun `test real claude connection and message`() = runBlocking {
        println("=== 开始真实Claude连接测试 ===")
        
        // 使用真实的传输层（不传入mockTransport参数）
        val options = ClaudeCodeOptions(
            model = "claude-sonnet-4-20250514"
        )
        val client = ClaudeCodeSdkClient(options)
        
        try {
            println("正在连接到 Claude CLI...")
            
            // 真实连接
            client.connect()
            
            println("连接状态：${client.isConnected()}")
            assertTrue(client.isConnected(), "应该成功连接到Claude")
            
            val serverInfo = client.getServerInfo()
            println("服务器信息：$serverInfo")
            assertNotNull(serverInfo, "应该获取到服务器信息")
            
            // 发送真实消息
            val testMessage = "Hello Claude! Please respond with just the word 'SUCCESS' and nothing else."
            println("发送消息：$testMessage")
            
            client.query(testMessage)
            
            // 收集响应
            println("等待Claude响应...")
            var receivedResponse = false
            var responseContent = ""
            
            // 使用 receiveResponse 等待完整响应
            withTimeout(30000) { // 30秒超时
                client.receiveResponse().collect { message ->
                    println("收到消息类型：${message::class.simpleName}")
                    
                    when (message) {
                        is AssistantMessage -> {
                            message.content.forEach { block ->
                                when (block) {
                                    is TextBlock -> {
                                        responseContent += block.text
                                        println("Claude回复：${block.text}")
                                    }
                                    else -> {
                                        println("收到其他类型内容块：${block::class.simpleName}")
                                    }
                                }
                            }
                            receivedResponse = true // 收到助手消息就标记为已收到
                        }
                        is ResultMessage -> {
                            println("收到结果消息：${message.subtype}")
                            // receiveResponse() 会自动在 ResultMessage 后结束，无需手动抛异常
                        }
                        else -> {
                            println("收到其他类型消息：${message::class.simpleName}")
                        }
                    }
                }
            }
            
            assertTrue(receivedResponse, "应该收到Claude的响应")
            assertTrue(responseContent.contains("SUCCESS", ignoreCase = true), 
                "回复应该包含SUCCESS，实际收到：$responseContent")
            
            println("✅ 真实Claude连接和消息测试成功！")
            
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
    
    @Test 
    fun `test real claude simple query`() = runBlocking {
        println("=== 简单查询测试 ===")
        
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(model = "claude-sonnet-4-20250514"))
        
        try {
            client.connect()
            println("连接成功")
            
            client.query("What is 2+2? Just answer with the number.")
            
            var gotAnswer = false
            withTimeout(15000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            val text = message.content.filterIsInstance<TextBlock>()
                                .joinToString("") { it.text }
                            println("Claude答案：$text")
                            if (text.contains("4")) {
                                gotAnswer = true
                            }
                        }
                        is ResultMessage -> {
                            if (message.subtype == "success") {
                                println("收到结果消息：查询完成")
                            }
                            // receiveResponse() 会自动在 ResultMessage 后结束
                        }
                        else -> {
                            println("收到其他类型消息：${message::class.simpleName}")
                        }
                    }
                }
            }
            
            assertTrue(gotAnswer, "应该收到包含答案4的回复")
            println("✅ 简单查询测试成功！")
            
        } finally {
            client.disconnect()
        }
    }
}