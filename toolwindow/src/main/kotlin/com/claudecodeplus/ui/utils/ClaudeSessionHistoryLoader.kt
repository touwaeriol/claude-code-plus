package com.claudecodeplus.ui.utils

import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageRole
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import mu.KotlinLogging
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Claude 会话历史记录加载器
 * 
 * 负责解析 Claude CLI 生成的 JSONL 格式会话文件，并转换为项目内部使用的消息格式。
 * 
 * JSONL 文件格式说明：
 * - 每行是一个完整的 JSON 对象
 * - 包含用户消息 (type: "user") 和助手消息 (type: "assistant")
 * - 每个消息包含 uuid、timestamp、sessionId、message 等字段
 */
class ClaudeSessionHistoryLoader {
    private val logger = KotlinLogging.logger {}
    
    /**
     * 从会话文件加载历史消息
     * 
     * @param sessionFile 会话文件路径
     * @return 解析出的消息列表
     */
    fun loadSessionHistory(sessionFile: Path): LoadResult {
        if (!Files.exists(sessionFile)) {
            logger.warn("[ClaudeSessionHistoryLoader] 会话文件不存在: $sessionFile")
            return LoadResult(emptyList(), null)
        }
        
        val messages = mutableListOf<EnhancedMessage>()
        var lineNumber = 0
        var currentSessionId: String? = null
        
        try {
            Files.newBufferedReader(sessionFile).use { reader ->
                reader.lineSequence().forEach { line ->
                    lineNumber++
                    
                    if (line.trim().isNotEmpty()) {
                        val parseResult = parseJsonLine(line, lineNumber)
                        parseResult?.message?.let { message ->
                            messages.add(message)
                        }
                        // 更新会话ID（如果解析结果中包含）
                        parseResult?.sessionId?.let { sessionId ->
                            currentSessionId = sessionId
                            logger.info("[ClaudeSessionHistoryLoader] 检测到会话ID更新: $sessionId")
                        }
                    }
                }
            }
            
            logger.info("[ClaudeSessionHistoryLoader] 成功加载会话历史: $sessionFile, 消息数量=${messages.size}, 当前会话ID=$currentSessionId")
        } catch (e: IOException) {
            logger.error("[ClaudeSessionHistoryLoader] 读取会话文件失败: $sessionFile", e)
        } catch (e: Exception) {
            logger.error("[ClaudeSessionHistoryLoader] 解析会话文件失败: $sessionFile", e)
        }
        
        return LoadResult(messages, currentSessionId)
    }
    
    /**
     * 解析 JSONL 中的单行 JSON
     * 
     * @param line JSON 字符串
     * @param lineNumber 行号（用于错误日志）
     * @return 解析结果，包含消息和可能的会话ID更新
     */
    private fun parseJsonLine(line: String, lineNumber: Int): ParseResult? {
        return try {
            val json = JsonParser.parseString(line).asJsonObject
            
            // 获取消息类型
            val type = json.get("type")?.asString
            
            // 处理 system init 消息（包含sessionId更新）
            if (type == "system" && json.get("message")?.asJsonObject?.get("role")?.asString == "init") {
                val sessionId = json.get("sessionId")?.asString
                logger.debug("[ClaudeSessionHistoryLoader] 检测到 system init 消息，sessionId: $sessionId")
                // 返回sessionId更新信息，但不包含消息
                return ParseResult(null, sessionId)
            }
            
            if (type != "user" && type != "assistant") {
                // 跳过非消息类型的记录（如系统消息、元数据等）
                return null
            }
            
            // 获取消息对象
            val messageObj = json.get("message")?.asJsonObject
            if (messageObj == null) {
                logger.debug("[ClaudeSessionHistoryLoader] 行 $lineNumber 缺少 message 字段")
                return null
            }
            
            val role = messageObj.get("role")?.asString ?: type
            
            // 处理 content 字段，它可能是字符串或数组
            val content = when {
                messageObj.get("content")?.isJsonArray == true -> {
                    // 如果是数组，提取所有文本内容
                    val contentArray = messageObj.getAsJsonArray("content")
                    contentArray.joinToString("\n") { element ->
                        if (element.isJsonObject) {
                            val contentObj = element.asJsonObject
                            when (contentObj.get("type")?.asString) {
                                "text" -> contentObj.get("text")?.asString ?: ""
                                "tool_use" -> {
                                    // 简化显示工具调用
                                    val name = contentObj.get("name")?.asString ?: "unknown"
                                    "[Tool: $name]"
                                }
                                else -> contentObj.toString()
                            }
                        } else {
                            element.asString
                        }
                    }
                }
                messageObj.get("content")?.isJsonPrimitive == true -> {
                    messageObj.get("content")?.asString ?: ""
                }
                else -> ""
            }
            
            // 跳过空内容的消息
            if (content.isBlank()) {
                return null
            }
            
            // 获取时间戳
            val timestamp = json.get("timestamp")?.asString
            val parsedTimestamp = parseTimestamp(timestamp)
            
            // 获取 UUID 和其他元数据
            val uuid = json.get("uuid")?.asString ?: ""
            val sessionId = json.get("sessionId")?.asString ?: ""
            val parentUuid = json.get("parentUuid")?.asString
            
            // 创建 EnhancedMessage 对象
            val message = createEnhancedMessage(
                role = role,
                content = content,
                timestamp = parsedTimestamp,
                uuid = uuid,
                sessionId = sessionId,
                parentUuid = parentUuid,
                originalJson = json
            )
            
            // 返回解析结果，包含消息和当前sessionId
            ParseResult(message, sessionId.ifBlank { null })
            
        } catch (e: JsonSyntaxException) {
            logger.warn("[ClaudeSessionHistoryLoader] 行 $lineNumber JSON 解析失败: ${e.message}")
            null
        } catch (e: Exception) {
            logger.error("[ClaudeSessionHistoryLoader] 行 $lineNumber 处理失败", e)
            null
        }
    }
    
    /**
     * 解析时间戳字符串
     * 
     * @param timestampStr 时间戳字符串 (ISO 8601 格式)
     * @return 解析后的时间戳（毫秒），解析失败返回当前时间
     */
    private fun parseTimestamp(timestampStr: String?): Long {
        if (timestampStr.isNullOrBlank()) {
            return System.currentTimeMillis()
        }
        
        return try {
            Instant.parse(timestampStr).toEpochMilli()
        } catch (e: Exception) {
            logger.debug("[ClaudeSessionHistoryLoader] 时间戳解析失败: $timestampStr, 使用当前时间")
            System.currentTimeMillis()
        }
    }
    
    /**
     * 创建 EnhancedMessage 对象
     * 
     * @param role 消息角色 ("user" 或 "assistant")
     * @param content 消息内容
     * @param timestamp 时间戳
     * @param uuid 消息 UUID
     * @param sessionId 会话 ID
     * @param parentUuid 父消息 UUID
     * @param originalJson 原始 JSON 对象（用于调试）
     * @return EnhancedMessage 对象
     */
    private fun createEnhancedMessage(
        role: String,
        content: String,
        timestamp: Long,
        uuid: String,
        sessionId: String,
        parentUuid: String?,
        originalJson: com.google.gson.JsonObject
    ): EnhancedMessage {
        
        return when (role.lowercase()) {
            "user" -> {
                EnhancedMessage(
                    id = uuid.ifBlank { generateUuid() },
                    role = MessageRole.USER,
                    content = content,
                    timestamp = timestamp
                )
            }
            "assistant" -> {
                EnhancedMessage(
                    id = uuid.ifBlank { generateUuid() },
                    role = MessageRole.ASSISTANT,
                    content = content,
                    timestamp = timestamp,
                    toolCalls = parseToolCalls(originalJson)
                )
            }
            else -> {
                // 默认创建用户消息
                EnhancedMessage(
                    id = uuid.ifBlank { generateUuid() },
                    role = MessageRole.USER,
                    content = content,
                    timestamp = timestamp
                )
            }
        }
    }
    
    /**
     * 解析工具调用信息（如果消息中包含）
     * 
     * @param json 原始 JSON 对象
     * @return 工具调用列表
     */
    private fun parseToolCalls(json: com.google.gson.JsonObject): List<com.claudecodeplus.ui.models.ToolCall> {
        // TODO: 根据实际的 Claude CLI 输出格式解析工具调用信息
        // 这里先返回空列表，后续根据需要完善
        return emptyList()
    }
    
    /**
     * 生成简单的 UUID（如果原始数据中没有）
     */
    private fun generateUuid(): String {
        return java.util.UUID.randomUUID().toString()
    }
    
    /**
     * 获取会话文件的基本信息
     * 
     * @param sessionFile 会话文件路径
     * @return 会话信息
     */
    fun getSessionInfo(sessionFile: Path): SessionInfo? {
        if (!Files.exists(sessionFile)) {
            return null
        }
        
        return try {
            var sessionId: String? = null
            var messageCount = 0
            var firstTimestamp: Long? = null
            var lastTimestamp: Long? = null
            var projectPath: String? = null
            
            Files.newBufferedReader(sessionFile).use { reader ->
                reader.lineSequence().take(100).forEach { line -> // 只读取前100行用于获取基本信息
                    if (line.trim().isNotEmpty()) {
                        try {
                            val json = JsonParser.parseString(line).asJsonObject
                            
                            // 获取 sessionId
                            if (sessionId == null) {
                                sessionId = json.get("sessionId")?.asString
                            }
                            
                            // 获取项目路径
                            if (projectPath == null) {
                                projectPath = json.get("cwd")?.asString
                            }
                            
                            // 计算消息数量
                            val type = json.get("type")?.asString
                            if (type == "user" || type == "assistant") {
                                messageCount++
                                
                                // 获取时间戳
                                val timestampStr = json.get("timestamp")?.asString
                                val timestamp = parseTimestamp(timestampStr)
                                
                                if (firstTimestamp == null) {
                                    firstTimestamp = timestamp
                                }
                                lastTimestamp = timestamp
                            }
                        } catch (e: Exception) {
                            // 忽略解析错误的行
                        }
                    }
                }
            }
            
            SessionInfo(
                sessionId = sessionId ?: sessionFile.fileName.toString().removeSuffix(".jsonl"),
                projectPath = projectPath,
                messageCount = messageCount,
                fileSize = Files.size(sessionFile),
                firstMessageTime = firstTimestamp,
                lastMessageTime = lastTimestamp,
                filePath = sessionFile
            )
        } catch (e: Exception) {
            logger.error("[ClaudeSessionHistoryLoader] 获取会话信息失败: $sessionFile", e)
            null
        }
    }
    
    /**
     * 加载结果数据类 - 包含消息列表和最新会话ID
     */
    data class LoadResult(
        val messages: List<EnhancedMessage>,
        val currentSessionId: String?
    )
    
    /**
     * 解析结果数据类 - 包含消息和可能的会话ID更新
     */
    private data class ParseResult(
        val message: EnhancedMessage?,
        val sessionId: String?
    )
    
    /**
     * 会话信息数据类
     */
    data class SessionInfo(
        val sessionId: String,
        val projectPath: String?,
        val messageCount: Int,
        val fileSize: Long,
        val firstMessageTime: Long?,
        val lastMessageTime: Long?,
        val filePath: Path
    ) {
        val fileSizeKB: Double
            get() = fileSize / 1024.0
            
        val duration: Long?
            get() = if (firstMessageTime != null && lastMessageTime != null) {
                lastMessageTime - firstMessageTime
            } else {
                null
            }
    }
}