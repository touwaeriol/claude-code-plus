package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger

class ModelOpusDetailedLoggingTest {
    
    @Test
    fun `test model opus command with detailed logging`() = runBlocking {
        // 设置详细日志输出
        setupDetailedLogging()
        
        println("=== /model opus 命令详细日志测试 ===")
        
        val options = ClaudeCodeOptions(
            model = "claude-sonnet-4-20250514"
        )
        val client = ClaudeCodeSdkClient(options)
        
        try {
            println("1. 🔌 连接到Claude CLI...")
            client.connect()
            assertTrue(client.isConnected(), "应该成功连接到Claude")
            println("✅ 连接成功！")
            
            println("\n2. 💬 发送 /model opus 命令...")
            client.query("/model opus")
            
            // 收集所有响应消息
            val allMessages = mutableListOf<String>()
            var hasAssistantMessage = false
            var hasResultMessage = false
            
            println("\n3. 📬 接收响应消息...")
            withTimeout(15000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            hasAssistantMessage = true
                            val text = message.content.filterIsInstance<TextBlock>()
                                .joinToString("") { it.text }
                            allMessages.add("AssistantMessage: $text")
                            println("🤖 Claude 回复: $text")
                        }
                        is ResultMessage -> {
                            hasResultMessage = true
                            allMessages.add("ResultMessage: subtype=${message.subtype}, isError=${message.isError}")
                            println("🎯 结果消息: subtype=${message.subtype}, isError=${message.isError}")
                        }
                        is SystemMessage -> {
                            allMessages.add("SystemMessage: ${message.subtype} - ${message.data}")
                            println("🔧 系统消息: ${message.subtype} - ${message.data}")
                        }
                        is UserMessage -> {
                            allMessages.add("UserMessage: ${message.content}")
                            println("👤 用户消息: ${message.content}")
                        }
                        else -> {
                            allMessages.add("其他消息: ${message::class.simpleName}")
                            println("📄 收到其他类型消息：${message::class.simpleName}")
                        }
                    }
                }
            }
            
            println("\n4. 📊 /model opus 命令响应分析:")
            allMessages.forEach { msg ->
                println("  - $msg")
            }
            
            // 验证结果
            assertTrue(hasResultMessage, "应该收到ResultMessage")
            
            if (hasAssistantMessage) {
                println("\n✅ /model opus 命令有Claude的文字回复（模型切换生效）")
            } else {
                println("\n⚠️ /model opus 命令没有Claude的文字回复（静默忽略，与之前发现一致）")
            }
            
            // 再发送一个常规问题验证模型
            println("\n5. 🔍 发送常规问题验证当前模型...")
            client.query("What model are you currently using? Please be specific.")
            
            var modelResponse = ""
            withTimeout(15000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            val text = message.content.filterIsInstance<TextBlock>()
                                .joinToString("") { it.text }
                            modelResponse += text
                            println("🤖 模型回复: $text")
                        }
                        is ResultMessage -> {
                            println("🎯 模型问题完成: ${message.subtype}")
                        }
                        else -> {
                            // 忽略其他消息类型
                        }
                    }
                }
            }
            
            println("\n6. 🧪 模型验证结果:")
            println("模型完整回复: $modelResponse")
            
            if (modelResponse.contains("opus", ignoreCase = true)) {
                println("✅ 模型已切换到Opus")
            } else if (modelResponse.contains("sonnet", ignoreCase = true)) {
                println("⚠️ 模型仍是Sonnet（/model opus命令被忽略）")
            } else {
                println("❓ 无法从回复中确定模型类型")
            }
            
            assertTrue(modelResponse.isNotEmpty(), "应该收到模型的回复")
            
        } catch (e: Exception) {
            println("❌ 测试失败：${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            try {
                client.disconnect()
                println("🚪 已断开连接")
            } catch (e: Exception) {
                println("⚠️ 断开连接时出错：${e.message}")
            }
        }
    }
    
    private fun setupDetailedLogging() {
        // 设置所有相关Logger的级别为INFO
        val loggers = listOf(
            "com.claudecodeplus.sdk.ClaudeCodeSdkClient",
            "com.claudecodeplus.sdk.transport.SubprocessTransport"
        )
        
        loggers.forEach { loggerName ->
            val logger = Logger.getLogger(loggerName)
            logger.level = Level.INFO
            
            // 确保有Console Handler
            if (logger.handlers.isEmpty()) {
                val handler = ConsoleHandler()
                handler.level = Level.INFO
                logger.addHandler(handler)
            }
            
            logger.useParentHandlers = false // 避免重复输出
        }
        
        println("🔧 已设置详细日志级别为INFO")
    }
}