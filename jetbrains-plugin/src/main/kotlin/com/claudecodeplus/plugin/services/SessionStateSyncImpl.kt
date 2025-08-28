package com.claudecodeplus.plugin.services

import com.claudecodeplus.plugin.interfaces.SessionStateSync
import com.claudecodeplus.plugin.services.ClaudeCodePlusBackgroundService.SessionState
import com.claudecodeplus.plugin.services.ClaudeCodePlusBackgroundService.SessionUpdate
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 会话状态同步实现类
 * 
 * 作为UI组件和后台服务之间的桥梁，
 * 提供统一的状态管理API。
 */
class SessionStateSyncImpl : SessionStateSync {
    
    companion object {
        private val logger = Logger.getInstance(SessionStateSyncImpl::class.java)
    }
    
    // 获取后台服务实例
    private val backgroundService: ClaudeCodePlusBackgroundService
        get() = service<ClaudeCodePlusBackgroundService>()
    
    override suspend fun saveSessionState(sessionId: String, state: SessionState) {
        logger.debug("💾 保存会话状态: $sessionId")
        // 后台服务内部管理状态，这里暂时不需要实现
        // 实际的状态保存通过后台服务的内部机制完成
    }
    
    override suspend fun loadSessionState(sessionId: String): SessionState? {
        logger.debug("📖 加载会话状态: $sessionId")
        return backgroundService.getSessionState(sessionId)
    }
    
    override fun observeSessionUpdates(sessionId: String): Flow<SessionState> {
        logger.debug("👀 观察会话更新: $sessionId")
        return backgroundService.observeSessionState(sessionId) ?: emptyFlow()
    }
    
    override fun observeProjectUpdates(projectPath: String): Flow<Map<String, SessionState>> {
        logger.debug("👀 观察项目更新: $projectPath")
        return backgroundService.observeProjectSessionUpdates(projectPath)
    }
    
    override suspend fun startBackgroundExecution(
        sessionId: String?,
        projectPath: String,
        prompt: String,
        options: Any
    ): Flow<SessionUpdate> {
        logger.info("🚀 启动后台执行: sessionId=$sessionId, project=$projectPath")
        
        // 将options转换为ClaudeCliWrapper.QueryOptions
        val queryOptions = when (options) {
            is ClaudeCliWrapper.QueryOptions -> options
            else -> {
                logger.warn("无效的options类型: ${options::class.java}")
                ClaudeCliWrapper.QueryOptions(cwd = projectPath)
            }
        }
        
        return backgroundService.startBackgroundSession(
            sessionId = sessionId,
            projectPath = projectPath,
            prompt = prompt,
            options = queryOptions
        )
    }
    
    override suspend fun terminateBackgroundSession(sessionId: String) {
        logger.info("🛑 终止后台会话: $sessionId")
        backgroundService.terminateSession(sessionId)
    }
    
    override suspend fun isSessionRunningInBackground(sessionId: String): Boolean {
        val state = backgroundService.getSessionState(sessionId)
        return state?.isGenerating == true
    }
    
    override suspend fun getBackgroundServiceStats(): Map<String, Any> {
        return backgroundService.getServiceStats()
    }
    
    override suspend fun recoverSessionHistory(sessionId: String, projectPath: String): Boolean {
        logger.info("🔄 请求恢复会话历史: sessionId=$sessionId, project=$projectPath")
        return backgroundService.recoverSessionHistory(sessionId, projectPath)
    }
}