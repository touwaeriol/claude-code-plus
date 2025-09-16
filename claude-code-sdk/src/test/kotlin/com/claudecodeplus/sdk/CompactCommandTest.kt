package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * 测试 /compact 命令的解析和处理
 * /compact 命令用于压缩对话历史，减少上下文长度
 */
class CompactCommandTest {

    @Test
    fun `test compact command after multiple messages`() = runBlocking {
        println("=== 测试多消息后的 /compact 命令 ===")

        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            appendSystemPrompt = "Be very brief in your responses."
        )
        val client = ClaudeCodeSdkClient(options)

        try {
            println("1. 连接到 Claude CLI...")
            client.connect()
            assertTrue(client.isConnected(), "应该成功连接到Claude")

            // 发送第一个消息
            println("\n2. 发送第一个消息...")
            client.query("What is 2 + 2? Just the number.")

            var firstResponse = ""
            withTimeout(30000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            message.content.forEach { block ->
                                if (block is TextBlock) {
                                    firstResponse += block.text
                                    println("Claude 回复 #1: ${block.text}")
                                }
                            }
                        }
                        is ResultMessage -> {
                            println("第一个消息完成")
                        }
                        else -> {}
                    }
                }
            }

            assertTrue(firstResponse.contains("4"), "第一个回复应该包含 4")

            // 发送第二个消息
            println("\n3. 发送第二个消息...")
            client.query("What is 3 + 3? Just the number.")

            var secondResponse = ""
            withTimeout(30000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            message.content.forEach { block ->
                                if (block is TextBlock) {
                                    secondResponse += block.text
                                    println("Claude 回复 #2: ${block.text}")
                                }
                            }
                        }
                        is ResultMessage -> {
                            println("第二个消息完成")
                        }
                        else -> {}
                    }
                }
            }

            assertTrue(secondResponse.contains("6"), "第二个回复应该包含 6")

            // 发送 /compact 命令
            println("\n4. 发送 /compact 命令...")
            client.query("/compact")

            // 收集 /compact 的响应
            val compactMessages = mutableListOf<Message>()
            var hasSystemMessage = false
            var hasCompactResult = false
            var compactInfo = ""

            withTimeout(30000) {
                client.receiveResponse().collect { message ->
                    compactMessages.add(message)
                    println("收到消息类型: ${message::class.simpleName}")

                    when (message) {
                        is SystemMessage -> {
                            hasSystemMessage = true
                            println("系统消息: subtype=${message.subtype}, data=${message.data}")

                            // /compact 通常返回 compact 或 command 类型的系统消息
                            if (message.subtype == "compact" || message.subtype == "command") {
                                hasCompactResult = true
                                compactInfo = message.data.toString()
                            }
                        }
                        is AssistantMessage -> {
                            val text = message.content.filterIsInstance<TextBlock>()
                                .joinToString("") { it.text }
                            println("Claude 回复: $text")

                            // 有时 /compact 也会有文字回复
                            if (text.contains("compact", ignoreCase = true) ||
                                text.contains("conversation", ignoreCase = true)) {
                                hasCompactResult = true
                            }
                        }
                        is ResultMessage -> {
                            println("结果消息: ${message.subtype}, isError=${message.isError}")
                            if (message.subtype == "command" || !message.isError) {
                                hasCompactResult = true
                            }
                        }
                        is UserMessage -> {
                            println("用户消息: ${message.content}")
                        }
                        else -> {
                            println("其他消息: $message")
                        }
                    }
                }
            }

            // 验证结果
            println("\n5. 验证 /compact 命令结果:")
            println("  - 收到 ${compactMessages.size} 条消息")
            println("  - 有系统消息: $hasSystemMessage")
            println("  - 有 compact 结果: $hasCompactResult")

            if (compactInfo.isNotEmpty()) {
                println("  - Compact 信息: $compactInfo")
            }

            // 断言验证
            assertTrue(compactMessages.isNotEmpty(), "/compact 应该返回消息")
            assertTrue(
                hasSystemMessage || hasCompactResult,
                "/compact 应该返回系统消息或 compact 结果"
            )

            // 验证解析器能正确处理不同类型的消息
            val messageTypes = compactMessages.map { it::class.simpleName }.toSet()
            println("\n6. 解析器处理的消息类型: $messageTypes")

            println("\n✅ /compact 命令测试成功完成")

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

    @Test
    fun `test message parser handles different message types`() = runBlocking {
        println("=== 测试消息解析器对不同消息类型的处理 ===")

        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            appendSystemPrompt = "Be brief."
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            client.connect()

            // 测试各种命令
            val commands = listOf(
                "/compact" to "压缩对话",
                "/help" to "帮助信息",
                "/model" to "模型信息"
            )

            for ((command, description) in commands) {
                println("\n测试命令: $command ($description)")
                client.query(command)

                var messageCount = 0
                val messageTypes = mutableSetOf<String>()

                withTimeout(15000) {
                    try {
                        client.receiveResponse().collect { message ->
                            messageCount++
                            messageTypes.add(message::class.simpleName ?: "Unknown")

                            when (message) {
                                is SystemMessage -> {
                                    println("  系统消息: ${message.subtype}")
                                }
                                is AssistantMessage -> {
                                    val preview = message.content
                                        .filterIsInstance<TextBlock>()
                                        .firstOrNull()?.text?.take(50) ?: ""
                                    println("  助手消息: $preview...")
                                }
                                is ResultMessage -> {
                                    println("  结果: ${message.subtype}, error=${message.isError}")
                                }
                                else -> {
                                    println("  其他: ${message::class.simpleName}")
                                }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        println("  (超时，可能命令不被支持)")
                    }
                }

                println("  收到 $messageCount 条消息，类型: $messageTypes")
                assertTrue(messageCount > 0, "$command 应该返回至少一条消息")
            }

            println("\n✅ 消息解析器测试完成")

        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `test parser robustness with edge cases`() = runBlocking {
        println("=== 测试解析器的健壮性 ===")

        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022"
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            client.connect()

            // 测试边缘情况
            val edgeCases = listOf(
                "" to "空消息",
                "/" to "单斜杠",
                "/unknown_command_xyz" to "未知命令",
                "normal message /compact" to "消息中包含命令",
                "/Compact" to "大写命令",
                "  /compact  " to "带空格的命令"
            )

            for ((input, description) in edgeCases) {
                println("\n测试: $description - 输入: '$input'")

                try {
                    client.query(input)

                    var hasResponse = false
                    withTimeout(10000) {
                        client.receiveResponse().collect { message ->
                            hasResponse = true
                            println("  收到: ${message::class.simpleName}")

                            if (message is ResultMessage && message.isError) {
                                println("  错误结果: ${message.result}")
                            }
                        }
                    }

                    assertTrue(hasResponse, "应该收到响应")

                } catch (e: Exception) {
                    println("  处理异常（预期的）: ${e.message}")
                }
            }

            println("\n✅ 健壮性测试完成")

        } finally {
            client.disconnect()
        }
    }
}