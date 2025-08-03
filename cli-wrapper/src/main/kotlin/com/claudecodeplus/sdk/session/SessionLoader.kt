package com.claudecodeplus.sdk.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mu.KotlinLogging

/**
 * 加载策略枚举
 */
enum class LoadingStrategy {
    IMMEDIATE,    // 立即加载：当前标签
    REALTIME,     // 实时更新：活跃会话  
    LAZY         // 延迟加载：后台标签和历史会话
}

/**
 * 会话加载器
 * 
 * 根据会话状态采用不同的加载策略
 */
class SessionLoader(
    private val cache: SimpleSessionCache,
    private val fileWatchService: SessionFileWatchService,
    private val scope: CoroutineScope
) {
    private val logger = KotlinLogging.logger {}
    
    // 跟踪实时监听的会话
    private val realtimeSubscriptions = mutableMapOf<String, kotlinx.coroutines.Job>()
    
    /**
     * 加载会话消息
     */
    suspend fun loadSession(
        sessionId: String, 
        projectPath: String, 
        strategy: LoadingStrategy = LoadingStrategy.LAZY
    ): List<ClaudeFileMessage> {
        
        return when (strategy) {
            LoadingStrategy.IMMEDIATE -> {
                // 立即加载
                logger.debug { "Loading session immediately: $sessionId" }
                cache.getSessionMessages(sessionId, projectPath)
            }
            
            LoadingStrategy.REALTIME -> {
                // 立即加载并启用实时监听
                logger.debug { "Loading session with realtime updates: $sessionId" }
                val messages = cache.getSessionMessages(sessionId, projectPath)
                enableRealtimeUpdates(sessionId, projectPath)
                messages
            }
            
            LoadingStrategy.LAZY -> {
                // 简单加载，不启用实时监听
                logger.debug { "Loading session lazily: $sessionId" }
                cache.getSessionMessages(sessionId, projectPath)
            }
        }
    }
    
    /**
     * 启用实时更新
     */
    private fun enableRealtimeUpdates(sessionId: String, projectPath: String) {
        val subscriptionKey = "$projectPath:$sessionId"
        
        // 如果已经在监听，直接返回
        if (realtimeSubscriptions.containsKey(subscriptionKey)) {
            logger.debug { "Session already has realtime updates: $sessionId" }
            return
        }
        
        // 订阅文件变化事件
        val subscription = fileWatchService.subscribeToSession(sessionId)
            .onEach { newMessages ->
                if (newMessages.isNotEmpty()) {
                    logger.debug { "Realtime update for session $sessionId: ${newMessages.size} new messages" }
                    // 清除缓存，下次访问时重新读取
                    cache.invalidate(sessionId, projectPath)
                }
            }
            .launchIn(scope)
        
        realtimeSubscriptions[subscriptionKey] = subscription
        logger.debug { "Enabled realtime updates for session: $sessionId" }
    }
    
    /**
     * 禁用实时更新
     */
    fun disableRealtimeUpdates(sessionId: String, projectPath: String) {
        val subscriptionKey = "$projectPath:$sessionId"
        realtimeSubscriptions.remove(subscriptionKey)?.cancel()
        logger.debug { "Disabled realtime updates for session: $sessionId" }
    }
    
    /**
     * 根据会话状态决定加载策略
     */
    fun determineLoadingStrategy(
        isCurrentTab: Boolean,
        isVisible: Boolean,
        isActive: Boolean
    ): LoadingStrategy {
        return when {
            isCurrentTab && isActive -> LoadingStrategy.REALTIME
            isCurrentTab || isVisible -> LoadingStrategy.IMMEDIATE
            else -> LoadingStrategy.LAZY
        }
    }
    
    /**
     * 清理所有实时订阅
     */
    fun cleanup() {
        realtimeSubscriptions.values.forEach { it.cancel() }
        realtimeSubscriptions.clear()
        logger.info { "Cleaned up all realtime subscriptions" }
    }
    
    /**
     * 获取统计信息
     */
    fun getStats(): LoaderStats {
        return LoaderStats(
            realtimeSubscriptions = realtimeSubscriptions.keys.toList(),
            cacheStats = cache.getCacheStats()
        )
    }
    
    data class LoaderStats(
        val realtimeSubscriptions: List<String>,
        val cacheStats: SimpleSessionCache.CacheStats
    )
}