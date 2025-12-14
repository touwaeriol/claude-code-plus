package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 测试 SDK 能够输出 StreamEvent
 * 
 * 当 includePartialMessages = true 时，SDK 应该发送 StreamEvent 消息
 * 用于实时渲染 Claude 的回复
 */
class StreamEventTest {

    private lateinit var client: ClaudeCodeSdkClient
    private lateinit var sessionId: String

    @BeforeEach
    fun setUp() {
        sessionId = "test-stream-event-${UUID.randomUUID()}"
        client = ClaudeCodeSdkClient(
            ClaudeAgentOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 1,
                includePartialMessages = true  // 启用流式输出
            )
        )
        runBlocking { client.connect() }
    }

    @AfterEach
    fun tearDown() {
        runBlocking {
            if (this@StreamEventTest::client.isInitialized) {
                client.disconnect()
            }
        }
    }

    @Test
    fun `test SDK outputs StreamEvent when includePartialMessages is true`() = runBlocking {
        var streamEventReceived = false
        var assistantMessageReceived = false
        var resultMessageReceived = false

        println("\n=== 测试 StreamEvent 输出 ===")
        println("发送查询: '请用一句话解释什么是 Kotlin'")

        // 发送查询
        client.query(
            prompt = "请用一句话解释什么是 Kotlin",
            sessionId = sessionId
        )

        // 收集消息流
        client.receiveResponse().collect { message ->
            when (message) {
                is StreamEvent -> {
                    streamEventReceived = true
                    println("✅ 收到 StreamEvent:")
                    println("   - UUID: ${message.uuid}")
                    println("   - Session ID: ${message.sessionId}")
                    println("   - Event: ${message.event}")
                    message.parentToolUseId?.let {
                        println("   - Parent Tool Use ID: $it")
                    }
                }

                is AssistantMessage -> {
                    assistantMessageReceived = true
                    println("✅ 收到 AssistantMessage:")
                    println("   - Model: ${message.model}")
                    println("   - Content blocks: ${message.content.size}")
                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> println("     - Text: ${block.text.take(50)}...")
                            is ThinkingBlock -> println("     - Thinking: ${block.thinking.take(50)}...")
                            else -> println("     - ${block::class.simpleName}")
                        }
                    }
                }

                is ResultMessage -> {
                    resultMessageReceived = true
                    println("✅ 收到 ResultMessage:")
                    println("   - Status: ${if (message.isError) "Error" else "Success"}")
                    println("   - Turns: ${message.numTurns}")
                    println("   - Duration: ${message.durationMs}ms")
                }

                is UserMessage -> {
                    println("📤 收到 UserMessage")
                }

                is SystemMessage -> {
                    println("🔧 收到 SystemMessage (已过滤)")
                }

                else -> {
                    println("❓ 收到未知消息类型: ${message::class.simpleName}")
                }
            }
        }

        // 验证结果
        println("\n=== 测试结果 ===")
        println("StreamEvent 收到: $streamEventReceived")
        println("AssistantMessage 收到: $assistantMessageReceived")
        println("ResultMessage 收到: $resultMessageReceived")

        // 断言：必须收到 StreamEvent
        assertTrue(streamEventReceived, "❌ 未收到 StreamEvent！SDK 应该输出 StreamEvent 当 includePartialMessages = true")
        assertTrue(assistantMessageReceived, "应该收到 AssistantMessage")
        assertTrue(resultMessageReceived, "应该收到 ResultMessage")

        println("✅ 测试通过：SDK 成功输出了 StreamEvent")
    }

    @Test
    fun `test SDK does not output StreamEvent when includePartialMessages is false`() = runBlocking {
        // 创建不启用流式输出的客户端
        val clientNoStream = ClaudeCodeSdkClient(
            ClaudeAgentOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 1,
                includePartialMessages = false  // 禁用流式输出
            )
        )
        clientNoStream.connect()

        var streamEventReceived = false
        var assistantMessageReceived = false

        try {
            println("\n=== 测试 includePartialMessages = false ===")
            println("发送查询: '请用一句话解释什么是 Kotlin'")

            clientNoStream.query(
                prompt = "请用一句话解释什么是 Kotlin",
                sessionId = "test-no-stream-${UUID.randomUUID()}"
            )

            clientNoStream.receiveResponse().collect { message ->
                when (message) {
                    is StreamEvent -> {
                        streamEventReceived = true
                        println("⚠️ 收到 StreamEvent (不应该收到)")
                    }

                    is AssistantMessage -> {
                        assistantMessageReceived = true
                        println("✅ 收到 AssistantMessage")
                    }

                    is ResultMessage -> {
                        println("✅ 收到 ResultMessage")
                        return@collect  // 结束收集
                    }

                    else -> {}
                }
            }

            println("\n=== 测试结果 ===")
            println("StreamEvent 收到: $streamEventReceived")
            println("AssistantMessage 收到: $assistantMessageReceived")

            // 断言：不应该收到 StreamEvent
            assertTrue(!streamEventReceived, "❌ 不应该收到 StreamEvent 当 includePartialMessages = false")
            assertTrue(assistantMessageReceived, "应该收到 AssistantMessage")

            println("✅ 测试通过：当 includePartialMessages = false 时，SDK 不输出 StreamEvent")
        } finally {
            clientNoStream.disconnect()
        }
    }
}


