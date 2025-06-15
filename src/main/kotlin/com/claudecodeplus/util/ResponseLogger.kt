package com.claudecodeplus.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/**
 * 服务器响应日志记录器
 * 将所有服务器响应记录到 logs 目录下
 */
object ResponseLogger {
    private val LOG = logger<ResponseLogger>()
    private val sessionCounter = AtomicInteger(0)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    
    // 获取日志目录
    private fun getLogDirectory(project: Project? = null): File {
        // 优先使用项目目录，如果没有则使用当前工作目录
        val baseDir = project?.basePath ?: System.getProperty("user.dir")
        LOG.info("Base directory for logs: $baseDir (from project: ${project != null})")
        
        val logDir = File(baseDir, "logs")
        if (!logDir.exists()) {
            LOG.info("Creating log directory: ${logDir.absolutePath}")
            val created = logDir.mkdirs()
            LOG.info("Log directory created: $created")
        }
        return logDir
    }
    
    // 创建新的会话日志文件
    fun createSessionLog(sessionId: String? = null, project: Project? = null): File {
        val logDir = getLogDirectory(project)
        LOG.info("Creating log in directory: ${logDir.absolutePath}")
        
        val timestamp = dateFormat.format(Date())
        val sessionNum = sessionCounter.incrementAndGet()
        val fileName = "session_${timestamp}_${sessionNum}.log"
        val logFile = File(logDir, fileName)
        
        LOG.info("Creating log file: ${logFile.absolutePath}")
        
        // 写入会话开始信息
        try {
            logFile.writeText(buildString {
                appendLine("=== Claude Code Plus Session Log ===")
                appendLine("Session ID: ${sessionId ?: "N/A"}")
                appendLine("Started at: ${timestampFormat.format(Date())}")
                appendLine("Log file: ${logFile.absolutePath}")
                appendLine("Project: ${project?.name ?: "Unknown"}")
                appendLine("Project Path: ${project?.basePath ?: "Unknown"}")
                appendLine("=" * 50)
                appendLine()
            })
            LOG.info("Successfully wrote initial content to log file")
        } catch (e: Exception) {
            LOG.error("Failed to write initial log content", e)
            throw e
        }
        
        LOG.info("Created session log: ${logFile.absolutePath}, exists: ${logFile.exists()}")
        return logFile
    }
    
    // 记录请求
    fun logRequest(
        logFile: File,
        requestType: String,
        message: String,
        options: Map<String, Any>? = null
    ) {
        try {
            logFile.appendText(buildString {
                appendLine("\n[REQUEST] ${timestampFormat.format(Date())}")
                appendLine("Type: $requestType")
                appendLine("Message: $message")
                if (options != null) {
                    appendLine("Options: ${gson.toJson(options)}")
                }
                appendLine("-" * 40)
            })
        } catch (e: Exception) {
            LOG.error("Failed to log request", e)
        }
    }
    
    // 记录响应块
    fun logResponseChunk(
        logFile: File,
        chunkType: String,
        content: String? = null,
        error: String? = null,
        metadata: Map<String, Any>? = null
    ) {
        try {
            logFile.appendText(buildString {
                appendLine("\n[RESPONSE CHUNK] ${timestampFormat.format(Date())}")
                appendLine("Type: $chunkType")
                if (content != null) {
                    // 保留原始内容，包括 ANSI 转义序列和 Markdown 格式
                    appendLine("Content Length: ${content.length} chars")
                    appendLine("Content:")
                    appendLine(content)
                    
                    // 检测特殊格式
                    val formats = mutableListOf<String>()
                    if (content.contains("\u001b[") || content.contains("\u001B[")) {
                        formats.add("ANSI")
                    }
                    if (content.contains("```") || content.contains("#") || content.contains("[]()")) {
                        formats.add("Markdown")
                    }
                    if (formats.isNotEmpty()) {
                        appendLine("Detected formats: ${formats.joinToString(", ")}")
                    }
                }
                if (error != null) {
                    appendLine("Error: $error")
                }
                if (metadata != null) {
                    appendLine("Metadata: ${gson.toJson(metadata)}")
                }
                appendLine("-" * 20)
            })
        } catch (e: Exception) {
            LOG.error("Failed to log response chunk", e)
        }
    }
    
    // 记录完整响应
    fun logFullResponse(
        logFile: File,
        response: String,
        success: Boolean,
        error: String? = null
    ) {
        try {
            logFile.appendText(buildString {
                appendLine("\n[FULL RESPONSE] ${timestampFormat.format(Date())}")
                appendLine("Success: $success")
                if (error != null) {
                    appendLine("Error: $error")
                }
                appendLine("Response length: ${response.length} characters")
                
                // 检测内容格式
                val hasAnsi = response.contains("\u001b[") || response.contains("\u001B[")
                val hasMarkdown = response.contains("```") || response.contains("#") || 
                    response.contains("[]()") || response.contains("**")
                
                if (hasAnsi) appendLine("Contains: ANSI escape sequences")
                if (hasMarkdown) appendLine("Contains: Markdown formatting")
                
                appendLine("Response:")
                appendLine(response)
                appendLine("=" * 50)
            })
        } catch (e: Exception) {
            LOG.error("Failed to log full response", e)
        }
    }
    
    // 记录 WebSocket 消息
    fun logWebSocketMessage(
        logFile: File,
        direction: String, // "SENT" or "RECEIVED"
        message: String
    ) {
        try {
            logFile.appendText(buildString {
                appendLine("\n[WS $direction] ${timestampFormat.format(Date())}")
                
                // 尝试格式化 JSON
                try {
                    val jsonElement = JsonParser.parseString(message)
                    appendLine("Message (formatted):")
                    appendLine(gson.toJson(jsonElement))
                } catch (e: Exception) {
                    appendLine("Message (raw):")
                    appendLine(message)
                }
                appendLine("-" * 20)
            })
        } catch (e: Exception) {
            LOG.error("Failed to log WebSocket message", e)
        }
    }
    
    // 记录会话结束
    fun closeSessionLog(logFile: File) {
        try {
            logFile.appendText(buildString {
                appendLine("\n\n=== Session Ended ===")
                appendLine("Ended at: ${timestampFormat.format(Date())}")
                appendLine("=" * 50)
            })
        } catch (e: Exception) {
            LOG.error("Failed to close session log", e)
        }
    }
    
    // 获取最近的日志文件列表
    fun getRecentLogs(limit: Int = 10, project: Project? = null): List<File> {
        val logDir = getLogDirectory(project)
        return logDir.listFiles { file -> file.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            ?: emptyList()
    }
    
    // 记录原始内容（用于调试）
    fun logRawContent(
        logFile: File,
        label: String,
        content: String
    ) {
        try {
            logFile.appendText(buildString {
                appendLine("\n[RAW CONTENT - $label] ${timestampFormat.format(Date())}")
                appendLine("Length: ${content.length} chars")
                appendLine("First 100 chars (escaped): ${content.take(100).toCharArray().joinToString("") { 
                    when (it) {
                        '\n' -> "\\n"
                        '\r' -> "\\r"
                        '\t' -> "\\t"
                        '\u001B' -> "\\033"
                        else -> if (it.code < 32 || it.code > 126) "\\x${it.code.toString(16)}" else it.toString()
                    }
                }}")
                appendLine("Raw content:")
                appendLine(content)
                appendLine("-" * 40)
            })
        } catch (e: Exception) {
            LOG.error("Failed to log raw content", e)
        }
    }
    
    // 清理旧日志（保留最近N个）
    fun cleanOldLogs(keepCount: Int = 50, project: Project? = null) {
        val logDir = getLogDirectory(project)
        val logs = logDir.listFiles { file -> file.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
            
        if (logs.size > keepCount) {
            logs.drop(keepCount).forEach { file ->
                try {
                    file.delete()
                    LOG.info("Deleted old log: ${file.name}")
                } catch (e: Exception) {
                    LOG.error("Failed to delete old log: ${file.name}", e)
                }
            }
        }
    }
}

// 扩展函数
private operator fun String.times(count: Int): String = repeat(count)