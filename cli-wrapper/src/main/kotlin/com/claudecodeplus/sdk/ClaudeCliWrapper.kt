package com.claudecodeplus.sdk

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
import kotlinx.serialization.json.*

/**
 * Claude CLI 包装器
 * 使用 kotlinx.serialization 解析 Claude CLI 输出
 * 
 * 工作原理：
 * 1. 通过 ProcessBuilder 直接调用系统中的 claude CLI 命令
 * 2. 使用 --output-format stream-json 参数获取流式 JSON 输出
 * 3. 使用 kotlinx.serialization 解析 JSON 流并转换为 Kotlin Flow
 * 4. 通过环境变量 CLAUDE_CODE_ENTRYPOINT 标识调用来源
 */
class ClaudeCliWrapper {
    companion object {
        private val logger = Logger.getLogger(ClaudeCliWrapper::class.java.name)
        
        /**
         * 查找 Claude 命令的路径
         * 支持自定义命令路径，默认查找系统中的 claude 命令
         */
        private fun findClaudeCommand(customCommand: String? = null): String {
            // 如果提供了自定义命令，直接使用
            if (!customCommand.isNullOrBlank()) {
                logger.info("Using custom claude command: $customCommand")
                return customCommand
            }
            
            val osName = System.getProperty("os.name").lowercase()
            
            // Windows 特定路径
            if (osName.contains("windows")) {
                val windowsPaths = listOf(
                    "claude.cmd",
                    "claude.exe",
                    "${System.getProperty("user.home")}\\AppData\\Roaming\\npm\\claude.cmd",
                    "C:\\Program Files\\nodejs\\claude.cmd"
                )
                
                for (path in windowsPaths) {
                    if (java.io.File(path).exists()) {
                        logger.info("Found claude at: $path")
                        return path
                    }
                }
                
                // 尝试 where 命令
                try {
                    val process = ProcessBuilder("where", "claude").start()
                    val output = process.inputStream.bufferedReader().readText().trim()
                    if (process.waitFor() == 0 && output.isNotEmpty()) {
                        val claudePath = output.lines().first()
                        logger.info("Found claude via where: $claudePath")
                        return claudePath
                    }
                } catch (e: Exception) {
                    logger.fine("Failed to find claude via where: ${e.message}")
                }
            } else {
                // Unix/Mac 路径
                val unixPaths = listOf(
                    "/usr/local/bin/claude",
                    "/usr/bin/claude",
                    "/opt/homebrew/bin/claude",
                    "${System.getProperty("user.home")}/.npm-global/bin/claude"
                )
                
                for (path in unixPaths) {
                    if (java.io.File(path).exists()) {
                        logger.info("Found claude at: $path")
                        return path
                    }
                }
                
                // 尝试 which 命令
                try {
                    val process = ProcessBuilder("which", "claude").start()
                    val output = process.inputStream.bufferedReader().readText().trim()
                    if (process.waitFor() == 0 && output.isNotEmpty()) {
                        logger.info("Found claude via which: $output")
                        return output
                    }
                } catch (e: Exception) {
                    logger.fine("Failed to find claude via which: ${e.message}")
                }
            }
            
            // 默认值
            return "claude"
        }
        
        /**
         * 检查命令是否可用
         */
        private fun isCommandAvailable(command: String): Boolean {
            return try {
                val process = ProcessBuilder(command, "--version").start()
                process.waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }
    }
    
    // 存储当前运行的进程，用于终止响应
    private val currentProcess = AtomicReference<Process?>(null)
    
    /**
     * 权限模式枚举
     */
    enum class PermissionMode(val cliValue: String) {
        /** 正常权限检查（默认） */
        DEFAULT("default"),
        
        /** 跳过权限检查 */
        BYPASS_PERMISSIONS("bypassPermissions"),
        
        /** 自动接受编辑操作 */
        ACCEPT_EDITS("acceptEdits"),
        
        /** 计划模式 */
        PLAN("plan")
    }
    
    /**
     * Claude CLI 查询选项
     */
    data class QueryOptions(
        // 模型配置
        val model: String? = null,
        val fallbackModel: String? = null,
        
        // 对话控制
        val maxTurns: Int? = null,
        val customSystemPrompt: String? = null,
        
        // 会话管理
        val resume: String? = null,
        
        // 权限设置
        val permissionMode: String = PermissionMode.DEFAULT.cliValue,
        val skipPermissions: Boolean = true,  // 是否跳过权限检查
        
        // 工作目录
        val cwd: String? = null,
        
        // MCP 服务器
        val mcpServers: Map<String, Any>? = null,
        
        // 调试
        val debug: Boolean = false,
        val showStats: Boolean = false,
        val requestId: String? = null,
        
        // 自定义命令路径
        val customCommand: String? = null
    )
    
    /**
     * 执行查询，返回流式响应
     */
    suspend fun query(prompt: String, options: QueryOptions = QueryOptions()): Flow<SDKMessage> = flow {
        val requestId = options.requestId ?: System.currentTimeMillis().toString()
        
        // 验证输入
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Prompt cannot be empty")
        }
        
        // 验证模型配置
        if (options.model != null && options.fallbackModel != null && options.model == options.fallbackModel) {
            throw IllegalArgumentException("Fallback model cannot be the same as the main model")
        }
        
        logger.info("🔵 [$requestId] 开始查询: ${prompt.take(100)}...")
        
        // 检查协程状态
        coroutineContext.ensureActive()
        
        // 构建命令行参数
        val args = mutableListOf<String>()
        
        // 添加 --print 参数以获取输出（非交互模式）
        args.add("--print")
        
        // 根据选项添加 --dangerously-skip-permissions 参数
        if (options.skipPermissions) {
            args.add("--dangerously-skip-permissions")
        }
        
        // 权限模式
        args.addAll(listOf("--permission-mode", options.permissionMode))
        
        // 模型配置
        options.model?.let { args.addAll(listOf("--model", it)) }
        options.fallbackModel?.let { args.addAll(listOf("--fallback-model", it)) }
        
        // 会话管理
        if (options.resume != null && options.resume.isNotBlank()) {
            args.addAll(listOf("--resume", options.resume))
        }
        
        // 对话控制
        options.maxTurns?.let { args.addAll(listOf("--max-turns", it.toString())) }
        
        // 系统提示词
        options.customSystemPrompt?.let { args.addAll(listOf("--append-system-prompt", it)) }
        
        // MCP 服务器配置
        options.mcpServers?.let { servers ->
            val json = Json.encodeToString(JsonObject.serializer(), buildJsonObject {
                servers.forEach { (name, config) ->
                    put(name, Json.encodeToJsonElement(config))
                }
            })
            args.addAll(listOf("--mcp-config", json))
        }
        
        // 调试选项
        if (options.debug) args.add("--debug")
        if (options.showStats) args.add("--show-stats")
        
        // 输出格式
        args.addAll(listOf("--output-format", "stream-json", "--verbose"))
        
        // 添加用户提示
        args.add(prompt)
        
        logger.info("🔵 [$requestId] 构建参数: ${args.joinToString(" ")}")
        
        val claudeCommand = withContext(Dispatchers.IO) {
            findClaudeCommand(options.customCommand)
        }
        
        logger.info("🔵 [$requestId] 完整命令行: $claudeCommand ${args.joinToString(" ")}")
        
        val processBuilder = ProcessBuilder(claudeCommand, *args.toTypedArray())
        options.cwd?.let { 
            processBuilder.directory(java.io.File(it))
            logger.info("🔵 [$requestId] 工作目录: $it")
        }
        
        // 设置环境变量
        val env = processBuilder.environment()
        env["CLAUDE_CODE_ENTRYPOINT"] = "sdk-kotlin"
        env["LANG"] = "en_US.UTF-8"
        env["LC_ALL"] = "en_US.UTF-8"
        env["PYTHONIOENCODING"] = "utf-8"
        
        // Windows 特定的编码设置
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            env["CHCP"] = "65001"  // UTF-8 代码页
        }
        
        logger.info("🔵 [$requestId] 启动 Claude CLI 进程...")
        val process = processBuilder.start()
        currentProcess.set(process)
        logger.info("🔵 [$requestId] 进程已启动，PID: ${process.pid()}")
        
        // 关闭输入流
        process.outputStream.close()
        
        // 读取输出，指定 UTF-8 编码
        val reader = BufferedReader(InputStreamReader(process.inputStream, "UTF-8"))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream, "UTF-8"))
        logger.info("🔵 [$requestId] 开始读取输出流...")
        
        // 启动错误流读取
        val errorBuilder = StringBuilder()
        Thread {
            errorReader.useLines { lines ->
                lines.forEach { line ->
                    errorBuilder.appendLine(line)
                    when {
                        line.contains("[DEP0190]") -> {
                            logger.fine("Node.js deprecation warning: $line")
                        }
                        line.contains("error", ignoreCase = true) -> {
                            logger.severe("Claude CLI error: $line")
                        }
                        else -> {
                            logger.warning("Claude CLI stderr: $line")
                        }
                    }
                }
            }
        }.start()
        
        try {
            reader.useLines { lines ->
                lines.forEach { line ->
                    // 检查协程是否被取消
                    try {
                        coroutineContext.ensureActive()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        logger.info("Coroutine cancelled, terminating process...")
                        terminate()
                        throw e
                    }
                    
                    if (line.trim().isNotEmpty()) {
                        try {
                            // 清理ANSI序列并记录日志
                            val cleanLine = AnsiProcessor.cleanAnsiSequences(line)
                            logger.info("🔵 [$requestId] 收到行: ${cleanLine.take(200)}")
                            
                            // 跳过空行和非JSON行
                            if (cleanLine.trim().isEmpty() || !cleanLine.trim().startsWith("{")) {
                                logger.info("🔵 [$requestId] 跳过非JSON行")
                                return@forEach
                            }
                            
                            // 使用新的序列化解析消息
                            val message = parseClaudeMessage(cleanLine)
                            if (message != null) {
                                processMessage(message)?.let { sdkMessage ->
                                    emit(sdkMessage)
                                }
                            }
                        } catch (e: Exception) {
                            logger.warning("解析消息失败: ${e.message}")
                        }
                    }
                }
            }
            
            // 等待进程结束
            val exitCode = process.waitFor()
            logger.info("🔵 [$requestId] 进程退出，退出码: $exitCode")
            
            if (exitCode != 0) {
                val errorMsg = errorBuilder.toString()
                logger.severe("Claude CLI 执行失败: $errorMsg")
                emit(SDKMessage(
                    type = MessageType.ERROR,
                    data = MessageData(error = "Claude CLI 执行失败: $errorMsg")
                ))
            } else {
                emit(SDKMessage(
                    type = MessageType.END,
                    data = MessageData()
                ))
            }
            
        } catch (e: Exception) {
            logger.severe("执行查询时发生错误: ${e.message}")
            emit(SDKMessage(
                type = MessageType.ERROR,
                data = MessageData(error = e.message)
            ))
            throw e
        } finally {
            process.destroy()
            currentProcess.set(null)
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 处理 Claude 消息并转换为 SDK 消息
     */
    private suspend fun processMessage(message: ClaudeMessage): SDKMessage? {
        return when (message) {
            is AssistantMessage -> {
                processAssistantMessage(message)
            }
            is UserMessage -> {
                processUserMessage(message)
            }
            is SystemMessage -> {
                // 系统消息可以包含会话ID
                SDKMessage(
                    type = MessageType.START,
                    data = MessageData(sessionId = message.sessionId)
                )
            }
            is ResultMessage -> {
                // 结果消息表示会话结束
                SDKMessage(
                    type = MessageType.END,
                    data = MessageData(text = message.result)
                )
            }
            is SummaryMessage -> {
                // 摘要消息通常不需要转发给UI
                null
            }
        }
    }
    
    /**
     * 处理助手消息
     */
    private suspend fun processAssistantMessage(message: AssistantMessage): SDKMessage? {
        val content = message.message?.content ?: return null
        
        // 遍历内容块，逐个发送
        for (block in content) {
            when (block) {
                is TextBlock -> {
                    if (block.text.isNotEmpty()) {
                        return SDKMessage(
                            type = MessageType.TEXT,
                            data = MessageData(text = block.text)
                        )
                    }
                }
                is ToolUseBlock -> {
                    return SDKMessage(
                        type = MessageType.TOOL_USE,
                        data = MessageData(
                            toolName = block.name,
                            toolCallId = block.id,
                            toolInput = block.input
                        )
                    )
                }
                else -> {
                    // 忽略其他类型
                }
            }
        }
        
        return null
    }
    
    /**
     * 处理用户消息（主要是工具结果）
     */
    private suspend fun processUserMessage(message: UserMessage): SDKMessage? {
        val messageData = message.message ?: return null
        
        when (val content = messageData.content) {
            is ContentOrList.ListContent -> {
                // 遍历内容块
                for (block in content.value) {
                    if (block is ToolResultBlock) {
                        val resultContent = when (block.content) {
                            is ContentOrString.StringValue -> block.content.value
                            is ContentOrString.JsonValue -> block.content.value.toString()
                            null -> null
                        }
                        
                        return SDKMessage(
                            type = MessageType.TOOL_RESULT,
                            data = MessageData(
                                toolCallId = block.toolUseId,
                                toolResult = resultContent,
                                error = if (block.isError == true) resultContent else null
                            )
                        )
                    }
                }
            }
            else -> {
                // 忽略简单文本内容
            }
        }
        
        return null
    }
    
    /**
     * 终止当前响应
     */
    fun terminate() {
        currentProcess.get()?.let { process ->
            logger.info("正在终止进程...")
            try {
                process.destroy()
                logger.info("进程已终止")
            } catch (e: Exception) {
                logger.severe("终止进程失败: ${e.message}")
                try {
                    process.destroyForcibly()
                    logger.info("进程已强制终止")
                } catch (e2: Exception) {
                    logger.severe("强制终止进程失败: ${e2.message}")
                }
            }
        }
    }
    
    /**
     * 检查 Claude CLI 是否可用
     */
    suspend fun isClaudeCliAvailable(customCommand: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val claudeCommand = findClaudeCommand(customCommand)
            val process = ProcessBuilder(claudeCommand, "--version").start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                val output = process.inputStream.bufferedReader().readText()
                logger.info("Claude CLI 版本: $output")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.warning("Claude CLI 不可用: ${e.message}")
            false
        }
    }
}