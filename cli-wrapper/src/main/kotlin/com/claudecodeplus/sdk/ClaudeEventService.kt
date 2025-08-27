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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray

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
            onOutput = { outputLine ->
                println("[ClaudeEventService] 新会话收到输出: $outputLine")
                scope.launch {
                    try {
                        val message = parseOutputLine(outputLine)
                        if (message != null) {
                            println("[ClaudeEventService] 新会话解析消息成功: ${message.type}")
                            eventChannel.send(ClaudeEvent.MessageReceived(message))
                        } else {
                            println("[ClaudeEventService] 新会话解析消息失败，跳过")
                        }
                    } catch (e: Exception) {
                        println("[ClaudeEventService] 新会话解析异常: ${e.message}")
                        eventChannel.send(ClaudeEvent.ParseError(outputLine, e))
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
            onOutput = { outputLine ->
                scope.launch {
                    try {
                        val message = parseOutputLine(outputLine)
                        if (message != null) {
                            eventChannel.send(ClaudeEvent.MessageReceived(message))
                        }
                    } catch (e: Exception) {
                        eventChannel.send(ClaudeEvent.ParseError(outputLine, e))
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
        val osName = System.getProperty("os.name").lowercase()
        
        // 构建 claude 命令及其参数
        val claudeArgs = mutableListOf<String>()
        
        // 使用用户自定义命令或默认的 "claude"
        val claudeCommand = options.customCommand ?: "claude"
        claudeArgs.add(claudeCommand)
        
        // 会话控制参数（如果有）
        if (options.resume != null && options.resume.isNotBlank()) {
            claudeArgs.add("--resume")
            claudeArgs.add(options.resume)
        }
        
        // 核心参数必须放在 prompt 之前
        if (options.model != null) {
            claudeArgs.add("--model")
            claudeArgs.add(options.model)
        }
        
        // 使用 --print 模式和 stream-json 输出
        claudeArgs.add("--print")
        claudeArgs.add("--output-format")
        claudeArgs.add("stream-json")
        claudeArgs.add("--verbose")
        
        // 权限设置
        if (options.skipPermissions) {
            claudeArgs.add("--dangerously-skip-permissions")
        } else {
            claudeArgs.add("--permission-mode")
            claudeArgs.add(options.permissionMode)
        }
        
        // prompt 参数放在最后
        claudeArgs.add(prompt)
        
        // 根据操作系统选择合适的 shell 来执行命令
        return when {
            osName.contains("win") -> {
                // Windows: 使用 cmd
                listOf("cmd", "/c", claudeArgs.joinToString(" ") { 
                    if (it.contains(" ")) "\"$it\"" else it 
                })
            }
            osName.contains("mac") -> {
                // macOS: 使用 zsh (默认shell)
                listOf("/bin/zsh", "-c", claudeArgs.joinToString(" ") { 
                    if (it.contains(" ")) "'$it'" else it 
                })
            }
            else -> {
                // Linux: 使用 bash
                listOf("/bin/bash", "-c", claudeArgs.joinToString(" ") { 
                    if (it.contains(" ")) "'$it'" else it 
                })
            }
        }
    }
    
    
    /**
     * 解析CLI输出行（支持JSON和非JSON内容）
     */
    private fun parseOutputLine(line: String): SDKMessage? {
        if (line.isBlank()) return null
        
        // 首先尝试作为JSON解析
        if (line.trim().startsWith("{") && line.trim().endsWith("}")) {
            return parseJsonLine(line)
        }
        
        // 对于非JSON内容，创建简单的文本消息
        return SDKMessage(
            type = MessageType.TEXT,
            data = MessageData(text = line),
            content = line
        )
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
            
            // 提取消息内容 - 保留原始JSON让MessageConverter处理
            val messageContent = line
            
            // 智能检测消息类型：区分纯文本消息和包含工具调用的消息
            val actualType = detectActualMessageType(type, jsonElement)
            
            return SDKMessage(
                type = actualType,
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
    
    /**
     * 智能检测实际消息类型
     * 根据消息内容区分纯文本消息和包含工具调用/结果的消息
     */
    private fun detectActualMessageType(type: String, jsonElement: JsonObject): MessageType {
        return when (type.lowercase()) {
            "assistant" -> {
                // 检查助手消息是否包含工具调用
                val messageContent = jsonElement["message"]?.jsonObject?.get("content")?.jsonArray
                val hasToolUse = messageContent?.any { element ->
                    element.jsonObject["type"]?.jsonPrimitive?.content == "tool_use" 
                } ?: false
                
                if (hasToolUse) {
                    println("[ClaudeEventService] 检测到工具调用助手消息")
                    MessageType.TOOL_USE
                } else {
                    println("[ClaudeEventService] 检测到纯文本助手消息")
                    MessageType.TEXT
                }
            }
            "user" -> {
                // 检查用户消息是否包含工具结果
                val messageContent = jsonElement["message"]?.jsonObject?.get("content")?.jsonArray
                val hasToolResult = messageContent?.any { element ->
                    element.jsonObject["type"]?.jsonPrimitive?.content == "tool_result" 
                } ?: false
                
                if (hasToolResult) {
                    println("[ClaudeEventService] 检测到工具结果用户消息")
                    MessageType.TOOL_RESULT
                } else {
                    println("[ClaudeEventService] 检测到纯文本用户消息")
                    MessageType.TEXT
                }
            }
            "error" -> MessageType.ERROR
            "tool_use" -> MessageType.TOOL_USE
            "tool_result" -> MessageType.TOOL_RESULT
            "start" -> MessageType.START
            "end" -> MessageType.END
            else -> {
                println("[ClaudeEventService] 未知消息类型: $type，默认为TEXT")
                MessageType.TEXT
            }
        }
    }
}