package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 测试 includePartialMessages 功能
 * 
 * 这个测试类可以直接运行，用于验证：
 * 1. 当 includePartialMessages = true 时，SDK 会输出 StreamEvent 消息
 * 2. 当 includePartialMessages = false 时，SDK 不会输出 StreamEvent 消息
 * 
 * 运行方式：
 * - 在 IDEA 中右键点击类名或测试方法，选择 "Run"
 * - 或使用命令行：./gradlew test --tests IncludePartialMessagesTest
 */
class IncludePartialMessagesTest {

    private lateinit var client: ClaudeCodeSdkClient
    private lateinit var sessionId: String

    @BeforeEach
    fun setUp() {
        sessionId = "test-include-partial-${UUID.randomUUID()}"
    }

    @AfterEach
    fun tearDown() {
        runBlocking {
            if (this@IncludePartialMessagesTest::client.isInitialized) {
                client.disconnect()
            }
        }
    }

    @Test
    fun `test includePartialMessages true - should receive StreamEvent messages`() = runBlocking {
        println("\n" + "=".repeat(60))
        println("测试 1: includePartialMessages = true")
        println("=".repeat(60))

        // 创建启用流式输出的客户端
        client = ClaudeCodeSdkClient(
            ClaudeAgentOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 1,
                includePartialMessages = true, // 启用流式输出
                dangerouslySkipPermissions = true,
                allowDangerouslySkipPermissions =  true
            )
        )
        client.connect()

        var streamEventCount = 0
        var assistantMessageReceived = false
        var resultMessageReceived = false
        val streamEventTypes = mutableSetOf<String>()

        val testPrompt = "请用一句话解释什么是 Kotlin"
        println("\n📤 发送查询: \"$testPrompt\"")
        println("\n📥 开始接收消息...\n")

        // 发送查询
        client.query(
            prompt = testPrompt,
            sessionId = sessionId
        )

        // 收集消息流
        client.receiveResponse().collect { message ->
            when (message) {
                is StreamEvent -> {
                    streamEventCount++
                    // 从 JsonElement 中提取事件类型
                    val eventType = when (val event = message.event) {
                        is JsonObject -> {
                            event["type"]?.jsonPrimitive?.content ?: "unknown"
                        }
                        else -> "unknown"
                    }
                    streamEventTypes.add(eventType)
                    
                    // 只打印前几个 StreamEvent，避免输出过多
                    if (streamEventCount <= 5) {
                        println("  ✅ StreamEvent #$streamEventCount: $eventType")
                    } else if (streamEventCount == 6) {
                        println("  ... (更多 StreamEvent 消息)")
                    }
                }

                is AssistantMessage -> {
                    assistantMessageReceived = true
                    println("\n✅ 收到 AssistantMessage:")
                    println("   - Model: ${message.model}")
                    println("   - Content blocks: ${message.content.size}")
                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> {
                                val text = block.text.take(100)
                                println("     - Text: $text${if (block.text.length > 100) "..." else ""}")
                            }
                            is ThinkingBlock -> {
                                val thinking = block.thinking.take(100)
                                println("     - Thinking: $thinking${if (block.thinking.length > 100) "..." else ""}")
                            }
                            else -> println("     - ${block::class.simpleName}")
                        }
                    }
                }

                is ResultMessage -> {
                    resultMessageReceived = true
                    println("\n✅ 收到 ResultMessage:")
                    println("   - Status: ${if (message.isError) "❌ Error" else "✅ Success"}")
                    println("   - Turns: ${message.numTurns}")
                    println("   - Duration: ${message.durationMs}ms")
                    if (message.result != null) {
                        val result = message.result.take(200)
                        println("   - Result: $result${if (message.result.length > 200) "..." else ""}")
                    }
                }

                is UserMessage -> {
                    println("📤 收到 UserMessage (回显)")
                }

                else -> {
                    println("❓ 收到未知消息类型: ${message::class.simpleName}")
                }
            }
        }

        // 打印测试结果
        println("\n" + "=".repeat(60))
        println("测试结果:")
        println("=".repeat(60))
        println("✅ StreamEvent 总数: $streamEventCount")
        println("✅ StreamEvent 类型: ${streamEventTypes.joinToString(", ")}")
        println("✅ AssistantMessage: ${if (assistantMessageReceived) "收到" else "未收到"}")
        println("✅ ResultMessage: ${if (resultMessageReceived) "收到" else "未收到"}")

        // 断言验证
        assertTrue(streamEventCount > 0, "❌ 应该收到 StreamEvent 消息，但实际收到 0 条")
        assertTrue(assistantMessageReceived, "应该收到 AssistantMessage")
        assertTrue(resultMessageReceived, "应该收到 ResultMessage")
        assertTrue(streamEventTypes.contains("content_block_delta"), "应该包含 content_block_delta 事件")

        println("\n✅ 测试通过：当 includePartialMessages = true 时，SDK 成功输出了 $streamEventCount 条 StreamEvent 消息")
    }

    @Test
    fun `test includePartialMessages false - should NOT receive StreamEvent messages`() = runBlocking {
        println("\n" + "=".repeat(60))
        println("测试 2: includePartialMessages = false")
        println("=".repeat(60))

        // 创建禁用流式输出的客户端
        client = ClaudeCodeSdkClient(
            ClaudeAgentOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 1,
                includePartialMessages = false  // 禁用流式输出
            )
        )
        client.connect()

        var streamEventCount = 0
        var assistantMessageReceived = false
        var resultMessageReceived = false

        val testPrompt = "请用一句话解释什么是 Kotlin"
        println("\n📤 发送查询: \"$testPrompt\"")
        println("\n📥 开始接收消息...\n")

        // 发送查询
        client.query(
            prompt = testPrompt,
            sessionId = "test-no-stream-${UUID.randomUUID()}"
        )

        // 收集消息流
        client.receiveResponse().collect { message ->
            when (message) {
                is StreamEvent -> {
                    streamEventCount++
                    val eventType = when (val event = message.event) {
                        is JsonObject -> {
                            event["type"]?.jsonPrimitive?.content ?: "unknown"
                        }
                        else -> "unknown"
                    }
                    println("⚠️  收到 StreamEvent (不应该收到): $eventType")
                }

                is AssistantMessage -> {
                    assistantMessageReceived = true
                    println("✅ 收到 AssistantMessage:")
                    println("   - Model: ${message.model}")
                    println("   - Content blocks: ${message.content.size}")
                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> {
                                val text = block.text.take(100)
                                println("     - Text: $text${if (block.text.length > 100) "..." else ""}")
                            }
                            else -> println("     - ${block::class.simpleName}")
                        }
                    }
                }

                is ResultMessage -> {
                    resultMessageReceived = true
                    println("\n✅ 收到 ResultMessage:")
                    println("   - Status: ${if (message.isError) "❌ Error" else "✅ Success"}")
                    println("   - Turns: ${message.numTurns}")
                    println("   - Duration: ${message.durationMs}ms")
                }

                else -> {}
            }
        }

        // 打印测试结果
        println("\n" + "=".repeat(60))
        println("测试结果:")
        println("=".repeat(60))
        println("✅ StreamEvent 总数: $streamEventCount (应该为 0)")
        println("✅ AssistantMessage: ${if (assistantMessageReceived) "收到" else "未收到"}")
        println("✅ ResultMessage: ${if (resultMessageReceived) "收到" else "未收到"}")

        // 断言验证
        assertTrue(streamEventCount == 0, "❌ 不应该收到 StreamEvent 消息，但实际收到 $streamEventCount 条")
        assertTrue(assistantMessageReceived, "应该收到 AssistantMessage")
        assertTrue(resultMessageReceived, "应该收到 ResultMessage")

        println("\n✅ 测试通过：当 includePartialMessages = false 时，SDK 没有输出 StreamEvent 消息")
    }

    @Test
    fun `test includePartialMessages with tool use - should receive StreamEvent for tool input`() = runBlocking {
        println("\n" + "=".repeat(60))
        println("测试 3: includePartialMessages = true + 工具调用")
        println("=".repeat(60))

        // 创建启用流式输出的客户端
        client = ClaudeCodeSdkClient(
            ClaudeAgentOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 1,
                includePartialMessages = true  // 启用流式输出
            )
        )
        client.connect()

        var streamEventCount = 0
        var toolUseReceived = false
        var inputJsonDeltaCount = 0

        val testPrompt = "使用 todowrite 工具创建一个待办事项：'测试 StreamEvent'"
        println("\n📤 发送查询: \"$testPrompt\"")
        println("\n📥 开始接收消息...\n")

        // 发送查询
        client.query(
            prompt = testPrompt,
            sessionId = "test-tool-use-${UUID.randomUUID()}"
        )

        // 收集消息流
        client.receiveResponse().collect { message ->
            when (message) {
                is StreamEvent -> {
                    streamEventCount++
                    when (val event = message.event) {
                        is JsonObject -> {
                            val eventType = event["type"]?.jsonPrimitive?.content
                            
                            // 检查是否是 content_block_delta 事件
                            if (eventType == "content_block_delta") {
                                val delta = event["delta"]?.jsonObject
                                if (delta != null) {
                                    // 检查是否是 input_json_delta
                                    if (delta.containsKey("partial_json")) {
                                        inputJsonDeltaCount++
                                        val partialJson = delta["partial_json"]?.jsonPrimitive?.content ?: ""
                                        if (inputJsonDeltaCount <= 3) {
                                            println("  ✅ ContentBlockDelta (input_json_delta): ${partialJson.take(50)}...")
                                        }
                                    }
                                }
                            }
                            
                            // 检查是否是 content_block_start 事件，且是 tool_use 类型
                            if (eventType == "content_block_start") {
                                val contentBlock = event["content_block"]?.jsonObject
                                if (contentBlock != null) {
                                    val blockType = contentBlock["type"]?.jsonPrimitive?.content
                                    if (blockType == "tool_use") {
                                        toolUseReceived = true
                                        val toolName = contentBlock["name"]?.jsonPrimitive?.content ?: "unknown"
                                        println("  ✅ ContentBlockStart (tool_use): $toolName")
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }

                is AssistantMessage -> {
                    println("\n✅ 收到 AssistantMessage:")
                    message.content.forEach { block ->
                        when (block) {
                            is ToolUseBlock -> {
                                println("   - Tool: ${block.name}")
                                println("   - Input: ${block.input}")
                            }
                            else -> println("   - ${block::class.simpleName}")
                        }
                    }
                }

                is ResultMessage -> {
                    println("\n✅ 收到 ResultMessage (完成)")
                }

                else -> {}
            }
        }

        // 打印测试结果
        println("\n" + "=".repeat(60))
        println("测试结果:")
        println("=".repeat(60))
        println("✅ StreamEvent 总数: $streamEventCount")
        println("✅ InputJsonDelta 数量: $inputJsonDeltaCount")
        println("✅ ToolUse 收到: ${if (toolUseReceived) "是" else "否"}")

        // 断言验证
        assertTrue(streamEventCount > 0, "应该收到 StreamEvent 消息")
        assertTrue(inputJsonDeltaCount > 0, "应该收到 input_json_delta 事件（工具输入流式更新）")

        println("\n✅ 测试通过：工具调用时也成功输出了 StreamEvent 消息")
    }
}


