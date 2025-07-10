package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.Duration

/**
 * 会话状态管理器 - 处理中断和恢复
 */
class ChatSessionStateManager {
    private val _interruptedSessions = MutableStateFlow<Map<String, InterruptedSession>>(emptyMap())
    val interruptedSessions: StateFlow<Map<String, InterruptedSession>> = _interruptedSessions.asStateFlow()
    
    private val _sessionStates = mutableMapOf<String, SessionState>()
    
    /**
     * 保存中断的会话
     */
    fun saveInterruptedSession(
        sessionId: String,
        chatTabId: String,
        lastMessageId: String,
        pendingInput: String?,
        context: List<ContextItem>,
        reason: InterruptedSession.InterruptReason
    ) {
        val interrupted = InterruptedSession(
            sessionId = sessionId,
            chatTabId = chatTabId,
            lastMessageId = lastMessageId,
            pendingInput = pendingInput,
            context = context,
            timestamp = Instant.now(),
            reason = reason
        )
        
        _interruptedSessions.value = _interruptedSessions.value + (sessionId to interrupted)
        
        // 更新会话状态
        _sessionStates[sessionId] = SessionState.INTERRUPTED
    }
    
    /**
     * 恢复中断的会话
     */
    fun resumeSession(sessionId: String): InterruptedSession? {
        val session = _interruptedSessions.value[sessionId]
        if (session != null) {
            _interruptedSessions.value = _interruptedSessions.value - sessionId
            _sessionStates[sessionId] = SessionState.ACTIVE
        }
        return session
    }
    
    /**
     * 检查会话是否可以恢复
     */
    fun canResumeSession(sessionId: String): Boolean {
        val session = _interruptedSessions.value[sessionId] ?: return false
        
        // 检查是否超时（默认24小时）
        val elapsed = Duration.between(session.timestamp, Instant.now())
        return elapsed.toHours() < 24
    }
    
    /**
     * 获取会话状态
     */
    fun getSessionState(sessionId: String): SessionState {
        return _sessionStates[sessionId] ?: SessionState.NEW
    }
    
    /**
     * 更新会话状态
     */
    fun updateSessionState(sessionId: String, state: SessionState) {
        _sessionStates[sessionId] = state
    }
    
    /**
     * 清理过期的中断会话
     */
    fun cleanupExpiredSessions(maxAge: Duration = Duration.ofDays(7)) {
        val now = Instant.now()
        _interruptedSessions.value = _interruptedSessions.value.filterValues { session ->
            Duration.between(session.timestamp, now) < maxAge
        }
    }
    
    /**
     * 获取所有中断的会话
     */
    fun getAllInterruptedSessions(): List<InterruptedSession> {
        return _interruptedSessions.value.values.toList()
    }
    
    /**
     * 删除中断的会话
     */
    fun deleteInterruptedSession(sessionId: String) {
        _interruptedSessions.value = _interruptedSessions.value - sessionId
        _sessionStates.remove(sessionId)
    }
    
    /**
     * 保存会话快照（用于自动保存）
     */
    fun saveSessionSnapshot(
        sessionId: String,
        chatTabId: String,
        messages: List<ChatMessage>,
        context: List<ContextItem>,
        currentInput: String?
    ) {
        // 保存到临时存储，以便在意外退出时恢复
        val snapshot = SessionSnapshot(
            sessionId = sessionId,
            chatTabId = chatTabId,
            messages = messages,
            context = context,
            currentInput = currentInput,
            timestamp = Instant.now()
        )
        
        // TODO: 持久化到本地存储
    }
    
    /**
     * 会话状态
     */
    enum class SessionState {
        NEW,
        ACTIVE,
        INTERRUPTED,
        COMPLETED,
        ERROR
    }
    
    /**
     * 会话快照
     */
    data class SessionSnapshot(
        val sessionId: String,
        val chatTabId: String,
        val messages: List<ChatMessage>,
        val context: List<ContextItem>,
        val currentInput: String?,
        val timestamp: Instant
    )
}