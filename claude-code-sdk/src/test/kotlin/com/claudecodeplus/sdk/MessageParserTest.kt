package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.protocol.MessageParser
import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * 测试 MessageParser 对各种消息类型的解析能力
 */
class MessageParserTest {

    private val parser = MessageParser()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `test parse context command response as AssistantMessage`() {
        // /context 命令返回的是 AssistantMessage，包含格式化的文本
        val contextResponse = """
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "⛁ ⛀ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁\n⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁  Context Usage\n⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁  claude-3-5-sonnet-20241022 • 122k/200k tokens (61%)\n\n⛁ System prompt: 3.4k tokens (1.7%)\n⛁ System tools: 11.8k tokens (5.9%)\n⛁ MCP tools: 14.9k tokens (7.5%)\n⛁ Memory files: 9.7k tokens (4.9%)\n⛁ Messages: 82.0k tokens (41.0%)\n⛶ Free space: 78.1k (39.1%)"
                    }
                ],
                "model": "claude-3-5-sonnet-20241022"
            }
        }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(contextResponse)
        val message = parser.parseMessage(jsonElement)

        // 验证是 AssistantMessage
        assertTrue(message is AssistantMessage, "应该是 AssistantMessage")

        val assistantMsg = message as AssistantMessage
        assertEquals("claude-3-5-sonnet-20241022", assistantMsg.model)
        assertEquals(1, assistantMsg.content.size)

        // 验证内容是 TextBlock
        val textBlock = assistantMsg.content.first()
        assertTrue(textBlock is TextBlock, "内容应该是 TextBlock")

        val text = (textBlock as TextBlock).text
        assertTrue(text.contains("Context Usage"), "应包含 'Context Usage'")
        assertTrue(text.contains("122k/200k tokens"), "应包含 token 使用信息")
        assertTrue(text.contains("System prompt"), "应包含系统提示信息")
        assertTrue(text.contains("MCP tools"), "应包含 MCP 工具信息")

        println("✅ /context 命令响应解析成功")
        println("  - 消息类型: AssistantMessage")
        println("  - 内容类型: TextBlock")
        println("  - 包含上下文使用信息: ✓")
    }

    @Test
    fun `test parse compact command response as SystemMessage`() {
        // /compact 命令可能返回 SystemMessage
        val compactSystemResponse = """
        {
            "type": "system",
            "subtype": "compact",
            "data": {
                "messages_removed": 5,
                "tokens_saved": 3500,
                "new_context_length": 98500,
                "operation": "success"
            }
        }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(compactSystemResponse)
        val message = parser.parseMessage(jsonElement)

        // 验证是 SystemMessage
        assertTrue(message is SystemMessage, "应该是 SystemMessage")

        val systemMsg = message as SystemMessage
        assertEquals("compact", systemMsg.subtype)

        // 验证 data 字段
        val data = systemMsg.data.jsonObject
        assertEquals(5, data["messages_removed"]?.jsonPrimitive?.int)
        assertEquals(3500, data["tokens_saved"]?.jsonPrimitive?.int)
        assertEquals("success", data["operation"]?.jsonPrimitive?.content)

        println("✅ /compact 系统消息解析成功")
        println("  - 消息类型: SystemMessage")
        println("  - Subtype: compact")
        println("  - 移除消息数: ${data["messages_removed"]?.jsonPrimitive?.int}")
        println("  - 节省 tokens: ${data["tokens_saved"]?.jsonPrimitive?.int}")
    }

    @Test
    fun `test parse compact command response as AssistantMessage`() {
        // /compact 也可能返回 AssistantMessage
        val compactAssistantResponse = """
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "I've compacted our conversation. Removed 10 messages from the beginning, saving approximately 5,000 tokens. The conversation context is now more focused on recent interactions."
                    }
                ],
                "model": "claude-3-5-sonnet-20241022"
            }
        }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(compactAssistantResponse)
        val message = parser.parseMessage(jsonElement)

        // 验证是 AssistantMessage
        assertTrue(message is AssistantMessage, "应该是 AssistantMessage")

        val assistantMsg = message as AssistantMessage
        val textBlock = assistantMsg.content.first() as TextBlock

        assertTrue(textBlock.text.contains("compacted"), "应包含 'compacted'")
        assertTrue(textBlock.text.contains("tokens"), "应包含 'tokens'")

        println("✅ /compact 助手消息解析成功")
        println("  - 消息类型: AssistantMessage")
        println("  - 包含压缩说明: ✓")
    }

    @Test
    fun `test parse various command result messages`() {
        // 测试 ResultMessage 的解析
        val resultMessages = listOf(
            // 成功的命令结果
            """
            {
                "type": "result",
                "subtype": "success",
                "duration_ms": 1500,
                "duration_api_ms": 1200,
                "is_error": false,
                "num_turns": 1,
                "session_id": "test-session",
                "result": "Command executed successfully"
            }
            """.trimIndent() to "success",

            // 命令类型的结果
            """
            {
                "type": "result",
                "subtype": "command",
                "duration_ms": 500,
                "duration_api_ms": 300,
                "is_error": false,
                "num_turns": 1,
                "session_id": "test-session"
            }
            """.trimIndent() to "command",

            // 错误的命令结果
            """
            {
                "type": "result",
                "subtype": "error",
                "duration_ms": 100,
                "duration_api_ms": 50,
                "is_error": true,
                "num_turns": 0,
                "session_id": "test-session",
                "result": "Unknown command"
            }
            """.trimIndent() to "error"
        )

        for ((responseJson, expectedSubtype) in resultMessages) {
            val jsonElement = json.parseToJsonElement(responseJson)
            val message = parser.parseMessage(jsonElement)

            assertTrue(message is ResultMessage, "应该是 ResultMessage")
            val resultMsg = message as ResultMessage

            assertEquals(expectedSubtype, resultMsg.subtype)
            assertEquals(expectedSubtype == "error", resultMsg.isError)

            println("✅ ResultMessage (subtype=$expectedSubtype) 解析成功")
        }
    }

    @Test
    fun `test parse messages with different formats`() {
        // 测试不同格式的消息（直接格式 vs 嵌套格式）

        // 直接格式的 AssistantMessage
        val directFormat = """
        {
            "type": "assistant",
            "content": [
                {"type": "text", "text": "Direct format message"}
            ],
            "model": "claude-3-5-sonnet-20241022"
        }
        """.trimIndent()

        val directMsg = parser.parseMessage(json.parseToJsonElement(directFormat))
        assertTrue(directMsg is AssistantMessage)
        assertEquals("Direct format message", ((directMsg as AssistantMessage).content.first() as TextBlock).text)

        // 嵌套格式的 AssistantMessage（在 message 字段内）
        val nestedFormat = """
        {
            "type": "assistant",
            "message": {
                "content": [
                    {"type": "text", "text": "Nested format message"}
                ],
                "model": "claude-3-5-sonnet-20241022"
            }
        }
        """.trimIndent()

        val nestedMsg = parser.parseMessage(json.parseToJsonElement(nestedFormat))
        assertTrue(nestedMsg is AssistantMessage)
        assertEquals("Nested format message", ((nestedMsg as AssistantMessage).content.first() as TextBlock).text)

        println("✅ 不同格式的消息都能正确解析")
    }

    @Test
    fun `test parse complex context output with colors and symbols`() {
        // 测试包含 ANSI 颜色码和特殊符号的 /context 输出
        val complexContextOutput = """
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "[38;2;153;153;153m⛁ ⛀ [38;2;102;102;102m⛁ ⛁ ⛁ ⛁ ⛁ ⛁ [38;2;8;145;178m⛁ ⛁ [39m\n[38;2;8;145;178m⛁ ⛁ ⛁ ⛁ ⛁ [38;2;215;119;87m⛁ ⛁ ⛁ ⛁ ⛁ [39m  [1mContext Usage[22m\n[38;2;147;51;234m⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁ ⛁ [39m  [2mclaude-opus-4-1-20250805 • 122k/200k tokens (61%)[22m"
                    }
                ],
                "model": "claude-opus-4-1-20250805"
            }
        }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(complexContextOutput)
        val message = parser.parseMessage(jsonElement)

        assertTrue(message is AssistantMessage, "应该是 AssistantMessage")
        val assistantMsg = message as AssistantMessage
        val textBlock = assistantMsg.content.first() as TextBlock

        // 文本包含 ANSI 颜色码
        assertTrue(textBlock.text.contains("[38;2;"), "应包含 ANSI 颜色码")
        // 包含特殊符号
        assertTrue(textBlock.text.contains("⛁"), "应包含特殊符号")
        // 包含实际内容
        assertTrue(textBlock.text.contains("Context Usage"), "应包含 Context Usage")

        println("✅ 复杂格式的 /context 输出解析成功")
        println("  - 包含 ANSI 颜色码: ✓")
        println("  - 包含特殊符号: ✓")
        println("  - 内容完整: ✓")
    }

    @Test
    fun `test parse edge cases and error handling`() {
        // 测试边缘情况

        // 缺少必需字段的消息
        val missingType = """{"content": "test"}"""
        assertFailsWith<Exception> {
            parser.parseMessage(json.parseToJsonElement(missingType))
        }
        println("✅ 缺少 type 字段时正确抛出异常")

        // 未知的消息类型
        val unknownType = """{"type": "unknown_type"}"""
        assertFailsWith<Exception> {
            parser.parseMessage(json.parseToJsonElement(unknownType))
        }
        println("✅ 未知消息类型时正确抛出异常")

        // 空的 content 数组
        val emptyContent = """
        {
            "type": "assistant",
            "message": {
                "content": [],
                "model": "claude-3-5-sonnet-20241022"
            }
        }
        """.trimIndent()

        val emptyMsg = parser.parseMessage(json.parseToJsonElement(emptyContent))
        assertTrue(emptyMsg is AssistantMessage)
        assertEquals(0, (emptyMsg as AssistantMessage).content.size)
        println("✅ 空 content 数组能正确解析")
    }

    @Test
    fun `test parse all content block types`() {
        // 测试所有内容块类型
        val allBlockTypes = """
        {
            "type": "assistant",
            "message": {
                "content": [
                    {"type": "text", "text": "This is text"},
                    {"type": "thinking", "thinking": "Thinking process", "signature": "sig123"},
                    {"type": "tool_use", "id": "tool1", "name": "Read", "input": {"file": "test.txt"}},
                    {"type": "tool_result", "tool_use_id": "tool1", "content": "File contents", "is_error": false}
                ],
                "model": "claude-3-5-sonnet-20241022"
            }
        }
        """.trimIndent()

        val message = parser.parseMessage(json.parseToJsonElement(allBlockTypes))
        assertTrue(message is AssistantMessage)

        val content = (message as AssistantMessage).content
        assertEquals(4, content.size)

        assertTrue(content[0] is TextBlock)
        assertTrue(content[1] is ThinkingBlock)
        assertTrue(content[2] is ToolUseBlock)
        assertTrue(content[3] is ToolResultBlock)

        println("✅ 所有内容块类型都能正确解析")
        println("  - TextBlock: ✓")
        println("  - ThinkingBlock: ✓")
        println("  - ToolUseBlock: ✓")
        println("  - ToolResultBlock: ✓")
    }

    @Test
    fun `test real world context and compact scenarios`() {
        println("\n=== 真实场景测试 ===")

        // 模拟真实的消息序列
        val messages = listOf(
            // 1. 用户发送 /context
            """{"type": "user", "content": "/context", "session_id": "test"}""",

            // 2. 收到 /context 的响应
            """
            {
                "type": "assistant",
                "message": {
                    "content": [{
                        "type": "text",
                        "text": "Context Usage\nclaude-3-5-sonnet • 100k/200k tokens (50%)\n\nSystem: 10k\nMessages: 90k\nFree: 100k"
                    }],
                    "model": "claude-3-5-sonnet-20241022"
                }
            }
            """.trimIndent(),

            // 3. 用户发送 /compact
            """{"type": "user", "content": "/compact", "session_id": "test"}""",

            // 4. 收到 /compact 的系统响应
            """
            {
                "type": "system",
                "subtype": "compact",
                "data": {"status": "completed", "saved": 20000}
            }
            """.trimIndent(),

            // 5. 收到结果消息
            """
            {
                "type": "result",
                "subtype": "command",
                "duration_ms": 1000,
                "duration_api_ms": 800,
                "is_error": false,
                "num_turns": 1,
                "session_id": "test"
            }
            """.trimIndent()
        )

        val parsedMessages = messages.map { msgJson ->
            parser.parseMessage(json.parseToJsonElement(msgJson))
        }

        // 验证消息类型序列
        assertTrue(parsedMessages[0] is UserMessage)
        assertTrue(parsedMessages[1] is AssistantMessage)
        assertTrue(parsedMessages[2] is UserMessage)
        assertTrue(parsedMessages[3] is SystemMessage)
        assertTrue(parsedMessages[4] is ResultMessage)

        // 验证 /context 响应
        val contextResponse = parsedMessages[1] as AssistantMessage
        val contextText = (contextResponse.content.first() as TextBlock).text
        assertTrue(contextText.contains("Context Usage"))
        assertTrue(contextText.contains("50%"))

        // 验证 /compact 响应
        val compactResponse = parsedMessages[3] as SystemMessage
        assertEquals("compact", compactResponse.subtype)
        val compactData = compactResponse.data.jsonObject
        assertEquals("completed", compactData["status"]?.jsonPrimitive?.content)
        assertEquals(20000, compactData["saved"]?.jsonPrimitive?.int)

        println("✅ 真实场景测试通过")
        println("  - /context 请求和响应: ✓")
        println("  - /compact 请求和响应: ✓")
        println("  - 完整消息序列解析: ✓")
    }
}