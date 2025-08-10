package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.SessionObject
import com.claudecodeplus.ui.models.EnhancedMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * 会话管理器 - 中央会话状态管理服务
 * 
 * 负责管理所有会话的生命周期和状态，支持：
 * - 会话的创建、获取和销毁
 * - 会话状态的集中管理
 * - 支持多个会话同时进行生成
 * - 会话切换时的状态保持
 * 
 * 与 ChatTabManager 配合使用：
 * - ChatTabManager 管理标签的 UI 层面（标题、分组等）
 * - SessionManager 管理会话的数据层面（消息、生成状态等）
 */
class SessionManager (
    private val scope: CoroutineScope? = null,
    private val enableFileWatching: Boolean = false
) {
    private val logger = KotlinLogging.logger {}
    
    // 使用线程安全的 ConcurrentHashMap 存储所有会话
    private val sessions = ConcurrentHashMap<String, SessionObject>()
    
    // 文件监听服务（可选）
    private val fileWatchService: SessionFileWatchService? = if (enableFileWatching && scope != null) {
        SessionFileWatchService(scope).also {
            logger.info { "File watching enabled for SessionManager" }
        }
    } else {
        null
    }
    
    // 当前活动会话 ID
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()
    
    // 会话事件流
    private val _events = MutableStateFlow<SessionEvent?>(null)
    val events: StateFlow<SessionEvent?> = _events.asStateFlow()
    
    /**
     * 获取或创建会话对象
     * 
     * @param tabId 标签 ID（作为会话的唯一标识）
     * @param initialSessionId 初始的 Claude 会话 ID（可选）
     * @param initialMessages 初始消息列表（可选）
     * @param initialModel 初始 AI 模型（可选，默认使用全局默认值）
     * @param initialPermissionMode 初始权限模式（可选，默认使用全局默认值）
     * @param initialSkipPermissions 初始是否跳过权限（可选，默认使用全局默认值）
     * @param project 关联的项目对象（必须）
     * @return 会话对象
     */
    fun getOrCreateSession(
        tabId: String,
        initialSessionId: String? = null,
        initialMessages: List<EnhancedMessage> = emptyList(),
        initialModel: com.claudecodeplus.ui.models.AiModel? = null,
        initialPermissionMode: com.claudecodeplus.ui.models.PermissionMode? = null,
        initialSkipPermissions: Boolean? = null,
        project: com.claudecodeplus.ui.models.Project
    ): SessionObject {
        return sessions.computeIfAbsent(tabId) {
            SessionObject(
                initialSessionId, 
                initialMessages,
                initialModel,
                initialPermissionMode,
                initialSkipPermissions,
                project
            ).also {
                _events.value = SessionEvent.SessionCreated(tabId, it)
            }
        }
    }
    
    /**
     * 获取会话对象（不创建）
     * 
     * @param tabId 标签 ID
     * @return 会话对象，如果不存在返回 null
     */
    fun getSession(tabId: String): SessionObject? {
        return sessions[tabId]
    }
    
    /**
     * 移除会话
     * 
     * @param tabId 标签 ID
     */
    fun removeSession(tabId: String) {
        sessions[tabId]?.let { session ->
            // 停止正在进行的生成
            session.stopGenerating()
            
            // 注意：文件追踪器现在由 UnifiedSessionService 管理，无需手动清理
            
            // 清理会话数据
            session.clearSession()
            
            // 从存储中移除
            sessions.remove(tabId)
            
            // 如果是当前活动会话，清空活动会话 ID
            if (_activeSessionId.value == tabId) {
                _activeSessionId.value = null
            }
            
            _events.value = SessionEvent.SessionRemoved(tabId)
        }
    }
    
    /**
     * 设置活动会话
     * 
     * @param tabId 标签 ID
     */
    fun setActiveSession(tabId: String) {
        if (sessions.containsKey(tabId)) {
            _activeSessionId.value = tabId
            _events.value = SessionEvent.SessionActivated(tabId)
        }
    }
    
    /**
     * 获取当前活动会话
     * 
     * @return 活动会话对象，如果没有返回 null
     */
    fun getActiveSession(): SessionObject? {
        return _activeSessionId.value?.let { sessions[it] }
    }
    
    /**
     * 获取所有正在生成的会话
     * 
     * @return 正在生成的会话列表（标签 ID 和会话对象的配对）
     */
    fun getGeneratingSessions(): List<Pair<String, SessionObject>> {
        return sessions.filter { it.value.isGenerating }.toList()
    }
    
    /**
     * 获取有待处理队列的会话
     * 
     * @return 有队列的会话列表
     */
    fun getSessionsWithQueue(): List<Pair<String, SessionObject>> {
        return sessions.filter { it.value.hasQueuedQuestions }.toList()
    }
    
    /**
     * 清理所有会话
     * 
     * 通常在应用退出时调用
     */
    fun clearAllSessions() {
        // 停止所有正在进行的生成
        sessions.values.forEach { 
            it.stopGenerating()
        }
        
        // 清空所有会话
        sessions.clear()
        
        // 清空活动会话 ID
        _activeSessionId.value = null
        
        // 停止文件监听服务
        fileWatchService?.stopAll()
        
        _events.value = SessionEvent.AllSessionsCleared
    }
    
    /**
     * 获取会话数量
     * 
     * @return 当前管理的会话总数
     */
    fun getSessionCount(): Int {
        return sessions.size
    }
    
    /**
     * 检查是否有正在生成的会话
     * 
     * @return 如果有任何会话正在生成返回 true
     */
    fun hasGeneratingSessions(): Boolean {
        return sessions.any { it.value.isGenerating }
    }
    
    /**
     * 获取会话统计信息
     * 
     * @return 会话统计数据
     */
    fun getSessionStats(): SessionStats {
        val allSessions = sessions.values
        return SessionStats(
            totalSessions = allSessions.size,
            generatingSessions = allSessions.count { it.isGenerating },
            sessionsWithQueue = allSessions.count { it.hasQueuedQuestions },
            totalQueuedQuestions = allSessions.sumOf { it.queueSize },
            totalMessages = allSessions.sumOf { it.messages.size }
        )
    }
    
    /**
     * 会话事件
     */
    sealed class SessionEvent {
        data class SessionCreated(val tabId: String, val session: SessionObject) : SessionEvent()
        data class SessionRemoved(val tabId: String) : SessionEvent()
        data class SessionActivated(val tabId: String) : SessionEvent()
        object AllSessionsCleared : SessionEvent()
    }
    
    /**
     * 会话统计信息
     */
    data class SessionStats(
        val totalSessions: Int,
        val generatingSessions: Int,
        val sessionsWithQueue: Int,
        val totalQueuedQuestions: Int,
        val totalMessages: Int
    )
    
    // ========== 文件追踪相关方法 ==========
    
    /**
     * 为会话关联文件追踪器
     * 
     * @param tabId 标签 ID
     * @param sessionId Claude 会话 ID
     * @param projectPath 项目路径
     */
    fun attachFileTracker(tabId: String, sessionId: String, projectPath: String) {
        if (fileWatchService == null) {
            logger.debug { "File watching is disabled, skipping tracker attachment" }
            return
        }
        
        sessions[tabId]?.let { session ->
            // 更新会话ID（文件追踪现在由 UnifiedSessionService 管理）
            session.sessionId = sessionId
            
            // 启动文件监听（如果启用）
            fileWatchService?.let { watchService ->
                watchService.startWatchingProject(projectPath)
                logger.info { "Started file watching for session: $sessionId in project: $projectPath" }
            }
        }
    }
    
    /**
     * 获取文件监听服务
     * 
     * @return 文件监听服务实例，如果未启用返回 null
     */
    fun getFileWatchService(): SessionFileWatchService? = fileWatchService
    
    /**
     * 检查是否启用了文件监听
     */
    fun isFileWatchingEnabled(): Boolean = fileWatchService != null
}