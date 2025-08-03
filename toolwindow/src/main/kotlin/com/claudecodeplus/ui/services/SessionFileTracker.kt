package com.claudecodeplus.ui.services

import com.claudecodeplus.session.models.ClaudeSessionMessage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * 单个会话文件的追踪器
 * 
 * 负责追踪和读取单个会话文件的变化，支持增量读取
 * 使用行号索引来跟踪读取位置，避免重复读取
 */
class SessionFileTracker(
    val sessionId: String,
    val filePath: String,
    val projectPath: String
) : Closeable {
    private val logger = KotlinLogging.logger {}
    private val gson = Gson()
    
    // 读取状态
    private var lastLineNumber: Int = 0
    private var lastFileSize: Long = 0
    private var lastModified: Long = 0
    
    // 并发控制
    private val mutex = Mutex()
    
    // 错误重试配置
    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 3
    
    /**
     * 读取新增的消息
     * 
     * @return 自上次读取以来新增的消息列表
     */
    suspend fun readNewMessages(): List<ClaudeSessionMessage> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) {
                logger.debug { "Session file does not exist: $filePath" }
                return@withContext emptyList()
            }
            
            try {
                // 快速检查：文件大小和修改时间
                val currentSize = file.length()
                val currentModified = file.lastModified()
                
                if (currentSize == lastFileSize && currentModified == lastModified) {
                    logger.trace { "File unchanged: $sessionId (size=$currentSize)" }
                    return@withContext emptyList()
                }
                
                val messages = readMessagesFromLine(file, lastLineNumber)
                
                // 更新状态
                if (messages.isNotEmpty()) {
                    lastFileSize = currentSize
                    lastModified = currentModified
                    consecutiveErrors = 0 // 重置错误计数
                    logger.debug { "Read ${messages.size} new messages from session $sessionId" }
                }
                
                messages
            } catch (e: Exception) {
                handleReadError(e)
                emptyList()
            }
        }
    }
    
    /**
     * 读取所有消息
     * 
     * @return 文件中的所有消息
     */
    suspend fun readAllMessages(): List<ClaudeSessionMessage> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) {
                logger.debug { "Session file does not exist: $filePath" }
                return@withContext emptyList()
            }
            
            try {
                readMessagesFromLine(file, 0)
            } catch (e: Exception) {
                logger.error(e) { "Failed to read all messages from $filePath" }
                emptyList()
            }
        }
    }
    
    /**
     * 从指定行开始读取消息
     */
    private fun readMessagesFromLine(file: File, startLine: Int): List<ClaudeSessionMessage> {
        val messages = mutableListOf<ClaudeSessionMessage>()
        var currentLine = 0
        
        RandomAccessFile(file, "r").use { raf ->
            // 如果是首次读取，先计算总行数
            if (lastLineNumber == 0 && startLine == 0) {
                var lineCount = 0
                raf.seek(0)
                while (raf.readLine() != null) {
                    lineCount++
                }
                lastLineNumber = lineCount
                logger.debug { "Initial read of session $sessionId: $lineCount lines" }
                return emptyList() // 首次读取不返回消息，只建立基线
            }
            
            // 读取新增的行
            raf.seek(0)
            var rawLine: String?
            
            while (raf.readLine().also { rawLine = it } != null) {
                if (currentLine >= startLine) {
                    rawLine?.let { line ->
                        // 处理编码：readLine() 返回的是 ISO-8859-1，需要转换为 UTF-8
                        val utf8Line = String(line.toByteArray(Charsets.ISO_8859_1), StandardCharsets.UTF_8)
                        parseMessage(utf8Line)?.let { message ->
                            messages.add(message)
                        }
                    }
                }
                currentLine++
            }
            
            // 更新最后读取的行号
            if (currentLine > lastLineNumber) {
                logger.trace { "Updated last line number for $sessionId: $lastLineNumber -> $currentLine" }
                lastLineNumber = currentLine
            }
        }
        
        return messages
    }
    
    /**
     * 解析单行 JSON 为消息对象
     */
    private fun parseMessage(line: String): ClaudeSessionMessage? {
        if (line.isBlank()) return null
        
        return try {
            val message = gson.fromJson(line, ClaudeSessionMessage::class.java)
            
            // 基本验证
            if (message.type.isNullOrBlank()) {
                logger.warn { "Invalid message without type: $line" }
                return null
            }
            
            // 验证 sessionId（如果消息中包含）
            if (!message.sessionId.isNullOrBlank() && message.sessionId != sessionId) {
                logger.warn { "Message sessionId mismatch: expected=$sessionId, actual=${message.sessionId}" }
            }
            
            message
        } catch (e: JsonSyntaxException) {
            logger.warn { "Failed to parse message: $line" }
            logger.debug(e) { "Parse error details" }
            null
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error parsing message" }
            null
        }
    }
    
    /**
     * 处理读取错误
     */
    private fun handleReadError(e: Exception) {
        consecutiveErrors++
        
        when {
            e.message?.contains("being used by another process") == true -> {
                logger.debug { "File is locked: $filePath (attempt $consecutiveErrors)" }
            }
            e is java.nio.file.AccessDeniedException -> {
                logger.warn { "Access denied to file: $filePath" }
            }
            else -> {
                logger.error(e) { "Error reading file: $filePath" }
            }
        }
        
        // 如果连续错误过多，重置状态
        if (consecutiveErrors >= maxConsecutiveErrors) {
            logger.warn { "Too many consecutive errors for $sessionId, resetting tracker" }
            reset()
        }
    }
    
    /**
     * 重置追踪器状态
     */
    fun reset() {
        lastLineNumber = 0
        lastFileSize = 0
        lastModified = 0
        consecutiveErrors = 0
        logger.info { "Reset tracker for session $sessionId" }
    }
    
    /**
     * 获取当前读取位置
     */
    fun getReadPosition(): Int = lastLineNumber
    
    /**
     * 获取文件大小
     */
    fun getFileSize(): Long = lastFileSize
    
    /**
     * 检查文件是否存在
     */
    fun fileExists(): Boolean = File(filePath).exists()
    
    /**
     * 获取文件信息
     */
    fun getFileInfo(): FileInfo? {
        val file = File(filePath)
        return if (file.exists()) {
            FileInfo(
                exists = true,
                size = file.length(),
                lastModified = file.lastModified(),
                lineCount = lastLineNumber,
                path = filePath
            )
        } else {
            null
        }
    }
    
    /**
     * 关闭追踪器
     */
    override fun close() {
        logger.debug { "Closing tracker for session $sessionId" }
        // 目前没有需要释放的资源
    }
    
    /**
     * 文件信息
     */
    data class FileInfo(
        val exists: Boolean,
        val size: Long,
        val lastModified: Long,
        val lineCount: Int,
        val path: String
    )
}