package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageRole
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.ContextReference
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

/**
 * Claude Code 历史会话加载服务
 */
class SessionHistoryService {
    
    private val module = SerializersModule {
        polymorphic(SessionEntry::class) {
            subclass(UserMessageEntry::class)
            subclass(AssistantMessageEntry::class)
            subclass(SummaryEntry::class)
            subclass(GenericEntry::class)
        }
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        serializersModule = module
        classDiscriminator = "#class"  // 使用不同的鉴别器避免与 type 属性冲突
    }
    
    /**
     * 会话条目基类
     */
    @Serializable
    abstract class SessionEntry {
        abstract val type: String
        abstract val uuid: String?
        abstract val parentUuid: String?
        abstract val timestamp: String?
        abstract val isMeta: Boolean?
    }
    
    /**
     * 用户消息条目
     */
    @Serializable
    @SerialName("user")
    data class UserMessageEntry(
        override val type: String = "user",
        override val uuid: String? = null,
        override val parentUuid: String? = null,
        override val timestamp: String? = null,
        override val isMeta: Boolean? = false,
        val message: MessageContent? = null,
        val cwd: String? = null,
        val sessionId: String? = null,
        val version: String? = null
    ) : SessionEntry()
    
    /**
     * 助手消息条目
     */
    @Serializable
    @SerialName("assistant")
    data class AssistantMessageEntry(
        override val type: String = "assistant",
        override val uuid: String? = null,
        override val parentUuid: String? = null,
        override val timestamp: String? = null,
        override val isMeta: Boolean? = false,
        val message: MessageContent? = null,
        val model: String? = null
    ) : SessionEntry()
    
    /**
     * 摘要条目
     */
    @Serializable
    @SerialName("summary")
    data class SummaryEntry(
        override val type: String = "summary",
        override val uuid: String? = null,
        override val parentUuid: String? = null,
        override val timestamp: String? = null,
        override val isMeta: Boolean? = false,
        val summary: String? = null,
        val leafUuid: String? = null
    ) : SessionEntry()
    
    /**
     * 通用条目（用于未知类型）
     */
    @Serializable
    data class GenericEntry(
        override val type: String,
        override val uuid: String? = null,
        override val parentUuid: String? = null,
        override val timestamp: String? = null,
        override val isMeta: Boolean? = false
    ) : SessionEntry()
    
    /**
     * 消息内容
     */
    @Serializable
    data class MessageContent(
        val role: String,
        val content: JsonElement? = null
    )
    
    /**
     * 获取项目的历史会话目录
     */
    fun getProjectSessionsPath(projectPath: String): Path? {
        val userHome = System.getProperty("user.home") ?: return null
        val claudeProjectsPath = Paths.get(userHome, ".claude", "projects")
        
        if (!claudeProjectsPath.exists()) {
            return null
        }
        
        // Claude Code 使用项目路径转换为目录名的规则：将路径中的 / 替换为 -
        val projectDirName = projectPath.replace("/", "-")
        val projectSessionsPath = claudeProjectsPath.resolve(projectDirName)
        
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
            // 获取最近会话文件失败: ${e.message}
            null
        }
    }
    
    /**
     * 解析消息内容
     */
    private fun parseMessageContent(content: JsonElement?): String? {
        return when (content) {
            is JsonPrimitive -> content.content
            is JsonArray -> {
                // Claude Code 可能使用数组格式，如 [{"type":"text","text":"内容"}]
                content.firstOrNull()?.let { element ->
                    if (element is JsonObject) {
                        element["text"]?.jsonPrimitive?.content
                    } else null
                }
            }
            is JsonObject -> {
                // 直接的对象格式
                content["text"]?.jsonPrimitive?.content
            }
            else -> null
        }
    }
    
    /**
     * 从文件末尾读取指定数量的行
     */
    private fun readLastLines(file: File, maxLines: Int): List<String> {
        val lines = mutableListOf<String>()
        
        try {
            RandomAccessFile(file, "r").use { raf ->
                val fileLength = raf.length()
                if (fileLength == 0L) return emptyList()
                
                // 从文件末尾开始
                var filePointer = fileLength - 1
                val buffer = StringBuilder()
                var lineCount = 0
                
                while (filePointer >= 0 && lineCount < maxLines) {
                    raf.seek(filePointer)
                    val readByte = raf.readByte().toInt().toChar()
                    
                    if (readByte == '\n' || readByte == '\r') {
                        if (buffer.isNotEmpty()) {
                            lines.add(0, buffer.reverse().toString())
                            buffer.clear()
                            lineCount++
                        }
                    } else {
                        buffer.append(readByte)
                    }
                    
                    filePointer--
                }
                
                // 添加最后一行（如果有）
                if (buffer.isNotEmpty()) {
                    lines.add(0, buffer.reverse().toString())
                }
            }
        } catch (e: Exception) {
            // 读取文件失败: ${e.message}
        }
        
        return lines
    }
    
    /**
     * 加载会话历史 - 优化版本
     * @param sessionFile 会话文件
     * @param maxMessages 最大消息数，默认50条
     * @param maxDaysOld 最大天数，默认7天
     */
    fun loadSessionHistory(
        sessionFile: File, 
        maxMessages: Int = 50,
        maxDaysOld: Int = 7
    ): List<EnhancedMessage> {
        val messages = mutableListOf<EnhancedMessage>()
        val cutoffTime = System.currentTimeMillis() - (maxDaysOld * 24 * 60 * 60 * 1000L)
        
        try {
            // 估算需要读取的行数（假设每条消息平均3行）
            val estimatedLines = maxMessages * 6
            val lines = readLastLines(sessionFile, estimatedLines)
            
            var processedCount = 0
            
            // 处理读取的行
            for (line in lines) {
                if (line.isBlank()) continue
                
                // 如果已经处理了足够的消息，停止
                if (messages.size >= maxMessages) {
                    break
                }
                
                try {
                    // 首先解析为 JsonObject 来获取 type
                    val jsonObject = json.parseToJsonElement(line).jsonObject
                    val type = jsonObject["type"]?.jsonPrimitive?.content ?: continue
                    
                    // 根据 type 反序列化为具体类型
                    val entry = when (type) {
                        "user" -> json.decodeFromJsonElement<UserMessageEntry>(jsonObject)
                        "assistant" -> json.decodeFromJsonElement<AssistantMessageEntry>(jsonObject)
                        "summary" -> json.decodeFromJsonElement<SummaryEntry>(jsonObject)
                        else -> json.decodeFromJsonElement<GenericEntry>(jsonObject)
                    }
                    
                    // 跳过元数据和系统消息
                    if (entry.isMeta == true) {
                        continue
                    }
                    
                    // 检查时间戳是否太旧
                    val messageTimestamp = entry.timestamp?.let {
                        try {
                            Instant.parse(it).toEpochMilli()
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    // 如果消息太旧，跳过
                    if (messageTimestamp != null && messageTimestamp < cutoffTime) {
                        continue
                    }
                    
                    // 处理用户和助手消息
                    when (entry) {
                        is UserMessageEntry -> {
                            entry.message?.let { messageContent ->
                                val content = parseMessageContent(messageContent.content) ?: return@let
                                
                                // 过滤掉 Claude Code 的系统消息
                                if (content.contains("Caveat: The messages below were generated by the user") ||
                                    content.contains("<command-name>") || 
                                    content.contains("<local-command-stdout>")) {
                                    return@let
                                }
                                
                                val timestamp = messageTimestamp ?: System.currentTimeMillis()
                                
                                messages.add(
                                    EnhancedMessage(
                                        id = entry.uuid ?: System.currentTimeMillis().toString(),
                                        role = MessageRole.USER,
                                        content = content,
                                        timestamp = timestamp,
                                        model = AiModel.OPUS,
                                        contexts = emptyList()
                                    )
                                )
                                processedCount++
                            }
                        }
                        is AssistantMessageEntry -> {
                            entry.message?.let { messageContent ->
                                val content = parseMessageContent(messageContent.content) ?: return@let
                                
                                val timestamp = messageTimestamp ?: System.currentTimeMillis()
                                
                                // 解析模型类型
                                val model = when {
                                    entry.model?.contains("opus") == true -> AiModel.OPUS
                                    entry.model?.contains("sonnet") == true -> AiModel.SONNET
                                    else -> AiModel.OPUS
                                }
                                
                                messages.add(
                                    EnhancedMessage(
                                        id = entry.uuid ?: System.currentTimeMillis().toString(),
                                        role = MessageRole.ASSISTANT,
                                        content = content,
                                        timestamp = timestamp,
                                        model = model,
                                        contexts = emptyList()
                                    )
                                )
                                processedCount++
                            }
                        }
                        else -> {
                            // 忽略其他类型
                        }
                    }
                } catch (e: Exception) {
                    // 忽略解析失败的行
                    // println("解析会话行失败: ${e.message}")
                }
            }
            
            // 加载了 ${messages.size} 条消息（从 ${lines.size} 行中）
            
        } catch (e: Exception) {
            // 加载会话历史失败: ${e.message}
        }
        
        return messages
    }
    
    /**
     * 从当前工作目录加载最近的会话
     * @param maxMessages 最大消息数
     * @param maxDaysOld 最大天数
     */
    fun loadLatestSession(maxMessages: Int = 50, maxDaysOld: Int = 7): List<EnhancedMessage> {
        val currentDir = System.getProperty("user.dir") ?: return emptyList()
        val sessionFile = getLatestSessionFile(currentDir) ?: return emptyList()
        
        // 加载历史会话: ${sessionFile.name}
        return loadSessionHistory(sessionFile, maxMessages, maxDaysOld)
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
            // 获取会话文件列表失败: ${e.message}
            emptyList()
        }
    }
    
    data class SessionFileInfo(
        val file: File,
        val name: String,
        val size: Long,
        val lastModified: Long
    )
}