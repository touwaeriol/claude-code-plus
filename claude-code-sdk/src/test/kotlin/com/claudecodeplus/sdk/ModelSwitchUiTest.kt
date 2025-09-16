package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class ModelSwitchUiTest {
    
    @Test
    fun `test model switch from opus to sonnet via ui commands`() = runBlocking {
        println("=== 模型切换UI测试 ===")
        
        val options = ClaudeCodeOptions(
            model = "claude-opus-4-1-20250805" // 初始使用 Opus
        )
        val client = ClaudeCodeSdkClient(options)
        
        try {
            println("1. 连接到 Claude CLI...")
            client.connect()
            assertTrue(client.isConnected(), "应该成功连接到Claude")
            
            // 第一步：发送 /model opus 命令并询问当前模型
            println("\n2. 发送 /model opus 命令...")
            client.query("/model opus")
            
            // 等待命令处理响应
            withTimeout(15000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            val text = message.content.filterIsInstance<TextBlock>()
                                .joinToString("") { it.text }
                            println("Claude 回复: $text")
                        }
                        is ResultMessage -> {
                            println("切换到 Opus 完成")
                        }
                        else -> {
                            println("收到其他类型消息：${message::class.simpleName}")
                        }
                    }
                }
            }
            
            println("\n3. 询问当前使用的模型...")
            client.query("What model are you currently using? Please tell me the exact model name.")
            
            var currentModel = ""
            withTimeout(15000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            val text = message.content.filterIsInstance<TextBlock>()
                                .joinToString("") { it.text }
                            currentModel = text
                            println("当前模型回复: $text")
                        }
                        is ResultMessage -> {
                            // 完成此轮对话
                        }
                        else -> {
                            println("收到其他类型消息：${message::class.simpleName}")
                        }
                    }
                }
            }
            
            assertTrue(currentModel.contains("opus", ignoreCase = true) || 
                      currentModel.contains("Opus", ignoreCase = true),
                      "应该确认当前使用 Opus 模型，实际回复：$currentModel")
            
            // 第二步：切换到 Sonnet
            println("\n4. 发送 /model sonnet 命令...")
            client.query("/model sonnet")
            
            withTimeout(15000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            val text = message.content.filterIsInstance<TextBlock>()
                                .joinToString("") { it.text }
                            println("Claude 回复: $text")
                        }
                        is ResultMessage -> {
                            println("切换到 Sonnet 完成")
                        }
                        else -> {
                            println("收到其他类型消息：${message::class.simpleName}")
                        }
                    }
                }
            }
            
            println("\n5. 再次询问当前使用的模型...")
            client.query("What model are you currently using? Please tell me the exact model name.")
            
            var newModel = ""
            withTimeout(15000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            val text = message.content.filterIsInstance<TextBlock>()
                                .joinToString("") { it.text }
                            newModel = text
                            println("新模型回复: $text")
                        }
                        is ResultMessage -> {
                            // 完成此轮对话
                        }
                        else -> {
                            println("收到其他类型消息：${message::class.simpleName}")
                        }
                    }
                }
            }
            
            assertTrue(newModel.contains("sonnet", ignoreCase = true) || 
                      newModel.contains("Sonnet", ignoreCase = true),
                      "应该确认当前使用 Sonnet 模型，实际回复：$newModel")
            
            println("\n✅ 模型切换测试成功！")
            println("  - 初始模型: Opus")
            println("  - 切换后模型: Sonnet")
            
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