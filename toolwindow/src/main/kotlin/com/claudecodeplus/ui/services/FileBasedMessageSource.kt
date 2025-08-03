package com.claudecodeplus.ui.services

import com.claudecodeplus.session.models.ClaudeSessionMessage
import com.claudecodeplus.session.models.MessageContent as SessionMessageContent
import com.claudecodeplus.sdk.session.ClaudeFileMessage
import com.claudecodeplus.sdk.session.MessageContent
import com.claudecodeplus.sdk.session.TimestampUtils
import com.claudecodeplus.ui.models.EnhancedMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * 基于文件的消息源实现
 * 
 * 通过监听 Claude 会话文件的变化来获取消息
 * 支持历史消息加载和实时消息更新
 */
class FileBasedMessageSource(
    private val fileWatchService: SessionFileWatchService,
    private val scope: CoroutineScope,
    private val config: MessageSourceConfig = MessageSourceConfig()
) : UnifiedMessageSource {
    
    private val logger = KotlinLogging.logger {}
    private val messageConverter = MessageFlowConverter()
    private val errorHandler = FileSystemErrorHandler()
    
    // 消息缓存（sessionId -> List<EnhancedMessage>）
    private val messageCache = if (config.enableCache) {
        ConcurrentHashMap<String, MutableList<EnhancedMessage>>()
    } else null
    
    // 活跃的订阅（sessionId -> Job）
    private val activeSubscriptions = ConcurrentHashMap<String, Job>()
    
    override suspend fun loadHistoricalMessages(
        sessionId: String,
        projectPath: String,
        limit: Int?
    ): List<EnhancedMessage> {
        logger.debug { "Loading historical messages for session: $sessionId" }
        
        // 检查缓存
        messageCache?.get(sessionId)?.let { cached ->
            if (cached.isNotEmpty()) {
                logger.debug { "Returning ${cached.size} messages from cache" }
                return if (limit != null) cached.take(limit) else cached
            }
        }
        
        // 从文件加载
        val messages = loadMessagesFromFile(sessionId, projectPath)
        
        // 更新缓存
        if (config.enableCache && messages.isNotEmpty()) {
            updateCache(sessionId, messages)
        }
        
        return if (limit != null) messages.take(limit) else messages
    }
    
    override fun subscribeToNewMessages(
        sessionId: String,
        projectPath: String
    ): Flow<EnhancedMessage> {
        logger.info { "Subscribing to new messages for session: $sessionId" }
        
        // 启动项目监听（如果尚未启动）
        fileWatchService.startWatchingProject(projectPath)
        
        // 创建消息流
        return fileWatchService.subscribeToSession(sessionId)
            .map { newMessages ->
                // 转换消息 - 将 ClaudeSessionMessage 转为 ClaudeFileMessage
                val fileMessages = newMessages.map { sessionMessage ->
                    ClaudeFileMessage(
                        type = sessionMessage.type,
                        sessionId = sessionMessage.sessionId,
                        timestamp = sessionMessage.timestamp, // 保持为 String
                        message = MessageContent(
                            role = sessionMessage.message.role,
                            content = sessionMessage.message.content
                        )
                    )
                }
                val enhancedMessages = messageConverter.convertMessages(fileMessages, sessionId)
                
                // 更新缓存
                if (config.enableCache && enhancedMessages.isNotEmpty()) {
                    appendToCache(sessionId, enhancedMessages)
                }
                
                enhancedMessages
            }
            .flatMapConcat { it.asFlow() }
            .onStart {
                logger.debug { "Started subscription for session: $sessionId" }
            }
            .onCompletion { cause ->
                logger.debug { "Completed subscription for session: $sessionId, cause: $cause" }
            }
            .catch { e ->
                logger.error(e) { "Error in message subscription for session: $sessionId" }
                emitAll(emptyFlow())
            }
            .shareIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 0
            )
    }
    
    override suspend fun sessionExists(
        sessionId: String,
        projectPath: String
    ): Boolean {
        return fileWatchService.sessionFileExists(projectPath, sessionId)
    }
    
    override suspend fun getMessageCount(
        sessionId: String,
        projectPath: String
    ): Int {
        // 优先从缓存获取
        messageCache?.get(sessionId)?.let { return it.size }
        
        // 否则需要读取文件
        val filePath = fileWatchService.getSessionFilePath(projectPath, sessionId)
        val file = File(filePath)
        
        return if (file.exists()) {
            withContext(Dispatchers.IO) {
                file.useLines { it.count() }
            }
        } else {
            0
        }
    }
    
    override fun cleanup() {
        logger.info { "Cleaning up FileBasedMessageSource" }
        
        // 取消所有订阅
        activeSubscriptions.values.forEach { it.cancel() }
        activeSubscriptions.clear()
        
        // 清理缓存
        messageCache?.clear()
        messageConverter.clearAllCaches()
    }
    
    override fun getSourceType(): MessageSourceType = MessageSourceType.FILE_BASED
    
    /**
     * 从文件加载消息
     */
    private suspend fun loadMessagesFromFile(
        sessionId: String,
        projectPath: String
    ): List<EnhancedMessage> = withContext(Dispatchers.IO) {
        val tracker = fileWatchService.getOrCreateTracker(sessionId, projectPath)
        
        // 使用错误处理器进行重试
        val result = errorHandler.withRetry<List<ClaudeSessionMessage>>(
            config = FileSystemErrorHandler.RetryConfig(
                maxAttempts = config.maxRetries,
                initialDelay = config.retryDelay.milliseconds
            )
        ) {
            tracker.readAllMessages()
        }
        
        when (result) {
            is FileSystemErrorHandler.OperationResult.Success -> {
                val sessionMessages = result.value
                logger.debug { "Loaded ${sessionMessages.size} messages from file" }
                
                // 转换为 EnhancedMessage - 将 ClaudeSessionMessage 转为 ClaudeFileMessage
                val fileMessages = sessionMessages.map { sessionMessage ->
                    ClaudeFileMessage(
                        type = sessionMessage.type,
                        sessionId = sessionMessage.sessionId,
                        timestamp = sessionMessage.timestamp, // 保持为 String
                        message = MessageContent(
                            role = sessionMessage.message.role,
                            content = sessionMessage.message.content
                        )
                    )
                }
                messageConverter.convertMessages(fileMessages, sessionId)
            }
            
            is FileSystemErrorHandler.OperationResult.Failure -> {
                logger.error { "Failed to load messages after ${result.attempts} attempts: ${result.error}" }
                emptyList()
            }
        }
    }
    
    /**
     * 更新缓存
     */
    private fun updateCache(sessionId: String, messages: List<EnhancedMessage>) {
        messageCache?.let { cache ->
            val cachedList = cache.getOrPut(sessionId) { mutableListOf() }
            cachedList.clear()
            cachedList.addAll(messages.takeLast(config.cacheSize))
            
            logger.trace { "Updated cache for session $sessionId: ${cachedList.size} messages" }
        }
    }
    
    /**
     * 追加到缓存
     */
    private fun appendToCache(sessionId: String, messages: List<EnhancedMessage>) {
        messageCache?.let { cache ->
            val cachedList = cache.getOrPut(sessionId) { mutableListOf() }
            cachedList.addAll(messages)
            
            // 限制缓存大小
            if (cachedList.size > config.cacheSize) {
                val toRemove = cachedList.size - config.cacheSize
                repeat(toRemove) { cachedList.removeAt(0) }
            }
            
            logger.trace { "Appended ${messages.size} messages to cache for session $sessionId" }
        }
    }
    
    /**
     * 清除会话缓存
     */
    fun clearSessionCache(sessionId: String) {
        messageCache?.remove(sessionId)
        messageConverter.clearSessionCache(sessionId)
        logger.debug { "Cleared cache for session: $sessionId" }
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            totalSessions = messageCache?.size ?: 0,
            totalMessages = messageCache?.values?.sumOf { it.size } ?: 0,
            cacheEnabled = config.enableCache
        )
    }
    
    /**
     * 缓存统计信息
     */
    data class CacheStats(
        val totalSessions: Int,
        val totalMessages: Int,
        val cacheEnabled: Boolean
    )
}