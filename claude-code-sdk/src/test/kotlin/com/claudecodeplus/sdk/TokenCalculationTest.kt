package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.protocol.MessageParser
import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * 测试 Token 使用信息的解析和计算
 */
class TokenCalculationTest {

    private val parser = MessageParser()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `test parse AssistantMessage with token usage`() {
        // AssistantMessage 包含 tokenUsage 信息
        val messageWithTokens = """
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "This is a response that uses tokens."
                    }
                ],
                "model": "claude-3-5-sonnet-20241022",
                "usage": {
                    "input_tokens": 1234,
                    "output_tokens": 567,
                    "cache_creation_input_tokens": 100,
                    "cache_read_input_tokens": 50
                }
            },
            "token_usage": {
                "input_tokens": 1234,
                "output_tokens": 567
            }
        }
        """.trimIndent()

        val message = parser.parseMessage(json.parseToJsonElement(messageWithTokens))
        assertTrue(message is AssistantMessage)

        val assistantMsg = message as AssistantMessage
        assertNotNull(assistantMsg.tokenUsage)

        val tokenUsage = assistantMsg.tokenUsage!!
        assertEquals(1234, tokenUsage.inputTokens)
        assertEquals(567, tokenUsage.outputTokens)

        // 计算总 token 数
        val totalTokens = tokenUsage.inputTokens + tokenUsage.outputTokens
        assertEquals(1801, totalTokens)

        println("✅ AssistantMessage token 信息解析成功")
        println("  - 输入 tokens: ${tokenUsage.inputTokens}")
        println("  - 输出 tokens: ${tokenUsage.outputTokens}")
        println("  - 总计 tokens: $totalTokens")
    }

    @Test
    fun `test parse ResultMessage with usage information`() {
        // ResultMessage 包含 usage 信息
        val resultWithUsage = """
        {
            "type": "result",
            "subtype": "success",
            "duration_ms": 2500,
            "duration_api_ms": 2000,
            "is_error": false,
            "num_turns": 3,
            "session_id": "test-session",
            "total_cost_usd": 0.0125,
            "usage": {
                "input_tokens": 5000,
                "output_tokens": 2500,
                "total_tokens": 7500,
                "cache_creation_input_tokens": 200,
                "cache_read_input_tokens": 300
            }
        }
        """.trimIndent()

        val message = parser.parseMessage(json.parseToJsonElement(resultWithUsage))
        assertTrue(message is ResultMessage)

        val resultMsg = message as ResultMessage
        assertNotNull(resultMsg.usage)
        assertEquals(0.0125, resultMsg.totalCostUsd)

        // 解析 usage JSON
        val usage = resultMsg.usage?.jsonObject
        assertNotNull(usage)

        val inputTokens = usage["input_tokens"]?.jsonPrimitive?.int ?: 0
        val outputTokens = usage["output_tokens"]?.jsonPrimitive?.int ?: 0
        val totalTokens = usage["total_tokens"]?.jsonPrimitive?.int ?: 0

        assertEquals(5000, inputTokens)
        assertEquals(2500, outputTokens)
        assertEquals(7500, totalTokens)

        println("✅ ResultMessage usage 信息解析成功")
        println("  - 输入 tokens: $inputTokens")
        println("  - 输出 tokens: $outputTokens")
        println("  - 总计 tokens: $totalTokens")
        println("  - 费用: $$${resultMsg.totalCostUsd}")
    }

    @Test
    fun `test calculate cumulative tokens from multiple messages`() {
        // 模拟一个对话序列，计算累计 token 使用
        val messages = listOf(
            // 第一个助手消息
            """
            {
                "type": "assistant",
                "message": {
                    "content": [{"type": "text", "text": "First response"}],
                    "model": "claude-3-5-sonnet-20241022"
                },
                "token_usage": {
                    "input_tokens": 100,
                    "output_tokens": 50
                }
            }
            """.trimIndent(),

            // 第二个助手消息
            """
            {
                "type": "assistant",
                "message": {
                    "content": [{"type": "text", "text": "Second response"}],
                    "model": "claude-3-5-sonnet-20241022"
                },
                "token_usage": {
                    "input_tokens": 150,
                    "output_tokens": 75
                }
            }
            """.trimIndent(),

            // 第三个助手消息
            """
            {
                "type": "assistant",
                "message": {
                    "content": [{"type": "text", "text": "Third response"}],
                    "model": "claude-3-5-sonnet-20241022"
                },
                "token_usage": {
                    "input_tokens": 200,
                    "output_tokens": 100
                }
            }
            """.trimIndent(),

            // 最终结果消息
            """
            {
                "type": "result",
                "subtype": "success",
                "duration_ms": 5000,
                "duration_api_ms": 4000,
                "is_error": false,
                "num_turns": 3,
                "session_id": "test",
                "usage": {
                    "input_tokens": 450,
                    "output_tokens": 225,
                    "total_tokens": 675
                }
            }
            """.trimIndent()
        )

        var cumulativeInputTokens = 0
        var cumulativeOutputTokens = 0
        var totalFromResult = 0

        messages.forEach { msgJson ->
            val message = parser.parseMessage(json.parseToJsonElement(msgJson))

            when (message) {
                is AssistantMessage -> {
                    message.tokenUsage?.let { usage ->
                        cumulativeInputTokens += usage.inputTokens
                        cumulativeOutputTokens += usage.outputTokens
                        println("助手消息 - 输入: ${usage.inputTokens}, 输出: ${usage.outputTokens}")
                    }
                }
                is ResultMessage -> {
                    message.usage?.jsonObject?.let { usage ->
                        totalFromResult = usage["total_tokens"]?.jsonPrimitive?.int ?: 0
                        println("结果消息 - 总计: $totalFromResult tokens")
                    }
                }
                else -> {}
            }
        }

        val calculatedTotal = cumulativeInputTokens + cumulativeOutputTokens
        assertEquals(675, calculatedTotal)
        assertEquals(totalFromResult, calculatedTotal)

        println("\n✅ 累计 token 计算成功")
        println("  - 累计输入: $cumulativeInputTokens")
        println("  - 累计输出: $cumulativeOutputTokens")
        println("  - 累计总计: $calculatedTotal")
        println("  - 结果验证: $totalFromResult")
    }

    @Test
    fun `test token usage with cache information`() {
        // 测试包含缓存信息的 token 使用
        val messageWithCache = """
        {
            "type": "assistant",
            "message": {
                "content": [{"type": "text", "text": "Response with cache"}],
                "model": "claude-3-5-sonnet-20241022"
            },
            "token_usage": {
                "input_tokens": 1000,
                "output_tokens": 200,
                "cache_creation_input_tokens": 500,
                "cache_read_input_tokens": 300
            }
        }
        """.trimIndent()

        val message = parser.parseMessage(json.parseToJsonElement(messageWithCache))
        assertTrue(message is AssistantMessage)

        val tokenUsage = (message as AssistantMessage).tokenUsage!!

        // 计算实际使用的 tokens（不包括缓存）
        val actualInputTokens = tokenUsage.inputTokens
        val cacheTokens = (tokenUsage.cacheCreationInputTokens ?: 0) +
                         (tokenUsage.cacheReadInputTokens ?: 0)

        println("✅ 缓存 token 信息解析成功")
        println("  - 总输入 tokens: ${tokenUsage.inputTokens}")
        println("  - 缓存创建 tokens: ${tokenUsage.cacheCreationInputTokens}")
        println("  - 缓存读取 tokens: ${tokenUsage.cacheReadInputTokens}")
        println("  - 缓存总计: $cacheTokens")
        println("  - 输出 tokens: ${tokenUsage.outputTokens}")
    }

    @Test
    fun `test messages without token information`() {
        // 测试没有 token 信息的消息
        val messageNoTokens = """
        {
            "type": "assistant",
            "message": {
                "content": [{"type": "text", "text": "Response without token info"}],
                "model": "claude-3-5-sonnet-20241022"
            }
        }
        """.trimIndent()

        val message = parser.parseMessage(json.parseToJsonElement(messageNoTokens))
        assertTrue(message is AssistantMessage)

        val assistantMsg = message as AssistantMessage
        assertNull(assistantMsg.tokenUsage)

        println("✅ 无 token 信息的消息解析成功")
        println("  - tokenUsage 为 null，符合预期")
    }

    @Test
    fun `test calculate token percentage of context`() {
        // 模拟计算 token 占用上下文的百分比
        val contextLimit = 200_000  // Claude 的上下文限制

        val currentUsage = """
        {
            "type": "result",
            "subtype": "success",
            "duration_ms": 1000,
            "duration_api_ms": 800,
            "is_error": false,
            "num_turns": 5,
            "session_id": "test",
            "usage": {
                "input_tokens": 122000,
                "output_tokens": 5000,
                "total_tokens": 127000
            }
        }
        """.trimIndent()

        val message = parser.parseMessage(json.parseToJsonElement(currentUsage))
        assertTrue(message is ResultMessage)

        val usage = (message as ResultMessage).usage?.jsonObject
        val totalTokens = usage?.get("total_tokens")?.jsonPrimitive?.int ?: 0

        val percentageUsed = (totalTokens * 100.0 / contextLimit)
        val remainingTokens = contextLimit - totalTokens

        println("✅ 上下文使用率计算成功")
        println("  - 当前使用: $totalTokens tokens")
        println("  - 上下文限制: $contextLimit tokens")
        println("  - 使用率: %.1f%%".format(percentageUsed))
        println("  - 剩余空间: $remainingTokens tokens")

        assertTrue(percentageUsed > 60 && percentageUsed < 65)
    }

    @Test
    fun `test extract token info from context command output`() {
        // 从 /context 命令的输出中提取 token 信息
        val contextOutput = "claude-opus-4-1-20250805 • 122k/200k tokens (61%)"

        // 使用正则表达式提取
        val tokenPattern = """(\d+)k/(\d+)k tokens \((\d+)%\)""".toRegex()
        val match = tokenPattern.find(contextOutput)

        assertNotNull(match)

        val (used, total, percentage) = match.destructured
        assertEquals("122", used)
        assertEquals("200", total)
        assertEquals("61", percentage)

        val usedTokens = used.toInt() * 1000
        val totalTokens = total.toInt() * 1000

        println("✅ 从 /context 输出提取 token 信息成功")
        println("  - 已用: ${usedTokens} tokens")
        println("  - 总计: ${totalTokens} tokens")
        println("  - 百分比: $percentage%")
    }

    @Test
    fun `test token cost calculation`() {
        // 测试 token 费用计算
        // Claude 3.5 Sonnet 价格（示例）：
        // 输入: $3 per million tokens
        // 输出: $15 per million tokens

        val pricePerMillionInput = 3.0
        val pricePerMillionOutput = 15.0

        val tokenUsage = TokenUsage(
            inputTokens = 10_000,
            outputTokens = 2_000,
            cacheCreationInputTokens = null,
            cacheReadInputTokens = null
        )

        val inputCost = (tokenUsage.inputTokens / 1_000_000.0) * pricePerMillionInput
        val outputCost = (tokenUsage.outputTokens / 1_000_000.0) * pricePerMillionOutput
        val totalCost = inputCost + outputCost

        println("✅ Token 费用计算成功")
        println("  - 输入 tokens: ${tokenUsage.inputTokens} = $${"%.4f".format(inputCost)}")
        println("  - 输出 tokens: ${tokenUsage.outputTokens} = $${"%.4f".format(outputCost)}")
        println("  - 总费用: $${"%.4f".format(totalCost)}")

        assertTrue(totalCost > 0.05 && totalCost < 0.07)
    }
}