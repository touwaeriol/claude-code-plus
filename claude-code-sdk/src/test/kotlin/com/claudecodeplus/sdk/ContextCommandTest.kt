package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger

class ContextCommandTest {
    
    @Test
    fun `test context command detailed output`() = runBlocking {
        // 设置详细日志输出
        setupDetailedLogging()
        
        println("=== /context 命令详细测试 ===")
        
        val options = ClaudeCodeOptions(
            model = "claude-sonnet-4-20250514"
        )
        val client = ClaudeCodeSdkClient(options)
        
        try {
            println("1. 🔌 连接到Claude CLI...")
            client.connect()
            assertTrue(client.isConnected(), "应该成功连接到Claude")
            println("✅ 连接成功！")
            
            println("\n2. 📊 发送 /context 命令...")
            client.query("/context")
            
            // 收集所有响应消息
            val allMessages = mutableListOf<String>()
            var hasAssistantMessage = false
            var hasUserMessage = false
            var contextOutput = ""
            
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
                        is UserMessage -> {
                            hasUserMessage = true
                            val content = message.content.toString()
                            contextOutput = content
                            allMessages.add("UserMessage: $content")
                            println("👤 用户消息（上下文输出）: 收到${content.length}字符的上下文报告")
                        }
                        is ResultMessage -> {
                            allMessages.add("ResultMessage: subtype=${message.subtype}, isError=${message.isError}")
                            println("🎯 结果消息: subtype=${message.subtype}, isError=${message.isError}")
                        }
                        is SystemMessage -> {
                            allMessages.add("SystemMessage: ${message.subtype} - ${message.data}")
                            println("🔧 系统消息: ${message.subtype} - ${message.data}")
                        }
                        else -> {
                            allMessages.add("其他消息: ${message::class.simpleName}")
                            println("📄 收到其他类型消息：${message::class.simpleName}")
                        }
                    }
                }
            }
            
            println("\n4. 📊 /context 命令响应分析:")
            allMessages.forEach { msg ->
                println("  - $msg")
            }
            
            println("\n5. 🔍 上下文报告详细分析:")
            if (hasUserMessage && contextOutput.isNotEmpty()) {
                println("✅ /context 成功生成了上下文使用报告")
                
                // 分析上下文内容
                val lines = contextOutput.lines()
                println("📈 报告总行数: ${lines.size}")
                
                // 查找关键信息
                val tokenUsageLine = lines.find { it.contains("tokens") && it.contains("%") }
                tokenUsageLine?.let {
                    println("💾 Token使用情况: $it")
                }
                
                val mcpToolsCount = lines.count { it.contains("mcp__") }
                println("🔧 MCP工具数量: $mcpToolsCount")
                
                val memoryFilesCount = lines.count { it.contains("/.claude/") || it.contains("CLAUDE.md") }
                println("📁 内存文件数量: $memoryFilesCount")
                
                // 查找具体的上下文分类
                if (contextOutput.contains("Context Usage")) {
                    println("✅ 包含上下文使用概览")
                }
                if (contextOutput.contains("MCP tools")) {
                    println("✅ 包含MCP工具列表")
                }
                if (contextOutput.contains("Memory files")) {
                    println("✅ 包含内存文件列表")
                }
                
            } else {
                println("❌ /context 命令没有生成预期的上下文报告")
            }
            
            // 验证结果
            assertTrue(hasUserMessage, "应该收到包含上下文报告的UserMessage")
            assertTrue(contextOutput.isNotEmpty(), "上下文输出应该不为空")
            
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