package com.claudecodeplus.sdk.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * 统一的会话管理 API
 * 
 * 简化版本：结合项目监听、内存缓存和智能加载
 * 这是 toolwindow 模块的主要接口
 */
class UnifiedSessionAPI(
    private val scope: CoroutineScope
) {
    private val fileWatchService = SessionFileWatchService(scope)
    private val cache = SimpleSessionCache()
    private val sessionLoader = SessionLoader(cache, fileWatchService, scope)
    private val resourceManager = SimpleResourceManager(fileWatchService, sessionLoader, scope)
    
    init {
        // 启动定时资源清理
        resourceManager.startPeriodicCleanup()
    }
    
    /**
     * 开始监听项目的会话文件
     */
    fun startProject(projectPath: String) {
        fileWatchService.startWatchingProject(projectPath)
    }
    
    /**
     * 停止监听项目
     */
    fun stopProject(projectPath: String) {
        fileWatchService.stopWatchingProject(projectPath)
    }
    
    /**
     * 加载会话消息（智能加载策略）
     */
    suspend fun loadSession(
        sessionId: String, 
        projectPath: String,
        isCurrentTab: Boolean = false,
        isVisible: Boolean = false,
        isActive: Boolean = false
    ): List<ClaudeFileMessage> {
        // 自动开始监听项目
        startProject(projectPath)
        
        // 决定加载策略
        val strategy = sessionLoader.determineLoadingStrategy(isCurrentTab, isVisible, isActive)
        
        return sessionLoader.loadSession(sessionId, projectPath, strategy)
    }
    
    /**
     * 加载历史会话（简单加载）
     */
    suspend fun loadHistoricalSession(
        sessionId: String, 
        projectPath: String
    ): List<ClaudeFileMessage> {
        return loadSession(sessionId, projectPath, isCurrentTab = false, isVisible = false, isActive = false)
    }
    
    /**
     * 订阅会话消息更新（启用实时监听）
     * 
     * 返回一个包含初始历史消息 + 实时更新的完整流
     */
    fun subscribeToSession(sessionId: String, projectPath: String): Flow<List<ClaudeFileMessage>> {
        // 确保项目正在被监听
        startProject(projectPath)
        
        return flow {
            // 1. 首先发送当前所有历史消息
            val historical = loadHistoricalSession(sessionId, projectPath)
            emit(historical) // 即使为空也要发送，这样UI就有初始状态
            
            // 2. 然后订阅实时更新
            fileWatchService.subscribeToSession(sessionId).collect { newMessages ->
                if (newMessages.isNotEmpty()) {
                    // 重新读取完整消息列表（确保顺序正确）
                    val allMessages = loadHistoricalSession(sessionId, projectPath)
                    emit(allMessages)
                }
            }
        }
    }
    
    /**
     * 订阅所有会话更新
     */
    fun subscribeToAllSessions(): Flow<SessionFileWatchService.FileChangeEvent> {
        return fileWatchService.subscribeToAll()
    }
    
    /**
     * 检查会话文件是否存在
     */
    fun sessionExists(sessionId: String, projectPath: String): Boolean {
        return fileWatchService.sessionFileExists(projectPath, sessionId)
    }
    
    /**
     * 获取项目的所有会话
     */
    fun getProjectSessions(projectPath: String): List<String> {
        return fileWatchService.getProjectSessions(projectPath)
    }
    
    /**
     * 获取会话文件路径
     */
    fun getSessionFilePath(sessionId: String, projectPath: String): String {
        return fileWatchService.getSessionFilePath(projectPath, sessionId)
    }
    
    /**
     * 停止会话实时监听
     */
    fun stopSessionUpdates(sessionId: String, projectPath: String) {
        sessionLoader.disableRealtimeUpdates(sessionId, projectPath)
    }
    
    /**
     * 获取系统统计信息
     */
    fun getSystemStats(): SystemStats {
        val resourceStats = resourceManager.getResourceStats()
        val loaderStats = sessionLoader.getStats()
        
        return SystemStats(
            watchedProjects = fileWatchService.getProjectSessions("").size, // 需要修改为获取所有项目
            cachedSessions = loaderStats.cacheStats.size,
            realtimeSubscriptions = loaderStats.realtimeSubscriptions.size,
            isCleanupRunning = resourceStats.isPeriodicCleanupRunning
        )
    }
    
    /**
     * 手动清理资源
     */
    fun cleanup() {
        resourceManager.cleanup()
    }
    
    /**
     * 内存压力处理
     */
    fun handleMemoryPressure() {
        resourceManager.handleMemoryPressure()
    }
    
    /**
     * 关闭所有监听
     */
    fun shutdown() {
        resourceManager.shutdown()
    }
    
    data class SystemStats(
        val watchedProjects: Int,
        val cachedSessions: Int,
        val realtimeSubscriptions: Int,
        val isCleanupRunning: Boolean
    )
}