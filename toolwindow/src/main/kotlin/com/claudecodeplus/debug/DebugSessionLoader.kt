package com.claudecodeplus.debug

import com.claudecodeplus.sdk.parseClaudeMessage
import com.claudecodeplus.ui.services.SessionHistoryService
import java.io.File

fun main() {
    println("=== 调试会话加载 ===")
    
    val sessionHistoryService = SessionHistoryService()
    val projectPath = "C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop"
    
    // 获取最新的会话文件
    val sessionFile = sessionHistoryService.getLatestSessionFile(projectPath)
    
    if (sessionFile != null) {
        println("会话文件: ${sessionFile.absolutePath}")
        println("\n分析前10行：\n")
        
        sessionFile.readLines().take(10).forEachIndexed { index, line ->
            println("行 ${index + 1}:")
            println("原始内容: ${line.take(200)}...")
            
            val message = parseClaudeMessage(line)
            if (message != null) {
                println("解析成功: ${message::class.simpleName}")
                when (message) {
                    is com.claudecodeplus.sdk.UserMessage -> {
                        println("  - UUID: ${message.uuid}")
                        println("  - SessionId: ${message.sessionId}")
                        println("  - Message: ${message.message}")
                        val content = message.message?.content
                        println("  - Content类型: ${content?.javaClass?.simpleName}")
                    }
                    is com.claudecodeplus.sdk.AssistantMessage -> {
                        println("  - UUID: ${message.uuid}")
                        println("  - SessionId: ${message.sessionId}")
                        println("  - Model: ${message.message?.model}")
                        println("  - Content: ${message.message?.content?.size} 个块")
                        message.message?.content?.forEach { block ->
                            println("    - ${block::class.simpleName}")
                        }
                    }
                    is com.claudecodeplus.sdk.SummaryMessage -> {
                        println("  - Summary: ${message.summary}")
                    }
                    else -> {
                        println("  - 其他类型: $message")
                    }
                }
            } else {
                println("解析失败!")
            }
            println("-".repeat(80))
        }
    } else {
        println("未找到会话文件")
    }
}