package com.asakii.claude.agent.sdk.transport

import cn.hutool.cache.CacheUtil
import cn.hutool.cache.impl.TimedCache
import cn.hutool.crypto.digest.DigestUtil
import com.asakii.claude.agent.sdk.exceptions.*
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
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
import java.util.Properties
import mu.KotlinLogging

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

    // 临时文件跟踪，用于存储过长的 agents JSON（参考 Python SDK）
    private val tempFiles = mutableListOf<Path>()

    companion object {
        // Windows 命令行长度限制（参考 Python SDK）
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
    
    private val logger = KotlinLogging.logger {}
    
    override suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            val command = buildCommand()
            logger.info("🚀 构建Claude CLI命令: ${command.joinToString(" ")}")
            
            val processBuilder = ProcessBuilder(command).apply {
                // Set working directory if provided
                options.cwd?.let { 
                    logger.info("📂 设置工作目录: $it")
                    directory(it.toFile()) 
                }
                
                // Set environment variables
                if (options.env.isNotEmpty()) {
                    logger.info("🌐 设置环境变量: ${options.env}")
                    environment().putAll(options.env)
                }

                // Set environment entrypoint
                environment()["CLAUDE_CODE_ENTRYPOINT"] = "sdk-kt-client"
                logger.info("🏷️ 设置环境入口点: sdk-kt-client")

                // Disable Ink UI to prevent "Raw mode is not supported" error
                environment()["CI"] = "true"
                environment()["FORCE_COLOR"] = "0"
                logger.info("🎨 禁用 Ink UI (CI=true, FORCE_COLOR=0)")
            }
            
            logger.info("⚡ 启动Claude CLI进程...")

            // 直接执行命令（不使用 cmd /c，避免 JSON 参数被 shell 解析）
            // Java ProcessBuilder 可以直接执行 .cmd 文件（如果使用完整路径）
            process = processBuilder.start()

            logger.info("✅ Claude CLI进程启动成功, PID: ${process?.pid()}")

            // 检查进程是否立即退出
            delay(100) // 短暂等待
            if (!process!!.isAlive) {
                val exitCode = process!!.exitValue()
                val stderrContent = try {
                    BufferedReader(InputStreamReader(process!!.errorStream)).readText()
                } catch (e: Exception) {
                    "无法读取stderr: ${e.message}"
                }
                logger.error("❌ Claude CLI进程立即退出，退出代码: $exitCode")
                logger.error("❌ stderr内容: $stderrContent")
                throw CLIConnectionException("Claude CLI process exited immediately with code $exitCode. stderr: $stderrContent")
            }

            // Setup I/O streams
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            errorReader = BufferedReader(InputStreamReader(process!!.errorStream))
            logger.info("📡 I/O流设置完成（包含stderr）")

            isConnectedFlag = true
            logger.info("🎉 SubprocessTransport连接成功!")
        } catch (e: java.io.IOException) {
            logger.error("❌ Claude CLI进程启动失败: ${e.message}")
            // Check if it's a file not found error (CLI not installed)
            if (e.message?.contains("No such file") == true || 
                e.message?.contains("not found") == true) {
                throw CLINotFoundException.withInstallInstructions(isNodeInstalled())
            }
            throw CLIConnectionException("Failed to start Claude CLI process", e)
        } catch (e: Exception) {
            logger.error("❌ Claude CLI进程启动失败: ${e.message}")
            throw CLIConnectionException("Failed to start Claude CLI process", e)
        }
    }
    
    override suspend fun write(data: String) = withContext(Dispatchers.IO) {
        try {
            writer?.let { w ->
                logger.info("📤 向CLI写入数据: $data")
                w.write(data)
                w.newLine()
                w.flush()
                logger.info("✅ 数据写入CLI成功")
            } ?: throw TransportException("Transport not connected")
        } catch (e: Exception) {
            logger.error("❌ 向CLI写入数据失败: ${e.message}")
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
                    logger.info("📥 从 CLI 读取到原始行: $line")
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
                            logger.info("📨 从CLI读取到完整JSON: ${jsonBuffer.toString()}")
                            emit(jsonElement)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // 协程被取消（正常的断开连接），直接重新抛出
                            logger.info("ℹ️ 消息处理被取消（连接断开）")
                            throw e
                        } catch (e: Exception) {
                            logger.warn("⚠️ JSON解析失败: ${jsonBuffer.toString()}, error: ${e.message}")
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
            logger.info("ℹ️ Transport 读取被取消（连接断开）")
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
                            logger.error("❌ Claude CLI进程失败，退出代码: $exitCode, stderr: $stderrContent")
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
                    logger.info("🗑️ 清理临时文件: $tempFile")
                } catch (e: Exception) {
                    logger.warn("⚠️ 清理临时文件失败: $tempFile - ${e.message}")
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
                    command.addAll(listOf("--system-prompt", prompt))
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
                            logger.info("📝 将 append-system-prompt 写入临时文件: $tempFile")
                            command.add("--append-system-prompt-file")
                            command.add("\"${tempFile.toAbsolutePath()}\"")
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
        // 使用 --append-system-prompt-file 参数，不会替换默认提示词
        options.appendSystemPromptFile?.let { appendContent ->
            val tempFile = getOrCreateSystemPromptFile(appendContent)
            logger.info("📝 将 appendSystemPromptFile 写入临时文件: $tempFile")
            command.add("--append-system-prompt-file")
            command.add("\"${tempFile.toAbsolutePath()}\"")
        }
        
        // Allowed tools
        // Windows 下工具名可能包含特殊字符（如 Bash(git:*)），需要引号包围
        if (options.allowedTools.isNotEmpty()) {
            val toolsArg = options.allowedTools.joinToString(",")
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            if (isWindows) {
                command.addAll(listOf("--allowed-tools", "\"$toolsArg\""))
            } else {
                command.addAll(listOf("--allowed-tools", toolsArg))
            }
        }

        // Disallowed tools
        if (options.disallowedTools.isNotEmpty()) {
            val toolsArg = options.disallowedTools.joinToString(",")
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            if (isWindows) {
                command.addAll(listOf("--disallowed-tools", "\"$toolsArg\""))
            } else {
                command.addAll(listOf("--disallowed-tools", toolsArg))
            }
        }

        // Agents (programmatic subagents) - 参考 Python SDK 实现
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

                // Windows 下需要转义 JSON 中的双引号（与 --mcp-config 处理一致）
                val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                if (isWindows) {
                    val escapedJson = "\"" + agentsJson.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                    command.addAll(listOf("--agents", escapedJson))
                    logger.info("🤖 配置自定义代理（Windows 转义）: ${agents.keys.joinToString(", ")}")
                } else {
                    command.addAll(listOf("--agents", agentsJson))
                    logger.info("🤖 配置自定义代理: ${agents.keys.joinToString(", ")}")
                }
            }
        }

        // Permission mode
        options.permissionMode?.let { mode ->
            val permissionModeValue = when (mode) {
                PermissionMode.DEFAULT -> "default"
                PermissionMode.ACCEPT_EDITS -> "acceptEdits"
                PermissionMode.PLAN -> "plan"
                PermissionMode.BYPASS_PERMISSIONS -> "bypassPermissions"
                PermissionMode.DONT_ASK -> "dontAsk"
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
        logger.info("🔍 [buildCommand] options.canUseTool=${options.canUseTool != null}, options.permissionPromptToolName=${options.permissionPromptToolName}")
        // 如果提供了 canUseTool 回调，自动设置为 "stdio" 以启用控制协议权限请求
        val effectivePermissionPromptTool = options.permissionPromptToolName
            ?: if (options.canUseTool != null) "stdio" else null
        effectivePermissionPromptTool?.let { tool ->
            command.addAll(listOf("--permission-prompt-tool", tool))
            logger.info("🔐 配置授权工具: $tool")
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
            val serversForCli = mutableMapOf<String, Map<String, Any?>>()

            options.mcpServers.forEach { (name, config) ->
                when (config) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val configMap = config as Map<String, Any?>
                        if (configMap["type"] == "sdk") {
                            // SDK 服务器：去掉 instance 字段，保留其他
                            val sdkConfig = configMap.filterKeys { it != "instance" }
                            serversForCli[name] = sdkConfig
                            logger.info("📦 添加 SDK MCP 服务器配置: $name -> $sdkConfig")
                        } else {
                            // 外部服务器：直接传递
                            serversForCli[name] = configMap
                            logger.info("📦 添加外部 MCP 服务器配置: $name")
                        }
                    }
                    else -> {
                        // 其他类型（如 McpServer 实例），转换为 SDK 配置
                        if (config is com.asakii.claude.agent.sdk.mcp.McpServer) {
                            val serverConfig = mutableMapOf<String, Any?>(
                                "type" to "sdk",
                                "name" to config.name
                            )
                            // 添加超时配置
                            // timeout > 0: 指定超时时间（毫秒）
                            // timeout <= 0 或 null: 显式传递 -1 表示无限超时
                            val timeout = config.timeout
                            if (timeout != null && timeout > 0) {
                                serverConfig["timeout"] = timeout
                            } else {
                                // 显式传递 -1 表示无限超时，确保 CLI 不使用默认超时
                                serverConfig["timeout"] = -1
                            }
                            serversForCli[name] = serverConfig
                            logger.info("📦 添加 MCP 服务器实例配置: $name -> type=sdk, timeout=${timeout ?: "infinite"}")
                        } else {
                            serversForCli[name] = mapOf(
                                "type" to "sdk",
                                "name" to name,
                                "timeout" to -1  // 默认无限超时
                            )
                            logger.info("📦 添加 MCP 服务器实例配置: $name -> type=sdk, timeout=infinite")
                        }
                    }
                }
            }

            if (serversForCli.isNotEmpty()) {
                val mcpConfigJson = buildJsonObject {
                    putJsonObject("mcpServers") {
                        serversForCli.forEach { (serverName, serverConfig) ->
                            putJsonObject(serverName) {
                                serverConfig.forEach { (key, value) ->
                                    when (value) {
                                        is String -> put(key, value)
                                        is Number -> put(key, value)
                                        is Boolean -> put(key, value)
                                        null -> put(key, JsonNull)
                                        else -> put(key, value.toString())
                                    }
                                }
                            }
                        }
                    }
                }.toString()

                // Windows 下需要转义 JSON 中的双引号（参考 Python subprocess.list2cmdline）
                // 规则：" -> \"，然后用双引号包围整个参数
                val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                if (isWindows) {
                    // Windows: 转义双引号并用双引号包围
                    val escapedJson = "\"" + mcpConfigJson.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                    command.addAll(listOf("--mcp-config", escapedJson))
                    logger.info("🔧 MCP 配置（Windows 转义）: $escapedJson")
                } else {
                    // Unix: 直接传递
                    command.addAll(listOf("--mcp-config", mcpConfigJson))
                    logger.info("🔧 MCP 配置: $mcpConfigJson")
                }
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
        
        // 检查命令行长度（Windows 限制约 8000 字符）
        // 如果过长且有 agents 参数，使用临时文件存储 agents JSON
        val cmdStr = command.joinToString(" ")
        if (cmdStr.length > CMD_LENGTH_LIMIT && options.agents != null) {
            try {
                val agentsIdx = command.indexOf("--agents")
                if (agentsIdx >= 0 && agentsIdx + 1 < command.size) {
                    val agentsJsonValue = command[agentsIdx + 1]

                    // 创建临时文件
                    val tempFile = Files.createTempFile("claude_agents_", ".json")
                    Files.writeString(tempFile, agentsJsonValue)
                    tempFiles.add(tempFile)

                    // 替换为 @filepath 引用
                    command[agentsIdx + 1] = "@${tempFile.toAbsolutePath()}"

                    logger.info("📄 命令行长度 (${cmdStr.length}) 超过限制 ($CMD_LENGTH_LIMIT)，使用临时文件: $tempFile")
                }
            } catch (e: Exception) {
                logger.warn("⚠️ 优化命令行长度失败: ${e.message}")
            }
        }

        logger.info("🔧 完整构建的Claude CLI命令: ${command.joinToString(" ")}")
        return command
    }
    
    /**
     * Find the Claude executable in the system.
     * 优先级：
     * 1. 用户指定路径 (options.cliPath)
     * 2. SDK 绑定的 CLI (resources/bundled/claude-cli-<version>.js, 通过 Node.js 运行)
     * 3. 系统全局安装的 CLI
     */
    private fun findClaudeExecutable(): List<String> {
        // 1. 用户指定路径（最高优先级）
        options.cliPath?.let { customPath ->
            logger.info("✅ 使用用户指定的 CLI: $customPath")
            return listOf(customPath.toString())
        }

        // 2. SDK 绑定的 CLI（使用 Node.js 运行）
        val bundledCliJs = findBundledCliJs()
        if (bundledCliJs != null) {
            val nodeCommand = findNodeExecutable()
            logger.info("✅ 使用 SDK 绑定的 CLI: $nodeCommand $bundledCliJs")
            return listOf(nodeCommand, bundledCliJs)
        }

        // 未找到绑定的 CLI，抛出异常（不再回退到系统全局 CLI）
        throw CLINotFoundException(
            "未找到 SDK 绑定的 Claude CLI。请确保：\n" +
            "1. 已运行 gradle processResources 或 gradle build\n" +
            "2. cli-version.properties 配置正确\n" +
            "3. bundled/claude-cli-<version>.js 文件存在于 resources 目录"
        )
    }

    /**
     * 返回 Node.js 命令名，直接依赖系统 PATH 环境变量
     */
    private fun findNodeExecutable(): String = "node"

    /**
     * 查找 SDK 绑定的 CLI (cli.js, 从 resources/bundled/ 目录)
     * 优先使用增强版 CLI (带补丁)，如果不存在则回退到原始版本
     */
    private fun findBundledCliJs(): String? {
        return try {
            // 读取 CLI 版本（cli-version.properties 由 copyCliVersionProps 任务复制到 resources 目录）
            val versionProps = Properties()
            this::class.java.classLoader.getResourceAsStream("cli-version.properties")?.use {
                versionProps.load(it)
            }
            val cliVersion = versionProps.getProperty("cli.version")
            if (cliVersion == null) {
                logger.warn("⚠️ 未找到 cli-version.properties 或 cli.version 属性")
                return null
            }

            // 查找增强版 CLI
            val cliJsName = "claude-cli-$cliVersion-enhanced.js"
            val resourcePath = "bundled/$cliJsName"
            logger.info("🔍 查找绑定的 CLI: $resourcePath")
            val resource = this::class.java.classLoader.getResource(resourcePath)

            if (resource != null) {
                // 如果资源在 JAR 内，提取到临时文件
                if (resource.protocol == "jar") {
                    val tempFile = kotlin.io.path.createTempFile("claude-cli-", ".js").toFile()
                    tempFile.deleteOnExit()

                    resource.openStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    logger.info("📦 从 JAR 提取 CLI: ${tempFile.absolutePath}")
                    return tempFile.absolutePath
                } else {
                    // 资源在文件系统中（开发模式）
                    val file = java.io.File(resource.toURI())
                    if (file.exists()) {
                        logger.info("📦 找到本地绑定的 CLI: ${file.absolutePath}")
                        return file.absolutePath
                    }
                }
            }

            logger.warn("⚠️ 未找到绑定的 CLI: $cliJsName")
            null
        } catch (e: Exception) {
            logger.debug("查找绑定 CLI 失败: ${e.message}")
            null
        }
    }

    /**
     * 查找 SDK 绑定的 CLI（从 resources/bundled/{platform}/ 目录）
     * 仿照 Python SDK 的 _find_bundled_cli() 实现
     * @deprecated 已废弃，使用 findBundledCliJs() 替代
     */
    @Deprecated("使用 findBundledCliJs() 替代")
    private fun findBundledCli(): String? {
        return try {
            // 检测当前平台
            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch").lowercase()

            val isWindows = osName.contains("windows")
            val isMac = osName.contains("mac") || osName.contains("darwin")
            val isLinux = osName.contains("linux")

            val arch = when {
                osArch.contains("amd64") || osArch.contains("x86_64") -> "x64"
                osArch.contains("aarch64") || osArch.contains("arm64") -> "arm64"
                else -> {
                    logger.debug("不支持的架构: $osArch")
                    return null
                }
            }

            // 组合平台标识（与下载任务一致）
            val platformId = when {
                isWindows -> "win32-$arch"
                isMac -> "darwin-$arch"
                isLinux -> "linux-$arch"  // 优先尝试 glibc 版本
                else -> {
                    logger.debug("不支持的操作系统: $osName")
                    return null
                }
            }

            val cliName = if (isWindows) "claude.exe" else "claude"

            // 从 ClassLoader 获取资源
            val resourcePath = "bundled/$platformId/$cliName"
            logger.info("🔍 查找绑定 CLI: $resourcePath (平台: $platformId)")
            val resource = this::class.java.classLoader.getResource(resourcePath)
            logger.info("🔍 ClassLoader.getResource() 结果: $resource")

            if (resource != null) {
                // 如果资源在 JAR 内，需要提取到临时文件
                if (resource.protocol == "jar") {
                    val tempFile = kotlin.io.path.createTempFile("claude-", if (isWindows) ".exe" else "").toFile()
                    tempFile.deleteOnExit()

                    resource.openStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Unix 系统设置可执行权限
                    if (!isWindows) {
                        tempFile.setExecutable(true)
                    }

                    logger.info("📦 从 JAR 提取 CLI ($platformId) 到: ${tempFile.absolutePath}")
                    return tempFile.absolutePath
                } else {
                    // 资源在文件系统中（开发模式）
                    val file = java.io.File(resource.toURI())
                    if (file.exists()) {
                        // 确保有可执行权限
                        if (!isWindows && !file.canExecute()) {
                            file.setExecutable(true)
                        }
                        logger.info("📦 找到本地绑定的 CLI ($platformId): ${file.absolutePath}")
                        return file.absolutePath
                    }
                }
            }

            // Linux 系统回退尝试 musl 版本
            if (isLinux) {
                val muslPlatformId = "linux-$arch-musl"
                val muslResourcePath = "bundled/$muslPlatformId/$cliName"
                val muslResource = this::class.java.classLoader.getResource(muslResourcePath)

                if (muslResource != null) {
                    logger.info("📦 回退到 musl 版本: $muslPlatformId")
                    // 同样的提取逻辑...
                    if (muslResource.protocol == "jar") {
                        val tempFile = kotlin.io.path.createTempFile("claude-", "").toFile()
                        tempFile.deleteOnExit()

                        muslResource.openStream().use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        tempFile.setExecutable(true)
                        logger.info("📦 从 JAR 提取 CLI ($muslPlatformId) 到: ${tempFile.absolutePath}")
                        return tempFile.absolutePath
                    } else {
                        val file = java.io.File(muslResource.toURI())
                        if (file.exists()) {
                            if (!file.canExecute()) {
                                file.setExecutable(true)
                            }
                            logger.info("📦 找到本地绑定的 CLI ($muslPlatformId): ${file.absolutePath}")
                            return file.absolutePath
                        }
                    }
                }
            }

            null
        } catch (e: Exception) {
            logger.debug("查找绑定 CLI 失败: ${e.message}")
            null
        }
    }
    
    /**
     * Check if Node.js is installed on the system.
     */
    private fun isNodeInstalled(): Boolean {
        return try {
            val process = ProcessBuilder("node", "--version").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
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
            logger.info("📦 使用缓存的系统提示词文件: $cachedPath (digest: $digest)")
            return cachedPath
        }

        // 缓存未命中或文件已删除，创建新文件
        // 使用子目录存放，方便查找：{tempDir}/claude-agent-sdk/system-prompts/
        val tempDir = Path.of(System.getProperty("java.io.tmpdir"))
        val promptDir = tempDir.resolve("claude-agent-sdk").resolve("system-prompts")

        // 确保子目录存在
        if (!Files.exists(promptDir)) {
            Files.createDirectories(promptDir)
            logger.info("📁 创建系统提示词目录: $promptDir")
        }

        val tempFile = promptDir.resolve("prompt-$digest.txt")

        // 写入内容
        Files.writeString(tempFile, content)
        tempFile.toFile().deleteOnExit()

        // 存入缓存
        systemPromptFileCache.put(digest, tempFile)
        logger.info("📝 创建新的系统提示词文件: $tempFile (digest: $digest)")

        return tempFile
    }
}