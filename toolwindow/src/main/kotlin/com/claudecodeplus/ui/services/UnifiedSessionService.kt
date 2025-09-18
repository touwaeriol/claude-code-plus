package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.utils.SessionIdRegistry
import kotlinx.coroutines.CoroutineScope
import mu.KotlinLogging
import java.util.UUID

/**
 * 统一的会话服务
 *
 * 已迁移到使用 SessionService，此类保持向后兼容
 * TODO: 重构所有使用此类的代码以直接使用 SessionService
 */
@Deprecated("使用 SessionService 替代")
class UnifiedSessionService(
    private val scope: CoroutineScope
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 内存中追踪已创建但可能还未写入磁盘的会话ID
     * 用于避免在会话文件异步写入期间的竞态条件
     */
    private val createdSessions = mutableSetOf<String>()

    /**
     * 执行查询 - 存根方法，已废弃
     */
    @Deprecated("使用 SessionService 替代")
    suspend fun query(
        prompt: String,
        options: Map<String, Any> = emptyMap(),
        tabId: String? = null
    ): Map<String, Any> {
        logger.warn("UnifiedSessionService.query() 已废弃，请使用 SessionService")
        return mapOf(
            "success" to false,
            "message" to "UnifiedSessionService 已废弃，请使用 SessionService"
        )
    }

    /**
     * 检查会话是否存在 - 存根方法
     */
    @Deprecated("使用 SessionService.sessionExists() 替代")
    fun sessionExists(sessionId: String, workingDir: String?): Boolean {
        logger.warn("UnifiedSessionService.sessionExists() 已废弃，请使用 SessionService")
        return false
    }

    /**
     * 设置输出回调 - 存根方法
     */
    @Deprecated("使用 SessionService 替代")
    fun setOutputLineCallback(callback: (String) -> Unit) {
        logger.warn("UnifiedSessionService.setOutputLineCallback() 已废弃，请使用 SessionService")
    }

    /**
     * 中断查询 - 存根方法
     */
    @Deprecated("使用 SessionService.interruptSession() 替代")
    suspend fun interrupt() {
        logger.warn("UnifiedSessionService.interrupt() 已废弃，请使用 SessionService")
    }

    /**
     * 清理资源 - 存根方法
     */
    @Deprecated("使用 SessionService 替代")
    fun cleanup() {
        logger.warn("UnifiedSessionService.cleanup() 已废弃，请使用 SessionService")
        createdSessions.clear()
    }
}