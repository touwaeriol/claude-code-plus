package com.claudecodeplus.ui.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SessionExistsIntegrationTest {
    
    @Test
    fun `test sessionExists with real desktop project`() {
        val desktopPath = "C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val service = UnifiedSessionService(scope, desktopPath)
        
        // 测试真实存在的会话ID
        val existingSessionId = "06db5254-8e25-4bd0-b17d-6b0e755d9676"
        val exists = service.sessionExists(existingSessionId)
        
        println("测试路径: $desktopPath")
        println("会话ID: $existingSessionId")
        println("是否存在: $exists")
        
        assertTrue(exists, "真实的会话ID应该存在")
    }
    
    @Test  
    fun `test sessionExists with non-existing session`() {
        val desktopPath = "C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val service = UnifiedSessionService(scope, desktopPath)
        
        // 测试不存在的会话ID
        val nonExistingSessionId = "00000000-0000-0000-0000-000000000000"
        val exists = service.sessionExists(nonExistingSessionId)
        
        println("测试路径: $desktopPath")
        println("会话ID: $nonExistingSessionId")
        println("是否存在: $exists")
        
        assertFalse(exists, "不存在的会话ID应该返回false")
    }
}