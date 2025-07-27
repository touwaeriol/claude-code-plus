package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageRole
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.ui.models.ContextReference
import com.claudecodeplus.ui.models.MessageTimelineItem
import com.claudecodeplus.sdk.*
import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Claude Code 历史会话加载服务
 * 使用 kotlinx.serialization 消息类型
 */
class SessionHistoryService {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 解析 Claude 消息
     */
    private fun parseClaudeMessage(line: String): ClaudeMessage? {
        return try {
            json.decodeFromString<ClaudeMessage>(line)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取消息的时间戳
     */
    private fun ClaudeMessage.getTimestamp(): String? {
        return when (this) {
            is UserMessage -> timestamp
            is AssistantMessage -> timestamp
            is SystemMessage -> timestamp
            is ResultMessage -> timestamp
            is SummaryMessage -> timestamp
        }
    }
    
    /**
     * 获取项目的历史会话目录
     */
    fun getProjectSessionsPath(projectPath: String): Path? {
        val userHome = System.getProperty("user.home") ?: return null
        val claudeProjectsPath = Paths.get(userHome, ".claude", "projects")
        
        if (!claudeProjectsPath.exists()) {
            return null
        }
        
        // Claude Code 使用项目路径转换为目录名的规则：
        // 例如: C:\Users\16790\IdeaProjects\claude-code-plus -> C--Users-16790-IdeaProjects-claude-code-plus
        val projectDirName = if (projectPath.contains(":")) {
            // Windows 路径
            projectPath.replace(":", "-").replace("\\", "-")
        } else {
            // Unix 路径
            projectPath.replace("/", "-")
        }
        val projectSessionsPath = claudeProjectsPath.resolve(projectDirName)
        
        println("SessionHistoryService.getProjectSessionsPath:")
        println("  - projectPath: $projectPath")
        println("  - projectDirName: $projectDirName")
        println("  - projectSessionsPath: $projectSessionsPath")
        println("  - exists: ${projectSessionsPath.exists()}")
        
        return if (projectSessionsPath.exists()) projectSessionsPath else null
    }
    
    /**
     * 获取最近的会话文件
     */
    fun getLatestSessionFile(projectPath: String): File? {
        val sessionsPath = getProjectSessionsPath(projectPath) ?: return null
        
        return try {
            sessionsPath.listDirectoryEntries("*.jsonl")
                .mapNotNull { path ->
                    val file = path.toFile()
                    if (file.length() > 0) file else null
                }
                .maxByOrNull { it.lastModified() }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 从文件末尾读取指定数量的行
     */
    private fun readLastLines(file: File, maxLines: Int): List<String> {
        return try {
            // 使用 UTF-8 编码读取所有行，然后取最后几行
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readLines().takeLast(maxLines)
            }
        } catch (e: Exception) {
            // 读取文件失败
            emptyList()
        }
    }
    
    /**
     * 解析时间戳
     */
    private fun parseTimestamp(timestamp: String?): Long {
        return timestamp?.let {
            try {
                Instant.parse(it).toEpochMilli()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        } ?: System.currentTimeMillis()
    }
    
    
    
    
    
    /**
     * 解析工具输入参数
     */
    private fun parseToolInput(input: JsonObject): Map<String, Any> {
        return input.entries.associate { (key, element) ->
            key to parseJsonValue(element)
        }
    }
    
    /**
     * 递归解析 JSON 值
     */
    private fun parseJsonValue(element: JsonElement): Any {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is JsonArray -> element.map { parseJsonValue(it) }
            is JsonObject -> element.entries.associate { (k, v) -> k to parseJsonValue(v) }
            is JsonNull -> ""
        }
    }
    
    
    /**
     * 获取项目的所有会话文件信息
     */
    fun getSessionFiles(projectPath: String): List<SessionFileInfo> {
        val sessionsPath = getProjectSessionsPath(projectPath) ?: return emptyList()
        
        return try {
            sessionsPath.listDirectoryEntries("*.jsonl")
                .mapNotNull { path ->
                    val file = path.toFile()
                    if (file.length() > 0) {
                        SessionFileInfo(
                            file = file,
                            name = file.name,
                            size = file.length(),
                            lastModified = file.lastModified()
                        )
                    } else null
                }
                .sortedByDescending { it.lastModified }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    data class SessionFileInfo(
        val file: File,
        val name: String,
        val size: Long,
        val lastModified: Long
    )
    
    /**
     * 将历史会话转换为 SDKMessage 流
     * 完全模拟实时消息流，让 SessionLoader 使用相同的处理逻辑
     */
    fun loadSessionHistoryAsFlow(
        sessionFile: File,
        maxMessages: Int = 50,
        maxDaysOld: Int = 7
    ): Flow<SDKMessage> = flow {
        val cutoffTime = System.currentTimeMillis() - (maxDaysOld * 24 * 60 * 60 * 1000L)
        var messageCount = 0
        
        try {
            val estimatedLines = maxMessages * 6
            val lines = readLastLines(sessionFile, estimatedLines)
            
            // 简单地逐行处理，完全模拟实时流
            for (line in lines) {
                if (line.isBlank()) continue
                if (messageCount >= maxMessages) break
                
                val message = parseClaudeMessage(line) ?: continue
                val timestamp = parseTimestamp(message.getTimestamp())
                
                if (timestamp < cutoffTime) continue
                
                println("[SessionHistory] 处理消息: ${message::class.simpleName}")
                
                when (message) {
                    is UserMessage -> {
                        val content = extractUserContent(message)
                        println("[SessionHistory] UserMessage: content = ${content?.take(100)}")
                        if (content != null && !isSystemMessage(content)) {
                            // 用户消息：START → 内容 → END
                            emit(SDKMessage(
                                type = MessageType.START,
                                data = MessageData(
                                    text = "USER_MESSAGE:$content",
                                    sessionId = message.uuid
                                )
                            ))
                            emit(SDKMessage(
                                type = MessageType.END,
                                data = MessageData()
                            ))
                            messageCount++
                        }
                    }
                    
                    is AssistantMessage -> {
                        val content = message.message?.content ?: continue
                        println("[DEBUG] AssistantMessage: id=${message.message?.id}, model=${message.message?.model}, content blocks=${content.size}")
                        
                        // 每个助手消息行都发送 START（让 SessionLoader 判断是否是同一消息）
                        emit(SDKMessage(
                            type = MessageType.START,
                            data = MessageData(
                                sessionId = message.uuid,
                                // 传递消息ID，让 SessionLoader 判断是否是同一消息）
                                text = "ASSISTANT_MESSAGE:${message.message?.id}",
                                model = message.message?.model  // 传递模型信息
                            )
                        ))
                        
                        // 发送内容块
                        for (block in content) {
                            when (block) {
                                is TextBlock -> {
                                    println("[DEBUG]   TextBlock: ${block.text.take(100)}")
                                    if (block.text.isNotEmpty()) {
                                        emit(SDKMessage(
                                            type = MessageType.TEXT,
                                            data = MessageData(text = block.text)
                                        ))
                                    }
                                }
                                is ToolUseBlock -> {
                                    emit(SDKMessage(
                                        type = MessageType.TOOL_USE,
                                        data = MessageData(
                                            toolName = block.name,
                                            toolCallId = block.id,
                                            toolInput = parseToolInput(block.input)
                                        )
                                    ))
                                    
                                    // 模拟工具执行结果
                                    emit(SDKMessage(
                                        type = MessageType.TOOL_RESULT,
                                        data = MessageData(
                                            toolResult = "Tool execution completed",
                                            toolCallId = block.id
                                        )
                                    ))
                                }
                                else -> {}
                            }
                        }
                        
                        // 不发送 END，让下一个 START 或文件结束来触发
                        messageCount++
                    }
                    
                    is SummaryMessage -> {
                        // 处理摘要消息，特别是压缩摘要
                        if (message.isCompactSummary == true && message.summary != null) {
                            // 发送压缩摘要标记
                            emit(SDKMessage(
                                type = MessageType.START,
                                data = MessageData(
                                    text = "COMPACT_SUMMARY:${message.uuid}",
                                    sessionId = message.uuid
                                )
                            ))
                            emit(SDKMessage(
                                type = MessageType.TEXT,
                                data = MessageData(
                                    text = message.summary
                                )
                            ))
                            emit(SDKMessage(
                                type = MessageType.END,
                                data = MessageData()
                            ))
                            messageCount++
                        }
                    }
                    
                    else -> {
                        // 忽略其他类型的消息（system, result等）
                    }
                }
            }
            
            // 不发送文件结束的 END，让会话保持开放状态
            
        } catch (e: Exception) {
            emit(SDKMessage(
                type = MessageType.ERROR,
                data = MessageData(error = "加载历史失败: ${e.message}")
            ))
        }
    }
    
    /**
     * 提取用户消息内容
     */
    private fun extractUserContent(message: UserMessage): String? {
        val messageData = message.message ?: return null
        
        return when (val content = messageData.content) {
            is ContentOrList.StringContent -> content.value
            is ContentOrList.ListContent -> {
                content.value.filterIsInstance<TextBlock>()
                    .map { it.text }
                    .joinToString("\n")
            }
            null -> null
        }
    }
    
    /**
     * 检查是否为系统消息
     */
    private fun isSystemMessage(content: String): Boolean {
        return content.contains("Caveat: The messages below were generated by the user") ||
               content.contains("<command-name>") || 
               content.contains("<local-command-stdout>")
    }
    
    
    /**
     * 从当前工作目录加载最近的会话（流式）
     */
    fun loadLatestSessionAsFlow(maxMessages: Int = 50, maxDaysOld: Int = 7): Flow<SDKMessage> {
        val currentDir = System.getProperty("user.dir") ?: return flow { 
            emit(SDKMessage(
                type = MessageType.ERROR,
                data = MessageData(error = "无法获取当前目录")
            ))
        }
        
        val sessionFile = getLatestSessionFile(currentDir) ?: return flow {
            emit(SDKMessage(
                type = MessageType.ERROR,
                data = MessageData(error = "未找到会话文件")
            ))
        }
        
        return loadSessionHistoryAsFlow(sessionFile, maxMessages, maxDaysOld)
    }
}