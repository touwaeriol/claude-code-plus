package com.claudecodeplus.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*

/**
 * 简化版 Claude 会话 - 基于成功的测试
 */
class SimpleClaudeSession {
    private var process: Process? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    
    private val _outputFlow = MutableSharedFlow<String>()
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()
    
    suspend fun start() {
        withContext(Dispatchers.IO) {
            process = ProcessBuilder("claude")
                .redirectErrorStream(true)
                .start()
            
            writer = PrintWriter(process!!.outputStream, true)
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            
            // 启动输出读取
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    var line: String?
                    while (reader!!.readLine().also { line = it } != null) {
                        _outputFlow.emit(line!! + "\n")
                    }
                } catch (e: Exception) {
                    // 忽略读取错误
                }
            }
        }
    }
    
    suspend fun sendMessage(message: String) {
        withContext(Dispatchers.IO) {
            writer?.println(message)
            writer?.flush()
        }
    }
    
    fun stop() {
        writer?.close()
        reader?.close()
        process?.destroy()
    }
}