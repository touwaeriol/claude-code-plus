package com.claudecodeplus.server

/**
 * WebSocket 连接的状态管理器
 *
 * 架构设计：
 * - 每个 WebSocket 连接对应一个状态管理器
 * - 管理 Claude SDK 会话的生命周期
 * - 限制：一个连接只能有一个活跃的 Claude 会话
 *
 * 状态流转：
 * 1. WebSocket 连接建立 → 创建空的 ConnectionState
 * 2. 客户端调用 connect RPC → sessionId != null
 * 3. 客户端调用 disconnect RPC → sessionId = null（可以重新 connect）
 * 4. WebSocket 断开 → 如果 sessionId != null，清理 SDK 会话
 *
 * 使用示例：
 * ```kotlin
 * webSocket("/ws") {
 *     val state = ConnectionState()
 *
 *     try {
 *         // 处理 RPC 请求
 *         when (request.method) {
 *             "connect" -> {
 *                 state.connect(sessionId, name)
 *                 // 初始化 SDK Client
 *             }
 *             "disconnect" -> {
 *                 state.disconnect()
 *                 // 清理 SDK Client
 *             }
 *         }
 *     } finally {
 *         // 连接断开时自动清理
 *         if (state.isConnected()) {
 *             ClaudeSessionManager.closeSession(state.sessionId!!)
 *         }
 *     }
 * }
 * ```
 */
data class ConnectionState(
    /**
     * 当前绑定的会话 ID
     * - null: 未连接 Claude SDK
     * - 非 null: 已连接到指定会话
     */
    var sessionId: String? = null,

    /**
     * 会话名称
     */
    var sessionName: String? = null,

    /**
     * 连接创建时间
     */
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 检查是否已连接 Claude SDK
     *
     * @return true 如果已连接（sessionId != null）
     */
    fun isConnected(): Boolean = sessionId != null

    /**
     * 连接 Claude SDK（创建会话）
     *
     * @param newSessionId 新会话的 ID
     * @param name 会话名称
     * @throws IllegalStateException 如果已经连接（需要先调用 disconnect）
     */
    fun connect(newSessionId: String, name: String?) {
        if (isConnected()) {
            throw IllegalStateException(
                "Already connected to session: $sessionId. " +
                "Call disconnect() first to create a new session."
            )
        }
        sessionId = newSessionId
        sessionName = name
    }

    /**
     * 断开 Claude SDK（销毁会话）
     *
     * 调用后可以重新调用 connect() 创建新会话
     */
    fun disconnect() {
        sessionId = null
        sessionName = null
    }

    /**
     * 获取连接持续时间（毫秒）
     */
    fun getDuration(): Long = System.currentTimeMillis() - createdAt
}

