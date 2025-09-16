package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.exceptions.ClientNotConnectedException
import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * 真实的 SDK 客户端测试（不使用 mock）
 */
class SimpleClientTest {

    @Test
    fun `test client initial state`() {
        val options = ClaudeCodeOptions(model = "claude-3-5-sonnet-20241022")
        val client = ClaudeCodeSdkClient(options)

        assertFalse(client.isConnected())
        assertNull(client.getServerInfo())
    }

    @Test
    fun `test query without connection throws exception`() = runTest {
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions())

        assertFailsWith<ClientNotConnectedException> {
            client.query("Hello")
        }
    }

    @Test
    fun `test interrupt without connection throws exception`() = runTest {
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions())

        assertFailsWith<ClientNotConnectedException> {
            client.interrupt()
        }
    }

    @Test
    fun `test real connection and simple query`() = runBlocking {
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            allowedTools = listOf("Read", "Write"),
            appendSystemPrompt = "Keep your responses very brief."
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            // 连接到真实的 Claude CLI
            println("🔌 正在连接到 Claude CLI...")
            client.connect()

            assertTrue(client.isConnected(), "应该成功连接到 Claude")

            val serverInfo = client.getServerInfo()
            assertNotNull(serverInfo, "应该获取到服务器信息")
            println("📋 服务器信息: $serverInfo")

            // 发送一个简单的数学问题
            val question = "What is 2 + 2? Answer only with the number."
            println("🗣️ 发送问题: $question")

            client.query(question)

            var aiResponse = ""
            var responseReceived = false

            withTimeout(30000) { // 30秒超时
                client.receiveResponse().collect { message ->
                    println("📨 收到消息类型: ${message::class.simpleName}")

                    when (message) {
                        is AssistantMessage -> {
                            message.content.forEach { block ->
                                if (block is TextBlock) {
                                    aiResponse += block.text
                                    println("🤖 Claude: ${block.text}")
                                }
                            }
                        }
                        is ResultMessage -> {
                            println("📊 结果消息: ${message.subtype}")
                            responseReceived = true
                        }
                        else -> {
                            println("📬 其他消息: ${message::class.simpleName}")
                        }
                    }
                }
            }

            assertTrue(responseReceived, "应该收到响应")
            assertTrue(aiResponse.contains("4"), "回复应该包含数字 4")

        } catch (e: Exception) {
            println("❌ 测试失败: ${e.message}")
            throw e
        } finally {
            client.disconnect()
            assertFalse(client.isConnected(), "断开连接后应该显示未连接")
            println("🔌 已断开连接")
        }
    }

    @Test
    fun `test use extension function`() = runBlocking {
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            appendSystemPrompt = "Keep your responses very brief."
        )

        val client = ClaudeCodeSdkClient(options)

        val result = client.use {
            assertTrue(isConnected(), "在 use 块中应该自动连接")

            query("What is the capital of France? Answer with just the city name.")

            var response = ""
            receiveResponse().collect { message ->
                if (message is AssistantMessage) {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            response += block.text
                        }
                    }
                }
            }

            assertTrue(response.contains("Paris"), "回复应该包含 Paris")
            response
        }

        assertFalse(client.isConnected(), "use 块结束后应该自动断开连接")
        println("✅ use 扩展函数测试通过，回复: $result")
    }

    @Test
    fun `test simpleQuery convenience function`() = runBlocking {
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            appendSystemPrompt = "Keep your responses very brief."
        )

        val client = ClaudeCodeSdkClient(options)

        val messages = client.simpleQuery("Is 3 a prime number? Answer with just yes or no.")

        assertTrue(messages.isNotEmpty(), "应该收到消息")

        var foundAnswer = false
        messages.forEach { message ->
            if (message is AssistantMessage) {
                message.content.forEach { block ->
                    if (block is TextBlock) {
                        val text = block.text.lowercase()
                        if (text.contains("yes")) {
                            foundAnswer = true
                        }
                    }
                }
            }
        }

        assertTrue(foundAnswer, "应该收到包含 'yes' 的回答")
        println("✅ simpleQuery 测试通过")
    }

    @Test
    fun `test claudeQuery top-level function`() = runBlocking {
        val messages = claudeQuery(
            prompt = "What is 5 * 5? Answer with just the number.",
            options = ClaudeCodeOptions(
                model = "claude-3-5-sonnet-20241022",
                appendSystemPrompt = "Keep your responses very brief."
            )
        )

        assertTrue(messages.isNotEmpty(), "应该收到消息")

        var foundAnswer = false
        messages.forEach { message ->
            if (message is AssistantMessage) {
                message.content.forEach { block ->
                    if (block is TextBlock && block.text.contains("25")) {
                        foundAnswer = true
                    }
                }
            }
        }

        assertTrue(foundAnswer, "应该收到包含 25 的回答")
        println("✅ claudeQuery 顶级函数测试通过")
    }

    @Test
    fun `test claudeCodeSdkClient helper function`() = runBlocking {
        val client = claudeCodeSdkClient(
            ClaudeCodeOptions(
                model = "claude-3-5-sonnet-20241022",
                allowedTools = listOf("Read"),
                appendSystemPrompt = "Be concise."
            )
        )

        assertNotNull(client, "应该成功创建客户端")
        // 注意：options 是私有的，不能直接访问
        // 可以通过实际使用来验证配置是否正确

        // 测试实际使用
        client.use {
            query("What is 1 + 1? Just the number.")

            var response = ""
            receiveResponse().collect { message ->
                if (message is AssistantMessage) {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            response += block.text
                        }
                    }
                }
            }

            assertTrue(response.contains("2"), "回复应该包含 2")
        }

        println("✅ claudeCodeSdkClient builder 测试通过")
    }

    @Test
    fun `test multiple queries in same session`() = runBlocking {
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            appendSystemPrompt = "Keep responses very brief."
        )

        val client = ClaudeCodeSdkClient(options)

        client.use {
            // 第一个问题
            query("Remember the number 42. What number did I just tell you?")

            var firstResponse = ""
            receiveResponse().collect { message ->
                if (message is AssistantMessage) {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            firstResponse += block.text
                        }
                    }
                }
            }

            assertTrue(firstResponse.contains("42"), "第一个回复应该包含 42")

            // 第二个问题（测试上下文记忆）
            query("What was the number I asked you to remember? Just the number.")

            var secondResponse = ""
            receiveResponse().collect { message ->
                if (message is AssistantMessage) {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            secondResponse += block.text
                        }
                    }
                }
            }

            assertTrue(secondResponse.contains("42"), "第二个回复应该记住 42")
        }

        println("✅ 多次查询测试通过")
    }
}