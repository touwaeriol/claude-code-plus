package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.io.*

class CliOutputTest {
    
    @Test
    fun `test cli raw output for model commands`() = runBlocking {
        println("=== Claude CLI 原始输出测试 ===")
        
        // 直接运行 Claude CLI 命令并捕获输出
        val processBuilder = ProcessBuilder().apply {
            command("claude", "--output-format", "stream-json", "--input-format", "stream-json", "--model", "claude-sonnet-4-20250514")
            redirectErrorStream(true) // 合并 stderr 到 stdout
        }
        
        try {
            println("1. 启动 Claude CLI 进程...")
            val process = processBuilder.start()
            
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            
            // 启动一个协程来读取输出
            val outputLines = mutableListOf<String>()
            val readJob = launch(Dispatchers.IO) {
                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { 
                            outputLines.add(it)
                            println("CLI 输出: $it")
                        }
                    }
                } catch (e: Exception) {
                    println("读取输出异常: ${e.message}")
                }
            }
            
            // 等待初始化
            delay(2000)
            
            println("\n2. 发送 /model opus 命令...")
            writer.write("""{"type": "user", "message": {"role": "user", "content": "/model opus"}, "session_id": "test"}""")
            writer.newLine()
            writer.flush()
            
            delay(3000)
            
            println("\n3. 发送 /compact 命令...")
            writer.write("""{"type": "user", "message": {"role": "user", "content": "/compact"}, "session_id": "test"}""")
            writer.newLine()
            writer.flush()
            
            delay(3000)
            
            println("\n4. 发送正常问题...")
            writer.write("""{"type": "user", "message": {"role": "user", "content": "What is 2+2?"}, "session_id": "test"}""")
            writer.newLine()
            writer.flush()
            
            delay(5000)
            
            // 关闭输入流
            writer.close()
            
            // 等待读取完成
            readJob.join()
            
            // 终止进程
            process.destroyForcibly()
            
            println("\n=== CLI 输出总结 ===")
            outputLines.forEachIndexed { index, line ->
                println("[$index] $line")
            }
            
            assertTrue(outputLines.isNotEmpty(), "应该收到 CLI 输出")
            
        } catch (e: Exception) {
            println("❌ 测试失败：${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}