package com.claudecodeplus.test

import com.claudecodeplus.ui.services.SessionHistoryService
import com.claudecodeplus.ui.services.SessionLoader
import com.claudecodeplus.ui.services.MessageProcessor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() {
    runBlocking {
        println("=== 测试历史会话加载 ===")
        
        val sessionHistoryService = SessionHistoryService()
        val messageProcessor = MessageProcessor()
        val sessionLoader = SessionLoader(sessionHistoryService, messageProcessor)
        
        // 找到最新的会话文件
        val projectPath = "C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop"
        val sessionFile = sessionHistoryService.getLatestSessionFile(projectPath)
        
        if (sessionFile != null) {
            println("找到会话文件: ${sessionFile.name}")
            println("文件大小: ${sessionFile.length()} bytes")
            println("\n开始加载会话...\n")
            
            var messageCount = 0
            sessionLoader.loadSessionAsMessageFlow(sessionFile, maxMessages = 10)
                .collect { result ->
                    when (result) {
                        is SessionLoader.LoadResult.MessageCompleted -> {
                            messageCount++
                            val msg = result.message
                            println("消息 #$messageCount:")
                            println("  角色: ${msg.role}")
                            println("  内容: ${msg.content.take(100)}${if (msg.content.length > 100) "..." else ""}")
                            println("  模型: ${msg.model?.displayName ?: "无"}")
                            println("  工具调用数: ${msg.toolCalls.size}")
                            println("  有序元素数: ${msg.orderedElements.size}")
                            msg.orderedElements.forEach { element ->
                                println("    - $element")
                            }
                            println()
                        }
                        is SessionLoader.LoadResult.MessageUpdated -> {
                            // 忽略更新消息
                        }
                        is SessionLoader.LoadResult.LoadComplete -> {
                            println("加载完成！共加载 ${result.messages.size} 条消息")
                        }
                        is SessionLoader.LoadResult.Error -> {
                            println("加载错误: ${result.error}")
                        }
                    }
                }
        } else {
            println("未找到会话文件")
            
            // 列出所有可用的会话文件
            val sessionFiles = sessionHistoryService.getSessionFiles(projectPath)
            println("\n可用的会话文件:")
            sessionFiles.forEach { fileInfo ->
                println("  - ${fileInfo.name} (${fileInfo.size} bytes)")
            }
        }
    }
}