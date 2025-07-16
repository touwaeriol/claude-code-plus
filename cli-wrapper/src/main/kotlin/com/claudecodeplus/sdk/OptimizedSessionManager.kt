package com.claudecodeplus.sdk

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.claudecodeplus.core.LoggerFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.*
import kotlin.io.path.*

/**
 * 优化的会话管理器
 * 
 * 整合了：
 * - 高性能缓存（Caffeine）
 * - 文件监听（Apache Commons IO）
 * - 事件总线
 * - 响应式流处理
 */
class OptimizedSessionManager {
    private val logger = LoggerFactory.getLogger(OptimizedSessionManager::class.java)
    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
    
    // 缓存管理器
    private val cache = SessionCache()
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private val CLAUDE_HOME = Paths.get(System.getProperty("user.home"), ".claude")
        private val PROJECTS_DIR = CLAUDE_HOME.resolve("projects")
    }
    
    /**
     * 获取项目的会话目录
     * 基于提供的原理：projectPath -> projectDirectory
     */
    private fun getProjectDirectory(projectPath: String): Path {
        val projectDirName = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        return PROJECTS_DIR.resolve(projectDirName)
    }
    
    /**
     * 列出所有会话文件
     * 返回文件路径和修改时间
     */
    private suspend fun listSessionFiles(projectDir: Path): List<SessionFileInfo> = withContext(Dispatchers.IO) {
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            logger.warn("Project directory not found: $projectDir")
            return@withContext emptyList()
        }
        
        try {
            projectDir.listDirectoryEntries("*.jsonl")
                .filter { it.isRegularFile() }
                .map { file ->
                    SessionFileInfo(
                        path = file,
                        sessionId = file.nameWithoutExtension,
                        modifiedTime = file.getLastModifiedTime().toMillis()
                    )
                }
        } catch (e: Exception) {
            logger.error("Error listing session files", e)
            emptyList()
        }
    }
    
    /**
     * 获取最新的会话
     * 实现提供的原理
     */
    /**
     * 获取指定项目的最新会话。
     *
     * 这个函数解决了多个项目路径可能映射到同一个会话存储目录的复杂问题。
     * 例如 `/path/to/a-b` 和 `/path/to/a/b` 可能会生成相同的目录名。
     * 
     * 实现逻辑:
     * 1. 根据 projectPath 计算出对应的会话目录。
     * 2. 并发地检查目录中每个会话文件的归属：
     *    a. **缓存优先**: 首先检查 `ownershipCache`，如果找到所有者，则直接比对，避免文件IO。
     *    b. **文件读取**: 如果缓存未命中，则异步读取文件的第一行，解析出 `cwd` (current working directory) 作为其所有者。
     *    c. **缓存更新**: 将新发现的所有权信息存入缓存，供后续快速查找。
     * 3. 从所有权与当前 `projectPath` 匹配的会话中，选出修改时间最新的一个。
     * 4. 加载并返回这个最新的会话。
     *
     * @param projectPath 要获取会话的项目的绝对路径。
     * @return 如果找到匹配的会话，则返回 `SessionInfo`，否则返回 `null`。
     */
    suspend fun getMostRecentSession(projectPath: String): SessionInfo? {
        val projectDir = getProjectDirectory(projectPath)
        logger.info("正在为项目 '$projectPath' 在目录 '$projectDir' 中查找会话...")

        val sessionFiles = listSessionFiles(projectDir)
        if (sessionFiles.isEmpty()) {
            logger.info("目录 '$projectDir' 中没有找到任何会话文件。")
            return null
        }

        logger.info("在 '$projectDir' 中找到 ${sessionFiles.size} 个会话文件，正在并发验证所有权...")

        // 使用协程并发处理所有文件，以提高效率
        val matchingSessions = coroutineScope {
            sessionFiles.map { fileInfo ->
                async(Dispatchers.IO) { // 在IO线程池中执行每个文件的检查
                    // 1. 缓存优先：检查所有权是否已被缓存
                    val cachedOwner = cache.getOwner(fileInfo.sessionId)
                    if (cachedOwner != null) {
                        logger.debug("会话 ${fileInfo.sessionId} 的所有权已缓存: $cachedOwner")
                        if (cachedOwner == projectPath) fileInfo else null
                    } else {
                        // 2. 缓存未命中：读取文件确定所有权
                        try {
                            logger.debug("缓存未命中，正在读取文件 ${fileInfo.path.fileName} 以确定所有权...")
                            val firstLine = fileInfo.path.toFile().bufferedReader().use { it.readLine() }
                            if (firstLine.isNullOrBlank()) {
                                null
                            } else {
                                val message = ClaudeSessionManager.parseSessionMessage(firstLine)
                                val actualOwner = if (message is ClaudeSessionManager.SessionMessage.SystemMessage && message.subtype == "init") {
                                    message.cwd
                                } else {
                                    // 对于旧格式或无法识别的格式，无法确定所有者
                                    null
                                }

                                if (actualOwner != null) {
                                    // 3. 缓存更新：将新发现的所有权存入缓存
                                    logger.info("已确定会话 ${fileInfo.sessionId} 的所有者为 '$actualOwner'，并已缓存。")
                                    cache.putOwner(fileInfo.sessionId, actualOwner)
                                } else {
                                    logger.warn("无法从文件 ${fileInfo.path.fileName} 的第一行确定所有者。")
                                }

                                if (actualOwner == projectPath) fileInfo else null
                            }
                        } catch (e: Exception) {
                            logger.warn("读取或解析文件 ${fileInfo.path.fileName} 的第一行失败。", e)
                            null
                        }
                    }
                }
            }.mapNotNull { it.await() } // 等待所有检查完成并过滤掉不匹配的结果
        }

        if (matchingSessions.isEmpty()) {
            logger.info("在目录 '$projectDir' 中没有找到专门属于项目 '$projectPath' 的会话文件。")
            return null
        }

        // 4. 从匹配的会话中选出最新的一个
        val mostRecent = matchingSessions.sortedByDescending { it.modifiedTime }.first()
        logger.info("已找到该项目的最新会话: ${mostRecent.sessionId} (修改于 ${mostRecent.modifiedTime})，路径: ${mostRecent.path}")

        return loadSession(projectPath, mostRecent)
    }
    
    /**
     * 加载会话内容
     * 优先从缓存读取，缓存未命中则从文件读取
     */
    private suspend fun loadSession(projectPath: String, fileInfo: SessionFileInfo): SessionInfo? {
        // 先尝试从缓存读取
        val cachedMessages = cache.getMessages(fileInfo.sessionId)
        if (cachedMessages != null) {
            logger.debug("Loaded session from cache: ${fileInfo.sessionId}")
            return SessionInfo(
                sessionId = fileInfo.sessionId,
                projectPath = projectPath,
                messages = cachedMessages,
                lastModified = fileInfo.modifiedTime
            )
        }
        
        // 从文件读取
        return try {
            val messages = readSessionFile(fileInfo.path)
            
            // 缓存结果
            cache.putMessages(fileInfo.sessionId, messages)
            
            SessionInfo(
                sessionId = fileInfo.sessionId,
                projectPath = projectPath,
                messages = messages,
                lastModified = fileInfo.modifiedTime
            )
        } catch (e: Exception) {
            logger.error("Error loading session: ${fileInfo.sessionId}", e)
            null
        }
    }
    
    /**
     * 读取会话文件
     */
    private suspend fun readSessionFile(sessionFile: Path): List<ClaudeSessionManager.SessionMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<ClaudeSessionManager.SessionMessage>()
        
        sessionFile.readLines()
            .filter { it.isNotBlank() }
            .forEach { line ->
                ClaudeSessionManager.parseSessionMessage(line)?.let { message ->
                    messages.add(message)
                }
            }
        
        logger.info("Read ${messages.size} messages from session file: ${sessionFile.fileName}")
        messages
    }
    
    /**
     * 批量获取会话历史
     * 支持分页和过滤
     */
    suspend fun getSessionHistory(
        projectPath: String,
        sessionId: String,
        offset: Int = 0,
        limit: Int = 100,
        filter: (ClaudeSessionManager.SessionMessage) -> Boolean = { true }
    ): SessionHistory {
        val messages = cache.getMessages(sessionId) 
            ?: loadSessionMessages(projectPath, sessionId)
            ?: emptyList()
        
        val filtered = messages.filter(filter)
        val total = filtered.size
        val page = filtered.drop(offset).take(limit)
        
        return SessionHistory(
            sessionId = sessionId,
            messages = page,
            offset = offset,
            limit = limit,
            total = total,
            hasMore = offset + limit < total
        )
    }
    
    /**
     * 加载会话消息
     */
    private suspend fun loadSessionMessages(projectPath: String, sessionId: String): List<ClaudeSessionManager.SessionMessage>? {
        val projectDir = getProjectDirectory(projectPath)
        val sessionFile = projectDir.resolve("$sessionId.jsonl")
        
        return if (sessionFile.exists()) {
            val messages = readSessionFile(sessionFile)
            cache.putMessages(sessionId, messages)
            messages
        } else {
            null
        }
    }
    
    /**
     * 提取并缓存显示内容
     */
    suspend fun extractDisplayContent(message: ClaudeSessionManager.SessionMessage): String? {
        // 生成消息ID
        val messageId = "${message.sessionId}-${message.uuid}"
        
        // 先从缓存读取
        cache.getDisplayContent(messageId)?.let { return it }
        
        // 提取内容
        val content = when (message) {
            is ClaudeSessionManager.SessionMessage.AssistantMessage -> {
                val rawContent = message.message?.content
                when (rawContent) {
                    is String -> rawContent
                    is List<*> -> {
                        rawContent.mapNotNull { item ->
                            when (item) {
                                is Map<*, *> -> {
                                    if (item["type"] == "text") {
                                        item["text"] as? String
                                    } else null
                                }
                                else -> null
                            }
                        }.joinToString("")
                    }
                    else -> null
                }
            }
            is ClaudeSessionManager.SessionMessage.UserMessage -> {
                when (val rawContent = message.message?.content) {
                    is String -> rawContent
                    is List<*> -> {
                        // 用户消息也可能包含工具结果等
                        rawContent.mapNotNull { item ->
                            when (item) {
                                is Map<*, *> -> {
                                    when (item["type"]) {
                                        "text" -> item["text"] as? String
                                        "tool_result" -> "[Tool Result: ${item["content"]}]"
                                        else -> null
                                    }
                                }
                                else -> null
                            }
                        }.joinToString("\n")
                    }
                    else -> null
                }
            }
            is ClaudeSessionManager.SessionMessage.SystemMessage -> {
                when (message.subtype) {
                    "init" -> "Session initialized with model: ${message.model}"
                    else -> "System: ${message.subtype}"
                }
            }
            is ClaudeSessionManager.SessionMessage.SummaryMessage -> message.summary
            is ClaudeSessionManager.SessionMessage.ResultMessage -> {
                if (message.is_error == true) {
                    "Error: ${message.result}"
                } else {
                    "Result: ${message.result}"
                }
            }
            is ClaudeSessionManager.SessionMessage.UnknownMessage -> "Unknown message type: ${message.type}"
        }
        
        // 缓存提取的内容
        content?.let { cache.putDisplayContent(messageId, it) }
        
        return content
    }
    
    /**
     * 获取缓存统计
     */
    fun getCacheStats() = cache.getCacheStats()
    
    /**
     * 清理资源
     */
    fun cleanup() {
        cache.cleanUp()
        scope.cancel()
    }
    
    // 数据类定义
    data class SessionFileInfo(
        val path: Path,
        val sessionId: String,
        val modifiedTime: Long
    )
    
    data class SessionInfo(
        val sessionId: String,
        val projectPath: String,
        val messages: List<ClaudeSessionManager.SessionMessage>,
        val lastModified: Long
    )
    
    data class SessionHistory(
        val sessionId: String,
        val messages: List<ClaudeSessionManager.SessionMessage>,
        val offset: Int,
        val limit: Int,
        val total: Int,
        val hasMore: Boolean
    )
    
    sealed class SessionUpdate {
        data class NewMessage(val message: ClaudeSessionManager.SessionMessage) : SessionUpdate()
        data class Compressed(val messageCount: Int) : SessionUpdate()
        data class Error(val error: Throwable) : SessionUpdate()
    }
}