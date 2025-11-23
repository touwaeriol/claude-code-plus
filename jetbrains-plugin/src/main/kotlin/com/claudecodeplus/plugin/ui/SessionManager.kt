package com.claudecodeplus.plugin.ui

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

/**
 * 会话管理器
 * 
 * 负责管理多个聊天会话，支持创建、删除、切换会话
 * 会话状态会持久化到项目配置中
 */
@State(
    name = "ClaudeCodePlusSessions",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
@Service(Service.Level.PROJECT)
class SessionManager : PersistentStateComponent<SessionManager.State> {
    
    @Serializable
    data class State(
        var sessions: MutableList<SessionInfo> = mutableListOf(),
        var currentSessionId: String? = null
    )
    
    @Serializable
    data class SessionInfo(
        val id: String,
        val name: String,
        val createdAt: Long,
        val lastActiveAt: Long,
        val messageCount: Int = 0
    )
    
    private var state = State()
    
    /**
     * 获取所有会话
     */
    fun getAllSessions(): List<SessionInfo> {
        return state.sessions.toList()
    }
    
    /**
     * 获取当前会话ID
     */
    fun getCurrentSessionId(): String? {
        return state.currentSessionId
    }
    
    /**
     * 获取当前会话信息
     */
    fun getCurrentSession(): SessionInfo? {
        return state.currentSessionId?.let { id ->
            state.sessions.find { it.id == id }
        }
    }
    
    /**
     * 创建新会话
     */
    fun createSession(name: String? = null): SessionInfo {
        val sessionId = UUID.randomUUID().toString()
        val sessionName = name ?: "Session ${state.sessions.size + 1}"
        val now = System.currentTimeMillis()
        
        val session = SessionInfo(
            id = sessionId,
            name = sessionName,
            createdAt = now,
            lastActiveAt = now,
            messageCount = 0
        )
        
        state.sessions.add(session)
        state.currentSessionId = sessionId
        
        return session
    }
    
    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String): Boolean {
        val removed = state.sessions.removeAll { it.id == sessionId }
        
        // 如果删除的是当前会话，切换到其他会话
        if (state.currentSessionId == sessionId) {
            state.currentSessionId = state.sessions.firstOrNull()?.id
        }
        
        return removed
    }
    
    /**
     * 切换会话
     */
    fun switchSession(sessionId: String): Boolean {
        val session = state.sessions.find { it.id == sessionId }
        return if (session != null) {
            state.currentSessionId = sessionId
            updateSessionActivity(sessionId)
            true
        } else {
            false
        }
    }
    
    /**
     * 更新会话活动时间
     */
    fun updateSessionActivity(sessionId: String) {
        val session = state.sessions.find { it.id == sessionId }
        if (session != null) {
            val index = state.sessions.indexOf(session)
            state.sessions[index] = session.copy(lastActiveAt = System.currentTimeMillis())
        }
    }
    
    /**
     * 增加会话消息计数
     */
    fun incrementMessageCount(sessionId: String) {
        val session = state.sessions.find { it.id == sessionId }
        if (session != null) {
            val index = state.sessions.indexOf(session)
            state.sessions[index] = session.copy(
                messageCount = session.messageCount + 1,
                lastActiveAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * 设置会话消息计数
     */
    fun setMessageCount(sessionId: String, count: Int) {
        val session = state.sessions.find { it.id == sessionId }
        if (session != null) {
            val index = state.sessions.indexOf(session)
            state.sessions[index] = session.copy(
                messageCount = count,
                lastActiveAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * 重命名会话
     */
    fun renameSession(sessionId: String, newName: String): Boolean {
        val session = state.sessions.find { it.id == sessionId }
        return if (session != null) {
            val index = state.sessions.indexOf(session)
            state.sessions[index] = session.copy(name = newName)
            true
        } else {
            false
        }
    }
    
    override fun getState(): State {
        return state
    }
    
    override fun loadState(state: State) {
        this.state = state
        
        // 如果没有会话，创建一个默认会话
        if (state.sessions.isEmpty()) {
            createSession("Default Session")
        }
        
        // 如果当前会话ID无效，切换到第一个会话
        if (state.currentSessionId != null && 
            state.sessions.none { it.id == state.currentSessionId }) {
            state.currentSessionId = state.sessions.firstOrNull()?.id
        }
    }
    
    companion object {
        /**
         * 获取项目的会话管理器实例
         */
        fun getInstance(project: Project): SessionManager {
            return project.getService(SessionManager::class.java)
        }
    }
}

