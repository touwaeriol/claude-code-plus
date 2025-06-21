package com.claudecodeplus.sdk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.RandomAccessFile
import java.nio.file.*
import java.security.MessageDigest
import kotlin.io.path.*

/**
 * Claude 会话管理器
 * 
 * 基于 Claude CLI 的会话管理机制：
 * 1. 会话存储在 ~/.claude/projects/{projectDirName}/ 目录下
 * 2. 每个会话是一个 {sessionId}.jsonl 文件
 * 3. 通过监听文件变化来获取实时的会话更新
 * 
 * 性能优化：
 * - 使用 RandomAccessFile 只读取新增内容
 * - 缓存已读取的消息，避免重复解析
 * - 使用轮询而非 WatchService，更好地处理文件重写
 * 
 * 上下文压缩处理：
 * - Claude CLI 会在达到上下文限制时压缩历史消息
 * - 压缩时会重写整个会话文件
 * - 通过检测文件大小减小来识别压缩
 */
class ClaudeSessionManager {
    private val logger = thisLogger()
    private val objectMapper = jacksonObjectMapper()
    
    companion object {
        private val CLAUDE_HOME = Paths.get(System.getProperty("user.home"), ".claude")
        private val PROJECTS_DIR = CLAUDE_HOME.resolve("projects")
        
        // 监听间隔（毫秒）
        private const val WATCH_INTERVAL = 500L
    }
    
    data class SessionMessage(
        val parentUuid: String? = null,
        val isSidechain: Boolean? = null,
        val userType: String? = null,
        val cwd: String? = null,
        val sessionId: String? = null,
        val version: String? = null,
        val type: String,  // "user" or "assistant"
        val message: Message? = null,
        val uuid: String? = null,
        val timestamp: String? = null,
        val requestId: String? = null,
        val compressed: Boolean? = null  // 标记是否为压缩后的消息
    )
    
    data class Message(
        val role: String? = null,
        val content: Any? = null,  // String or List<ContentBlock>
        val id: String? = null,
        val type: String? = null,
        val model: String? = null
    )
    
    data class SessionState(
        var lastFileSize: Long = 0,
        var lastLineCount: Int = 0,
        var lastModified: Long = 0,
        val messageCache: MutableList<SessionMessage> = mutableListOf()
    )
    
    private val sessionStates = mutableMapOf<String, SessionState>()
    
    /**
     * 获取项目的会话目录名
     * Claude 使用项目路径并将 / 替换为 - 作为目录名
     * 例如: /home/user/project -> -home-user-project
     */
    private fun getProjectDirName(projectPath: String): String {
        return ProjectPathUtils.projectPathToDirectoryName(projectPath)
    }
    
    /**
     * 获取项目的会话目录
     */
    fun getProjectSessionDir(projectPath: String): Path? {
        val projectDirName = getProjectDirName(projectPath)
        val projectDir = PROJECTS_DIR.resolve(projectDirName)
        
        return if (projectDir.exists() && projectDir.isDirectory()) {
            logger.info("Found project session directory: $projectDir")
            projectDir
        } else {
            logger.warn("Project session directory not found: $projectDir")
            null
        }
    }
    
    /**
     * 获取最新的会话ID
     */
    fun getLatestSessionId(projectPath: String): String? {
        val projectDir = getProjectSessionDir(projectPath) ?: return null
        
        try {
            val sessionFiles = projectDir.listDirectoryEntries("*.jsonl")
                .filter { it.isRegularFile() }
                .sortedByDescending { it.getLastModifiedTime() }
            
            return sessionFiles.firstOrNull()?.let { sessionFile ->
                val sessionId = sessionFile.nameWithoutExtension
                logger.info("Found latest session: $sessionId")
                sessionId
            }
        } catch (e: Exception) {
            logger.error("Error finding latest session", e)
            return null
        }
    }
    
    /**
     * 高效读取会话历史
     * 只读取新增的内容，避免重复解析整个文件
     */
    fun readSessionHistory(projectPath: String, sessionId: String): List<SessionMessage> {
        val projectDir = getProjectSessionDir(projectPath) ?: return emptyList()
        val sessionFile = projectDir.resolve("$sessionId.jsonl")
        
        if (!sessionFile.exists()) {
            logger.warn("Session file not found: $sessionFile")
            return emptyList()
        }
        
        val state = sessionStates.getOrPut(sessionId) { SessionState() }
        val currentSize = sessionFile.fileSize()
        val currentModified = sessionFile.getLastModifiedTime().toMillis()
        
        // 检测文件是否被重写（上下文压缩）
        if (currentSize < state.lastFileSize || currentModified > state.lastModified + 1000) {
            logger.info("Session file was rewritten (possibly compressed), reloading all messages")
            state.messageCache.clear()
            state.lastFileSize = 0
            state.lastLineCount = 0
        }
        
        // 如果缓存中已有数据且文件未变化，直接返回缓存
        if (state.lastFileSize == currentSize && state.messageCache.isNotEmpty()) {
            return state.messageCache.toList()
        }
        
        try {
            RandomAccessFile(sessionFile.toFile(), "r").use { file ->
                // 跳到上次读取的位置
                if (state.lastFileSize > 0 && state.lastFileSize < currentSize) {
                    file.seek(state.lastFileSize)
                }
                
                // 读取新增的行
                var line: String?
                while (file.readLine().also { line = it } != null) {
                    if (!line.isNullOrBlank()) {
                        try {
                            val message = objectMapper.readValue<SessionMessage>(line!!)
                            state.messageCache.add(message)
                            state.lastLineCount++
                        } catch (e: Exception) {
                            logger.warn("Failed to parse session line: $line", e)
                        }
                    }
                }
                
                state.lastFileSize = currentSize
                state.lastModified = currentModified
            }
            
            return state.messageCache.toList()
        } catch (e: Exception) {
            logger.error("Error reading session history", e)
            return state.messageCache.toList()
        }
    }
    
    /**
     * 高性能监听会话文件变化
     * 使用轮询而不是 WatchService，因为需要处理文件重写的情况
     */
    fun watchSessionFile(projectPath: String, sessionId: String): Flow<SessionMessage> = flow {
        val projectDir = getProjectSessionDir(projectPath) ?: return@flow
        val sessionFile = projectDir.resolve("$sessionId.jsonl")
        
        if (!sessionFile.exists()) {
            logger.warn("Session file not found for watching: $sessionFile")
            return@flow
        }
        
        val state = sessionStates.getOrPut(sessionId) { SessionState() }
        
        // 初始化状态
        state.lastFileSize = sessionFile.fileSize()
        state.lastModified = sessionFile.getLastModifiedTime().toMillis()
        
        withContext(Dispatchers.IO) {
            while (currentCoroutineContext().isActive) {
                try {
                    val currentSize = sessionFile.fileSize()
                    val currentModified = sessionFile.getLastModifiedTime().toMillis()
                    
                    // 检测文件变化
                    when {
                        // 文件被重写（压缩）
                        currentSize < state.lastFileSize -> {
                            logger.info("Session file was compressed, reloading")
                            handleFileRewrite(sessionFile, state)
                        }
                        // 文件有新增内容
                        currentSize > state.lastFileSize -> {
                            handleNewContent(sessionFile, state, currentSize)?.let { messages ->
                                messages.forEach { emit(it) }
                            }
                        }
                        // 文件被修改但大小未变（可能是编辑）
                        currentModified > state.lastModified -> {
                            logger.debug("File modified but size unchanged")
                            state.lastModified = currentModified
                        }
                    }
                    
                } catch (e: Exception) {
                    logger.error("Error in session watch loop", e)
                }
                
                delay(WATCH_INTERVAL)
            }
        }
    }
    
    /**
     * 处理文件重写（上下文压缩）
     */
    private suspend fun handleFileRewrite(sessionFile: Path, state: SessionState) {
        state.messageCache.clear()
        state.lastFileSize = 0
        state.lastLineCount = 0
        
        // 重新读取整个文件
        try {
            sessionFile.readLines()
                .filter { it.isNotBlank() }
                .forEach { line ->
                    try {
                        val message = objectMapper.readValue<SessionMessage>(line)
                        state.messageCache.add(message.copy(compressed = true))
                    } catch (e: Exception) {
                        logger.warn("Failed to parse compressed session line: $line", e)
                    }
                }
            
            state.lastFileSize = sessionFile.fileSize()
            state.lastModified = sessionFile.getLastModifiedTime().toMillis()
            
            logger.info("Reloaded ${state.messageCache.size} messages after compression")
        } catch (e: Exception) {
            logger.error("Error handling file rewrite", e)
        }
    }
    
    /**
     * 处理新增内容
     */
    private fun handleNewContent(
        sessionFile: Path, 
        state: SessionState, 
        currentSize: Long
    ): List<SessionMessage>? {
        val newMessages = mutableListOf<SessionMessage>()
        
        try {
            RandomAccessFile(sessionFile.toFile(), "r").use { file ->
                // 跳到上次读取的位置
                file.seek(state.lastFileSize)
                
                var line: String?
                while (file.readLine().also { line = it } != null) {
                    if (!line.isNullOrBlank()) {
                        try {
                            val message = objectMapper.readValue<SessionMessage>(line!!)
                            state.messageCache.add(message)
                            newMessages.add(message)
                            state.lastLineCount++
                        } catch (e: Exception) {
                            logger.warn("Failed to parse new session line: $line", e)
                        }
                    }
                }
                
                state.lastFileSize = currentSize
                state.lastModified = sessionFile.getLastModifiedTime().toMillis()
            }
            
            return if (newMessages.isNotEmpty()) newMessages else null
        } catch (e: Exception) {
            logger.error("Error reading new content", e)
            return null
        }
    }
    
    /**
     * 提取显示内容
     */
    fun extractDisplayContent(message: SessionMessage): String? {
        return when (message.type) {
            "assistant" -> {
                // AI 响应
                val content = message.message?.content
                when (content) {
                    is String -> content
                    is List<*> -> {
                        // content 是一个数组，包含 text blocks
                        content.mapNotNull { item ->
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
            "user" -> {
                // 用户输入
                when (val content = message.message?.content) {
                    is String -> content
                    else -> null
                }
            }
            "system" -> {
                // 系统消息，如压缩通知
                message.message?.content?.toString()
            }
            else -> null
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup(sessionId: String) {
        sessionStates.remove(sessionId)
    }
}