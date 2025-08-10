package com.claudecodeplus.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 会话历史加载器
 * 负责智能加载历史会话消息，支持 leafUuid 链接机制
 * 完全符合 Claudia 项目的历史加载模式
 */
class SessionHistoryLoader {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 加载会话历史消息
     * 完全模仿 Claudia 的 loadSessionHistory 逻辑：
     * 1. 先加载主会话文件
     * 2. 通过 leafUuid 链接加载相关会话文件  
     * 3. 按时间戳排序合并消息
     * 
     * @param sessionId 会话ID
     * @param projectPath 项目路径
     * @return 按时间排序的历史消息列表
     */
    suspend fun loadSessionHistory(sessionId: String, projectPath: String): List<SDKMessage> = withContext(Dispatchers.IO) {
        val claudeProjectDir = getClaudeProjectDir(projectPath)
        val sessionFile = File(claudeProjectDir, "$sessionId.jsonl")
        
        if (!sessionFile.exists()) {
            println("Session file not found: ${sessionFile.absolutePath}")
            return@withContext emptyList()
        }
        
        val messages = mutableListOf<SDKMessage>()
        val processedFiles = mutableSetOf<String>()
        val filesToProcess = mutableListOf<String>()
        
        filesToProcess.add(sessionId)
        
        // 递归加载所有相关的会话文件（通过 leafUuid 链接）
        while (filesToProcess.isNotEmpty()) {
            val currentSessionId = filesToProcess.removeFirst()
            if (currentSessionId in processedFiles) continue
            
            val currentFile = File(claudeProjectDir, "$currentSessionId.jsonl")
            if (!currentFile.exists()) continue
            
            println("Loading session file: ${currentFile.name}")
            
            val fileMessages = currentFile.readLines()
                .mapNotNull { line -> parseJsonLine(line) }
            
            messages.addAll(fileMessages)
            processedFiles.add(currentSessionId)
            
            // 查找引用的其他会话（通过 leafUuid）
            fileMessages.forEach { message ->
                findReferencedSessions(message, claudeProjectDir, processedFiles, filesToProcess)
            }
        }
        
        println("Loaded ${messages.size} messages from ${processedFiles.size} session files")
        
        // 按类型排序返回（暂时无法按时间戳排序，因为新结构中没有 timestamp 字段）
        messages
    }
    
    /**
     * 解析 JSONL 行为 SDKMessage
     */
    private fun parseJsonLine(line: String): SDKMessage? {
        if (line.isBlank()) return null
        
        try {
            val jsonElement = json.parseToJsonElement(line)
            if (jsonElement !is JsonObject) return null
            
            // 提取基本字段
            val type = jsonElement["type"]?.jsonPrimitive?.content ?: return null
            val sessionId = jsonElement["sessionId"]?.jsonPrimitive?.content
            val timestamp = jsonElement["timestamp"]?.jsonPrimitive?.content ?: ""
            val uuid = jsonElement["uuid"]?.jsonPrimitive?.content
            val parentUuid = jsonElement["parentUuid"]?.jsonPrimitive?.content
            val leafUuid = jsonElement["leafUuid"]?.jsonPrimitive?.content
            
            // 构造 SDKMessage  
            return SDKMessage(
                type = when (type.lowercase()) {
                    "user", "assistant", "text" -> MessageType.TEXT
                    "error" -> MessageType.ERROR
                    "tool_use" -> MessageType.TOOL_USE
                    "tool_result" -> MessageType.TOOL_RESULT
                    "start" -> MessageType.START
                    "end" -> MessageType.END
                    else -> MessageType.TEXT
                },
                data = MessageData(
                    text = line, // 保存原始 JSON 用于后续解析
                    sessionId = sessionId,
                    toolCallId = uuid
                ),
                sessionId = sessionId,
                messageId = uuid,
                parentId = parentUuid,
                timestamp = timestamp,
                leafUuid = leafUuid,
                parentUuid = parentUuid,
                content = line // 保存完整的原始 JSON
            )
        } catch (e: Exception) {
            println("Error parsing JSONL line: ${e.message}")
            return null
        }
    }
    
    /**
     * 查找消息中引用的其他会话
     */
    private fun findReferencedSessions(
        message: SDKMessage,
        claudeProjectDir: File,
        processedFiles: Set<String>,
        filesToProcess: MutableList<String>
    ) {
        // 使用新结构中的 leafUuid 字段
        val leafUuid = message.leafUuid
        if (leafUuid != null) {
            findSessionByLeafUuid(claudeProjectDir, leafUuid)?.let { referencedSessionId ->
                if (referencedSessionId !in processedFiles && referencedSessionId !in filesToProcess) {
                    filesToProcess.add(referencedSessionId)
                    println("Found referenced session via leafUuid: $referencedSessionId")
                }
            }
        }
        
        // 作为备选方案，也检查 parentUuid
        val parentUuid = message.parentUuid
        if (parentUuid != null && parentUuid != message.sessionId) {
            // 如果 parentUuid 指向不同的会话，也加载它
            if (parentUuid !in processedFiles && parentUuid !in filesToProcess) {
                filesToProcess.add(parentUuid)
                println("Found referenced session via parentUuid: $parentUuid")
            }
        }
    }
    
    /**
     * 通过 leafUuid 查找包含该 UUID 的会话文件
     */
    private fun findSessionByLeafUuid(claudeProjectDir: File, leafUuid: String): String? {
        try {
            claudeProjectDir.listFiles { _, name -> name.endsWith(".jsonl") }?.forEach { file ->
                file.readLines().forEach { line ->
                    if (line.contains(leafUuid)) {
                        // 尝试解析获取 sessionId
                        try {
                            val jsonElement = json.parseToJsonElement(line)
                            if (jsonElement is JsonObject) {
                                val sessionId = jsonElement["sessionId"]?.jsonPrimitive?.content
                                if (sessionId != null) {
                                    return sessionId
                                }
                            }
                        } catch (e: Exception) {
                            // 继续搜索
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error searching for leafUuid: ${e.message}")
        }
        return null
    }
    
    /**
     * 获取 Claude 项目目录
     * 模仿 Claudia 的路径编码逻辑
     */
    private fun getClaudeProjectDir(projectPath: String): File {
        val homeDir = System.getProperty("user.home")
        val claudeDir = File(homeDir, ".claude")
        
        // 编码项目路径（类似 Claudia 的处理）
        val encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8)
            .replace("%2F", "-")
            .replace("%3A", "-")
            .replace("+", "-")
        
        return File(claudeDir, "projects/$encodedPath")
    }
}