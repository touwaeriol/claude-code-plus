package com.claudecodeplus.sdk

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.claudecodeplus.core.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(ClaudeSessionManager::class.java)
    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
    
    companion object {
        private val CLAUDE_HOME = Paths.get(System.getProperty("user.home"), ".claude")
        private val PROJECTS_DIR = CLAUDE_HOME.resolve("projects")
        
        // 监听间隔（毫秒）
        private const val WATCH_INTERVAL = 500L
        
        // 共享的 ObjectMapper 实例
        private val sharedObjectMapper = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
        
        /**
         * 解析 JSON 行为具体的 SessionMessage 类型
         */
        fun parseSessionMessage(json: String): SessionMessage? {
            return try {
                val node = sharedObjectMapper.readTree(json)
                val type = node.get("type")?.asText() ?: return null
                
                when (type) {
                    "user" -> sharedObjectMapper.treeToValue<SessionMessage.UserMessage>(node)
                    "assistant" -> sharedObjectMapper.treeToValue<SessionMessage.AssistantMessage>(node)
                    "system" -> sharedObjectMapper.treeToValue<SessionMessage.SystemMessage>(node)
                    "summary" -> sharedObjectMapper.treeToValue<SessionMessage.SummaryMessage>(node)
                    "result" -> sharedObjectMapper.treeToValue<SessionMessage.ResultMessage>(node)
                    else -> SessionMessage.UnknownMessage(
                        type = type,
                        uuid = node.get("uuid")?.asText(),
                        timestamp = node.get("timestamp")?.asText(),
                        sessionId = node.get("sessionId")?.asText(),
                        compressed = node.get("compressed")?.asBoolean(),
                        data = sharedObjectMapper.treeToValue<Map<String, Any>>(node)
                    )
                }
            } catch (e: Exception) {
                LoggerFactory.getLogger(ClaudeSessionManager::class.java).warn("Failed to parse session message: $json", e)
                null
            }
        }
    }
    
    // 基础消息类
    sealed class SessionMessage {
        abstract val type: String
        abstract val uuid: String?
        abstract val timestamp: String?
        abstract val sessionId: String?
        abstract val compressed: Boolean?
        
        // 用户消息
        data class UserMessage(
            override val type: String = "user",
            override val uuid: String? = null,
            override val timestamp: String? = null,
            override val sessionId: String? = null,
            override val compressed: Boolean? = null,
            val parentUuid: String? = null,
            val isSidechain: Boolean? = null,
            val userType: String? = null,
            val cwd: String? = null,
            val version: String? = null,
            val message: Message? = null,
            val toolUseResult: Any? = null  // 可能是字符串或Map
        ) : SessionMessage()
        
        // AI 响应消息
        data class AssistantMessage(
            override val type: String = "assistant",
            override val uuid: String? = null,
            override val timestamp: String? = null,
            override val sessionId: String? = null,
            override val compressed: Boolean? = null,
            val parentUuid: String? = null,
            val isSidechain: Boolean? = null,
            val userType: String? = null,
            val cwd: String? = null,
            val version: String? = null,
            val message: Message? = null,
            val requestId: String? = null,
            val isApiErrorMessage: Boolean? = null
        ) : SessionMessage()
        
        // 系统消息（初始化、错误等）
        data class SystemMessage(
            override val type: String = "system",
            override val uuid: String? = null,
            override val timestamp: String? = null,
            override val sessionId: String? = null,
            override val compressed: Boolean? = null,
            val subtype: String? = null,
            val cwd: String? = null,
            val version: String? = null,
            val tools: List<String>? = null,
            val mcp_servers: List<Map<String, Any>>? = null,
            val model: String? = null,
            val permissionMode: String? = null,
            val apiKeySource: String? = null
        ) : SessionMessage()
        
        // 摘要消息
        data class SummaryMessage(
            override val type: String = "summary",
            override val uuid: String? = null,
            override val timestamp: String? = null,
            override val sessionId: String? = null,
            override val compressed: Boolean? = null,
            val summary: String? = null,
            val leafUuid: String? = null
        ) : SessionMessage()
        
        // 结果消息
        data class ResultMessage(
            override val type: String = "result",
            override val uuid: String? = null,
            override val timestamp: String? = null,
            override val sessionId: String? = null,
            override val compressed: Boolean? = null,
            val subtype: String? = null,
            val duration_ms: Long? = null,
            val duration_api_ms: Long? = null,
            val num_turns: Int? = null,
            val result: String? = null,
            val is_error: Boolean? = null,
            val total_cost_usd: Double? = null,
            val usage: Map<String, Any>? = null
        ) : SessionMessage()
        
        // 未知类型（兜底）
        data class UnknownMessage(
            override val type: String,
            override val uuid: String? = null,
            override val timestamp: String? = null,
            override val sessionId: String? = null,
            override val compressed: Boolean? = null,
            val data: Map<String, Any>? = null
        ) : SessionMessage()
    }
    
    data class Message(
        val role: String? = null,
        val content: Any? = null,  // String or List<ContentBlock>
        val id: String? = null,
        val type: String? = null,
        val model: String? = null,
        val stop_reason: String? = null,
        val stop_sequence: String? = null,
        val usage: Map<String, Any>? = null
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
                        parseSessionMessage(line!!)?.let { message ->
                            state.messageCache.add(message)
                            state.lastLineCount++
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
     * 提取显示内容
     */
    fun extractDisplayContent(message: SessionMessage): String? {
        return when (message) {
            is SessionMessage.AssistantMessage -> {
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
            is SessionMessage.UserMessage -> {
                // 用户输入
                when (val content = message.message?.content) {
                    is String -> content
                    is List<*> -> {
                        // 用户消息也可能包含工具结果等
                        content.mapNotNull { item ->
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
            is SessionMessage.SystemMessage -> {
                // 系统消息
                when (message.subtype) {
                    "init" -> "Session initialized with model: ${message.model}"
                    else -> "System: ${message.subtype}"
                }
            }
            is SessionMessage.SummaryMessage -> {
                // 摘要消息
                message.summary
            }
            is SessionMessage.ResultMessage -> {
                // 结果消息
                if (message.is_error == true) {
                    "Error: ${message.result}"
                } else {
                    "Result: ${message.result}"
                }
            }
            is SessionMessage.UnknownMessage -> {
                // 未知消息
                "Unknown message type: ${message.type}"
            }
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup(sessionId: String) {
        sessionStates.remove(sessionId)
    }
}