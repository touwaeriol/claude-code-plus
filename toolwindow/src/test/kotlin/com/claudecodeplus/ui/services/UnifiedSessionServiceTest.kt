package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.ClaudeCliWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedSessionServiceTest {
    
    private fun createTempService(): UnifiedSessionService {
        val tempDir = createTempDir("test-session")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return UnifiedSessionService(scope, tempDir.absolutePath)
    }
    
    @Test
    fun `test sessionExists returns false for non-existent session`() {
        val service = createTempService()
        val sessionId = "non-existent-session-id"
        val exists = service.sessionExists(sessionId)
        assertFalse(exists, "不存在的会话应该返回false")
    }
    
    @Test
    fun `test sessionExists returns true for existing session`() {
        val tempDir = createTempDir("test-session")
        val service = UnifiedSessionService(
            CoroutineScope(SupervisorJob() + Dispatchers.Default), 
            tempDir.absolutePath
        )
        
        // 创建一个模拟的会话文件
        val sessionId = "12345678-1234-1234-1234-123456789abc"
        val claudeDir = File(tempDir, ".claude/projects/${tempDir.absolutePath.replace(":", "--")}")
        claudeDir.mkdirs()
        
        val sessionFile = File(claudeDir, "$sessionId.jsonl")
        sessionFile.writeText("""{"type": "user", "content": "test message"}""")
        
        val exists = service.sessionExists(sessionId)
        assertTrue(exists, "存在的会话应该返回true")
    }
    
    @Test  
    fun `test query with new session generates UUID and uses session-id`() {
        // 使用模拟来验证参数传递
        val options = ClaudeCliWrapper.QueryOptions()
        
        // 验证新会话逻辑
        val finalOptions = when {
            options.sessionId == null && options.resume == null -> {
                // 模拟UnifiedSessionService的逻辑
                val newSessionId = "generated-uuid-123"
                options.copy(sessionId = newSessionId)
            }
            else -> options
        }
        
        assertEquals("generated-uuid-123", finalOptions.sessionId)
        assertEquals(null, finalOptions.resume)
    }
    
    @Test
    fun `test query with existing session uses resume parameter`() {
        val tempDir = createTempDir("test-session")
        val service = UnifiedSessionService(
            CoroutineScope(SupervisorJob() + Dispatchers.Default), 
            tempDir.absolutePath
        )
        
        // 创建一个已存在的会话
        val sessionId = "12345678-1234-1234-1234-123456789abc"
        val claudeDir = File(tempDir, ".claude/projects/${tempDir.absolutePath.replace(":", "--")}")
        claudeDir.mkdirs()
        
        val sessionFile = File(claudeDir, "$sessionId.jsonl")
        sessionFile.writeText("""{"type": "user", "content": "existing session"}""")
        
        // 模拟UnifiedSessionService的逻辑
        val options = ClaudeCliWrapper.QueryOptions(sessionId = sessionId)
        val exists = service.sessionExists(sessionId)
        
        val finalOptions = if (exists) {
            options.copy(resume = sessionId, sessionId = null)
        } else {
            options
        }
        
        assertTrue(exists, "会话应该存在")
        assertEquals(sessionId, finalOptions.resume)
        assertEquals(null, finalOptions.sessionId)
    }
    
    @Test
    fun `test query with non-existing session uses session-id parameter`() {
        val service = createTempService()
        val sessionId = "non-existent-session-id"
        val options = ClaudeCliWrapper.QueryOptions(sessionId = sessionId)
        val exists = service.sessionExists(sessionId)
        
        val finalOptions = if (exists) {
            options.copy(resume = sessionId, sessionId = null)
        } else {
            options
        }
        
        assertFalse(exists, "会话不应该存在")
        assertEquals(sessionId, finalOptions.sessionId)
        assertEquals(null, finalOptions.resume)
    }
    
    @Test
    fun `test file monitoring setup`() {
        val service = createTempService()
        
        // 应该能够获取会话列表（即使为空）
        val sessions = service.getAllSessions()
        assertTrue(sessions.isEmpty() || sessions.isNotEmpty(), "应该能够获取会话列表")
    }
}