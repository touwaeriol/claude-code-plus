package com.claudecodeplus.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Claude 进程事件系统
 * 
 * 用于替代文件监听系统，直接从 Claude CLI 进程的输出流获取消息
 * 参考 Claudia 项目的 Tauri 事件系统实现
 */

/**
 * Claude 会话事件定义
 * 支持历史记录加载和实时消息接收
 * 完全符合 Claudia 项目的事件模式
 */
sealed class ClaudeEvent {
    // 实时消息事件
    data class MessageReceived(val message: SDKMessage) : ClaudeEvent()
    
    // 历史消息事件（预加载时使用）
    data class HistoryMessageLoaded(val message: SDKMessage) : ClaudeEvent()
    data class HistoryLoadError(val error: String) : ClaudeEvent()
    
    // 进程和错误事件
    data class ProcessError(val error: String) : ClaudeEvent()
    data class ParseError(val rawLine: String, val exception: Exception) : ClaudeEvent()
    data class ProcessComplete(val success: Boolean) : ClaudeEvent()
    data class SessionComplete(val success: Boolean) : ClaudeEvent()
}

/**
 * Claude 进程事件类型（原有的事件总线系统保留）
 */
enum class ClaudeEventType {
    /** Claude 输出消息（stdout） */
    OUTPUT,
    /** Claude 错误消息（stderr） */
    ERROR,
    /** Claude 进程完成 */
    COMPLETE,
    /** Claude 进程开始 */
    STARTED,
    /** Claude 进程被取消 */
    CANCELLED
}

/**
 * Claude 进程事件
 */
@Serializable
data class ClaudeProcessEvent(
    /** 事件类型 */
    val type: ClaudeEventType,
    /** 会话ID */
    val sessionId: String?,
    /** 事件数据 */
    val data: String?,
    /** 进程ID */
    val processId: Long?,
    /** 时间戳 */
    val timestamp: Long = System.currentTimeMillis(),
    /** 是否成功（仅对 COMPLETE 事件有意义） */
    val success: Boolean? = null
)

/**
 * Claude 进程事件监听器
 */
fun interface ClaudeProcessEventListener {
    /**
     * 处理 Claude 进程事件
     */
    suspend fun onEvent(event: ClaudeProcessEvent)
}

/**
 * Claude 进程事件总线
 * 
 * 管理事件监听器的注册和事件分发
 */
class ClaudeProcessEventBus {
    private val listeners = mutableMapOf<String, MutableSet<ClaudeProcessEventListener>>()
    
    /**
     * 注册事件监听器
     * 
     * @param sessionId 会话ID，用于会话隔离
     * @param listener 事件监听器
     */
    fun addListener(sessionId: String, listener: ClaudeProcessEventListener) {
        listeners.getOrPut(sessionId) { mutableSetOf() }.add(listener)
    }
    
    /**
     * 移除事件监听器
     */
    fun removeListener(sessionId: String, listener: ClaudeProcessEventListener) {
        listeners[sessionId]?.remove(listener)
        if (listeners[sessionId]?.isEmpty() == true) {
            listeners.remove(sessionId)
        }
    }
    
    /**
     * 移除会话的所有监听器
     */
    fun removeAllListeners(sessionId: String) {
        listeners.remove(sessionId)
    }
    
    /**
     * 发送事件到对应会话的所有监听器
     */
    suspend fun emit(event: ClaudeProcessEvent) {
        val sessionId = event.sessionId ?: return
        listeners[sessionId]?.forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                // 记录监听器异常，但不影响其他监听器
                println("Error in event listener: ${e.message}")
            }
        }
        
        // 同时向全局监听器发送事件（sessionId 为 "*"）
        listeners["*"]?.forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                println("Error in global event listener: ${e.message}")
            }
        }
    }
    
    companion object {
        /**
         * 全局事件总线实例
         */
        val instance = ClaudeProcessEventBus()
    }
}