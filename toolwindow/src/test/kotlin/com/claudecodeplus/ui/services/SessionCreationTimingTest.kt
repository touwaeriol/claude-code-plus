package com.claudecodeplus.ui.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SessionCreationTimingTest {
    
    @Test
    fun `test session exists immediately after creation`() {
        val desktopPath = "C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val service = UnifiedSessionService(scope, desktopPath)
        
        // 测试一个可能刚创建的会话ID - 从日志中获取
        val sessionId = "d9db9782-b21c-40ab-a1a3-fb4f7435553c"
        val exists = service.sessionExists(sessionId)
        
        println("测试会话ID: $sessionId")
        println("是否存在: $exists")
        
        if (exists) {
            println("✅ 会话文件已存在")
        } else {
            println("❌ 会话文件不存在，这可能解释了为什么第二次调用仍使用--session-id")
        }
        
        // 不做断言，只是观察
    }
    
    @Test
    fun `test session detection for recent session`() {
        val desktopPath = "C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val service = UnifiedSessionService(scope, desktopPath)
        
        // 获取所有会话
        val sessions = service.getAllSessions()
        println("桌面项目的所有会话: ${sessions.size} 个")
        
        if (sessions.isNotEmpty()) {
            val lastSession = sessions.last()
            println("最新的会话ID: $lastSession")
            
            val exists = service.sessionExists(lastSession)
            println("最新会话是否存在: $exists")
            
            assertTrue(exists, "最新会话应该存在")
        }
    }
}