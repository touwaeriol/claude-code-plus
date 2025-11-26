package com.asakii.claude.agent.sdk.plugin.interfaces

import com.asakii.claude.agent.sdk.plugin.types.SessionState
import com.asakii.claude.agent.sdk.plugin.types.SessionUpdate
import kotlinx.coroutines.flow.Flow

/**
 * 会话状态同步接口
 * 
 * 定义了UI和后台服务之间的状态同步契约，
 * 确保会话数据在不同组件之间保持一致。
 */
interface SessionStateSync {
    
    /**
     * 保存会话状态到后台服务
     * 
     * @param sessionId 会话ID
     * @param state 会话状态
     */
    suspend fun saveSessionState(sessionId: String, state: SessionState)
    
    /**
     * 从后台服务加载会话状态
     * 
     * @param sessionId 会话ID
     * @return 会话状态，如果不存在返回null
     */
    suspend fun loadSessionState(sessionId: String): SessionState?
    
    /**
     * 观察会话状态更新
     * 
     * @param sessionId 会话ID
     * @return 会话状态更新的Flow，如果会话不存在返回空Flow
     */
    fun observeSessionUpdates(sessionId: String): Flow<SessionState>
    
    /**
     * 观察项目的所有会话更新
     * 
     * @param projectPath 项目路径
     * @return 项目所有会话状态的Map Flow
     */
    fun observeProjectUpdates(projectPath: String): Flow<Map<String, SessionState>>
    
    /**
     * 启动后台会话执行
     * 
     * @param sessionId 会话ID，如果为null将自动生成
     * @param projectPath 项目路径
     * @param prompt 用户输入
     * @param options Claude CLI执行选项
     * @return 会话更新事件的Flow
     */
    suspend fun startBackgroundExecution(
        sessionId: String?,
        projectPath: String,
        prompt: String,
        options: Any // 这里使用Any避免循环依赖，实际使用时会是ClaudeCliWrapper.QueryOptions
    ): Flow<SessionUpdate>
    
    /**
     * 终止后台会话
     * 
     * @param sessionId 会话ID
     */
    suspend fun terminateBackgroundSession(sessionId: String)
    
    /**
     * 检查会话是否在后台运行
     * 
     * @param sessionId 会话ID
     * @return 如果会话正在后台运行返回true
     */
    suspend fun isSessionRunningInBackground(sessionId: String): Boolean
    
    /**
     * 获取后台服务统计信息
     * 
     * @return 统计信息Map
     */
    suspend fun getBackgroundServiceStats(): Map<String, Any>
    
    /**
     * 按需恢复会话历史
     * 
     * @param sessionId 会话ID
     * @param projectPath 项目路径
     * @return 是否成功恢复
     */
    suspend fun recoverSessionHistory(sessionId: String, projectPath: String): Boolean
}