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
        // 直接执行Claude CLI命令，关键是要正确管理输入流
        println("[ProcessHandler] 执行命令: ${command.joinToString(" ")}")
        
        val processBuilder = ProcessBuilder(command)
            .directory(File(workingDirectory))
            .redirectErrorStream(false) // 分离 stdout 和 stderr
        
        // 确保环境变量正确传递（特别是PATH）
        val env = processBuilder.environment()
        env["PATH"] = System.getenv("PATH") ?: ""
        env["HOME"] = System.getenv("HOME") ?: ""
        // 设置非交互模式环境变量
        env["TERM"] = "dumb"
        env["FORCE_COLOR"] = "0"
        // 确保输出编码为UTF-8
        env["LANG"] = "en_US.UTF-8"
        env["LC_ALL"] = "en_US.UTF-8"
        
        println("[ProcessHandler] 命令: ${command.joinToString(" ")}")
        println("[ProcessHandler] 工作目录: $workingDirectory")
        println("[ProcessHandler] PATH: ${env["PATH"]}")
        println("[ProcessHandler] HOME: ${env["HOME"]}")
        
        val process = processBuilder.start()
        
        // 获取进程 PID 用于日志记录（模仿 Claudia）
        val pid = process.pid()
        println("Spawned Claude process with PID: $pid")
        
        // 🔑 关键修复：立即关闭输入流解决Claude CLI输出读取问题
        // 
        // 问题原因：Claude CLI在非TTY环境（如ProcessBuilder）中使用不同的输出策略
        // - 在真实终端中：使用行缓冲，每行立即输出
        // - 在ProcessBuilder中：等待stdin关闭信号才开始输出，避免与交互式输入混淆
        // 
        // 解决方案：立即关闭输入流(stdin)，明确告知Claude CLI "没有更多输入，可以开始处理"
        // 这样Claude CLI会立即开始输出JSONL格式的响应，而不是无限等待更多输入
        // 
        // 验证结果：
        // ✅ 无需复杂的伪终端包装 (script -q /dev/null)  
        // ✅ 无需非阻塞轮询读取
        // ✅ 标准BufferedReader.readLine()完美工作
        // ✅ 跨平台兼容(Windows/Linux/macOS)
        println("[ProcessHandler] 关闭输入流，通知Claude CLI开始处理...")
        process.outputStream.close()
        
        // 注册进程到监控器
        val trackingId = ProcessMonitor.instance.registerProcess(
            sessionId = sessionId,
            process = process,
            projectPath = workingDirectory
        )
        
        // 启动 stdout 监听协程 - 使用传统BufferedReader，输入流关闭后正常工作
        launch {
            try {
                println("[ProcessHandler] 开始监听 stdout...")
                
                // 使用 Scanner 进行实时行读取，避免 BufferedReader 的缓冲延迟
                java.util.Scanner(process.inputStream, "UTF-8").use { scanner ->
                    var lineCount = 0
                    val startTime = System.currentTimeMillis()
                    
                    println("[ProcessHandler] Scanner 创建成功，开始实时逐行读取...")
                    
                    while (scanner.hasNextLine()) {
                        val currentLine = scanner.nextLine()
                        if (currentLine.isNotBlank()) {
                            lineCount++
                            println("[ProcessHandler] stdout 实时第${lineCount}行: $currentLine")
                            
                            // 立即处理这一行，实现真正的实时输出
                            onOutput(currentLine.trim())
                        }
                    }
                    
                    val duration = System.currentTimeMillis() - startTime
                    println("[ProcessHandler] stdout Scanner读取完成，总共${lineCount}行，耗时${duration}ms")
                }
                println("[ProcessHandler] stdout 流结束")
            } catch (e: Exception) {
                if (e.message?.contains("Stream closed") != true) {
                    println("[ProcessHandler] ❌ Error reading stdout: ${e.message}")
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
                            println("[ProcessHandler] stderr 输出: $line")
                            
                            // 检查是否是用户中断消息
                            if (line.contains("Request interrupted by user", ignoreCase = true) ||
                                line.contains("interrupted", ignoreCase = true)) {
                                println("[ProcessHandler] 检测到用户中断请求")
                                // 用户中断不作为错误处理，而是正常的操作结果
                                onOutput("用户已中断请求")
                            } else {
                                onError(line)
                            }
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
                val startWaitTime = System.currentTimeMillis()
                val exitCode = process.waitFor()
                val success = exitCode == 0
                val waitTime = System.currentTimeMillis() - startWaitTime
                
                if (success) {
                    println("[ProcessHandler] ✅ Claude process finished successfully with exit code: $exitCode, 运行时间: ${waitTime}ms")
                } else {
                    println("[ProcessHandler] ❌ Claude process failed with exit code: $exitCode, 运行时间: ${waitTime}ms")
                }
                
                // 检查进程是否产生了输出
                if (process.inputStream.available() > 0) {
                    println("[ProcessHandler] ⚠️ 进程结束时仍有未读输出: ${process.inputStream.available()} bytes")
                }
                
                onComplete(success)
                
                // 进程结束后自动清理（进程监控器会自动处理，这里是双保险）
                ProcessMonitor.instance.terminateProcess(trackingId, forceful = false)
            } catch (e: Exception) {
                println("[ProcessHandler] ❌ Error waiting for process: ${e.message}")
                e.printStackTrace()
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