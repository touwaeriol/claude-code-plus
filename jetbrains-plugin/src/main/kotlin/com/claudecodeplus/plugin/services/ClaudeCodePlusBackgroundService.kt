package com.claudecodeplus.plugin.services

import com.claudecodeplus.plugin.types.SessionState
import com.claudecodeplus.plugin.types.SessionUpdate
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