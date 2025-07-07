package com.claudecodeplus.session

import com.claudecodeplus.session.models.*
import com.claudecodeplus.ui.models.EnhancedMessage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Claude 会话管理器
 */
class ClaudeSessionManager {
    
    private val gson = Gson()
    private val sessionCache = mutableMapOf<String, List<ClaudeSessionMessage>>()
    
    /**
     * 获取项目的会话列表
     */
    suspend fun getSessionList(projectPath: String): List<SessionInfo> = withContext(Dispatchers.IO) {
        // Getting sessions for $projectPath
        val sessionsDir = getSessionsDirectory(projectPath)
        // Session directory: ${sessionsDir.absolutePath}
        // Directory exists: ${sessionsDir.exists()}
        
        if (!sessionsDir.exists()) {
            // Directory does not exist, returning empty list
            return@withContext emptyList()
        }
        
        val files = sessionsDir.listFiles { file -> file.extension == "jsonl" }
        // Found ${files?.size ?: 0} JSONL files
        
        files
            ?.mapNotNull { file ->
                try {
                    val lines = file.readLines()
                    if (lines.isEmpty()) return@mapNotNull null
                    
                    // 解析第一条消息获取会话信息
                    val firstMessage = lines.firstOrNull()?.let { parseMessage(it) }
                    
                    // 找到第一条用户消息作为标题
                    val firstUserMessage = lines.asSequence()
                        .mapNotNull { parseMessage(it) }
                        .firstOrNull { it.type == "user" }
                    
                    SessionInfo(
                        sessionId = firstMessage?.sessionId ?: file.nameWithoutExtension,
                        filePath = file.absolutePath,
                        lastModified = file.lastModified(),
                        messageCount = lines.size,
                        firstMessage = extractMessageText(firstUserMessage),
                        lastMessage = lines.lastOrNull()?.let { parseMessage(it) }?.let { extractMessageText(it) },
                        projectPath = projectPath
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            ?.distinctBy { it.sessionId }  // 去重，避免重复的sessionId
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }
    
    /**
     * 读取会话消息（分页）
     */
    suspend fun readSessionMessages(
        sessionId: String,
        projectPath: String,
        pageSize: Int = 50,
        page: Int = 0
    ): Pair<List<ClaudeSessionMessage>, Int> = withContext(Dispatchers.IO) {
        // 检查缓存
        val cachedMessages = sessionCache[sessionId]
        if (cachedMessages != null) {
            return@withContext paginateMessages(cachedMessages, pageSize, page)
        }
        
        // 从文件读取
        val sessionFile = File(getSessionFilePath(projectPath, sessionId))
        if (!sessionFile.exists()) return@withContext emptyList<ClaudeSessionMessage>() to 0
        
        val allMessages = sessionFile.readLines()
            .mapNotNull { line -> parseMessage(line) }
        
        // 缓存消息
        sessionCache[sessionId] = allMessages
        
        paginateMessages(allMessages, pageSize, page)
    }
    
    /**
     * 创建新会话
     */
    fun createSession(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * 保存消息到会话
     */
    suspend fun saveMessage(
        sessionId: String,
        projectPath: String,
        message: ClaudeSessionMessage
    ) = withContext(Dispatchers.IO) {
        val sessionFile = File(getSessionFilePath(projectPath, sessionId))
        sessionFile.parentFile.mkdirs()
        
        val jsonLine = gson.toJson(message)
        sessionFile.appendText("$jsonLine\n")
        
        // 更新缓存
        sessionCache[sessionId]?.let {
            sessionCache[sessionId] = it + message
        }
    }
    
    /**
     * 删除会话
     */
    suspend fun deleteSession(sessionId: String, projectPath: String) = withContext(Dispatchers.IO) {
        val sessionFile = File(getSessionFilePath(projectPath, sessionId))
        if (sessionFile.exists()) {
            sessionFile.delete()
        }
        sessionCache.remove(sessionId)
    }
    
    /**
     * 获取最近的会话
     */
    suspend fun getRecentSession(projectPath: String): SessionInfo? {
        return getSessionList(projectPath).firstOrNull()
    }
    
    /**
     * 流式读取会话消息
     */
    fun readSessionMessagesFlow(
        sessionId: String,
        projectPath: String,
        pageSize: Int = 50
    ): Flow<List<EnhancedMessage>> = flow {
        var page = 0
        var hasMore = true
        
        while (hasMore) {
            val (messages, total) = readSessionMessages(sessionId, projectPath, pageSize, page)
            
            val enhancedMessages = messages.mapNotNull { it.toEnhancedMessage() }
            if (enhancedMessages.isNotEmpty()) {
                emit(enhancedMessages)
            }
            
            page++
            hasMore = page * pageSize < total
        }
    }
    
    /**
     * 获取会话文件路径
     */
    fun getSessionFilePath(projectPath: String, sessionId: String): String {
        val sessionsDir = getSessionsDirectory(projectPath)
        return File(sessionsDir, "$sessionId.jsonl").absolutePath
    }
    
    private fun getSessionsDirectory(projectPath: String): File {
        // Claude 使用项目路径作为目录名，将 '/' 替换为 '-'
        val encodedPath = projectPath.replace("/", "-")
        val homeDir = System.getProperty("user.home")
        return File(homeDir, ".claude/projects/$encodedPath")
    }
    
    private fun parseMessage(line: String): ClaudeSessionMessage? {
        return try {
            gson.fromJson(line, ClaudeSessionMessage::class.java)
        } catch (e: JsonSyntaxException) {
            null
        }
    }
    
    private fun extractMessageText(message: ClaudeSessionMessage?): String? {
        if (message == null || message.message == null) return null
        
        return when (val content = message.message.content) {
            is String -> content.take(100)
            is List<*> -> {
                content.filterIsInstance<Map<*, *>>()
                    .firstOrNull { it["type"] == "text" }
                    ?.get("text")
                    ?.let { it as? String }
                    ?.take(100)
            }
            else -> null
        }
    }
    
    private fun paginateMessages(
        messages: List<ClaudeSessionMessage>,
        pageSize: Int,
        page: Int
    ): Pair<List<ClaudeSessionMessage>, Int> {
        val totalCount = messages.size
        val startIndex = page * pageSize
        val endIndex = minOf(startIndex + pageSize, totalCount)
        
        return if (startIndex < totalCount) {
            messages.subList(startIndex, endIndex) to totalCount
        } else {
            emptyList<ClaudeSessionMessage>() to totalCount
        }
    }
}