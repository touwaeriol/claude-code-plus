package com.claudecodeplus.ui.utils

import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageRole
import com.claudecodeplus.ui.services.ModernClaudeMessageParser
import com.claudecodeplus.ui.services.ClaudeMessageAdapter
import mu.KotlinLogging
import kotlinx.serialization.json.*
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
    private val modernParser = ModernClaudeMessageParser()
    private val adapter = ClaudeMessageAdapter()

    // 用于快速元数据解析的JSON配置
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
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
            // 使用现代解析器解析JSONL行
            val parseResult = modernParser.parseJsonLine(line, lineNumber) ?: return null

            // 使用适配器转换为EnhancedMessage
            val enhancedMessage = adapter.toEnhancedMessage(parseResult.message)

            // 检查是否为系统初始化消息或其他非UI显示消息
            val sessionIdUpdate = adapter.extractSessionIdUpdate(parseResult.message)
            if (sessionIdUpdate != null && enhancedMessage == null) {
                logger.debug("[ClaudeSessionHistoryLoader] 检测到 system init 消息，sessionId: $sessionIdUpdate")
                return ParseResult(null, sessionIdUpdate)
            }

            // 返回解析结果
            ParseResult(enhancedMessage, parseResult.sessionId)

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
                            val jsonObject = json.parseToJsonElement(line).jsonObject

                            // 获取 sessionId
                            if (sessionId == null) {
                                sessionId = jsonObject["sessionId"]?.jsonPrimitive?.content
                            }

                            // 获取项目路径
                            if (projectPath == null) {
                                projectPath = jsonObject["cwd"]?.jsonPrimitive?.content
                            }

                            // 计算消息数量
                            val type = jsonObject["type"]?.jsonPrimitive?.content
                            if (type == "user" || type == "assistant") {
                                messageCount++

                                // 获取时间戳
                                val timestampStr = jsonObject["timestamp"]?.jsonPrimitive?.content
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