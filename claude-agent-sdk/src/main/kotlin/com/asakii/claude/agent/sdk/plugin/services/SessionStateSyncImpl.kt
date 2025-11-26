package com.asakii.claude.agent.sdk.plugin.services

import com.asakii.claude.agent.sdk.plugin.interfaces.SessionStateSync
import com.asakii.claude.agent.sdk.plugin.types.SessionState
import com.asakii.claude.agent.sdk.plugin.types.SessionUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * SessionStateSync 接口的简化实现
 */
class SessionStateSyncImpl : SessionStateSync {

    override suspend fun saveSessionState(sessionId: String, state: SessionState) {
        // 简化实现 - 暂时不做持久化
    }

    override suspend fun loadSessionState(sessionId: String): SessionState? {
        // 简化实现 - 返回空状态
        return null
    }

    override fun observeSessionUpdates(sessionId: String): Flow<SessionState> {
        // 简化实现 - 返回空流
        return flowOf()
    }

    override fun observeProjectUpdates(projectPath: String): Flow<Map<String, SessionState>> {
        // 简化实现 - 返回空流
        return flowOf(emptyMap())
    }

    override suspend fun startBackgroundExecution(
        sessionId: String?,
        projectPath: String,
        prompt: String,
        options: Any
    ): Flow<SessionUpdate> {
        // 简化实现 - 返回空流
        return flowOf()
    }

    override suspend fun terminateBackgroundSession(sessionId: String) {
        // 简化实现 - 无操作
    }

    override suspend fun isSessionRunningInBackground(sessionId: String): Boolean {
        // 简化实现 - 总是返回 false
        return false
    }

    override suspend fun getBackgroundServiceStats(): Map<String, Any> {
        // 简化实现 - 返回空统计
        return emptyMap()
    }

    override suspend fun recoverSessionHistory(sessionId: String, projectPath: String): Boolean {
        // 简化实现 - 总是返回 false
        return false
    }

    // 额外的方法以满足现有代码的需求
    fun observeProjectSessionUpdates(projectPath: String): Flow<Map<String, SessionState>> {
        return observeProjectUpdates(projectPath)
    }

    fun getServiceStats(): Map<String, Any> {
        return emptyMap()
    }
}