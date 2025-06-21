package com.claudecodeplus.sdk

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import java.nio.file.Path

/**
 * 会话监听器接口
 * 定义了会话监听的核心功能
 */
interface SessionWatcher {
    /**
     * 开始监听会话文件
     * @param sessionFile 会话文件路径
     * @return 会话消息流
     */
    fun watchSession(sessionFile: Path): Flow<ClaudeSessionManager.SessionMessage>
    
    /**
     * 停止监听
     */
    fun stop()
    
    /**
     * 获取监听状态
     */
    fun isWatching(): Boolean
}

/**
 * 会话事件
 */
sealed class SessionEvent {
    data class NewMessage(val message: ClaudeSessionManager.SessionMessage) : SessionEvent()
    data class SessionCompressed(val messageCount: Int) : SessionEvent()
    data class Error(val error: Throwable) : SessionEvent()
    object SessionStarted : SessionEvent()
    object SessionEnded : SessionEvent()
}

/**
 * 会话事件总线接口
 */
interface SessionEventBus {
    /**
     * 发布事件
     */
    suspend fun publish(event: SessionEvent)
    
    /**
     * 订阅事件
     */
    fun subscribe(): SharedFlow<SessionEvent>
}