package com.claudecodeplus.plugin.services

/**
 * 项目级会话状态服务
 *
 * 提供项目级别的会话状态管理功能
 */
class ProjectSessionStateService {

    /**
     * 清理当前会话
     */
    fun clearCurrentSession() {
        // 简化实现 - 清理会话状态
    }

    /**
     * 获取统计信息
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "activeSessionsCount" to 0,
            "totalMessages" to 0,
            "status" to "ready"
        )
    }

    /**
     * 获取服务统计信息
     */
    fun getServiceStats(): Map<String, Any> {
        return getStats()
    }
}