package com.claudecodeplus.ui.services

import kotlin.test.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import com.claudecodeplus.ui.models.MessageRole
import com.claudecodeplus.ui.models.MessageTimelineItem

class SessionLoaderTest {
    
    private val sessionHistoryService = SessionHistoryService()
    private val messageProcessor = MessageProcessor()
    private val sessionLoader = SessionLoader(sessionHistoryService, messageProcessor)
    
    @Test
    fun `test loading session with tool calls using flow`() = runBlocking {
        // 获取测试资源文件
        val testResource = this::class.java.getResource("/test-session.jsonl")
        assertNotNull(testResource, "Test resource should exist")
        
        val tempFile = Files.createTempFile("test-session", ".jsonl").toFile()
        try {
            // 复制测试文件到临时位置
            testResource.openStream().use { input ->
                Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            
            // 使用流式加载
            val results = sessionLoader.loadSessionAsMessageFlow(tempFile, maxMessages = 50).toList()
            
            // 验证结果
            assertTrue(results.isNotEmpty(), "Should have results")
            
            // 查找完成的消息
            val completedMessages = results.filterIsInstance<SessionLoader.LoadResult.MessageCompleted>()
            assertTrue(completedMessages.isNotEmpty(), "Should have completed messages")
            
            // 验证消息内容
            val userMessages = completedMessages.filter { it.message.role == MessageRole.USER }
            val assistantMessages = completedMessages.filter { it.message.role == MessageRole.ASSISTANT }
            
            println("Loaded messages:")
            println("  User messages: ${userMessages.size}")
            println("  Assistant messages: ${assistantMessages.size}")
            
            // 验证第一条用户消息
            val firstUserMessage = userMessages.firstOrNull()?.message
            assertNotNull(firstUserMessage)
            assertEquals("理解当前项目", firstUserMessage.content)
            
            // 验证助手消息包含工具调用
            val messagesWithTools = assistantMessages.filter { it.message.toolCalls.isNotEmpty() }
            assertTrue(messagesWithTools.isNotEmpty(), "Should have messages with tool calls")
            
            // 详细检查工具调用消息
            messagesWithTools.forEach { result ->
                val msg = result.message
                println("\nAssistant message with tools:")
                println("  ID: ${msg.id}")
                println("  Content: ${msg.content.take(100)}...")
                println("  Tool calls: ${msg.toolCalls.size}")
                println("  Tool names: ${msg.toolCalls.map { it.name }}")
                println("  Ordered elements: ${msg.orderedElements.size}")
                
                // 验证 orderedElements 正确构建
                assertTrue(msg.orderedElements.isNotEmpty(), "Should have ordered elements")
                
                // 验证包含工具调用元素
                val toolCallItems = msg.orderedElements.filterIsInstance<MessageTimelineItem.ToolCallItem>()
                assertTrue(toolCallItems.isNotEmpty(), "Should have tool call items in ordered elements")
                
                // 验证工具调用元素与实际工具调用匹配
                assertEquals(msg.toolCalls.size, toolCallItems.size, "Tool call count should match")
            }
            
            // 验证 TodoWrite 工具调用
            val todoWriteMessage = messagesWithTools.find { result ->
                result.message.toolCalls.any { it.name == "TodoWrite" }
            }
            assertNotNull(todoWriteMessage, "Should find TodoWrite tool call")
            
            val todoWriteCall = todoWriteMessage.message.toolCalls.first { it.name == "TodoWrite" }
            assertEquals("TodoWrite", todoWriteCall.name)
            assertTrue(todoWriteCall.parameters.containsKey("todos"), "Should have todos parameter")
            
            // 验证加载完成
            val loadComplete = results.filterIsInstance<SessionLoader.LoadResult.LoadComplete>().firstOrNull()
            assertNotNull(loadComplete, "Should have load complete result")
            
        } finally {
            tempFile.delete()
        }
    }
    
    @Test
    fun `test message processing flow matches real-time processing`() = runBlocking {
        val testResource = this::class.java.getResource("/test-session.jsonl")
        assertNotNull(testResource, "Test resource should exist")
        
        val tempFile = Files.createTempFile("test-session", ".jsonl").toFile()
        try {
            testResource.openStream().use { input ->
                Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            
            // 收集所有消息更新
            val allUpdates = mutableListOf<SessionLoader.LoadResult>()
            sessionLoader.loadSessionAsMessageFlow(tempFile, maxMessages = 10)
                .collect { result ->
                    allUpdates.add(result)
                }
            
            // 验证消息更新流程
            val messageUpdates = allUpdates.filterIsInstance<SessionLoader.LoadResult.MessageUpdated>()
            val messageCompletions = allUpdates.filterIsInstance<SessionLoader.LoadResult.MessageCompleted>()
            
            println("\nMessage processing flow:")
            println("  Total updates: ${allUpdates.size}")
            println("  Message updates: ${messageUpdates.size}")
            println("  Message completions: ${messageCompletions.size}")
            
            // 验证每条助手消息都有更新和完成事件
            val assistantCompletions = messageCompletions.filter { 
                it.message.role == MessageRole.ASSISTANT 
            }
            
            assistantCompletions.forEach { completion ->
                println("\nAssistant message completion:")
                println("  Has content: ${completion.message.content.isNotEmpty()}")
                println("  Has tool calls: ${completion.message.toolCalls.isNotEmpty()}")
                println("  Has ordered elements: ${completion.message.orderedElements.isNotEmpty()}")
                
                // 每条包含工具调用的消息都应该有正确的 orderedElements
                if (completion.message.toolCalls.isNotEmpty()) {
                    assertTrue(
                        completion.message.orderedElements.isNotEmpty(),
                        "Message with tool calls should have ordered elements"
                    )
                }
            }
            
        } finally {
            tempFile.delete()
        }
    }
}