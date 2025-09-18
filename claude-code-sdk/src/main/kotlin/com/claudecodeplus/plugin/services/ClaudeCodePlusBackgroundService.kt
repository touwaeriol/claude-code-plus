package com.claudecodeplus.plugin.services

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Claude Code Plus 后台服务
 *
 * 这是从 cli-wrapper 模块迁移到 claude-code-sdk 的简化版本，
 * 包含了 jetbrains-plugin 模块所需的基本类型定义。
 */
class ClaudeCodePlusBackgroundService {

    /**
     * 会话状态数据类
     */
    data class SessionState(
        var lastFileSize: Long = 0,
        var lastLineCount: Int = 0,
        var lastModified: Long = 0,
        val messageCache: MutableList<SessionMessage> = mutableListOf(),
        val messages: List<SessionMessage> = emptyList(),
        var isGenerating: Boolean = false
    )

    /**
     * 会话更新事件
     */
    sealed class SessionUpdate {
        data class NewMessage(val message: SessionMessage) : SessionUpdate()
        data class Compressed(val messageCount: Int) : SessionUpdate()
        data class Error(val error: Throwable) : SessionUpdate()
    }

    /**
     * 会话消息数据类 - 简化版本
     */
    data class SessionMessage(
        val content: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * 获取会话状态
     */
    fun getSessionState(sessionId: String): SessionState? {
        // 简化实现，返回空状态
        return null
    }

    /**
     * 观察会话更新
     */
    fun observeSessionUpdates(sessionId: String): Flow<SessionUpdate> {
        // 简化实现，返回空流
        return flowOf()
    }

    /**
     * 获取服务统计信息
     */
    fun getServiceStats(): Map<String, Any> {
        return mapOf(
            "activeSessionsCount" to 0,
            "totalMessages" to 0,
            "status" to "ready"
        )
    }

    /**
     * 观察项目更新
     */
    fun observeProjectUpdates(projectPath: String): Flow<Map<String, SessionState>> {
        // 简化实现，返回空映射流
        return flowOf(emptyMap())
    }
}