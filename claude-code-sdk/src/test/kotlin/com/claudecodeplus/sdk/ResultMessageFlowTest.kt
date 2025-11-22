package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

/**
 * 测试 ResultMessage 在 stream-json 模式下的处理
 *
 * 验证点：
 * 1. 一次请求可能包含多轮 API 调用（工具调用后继续）
 * 2. 每轮调用会有 message_stop 事件，但不代表请求结束
 * 3. 只有 ResultMessage 才表示整个请求结束
 */
class ResultMessageFlowTest {

    @Test
    fun `test ResultMessage is received after all turns complete`() = runBlocking {
        // 创建启用 stream-json 的客户端
        val client = ClaudeCodeSdkClient(
            ClaudeCodeOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 5,  // 允许多轮
                includePartialMessages = true,
                dangerouslySkipPermissions = true,
                verbose = true  // 必须与 stream-json 一起使用
            )
        )

        try {
            client.connect()

            // 发送一个可能触发工具调用的请求
            client.query("请读取 settings.gradle.kts 文件的前5行")

            var messageStopCount = 0
            var resultMessageReceived = false
            var resultMessage: ResultMessage? = null
            var assistantMessageCount = 0

            withTimeout(120_000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is StreamEvent -> {
                            // 解析内部事件类型
                            val eventType = message.event.toString()
                            if (eventType.contains("message_stop")) {
                                messageStopCount++
                                println("📍 收到 message_stop 事件 #$messageStopCount")
                            }
                        }

                        is AssistantMessage -> {
                            assistantMessageCount++
                            println("🤖 收到 AssistantMessage #$assistantMessageCount")
                            message.content.forEach { block ->
                                when (block) {
                                    is TextBlock -> println("   - Text: ${block.text.take(50)}...")
                                    is ToolUseBlock -> println("   - ToolUse: ${block.name}")
                                    else -> println("   - ${block::class.simpleName}")
                                }
                            }
                        }

                        is ResultMessage -> {
                            resultMessageReceived = true
                            resultMessage = message
                            println("✅ 收到 ResultMessage:")
                            println("   - subtype: ${message.subtype}")
                            println("   - is_error: ${message.isError}")
                            println("   - num_turns: ${message.numTurns}")
                            println("   - duration_ms: ${message.durationMs}")
                            println("   - session_id: ${message.sessionId}")
                        }

                        is UserMessage -> {
                            println("👤 收到 UserMessage (tool_result)")
                        }

                        else -> {
                            println("❓ 其他消息: ${message::class.simpleName}")
                        }
                    }
                }
            }

            // 验证
            println("\n=== 测试结果 ===")
            println("message_stop 事件数量: $messageStopCount")
            println("AssistantMessage 数量: $assistantMessageCount")
            println("ResultMessage 收到: $resultMessageReceived")

            assertTrue(resultMessageReceived, "必须收到 ResultMessage 表示请求结束")
            assertNotNull(resultMessage, "ResultMessage 不能为 null")

            // 如果有工具调用，应该有多个 message_stop
            if (resultMessage!!.numTurns > 1) {
                println("✅ 多轮调用场景: ${resultMessage!!.numTurns} 轮")
                // 注意：message_stop 数量可能等于或少于 numTurns
                // 因为 SDK 可能会过滤某些中间事件
            }

            println("✅ 测试通过")

        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `test ResultMessage contains usage statistics`() = runBlocking {
        val client = ClaudeCodeSdkClient(
            ClaudeCodeOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 1,
                includePartialMessages = true,
                dangerouslySkipPermissions = true,
                verbose = true  // 必须与 stream-json 一起使用
            )
        )

        try {
            client.connect()
            client.query("说 'Hello'")

            var resultMessage: ResultMessage? = null

            withTimeout(30_000) {
                client.receiveResponse().collect { message ->
                    if (message is ResultMessage) {
                        resultMessage = message
                    }
                }
            }

            assertNotNull(resultMessage, "必须收到 ResultMessage")

            println("=== ResultMessage 统计信息 ===")
            println("duration_ms: ${resultMessage!!.durationMs}")
            println("duration_api_ms: ${resultMessage!!.durationApiMs}")
            println("num_turns: ${resultMessage!!.numTurns}")
            println("total_cost_usd: ${resultMessage!!.totalCostUsd}")
            println("usage: ${resultMessage!!.usage}")

            // 验证基本字段
            assertTrue(resultMessage!!.durationMs > 0, "duration_ms 应该大于 0")
            assertEquals("success", resultMessage!!.subtype, "subtype 应该是 success")

            println("✅ 测试通过")

        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `test message flow order in stream-json mode`() = runBlocking {
        val client = ClaudeCodeSdkClient(
            ClaudeCodeOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 2,
                includePartialMessages = true,
                dangerouslySkipPermissions = true,
                verbose = true  // 必须与 stream-json 一起使用
            )
        )

        try {
            client.connect()
            client.query("当前目录下有什么文件？列出前3个")

            val messageOrder = mutableListOf<String>()

            withTimeout(60_000) {
                client.receiveResponse().collect { message ->
                    val type = when (message) {
                        is StreamEvent -> {
                            val eventType = try {
                                message.event.toString().let { str ->
                                    when {
                                        str.contains("message_start") -> "stream:message_start"
                                        str.contains("message_stop") -> "stream:message_stop"
                                        str.contains("content_block_start") -> "stream:content_block_start"
                                        str.contains("content_block_delta") -> "stream:content_block_delta"
                                        str.contains("content_block_stop") -> "stream:content_block_stop"
                                        else -> "stream:other"
                                    }
                                }
                            } catch (e: Exception) {
                                "stream:unknown"
                            }
                            eventType
                        }
                        is AssistantMessage -> "AssistantMessage"
                        is UserMessage -> "UserMessage"
                        is ResultMessage -> "ResultMessage"
                        is SystemMessage -> "SystemMessage"
                        else -> message::class.simpleName ?: "Unknown"
                    }
                    messageOrder.add(type)
                }
            }

            println("=== 消息顺序 ===")
            messageOrder.forEachIndexed { index, type ->
                println("$index: $type")
            }

            // 验证 ResultMessage 是最后一个
            val lastMessage = messageOrder.lastOrNull()
            assertEquals("ResultMessage", lastMessage, "ResultMessage 应该是最后一个消息")

            println("✅ 测试通过：ResultMessage 确实是最后一个消息")

        } finally {
            client.disconnect()
        }
    }
}
