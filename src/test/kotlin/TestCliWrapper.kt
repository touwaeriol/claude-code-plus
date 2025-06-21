package com.claudecodeplus.test

import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.MessageType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

fun main() {
    println("测试 ClaudeCliWrapper...")
    println("=" .repeat(50))
    
    val wrapper = ClaudeCliWrapper()
    
    // 测试1：简单 chat 方法
    println("\n测试1：使用 chat 方法")
    runBlocking {
        try {
            val response = wrapper.chat("What is 2+2?")
            println("响应: $response")
        } catch (e: Exception) {
            println("错误: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 测试2：使用 query 方法和流式响应
    println("\n测试2：使用 query 方法（流式响应）")
    runBlocking {
        try {
            wrapper.query("Tell me a very short joke").collect { message ->
                when (message.type) {
                    MessageType.TEXT -> print(message.data.text ?: "")
                    MessageType.ERROR -> println("\n错误: ${message.data.error}")
                    MessageType.START -> println("开始响应...")
                    MessageType.END -> println("\n响应结束")
                    MessageType.TOOL_USE -> println("\n使用工具: ${message.data.toolName}")
                    MessageType.TOOL_RESULT -> println("工具结果")
                }
            }
        } catch (e: Exception) {
            println("\n错误: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 测试3：带选项的查询
    println("\n\n测试3：带自定义选项")
    runBlocking {
        try {
            val options = ClaudeCliWrapper.QueryOptions(
                model = "claude-3-5-sonnet-20241022",
                maxTurns = 1
            )
            
            wrapper.query("What is the capital of France?", options).collect { message ->
                when (message.type) {
                    MessageType.TEXT -> print(message.data.text ?: "")
                    MessageType.ERROR -> println("\n错误: ${message.data.error}")
                    else -> {} // 忽略其他类型
                }
            }
            println()
        } catch (e: Exception) {
            println("错误: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 测试4：权限模式测试
    println("\n测试4：测试权限模式")
    runBlocking {
        try {
            val options = ClaudeCliWrapper.QueryOptions(
                permissionMode = "skip",
                allowedTools = listOf("Read", "Write")
            )
            
            wrapper.query("List files in current directory", options).collect { message ->
                when (message.type) {
                    MessageType.TEXT -> print(message.data.text ?: "")
                    MessageType.TOOL_USE -> println("\n工具使用: ${message.data.toolName}")
                    MessageType.ERROR -> println("\n错误: ${message.data.error}")
                    else -> {}
                }
            }
            println()
        } catch (e: Exception) {
            println("错误: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 测试5：终止响应功能
    println("\n测试5：测试终止响应功能")
    runBlocking {
        try {
            // 启动一个长时间运行的查询
            val job = kotlinx.coroutines.launch {
                wrapper.query("Count from 1 to 100 slowly, with detailed explanations for each number").collect { message ->
                    when (message.type) {
                        MessageType.TEXT -> print(message.data.text ?: "")
                        MessageType.ERROR -> println("\n错误: ${message.data.error}")
                        else -> {}
                    }
                }
            }
            
            // 等待2秒后终止
            kotlinx.coroutines.delay(2000)
            println("\n\n正在终止响应...")
            
            val terminated = wrapper.terminate()
            println("终止结果: $terminated")
            
            // 取消协程
            job.cancel()
            
            // 验证进程已停止
            println("进程是否还在运行: ${wrapper.isRunning()}")
            
        } catch (e: Exception) {
            println("测试终止功能时出错: ${e.message}")
        }
    }
    
    println("\n测试完成!")
}