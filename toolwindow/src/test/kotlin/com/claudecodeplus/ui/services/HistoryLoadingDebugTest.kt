package com.claudecodeplus.ui.services

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 调试历史加载问题的简单测试程序
 */
class HistoryLoadingDebugTest {
    
    @Test
    fun debugHistoryLoading() = runBlocking {
    val sessionHistoryService = SessionHistoryService()
    val messageProcessor = MessageProcessor()
    val sessionLoader = SessionLoader(sessionHistoryService, messageProcessor)
    
    // 获取测试资源文件
    val testResource = SessionLoaderTest::class.java.getResource("/test-session.jsonl")
    if (testResource == null) {
        println("测试资源不存在")
        return@runBlocking
    }
    
    val tempFile = Files.createTempFile("test-session", ".jsonl").toFile()
    try {
        // 复制测试文件到临时位置
        testResource.openStream().use { input ->
            Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        
        println("=== 开始加载历史会话 ===")
        
        sessionLoader.loadSessionAsMessageFlow(tempFile, maxMessages = 50)
            .collect { result ->
                when (result) {
                    is SessionLoader.LoadResult.MessageCompleted -> {
                        val msg = result.message
                        println("\n完成消息:")
                        println("  角色: ${msg.role}")
                        println("  内容: ${msg.content.take(100)}${if (msg.content.length > 100) "..." else ""}")
                        println("  工具调用: ${msg.toolCalls.size}")
                        println("  orderedElements: ${msg.orderedElements.size}")
                        
                        if (msg.toolCalls.isNotEmpty()) {
                            println("  工具详情:")
                            msg.toolCalls.forEach { tool ->
                                println("    - ${tool.name} (ID: ${tool.id})")
                            }
                            
                            println("  orderedElements 详情:")
                            msg.orderedElements.forEach { element ->
                                when (element) {
                                    is com.claudecodeplus.ui.models.MessageTimelineItem.ContentItem -> {
                                        println("    - ContentItem: ${element.content.take(50)}...")
                                    }
                                    is com.claudecodeplus.ui.models.MessageTimelineItem.ToolCallItem -> {
                                        println("    - ToolCallItem: ${element.toolCall.name}")
                                    }
                                    is com.claudecodeplus.ui.models.MessageTimelineItem.StatusItem -> {
                                        println("    - StatusItem: ${element.status}")
                                    }
                                }
                            }
                        }
                    }
                    is SessionLoader.LoadResult.MessageUpdated -> {
                        // 消息更新，暂不打印
                    }
                    is SessionLoader.LoadResult.LoadComplete -> {
                        println("\n=== 加载完成，共 ${result.messages.size} 条消息 ===")
                    }
                    is SessionLoader.LoadResult.Error -> {
                        println("\n错误: ${result.error}")
                    }
                }
            }
        
        // 分析原始文件内容
        println("\n=== 原始文件分析 ===")
        val lines = tempFile.readLines()
        val assistantMessages = lines
            .filter { it.contains("\"type\":\"assistant\"") }
            .filter { it.contains("\"id\":\"msg_") }
        
        println("助手消息总数: ${assistantMessages.size}")
        
        // 按消息ID分组
        val messageGroups = mutableMapOf<String, MutableList<String>>()
        assistantMessages.forEach { line ->
            val idMatch = Regex("\"id\":\"(msg_[^\"]+)\"").find(line)
            if (idMatch != null) {
                val messageId = idMatch.groupValues[1]
                messageGroups.getOrPut(messageId) { mutableListOf() }.add(line)
            }
        }
        
        println("唯一消息ID数: ${messageGroups.size}")
        messageGroups.forEach { (id, lines) ->
            println("  $id: ${lines.size} 条记录")
            lines.forEachIndexed { index, line ->
                val hasText = line.contains("\"type\":\"text\"")
                val hasToolUse = line.contains("\"type\":\"tool_use\"")
                println("    ${index + 1}. 有文本: $hasText, 有工具调用: $hasToolUse")
            }
        }
        
    } finally {
        tempFile.delete()
    }
    }
}