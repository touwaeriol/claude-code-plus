package com.claudecodeplus.sdk.session

import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * 简单的会话缓存系统
 * 
 * 使用LRU策略，最多缓存5个会话在内存中
 */
class SimpleSessionCache {
    private val logger = KotlinLogging.logger {}
    private val gson = Gson()
    
    companion object {
        private const val MAX_CACHE_SIZE = 5
    }
    
    // LRU缓存（使用LinkedHashMap实现）
    private val cache = object : LinkedHashMap<String, CachedSession>(
        MAX_CACHE_SIZE + 1, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSession>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }
    
    // 线程安全的缓存访问
    private val cacheLock = Any()
    
    /**
     * 缓存的会话数据
     */
    data class CachedSession(
        val messages: List<ClaudeFileMessage>,
        val lastFileSize: Long,
        val lastModified: Long,
        val lastReadLine: Int  // 用于增量读取
    )
    
    /**
     * 获取会话消息
     */
    suspend fun getSessionMessages(
        sessionId: String, 
        projectPath: String
    ): List<ClaudeFileMessage> {
        val cacheKey = "$projectPath:$sessionId"
        val sessionFile = File(getSessionFilePath(projectPath, sessionId))
        
        synchronized(cacheLock) {
            val cached = cache[cacheKey]
            
            if (cached != null && sessionFile.exists()) {
                // 检查文件是否有变化
                if (sessionFile.lastModified() == cached.lastModified && 
                    sessionFile.length() == cached.lastFileSize) {
                    logger.debug { "Cache hit for session: $sessionId" }
                    return cached.messages
                }
                
                // 文件有变化，增量读取
                try {
                    val newMessages = readIncrementalMessages(sessionFile, cached.lastReadLine)
                    val allMessages = cached.messages + newMessages
                    
                    // 更新缓存
                    cache[cacheKey] = CachedSession(
                        messages = allMessages,
                        lastFileSize = sessionFile.length(),
                        lastModified = sessionFile.lastModified(),
                        lastReadLine = allMessages.size
                    )
                    
                    logger.debug { "Cache updated for session: $sessionId (${newMessages.size} new messages)" }
                    return allMessages
                } catch (e: Exception) {
                    logger.error(e) { "Failed to read incremental messages for $sessionId" }
                    // 失败时返回缓存的数据
                    return cached.messages
                }
            }
            
            // 没有缓存或文件不存在，直接读取
            if (!sessionFile.exists()) {
                logger.debug { "Session file does not exist: $sessionId" }
                return emptyList()
            }
            
            try {
                val messages = readFullSessionFile(sessionFile)
                
                // 存入缓存
                cache[cacheKey] = CachedSession(
                    messages = messages,
                    lastFileSize = sessionFile.length(),
                    lastModified = sessionFile.lastModified(),
                    lastReadLine = messages.size
                )
                
                logger.debug { "Cache loaded for session: $sessionId (${messages.size} messages)" }
                return messages
            } catch (e: Exception) {
                logger.error(e) { "Failed to read session file: $sessionId" }
                return emptyList()
            }
        }
    }
    
    /**
     * 读取完整的会话文件
     */
    private fun readFullSessionFile(file: File): List<ClaudeFileMessage> {
        val messages = mutableListOf<ClaudeFileMessage>()
        
        file.useLines { lines ->
            lines.forEach { line ->
                parseMessage(line)?.let { message ->
                    messages.add(message)
                }
            }
        }
        
        return messages
    }
    
    /**
     * 增量读取新消息
     */
    private fun readIncrementalMessages(file: File, startLine: Int): List<ClaudeFileMessage> {
        val messages = mutableListOf<ClaudeFileMessage>()
        var currentLine = 0
        
        file.useLines { lines ->
            lines.forEach { line ->
                if (currentLine >= startLine) {
                    parseMessage(line)?.let { message ->
                        messages.add(message)
                    }
                }
                currentLine++
            }
        }
        
        return messages
    }
    
    /**
     * 解析单行 JSON 为消息对象
     */
    private fun parseMessage(line: String): ClaudeFileMessage? {
        if (line.isBlank()) return null
        
        return try {
            gson.fromJson(line, ClaudeFileMessage::class.java)
        } catch (e: JsonSyntaxException) {
            logger.debug { "Failed to parse message: $line" }
            null
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error parsing message" }
            null
        }
    }
    
    /**
     * 获取会话文件路径
     */
    private fun getSessionFilePath(projectPath: String, sessionId: String): String {
        // 使用与 SessionFileWatchService 相同的正确编码逻辑
        val encodedPath = com.claudecodeplus.sdk.ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val homeDir = System.getProperty("user.home")
        return File(File(homeDir, ".claude/projects/$encodedPath"), "$sessionId.jsonl").absolutePath
    }
    
    /**
     * 清除指定会话的缓存
     */
    fun invalidate(sessionId: String, projectPath: String) {
        val cacheKey = "$projectPath:$sessionId"
        synchronized(cacheLock) {
            cache.remove(cacheKey)
            logger.debug { "Cache invalidated for session: $sessionId" }
        }
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAll() {
        synchronized(cacheLock) {
            cache.clear()
            logger.info { "All cache cleared" }
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        synchronized(cacheLock) {
            return CacheStats(
                size = cache.size,
                maxSize = MAX_CACHE_SIZE,
                keys = cache.keys.toList()
            )
        }
    }
    
    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val keys: List<String>
    )
}