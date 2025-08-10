package com.claudecodeplus.sdk

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Claude 会话事件服务
 * 管理会话生命周期，处理消息流，维护会话状态
 * 完全符合 Claudia 项目的会话管理策略
 */
class ClaudeEventService(
    private val processHandler: ClaudeProcessEventHandler,
    private val cliWrapper: ClaudeCliWrapper,
    private val historyLoader: SessionHistoryLoader
) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 启动新会话（对应 Claudia 的 executeClaudeCode）
     * 使用与 Claudia 完全相同的参数模式
     */
    fun startNewSession(
        projectPath: String, 
        prompt: String,
        options: ClaudeCliWrapper.QueryOptions
    ): Flow<ClaudeEvent> = flow {
        // 构建命令，确保不使用 --resume 参数（新会话）
        val command = buildClaudeCommandList(
            prompt = prompt, 
            options = options.copy(resume = null, cwd = projectPath), // 明确移除 resume 参数并设置工作目录
            projectPath = projectPath
        )
        
        println("[ClaudeEventService] 启动新会话命令: ${command.joinToString(" ")}")
        
        // 使用 Channel 来处理异步事件
        val eventChannel = Channel<ClaudeEvent>()
        val scope = CoroutineScope(Dispatchers.IO)
        
        val process = processHandler.executeWithEvents(
            command = command,
            workingDirectory = projectPath,
            sessionId = null, // 新会话没有 sessionId
            onOutput = { jsonLine ->
                println("[ClaudeEventService] 新会话收到输出: $jsonLine")
                scope.launch {
                    try {
                        val message = parseJsonLine(jsonLine)
                        if (message != null) {
                            println("[ClaudeEventService] 新会话解析消息成功: ${message.type}")
                            eventChannel.send(ClaudeEvent.MessageReceived(message))
                        } else {
                            println("[ClaudeEventService] 新会话解析消息失败，跳过")
                        }
                    } catch (e: Exception) {
                        println("[ClaudeEventService] 新会话解析异常: ${e.message}")
                        eventChannel.send(ClaudeEvent.ParseError(jsonLine, e))
                    }
                }
            },
            onError = { errorLine ->
                println("[ClaudeEventService] 新会话收到错误: $errorLine")
                scope.launch {
                    eventChannel.send(ClaudeEvent.ProcessError(errorLine))
                }
            },
            onComplete = { success ->
                println("[ClaudeEventService] 新会话进程完成: success=$success")
                scope.launch {
                    eventChannel.send(ClaudeEvent.ProcessComplete(success))
                    eventChannel.send(ClaudeEvent.SessionComplete(success))
                    // 进程完成后关闭 channel
                    eventChannel.close()
                }
            }
        )
        
        // 使用协程收集事件并发送
        eventChannel.consumeAsFlow().collect { event ->
            emit(event)
        }
    }
    
    /**
     * 恢复已有会话（对应 Claudia 的 resumeClaudeCode）
     * 关键：先预加载历史，再继续会话
     */
    fun resumeExistingSession(
        sessionId: String,
        projectPath: String,
        prompt: String,
        options: ClaudeCliWrapper.QueryOptions
    ): Flow<ClaudeEvent> = flow {
        // 1. 先预加载历史记录（关键步骤，符合 Claudia 模式）
        try {
            val historyMessages = historyLoader.loadSessionHistory(sessionId, projectPath)
            historyMessages.forEach { message ->
                emit(ClaudeEvent.HistoryMessageLoaded(message))
            }
        } catch (e: Exception) {
            emit(ClaudeEvent.HistoryLoadError(e.message ?: "Failed to load history"))
        }
        
        // 2. 然后使用 --resume 继续会话
        val resumeOptions = options.copy(resume = sessionId)
        val command = buildClaudeCommandList(
            prompt = prompt,
            options = resumeOptions, 
            projectPath = projectPath
        )
        
        println("[ClaudeEventService] 恢复会话命令: ${command.joinToString(" ")}")
        
        // 使用 Channel 来处理异步事件
        val eventChannel = Channel<ClaudeEvent>()
        val scope = CoroutineScope(Dispatchers.IO)
        
        val process = processHandler.executeWithEvents(
            command = command,
            workingDirectory = projectPath,
            sessionId = sessionId,
            onOutput = { jsonLine ->
                scope.launch {
                    try {
                        val message = parseJsonLine(jsonLine)
                        if (message != null) {
                            eventChannel.send(ClaudeEvent.MessageReceived(message))
                        }
                    } catch (e: Exception) {
                        eventChannel.send(ClaudeEvent.ParseError(jsonLine, e))
                    }
                }
            },
            onError = { errorLine ->
                scope.launch {
                    eventChannel.send(ClaudeEvent.ProcessError(errorLine))
                }
            },
            onComplete = { success ->
                scope.launch {
                    eventChannel.send(ClaudeEvent.ProcessComplete(success))
                    eventChannel.send(ClaudeEvent.SessionComplete(success))
                    eventChannel.close()
                }
            }
        )
        
        // 使用协程收集事件并发送
        eventChannel.consumeAsFlow().collect { event ->
            emit(event)
        }
    }
    
    /**
     * 智能会话处理（根据是否为首次消息选择策略）
     * 完全模仿 Claudia 的逻辑：
     * - if (effectiveSession && !isFirstPrompt) -> resumeClaudeCode
     * - else -> executeClaudeCode
     */
    fun handleMessage(
        sessionId: String?,
        isFirstMessage: Boolean,
        projectPath: String,
        prompt: String,
        options: ClaudeCliWrapper.QueryOptions
    ): Flow<ClaudeEvent> {
        return if (sessionId != null && !isFirstMessage) {
            // 有会话ID且非首次消息 -> 恢复会话
            resumeExistingSession(sessionId, projectPath, prompt, options)
        } else {
            // 无会话ID或首次消息 -> 新会话  
            startNewSession(projectPath, prompt, options)
        }
    }
    
    /**
     * 构建 Claude CLI 命令
     * 完全符合 Claudia 的参数模式
     */
    private fun buildClaudeCommandList(
        prompt: String,
        options: ClaudeCliWrapper.QueryOptions,
        projectPath: String
    ): List<String> {
        val args = mutableListOf<String>()
        
        // 查找 claude 命令路径
        val claudeCommand = options.customCommand ?: findClaudeCommand()
        args.add(claudeCommand)
        
        // 会话控制参数（如果有）
        if (options.resume != null && options.resume.isNotBlank()) {
            args.add("--resume")
            args.add(options.resume)
        }
        
        // 核心参数必须放在 prompt 之前
        if (options.model != null) {
            args.add("--model")
            args.add(options.model)
        }
        
        // 使用 --print 模式和 stream-json 输出
        args.add("--print")
        args.add("--output-format")
        args.add("stream-json")
        args.add("--verbose")
        
        // 权限设置
        if (options.skipPermissions) {
            args.add("--dangerously-skip-permissions")
        } else {
            args.add("--permission-mode")
            args.add(options.permissionMode)
        }
        
        // prompt 参数放在最后
        args.add(prompt)
        
        return args
    }
    
    /**
     * 查找 Claude CLI 命令路径
     */
    private fun findClaudeCommand(): String {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") -> {
                // Windows: 查找 claude.cmd
                val paths = listOf(
                    "claude.cmd",
                    "C:\\Users\\${System.getProperty("user.name")}\\AppData\\Roaming\\npm\\claude.cmd",
                    "C:\\Program Files\\nodejs\\claude.cmd"
                )
                paths.firstOrNull { commandExists(it) } ?: "claude.cmd"
            }
            else -> {
                // Unix 系统: 查找 claude
                val paths = listOf(
                    "claude",
                    "/usr/local/bin/claude",
                    "${System.getProperty("user.home")}/.local/bin/claude"
                )
                paths.firstOrNull { commandExists(it) } ?: "claude"
            }
        }
    }
    
    /**
     * 检查命令是否存在
     */
    private fun commandExists(command: String): Boolean {
        return try {
            val processBuilder = ProcessBuilder(command, "--version")
            val process = processBuilder.start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 解析 JSONL 行为 SDKMessage
     */
    private fun parseJsonLine(line: String): SDKMessage? {
        if (line.isBlank()) return null
        
        try {
            println("[ClaudeEventService] 尝试解析JSON: $line")
            
            // 首先尝试解析为标准 Claude CLI 输出格式
            val jsonElement = json.parseToJsonElement(line)
            if (jsonElement !is JsonObject) {
                println("[ClaudeEventService] JSON不是对象格式")
                return null
            }
            
            // 提取基本字段
            val type = jsonElement["type"]?.jsonPrimitive?.content ?: "text"
            val sessionId = jsonElement["session_id"]?.jsonPrimitive?.content ?: jsonElement["sessionId"]?.jsonPrimitive?.content
            val messageId = jsonElement["uuid"]?.jsonPrimitive?.content
            val parentId = jsonElement["parentUuid"]?.jsonPrimitive?.content
            val timestamp = jsonElement["timestamp"]?.jsonPrimitive?.content ?: ""
            val leafUuid = jsonElement["leafUuid"]?.jsonPrimitive?.content
            val parentUuid = jsonElement["parentUuid"]?.jsonPrimitive?.content
            
            println("[ClaudeEventService] 解析字段: type=$type, sessionId=$sessionId")
            
            // 提取消息内容 - 根据不同类型处理
            val messageContent = when (type) {
                "assistant" -> {
                    // 助手消息可能在 message.content 中
                    jsonElement["message"]?.let { msgElement ->
                        if (msgElement is JsonObject) {
                            msgElement["content"]?.toString() ?: msgElement.toString()
                        } else {
                            msgElement.toString()
                        }
                    } ?: line
                }
                "result" -> {
                    // 结果消息可能在 result 字段中
                    jsonElement["result"]?.jsonPrimitive?.content ?: line
                }
                else -> {
                    jsonElement["message"]?.toString() ?: line
                }
            }
            
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
                    text = messageContent,
                    sessionId = sessionId
                ),
                sessionId = sessionId,
                messageId = messageId,
                parentId = parentId,
                timestamp = timestamp,
                leafUuid = leafUuid,
                parentUuid = parentUuid,
                content = line // 保存原始 JSON 用于后续解析
            )
        } catch (e: Exception) {
            println("Error parsing JSONL line: ${e.message}")
            // 返回原始文本消息作为降级处理
            return SDKMessage(
                type = MessageType.TEXT,
                data = MessageData(text = line),
                content = line
            )
        }
    }
}