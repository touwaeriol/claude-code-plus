package com.claudecodeplus.plugin.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 项目级会话状态持久化服务
 * 
 * 为每个 IntelliJ 项目独立管理 Claude 会话状态，确保不同项目之间的会话完全隔离。
 * 会话配置存储在项目的 .idea/claude-session-state.xml 文件中。
 * 
 * 主要功能：
 * - 记录当前活动的会话 ID
 * - 维护会话历史记录（最近 10 个会话）
 * - 提供新会话创建时的状态清理
 * - 支持会话 ID 的实时更新（--resume 后）
 * 
 * 重要特性：
 * - 项目级隔离：每个项目有独立的配置文件
 * - 自动持久化：IDE 自动处理保存/加载
 * - 历史追踪：便于调试和会话管理
 */
@State(
    name = "ClaudeCodePlusProjectSessionState",
    storages = [Storage("claude-session-state.xml")]  // 存储在 .idea/claude-session-state.xml
)
@Service(Service.Level.PROJECT)
class ProjectSessionStateService : PersistentStateComponent<ProjectSessionStateService.State> {
    
    /**
     * 持久化状态数据结构
     */
    data class State(
        /**
         * 当前活动会话的 ID
         * - 插件启动时为 null（创建新会话）
         * - --resume 后更新为 Claude CLI 返回的新 sessionId
         */
        var currentSessionId: String? = null,
        
        /**
         * 最后更新时间戳
         */
        var lastUpdated: Long = 0L,
        
        /**
         * 会话历史记录（最多保留 10 个）
         * 按时间倒序排列，最新的在前
         */
        var sessionHistory: MutableList<SessionRecord> = mutableListOf()
    )
    
    /**
     * 会话记录数据结构
     */
    data class SessionRecord(
        val sessionId: String,              // 会话 ID
        val createdAt: Long,               // 创建时间戳
        val lastMessageAt: Long,           // 最后消息时间戳
        val messageCount: Int = 0,         // 消息数量
        val isActive: Boolean = false      // 是否为当前活动会话
    )
    
    private var state = State()
    
    // ========== PersistentStateComponent 实现 ==========
    
    override fun getState(): State = state
    
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }
    
    // ========== 公共 API ==========
    
    /**
     * 获取当前活动的会话 ID
     * 
     * @return 当前会话 ID，如果没有则返回 null（表示需要创建新会话）
     */
    fun getCurrentSessionId(): String? = state.currentSessionId
    
    /**
     * 更新当前会话 ID
     * 
     * 在以下场景调用：
     * 1. Claude CLI 首次返回会话 ID 时
     * 2. --resume 操作后 Claude CLI 返回新的会话 ID 时
     * 
     * @param newSessionId 新的会话 ID
     */
    fun updateCurrentSessionId(newSessionId: String) {
        val oldSessionId = state.currentSessionId
        state.currentSessionId = newSessionId
        state.lastUpdated = System.currentTimeMillis()
        
        // 添加到历史记录
        addToSessionHistory(newSessionId)
        
        println("[ProjectSessionStateService] 会话 ID 已更新: $oldSessionId -> $newSessionId")
    }
    
    /**
     * 创建新会话时清理状态
     * 
     * 在以下场景调用：
     * 1. 插件启动时创建新会话
     * 2. 用户手动创建新对话时
     * 
     * 这确保每次创建新会话时都从干净的状态开始，不会加载之前的会话。
     */
    fun clearCurrentSession() {
        val oldSessionId = state.currentSessionId
        state.currentSessionId = null
        state.lastUpdated = System.currentTimeMillis()
        
        println("[ProjectSessionStateService] 会话已清理，准备创建新会话: $oldSessionId -> null")
    }
    
    /**
     * 获取会话历史记录
     * 
     * @return 会话历史记录列表，按时间倒序排列
     */
    fun getSessionHistory(): List<SessionRecord> = state.sessionHistory.toList()
    
    /**
     * 添加会话到历史记录
     * 
     * @param sessionId 会话 ID
     * @param messageCount 消息数量（可选）
     */
    private fun addToSessionHistory(sessionId: String, messageCount: Int = 0) {
        val now = System.currentTimeMillis()
        
        // 移除相同 sessionId 的旧记录（避免重复）
        state.sessionHistory.removeAll { it.sessionId == sessionId }
        
        // 将所有历史记录标记为非活动
        state.sessionHistory.forEach { record ->
            val updatedRecord = record.copy(isActive = false)
            val index = state.sessionHistory.indexOf(record)
            if (index >= 0) {
                state.sessionHistory[index] = updatedRecord
            }
        }
        
        // 添加新记录到开头（最新的在前）
        val newRecord = SessionRecord(
            sessionId = sessionId,
            createdAt = now,
            lastMessageAt = now,
            messageCount = messageCount,
            isActive = true
        )
        state.sessionHistory.add(0, newRecord)
        
        // 只保留最近 10 个会话
        while (state.sessionHistory.size > 10) {
            state.sessionHistory.removeAt(state.sessionHistory.size - 1)
        }
        
        println("[ProjectSessionStateService] 会话已添加到历史记录: $sessionId (总计: ${state.sessionHistory.size})")
    }
    
    /**
     * 更新会话的消息统计
     * 
     * @param sessionId 会话 ID
     * @param messageCount 新的消息数量
     */
    fun updateSessionMessageCount(sessionId: String, messageCount: Int) {
        val recordIndex = state.sessionHistory.indexOfFirst { it.sessionId == sessionId }
        if (recordIndex >= 0) {
            val existingRecord = state.sessionHistory[recordIndex]
            val updatedRecord = existingRecord.copy(
                messageCount = messageCount,
                lastMessageAt = System.currentTimeMillis()
            )
            state.sessionHistory[recordIndex] = updatedRecord
            
            println("[ProjectSessionStateService] 会话消息统计已更新: $sessionId -> $messageCount 条消息")
        }
    }
    
    /**
     * 检查是否存在活动会话
     * 
     * @return true 如果存在活动会话，false 表示需要创建新会话
     */
    fun hasActiveSession(): Boolean = !state.currentSessionId.isNullOrEmpty()
    
    /**
     * 获取服务统计信息（用于调试）
     * 
     * @return 包含当前状态的字符串描述
     */
    fun getStats(): String {
        return buildString {
            append("当前会话: ${state.currentSessionId ?: "无"}")
            append(", 历史记录: ${state.sessionHistory.size} 个")
            if (state.lastUpdated > 0) {
                val lastUpdateTime = java.time.Instant.ofEpochMilli(state.lastUpdated)
                append(", 最后更新: $lastUpdateTime")
            }
        }
    }
    
    companion object {
        /**
         * 获取项目的会话状态服务实例
         * 
         * @param project IntelliJ 项目实例
         * @return 项目级会话状态服务
         */
        fun getInstance(project: Project): ProjectSessionStateService {
            return project.service<ProjectSessionStateService>()
        }
    }
}