package com.claudecodeplus.sdk

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.future.asDeferred
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * 会话缓存管理器
 * 使用 Caffeine 提供高性能缓存
 */
class SessionCache {
    private val logger = thisLogger()
    
    // 消息缓存：sessionId -> List<SessionMessage>
    private val messageCache: AsyncCache<String, List<ClaudeSessionManager.SessionMessage>> = Caffeine.newBuilder()
        .maximumSize(1000) // 最多缓存1000个会话
        .expireAfterAccess(Duration.ofMinutes(30)) // 30分钟未访问后过期
        .buildAsync()
    
    // 会话元数据缓存
    private val metadataCache: AsyncCache<String, SessionMetadata> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofHours(1)) // 1小时后过期
        .buildAsync()
    
    // 解析后的内容缓存：messageId -> displayContent
    private val contentCache: AsyncCache<String, String> = Caffeine.newBuilder()
        .maximumSize(10000) // 最多缓存10000条消息内容
        .expireAfterAccess(Duration.ofMinutes(10)) // 10分钟未访问后过期
        .buildAsync()
    
    // 文件读取位置缓存
    private val filePositions = ConcurrentHashMap<String, FilePosition>()
    
    data class SessionMetadata(
        val sessionId: String,
        val projectPath: String,
        val createdAt: Long,
        val lastModified: Long,
        val messageCount: Int,
        val compressed: Boolean = false
    )
    
    data class FilePosition(
        var lastFileSize: Long = 0,
        var lastLineCount: Int = 0,
        var lastModified: Long = 0
    )
    
    /**
     * 获取会话消息（带缓存）
     */
    suspend fun getMessages(sessionId: String): List<ClaudeSessionManager.SessionMessage>? {
        return messageCache.get(sessionId) { _, _ -> 
            CompletableFuture.completedFuture(null)
        }.asDeferred().await()
    }
    
    /**
     * 缓存会话消息
     */
    suspend fun putMessages(sessionId: String, messages: List<ClaudeSessionManager.SessionMessage>) {
        messageCache.put(sessionId, CompletableFuture.completedFuture(messages))
        
        // 更新元数据
        updateMetadata(sessionId, messages)
    }
    
    /**
     * 增量更新消息缓存
     */
    suspend fun appendMessages(sessionId: String, newMessages: List<ClaudeSessionManager.SessionMessage>) {
        val existing = getMessages(sessionId) ?: emptyList()
        val updated = existing + newMessages
        putMessages(sessionId, updated)
    }
    
    /**
     * 获取解析后的显示内容（带缓存）
     */
    suspend fun getDisplayContent(messageId: String): String? {
        return contentCache.get(messageId) { _, _ ->
            CompletableFuture.completedFuture(null)
        }.asDeferred().await()
    }
    
    /**
     * 缓存解析后的显示内容
     */
    fun putDisplayContent(messageId: String, content: String) {
        contentCache.put(messageId, CompletableFuture.completedFuture(content))
    }
    
    /**
     * 获取文件读取位置
     */
    fun getFilePosition(sessionId: String): FilePosition {
        return filePositions.computeIfAbsent(sessionId) { FilePosition() }
    }
    
    /**
     * 更新文件读取位置
     */
    fun updateFilePosition(sessionId: String, size: Long, lineCount: Int, modified: Long) {
        val position = getFilePosition(sessionId)
        position.lastFileSize = size
        position.lastLineCount = lineCount
        position.lastModified = modified
    }
    
    /**
     * 清除会话缓存（当检测到文件压缩时）
     */
    suspend fun clearSession(sessionId: String) {
        messageCache.synchronous().invalidate(sessionId)
        metadataCache.synchronous().invalidate(sessionId)
        filePositions.remove(sessionId)
        
        logger.info("Cleared cache for session: $sessionId")
    }
    
    /**
     * 获取会话元数据
     */
    suspend fun getMetadata(sessionId: String): SessionMetadata? {
        return metadataCache.get(sessionId) { _, _ ->
            CompletableFuture.completedFuture(null)
        }.asDeferred().await()
    }
    
    private fun updateMetadata(sessionId: String, messages: List<ClaudeSessionManager.SessionMessage>) {
        val metadata = SessionMetadata(
            sessionId = sessionId,
            projectPath = messages.firstOrNull()?.cwd ?: "",
            createdAt = messages.firstOrNull()?.timestamp?.toLongOrNull() ?: System.currentTimeMillis(),
            lastModified = System.currentTimeMillis(),
            messageCount = messages.size,
            compressed = messages.any { it.compressed == true }
        )
        
        metadataCache.put(sessionId, CompletableFuture.completedFuture(metadata))
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            messageCacheSize = messageCache.synchronous().estimatedSize(),
            contentCacheSize = contentCache.synchronous().estimatedSize(),
            metadataCacheSize = metadataCache.synchronous().estimatedSize(),
            filePositionCount = filePositions.size
        )
    }
    
    data class CacheStats(
        val messageCacheSize: Long,
        val contentCacheSize: Long,
        val metadataCacheSize: Long,
        val filePositionCount: Int
    )
    
    /**
     * 清理所有缓存
     */
    fun cleanUp() {
        messageCache.synchronous().cleanUp()
        contentCache.synchronous().cleanUp()
        metadataCache.synchronous().cleanUp()
        filePositions.clear()
    }
}