package com.asakii.server.services

import com.asakii.logging.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger = getLogger("FileContentCache")

/**
 * 文件内容缓存服务
 *
 * 用于在 Edit/Write 工具执行前保存原始文件内容，
 * 以便在显示 Diff 时能够准确展示修改前后的对比。
 *
 * 缓存以 toolUseId 为 key，存储原始文件内容。
 * 
 * 安全特性：
 * - 最大缓存条目限制，防止内存泄漏
 * - 自动过期清理
 * - LRU 驱逐策略
 */
object FileContentCache {

    /**
     * 缓存条目
     */
    data class CacheEntry(
        val filePath: String,
        val originalContent: String,
        val timestamp: Long = System.currentTimeMillis(),
        var lastAccessTime: Long = System.currentTimeMillis()
    )

    // toolUseId -> CacheEntry
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    // 缓存过期时间（30分钟）
    private const val CACHE_EXPIRY_MS = 30 * 60 * 1000L
    
    // 最大缓存条目数（防止无边界增长）
    private const val MAX_CACHE_SIZE = 500
    
    // 当达到最大容量时，一次性驱逐的条目数量
    private const val EVICTION_BATCH_SIZE = 50
    
    // 定时清理调度器
    private val cleanupScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "FileContentCache-Cleanup").apply { isDaemon = true }
    }
    
    // 是否已启动定时清理
    @Volatile
    private var cleanupScheduled = false

    /**
     * 保存文件的原始内容
     *
     * @param toolUseId 工具调用 ID
     * @param filePath 文件路径
     * @return true 如果成功保存，false 如果文件不存在或读取失败
     */
    fun saveOriginalContent(toolUseId: String, filePath: String): Boolean {
        // 确保定时清理已启动
        ensureCleanupScheduled()
        
        // 检查缓存容量，必要时进行驱逐
        if (cache.size >= MAX_CACHE_SIZE) {
            evictOldestEntries()
        }
        
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                logger.debug { "[FileContentCache] 文件不存在，跳过缓存: $filePath" }
                return false
            }

            val content = file.readText(Charsets.UTF_8)
            cache[toolUseId] = CacheEntry(
                filePath = filePath,
                originalContent = content
            )
            logger.info { "[FileContentCache] 已缓存原始内容: toolUseId=$toolUseId, filePath=$filePath, size=${content.length}, cacheSize=${cache.size}" }
            true
        } catch (e: Exception) {
            logger.warn { "[FileContentCache] 缓存失败: toolUseId=$toolUseId, filePath=$filePath, error=${e.message}" }
            false
        }
    }
    
    /**
     * 确保定时清理任务已启动
     */
    private fun ensureCleanupScheduled() {
        if (!cleanupScheduled) {
            synchronized(this) {
                if (!cleanupScheduled) {
                    cleanupScheduler.scheduleAtFixedRate(
                        { cleanupExpired() },
                        5, 5, TimeUnit.MINUTES
                    )
                    cleanupScheduled = true
                    logger.info { "[FileContentCache] 定时清理任务已启动（每5分钟执行一次）" }
                }
            }
        }
    }
    
    /**
     * 驱逐最旧的条目（LRU 策略）
     */
    private fun evictOldestEntries() {
        val entriesToEvict = cache.entries
            .sortedBy { it.value.lastAccessTime }
            .take(EVICTION_BATCH_SIZE)
            .map { it.key }
        
        entriesToEvict.forEach { cache.remove(it) }
        
        if (entriesToEvict.isNotEmpty()) {
            logger.info { "[FileContentCache] LRU 驱逐了 ${entriesToEvict.size} 个最旧条目，当前缓存大小: ${cache.size}" }
        }
    }

    /**
     * 获取缓存的原始内容
     *
     * @param toolUseId 工具调用 ID
     * @return 原始内容，如果不存在则返回 null
     */
    fun getOriginalContent(toolUseId: String): String? {
        val entry = cache[toolUseId]
        if (entry == null) {
            logger.debug { "[FileContentCache] 缓存未命中: toolUseId=$toolUseId" }
            return null
        }

        // 检查是否过期
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRY_MS) {
            logger.debug { "[FileContentCache] 缓存已过期: toolUseId=$toolUseId" }
            cache.remove(toolUseId)
            return null
        }

        // 更新最后访问时间（用于 LRU 驱逐）
        entry.lastAccessTime = System.currentTimeMillis()
        
        logger.debug { "[FileContentCache] 缓存命中: toolUseId=$toolUseId, filePath=${entry.filePath}" }
        return entry.originalContent
    }

    /**
     * 获取缓存条目
     */
    fun getEntry(toolUseId: String): CacheEntry? {
        return cache[toolUseId]
    }

    /**
     * 移除缓存条目
     */
    fun remove(toolUseId: String) {
        cache.remove(toolUseId)
        logger.debug { "[FileContentCache] 已移除缓存: toolUseId=$toolUseId" }
    }

    /**
     * 清理过期缓存
     */
    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expiredKeys = cache.entries
            .filter { now - it.value.timestamp > CACHE_EXPIRY_MS }
            .map { it.key }

        expiredKeys.forEach { cache.remove(it) }

        if (expiredKeys.isNotEmpty()) {
            logger.info { "[FileContentCache] 清理了 ${expiredKeys.size} 个过期缓存" }
        }
    }

    /**
     * 清空所有缓存
     */
    fun clear() {
        cache.clear()
        logger.info { "[FileContentCache] 已清空所有缓存" }
    }

    /**
     * 获取缓存大小
     */
    fun size(): Int = cache.size
}
