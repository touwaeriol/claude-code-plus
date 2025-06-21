package com.claudecodeplus.test

import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.MessageType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

fun main() {
    val wrapper = ClaudeCliWrapper()
    
    // 测试1：最简单的 chat 测试
    println("=== 测试1：简单 chat ===")
    runBlocking {
        try {
            val result = wrapper.chat("What is 2+2? Just answer with the number.")
            println("结果: $result")
            println("✓ 测试通过\n")
        } catch (e: Exception) {
            println("✗ 错误: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 测试2：流式响应
    println("=== 测试2：流式响应 ===")
    runBlocking {
        try {
            val messages = mutableListOf<String>()
            wrapper.query("Say 'Hello World'").collect { message ->
                when (message.type) {
                    MessageType.TEXT -> {
                        val text = message.data.text ?: ""
                        messages.add(text)
                        print(text)
                    }
                    MessageType.ERROR -> println("错误: ${message.data.error}")
                    MessageType.START -> println("[开始]")
                    MessageType.END -> println("\n[结束]")
                    else -> {}
                }
            }
            println("收集到的消息: ${messages.joinToString("")}")
            println("✓ 测试通过\n")
        } catch (e: Exception) {
            println("✗ 错误: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 测试3：带超时的查询
    println("=== 测试3：超时测试 ===")
    runBlocking {
        try {
            withTimeout(5000) {
                val result = wrapper.chat("What is the capital of France?")
                println("结果: $result")
                println("✓ 测试通过\n")
            }
        } catch (e: Exception) {
            println("✗ 错误: ${e.message}")
        }
    }
    
    // 测试4：终止功能
    println("=== 测试4：终止功能 ===")
    runBlocking {
        try {
            println("启动长查询...")
            val job = launch {
                try {
                    wrapper.query("Count from 1 to 20 slowly").collect { message ->
                        when (message.type) {
                            MessageType.TEXT -> print(message.data.text ?: "")
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    println("\n查询被中断: ${e.message}")
                }
            }
            
            // 等待1秒后终止
            delay(1000)
            println("\n\n终止查询...")
            val terminated = wrapper.terminate()
            println("终止成功: $terminated")
            
            job.cancel()
            delay(100)
            
            println("进程是否运行: ${wrapper.isRunning()}")
            println("✓ 测试通过\n")
        } catch (e: Exception) {
            println("✗ 错误: ${e.message}")
            e.printStackTrace()
        }
    }
    
    println("=== 所有测试完成 ===")
}