package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * 真实的连接和发送测试（不使用 mock）
 */
class ConnectAndSendTest {

    @Test
    fun `test real connection and send message with tool usage`() = runBlocking {
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            allowedTools = listOf("Read", "Write", "Bash"),
            appendSystemPrompt = "You can use tools when needed. Be brief in responses."
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            println("🔌 正在连接到 Claude CLI...")
            client.connect()

            assertTrue(client.isConnected(), "应该成功连接")

            // 测试基本查询
            println("📝 发送基本查询...")
            client.query("What tools do you have access to? List them briefly.")

            var toolListReceived = false
            var aiResponse = ""

            withTimeout(30000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            message.content.forEach { block ->
                                when (block) {
                                    is TextBlock -> {
                                        aiResponse += block.text
                                        println("🤖 Claude: ${block.text}")
                                    }
                                    is ToolUseBlock -> {
                                        println("🔧 工具调用: ${block.name}")
                                    }
                                    else -> {
                                        // 其他块类型
                                    }
                                }
                            }
                        }
                        is ResultMessage -> {
                            toolListReceived = true
                            println("✅ 收到结果消息")
                        }
                        else -> {
                            // 其他消息类型
                        }
                    }
                }
            }

            assertTrue(toolListReceived, "应该收到工具列表")
            assertTrue(
                aiResponse.contains("Read", ignoreCase = true) ||
                aiResponse.contains("Write", ignoreCase = true) ||
                aiResponse.contains("Bash", ignoreCase = true),
                "回复应该提到可用的工具"
            )

        } finally {
            client.disconnect()
            println("🔌 已断开连接")
        }
    }

    @Test
    fun `test connection with initial prompt`() = runBlocking {
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            appendSystemPrompt = "Be very brief."
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            println("🔌 连接并发送初始提示...")
            client.connect("What is 10 divided by 2? Just the number.")

            var responseReceived = false
            var answer = ""

            withTimeout(30000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            message.content.forEach { block ->
                                if (block is TextBlock) {
                                    answer += block.text
                                }
                            }
                        }
                        is ResultMessage -> {
                            responseReceived = true
                        }
                        else -> {
                            // 其他消息类型
                        }
                    }
                }
            }

            assertTrue(responseReceived, "应该收到响应")
            assertTrue(answer.contains("5"), "答案应该包含 5")
            println("✅ 初始提示测试通过，答案: $answer")

        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `test session id in queries`() = runBlocking {
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            appendSystemPrompt = "Be brief."
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            client.connect()

            // 使用自定义 session ID
            val customSessionId = "test-session-${System.currentTimeMillis()}"
            println("📝 使用自定义会话 ID: $customSessionId")

            client.query("Hello! What's 3 + 3?", customSessionId)

            var responseReceived = false

            withTimeout(30000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            println("🤖 收到助手消息")
                        }
                        is ResultMessage -> {
                            responseReceived = true
                            // 注意：ResultMessage 中的 sessionId 可能不会反映我们发送的 customSessionId
                            println("📊 结果消息 - 会话 ID: ${message.sessionId}")
                        }
                        else -> {
                            // 其他消息类型
                        }
                    }
                }
            }

            assertTrue(responseReceived, "应该收到响应")

        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `test connection timeout handling`() = runBlocking {
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            // 使用更保守的超时设置
            appendSystemPrompt = "Respond immediately with 'OK'."
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            client.connect()
            assertTrue(client.isConnected())

            // 发送一个简单查询
            client.query("Say OK")

            var gotResponse = false

            // 使用较短的超时来测试
            withTimeout(15000) { // 15秒超时
                client.receiveResponse().collect { message ->
                    if (message is ResultMessage) {
                        gotResponse = true
                    }
                }
            }

            assertTrue(gotResponse, "应该在超时前收到响应")

        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `test message content types`() = runBlocking {
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            allowedTools = emptyList(), // 不允许工具，只测试文本响应
            appendSystemPrompt = "Provide brief text-only responses."
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            client.connect()

            client.query("What is the value of pi to 2 decimal places?")

            var foundTextBlock = false
            var foundThinkingBlock = false
            var piValue = ""

            withTimeout(30000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            message.content.forEach { block ->
                                when (block) {
                                    is TextBlock -> {
                                        foundTextBlock = true
                                        piValue += block.text
                                        println("📝 文本块: ${block.text}")
                                    }
                                    is ThinkingBlock -> {
                                        foundThinkingBlock = true
                                        println("🤔 思考块: ${block.thinking}")
                                    }
                                    else -> {
                                        println("📦 其他内容块: ${block::class.simpleName}")
                                    }
                                }
                            }
                        }
                        is ResultMessage -> {
                            println("✅ 消息接收完成")
                        }
                        else -> {
                            // 其他消息类型
                        }
                    }
                }
            }

            assertTrue(foundTextBlock, "应该包含文本块")
            assertTrue(piValue.contains("3.14"), "应该包含 π 值 3.14")
            // ThinkingBlock 可能不总是出现
            println("是否有思考块: $foundThinkingBlock")

        } finally {
            client.disconnect()
        }
    }
}