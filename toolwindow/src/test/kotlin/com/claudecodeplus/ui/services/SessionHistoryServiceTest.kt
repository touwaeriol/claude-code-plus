package com.claudecodeplus.ui.services

import kotlin.test.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import com.claudecodeplus.sdk.MessageType

class SessionHistoryServiceTest {
    
    private val service = SessionHistoryService()
    
    
    @Test
    fun `test project sessions path calculation`() {
        // Windows 路径
        val windowsPath = "C:\\Users\\16790\\IdeaProjects\\claude-code-plus"
        val windowsSessionPath = service.getProjectSessionsPath(windowsPath)
        println("Windows session path: $windowsSessionPath")
        
        // Unix 路径
        val unixPath = "/home/user/projects/claude-code-plus"
        val unixSessionPath = service.getProjectSessionsPath(unixPath)
        println("Unix session path: $unixSessionPath")
        
        // 验证路径转换
        if (windowsSessionPath != null) {
            assertTrue(windowsSessionPath.toString().contains("C--Users-16790-IdeaProjects-claude-code-plus"))
        }
        // Unix 路径在 Windows 系统上可能为 null
        if (unixSessionPath != null) {
            assertTrue(unixSessionPath.toString().contains("-home-user-projects-claude-code-plus"))
        }
    }
    
    @Test
    fun `test loading session as flow`() = runBlocking {
        val testResource = this::class.java.getResource("/test-session.jsonl")
        assertNotNull(testResource, "Test resource should exist")
        
        val tempFile = Files.createTempFile("test-session", ".jsonl").toFile()
        try {
            // 复制测试文件
            testResource.openStream().use { input ->
                Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            
            // 收集流消息
            val messages = service.loadSessionHistoryAsFlow(tempFile).toList()
            
            assertTrue(messages.isNotEmpty(), "Should receive messages")
            
            // 统计消息类型
            val messageTypes = messages.groupBy { it.type }
                .mapValues { it.value.size }
            
            println("Message types: $messageTypes")
            
            // 验证消息类型
            assertTrue(messages.any { it.type == MessageType.START }, "Should have START messages")
            assertTrue(messages.any { it.type == MessageType.TEXT }, "Should have TEXT messages")
            assertTrue(messages.any { it.type == MessageType.TOOL_USE }, "Should have TOOL_USE messages")
            assertTrue(messages.any { it.type == MessageType.END }, "Should have END message")
            
        } finally {
            tempFile.delete()
        }
    }
    
}