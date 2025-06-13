package com.claudecodeplus.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * PTY 会话测试程序
 * 运行: ./gradlew run -PmainClass="com.claudecodeplus.core.TestPtySessionKt"
 */
fun main() = runBlocking {
    println("=== Claude PTY 会话测试 ===")
    println("使用 pty4j 创建真正的伪终端\n")
    
    val config = SessionConfig(
        command = listOf("claude"),
        initialColumns = 120,
        initialRows = 30
    )
    
    val session = ClaudeSession(config)
    
    // 收集并显示所有消息
    val outputJob = launch {
        session.messageFlow.collect { message ->
            when (message) {
                is SessionMessage.Input -> {
                    println("\n>>> ${message.text}")
                }
                is SessionMessage.Output -> {
                    print(message.text)
                }
                is SessionMessage.Error -> {
                    println("\n[错误] ${message.message}")
                }
                is SessionMessage.StateChanged -> {
                    println("\n[状态] ${message.state}")
                }
            }
        }
    }
    
    try {
        // 启动会话
        println("启动 PTY 会话...")
        session.start()
        
        // 等待会话就绪
        delay(2000)
        
        // 发送第一个问题
        println("\n发送问题: 你是什么模型")
        session.sendInput("你是什么模型\n")
        
        // 等待回复
        println("等待回复...")
        delay(10000)
        
        // 发送第二个问题
        println("\n\n发送问题: 2+2等于几")
        session.sendInput("2+2等于几\n")
        
        delay(5000)
        
        // 显示会话信息
        println("\n\n会话信息:")
        println("- 会话 ID: ${session.id}")
        println("- 进程 PID: ${session.getPid()}")
        println("- 进程存活: ${session.isAlive()}")
        
    } catch (e: Exception) {
        println("\n测试失败: ${e.message}")
        e.printStackTrace()
    } finally {
        outputJob.cancel()
        println("\n\n停止会话...")
        session.stop()
        delay(1000)
    }
    
    println("\n测试完成")
}