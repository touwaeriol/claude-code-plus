package com.claudecodeplus.sdk.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 简单的资源管理器
 * 
 * 负责定时清理不需要的资源
 */
class SimpleResourceManager(
    private val fileWatchService: SessionFileWatchService,
    private val sessionLoader: SessionLoader,
    private val scope: CoroutineScope
) {
    private val logger = KotlinLogging.logger {}
    private val isRunning = AtomicBoolean(false)
    
    companion object {
        private const val CLEANUP_INTERVAL_MS = 5 * 60 * 1000L // 5分钟
    }
    
    /**
     * 启动定时清理
     */
    fun startPeriodicCleanup() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info { "Starting periodic resource cleanup" }
            
            scope.launch {
                while (isRunning.get()) {
                    try {
                        cleanup()
                        delay(CLEANUP_INTERVAL_MS)
                    } catch (e: Exception) {
                        logger.error(e) { "Error during periodic cleanup" }
                        delay(CLEANUP_INTERVAL_MS) // 即使出错也要等待
                    }
                }
            }
        }
    }
    
    /**
     * 停止定时清理
     */
    fun stopPeriodicCleanup() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info { "Stopped periodic resource cleanup" }
        }
    }
    
    /**
     * 执行一次清理
     */
    fun cleanup() {
        logger.debug { "Starting resource cleanup" }
        
        try {
            // 1. 清理不活跃的项目监听
            fileWatchService.cleanupInactiveProjects()
            
            // 2. 清理不需要的实时订阅（在实际应用中可以根据需要扩展）
            // sessionLoader.cleanupInactiveSubscriptions()
            
            logger.debug { "Resource cleanup completed" }
        } catch (e: Exception) {
            logger.error(e) { "Error during resource cleanup" }
        }
    }
    
    /**
     * 内存压力下的降级策略
     */
    fun handleMemoryPressure() {
        logger.warn { "Handling memory pressure" }
        
        try {
            // 1. 清除所有缓存
            // cache.clearAll() // 如果需要的话
            
            // 2. 清理所有实时订阅
            sessionLoader.cleanup()
            
            // 3. 强制执行一次清理
            cleanup()
            
            logger.info { "Memory pressure handling completed" }
        } catch (e: Exception) {
            logger.error(e) { "Error handling memory pressure" }
        }
    }
    
    /**
     * 关闭资源管理器
     */
    fun shutdown() {
        logger.info { "Shutting down resource manager" }
        
        stopPeriodicCleanup()
        
        // 清理所有资源
        sessionLoader.cleanup()
        fileWatchService.stopAll()
        
        logger.info { "Resource manager shutdown completed" }
    }
    
    /**
     * 获取资源使用统计
     */
    fun getResourceStats(): ResourceStats {
        val loaderStats = sessionLoader.getStats()
        
        return ResourceStats(
            isPeriodicCleanupRunning = isRunning.get(),
            realtimeSubscriptions = loaderStats.realtimeSubscriptions.size,
            cachedSessions = loaderStats.cacheStats.size,
            maxCacheSize = loaderStats.cacheStats.maxSize
        )
    }
    
    data class ResourceStats(
        val isPeriodicCleanupRunning: Boolean,
        val realtimeSubscriptions: Int,
        val cachedSessions: Int,
        val maxCacheSize: Int
    )
}