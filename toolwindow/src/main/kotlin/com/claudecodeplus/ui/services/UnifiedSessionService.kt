package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.utils.SessionIdRegistry
import kotlinx.coroutines.CoroutineScope
import mu.KotlinLogging
import java.util.UUID

/**
 * 统一的会话服务
 * 
 * 结合 CLI 执行和文件监听，提供统一的会话管理 API
 */
class UnifiedSessionService(
    private val scope: CoroutineScope
) {
    private val logger = KotlinLogging.logger {}
    private val cliWrapper = ClaudeCliWrapper()
    // 移除已删除的消息转换器
    
    /**
     * 内存中追踪已创建但可能还未写入磁盘的会话ID
     * 用于避免在会话文件异步写入期间的竞态条件
     */
    private val createdSessions = mutableSetOf<String>()
    
    // 移除自动监听，改为由 ProjectManager 在项目确定时启动
    
    /**
     * 执行查询
     * 
     * 如果没有指定 sessionId 且不是恢复会话，会自动生成一个新的 UUID
     * 同时会自动记录 sessionId 到注册表以便后续恢复
     */
    suspend fun query(
        prompt: String, 
        options: ClaudeCliWrapper.QueryOptions,
        tabId: String? = null  // 添加标签页ID参数，用于记录会话映射
    ): ClaudeCliWrapper.QueryResult {
        // 使用传入的 cwd
        val workingDir = options.cwd
        
        val finalOptions = when {
            // 情况1：没有指定任何会话ID，创建新会话
            options.sessionId == null && options.resume == null -> {
                val newSessionId = UUID.randomUUID().toString()
                options.copy(cwd = workingDir, sessionId = newSessionId)
            }
            // 情况2：已指定resume参数，直接使用
            options.resume != null -> {
                options.copy(cwd = workingDir)
            }
            // 情况3：指定了sessionId，需要检查会话是否存在
            options.sessionId != null -> {
                val sessionId = options.sessionId!!
                if (sessionExists(sessionId, workingDir)) {
                    // 会话已存在，使用resume参数
                    options.copy(cwd = workingDir, resume = sessionId, sessionId = null)
                } else {
                    // 会话不存在，使用sessionId创建新会话
                    options.copy(cwd = workingDir, sessionId = sessionId)
                }
            }
            else -> {
                options.copy(cwd = workingDir)
            }
        }
        
        val result = cliWrapper.query(prompt, finalOptions)
        
        // 如果命令成功，处理会话ID记录
        if (result.success) {
            val sessionId = result.sessionId ?: finalOptions.sessionId
            
            if (sessionId != null) {
                // 将会话ID添加到内存缓存
                if (finalOptions.sessionId != null && finalOptions.resume == null) {
                    // 这是一个新创建的会话（使用了--session-id而不是--resume）
                    createdSessions.add(sessionId)
                    logger.info("[UnifiedSessionService] 记录新创建的会话: $sessionId")
                }
                
                // 自动记录 sessionId 到注册表（如果提供了项目路径和标签页ID）
                if (tabId != null && workingDir != null) {
                    try {
                        SessionIdRegistry.recordSessionId(workingDir, tabId, sessionId)
                        logger.info("[UnifiedSessionService] 自动记录会话映射: $workingDir:$tabId -> $sessionId")
                    } catch (e: Exception) {
                        logger.warn("[UnifiedSessionService] 记录会话映射失败", e)
                    }
                }
            }
        }
        
        return result
    }
    
    
    /**
     * 检查会话是否存在
     * 首先检查内存缓存，然后检查文件系统
     */
    fun sessionExists(sessionId: String, projectPath: String): Boolean {
        // 先检查内存中是否已知这个会话被创建
        if (createdSessions.contains(sessionId)) {
            return true
        }
        
        // 简化：直接返回 false，不再检查文件系统
        return false
    }
    
    /**
     * 获取所有会话（指定项目路径）
     */
    fun getAllSessions(projectPath: String): List<String> {
        // 简化：返回空列表
        return emptyList()
    }
    
    /**
     * 终止当前 CLI 进程
     */
    fun terminate() {
        cliWrapper.terminate()
    }
    
    /**
     * 检查 Claude Code SDK 是否可用
     */
    suspend fun isCliAvailable(): Boolean {
        return cliWrapper.isClaudeCodeSdkAvailable()
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
        // 简化：不再需要关闭 sessionAPI
    }
}