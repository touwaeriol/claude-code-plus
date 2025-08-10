package com.claudecodeplus.sdk

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Claude CLI 进程事件处理器
 * 负责启动进程、监听输出流、解析消息并分发事件
 * 完全符合 Claudia 项目的进程管理模式
 */
class ClaudeProcessEventHandler {
    
    /**
     * 启动 Claude CLI 进程并监听事件流
     * 完全模仿 Claudia 的 spawn_claude_process 函数
     * 
     * @param command Claude CLI 命令参数列表
     * @param workingDirectory 工作目录
     * @param sessionId 会话ID（用于进程跟踪）
     * @param onOutput stdout 消息回调
     * @param onError stderr 消息回调  
     * @param onComplete 进程完成回调
     * @return 启动的进程实例
     */
    suspend fun executeWithEvents(
        command: List<String>,
        workingDirectory: String,
        sessionId: String? = null,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: (Boolean) -> Unit
    ): Process = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(command)
            .directory(File(workingDirectory))
            .redirectErrorStream(false) // 分离 stdout 和 stderr
        
        // 确保环境变量正确传递（特别是PATH）
        val env = processBuilder.environment()
        env["PATH"] = System.getenv("PATH") ?: ""
        env["HOME"] = System.getenv("HOME") ?: ""
        
        println("[ProcessHandler] 命令: ${command.joinToString(" ")}")
        println("[ProcessHandler] 工作目录: $workingDirectory")
        println("[ProcessHandler] PATH: ${env["PATH"]}")
        println("[ProcessHandler] HOME: ${env["HOME"]}")
        
        val process = processBuilder.start()
        
        // 获取进程 PID 用于日志记录（模仿 Claudia）
        val pid = process.pid()
        println("Spawned Claude process with PID: $pid")
        
        // 注册进程到监控器
        val trackingId = ProcessMonitor.instance.registerProcess(
            sessionId = sessionId,
            process = process,
            projectPath = workingDirectory
        )
        
        // 启动 stdout 监听协程
        launch {
            try {
                println("[ProcessHandler] 开始监听 stdout...")
                process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { currentLine ->
                            println("[ProcessHandler] stdout 原始输出: $currentLine")
                            if (currentLine.isNotBlank()) {
                                println("[ProcessHandler] 调用 onOutput 回调")
                                onOutput(currentLine)
                            }
                        }
                    }
                }
                println("[ProcessHandler] stdout 流结束")
            } catch (e: Exception) {
                if (e.message?.contains("Stream closed") != true) {
                    println("[ProcessHandler] Error reading stdout: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        
        // 启动 stderr 监听协程
        launch {
            try {
                process.errorStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isNotBlank()) {
                            onError(line)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e.message?.contains("Stream closed") != true) {
                    println("Error reading stderr: ${e.message}")
                }
            }
        }
        
        // 启动进程等待协程
        launch {
            try {
                println("[ProcessHandler] 等待进程完成...")
                val exitCode = process.waitFor()
                val success = exitCode == 0
                println("[ProcessHandler] Claude process finished with exit code: $exitCode")
                onComplete(success)
                
                // 进程结束后自动清理（进程监控器会自动处理，这里是双保险）
                ProcessMonitor.instance.terminateProcess(trackingId, forceful = false)
            } catch (e: Exception) {
                println("Error waiting for process: ${e.message}")
                onComplete(false)
                ProcessMonitor.instance.terminateProcess(trackingId, forceful = true)
            }
        }
        
        process
    }
    
    /**
     * 终止进程
     * 提供强制终止和优雅终止两种方式
     */
    fun terminateProcess(process: Process, forceful: Boolean = false) {
        try {
            if (forceful) {
                process.destroyForcibly()
                println("Forcefully terminated Claude process PID: ${process.pid()}")
            } else {
                process.destroy()
                println("Gracefully terminated Claude process PID: ${process.pid()}")
            }
        } catch (e: Exception) {
            println("Error terminating process: ${e.message}")
        }
    }
}