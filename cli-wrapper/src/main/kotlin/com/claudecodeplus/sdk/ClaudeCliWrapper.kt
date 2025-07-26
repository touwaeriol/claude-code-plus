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
        
        /**
         * 查找 Node.js 和 Claude CLI.js 的路径
         */
        private fun findNodeAndClaudePaths(): Pair<String, String> {
            // 1. 查找 Node.js
            val nodeCommand = findNodeCommand()
            
            // 2. 查找 Claude Code CLI.js
            val claudeCliPath = findClaudeCliJs()
            
            return Pair(nodeCommand, claudeCliPath)
        }
        
        /**
         * 查找 Node.js 命令
         */
        private fun findNodeCommand(): String {
            // 检查环境变量
            System.getenv("NODE_PATH")?.let { 
                if (java.io.File(it).exists()) {
                    return it
                }
            }
            
            val osName = System.getProperty("os.name").lowercase()
            
            // Windows 特定路径
            if (osName.contains("windows")) {
                val windowsPaths = listOf(
                    "node.exe",
                    "C:\\Program Files\\nodejs\\node.exe",
                    "C:\\Program Files (x86)\\nodejs\\node.exe",
                    "${System.getProperty("user.home")}\\AppData\\Local\\Programs\\nodejs\\node.exe"
                )
                
                for (path in windowsPaths) {
                    if (java.io.File(path).exists()) {
                        logger.info("Found node at: $path")
                        return path
                    }
                }
                
                // 尝试 where 命令
                try {
                    val process = ProcessBuilder("where", "node").start()
                    val output = process.inputStream.bufferedReader().readText().trim()
                    if (process.waitFor() == 0 && output.isNotEmpty()) {
                        val nodePath = output.lines().first()
                        logger.info("Found node via where: $nodePath")
                        return nodePath
                    }
                } catch (e: Exception) {
                    logger.fine("Failed to find node via where: ${e.message}")
                }
            } else {
                // Unix/Mac 路径
                val unixPaths = listOf(
                    "/usr/local/bin/node",
                    "/usr/bin/node",
                    "/opt/homebrew/bin/node"
                )
                
                for (path in unixPaths) {
                    if (java.io.File(path).exists()) {
                        logger.info("Found node at: $path")
                        return path
                    }
                }
                
                // 尝试 which 命令
                try {
                    val process = ProcessBuilder("which", "node").start()
                    val output = process.inputStream.bufferedReader().readText().trim()
                    if (process.waitFor() == 0 && output.isNotEmpty()) {
                        logger.info("Found node via which: $output")
                        return output
                    }
                } catch (e: Exception) {
                    logger.fine("Failed to find node via which: ${e.message}")
                }
            }
            
            // 默认值
            return "node"
        }
        
        /**
         * 查找 Claude CLI.js 的路径
         */
        private fun findClaudeCliJs(): String {
            val osName = System.getProperty("os.name").lowercase()
            
            if (osName.contains("windows")) {
                // Windows npm 全局安装路径
                val windowsPaths = listOf(
                    "${System.getProperty("user.home")}\\AppData\\Roaming\\npm\\node_modules\\@anthropic-ai\\claude-code\\cli.js",
                    "C:\\Users\\${System.getProperty("user.name")}\\AppData\\Roaming\\npm\\node_modules\\@anthropic-ai\\claude-code\\cli.js"
                )
                
                for (path in windowsPaths) {
                    if (java.io.File(path).exists()) {
                        logger.info("Found claude cli.js at: $path")
                        return path
                    }
                }
            } else {
                // Unix/Mac 路径
                val unixPaths = listOf(
                    "/usr/local/lib/node_modules/@anthropic-ai/claude-code/cli.js",
                    "/opt/homebrew/lib/node_modules/@anthropic-ai/claude-code/cli.js",
                    "${System.getProperty("user.home")}/.npm-global/lib/node_modules/@anthropic-ai/claude-code/cli.js"
                )
                
                for (path in unixPaths) {
                    if (java.io.File(path).exists()) {
                        logger.info("Found claude cli.js at: $path")
                        return path
                    }
                }
            }
            
            throw IllegalStateException("Could not find Claude Code cli.js. Please ensure @anthropic-ai/claude-code is installed globally via npm")
        }
    }
    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
    
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
     * 支持 Claude CLI 的所有参数，默认配置适合开发环境使用
     */
    data class QueryOptions(
        // 模型配置
        /** 模型名称，如 'sonnet', 'opus' 或完整模型名 */
        val model: String? = null,
        
        /** 自动回退模型，当主模型过载时使用 */
        val fallbackModel: String? = null,
        
        // 对话控制
        /** 最大对话轮数 */
        val maxTurns: Int? = null,
        
        /** 自定义系统提示词（完全替换默认提示词） */
        val customSystemPrompt: String? = null,
        
        /** 追加系统提示词（在默认提示词后添加） */
        val appendSystemPrompt: String? = null,
        
        // 会话管理
        /** 是否继续最近的对话 */
        val continueConversation: Boolean = false,
        
        /** 恢复指定会话ID的对话 */
        val resume: String? = null,
        
        // 权限控制
        /** 是否跳过所有权限检查（默认true，适合开发环境。设置为false可以启用权限检查） */
        val dangerouslySkipPermissions: Boolean = true,
        
        /** 权限模式 */
        val permissionMode: PermissionMode = PermissionMode.DEFAULT,
        
        /** 权限提示工具名称 */
        val permissionPromptToolName: String? = null,
        
        // 工具控制
        /** 允许使用的工具列表，如 ["Bash(git:*)", "Edit"] */
        val allowedTools: List<String> = emptyList(),
        
        /** 禁止使用的工具列表 */
        val disallowedTools: List<String> = emptyList(),
        
        // MCP 服务器配置
        /** MCP 服务器配置映射 */
        val mcpServers: Map<String, Any>? = null,
        
        // 执行环境
        /** 工作目录 */
        val cwd: String? = null,
        
        /** 额外允许工具访问的目录列表 */
        val addDirs: List<String> = emptyList(),
        
        // 输出控制
        /** 是否启用调试模式 */
        val debug: Boolean = false,
        
        /** 是否覆盖配置文件中的verbose设置 */
        val verbose: Boolean? = null,
        
        // IDE 集成
        /** 是否自动连接到IDE */
        val autoConnectIde: Boolean = false
    )
    
    /**
     * 执行 Claude 查询
     * @param prompt 用户提示
     * @param options 查询选项
     * @return 响应消息流
     */
    fun query(prompt: String, options: QueryOptions = QueryOptions()): Flow<SDKMessage> = flow {
        val requestId = java.util.UUID.randomUUID().toString().take(8)
        logger.info("🔵 [$requestId] 开始处理 Claude 查询请求")
        logger.info("🔵 [$requestId] 提示词: ${prompt.take(100)}${if(prompt.length > 100) "..." else ""}")
        logger.info("🔵 [$requestId] 选项: $options")
        logger.info("🔵 [$requestId] 会话ID: ${options.resume ?: "null (新会话)"}")
        
        val args = mutableListOf<String>()
        
        // 核心参数（必须在前面）
        args.add("--print")  // 必须使用 --print 才能获得非交互式输出
        args.addAll(listOf("--output-format", "stream-json"))
        args.addAll(listOf("--input-format", "text"))  // 使用文本输入格式（默认）
        logger.info("🔵 [$requestId] 添加核心参数: --print --output-format stream-json --input-format text")
        
        // 调试和verbose控制
        if (options.debug) {
            args.add("--debug")
        }
        
        // 总是启用 verbose 以获取 session_id
        args.add("--verbose")
        
        // 模型配置
        options.model?.let { args.addAll(listOf("--model", it)) }
        options.fallbackModel?.let {
            if (options.model == it) {
                throw IllegalArgumentException("Fallback model cannot be the same as the main model")
            }
            args.addAll(listOf("--fallback-model", it))
        }
        
        // 系统提示词配置
        options.customSystemPrompt?.let { args.addAll(listOf("--system-prompt", it)) }
        options.appendSystemPrompt?.let { args.addAll(listOf("--append-system-prompt", it)) }
        
        // 对话控制
        options.maxTurns?.let { args.addAll(listOf("--max-turns", it.toString())) }
        
        // 会话管理 - 只在有明确的会话ID时使用 --resume
        if (options.resume != null && options.resume.isNotBlank()) {
            args.addAll(listOf("--resume", options.resume))
            logger.info("🔵 [$requestId] 恢复会话: ${options.resume}")
        }
        // 不使用 --continue 参数
        
        // 权限控制 - 两个独立的参数
        if (options.dangerouslySkipPermissions) {
            args.add("--dangerously-skip-permissions")
        }
        
        if (options.permissionMode != PermissionMode.DEFAULT) {
            args.addAll(listOf("--permission-mode", options.permissionMode.cliValue))
        }
        
        options.permissionPromptToolName?.let {
            args.addAll(listOf("--permission-prompt-tool", it))
        }
        
        // 工具控制
        if (options.allowedTools.isNotEmpty()) {
            args.addAll(listOf("--allowedTools", options.allowedTools.joinToString(",")))
        }
        
        if (options.disallowedTools.isNotEmpty()) {
            args.addAll(listOf("--disallowedTools", options.disallowedTools.joinToString(",")))
        }
        
        // MCP 服务器配置
        options.mcpServers?.let {
            args.addAll(listOf("--mcp-config", objectMapper.writeValueAsString(mapOf("mcpServers" to it))))
        }
        
        // 目录权限
        if (options.addDirs.isNotEmpty()) {
            args.addAll(listOf("--add-dir", *options.addDirs.toTypedArray()))
        }
        
        // IDE 集成
        if (options.autoConnectIde) {
            args.add("--ide")
        }
        
        // 提示词（必须在最后）
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Prompt is required")
        }
        args.add(prompt.trim())
        
        // 构建进程 - 使用 Node.js 运行 Claude CLI.js
        val (nodeCommand, claudeCliPath) = findNodeAndClaudePaths()
        logger.info("🔵 [$requestId] 使用 Node.js: $nodeCommand")
        logger.info("🔵 [$requestId] Claude CLI.js 路径: $claudeCliPath")
        
        // 构建命令：node cli.js [args]
        val fullArgs = mutableListOf<String>()
        fullArgs.add(claudeCliPath)
        fullArgs.addAll(args)
        
        logger.info("🔵 [$requestId] 完整命令行: $nodeCommand ${fullArgs.joinToString(" ")}")
        
        val processBuilder = ProcessBuilder(nodeCommand, *fullArgs.toTypedArray())
        options.cwd?.let { 
            processBuilder.directory(java.io.File(it))
            logger.info("🔵 [$requestId] 工作目录: $it")
        }
        
        // 设置环境变量
        val env = processBuilder.environment()
        
        // 标识调用来源
        env["CLAUDE_CODE_ENTRYPOINT"] = "sdk-kotlin"
        
        // 设置编码相关的环境变量，确保子进程使用 UTF-8
        env["LANG"] = "en_US.UTF-8"
        env["LC_ALL"] = "en_US.UTF-8"
        env["PYTHONIOENCODING"] = "utf-8"  // 如果 Node.js 调用了 Python
        
        // Windows 特定的编码设置
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            env["CHCP"] = "65001"  // UTF-8 代码页
        }
        
        // 确保 PATH 包含必要的目录
        val currentPath = env["PATH"] ?: System.getenv("PATH") ?: ""
        val additionalPaths = listOf(
            "/Users/${System.getProperty("user.name")}/.local/bin",
            "/usr/local/bin",
            "/opt/homebrew/bin"
        ).joinToString(":")
        env["PATH"] = "$additionalPaths:$currentPath"
        
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
                    // 根据错误类型使用不同的日志级别
                    when {
                        line.contains("[DEP0190]") -> {
                            // Node.js 警告，使用 FINE 级别
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
            // 设置协程取消监听器，自动终止进程
            val cancelHandler = { 
                logger.info("Coroutine cancelled, terminating process...")
                terminate()
            }
            
            reader.useLines { lines ->
                lines.forEach { line ->
                    // 检查协程是否被取消，如果取消则自动终止进程
                    try {
                        coroutineContext.ensureActive()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        cancelHandler()
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
                                
                                // 解析 Claude CLI 的 JSON 输出
                                val jsonNode = objectMapper.readTree(cleanLine)
                                val type = jsonNode.get("type")?.asText()
                                
                                when (type) {
                                    "assistant" -> {
                                        // 提取助手消息
                                        logger.fine("Processing assistant message: $line")
                                        val content = jsonNode.get("message")?.get("content")
                                        if (content != null) {
                                            if (content.isArray) {
                                                content.forEach { item ->
                                                    val itemType = item.get("type")?.asText()
                                                    when (itemType) {
                                                        "text" -> {
                                                            val text = item.get("text")?.asText()
                                                            if (!text.isNullOrEmpty()) {
                                                                logger.fine("Emitting text message: ${text.take(50)}...")
                                                                emit(SDKMessage(
                                                                    type = MessageType.TEXT,
                                                                    data = MessageData(text = text)
                                                                ))
                                                            }
                                                        }
                                                        "tool_use" -> {
                                                            // 处理工具调用
                                                            val toolName = item.get("name")?.asText()
                                                            val toolInput = item.get("input")
                                                            val toolCallId = item.get("id")?.asText()
                                                            logger.fine("Emitting tool use: $toolName with id: $toolCallId")
                                                            emit(SDKMessage(
                                                                type = MessageType.TOOL_USE,
                                                                data = MessageData(
                                                                    toolName = toolName,
                                                                    toolCallId = toolCallId,
                                                                    toolInput = if (toolInput != null) {
                                                                        // 将JsonNode转换为Map或String
                                                                        if (toolInput.isObject) {
                                                                            objectMapper.convertValue(toolInput, Map::class.java)
                                                                        } else {
                                                                            toolInput.asText()
                                                                        }
                                                                    } else null
                                                                )
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
                                    "user" -> {
                                        // 处理用户消息（主要是工具结果）
                                        logger.fine("Processing user message: $line")
                                        val content = jsonNode.get("message")?.get("content")
                                        if (content != null && content.isArray) {
                                            content.forEach { item ->
                                                val itemType = item.get("type")?.asText()
                                                if (itemType == "tool_result") {
                                                    val toolUseId = item.get("tool_use_id")?.asText()
                                                    val resultContent = item.get("content")?.asText()
                                                    val error = item.get("error")?.asText()
                                                    logger.fine("Emitting tool result for id: $toolUseId")
                                                    emit(SDKMessage(
                                                        type = MessageType.TOOL_RESULT,
                                                        data = MessageData(
                                                            toolCallId = toolUseId,
                                                            toolResult = resultContent,
                                                            error = error
                                                        )
                                                    ))
                                                }
                                            }
                                        }
                                    }
                                    "tool_result" -> {
                                        // 处理工具执行结果
                                        logger.fine("Processing tool result: $line")
                                        val toolResult = jsonNode.get("content")
                                        val error = jsonNode.get("error")?.asText()
                                        emit(SDKMessage(
                                            type = MessageType.TOOL_RESULT,
                                            data = MessageData(
                                                toolResult = toolResult?.let {
                                                    if (it.isTextual) it.asText() else objectMapper.writeValueAsString(it)
                                                },
                                                error = error
                                            )
                                        ))
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
                                            logger.info("🔵 [$requestId] 新会话已创建，ID: $sessionId")
                                            emit(SDKMessage(
                                                type = MessageType.START,
                                                data = MessageData(sessionId = sessionId)
                                            ))
                                        } else {
                                            logger.fine("System message without session_id: $jsonNode")
                                        }
                                    }
                                    "result" -> {
                                        // 结果消息，表示对话结束
                                        logger.fine("Result message: $line")
                                        emit(SDKMessage(
                                            type = MessageType.END,
                                            data = MessageData()
                                        ))
                                    }
                                    "tool_use" -> {
                                        // 工具使用消息
                                        val toolName = jsonNode.get("tool_name")?.asText()
                                        val toolInput = jsonNode.get("tool_input")
                                        val toolCallId = jsonNode.get("id")?.asText()
                                        emit(SDKMessage(
                                            type = MessageType.TOOL_USE,
                                            data = MessageData(
                                                toolName = toolName,
                                                toolCallId = toolCallId,
                                                toolInput = toolInput
                                            )
                                        ))
                                    }
                                    "tool_result" -> {
                                        // 工具结果消息
                                        val toolName = jsonNode.get("tool_name")?.asText()
                                        val toolResult = jsonNode.get("tool_result")
                                        emit(SDKMessage(
                                            type = MessageType.TOOL_RESULT,
                                            data = MessageData(
                                                toolName = toolName,
                                                toolResult = toolResult
                                            )
                                        ))
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
                logger.severe("🔴 [$requestId] Claude CLI 进程异常退出")
                logger.severe("🔴 [$requestId] 退出码: $exitCode")
                logger.severe("🔴 [$requestId] 错误输出: $errorMessage")
                logger.severe("🔴 [$requestId] 命令行参数: ${args.joinToString(" ")}")
                
                // 针对特定错误提供更友好的错误信息
                val friendlyError = when {
                    errorMessage.contains("[DEP0190]") -> {
                        "Node.js 安全警告: $errorMessage\n这是 Claude CLI 的警告，不影响正常使用。"
                    }
                    errorMessage.contains("ENOENT") || errorMessage.contains("not found") -> {
                        "找不到 Claude CLI。请确保已安装 @anthropic-ai/claude-code: npm install -g @anthropic-ai/claude-code"
                    }
                    errorMessage.contains("CLAUDE_API_KEY") -> {
                        "未设置 CLAUDE_API_KEY 环境变量。请设置您的 Claude API 密钥。"
                    }
                    else -> errorMessage
                }
                
                throw RuntimeException("Claude process exited with code $exitCode. Error: $friendlyError")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程被取消，确保进程被终止
            logger.info("Query cancelled, terminating process...")
            terminate()
            throw e
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
            
            // 给进程一点时间来清理，但缩短等待时间以提高响应速度
            try {
                if (!process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    // 如果500毫秒后还没结束，强制终止
                    logger.warning("Process did not terminate gracefully, forcing termination")
                    process.destroyForcibly()
                    // 再等待一点时间确保强制终止完成
                    process.waitFor(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                }
            } catch (e: InterruptedException) {
                // 如果等待被中断，立即强制终止
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
    
    /**
     * 发送消息并获取流式响应
     * @param message 用户消息
     * @param sessionId 会话ID（可选，用于 --resume）
     * @return 响应流
     */
    fun sendMessage(
        message: String, 
        sessionId: String? = null,
        cwd: String? = null
    ): Flow<StreamResponse> = flow {
        val options = if (sessionId != null) {
            QueryOptions(resume = sessionId, cwd = cwd)
        } else {
            QueryOptions(cwd = cwd)
        }
        
        query(message, options).collect { sdkMessage ->
            when (sdkMessage.type) {
                MessageType.START -> {
                    sdkMessage.data.sessionId?.let {
                        emit(StreamResponse.SessionStart(it))
                    }
                }
                MessageType.TEXT -> {
                    sdkMessage.data.text?.let {
                        emit(StreamResponse.Content(it))
                    }
                }
                MessageType.ERROR -> {
                    emit(StreamResponse.Error(sdkMessage.data.error ?: "Unknown error"))
                }
                MessageType.END -> {
                    emit(StreamResponse.Complete)
                }
                MessageType.TOOL_USE -> {
                    emit(StreamResponse.ToolUse(
                        toolName = sdkMessage.data.toolName ?: "unknown",
                        toolInput = sdkMessage.data.toolInput
                    ))
                }
                MessageType.TOOL_RESULT -> {
                    emit(StreamResponse.ToolResult(
                        toolName = sdkMessage.data.toolName ?: "unknown", 
                        result = sdkMessage.data.toolResult
                    ))
                }
                else -> {
                    // 忽略其他类型
                }
            }
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 流式响应类型
     */
    sealed class StreamResponse {
        data class Content(val content: String) : StreamResponse()
        data class Error(val error: String) : StreamResponse()
        data class SessionStart(val sessionId: String) : StreamResponse()
        data class ToolUse(val toolName: String, val toolInput: Any?) : StreamResponse()
        data class ToolResult(val toolName: String, val result: Any?) : StreamResponse()
        object Complete : StreamResponse()
    }
    
    /**
     * Claude 消息
     */
    data class ClaudeMessage(
        val role: String,
        val content: String
    )
}