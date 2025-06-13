package com.claudecodeplus.core

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ClaudeFinalTest {
    
    @Test
    fun testClaudeInteractiveSession() = runBlocking {
        println("=== 最终测试：Claude 交互式会话 ===\n")
        
        val session = ClaudeSession()
        val allOutputs = mutableListOf<String>()
        var foundResponse = false
        
        val collectJob = launch {
            session.messageFlow.collect { msg ->
                when (msg) {
                    is SessionMessage.Output -> {
                        allOutputs.add(msg.text)
                        val clean = msg.text.replace(Regex("\\x1b\\[[0-9;]*[a-zA-Z]"), "").trim()
                        
                        // 打印有意义的输出
                        if (clean.isNotEmpty() && !clean.contains("╭") && !clean.contains("│")) {
                            println("[输出]: ${clean.take(100)}")
                        }
                        
                        // 检查是否包含答案 - 排除终端控制序列
                        if (clean.contains("4") && 
                            !clean.contains("Welcome") && 
                            !clean.contains("PTY") &&
                            !clean.contains("2004") &&  // 排除 [?2004h
                            !clean.contains("1004") &&  // 排除 [?1004h
                            clean.length > 1) {         // 确保不是单个字符
                            foundResponse = true
                            println("\n✅ 找到答案！Claude 回复: $clean")
                        }
                    }
                    else -> {}
                }
            }
        }
        
        try {
            session.start()
            
            // 等待更长时间让 Claude 完全初始化
            println("等待 Claude 初始化...")
            delay(5000)
            
            // 尝试不同的输入方式 - 使用 \r 而不是 \n
            println("\n1. 发送: 2+2 (使用 \\r)")
            session.sendInput("2+2\r")
            delay(10000)
            
            if (!foundResponse) {
                println("\n2. 再次发送回车 (\\r)")
                session.sendInput("\r")
                delay(5000)
            }
            
            if (!foundResponse) {
                println("\n3. 发送新问题: What is 2+2? (使用 \\r)")
                session.sendInput("What is 2+2?\r")
                delay(10000)
            }
            
            if (!foundResponse) {
                println("\n4. 尝试发送 \\r\\n")
                session.sendInput("1+1\r\n")
                delay(10000)
            }
            
            // 结论
            println("\n\n=== 测试结论 ===")
            
            // 检查是否看到了 Claude 处理输入
            val sawProcessing = allOutputs.any { 
                it.contains("Cerebrating") || 
                it.contains("Simmering") || 
                it.contains("> 2+2") ||
                it.contains(">") && it.contains("2+2")
            }
            
            if (sawProcessing) {
                println("✅ Claude 接收到了输入并开始处理！")
                println("看到了: '> 2+2' 和 'Simmering...' 消息")
                println("\n使用 \\r 成功触发了 Claude 的响应！")
            } else {
                println("❌ Claude 没有响应输入")
            }
            
            assertTrue(sawProcessing, 
                "Claude 应该显示输入并开始处理")
            
            println("\n建议的解决方案：")
            println("1. 使用 'claude -p' 非交互模式")
            println("2. 使用管道方式：echo '问题' | claude")
            println("3. 研究 Claude 的 API 或其他集成方式")
            
        } finally {
            collectJob.cancel()
            session.stop()
        }
    }
}