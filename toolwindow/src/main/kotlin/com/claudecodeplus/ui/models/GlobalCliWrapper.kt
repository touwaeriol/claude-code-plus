package com.claudecodeplus.ui.models

import com.claudecodeplus.ui.services.ClaudeCodeSdkAdapter
import kotlinx.coroutines.flow.Flow

/**
 * 全局 Claude CLI Wrapper 管理器
 *
 * 现已迁移到使用 claude-code-sdk，提供向后兼容的接口
 * 实际功能由 ClaudeCodeSdkAdapter 提供
 */
object GlobalCliWrapper {

    /**
     * 注册会话的输出回调（兼容接口）
     * @param sessionId 会话ID，为null时使用默认会话
     * @param callback 输出回调函数
     */
    fun registerSessionCallback(sessionId: String?, callback: (String) -> Unit) {
        val key = sessionId ?: "default"
        // 将字符串回调转换为 EnhancedMessage 回调
        ClaudeCodeSdkAdapter.registerSessionCallback(key) { enhancedMessage ->
            // 为了兼容旧接口，将 EnhancedMessage 转换为 JSON 字符串
            val jsonString = buildJsonLikeString(enhancedMessage)
            callback(jsonString)
        }
        println("[GlobalCliWrapper] 注册会话回调: sessionId=$key (通过 SDK 适配器)")
    }

    /**
     * 注销会话的输出回调（兼容接口）
     * @param sessionId 会话ID
     */
    fun unregisterSessionCallback(sessionId: String?) {
        val key = sessionId ?: "default"
        ClaudeCodeSdkAdapter.unregisterSessionCallback(key)
        println("[GlobalCliWrapper] 注销会话回调: sessionId=$key (通过 SDK 适配器)")
    }

    /**
     * 获取当前活跃的会话数量（兼容接口）
     */
    val activeSessionCount: Int
        get() = ClaudeCodeSdkAdapter.getActiveSessionCount()

    /**
     * 获取所有活跃的会话ID列表（兼容接口）
     */
    val activeSessionIds: Set<String>
        get() = ClaudeCodeSdkAdapter.getActiveSessionIds()

    /**
     * 清理所有会话回调（兼容接口）
     */
    fun clearAllCallbacks() {
        println("[GlobalCliWrapper] 清理所有会话回调 (通过 SDK 适配器)")
        // SDK 适配器会在关闭会话时自动清理，这里提供兼容接口
    }

    /**
     * 发送消息到会话（新增方法）
     */
    suspend fun sendMessage(
        sessionId: String,
        message: EnhancedMessage,
        sessionObject: SessionObject,
        project: Project? = null
    ): Flow<EnhancedMessage> {
        return ClaudeCodeSdkAdapter.sendMessage(sessionId, message, sessionObject, project)
    }

    /**
     * 中断会话（新增方法）
     */
    suspend fun interruptSession(sessionId: String) {
        ClaudeCodeSdkAdapter.interruptSession(sessionId)
    }

    /**
     * 关闭会话（新增方法）
     */
    suspend fun closeSession(sessionId: String) {
        ClaudeCodeSdkAdapter.closeSession(sessionId)
    }

    /**
     * 检查会话是否连接（新增方法）
     */
    fun isSessionConnected(sessionId: String): Boolean {
        return ClaudeCodeSdkAdapter.isSessionConnected(sessionId)
    }

    /**
     * 将 EnhancedMessage 转换为类似 JSON 的字符串（兼容旧接口）
     */
    private fun buildJsonLikeString(message: EnhancedMessage): String {
        val type = when (message.role) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.SYSTEM -> "system"
            MessageRole.ERROR -> "error"
        }

        return """{"type":"$type","content":"${message.content.replace("\"", "\\\"")}","id":"${message.id}","timestamp":${message.timestamp}}"""
    }

    // === 已废弃的属性 ===

    /**
     * @deprecated 使用 ClaudeCodeSdkAdapter 替代
     * 提供兼容性，但实际上不再使用
     */
    @Deprecated("使用 ClaudeCodeSdkAdapter 替代", ReplaceWith("ClaudeCodeSdkAdapter"))
    val instance: Any get() = object {
        fun setOutputLineCallback(callback: (String) -> Unit) {
            println("[GlobalCliWrapper] setOutputLineCallback 已废弃，请使用 registerSessionCallback")
        }
    }
}