package com.claudecodeplus.core

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Claude 会话消息
 */
sealed class SessionMessage {
    data class Input(val text: String, val timestamp: Long = System.currentTimeMillis()) : SessionMessage()
    data class Output(val text: String, val timestamp: Long = System.currentTimeMillis()) : SessionMessage()
    data class Error(val message: String, val timestamp: Long = System.currentTimeMillis()) : SessionMessage()
    data class StateChanged(val state: SessionState) : SessionMessage()
}

/**
 * 会话状态
 */
enum class SessionState {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED
}

/**
 * Claude PTY 会话配置
 */
data class SessionConfig(
    val command: List<String> = listOf("claude"),
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val initialColumns: Int = 80,
    val initialRows: Int = 24
)

/**
 * 基于 PTY 的 Claude 交互式会话
 */
class ClaudeSession(
    private val config: SessionConfig = SessionConfig()
) {
    val id: String = UUID.randomUUID().toString()
    
    private var ptyProcess: PtyProcess? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    
    private val _messageFlow = MutableSharedFlow<SessionMessage>()
    val messageFlow: SharedFlow<SessionMessage> = _messageFlow.asSharedFlow()
    
    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null
    
    /**
     * 启动会话
     */
    suspend fun start() {
        if (_state.value != SessionState.IDLE && _state.value != SessionState.STOPPED) {
            throw IllegalStateException("Session is already running or starting")
        }
        
        setState(SessionState.STARTING)
        
        withContext(Dispatchers.IO) {
            try {
                // 构建 PTY 进程
                val builder = PtyProcessBuilder()
                    .setCommand(config.command.toTypedArray())
                    .setRedirectErrorStream(true)
                    .setConsole(true)
                    .setInitialColumns(config.initialColumns)
                    .setInitialRows(config.initialRows)
                
                // 设置工作目录
                config.workingDirectory?.let {
                    builder.setDirectory(it)
                }
                
                // 设置环境变量
                val env = mutableMapOf<String, String>()
                env.putAll(System.getenv())
                env.putAll(config.environment)
                env["TERM"] = env["TERM"] ?: "xterm-256color"
                env["LANG"] = env["LANG"] ?: "en_US.UTF-8"
                builder.setEnvironment(env)
                
                // 启动 PTY 进程
                ptyProcess = builder.start()
                outputStream = ptyProcess!!.outputStream
                inputStream = ptyProcess!!.inputStream
                
                setState(SessionState.RUNNING)
                
                // 启动读取任务
                readJob = scope.launch {
                    readOutput()
                }
                
                // 监控进程状态
                scope.launch {
                    monitorProcess()
                }
                
                _messageFlow.emit(SessionMessage.Output("Claude PTY session started (PID: ${ptyProcess!!.pid()})\n"))
                
            } catch (e: Exception) {
                setState(SessionState.STOPPED)
                _messageFlow.emit(SessionMessage.Error("Failed to start Claude PTY: ${e.message}"))
                throw e
            }
        }
    }
    
    /**
     * 发送输入
     */
    suspend fun sendInput(text: String) {
        if (_state.value != SessionState.RUNNING) {
            throw IllegalStateException("Session is not running")
        }
        
        withContext(Dispatchers.IO) {
            try {
                outputStream?.let { stream ->
                    // PTY 环境需要发送 \r 而不是 \n
                    val inputText = if (!text.endsWith("\r") && !text.endsWith("\n")) {
                        text + "\r"
                    } else if (text.endsWith("\n")) {
                        text.dropLast(1) + "\r"
                    } else {
                        text
                    }
                    stream.write(inputText.toByteArray(StandardCharsets.UTF_8))
                    stream.flush()
                }
                _messageFlow.emit(SessionMessage.Input(text.trimEnd()))
            } catch (e: Exception) {
                _messageFlow.emit(SessionMessage.Error("Failed to send input: ${e.message}"))
                throw e
            }
        }
    }
    
    /**
     * 调整终端大小
     */
    suspend fun resize(columns: Int, rows: Int) {
        withContext(Dispatchers.IO) {
            try {
                ptyProcess?.winSize = WinSize(columns, rows)
            } catch (e: Exception) {
                _messageFlow.emit(SessionMessage.Error("Failed to resize terminal: ${e.message}"))
            }
        }
    }
    
    /**
     * 停止会话
     */
    suspend fun stop() {
        if (_state.value == SessionState.STOPPED || _state.value == SessionState.STOPPING) {
            return
        }
        
        setState(SessionState.STOPPING)
        
        withContext(Dispatchers.IO) {
            try {
                readJob?.cancel()
                outputStream?.close()
                inputStream?.close()
                ptyProcess?.destroyForcibly()
                
                setState(SessionState.STOPPED)
                _messageFlow.emit(SessionMessage.Output("Session stopped\n"))
                
            } catch (e: Exception) {
                _messageFlow.emit(SessionMessage.Error("Error stopping session: ${e.message}"))
            } finally {
                scope.cancel()
            }
        }
    }
    
    /**
     * 读取输出
     */
    private suspend fun readOutput() {
        val buffer = ByteArray(4096)
        
        try {
            val stream = inputStream ?: return
            
            while (true) {
                try {
                    val bytesRead = stream.read(buffer)
                    when {
                        bytesRead > 0 -> {
                            val text = String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
                            _messageFlow.emit(SessionMessage.Output(text))
                        }
                        bytesRead < 0 -> break
                    }
                } catch (e: IOException) {
                    // 如果是因为流被关闭，正常退出
                    if (e.message?.contains("Stream closed") == true) {
                        break
                    }
                    // 其他错误，发送错误消息
                    try {
                        _messageFlow.emit(SessionMessage.Error("Error reading: ${e.message}"))
                    } catch (_: Exception) {
                        // 忽略发送错误时的异常
                    }
                    break
                }
            }
        } catch (e: Exception) {
            // 顶层异常处理
            try {
                _messageFlow.emit(SessionMessage.Error("Fatal error: ${e.message}"))
            } catch (_: Exception) {
                // 忽略
            }
        }
    }
    
    /**
     * 监控进程状态
     */
    private suspend fun monitorProcess() {
        try {
            ptyProcess?.waitFor()
            setState(SessionState.STOPPED)
            _messageFlow.emit(SessionMessage.Output("\nProcess exited\n"))
        } catch (e: Exception) {
            _messageFlow.emit(SessionMessage.Error("Error monitoring process: ${e.message}"))
        }
    }
    
    private suspend fun setState(newState: SessionState) {
        _state.value = newState
        _messageFlow.emit(SessionMessage.StateChanged(newState))
    }
    
    fun isAlive(): Boolean = ptyProcess?.isAlive ?: false
    
    fun getPid(): Long = ptyProcess?.pid() ?: -1
}