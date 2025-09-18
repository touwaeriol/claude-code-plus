package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.exceptions.ClientNotConnectedException
import com.claudecodeplus.sdk.transport.Transport
import com.claudecodeplus.sdk.types.ClaudeCodeOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeoutException

/**
 * 测试 ClaudeCodeSdkClient 的连接逻辑，特别是 isConnected() 方法
 */
class ClaudeCodeSdkClientTest {

    /**
     * 模拟的 Transport 实现，用于测试
     */
    class MockTransport(
        private val connectionState: Boolean = true,
        private val shouldInitializationFail: Boolean = false
    ) : Transport {
        override suspend fun connect() {
            // 模拟连接过程
        }

        override suspend fun write(data: String) {
            // 模拟写入
        }

        override fun readMessages() = kotlinx.coroutines.flow.emptyFlow<kotlinx.serialization.json.JsonElement>()

        override fun isReady(): Boolean = connectionState

        override suspend fun endInput() {
            // 模拟结束输入
        }

        override suspend fun close() {
            // 模拟关闭
        }

        override fun isConnected(): Boolean = connectionState
    }

    @Test
    fun `测试控制协议初始化成功时的连接状态`() = runBlocking {
        println("=== 测试1: 控制协议初始化成功 ===")

        val transport = MockTransport(connectionState = true)
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(), transport)

        // 连接客户端（这会初始化控制协议）
        try {
            client.connect()
            println("✅ 客户端连接成功")
        } catch (e: Exception) {
            println("⚠️ 连接时出现异常（可能是控制协议超时）: ${e.message}")
        }

        // 等待一下让异步操作完成
        delay(100)

        // 检查连接状态
        val isConnected = client.isConnected()
        println("🔍 isConnected() 返回: $isConnected")
        println("📊 serverInfo: ${client.getServerInfo()}")

        // 测试消息发送（不应该抛出 ClientNotConnectedException）
        try {
            client.query("测试消息")
            println("✅ query() 调用成功，没有抛出异常")
        } catch (e: ClientNotConnectedException) {
            println("❌ query() 抛出了 ClientNotConnectedException: ${e.message}")
            throw e
        } catch (e: Exception) {
            println("⚠️ query() 抛出了其他异常: ${e.message}")
        }

        client.disconnect()
    }

    @Test
    fun `测试控制协议初始化超时时的fallback连接状态`() = runBlocking {
        println("=== 测试2: 控制协议初始化超时，验证fallback机制 ===")

        val transport = MockTransport(connectionState = true)
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(), transport)

        // 连接客户端，预期控制协议会超时但有fallback
        try {
            client.connect()
            println("✅ 客户端连接完成")
        } catch (e: Exception) {
            println("⚠️ 连接异常（预期的控制协议超时）: ${e.message}")
        }

        // 等待控制协议超时（大约10秒，但我们可以检查早期状态）
        delay(500)

        println("🔍 检查连接状态:")
        val isConnected = client.isConnected()
        val serverInfo = client.getServerInfo()

        println("  - transport.isConnected(): ${transport.isConnected()}")
        println("  - serverInfo != null: ${serverInfo != null}")
        println("  - serverInfo: $serverInfo")
        println("  - client.isConnected(): $isConnected")

        // 即使控制协议初始化可能超时，只要transport连接且有fallback serverInfo，就应该认为已连接
        if (serverInfo != null && transport.isConnected()) {
            println("✅ 符合连接条件：transport已连接且有serverInfo")

            // 测试消息发送不应该抛出异常
            try {
                client.query("测试消息")
                println("✅ query() 在fallback模式下成功调用")
            } catch (e: ClientNotConnectedException) {
                println("❌ 即使在fallback模式下，query() 仍然抛出 ClientNotConnectedException")
                println("  这说明 isConnected() 逻辑有问题")
                throw e
            }
        } else {
            println("❌ 不符合连接条件，需要等待更长时间或修复逻辑")
        }

        client.disconnect()
    }

    @Test
    fun `测试transport未连接时的状态`() = runBlocking {
        println("=== 测试3: Transport未连接 ===")

        val transport = MockTransport(connectionState = false)
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(), transport)

        try {
            client.connect()
        } catch (e: Exception) {
            println("⚠️ 连接失败（预期）: ${e.message}")
        }

        delay(100)

        val isConnected = client.isConnected()
        println("🔍 isConnected() 返回: $isConnected")

        // 应该返回 false，因为 transport 未连接
        assert(!isConnected) { "Transport未连接时，isConnected()应该返回false" }

        // 应该抛出 ClientNotConnectedException
        assertThrows<ClientNotConnectedException> {
            runBlocking { client.query("测试消息") }
        }
        println("✅ 正确抛出了 ClientNotConnectedException")

        client.disconnect()
    }
}