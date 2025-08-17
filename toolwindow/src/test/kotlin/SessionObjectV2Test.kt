package com.claudecodeplus.ui.models

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * SessionObjectV2 单元测试
 * 验证重构后的会话对象功能
 */
class SessionObjectV2Test {
    
    private lateinit var sessionObject: SessionObjectV2
    private val testProject = Project(
        id = "test-project",
        name = "Test Project",
        path = "/test/path",
        lastAccessedAt = "2024-01-01T00:00:00Z"
    )
    
    @BeforeEach
    fun setUp() {
        // 创建测试用的SessionObjectV2
        sessionObject = SessionObjectV2(
            initialSessionId = null,
            initialMessages = emptyList(),
            initialModel = AiModel.SONNET,
            initialPermissionMode = PermissionMode.DEFAULT,
            initialSkipPermissions = false,
            project = testProject
        )
    }
    
    @Test
    fun `测试初始状态`() {
        // 验证初始状态
        assertNull(sessionObject.sessionId)
        assertTrue(sessionObject.isNewSession)
        assertFalse(sessionObject.hasSessionId)
        assertTrue(sessionObject.messages.isEmpty())
        assertFalse(sessionObject.isGenerating)
        assertNull(sessionObject.errorMessage)
        assertTrue(sessionObject.contexts.isEmpty())
        assertTrue(sessionObject.questionQueue.isEmpty())
        assertEquals("", sessionObject.inputText)
        assertEquals(AiModel.SONNET, sessionObject.selectedModel)
        assertEquals(PermissionMode.DEFAULT, sessionObject.selectedPermissionMode)
        assertFalse(sessionObject.skipPermissions)
    }
    
    @Test
    fun `测试消息管理`() {
        val testMessage = EnhancedMessage(
            id = "test-1",
            role = MessageRole.USER,
            content = "Hello, world!",
            timestamp = System.currentTimeMillis()
        )
        
        // 添加消息
        sessionObject.addMessage(testMessage)
        assertEquals(1, sessionObject.messages.size)
        assertEquals(testMessage, sessionObject.messages.first())
        
        // 避免重复添加
        sessionObject.addMessage(testMessage)
        assertEquals(1, sessionObject.messages.size)
        
        // 更新最后一条消息
        sessionObject.updateLastMessage { it.copy(content = "Updated content") }
        assertEquals("Updated content", sessionObject.messages.first().content)
        
        // 替换指定消息
        sessionObject.replaceMessage("test-1") { it.copy(content = "Replaced content") }
        assertEquals("Replaced content", sessionObject.messages.first().content)
    }
    
    @Test
    fun `测试上下文管理`() {
        val context1 = ContextReference.FileReference("/test/file1.txt", "/test/file1.txt")
        val context2 = ContextReference.FileReference("/test/file2.txt", "/test/file2.txt")
        
        // 添加上下文
        sessionObject.addContext(context1)
        assertEquals(1, sessionObject.contexts.size)
        
        sessionObject.addContext(context2)
        assertEquals(2, sessionObject.contexts.size)
        
        // 移除上下文
        sessionObject.removeContext(context1)
        assertEquals(1, sessionObject.contexts.size)
        assertEquals(context2, sessionObject.contexts.first())
        
        // 清空上下文
        sessionObject.clearContexts()
        assertTrue(sessionObject.contexts.isEmpty())
    }
    
    @Test
    fun `测试队列管理`() {
        assertFalse(sessionObject.hasQueuedQuestions)
        assertEquals(0, sessionObject.queueSize)
        
        // 添加到队列
        sessionObject.addToQueue("Question 1")
        sessionObject.addToQueue("Question 2")
        
        assertTrue(sessionObject.hasQueuedQuestions)
        assertEquals(2, sessionObject.queueSize)
        
        // 从队列获取
        assertEquals("Question 1", sessionObject.getNextFromQueue())
        assertEquals(1, sessionObject.queueSize)
        
        assertEquals("Question 2", sessionObject.getNextFromQueue())
        assertEquals(0, sessionObject.queueSize)
        
        assertNull(sessionObject.getNextFromQueue())
        
        // 清空队列
        sessionObject.addToQueue("Question 3")
        sessionObject.clearQueue()
        assertEquals(0, sessionObject.queueSize)
    }
    
    @Test
    fun `测试输入管理`() {
        // 测试输入文本
        sessionObject.inputText = "Hello"
        assertEquals("Hello", sessionObject.inputText)
        assertEquals("Hello", sessionObject.inputTextFieldValue.text)
        
        // 清空输入
        sessionObject.clearInput()
        assertEquals("", sessionObject.inputText)
        assertFalse(sessionObject.showContextSelector)
        assertNull(sessionObject.atSymbolPosition)
    }
    
    @Test
    fun `测试工具调用管理`() {
        val toolCall = ToolCall(
            id = "tool-1",
            name = "TestTool",
            parameters = mapOf("param1" to "value1"),
            status = ToolCallStatus.RUNNING,
            result = null,
            startTime = System.currentTimeMillis(),
            endTime = null
        )
        
        // 添加工具调用
        sessionObject.addRunningToolCall(toolCall)
        assertEquals(1, sessionObject.runningToolCallsCount)
        assertTrue(sessionObject.hasRunningToolCalls)
        
        // 更新工具调用状态
        sessionObject.updateToolCallStatus(
            "tool-1", 
            ToolCallStatus.SUCCESS, 
            ToolResult.Success("Success result")
        )
        assertEquals(0, sessionObject.runningToolCallsCount) // 成功后自动移除
        assertFalse(sessionObject.hasRunningToolCalls)
    }
    
    @Test
    fun `测试状态保存和恢复`() {
        // 设置一些状态
        sessionObject.inputText = "Test input"
        sessionObject.selectedModel = AiModel.OPUS
        sessionObject.addContext(ContextReference.FileReference("/test.txt", "/test.txt"))
        
        val testMessage = EnhancedMessage(
            id = "msg-1",
            role = MessageRole.USER,
            content = "Test message",
            timestamp = System.currentTimeMillis()
        )
        sessionObject.addMessage(testMessage)
        
        // 保存状态
        val savedState = sessionObject.saveSessionState()
        
        // 清空当前状态
        sessionObject.clearSession()
        assertEquals("", sessionObject.inputText)
        assertTrue(sessionObject.messages.isEmpty())
        assertTrue(sessionObject.contexts.isEmpty())
        
        // 恢复状态
        sessionObject.restoreSessionState(savedState)
        assertEquals("Test input", sessionObject.inputText)
        assertEquals(AiModel.OPUS, sessionObject.selectedModel)
        assertEquals(1, sessionObject.contexts.size)
        assertEquals(1, sessionObject.messages.size)
        assertEquals(testMessage.id, sessionObject.messages.first().id)
    }
    
    @Test
    fun `测试会话清空`() {
        // 设置一些状态
        sessionObject.addMessage(EnhancedMessage(
            id = "msg-1",
            role = MessageRole.USER,
            content = "Test",
            timestamp = System.currentTimeMillis()
        ))
        sessionObject.addContext(ContextReference.FileReference("/test.txt", "/test.txt"))
        sessionObject.addToQueue("Question")
        sessionObject.inputText = "Input"
        
        // 清空会话
        sessionObject.clearSession()
        
        // 验证所有状态都被重置
        assertNull(sessionObject.sessionId)
        assertTrue(sessionObject.messages.isEmpty())
        assertTrue(sessionObject.contexts.isEmpty())
        assertTrue(sessionObject.questionQueue.isEmpty())
        assertEquals("", sessionObject.inputText)
        assertFalse(sessionObject.isGenerating)
        assertNull(sessionObject.errorMessage)
        assertTrue(sessionObject.isFirstMessage)
        assertEquals(MessageLoadingState.IDLE, sessionObject.messageLoadingState)
        assertEquals(0f, sessionObject.scrollPosition)
    }
    
    @Test
    fun `测试项目工作目录`() {
        assertEquals("/test/path", sessionObject.getProjectCwd())
    }
    
    @Test
    fun `测试生命周期清理`() {
        // 这个测试主要验证dispose方法不会抛异常
        assertDoesNotThrow {
            sessionObject.dispose()
        }
    }
    
    @Test
    fun `测试toString方法`() {
        val toString = sessionObject.toString()
        assertTrue(toString.contains("SessionObjectV2"))
        assertTrue(toString.contains("sessionId=null"))
        assertTrue(toString.contains("messages=0"))
        assertTrue(toString.contains("isGenerating=false"))
        assertTrue(toString.contains("queue=0"))
    }
}