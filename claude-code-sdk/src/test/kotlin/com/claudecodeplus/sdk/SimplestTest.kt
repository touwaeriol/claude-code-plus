package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.*

class SimplestTest {
    
    @Test
    fun `test basic claude connection without initialization`() = runTest {
        println("=== 最简单的Claude连接测试 ===")
        
        // 使用最基本的配置
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet"
        )
        val client = ClaudeCodeSdkClient(options)
        
        try {
            println("正在连接到 Claude CLI...")
            
            // 连接，不发送任何初始化请求
            client.connect()
            
            println("连接状态：${client.isConnected()}")
            assertTrue(client.isConnected(), "应该成功连接到Claude")
            
            val serverInfo = client.getServerInfo()
            println("服务器信息：$serverInfo")
            
            println("✅ 基本连接测试成功！")
            
        } catch (e: Exception) {
            println("❌ 测试失败：${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            try {
                client.disconnect()
                println("已断开连接")
            } catch (e: Exception) {
                println("断开连接时出错：${e.message}")
            }
        }
    }
}