package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.session.UnifiedSessionAPI
import com.claudecodeplus.sdk.session.ClaudeFileMessage
import com.claudecodeplus.ui.models.EnhancedMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * 统一的会话服务
 * 
 * 结合 CLI 执行和文件监听，提供统一的会话管理 API
 */
class UnifiedSessionService(
    private val scope: CoroutineScope,
    private val workingDirectory: String
) {
    private val cliWrapper = ClaudeCliWrapper()
    private val sessionAPI = UnifiedSessionAPI(scope)
    private val messageConverter = MessageFlowConverter()
    
    /**
     * 内存中追踪已创建但可能还未写入磁盘的会话ID
     * 用于避免在会话文件异步写入期间的竞态条件
     */
    private val createdSessions = mutableSetOf<String>()
    
    init {
        // 自动开始监听当前项目
        sessionAPI.startProject(workingDirectory)
    }
    
    /**
     * 执行查询
     * 
     * 如果没有指定 sessionId 且不是恢复会话，会自动生成一个新的 UUID
     */
    suspend fun query(
        prompt: String, 
        options: ClaudeCliWrapper.QueryOptions = ClaudeCliWrapper.QueryOptions()
    ): ClaudeCliWrapper.QueryResult {
        val finalOptions = when {
            // 情况1：没有指定任何会话ID，创建新会话
            options.sessionId == null && options.resume == null -> {
                val newSessionId = UUID.randomUUID().toString()
                options.copy(cwd = workingDirectory, sessionId = newSessionId)
            }
            // 情况2：已指定resume参数，直接使用
            options.resume != null -> {
                options.copy(cwd = workingDirectory)
            }
            // 情况3：指定了sessionId，需要检查会话是否存在
            options.sessionId != null -> {
                val sessionId = options.sessionId!!
                if (sessionExists(sessionId)) {
                    // 会话已存在，使用resume参数
                    options.copy(cwd = workingDirectory, resume = sessionId, sessionId = null)
                } else {
                    // 会话不存在，使用sessionId创建新会话
                    options.copy(cwd = workingDirectory, sessionId = sessionId)
                }
            }
            else -> {
                options.copy(cwd = workingDirectory)
            }
        }
        
        val result = cliWrapper.query(prompt, finalOptions)
        
        // 如果命令成功且创建了新会话，将会话ID添加到内存缓存
        if (result.success) {
            val sessionId = result.sessionId ?: finalOptions.sessionId
            if (sessionId != null && finalOptions.sessionId != null && finalOptions.resume == null) {
                // 这是一个新创建的会话（使用了--session-id而不是--resume）
                createdSessions.add(sessionId)
            }
        }
        
        return result
    }
    
    /**
     * 订阅会话消息
     */
    fun subscribeToSession(sessionId: String): Flow<List<EnhancedMessage>> {
        return sessionAPI.subscribeToSession(sessionId, workingDirectory)
            .map { messages ->
                messageConverter.convertMessages(messages, sessionId)
            }
    }
    
    /**
     * 加载历史会话
     */
    suspend fun loadHistoricalSession(sessionId: String): List<EnhancedMessage> {
        val messages = sessionAPI.loadHistoricalSession(sessionId, workingDirectory)
        return messageConverter.convertMessages(messages, sessionId)
    }
    
    /**
     * 加载历史会话（指定项目路径）
     */
    suspend fun loadHistoricalSession(sessionId: String, projectPath: String): List<EnhancedMessage> {
        val messages = sessionAPI.loadHistoricalSession(sessionId, projectPath)
        return messageConverter.convertMessages(messages, sessionId)
    }
    
    /**
     * 加载会话（智能策略）
     */
    suspend fun loadSession(
        sessionId: String,
        isCurrentTab: Boolean = false,
        isVisible: Boolean = false,
        isActive: Boolean = false
    ): List<EnhancedMessage> {
        val messages = sessionAPI.loadSession(
            sessionId, workingDirectory, isCurrentTab, isVisible, isActive
        )
        return messageConverter.convertMessages(messages, sessionId)
    }
    
    /**
     * 检查会话是否存在
     * 首先检查内存缓存，然后检查文件系统
     */
    fun sessionExists(sessionId: String): Boolean {
        // 先检查内存中是否已知这个会话被创建
        if (createdSessions.contains(sessionId)) {
            return true
        }
        
        // 然后检查文件系统
        val fileExists = sessionAPI.sessionExists(sessionId, workingDirectory)
        
        // 如果文件存在，也加入到内存缓存中
        if (fileExists) {
            createdSessions.add(sessionId)
        }
        
        return fileExists
    }
    
    /**
     * 获取所有会话
     */
    fun getAllSessions(): List<String> {
        return sessionAPI.getProjectSessions(workingDirectory)
    }
    
    /**
     * 终止当前 CLI 进程
     */
    fun terminate() {
        cliWrapper.terminate()
    }
    
    /**
     * 检查 CLI 是否可用
     */
    suspend fun isCliAvailable(): Boolean {
        return cliWrapper.isClaudeCliAvailable()
    }
    
    /**
     * 检查进程是否运行
     */
    fun isProcessAlive(): Boolean {
        return cliWrapper.isProcessAlive()
    }
    
    /**
     * 关闭服务
     */
    fun shutdown() {
        sessionAPI.shutdown()
    }
}