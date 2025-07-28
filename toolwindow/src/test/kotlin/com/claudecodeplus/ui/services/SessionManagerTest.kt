package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.PermissionMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class SessionManagerTest {
    private lateinit var sessionManager: SessionManager
    
    @BeforeEach
    fun setup() {
        sessionManager = SessionManager()
    }
    
    @Test
    fun `test session creation with default config`() {
        // 创建会话，使用默认配置
        val session = sessionManager.getOrCreateSession("tab1")
        
        // 验证使用了默认配置
        assertEquals(DefaultSessionConfig.defaultModel, session.selectedModel)
        assertEquals(DefaultSessionConfig.defaultPermissionMode, session.selectedPermissionMode)
        assertEquals(DefaultSessionConfig.defaultSkipPermissions, session.skipPermissions)
    }
    
    @Test
    fun `test session creation with custom config`() {
        // 创建会话，使用自定义配置
        val session = sessionManager.getOrCreateSession(
            tabId = "tab2",
            initialModel = AiModel.SONNET,
            initialPermissionMode = PermissionMode.ACCEPT_EDITS,
            initialSkipPermissions = false
        )
        
        // 验证使用了自定义配置
        assertEquals(AiModel.SONNET, session.selectedModel)
        assertEquals(PermissionMode.ACCEPT_EDITS, session.selectedPermissionMode)
        assertFalse(session.skipPermissions)
    }
    
    @Test
    fun `test session switching preserves state`() {
        // 创建两个会话
        val session1 = sessionManager.getOrCreateSession("tab1")
        val session2 = sessionManager.getOrCreateSession("tab2")
        
        // 设置不同的配置
        session1.selectedModel = AiModel.SONNET
        session1.skipPermissions = false
        session1.addToQueue("Question 1")
        
        session2.selectedModel = AiModel.OPUS  
        session2.skipPermissions = true
        session2.addToQueue("Question 2")
        session2.addToQueue("Question 3")
        
        // 切换会话并验证状态保持
        sessionManager.setActiveSession("tab1")
        val activeSession1 = sessionManager.getActiveSession()
        assertEquals(AiModel.SONNET, activeSession1?.selectedModel)
        assertFalse(activeSession1?.skipPermissions ?: true)
        assertEquals(1, activeSession1?.queueSize)
        
        sessionManager.setActiveSession("tab2")
        val activeSession2 = sessionManager.getActiveSession()
        assertEquals(AiModel.OPUS, activeSession2?.selectedModel)
        assertTrue(activeSession2?.skipPermissions ?: false)
        assertEquals(2, activeSession2?.queueSize)
    }
    
    @Test
    fun `test concurrent generation sessions`() {
        // 创建多个会话
        val session1 = sessionManager.getOrCreateSession("tab1")
        val session2 = sessionManager.getOrCreateSession("tab2")
        val session3 = sessionManager.getOrCreateSession("tab3")
        
        // 模拟生成状态
        session1.isGenerating = true
        session2.isGenerating = false
        session3.isGenerating = true
        
        // 验证正在生成的会话
        val generatingSessions = sessionManager.getGeneratingSessions()
        assertEquals(2, generatingSessions.size)
        assertTrue(generatingSessions.any { it.first == "tab1" })
        assertTrue(generatingSessions.any { it.first == "tab3" })
        assertFalse(generatingSessions.any { it.first == "tab2" })
    }
    
    @Test
    fun `test session stats`() {
        // 创建多个会话并设置状态
        val session1 = sessionManager.getOrCreateSession("tab1")
        val session2 = sessionManager.getOrCreateSession("tab2")
        val session3 = sessionManager.getOrCreateSession("tab3")
        
        session1.isGenerating = true
        session1.addToQueue("Q1")
        session1.addToQueue("Q2")
        
        session2.addToQueue("Q3")
        
        session3.isGenerating = true
        
        // 获取统计信息
        val stats = sessionManager.getSessionStats()
        
        assertEquals(3, stats.totalSessions)
        assertEquals(2, stats.generatingSessions)
        assertEquals(2, stats.sessionsWithQueue)
        assertEquals(3, stats.totalQueuedQuestions)
    }
}