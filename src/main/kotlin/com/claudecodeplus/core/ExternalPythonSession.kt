package com.claudecodeplus.core

import com.claudecodeplus.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.time.LocalDateTime
import java.util.UUID
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 使用外部 Python 进程的 Claude 会话实现
 * 适用于没有 GraalPython 的环境
 */
class ExternalPythonSession(
    override val config: SessionConfig
) : ClaudeSession {
    
    override val sessionId: String = UUID.randomUUID().toString()
    
    private var pythonProcess: Process? = null
    private var processWriter: PrintWriter? = null
    private var processReader: BufferedReader? = null
    private val gson = Gson()
    
    private var _state = SessionState(
        sessionId = sessionId,
        status = SessionStatus.IDLE
    )
    
    override val state: SessionState
        get() = _state
    
    private val messages = mutableListOf<SessionMessage>()
    
    override suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            _state = _state.copy(
                status = SessionStatus.INITIALIZING,
                startTime = LocalDateTime.now()
            )
            
            // 启动 Python 进程
            val pythonCmd = config.pythonPath ?: "python3"
            val scriptPath = getWrapperScriptPath()
            
            println("Starting Python process: $pythonCmd $scriptPath")
            println("Note: Currently using predefined responses for testing. Real Claude API integration is in development.")
            
            val processBuilder = ProcessBuilder(pythonCmd, scriptPath)
                .directory(java.io.File(config.workingDirectory))
            
            // 设置环境变量
            val env = processBuilder.environment()
            config.apiKey?.let { env["CLAUDE_API_KEY"] = it }
            
            pythonProcess = processBuilder.start()
            processWriter = PrintWriter(pythonProcess!!.outputStream, true)
            processReader = BufferedReader(InputStreamReader(pythonProcess!!.inputStream))
            
            // 启动错误流读取器
            val errorReader = BufferedReader(InputStreamReader(pythonProcess!!.errorStream))
            Thread {
                var line: String?
                while (errorReader.readLine().also { line = it } != null) {
                    System.err.println("Python stderr: $line")
                }
            }.start()
            
            // 读取启动信号
            val startupSignal = processReader?.readLine()
            println("Python script startup signal: $startupSignal")
            
            if (startupSignal == null) {
                throw RuntimeException("Python script failed to start")
            }
            
            // 检查启动信号
            val startupResult = gson.fromJson(startupSignal, JsonObject::class.java)
            if (startupResult.get("status")?.asString != "ready") {
                throw RuntimeException("Python script not ready: $startupSignal")
            }
            
            // 发送初始化命令
            val initCommand = JsonObject().apply {
                addProperty("command", "initialize")
                add("config", gson.toJsonTree(mapOf(
                    "api_key" to config.apiKey,
                    "model" to config.model,
                    "max_tokens" to config.maxTokens,
                    "temperature" to config.temperature
                )))
            }
            
            println("Sending init command: ${gson.toJson(initCommand)}")
            processWriter?.println(gson.toJson(initCommand))
            processWriter?.flush()
            
            // 读取初始化结果
            val response = processReader?.readLine()
            println("Python init response: $response")
            
            if (response != null) {
                val result = gson.fromJson(response, JsonObject::class.java)
                if (result.get("success")?.asBoolean == true) {
                    _state = _state.copy(status = SessionStatus.READY)
                    println("Python session initialized successfully")
                } else {
                    val error = result.get("error")?.asString ?: result.get("message")?.asString ?: "Unknown error"
                    throw RuntimeException("Failed to initialize: $error")
                }
            } else {
                throw RuntimeException("No response from Python script")
            }
            
        } catch (e: Exception) {
            _state = _state.copy(status = SessionStatus.ERROR)
            println("Failed to initialize: ${e.message}")
            e.printStackTrace()
            throw RuntimeException("Failed to initialize external Python session", e)
        }
    }
    
    override suspend fun sendMessage(message: String): Flow<String> = flow {
        try {
            _state = _state.copy(status = SessionStatus.PROCESSING)
            
            // 添加用户消息
            val userMessage = SessionMessage(
                role = MessageRole.USER,
                content = message
            )
            messages.add(userMessage)
            _state = _state.copy(messages = messages.toList())
            
            // 在IO线程中发送消息
            val response = withContext(Dispatchers.IO) {
                // 发送消息命令
                val sendCommand = JsonObject().apply {
                    addProperty("command", "send_message")
                    addProperty("message", message)
                }
                
                println("Sending message command: ${gson.toJson(sendCommand)}")
                processWriter?.println(gson.toJson(sendCommand))
                processWriter?.flush()
                
                // 读取响应（添加超时检查）
                var responseStr: String? = null
                val startTime = System.currentTimeMillis()
                val timeout = 30000L // 30秒超时
                
                while (responseStr == null && (System.currentTimeMillis() - startTime) < timeout) {
                    if (processReader?.ready() == true) {
                        responseStr = processReader?.readLine()
                        println("Received response: $responseStr")
                    } else {
                        Thread.sleep(100) // 短暂等待
                    }
                }
                
                if (responseStr == null) {
                    println("Timeout waiting for response")
                    throw RuntimeException("Timeout waiting for response from Python script")
                }
                
                responseStr
            }
            
            if (response != null) {
                val result = gson.fromJson(response, JsonObject::class.java)
                if (result.get("success")?.asBoolean == true) {
                    val responseText = result.get("response")?.asString ?: ""
                    
                    // 添加助手消息
                    val assistantMessage = SessionMessage(
                        role = MessageRole.ASSISTANT,
                        content = responseText
                    )
                    messages.add(assistantMessage)
                    _state = _state.copy(messages = messages.toList())
                    
                    emit(responseText)
                } else {
                    val error = result.get("error")?.asString ?: "Unknown error"
                    throw RuntimeException("Claude API error: $error")
                }
            } else {
                throw RuntimeException("No response from Python script")
            }
            
            _state = _state.copy(status = SessionStatus.READY)
            
        } catch (e: Exception) {
            _state = _state.copy(status = SessionStatus.ERROR)
            
            val errorMessage = SessionMessage(
                role = MessageRole.ERROR,
                content = "Error: ${e.message}"
            )
            messages.add(errorMessage)
            _state = _state.copy(messages = messages.toList())
            
            println("Error in sendMessage: ${e.message}")
            e.printStackTrace()
            
            emit("Error: ${e.message}")
        }
    }
    
    override suspend fun stop() {
        _state = _state.copy(status = SessionStatus.READY)
    }
    
    override suspend fun terminate() = withContext(Dispatchers.IO) {
        try {
            _state = _state.copy(
                status = SessionStatus.TERMINATED,
                endTime = LocalDateTime.now()
            )
            
            // 发送退出命令
            val exitCommand = JsonObject().apply {
                addProperty("command", "exit")
            }
            processWriter?.println(gson.toJson(exitCommand))
            
            // 关闭流
            processWriter?.close()
            processReader?.close()
            
            // 终止进程
            pythonProcess?.destroyForcibly()
            pythonProcess = null
            
        } catch (e: Exception) {
            // 忽略清理错误
        }
    }
    
    override fun getHistory(): List<SessionMessage> = messages.toList()
    
    override fun clearHistory() {
        messages.clear()
        _state = _state.copy(messages = emptyList())
    }
    
    override fun isProcessing(): Boolean = _state.status == SessionStatus.PROCESSING
    
    private fun getWrapperScriptPath(): String {
        // 优先使用官方 claude-code-sdk 脚本
        val claudeCodeSdkPath = "/Users/erio/codes/idea/claude-code-plus/scripts/claude_code_sdk_wrapper.py"
        val claudeCodeSdkFile = java.io.File(claudeCodeSdkPath)
        if (claudeCodeSdkFile.exists()) {
            return claudeCodeSdkFile.absolutePath
        }
        
        // 备用：简单脚本进行测试
        val simpleWrapperPath = "/Users/erio/codes/idea/claude-code-plus/scripts/claude_simple_wrapper.py"
        val simpleWrapperFile = java.io.File(simpleWrapperPath)
        if (simpleWrapperFile.exists()) {
            return simpleWrapperFile.absolutePath
        }
        
        // 备用：Anthropic SDK 脚本
        val anthropicSdkPath = "/Users/erio/codes/idea/claude-code-plus/scripts/claude_anthropic_sdk.py"
        val anthropicSdkFile = java.io.File(anthropicSdkPath)
        if (anthropicSdkFile.exists()) {
            return anthropicSdkFile.absolutePath
        }
        
        // 备用：开发时的项目路径
        val devProjectPath = "/Users/erio/codes/idea/claude-code-plus/scripts/claude_wrapper.py"
        val devFile = java.io.File(devProjectPath)
        if (devFile.exists()) {
            return devFile.absolutePath
        }
        
        // 尝试从项目目录获取
        val projectPath = System.getProperty("user.dir")
        val scriptFile = java.io.File(projectPath, "scripts/claude_wrapper.py")
        if (scriptFile.exists()) {
            return scriptFile.absolutePath
        }
        
        // 尝试从当前运行目录的相对路径
        val relativePaths = listOf(
            "claude-code-plus/scripts/claude_wrapper.py",
            "../scripts/claude_wrapper.py",
            "../../scripts/claude_wrapper.py"
        )
        
        for (path in relativePaths) {
            val file = java.io.File(path)
            if (file.exists()) {
                return file.absolutePath
            }
        }
        
        // 尝试从用户目录
        val userDir = System.getProperty("user.home")
        val userScriptFile = java.io.File(userDir, ".claude-code-plus/scripts/claude_wrapper.py")
        if (userScriptFile.exists()) {
            return userScriptFile.absolutePath
        }
        
        throw RuntimeException("Cannot find claude_wrapper.py script. Searched in: $devProjectPath, ${scriptFile.absolutePath}")
    }
}