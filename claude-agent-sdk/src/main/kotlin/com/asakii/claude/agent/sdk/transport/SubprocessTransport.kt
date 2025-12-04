package com.asakii.claude.agent.sdk.transport

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
import java.util.logging.Logger
import kotlin.io.path.exists

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
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val logger = Logger.getLogger(SubprocessTransport::class.java.name)
    
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
                logger.severe("❌ Claude CLI进程立即退出，退出代码: $exitCode")
                logger.severe("❌ stderr内容: $stderrContent")
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
            logger.severe("❌ Claude CLI进程启动失败: ${e.message}")
            // Check if it's a file not found error (CLI not installed)
            if (e.message?.contains("No such file") == true || 
                e.message?.contains("not found") == true) {
                throw CLINotFoundException.withInstallInstructions(isNodeInstalled())
            }
            throw CLIConnectionException("Failed to start Claude CLI process", e)
        } catch (e: Exception) {
            logger.severe("❌ Claude CLI进程启动失败: ${e.message}")
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
            logger.severe("❌ 向CLI写入数据失败: ${e.message}")
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
                        } catch (e: Exception) {
                            logger.warning("⚠️ JSON解析失败: ${jsonBuffer.toString()}, error: ${e.message}")
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
                            logger.severe("❌ Claude CLI进程失败，退出代码: $exitCode, stderr: $stderrContent")
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
        
        // Base command - try to find claude executable
        command.add(findClaudeExecutable())
        
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

        // Print flag (根据选项决定) - 必须在 --verbose 之后
        if (options.print) {
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
                        // Use the preset flag
                        command.add("--system-prompt-preset")
                        command.add(prompt.preset)

                        // Add append if provided
                        prompt.append?.let { appendText ->
                            command.add("--append-system-prompt")
                            command.add(appendText)
                        }
                    } else {
                        // Unknown preset, convert to string
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
        
        // Allowed tools
        if (options.allowedTools.isNotEmpty()) {
            command.addAll(listOf("--allowed-tools", options.allowedTools.joinToString(",")))
        }
        
        // Disallowed tools
        if (options.disallowedTools.isNotEmpty()) {
            command.addAll(listOf("--disallowed-tools", options.disallowedTools.joinToString(",")))
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

        // Permission prompt tool - 配置授权请求使用的 MCP 工具
        // 当 Claude 需要执行敏感操作时，会调用此工具请求用户授权
        // 如果提供了 canUseTool 回调，自动设置为 "stdio"（与 Python SDK 一致）
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
                            // 添加超时配置（null 或 0 表示无限超时）
                            config.timeout?.let { timeout ->
                                if (timeout > 0) {
                                    serverConfig["timeout"] = timeout
                                }
                                // timeout 为 null 或 0 时不传递，CLI 默认无限等待
                            }
                            serversForCli[name] = serverConfig
                            logger.info("📦 添加 MCP 服务器实例配置: $name -> type=sdk, timeout=${config.timeout ?: "infinite"}")
                        } else {
                            serversForCli[name] = mapOf(
                                "type" to "sdk",
                                "name" to name
                            )
                            logger.info("📦 添加 MCP 服务器实例配置: $name -> type=sdk")
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
        
        logger.info("🔧 完整构建的Claude CLI命令: ${command.joinToString(" ")}")
        return command
    }
    
    /**
     * Find the Claude executable in the system.
     * 参考 Python SDK: Windows 上优先使用 claude.exe（不是 .cmd）
     * 因为 .cmd 是批处理文件，会经过 cmd.exe 解析，破坏 JSON 参数
     */
    private fun findClaudeExecutable(): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")

        // Windows 上优先查找 .exe（参考 Python SDK）
        // .exe 直接执行，不经过 cmd.exe 解析，参数不会被破坏
        if (isWindows) {
            try {
                val process = ProcessBuilder("where", "claude").start()
                val result = process.inputStream.bufferedReader().readText().trim()
                if (process.waitFor() == 0 && result.isNotEmpty()) {
                    val lines = result.lines()
                    // 优先选择 .exe 文件（不会经过 shell 解析）
                    val exeFile = lines.find { it.endsWith(".exe") }
                    if (exeFile != null) {
                        logger.info("✅ 找到 claude.exe: $exeFile")
                        return exeFile
                    }
                    // 其次选择 .cmd（但会有参数问题）
                    val cmdFile = lines.find { it.endsWith(".cmd") }
                    if (cmdFile != null) {
                        logger.warning("⚠️ 只找到 claude.cmd，JSON 参数可能被破坏: $cmdFile")
                        return cmdFile
                    }
                    return lines.first()
                }
            } catch (e: Exception) {
                logger.info("where 命令失败: ${e.message}")
            }
        } else {
            // Unix 系统
            try {
                val process = ProcessBuilder("which", "claude").start()
                val result = process.inputStream.bufferedReader().readText().trim()
                if (process.waitFor() == 0 && result.isNotEmpty()) {
                    return result.lines().first()
                }
            } catch (e: Exception) {
                logger.info("which 命令失败: ${e.message}")
            }
        }

        // 回退到直接使用 "claude"
        logger.info("直接使用 'claude' 命令")
        return "claude"
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
}