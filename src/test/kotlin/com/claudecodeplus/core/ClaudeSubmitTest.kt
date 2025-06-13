package com.claudecodeplus.core

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test

class ClaudeSubmitTest {
    
    @Test
    fun testMultilineInput() = runBlocking {
        println("=== 测试多行输入和提交 ===\n")
        
        val session = ClaudeSession()
        
        val collectJob = launch {
            session.messageFlow.collect { msg ->
                when (msg) {
                    is SessionMessage.Output -> {
                        val text = msg.text
                        // 检查是否有光标移动到新位置
                        if (text.contains("[7m") && text.contains("[27m")) {
                            println("[光标位置更新]")
                        }
                        
                        val clean = text.replace(Regex("\\x1b\\[[0-9;]*[a-zA-Z]"), "").trim()
                        if (clean.isNotEmpty() && clean.length > 10 && 
                            !clean.contains("Welcome") && !clean.contains("│")) {
                            println("[可能的输出]: $clean")
                        }
                    }
                    is SessionMessage.Input -> {
                        println("[输入]: ${msg.text}")
                    }
                    else -> {}
                }
            }
        }
        
        try {
            session.start()
            delay(3000)
            
            // 理论：Claude 可能在等待"输入完成"的信号
            // 在真实终端中，这可能是：
            // 1. 空行
            // 2. 特定的快捷键
            // 3. 某种模式切换
            
            println("\n--- 测试1：正常输入+回车 ---")
            session.sendInput("Hello Claude\n")
            delay(5000)
            
            println("\n--- 测试2：输入+空行 ---")
            session.sendInput("What is 2+2?\n\n")
            delay(5000)
            
            println("\n--- 测试3：分步输入 ---")
            session.sendInput("Tell me")
            delay(1000)
            session.sendInput(" about")
            delay(1000)
            session.sendInput(" yourself")
            delay(1000)
            session.sendInput("\n")
            delay(5000)
            
            println("\n--- 测试4：Ctrl+D (EOF) ---")
            session.sendInput("Hi\n")
            delay(500)
            session.sendInput("\u0004")  // Ctrl+D
            delay(5000)
            
            println("\n结论：Claude 在 PTY 模式下似乎不响应标准输入。")
            println("这可能是因为：")
            println("1. Claude 检测到是程序调用而非真实用户")
            println("2. PTY 设置不完全模拟真实终端")
            println("3. 需要特定的认证或初始化流程")
            
        } finally {
            collectJob.cancel()
            session.stop()
        }
    }
}