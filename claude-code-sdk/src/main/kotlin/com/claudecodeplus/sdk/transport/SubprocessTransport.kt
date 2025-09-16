package com.claudecodeplus.sdk.transport

import com.claudecodeplus.sdk.exceptions.*
import com.claudecodeplus.sdk.types.ClaudeCodeOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
    private val options: ClaudeCodeOptions,
    private val streamingMode: Boolean = true
) : Transport {
    
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
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
            }
            
            logger.info("⚡ 启动Claude CLI进程...")
            process = processBuilder.start()
            logger.info("✅ Claude CLI进程启动成功, PID: ${process?.pid()}")
            
            // Setup I/O streams
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            logger.info("📡 I/O流设置完成")
            
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
                            throw ProcessException(
                                "Command failed with exit code $exitCode",
                                exitCode = exitCode,
                                stderr = "Check stderr output for details"
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
        
        // Output format
        command.addAll(listOf("--output-format", "stream-json"))
        
        // Verbose output
        command.add("--verbose")
        
        // Input format for streaming mode
        if (streamingMode) {
            command.addAll(listOf("--input-format", "stream-json"))
        } else {
            command.addAll(listOf("--print", "--"))
        }
        
        // Note: Permission handling is done through the stream-json protocol
        // No special command line flags needed for permission callbacks
        
        // Model selection
        options.model?.let { model ->
            command.addAll(listOf("--model", model))
        }
        
        // System prompt
        options.systemPrompt?.let { prompt ->
            command.addAll(listOf("--system-prompt", prompt))
        }
        
        // Append system prompt
        options.appendSystemPrompt?.let { prompt ->
            command.addAll(listOf("--append-system-prompt", prompt))
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
            command.addAll(listOf("--permission-mode", mode.name.lowercase().replace("_", "")))
        }
        
        // Continue conversation
        if (options.continueConversation) {
            command.add("--continue-conversation")
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
        
        // MCP servers configuration
        if (options.mcpServers.isNotEmpty()) {
            // For now, skip MCP config serialization to avoid serialization issues
            // TODO: Implement proper MCP configuration if needed
        }
        
        // Extra arguments
        options.extraArgs.forEach { (key, value) ->
            command.add("--$key")
            value?.let { command.add(it) }
        }
        
        return command
    }
    
    /**
     * Find the Claude executable in the system.
     */
    private fun findClaudeExecutable(): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val executable = if (isWindows) "claude.cmd" else "claude"
        
        // First try to find claude via which/where command
        try {
            val whichCommand = if (isWindows) "where" else "which"
            val process = ProcessBuilder(whichCommand, "claude").start()
            val result = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && result.isNotEmpty()) {
                return result.lines().first() // Return first match
            }
        } catch (e: Exception) {
            // Continue to manual search if which/where fails
        }
        
        // Check common installation locations
        val homeDir = System.getProperty("user.home")
        val paths = listOf(
            "$homeDir/.npm-global/bin/$executable",
            "/usr/local/bin/$executable", 
            "/opt/homebrew/bin/$executable",
            "$homeDir/.local/bin/$executable",
            "$homeDir/node_modules/.bin/$executable",
            "$homeDir/.yarn/bin/$executable"
        )
        
        for (path in paths) {
            if (Path.of(path).exists()) {
                return path
            }
        }
        
        // Check if Node.js is installed
        val nodeInstalled = isNodeInstalled()
        
        if (!nodeInstalled) {
            throw CLINotFoundException.withInstallInstructions(nodeInstalled = false)
        }
        
        // If we get here, Claude is not found but Node.js is installed
        throw CLINotFoundException.withInstallInstructions(nodeInstalled = true)
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