package com.claudecodeplus.core

import com.pty4j.PtyProcessBuilder
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 手动测试 Claude 的交互方式
 * 
 * 运行这个程序后，它会启动 Claude 并让你手动交互
 * 用于理解 Claude 的真实工作方式
 */
fun main() {
    println("=== Claude 手动交互测试 ===")
    println("这个程序将启动 Claude，让你可以手动测试交互")
    println()
    
    try {
        // 创建 PTY 进程
        val builder = PtyProcessBuilder()
            .setCommand(arrayOf("claude"))
            .setRedirectErrorStream(true)
            .setConsole(true)
            .setInitialColumns(120)
            .setInitialRows(40)
        
        // 设置环境
        val env = mutableMapOf<String, String>()
        env.putAll(System.getenv())
        env["TERM"] = "xterm-256color"
        env["LANG"] = System.getenv("LANG") ?: "en_US.UTF-8"
        builder.setEnvironment(env)
        
        val process = builder.start()
        
        println("Claude 已启动 (PID: ${process.pid()})")
        println("=".repeat(50))
        println()
        
        // 创建读写器
        val reader = InputStreamReader(process.inputStream)
        val writer = OutputStreamWriter(process.outputStream)
        val userInput = System.`in`.bufferedReader()
        
        // 启动输出线程
        Thread {
            val buffer = CharArray(1024)
            while (true) {
                try {
                    val count = reader.read(buffer)
                    if (count > 0) {
                        print(String(buffer, 0, count))
                        System.out.flush()
                    } else if (count < 0) {
                        break
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }.start()
        
        // 启动输入线程
        Thread {
            println("\n提示：")
            println("- 输入文本后按回车")
            println("- 输入 'EXIT' 退出程序")
            println("- 观察 Claude 如何响应你的输入")
            println()
            
            while (true) {
                try {
                    val line = userInput.readLine() ?: break
                    if (line == "EXIT") {
                        break
                    }
                    
                    // 发送用户输入
                    writer.write(line + "\n")
                    writer.flush()
                    
                    // 记录发送的内容
                    println("[已发送]: $line")
                    
                } catch (e: Exception) {
                    println("输入错误: ${e.message}")
                    break
                }
            }
        }.start()
        
        // 等待进程结束
        process.waitFor()
        
    } catch (e: Exception) {
        println("错误: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * 测试快捷键
 */
fun testShortcuts() {
    println("\n=== 测试 Claude 快捷键 ===")
    println()
    println("常见的终端提交方式：")
    println("1. Enter - 提交单行")
    println("2. Ctrl+D - EOF信号")
    println("3. Ctrl+Enter - 某些应用的提交快捷键")
    println("4. Tab - 自动补全或切换")
    println("5. Shift+Enter - 换行不提交")
    println()
    println("请在 Claude 中测试这些按键")
}