package com.asakii.claude.agent.sdk.transport

import cn.hutool.cache.CacheUtil
import cn.hutool.cache.impl.TimedCache
import cn.hutool.crypto.digest.DigestUtil
import com.asakii.claude.agent.sdk.exceptions.*
import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import com.asakii.claude.agent.sdk.types.McpHttpServerConfig
import com.asakii.claude.agent.sdk.types.McpServerConfig
import com.asakii.claude.agent.sdk.types.McpSSEServerConfig
import com.asakii.claude.agent.sdk.types.McpStdioServerConfig
import com.asakii.claude.agent.sdk.types.PermissionMode
import com.asakii.claude.agent.sdk.types.SystemPromptPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import com.asakii.logging.*

/**
 * Transport implementation using subprocess for Claude CLI communication.
 */
class SubprocessTransport(
    private val options: ClaudeAgentOptions,
    private val streamingMode: Boolean = true
) : Transport {
    
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var errorReader: BufferedReader? = null
    private var isConnectedFlag = false

    // 临时文件跟踪，用于存储 agents JSON、system prompts 等（参考 Python SDK）
    private val tempFiles = mutableListOf<Path>()

    companion object {
        // Windows 命令行长度限制（参考值，参考 Python SDK）
        // 注意：当前 agents 和 mcp-config 等参数总是使用文件方式，避免转义问题
        @Suppress("unused")
        private const val CMD_LENGTH_LIMIT = 8000

        // 系统提示词临时文件缓存（TTL = 1 小时）
        // key = 内容摘要 (MD5), value = 临时文件路径
        private val systemPromptFileCache: TimedCache<String, Path> = CacheUtil.newTimedCache(60 * 60 * 1000L)

        init {
            // 启动定时清理过期缓存
            systemPromptFileCache.schedulePrune(60 * 1000L) // 每分钟清理一次
        }
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val logger = getLogger("SubprocessTransport")

    /**
     * 检测当前操作系统是否为 Windows
     */
    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }

    /**
     * 根据平台为参数添加引号（Windows 需要，Unix 不需要）
     * @param arg 原始参数字符串
     * @param isWindows 是否为 Windows 平台
     * @return 处理后的参数字符串
     */
    private fun wrapArgForPlatform(arg: String, isWindows: Boolean): String {
        return if (isWindows) {
            "\"$arg\""
        } else {
            arg
        }
    }

    /**
     * 根据平台处理 JSON 参数（Windows 需要转义，Unix 直接传递）
     * @param json JSON 字符串
     * @param isWindows 是否为 Windows 平台
     * @return 处理后的参数字符串
     */
    private fun wrapJsonForPlatform(json: String, isWindows: Boolean): String {
        return if (isWindows) {
            // Windows: 先转义反斜杠，再转义引号，最后用引号包裹
            "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } else {
            // Unix: 直接传递 JSON 字符串
            json
        }
    }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        // 在 try 块外构建命令，以便在异常信息中使用
        val command = buildCommand()
        val commandString = command.joinToString(" ")
        logger.info { "🔧 构建的命令: $commandString" }
        // 同时输出到 stdout，确保在 IDEA 插件环境中也能看到（会被记录到 idea.log 的 STDOUT）
        println("🔧 [SubprocessTransport] CLI启动命令: $commandString")
        
        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(options.cwd?.toFile() ?: java.io.File(System.getProperty("user.dir")))

            // 设置环境变量
            val env = processBuilder.environment()
            options.env.forEach { (key, value) ->
                env[key] = value
            }

            logger.info { "⚡ 启动Claude CLI进程..." }

            // 通过 shell 执行命令，自动加载用户环境变量
            process = processBuilder.start()

            logger.info { "✅ Claude CLI进程启动成功, PID: ${process?.pid()}" }

            // 检查进程是否立即退出
            delay(100) // 短暂等待
            if (!process!!.isAlive) {
                val exitCode = process!!.exitValue()
                val stderrContent = try {
                    BufferedReader(InputStreamReader(process!!.errorStream)).readText()
                } catch (e: Exception) {
                    "无法读取stderr: ${e.message}"
                }
                logger.error { "❌ Claude CLI进程立即退出，退出代码: $exitCode" }
                logger.error { "❌ stderr内容: $stderrContent" }
                logger.error { "❌ 启动命令: $commandString" }
                throw CLIConnectionException("Claude CLI process exited immediately with code $exitCode. Command: $commandString. stderr: $stderrContent")
            }

            // Setup I/O streams - 显式指定 UTF-8 编码，避免 Windows 默认编码问题
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream, Charsets.UTF_8))
            reader = BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8))
            errorReader = BufferedReader(InputStreamReader(process!!.errorStream, Charsets.UTF_8))
            logger.info { "📡 I/O流设置完成（包含stderr）" }

            isConnectedFlag = true
            logger.info { "🎉 SubprocessTransport连接成功!" }
        } catch (e: java.io.IOException) {
            logger.error { "❌ Claude CLI进程启动失败: ${e.message}" }
            logger.error { "❌ 启动命令: $commandString" }
            // Check if it's a file not found error (CLI not installed)
            if (e.message?.contains("No such file") == true ||
                e.message?.contains("not found") == true) {
                throw CLINotFoundException.withInstallInstructions()
            }
            throw CLIConnectionException("Failed to start Claude CLI process. Command: $commandString", e)
        } catch (e: Exception) {
            logger.error { "❌ Claude CLI进程启动失败: ${e.message}" }
            logger.error { "❌ 启动命令: $commandString" }
            throw CLIConnectionException("Failed to start Claude CLI process. Command: $commandString", e)
        }
    }
    
    override suspend fun write(data: String) = withContext(Dispatchers.IO) {
        try {
            writer?.let { w ->
                logger.info { "📤 向CLI写入数据: $data" }
                w.write(data)
                w.newLine()
                w.flush()
                logger.info { "✅ 数据写入CLI成功" }
            } ?: throw TransportException("Transport not connected")
        } catch (e: Exception) {
            logger.error { "❌ 向CLI写入数据失败: ${e.message}" }
            throw TransportException("Failed to write to CLI stdin", e)
        }
    }
    
    override fun readMessages(): Flow<JsonElement> = flow {
        val jsonBuffer = StringBuilder()
        var braceCount = 0
        var inString = false
        var escapeNext = false
        
        try {
            var currentLine: String? = null
            while (isConnected() && reader?.readLine().also { currentLine = it } != null) {
                currentLine?.let { line ->
                    logger.info { "📥 从 CLI 读取到原始行: $line" }
                    jsonBuffer.append(line)

                    // Parse JSON character by character to detect complete objects
                    for (char in line) {
                        when {
                            escapeNext -> escapeNext = false
                            char == '\\' && inString -> escapeNext = true
                            char == '"' && !escapeNext -> inString = !inString
                            !inString && char == '{' -> braceCount++
                            !inString && char == '}' -> braceCount--
                        }
                    }

                    // If we have a complete JSON object
                    if (braceCount == 0 && jsonBuffer.isNotEmpty()) {
                        try {
                            val jsonElement = json.parseToJsonElement(jsonBuffer.toString())
                            logger.info { "📨 从CLI读取到完整JSON: ${jsonBuffer.toString()}" }
                            emit(jsonElement)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // 协程被取消（正常的断开连接），直接重新抛出
                            logger.info { "ℹ️ 消息处理被取消（连接断开）" }
                            throw e
                        } catch (e: Exception) {
                            logger.warn { "⚠️ JSON解析失败: ${jsonBuffer.toString()}, error: ${e.message}" }
                            throw JSONDecodeException(
                                "Failed to decode JSON from CLI output",
                                originalLine = jsonBuffer.toString(),
                                cause = e
                            )
                        }
                        jsonBuffer.clear()
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程被取消，正常断开连接，不报错
            logger.info { "ℹ️ Transport 读取被取消（连接断开）" }
            throw e
        } catch (e: Exception) {
            if (isConnected()) {
                throw TransportException("Failed to read from CLI stdout", e)
            }
        } finally {
            // Check process completion and handle errors (like Python SDK)
            process?.let { p ->
                try {
                    if (!p.isAlive) {
                        val exitCode = p.exitValue()
                        if (exitCode != 0) {
                            // 读取stderr内容
                            val stderrContent = try {
                                errorReader?.readText() ?: "No stderr content available"
                            } catch (e: Exception) {
                                "Failed to read stderr: ${e.message}"
                            }
                            logger.error { "❌ Claude CLI进程失败，退出代码: $exitCode, stderr: $stderrContent" }
                            throw ProcessException(
                                "Command failed with exit code $exitCode",
                                exitCode = exitCode,
                                stderr = stderrContent
                            )
                        }
                    }
                } catch (e: IllegalThreadStateException) {
                    // Process is still running, this is normal
                }
            }
        }
    }.flowOn(Dispatchers.IO)
    
    override fun isReady(): Boolean = isConnectedFlag && process?.isAlive == true
    
    override suspend fun endInput(): Unit = withContext(Dispatchers.IO) {
        try {
            writer?.close()
        } catch (e: Exception) {
            throw TransportException("Failed to close CLI stdin", e)
        }
    }
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            writer?.close()
            reader?.close()
            errorReader?.close()

            process?.let { p ->
                // Give the process a chance to terminate gracefully
                if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                }
            }

            // 清理临时文件（agents JSON 等）
            tempFiles.forEach { tempFile ->
                try {
                    Files.deleteIfExists(tempFile)
                    logger.info { "🗑️ 清理临时文件: $tempFile" }
                } catch (e: Exception) {
                    logger.warn { "⚠️ 清理临时文件失败: $tempFile - ${e.message}" }
                }
            }
            tempFiles.clear()

            isConnectedFlag = false
        } catch (e: Exception) {
            throw TransportException("Failed to close transport", e)
        }
    }
    
    override fun isConnected(): Boolean = isConnectedFlag && process?.isAlive == true

    /**
     * Build the Claude CLI command with appropriate arguments.
     */
    private fun buildCommand(): List<String> {
        val command = mutableListOf<String>()

        // 提前检测平台，避免重复调用
        val isWindows = isWindows()

        // Base command - try to find claude executable (may return [node, cli.js] or [claude])
        command.addAll(findClaudeExecutable())
        
        // Verbose output - 必须在 --print 之前设置
        // 注意：当使用 --output-format=stream-json 时，必须同时使用 --verbose
        // Claude CLI 要求：--output-format=stream-json 总是需要 --verbose
        val outputFormat = options.extraArgs["output-format"] ?: "stream-json"
        val needsVerbose = options.verbose || outputFormat == "stream-json"
        if (needsVerbose) {
            command.add("--verbose")
        }

        // Output format (从 extraArgs 或默认使用 stream-json)
        command.addAll(listOf("--output-format", outputFormat))

        // Print flag - 非交互式模式必须添加 --print
        // 注意：Claude CLI 默认启动交互式 TUI，在非 TTY 环境会报 "Raw mode is not supported" 错误
        // 使用 stream-json 模式时必须强制添加 --print，否则 CLI 无法在后台进程中运行
        if (options.print || outputFormat == "stream-json" || streamingMode) {
            command.add("--print")
        }

        // Include partial messages for real-time token usage information (根据选项决定)
        if (options.includePartialMessages) {
            command.add("--include-partial-messages")
        }

        // Input format for streaming mode
        if (streamingMode) {
            command.addAll(listOf("--input-format", "stream-json"))
        } else {
            command.add("--")
        }
        
        // Note: Permission handling is done through the stream-json protocol
        // No special command line flags needed for permission callbacks
        
        // Model selection
        options.model?.let { model ->
            command.addAll(listOf("--model", model))
        }
        
        // System prompt (supports String or SystemPromptPreset)
        options.systemPrompt?.let { prompt ->
            when (prompt) {
                is String -> {
                    // 使用 --system-prompt-file 避免 Windows 命令行长度限制问题
                    // 参考: https://github.com/anthropics/claude-agent-sdk-python/issues/238
                    val tempFile = getOrCreateSystemPromptFile(prompt)
                    logger.info { "📝 将 system-prompt 写入临时文件: $tempFile" }
                    command.add("--system-prompt-file")
                    command.add(tempFile.toAbsolutePath().toString())
                }
                is SystemPromptPreset -> {
                    if (prompt.preset == "claude_code") {
                        // For claude_code preset, use default system prompt (don't pass --system-prompt)
                        // Only add append if provided
                        prompt.append?.let { appendText ->
                            // 使用 --append-system-prompt-file 避免 Windows 命令行参数问题
                            // 参考: https://github.com/anthropics/claude-code/issues/3411
                            // 多行文本在 Windows 上会破坏后续命令行参数的解析
                            val tempFile = getOrCreateSystemPromptFile(appendText)
                            logger.info { "📝 将 append-system-prompt 写入临时文件: $tempFile" }
                            command.add("--append-system-prompt-file")
                            command.add(tempFile.toAbsolutePath().toString())
                        }
                    } else {
                        // Unknown preset, use as system prompt
                        command.add("--system-prompt")
                        command.add(prompt.preset)
                    }
                }
                else -> {
                    // Unknown type, convert to string
                    command.add("--system-prompt")
                    command.add(prompt.toString())
                }
            }
        }

        // Append system prompt file（独立参数，用于 MCP 场景追加提示词）
        options.appendSystemPromptFile?.let { appendContent ->
            val tempFile = getOrCreateSystemPromptFile(appendContent)
            logger.info { "📝 将 appendSystemPromptFile 写入临时文件: $tempFile" }
            command.add("--append-system-prompt-file")
            command.add(wrapArgForPlatform(tempFile.toAbsolutePath().toString(), isWindows))
        }

        // Allowed tools（Windows 需要引号包裹，Unix 系统不需要）
        if (options.allowedTools.isNotEmpty()) {
            val toolsArg = options.allowedTools.joinToString(",")
            command.addAll(listOf("--allowed-tools", wrapArgForPlatform(toolsArg, isWindows)))
        }

        // Disallowed tools
        if (options.disallowedTools.isNotEmpty()) {
            val toolsArg = options.disallowedTools.joinToString(",")
            command.addAll(listOf("--disallowed-tools", wrapArgForPlatform(toolsArg, isWindows)))
        }

        // Agents (programmatic subagents)
        options.agents?.let { agents ->
            if (agents.isNotEmpty()) {
                val agentsJson = buildJsonObject {
                    agents.forEach { (name, agentDef) ->
                        putJsonObject(name) {
                            put("description", agentDef.description)
                            put("prompt", agentDef.prompt)
                            agentDef.tools?.let { tools ->
                                putJsonArray("tools") {
                                    tools.forEach { add(it) }
                                }
                            }
                            agentDef.model?.let { put("model", it) }
                        }
                    }
                }.toString()

                // 根据平台处理 JSON（Windows 需要转义，Unix 直接传递）
                command.addAll(listOf("--agents", wrapJsonForPlatform(agentsJson, isWindows)))
                logger.info { "🤖 配置自定义代理: ${agents.keys.joinToString(", ")}" }
            }
        }

        // Permission mode
        options.permissionMode?.let { mode ->
            val permissionModeValue = when (mode) {
                PermissionMode.DEFAULT -> "default"
                PermissionMode.ACCEPT_EDITS -> "acceptEdits"
                PermissionMode.PLAN -> "plan"
                PermissionMode.BYPASS_PERMISSIONS -> "bypassPermissions"
            }
            command.addAll(listOf("--permission-mode", permissionModeValue))
        }

        // Dangerously skip permissions
        if (options.dangerouslySkipPermissions == true) {
            command.add("--dangerously-skip-permissions")
        }

        // Allow dangerously skip permissions
        if (options.allowDangerouslySkipPermissions == true) {
            command.add("--allow-dangerously-skip-permissions")
        }

        // Permission prompt tool - 配置授权请求使用的方式
        // 当设置为 "stdio" 时，Claude CLI 会通过控制协议 (control_request/control_response) 发送权限请求
        // SDK 的 ControlProtocol.handlePermissionRequest() 会处理 subtype="can_use_tool" 并调用 canUseTool 回调
        logger.info { "🔍 [buildCommand] options.canUseTool=${options.canUseTool != null}, options.permissionPromptToolName=${options.permissionPromptToolName}" }
        // 如果提供了 canUseTool 回调，自动设置为 "stdio" 以启用控制协议权限请求
        val effectivePermissionPromptTool = options.permissionPromptToolName
            ?: if (options.canUseTool != null) "stdio" else null
        effectivePermissionPromptTool?.let { tool ->
            command.addAll(listOf("--permission-prompt-tool", tool))
            logger.info { "🔐 配置授权工具: $tool" }
        }

        // Continue conversation
        if (options.continueConversation) {
            command.add("--continue")
        }
        
        // Resume session
        options.resume?.let { sessionId ->
            command.addAll(listOf("--resume", sessionId))
        }

        // Replay user messages when resuming session
        if (options.replayUserMessages) {
            command.add("--replay-user-messages")
        }

        // Disable session persistence (sessions will not be saved to disk)
        if (options.noSessionPersistence) {
            command.add("--no-session-persistence")
        }

        // Max turns
        options.maxTurns?.let { turns ->
            command.addAll(listOf("--max-turns", turns.toString()))
        }
        
        // Additional directories
        options.addDirs.forEach { dir ->
            command.addAll(listOf("--add-dir", dir.toString()))
        }
        
        // Settings file
        options.settings?.let { settings ->
            command.addAll(listOf("--settings", settings))
        }

        // Extended thinking tokens (0 表示显式禁用思考)
        command.addAll(listOf("--max-thinking-tokens", options.maxThinkingTokens.coerceAtLeast(0).toString()))
        
        // MCP servers configuration - 参考 Python SDK 实现
        if (options.mcpServers.isNotEmpty()) {
            val serversForCli = mutableMapOf<String, JsonObject>()

            options.mcpServers.forEach { (name, config) ->
                val serverConfig = when (config) {
                    is McpStdioServerConfig -> buildJsonObject {
                        put("type", config.type)
                        put("command", config.command)
                        putJsonArray("args") { config.args.forEach { add(it) } }
                        putJsonObject("env") { config.env.forEach { (k, v) -> put(k, v) } }
                    }
                    is McpSSEServerConfig -> buildJsonObject {
                        put("type", config.type)
                        put("url", config.url)
                        putJsonObject("headers") { config.headers.forEach { (k, v) -> put(k, v) } }
                    }
                    is McpHttpServerConfig -> buildJsonObject {
                        put("type", config.type)
                        put("url", config.url)
                        putJsonObject("headers") { config.headers.forEach { (k, v) -> put(k, v) } }
                    }
                    is McpServer -> buildJsonObject {
                        put("type", "sdk")
                        put("name", name)
                    }
                    else -> {
                        logger.warn("Unsupported MCP server config type $name -> ${config::class.simpleName}")
                        null
                    }
                }

                if (serverConfig != null) {
                    serversForCli[name] = serverConfig
                    val typeLabel = serverConfig["type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                    logger.info { "Added MCP server config $name -> type=$typeLabel" }
                }
            }

            if (serversForCli.isNotEmpty()) {
                val mcpConfigJson = buildJsonObject {
                    putJsonObject("mcpServers") {
                        serversForCli.forEach { (serverName, serverConfig) ->
                            put(serverName, serverConfig)
                        }
                    }
                }.toString()

                // 创建临时文件存储 MCP 配置 JSON
                // 路径格式: 临时目录/claude-code-plus/claude_mcp_config_日期_uuid.json
                val tempDir = Path.of(System.getProperty("java.io.tmpdir"), "claude-code-plus")
                Files.createDirectories(tempDir)
                val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy_MM_dd_HH"))
                val uuid = java.util.UUID.randomUUID().toString().substring(0, 8)
                val tempFile = tempDir.resolve("claude_mcp_config_${timestamp}_${uuid}.json")
                Files.writeString(tempFile, mcpConfigJson)
                tempFiles.add(tempFile)

                // --mcp-config 参数接受文件路径（不需要 @ 前缀）
                command.addAll(listOf("--mcp-config", tempFile.toAbsolutePath().toString()))
                logger.info { "🔧 MCP 配置（使用文件）: $tempFile" }
                logger.debug { "🔧 MCP 配置内容: $mcpConfigJson" }
            }
        }
        
        // Chrome integration
        // --chrome: enable Chrome extension integration
        // --no-chrome: disable Chrome extension integration
        when (options.chromeEnabled) {
            true -> {
                command.add("--chrome")
                logger.info { "🌐 Chrome 集成已启用 (--chrome)" }
            }
            false -> {
                command.add("--no-chrome")
                logger.info { "🌐 Chrome 集成已禁用 (--no-chrome)" }
            }
            null -> {
                // null: use CLI default (respects user config)
                logger.debug { "🌐 Chrome 集成使用默认配置" }
            }
        }

        // Extra arguments (排除已经显式处理的参数，避免重复)
        // 已处理的参数：output-format (第 275 行), print (第 278-280 行)
        val processedKeys = setOf("output-format", "print")
        options.extraArgs.forEach { (key, value) ->
            if (key !in processedKeys) {
                command.add("--$key")
                value?.let { command.add(it) }
            }
        }
        
        // 处理 extraArgs 中的 print（如果存在且 options.print 为 false）
        // 注意：如果 extraArgs 中有 print，它会在最后被添加，但 --verbose 已经在前面添加了
        if (!options.print && options.extraArgs.containsKey("print")) {
            command.add("--print")
        }

        logger.info { "🔧 完整构建的Claude CLI命令: ${command.joinToString(" ")}" }

        return command
    }
    
    /**
     * Find the Claude executable in the system.
     * 优先级：
     * 1. 用户指定路径 (options.cliPath)
     * 2. 系统 claude 命令（通过 PATH 检测）
     */
    private fun findClaudeExecutable(): List<String> {
        val isWindows = isWindows()

        // 1. 用户指定路径（最高优先级）
        options.cliPath?.let { customPath ->
            val path = customPath.toString()
            logger.info { "✅ 使用用户指定的 CLI: $path" }
            // Windows 上 .cmd/.bat 文件需要通过 cmd /c 启动（ProcessBuilder 无法直接执行）
            return if (isWindows && (path.lowercase().endsWith(".cmd") || path.lowercase().endsWith(".bat"))) {
                listOf("cmd", "/c", path)
            } else {
                listOf(path)
            }
        }

        // 2. 系统 claude 命令
        // Windows: 使用 cmd /c claude，让 cmd.exe 通过 PATHEXT 自动解析 .cmd/.exe
        //   参考: https://github.com/anthropics/claude-agent-sdk-python/issues/252
        //   npm 安装的 claude 在 Windows 上有 claude（bash脚本）、claude.cmd、claude.ps1 三种形式
        //   ProcessBuilder 无法直接执行 bash 脚本和 .cmd，但 cmd /c 会通过 PATHEXT 正确解析
        // macOS/Linux: 先通过 login shell 检测路径，确保用户环境变量（PATH 等）被正确加载
        if (isWindows) {
            // 验证 claude 命令是否存在
            if (isClaudeAvailableOnWindows()) {
                logger.info { "✅ 使用系统 Claude CLI (via cmd /c claude)" }
                return listOf("cmd", "/c", "claude")
            }
        } else {
            val claudePath = detectClaudePathUnix()
            if (claudePath.isNotEmpty()) {
                logger.info { "✅ 使用系统 Claude CLI: $claudePath" }
                return listOf(claudePath)
            }
        }

        // 未找到 Claude CLI
        throw CLINotFoundException.withInstallInstructions()
    }

    /**
     * Windows: 验证 claude 命令是否可用
     * 使用 cmd /c where claude 检查，只需确认存在即可
     * 实际启动时使用 cmd /c claude，由 cmd.exe 通过 PATHEXT 自动解析正确的可执行文件
     */
    private fun isClaudeAvailableOnWindows(): Boolean {
        return try {
            val process = ProcessBuilder("cmd", "/c", "where", "claude")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readLine() // consume output
            process.waitFor() == 0
        } catch (e: Exception) {
            logger.debug { "⚠️ 检测 Claude CLI 可用性失败: ${e.message}" }
            false
        }
    }

    /**
     * macOS/Linux: 检测 Claude CLI 路径
     * 使用 login shell 执行，以正确加载用户的环境变量（PATH 等）
     * @return Claude CLI 可执行文件路径，未找到返回空字符串
     */
    private fun detectClaudePathUnix(): String {
        try {
            val defaultShell = System.getenv("SHELL") ?: "/bin/bash"
            val process = ProcessBuilder(defaultShell, "-l", "-c", "which claude")
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream.bufferedReader().readLine()?.trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && !result.isNullOrBlank()) {
                logger.info { "🔍 检测到 Claude CLI: $result" }
                return result
            }
        } catch (e: Exception) {
            logger.debug { "⚠️ 检测 Claude CLI 路径失败: ${e.message}" }
        }

        return ""
    }

    /**
     * 获取或创建系统提示词临时文件（带缓存）
     * 使用内容摘要作为缓存 key，避免重复创建相同内容的临时文件
     * 文件存放在 {tempDir}/claude-agent-sdk/system-prompts/ 子目录下，方便查找和管理
     */
    private fun getOrCreateSystemPromptFile(content: String): Path {
        // 计算内容摘要作为 key
        val digest = DigestUtil.md5Hex(content)

        // 尝试从缓存获取
        val cachedPath = systemPromptFileCache.get(digest)
        if (cachedPath != null && Files.exists(cachedPath)) {
            logger.info { "📦 使用缓存的系统提示词文件: $cachedPath (digest: $digest)" }
            return cachedPath
        }

        // 缓存未命中或文件已删除，创建新文件
        // 使用子目录存放，方便查找：{tempDir}/claude-agent-sdk/system-prompts/
        val tempDir = Path.of(System.getProperty("java.io.tmpdir"))
        val promptDir = tempDir.resolve("claude-agent-sdk").resolve("system-prompts")

        // 确保子目录存在
        if (!Files.exists(promptDir)) {
            Files.createDirectories(promptDir)
            logger.info { "📁 创建系统提示词目录: $promptDir" }
        }

        val tempFile = promptDir.resolve("prompt-$digest.md")

        // 写入内容
        Files.writeString(tempFile, content)
        tempFile.toFile().deleteOnExit()

        // 存入缓存
        systemPromptFileCache.put(digest, tempFile)
        logger.info { "📝 创建新的系统提示词文件: $tempFile (digest: $digest)" }

        return tempFile
    }
}
