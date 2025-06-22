package com.claudecodeplus.sdk

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Claude CLI 包装器
 * 直接调用 claude 命令行工具，避免通过 Node.js 服务中转
 * 
 * 工作原理：
 * 1. 通过 ProcessBuilder 直接调用系统中的 claude CLI 命令
 * 2. 使用 --output-format stream-json 参数获取流式 JSON 输出
 * 3. 解析 JSON 流并转换为 Kotlin Flow，支持实时响应
 * 4. 通过环境变量 CLAUDE_CODE_ENTRYPOINT 标识调用来源
 */
class ClaudeCliWrapper {
    companion object {
        private val logger = Logger.getLogger(ClaudeCliWrapper::class.java.name)
    }
    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
    
    // 存储当前运行的进程，用于终止响应
    private val currentProcess = AtomicReference<Process?>(null)
    
    data class QueryOptions(
        val model: String? = null,
        val maxTurns: Int? = null,
        val customSystemPrompt: String? = null,
        val appendSystemPrompt: String? = null,
        val permissionMode: String = "default",  // "default" 正常权限检查，"skip" 跳过所有权限检查
        val allowedTools: List<String> = emptyList(),
        val disallowedTools: List<String> = emptyList(),
        val mcpServers: Map<String, Any>? = null,
        val cwd: String? = null,
        val continueConversation: Boolean = false,
        val resume: String? = null,
        val fallbackModel: String? = null
    )
    
    /**
     * 执行 Claude 查询
     * @param prompt 用户提示
     * @param options 查询选项
     * @return 响应消息流
     */
    fun query(prompt: String, options: QueryOptions = QueryOptions()): Flow<SDKMessage> = flow {
        val args = mutableListOf<String>()
        
        // 基础参数
        args.addAll(listOf("--output-format", "stream-json", "--verbose"))
        
        // 可选参数
        options.customSystemPrompt?.let { args.addAll(listOf("--system-prompt", it)) }
        options.appendSystemPrompt?.let { args.addAll(listOf("--append-system-prompt", it)) }
        options.maxTurns?.let { args.addAll(listOf("--max-turns", it.toString())) }
        options.model?.let { args.addAll(listOf("--model", it)) }
        
        // 会话管理：resume 和 continue 是互斥的
        when {
            options.resume != null -> {
                // 恢复特定会话
                args.addAll(listOf("--resume", options.resume))
            }
            options.continueConversation -> {
                // 继续当前会话
                args.add("--continue")
            }
        }
        
        if (options.allowedTools.isNotEmpty()) {
            args.addAll(listOf("--allowedTools", options.allowedTools.joinToString(",")))
        }
        
        if (options.disallowedTools.isNotEmpty()) {
            args.addAll(listOf("--disallowedTools", options.disallowedTools.joinToString(",")))
        }
        
        options.mcpServers?.let {
            args.addAll(listOf("--mcp-config", objectMapper.writeValueAsString(mapOf("mcpServers" to it))))
        }
        
        // 权限控制
        // Claude CLI 使用 --dangerously-skip-permissions 来跳过权限检查
        // 根据 permissionMode 值来决定是否添加该参数
        if (options.permissionMode == "skip") {
            args.add("--dangerously-skip-permissions")
        }
        // 注意：Claude CLI 当前不支持细粒度的权限模式控制
        // 只能通过 allowedTools 和 disallowedTools 来控制工具权限
        
        options.fallbackModel?.let {
            if (options.model == it) {
                throw IllegalArgumentException("Fallback model cannot be the same as the main model")
            }
            args.addAll(listOf("--fallback-model", it))
        }
        
        // 添加提示词
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Prompt is required")
        }
        args.addAll(listOf("--print", prompt.trim()))
        
        // 构建进程
        val processBuilder = ProcessBuilder("claude", *args.toTypedArray())
        options.cwd?.let { processBuilder.directory(java.io.File(it)) }
        
        // 设置环境变量，标识调用来源
        // 这个环境变量会被 Claude CLI 识别，用于统计和跟踪
        processBuilder.environment()["CLAUDE_CODE_ENTRYPOINT"] = "sdk-kotlin"
        
        val process = processBuilder.start()
        currentProcess.set(process)
        
        // 关闭输入流
        process.outputStream.close()
        
        // 读取输出
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
        
        // 启动错误流读取
        val errorBuilder = StringBuilder()
        Thread {
            errorReader.useLines { lines ->
                lines.forEach { line ->
                    errorBuilder.appendLine(line)
                    logger.warning("Claude CLI stderr: $line")
                }
            }
        }.start()
        
        try {
            reader.useLines { lines ->
                lines.forEach { line ->
                    // 检查协程是否被取消
                    coroutineContext.ensureActive()
                        
                        if (line.trim().isNotEmpty()) {
                            try {
                                // 解析 Claude CLI 的 JSON 输出
                                val jsonNode = objectMapper.readTree(line)
                                val type = jsonNode.get("type")?.asText()
                                
                                when (type) {
                                    "assistant" -> {
                                        // 提取助手消息
                                        logger.fine("Processing assistant message: $line")
                                        val content = jsonNode.get("message")?.get("content")
                                        if (content != null) {
                                            if (content.isArray) {
                                                content.forEach { item ->
                                                    if (item.get("type")?.asText() == "text") {
                                                        val text = item.get("text")?.asText()
                                                        if (!text.isNullOrEmpty()) {
                                                            logger.fine("Emitting text message: ${text.take(50)}...")
                                                            emit(SDKMessage(
                                                                type = MessageType.TEXT,
                                                                data = MessageData(text = text)
                                                            ))
                                                        }
                                                    }
                                                }
                                            } else if (content.isTextual) {
                                                // 处理直接的文本内容
                                                val text = content.asText()
                                                if (text.isNotEmpty()) {
                                                    logger.fine("Emitting direct text message: ${text.take(50)}...")
                                                    emit(SDKMessage(
                                                        type = MessageType.TEXT,
                                                        data = MessageData(text = text)
                                                    ))
                                                }
                                            }
                                        } else {
                                            logger.warning("Assistant message has no content: $jsonNode")
                                        }
                                    }
                                    "error" -> {
                                        // 错误消息
                                        val error = jsonNode.get("error")?.asText() ?: "Unknown error"
                                        emit(SDKMessage(
                                            type = MessageType.ERROR,
                                            data = MessageData(error = error)
                                        ))
                                    }
                                    "system" -> {
                                        // 系统消息，如初始化
                                        logger.fine("System message: $line")
                                        // 提取会话ID
                                        val sessionId = jsonNode.get("session_id")?.asText()
                                        if (sessionId != null) {
                                            emit(SDKMessage(
                                                type = MessageType.START,
                                                data = MessageData(sessionId = sessionId)
                                            ))
                                        }
                                    }
                                    "result" -> {
                                        // 结果消息，表示对话结束
                                        logger.fine("Result message: $line")
                                    }
                                    else -> {
                                        logger.fine("Unknown message type: $type")
                                    }
                            }
                        } catch (e: Exception) {
                            // 解析失败，可能是非JSON输出
                            logger.log(java.util.logging.Level.WARNING, "Failed to parse line: $line", e)
                        }
                    }
                }
            }
            
            // 等待进程结束
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val errorMessage = errorBuilder.toString()
                throw RuntimeException("Claude process exited with code $exitCode. Error: $errorMessage")
            }
        } finally {
            currentProcess.set(null)
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 终止当前正在运行的查询
     * @return 是否成功终止
     */
    fun terminate(): Boolean {
        val process = currentProcess.getAndSet(null)
        return if (process != null && process.isAlive) {
            logger.info("Terminating Claude process...")
            // 先尝试正常终止
            process.destroy()
            
            // 给进程一点时间来清理
            try {
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    // 如果2秒后还没结束，强制终止
                    logger.warning("Process did not terminate gracefully, forcing termination")
                    process.destroyForcibly()
                }
            } catch (e: InterruptedException) {
                // 如果等待被中断，强制终止
                process.destroyForcibly()
            }
            true
        } else {
            false
        }
    }
    
    /**
     * 检查是否有正在运行的查询
     */
    fun isRunning(): Boolean {
        val process = currentProcess.get()
        return process != null && process.isAlive
    }
    
    /**
     * 执行简单的单轮对话
     */
    suspend fun chat(prompt: String, model: String? = null): String {
        val messages = mutableListOf<String>()
        
        query(prompt, QueryOptions(model = model)).collect { message ->
            when (message.type) {
                MessageType.TEXT -> {
                    messages.add(message.data.text ?: "")
                }
                MessageType.ERROR -> {
                    throw RuntimeException("Claude error: ${message.data.error}")
                }
                else -> {
                    // 忽略其他类型的消息
                }
            }
        }
        
        return messages.joinToString("")
    }
}