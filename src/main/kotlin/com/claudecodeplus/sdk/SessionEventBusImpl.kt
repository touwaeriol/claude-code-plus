package com.claudecodeplus.sdk

import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 会话事件总线实现
 * 
 * 使用 SharedFlow 实现事件的发布/订阅模式
 * 解耦会话监听和UI更新
 */
class SessionEventBusImpl : SessionEventBus {
    private val logger = thisLogger()
    
    // 使用 SharedFlow 作为事件总线
    private val eventFlow = MutableSharedFlow<SessionEvent>(
        replay = 10, // 缓存最近10个事件，新订阅者可以接收到
        extraBufferCapacity = 100 // 额外缓冲区，防止背压
    )
    
    // 事件总线的协程作用域
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 事件统计
    private var eventCount = 0L
    private val eventStats = mutableMapOf<String, Long>()
    
    override suspend fun publish(event: SessionEvent) {
        try {
            eventFlow.emit(event)
            eventCount++
            
            // 更新统计
            val eventType = event::class.simpleName ?: "Unknown"
            eventStats[eventType] = (eventStats[eventType] ?: 0) + 1
            
            logger.debug("Published event: $eventType")
        } catch (e: Exception) {
            logger.error("Failed to publish event", e)
        }
    }
    
    override fun subscribe(): SharedFlow<SessionEvent> {
        return eventFlow.asSharedFlow()
    }
    
    /**
     * 异步发布事件（不阻塞调用者）
     */
    fun publishAsync(event: SessionEvent) {
        scope.launch {
            publish(event)
        }
    }
    
    /**
     * 批量发布事件
     */
    suspend fun publishBatch(events: List<SessionEvent>) {
        events.forEach { event ->
            publish(event)
        }
    }
    
    /**
     * 获取事件统计信息
     */
    fun getEventStats(): EventStats {
        return EventStats(
            totalEvents = eventCount,
            eventTypeCounts = eventStats.toMap(),
            subscriberCount = eventFlow.subscriptionCount.value
        )
    }
    
    data class EventStats(
        val totalEvents: Long,
        val eventTypeCounts: Map<String, Long>,
        val subscriberCount: Int
    )
    
    /**
     * 清理资源
     */
    fun shutdown() {
        scope.launch {
            eventFlow.emit(SessionEvent.SessionEnded)
        }
    }
}

/**
 * 单例事件总线
 */
object GlobalSessionEventBus : SessionEventBus {
    private val impl = SessionEventBusImpl()
    
    override suspend fun publish(event: SessionEvent) {
        impl.publish(event)
    }
    
    override fun subscribe(): SharedFlow<SessionEvent> {
        return impl.subscribe()
    }
    
    fun publishAsync(event: SessionEvent) {
        impl.publishAsync(event)
    }
    
    fun getStats() = impl.getEventStats()
}