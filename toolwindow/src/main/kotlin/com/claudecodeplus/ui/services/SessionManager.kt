package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.SessionObject
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.utils.ClaudeSessionFileLocator
import com.claudecodeplus.ui.utils.ClaudeSessionHistoryLoader
import com.claudecodeplus.ui.utils.SessionIdRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
class SessionManager {
    private val logger = KotlinLogging.logger {}
    
    // 使用线程安全的 ConcurrentHashMap 存储所有会话
    // key 格式: "projectId:tabId"，这样可以支持跨项目的会话管理
    private val sessions = ConcurrentHashMap<String, SessionObject>()
    
    
    // 当前活动会话 ID
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()
    
    // 会话事件流
    private val _events = MutableStateFlow<SessionEvent?>(null)
    val events: StateFlow<SessionEvent?> = _events.asStateFlow()
    
    /**
     * 获取或创建会话对象
     * 
     * @param projectId 项目 ID
     * @param tabId 标签 ID
     * @param initialSessionId 初始的 Claude 会话 ID（可选）
     * @param initialMessages 初始消息列表（可选）
     * @param initialModel 初始 AI 模型（可选，默认使用全局默认值）
     * @param initialPermissionMode 初始权限模式（可选，默认使用全局默认值）
     * @param initialSkipPermissions 初始是否跳过权限（可选，默认使用全局默认值）
     * @param project 关联的项目对象（必须）
     * @return 会话对象
     */
    fun getOrCreateSession(
        projectId: String,
        tabId: String,
        initialSessionId: String? = null,
        initialMessages: List<EnhancedMessage> = emptyList(),
        initialModel: com.claudecodeplus.ui.models.AiModel? = null,
        initialPermissionMode: com.claudecodeplus.ui.models.PermissionMode? = null,
        initialSkipPermissions: Boolean? = null,
        project: com.claudecodeplus.ui.models.Project
    ): SessionObject {
        val sessionKey = "$projectId:$tabId"
        return sessions.computeIfAbsent(sessionKey) {
            val newSession = SessionObject(
                initialSessionId, 
                initialMessages,
                initialModel,
                initialPermissionMode,
                initialSkipPermissions,
                project
            )
            
            // 如果提供了初始 sessionId 或消息，说明是恢复的会话
            if (!initialSessionId.isNullOrEmpty() || initialMessages.isNotEmpty()) {
                newSession.isFirstMessage = false
                logger.info { "[SessionManager] 恢复会话 $sessionKey, sessionId=$initialSessionId, messages=${initialMessages.size}" }
            } else {
                logger.info { "[SessionManager] 创建新会话 $sessionKey" }
            }
            
            _events.value = SessionEvent.SessionCreated(sessionKey, newSession)
            newSession
        }
    }
    
    /**
     * 获取会话对象（不创建）
     * 
     * @param projectId 项目 ID
     * @param tabId 标签 ID
     * @return 会话对象，如果不存在返回 null
     */
    fun getSession(projectId: String, tabId: String): SessionObject? {
        return sessions["$projectId:$tabId"]
    }
    
    /**
     * 获取指定项目的所有会话
     * 
     * @param projectId 项目 ID
     * @return 该项目的所有会话
     */
    fun getAllSessionsForProject(projectId: String): Map<String, SessionObject> {
        return sessions.filterKeys { it.startsWith("$projectId:") }
            .mapKeys { it.key.substringAfter(":") } // 移除项目ID前缀，只保留tabId
    }
    
    /**
     * 移除会话
     * 
     * @param sessionKey 会话键（格式: "projectId:tabId"）
     */
    fun removeSession(sessionKey: String) {
        sessions[sessionKey]?.let { session ->
            // 停止正在进行的生成
            session.stopGenerating()
            
            // 注意：文件追踪器现在由 UnifiedSessionService 管理，无需手动清理
            
            // 清理会话数据
            session.clearSession()
            
            // 从存储中移除
            sessions.remove(sessionKey)
            
            // 如果是当前活动会话，清空活动会话 ID
            if (_activeSessionId.value == sessionKey) {
                _activeSessionId.value = null
            }
            
            _events.value = SessionEvent.SessionRemoved(sessionKey)
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
     * 恢复会话历史
     * 加载历史消息后更新会话状态
     * 
     * @param tabId 标签 ID
     * @param sessionId Claude 会话 ID
     * @param messages 历史消息列表
     */
    fun restoreSessionHistory(tabId: String, sessionId: String, messages: List<EnhancedMessage>) {
        sessions[tabId]?.let { session ->
            session.messages = messages
            session.sessionId = sessionId
            session.onHistoryLoaded()  // 标记历史已加载
            logger.info { "[SessionManager] 恢复会话历史 $tabId: sessionId=$sessionId, messages=${messages.size}" }
            _events.value = SessionEvent.SessionHistoryRestored(tabId, messages.size)
        } ?: run {
            logger.warn { "[SessionManager] 无法恢复会话历史，会话不存在: $tabId" }
        }
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
        data class SessionHistoryRestored(val tabId: String, val messageCount: Int) : SessionEvent()
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
    
    // ========== Claude 会话文件恢复相关方法 ==========
    
    /**
     * 从 Claude 会话文件恢复会话状态
     * 
     * 该方法会：
     * 1. 从 SessionIdRegistry 获取已记录的会话 ID
     * 2. 如果没有记录，则获取项目最新的会话文件
     * 3. 从会话文件加载历史消息
     * 4. 创建或更新会话对象
     * 
     * @param projectPath 项目完整路径
     * @param tabId 标签页 ID
     * @param project 项目对象
     * @param coroutineScope 协程作用域（用于异步操作）
     * @return 如果成功恢复返回 true，否则返回 false
     */
    fun restoreSessionFromClaudeFile(
        projectPath: String, 
        tabId: String, 
        project: com.claudecodeplus.ui.models.Project,
        coroutineScope: CoroutineScope
    ): Boolean {
        if (projectPath.isBlank() || tabId.isBlank()) {
            logger.warn("[SessionManager] 恢复会话参数为空")
            return false
        }
        
        logger.info("[SessionManager] 开始恢复会话: projectPath=$projectPath, tabId=$tabId")
        
        // 异步执行会话恢复，避免阻塞 UI
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // 1. 获取会话 ID
                val sessionId = SessionIdRegistry.getSessionId(projectPath, tabId)
                    ?: SessionIdRegistry.getLatestSessionForProject(projectPath)
                    ?: ClaudeSessionFileLocator.getLatestSessionId(projectPath)
                
                if (sessionId == null) {
                    logger.info("[SessionManager] 项目没有可恢复的会话: $projectPath")
                    return@launch
                }
                
                // 2. 获取会话文件
                val sessionFile = ClaudeSessionFileLocator.getSessionFile(projectPath, sessionId)
                if (sessionFile == null) {
                    logger.warn("[SessionManager] 会话文件不存在: projectPath=$projectPath, sessionId=$sessionId")
                    return@launch
                }
                
                // 3. 加载历史消息和获取当前会话ID
                val historyLoader = ClaudeSessionHistoryLoader()
                val loadResult = historyLoader.loadSessionHistory(sessionFile)
                val messages = loadResult.messages
                val currentSessionId = loadResult.currentSessionId
                
                if (messages.isEmpty()) {
                    logger.warn("[SessionManager] 会话文件没有有效消息: $sessionFile")
                    return@launch
                }
                
                // 记录会话ID更新情况
                if (currentSessionId != null && currentSessionId != sessionId) {
                    logger.info("[SessionManager] 会话ID已更新: $sessionId -> $currentSessionId")
                }
                
                // 4. 在主线程创建/更新会话对象
                coroutineScope.launch(Dispatchers.Main) {
                    val sessionKey = "$projectPath:$tabId"
                    
                    // 检查是否已经存在会话
                    val existingSession = sessions[sessionKey]
                    if (existingSession != null) {
                        // 更新现有会话
                        existingSession.messages = messages.toMutableList()
                        // 使用最新的会话ID（如果有更新的话）
                        existingSession.sessionId = currentSessionId ?: sessionId
                        existingSession.onHistoryLoaded()
                        logger.info("[SessionManager] 更新现有会话: $sessionKey, messages=${messages.size}, sessionId=${existingSession.sessionId}")
                    } else {
                        // 创建新会话，使用最新的会话ID
                        val effectiveSessionId = currentSessionId ?: sessionId
                        val newSession = SessionObject(
                            effectiveSessionId,
                            messages,
                            null, // 使用默认模型
                            null, // 使用默认权限模式
                            null, // 使用默认跳过权限设置
                            project
                        )
                        newSession.isFirstMessage = false // 标记为已有消息的会话
                        newSession.onHistoryLoaded()
                        
                        sessions[sessionKey] = newSession
                        logger.info("[SessionManager] 创建恢复会话: $sessionKey, messages=${messages.size}")
                        
                        _events.value = SessionEvent.SessionCreated(sessionKey, newSession)
                    }
                    
                    // 记录会话 ID 映射（如果还没有记录的话）
                    SessionIdRegistry.recordSessionId(projectPath, tabId, sessionId)
                    
                    // 发送历史恢复事件
                    _events.value = SessionEvent.SessionHistoryRestored(sessionKey, messages.size)
                }
                
            } catch (e: Exception) {
                logger.error("[SessionManager] 恢复会话失败: projectPath=$projectPath, tabId=$tabId", e)
            }
        }
        
        return true
    }
    
    /**
     * 记录新的会话 ID
     * 
     * 当开始新的 Claude 对话时调用此方法记录会话 ID，
     * 以便后续能够从会话文件恢复状态。
     * 
     * @param projectPath 项目完整路径
     * @param tabId 标签页 ID
     * @param sessionId Claude 会话 ID
     */
    fun recordSessionId(projectPath: String, tabId: String, sessionId: String) {
        if (projectPath.isBlank() || tabId.isBlank() || sessionId.isBlank()) {
            logger.warn("[SessionManager] 记录会话ID参数为空")
            return
        }
        
        try {
            SessionIdRegistry.recordSessionId(projectPath, tabId, sessionId)
            
            // 更新内存中的会话对象
            val sessionKey = "$projectPath:$tabId"
            sessions[sessionKey]?.let { session ->
                session.sessionId = sessionId
            }
            
            logger.info("[SessionManager] 记录会话ID: $sessionKey -> $sessionId")
        } catch (e: Exception) {
            logger.error("[SessionManager] 记录会话ID失败", e)
        }
    }
    
    /**
     * 获取项目的会话恢复统计信息
     * 
     * @param projectPath 项目完整路径
     * @return 会话恢复统计信息
     */
    fun getSessionRestoreStats(projectPath: String): SessionRestoreStats {
        return try {
            val registeredSessions = SessionIdRegistry.getProjectSessions(projectPath)
            val availableFiles = ClaudeSessionFileLocator.getProjectSessionFiles(projectPath)
            val fileStats = ClaudeSessionFileLocator.getSessionStats(projectPath)
            
            SessionRestoreStats(
                registeredSessionCount = registeredSessions.size,
                availableFileCount = availableFiles.size,
                totalFileSizeMB = fileStats.totalSizeMB,
                newestSessionTime = fileStats.newestSessionTime,
                oldestSessionTime = fileStats.oldestSessionTime
            )
        } catch (e: Exception) {
            logger.error("[SessionManager] 获取恢复统计信息失败: $projectPath", e)
            SessionRestoreStats(0, 0, 0.0, null, null)
        }
    }
    
    /**
     * 清理项目的会话映射记录
     * 
     * @param projectPath 项目完整路径
     * @return 清理的记录数量
     */
    fun clearProjectSessionMappings(projectPath: String): Int {
        return try {
            val clearedCount = SessionIdRegistry.clearProject(projectPath)
            logger.info("[SessionManager] 清理项目会话映射: $projectPath, 清理了 $clearedCount 个记录")
            clearedCount
        } catch (e: Exception) {
            logger.error("[SessionManager] 清理项目会话映射失败: $projectPath", e)
            0
        }
    }
    
    /**
     * 会话恢复统计信息
     */
    data class SessionRestoreStats(
        val registeredSessionCount: Int,    // 已注册的会话数量
        val availableFileCount: Int,        // 可用的会话文件数量
        val totalFileSizeMB: Double,        // 总文件大小（MB）
        val newestSessionTime: Long?,       // 最新会话时间
        val oldestSessionTime: Long?        // 最旧会话时间
    )
}