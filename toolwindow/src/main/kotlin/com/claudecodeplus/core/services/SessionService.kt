package com.claudecodeplus.core.services

import com.claudecodeplus.core.models.SessionEvent
import com.claudecodeplus.core.models.SessionMetadata
import com.claudecodeplus.core.models.SessionState
import com.claudecodeplus.core.types.Result
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.ContextReference
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.PermissionMode
import kotlinx.coroutines.flow.Flow

/**
 * 会话管理服务接口
 * 负责会话的生命周期管理和消息传递
 */
interface SessionService {
    
    /**
     * 创建新会话
     * @param projectPath 项目路径
     * @param model 使用的AI模型
     * @param permissionMode 权限模式
     * @return 新创建的会话ID
     */
    suspend fun createSession(
        projectPath: String,
        model: AiModel = AiModel.OPUS,
        permissionMode: PermissionMode = PermissionMode.DEFAULT
    ): Result<String>
    
    /**
     * 发送消息到指定会话
     * @param sessionId 会话ID
     * @param message 消息内容
     * @param contexts 上下文引用列表
     * @param workingDirectory 工作目录
     * @return 发送结果
     */
    suspend fun sendMessage(
        sessionId: String,
        message: String,
        contexts: List<ContextReference> = emptyList(),
        workingDirectory: String
    ): Result<Unit>
    
    /**
     * 恢复已有会话并发送消息
     * @param sessionId 会话ID
     * @param message 消息内容
     * @param contexts 上下文引用列表
     * @param workingDirectory 工作目录
     * @return 发送结果
     */
    suspend fun resumeSession(
        sessionId: String,
        message: String,
        contexts: List<ContextReference> = emptyList(),
        workingDirectory: String
    ): Result<Unit>
    
    /**
     * 加载会话历史消息
     * @param sessionId 会话ID
     * @param projectPath 项目路径
     * @param forceReload 是否强制重新加载
     * @return 历史消息列表
     */
    suspend fun loadSessionHistory(
        sessionId: String,
        projectPath: String,
        forceReload: Boolean = false
    ): Result<List<EnhancedMessage>>
    
    /**
     * 中断当前会话的生成过程
     * @param sessionId 会话ID
     * @return 中断结果
     */
    suspend fun interruptSession(sessionId: String): Result<Unit>
    
    /**
     * 观察会话事件流
     * @param sessionId 会话ID
     * @return 事件流
     */
    fun observeSessionEvents(sessionId: String): Flow<SessionEvent>
    
    /**
     * 获取会话元数据
     * @param sessionId 会话ID
     * @return 会话元数据
     */
    suspend fun getSessionMetadata(sessionId: String): Result<SessionMetadata>
    
    /**
     * 更新会话元数据
     * @param sessionId 会话ID
     * @param metadata 新的元数据
     * @return 更新结果
     */
    suspend fun updateSessionMetadata(sessionId: String, metadata: SessionMetadata): Result<Unit>
    
    /**
     * 删除会话
     * @param sessionId 会话ID
     * @param projectPath 项目路径
     * @return 删除结果
     */
    suspend fun deleteSession(sessionId: String, projectPath: String): Result<Unit>
    
    /**
     * 检查会话是否存在
     * @param sessionId 会话ID
     * @param projectPath 项目路径
     * @return 是否存在
     */
    suspend fun sessionExists(sessionId: String, projectPath: String): Boolean
}
