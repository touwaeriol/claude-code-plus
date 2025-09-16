package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.protocol.MessageParser
import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * 测试 /help 命令的解析
 */
class HelpCommandTest {

    private val parser = MessageParser()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `test parse help command response`() {
        // /help 通常返回 AssistantMessage，包含帮助文本
        val helpResponse = """
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "Available commands:\n\n/help - Show this help message\n/context - Show context usage\n/compact - Compact the conversation\n/model - Show or change the model\n/exit - Exit the session\n/clear - Clear the conversation\n/save - Save the conversation\n/load - Load a conversation\n\nFor more information, visit https://claude.ai/help"
                    }
                ],
                "model": "claude-3-5-sonnet-20241022"
            }
        }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(helpResponse)
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
        assertTrue(text.contains("/help"), "应包含 /help 命令")
        assertTrue(text.contains("/context"), "应包含 /context 命令")
        assertTrue(text.contains("/compact"), "应包含 /compact 命令")
        assertTrue(text.contains("/model"), "应包含 /model 命令")
        assertTrue(text.contains("Available commands"), "应包含 'Available commands'")

        println("✅ /help 命令响应解析成功")
        println("  - 消息类型: AssistantMessage")
        println("  - 内容类型: TextBlock")
        println("  - 包含命令列表: ✓")
    }

    @Test
    fun `test real help command execution`() = runBlocking {
        println("\n=== 测试真实的 /help 命令执行 ===")

        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022"
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            println("1. 连接到 Claude CLI...")
            client.connect()
            assertTrue(client.isConnected(), "应该成功连接")

            println("2. 发送 /help 命令...")
            client.query("/help")

            var helpReceived = false
            var helpContent = ""
            val messageTypes = mutableSetOf<String>()

            withTimeout(30000) {
                client.receiveResponse().collect { message ->
                    messageTypes.add(message::class.simpleName ?: "Unknown")

                    when (message) {
                        is AssistantMessage -> {
                            message.content.forEach { block ->
                                if (block is TextBlock) {
                                    helpContent += block.text
                                    println("收到帮助文本（前100字符）: ${block.text.take(100)}...")

                                    // 检查是否包含帮助信息的特征
                                    if (block.text.contains("help", ignoreCase = true) ||
                                        block.text.contains("command", ignoreCase = true) ||
                                        block.text.contains("/", ignoreCase = false)) {
                                        helpReceived = true
                                    }
                                }
                            }
                        }
                        is SystemMessage -> {
                            println("系统消息: ${message.subtype}")
                            if (message.subtype == "help" || message.subtype == "command") {
                                helpReceived = true
                            }
                        }
                        is ResultMessage -> {
                            println("结果消息: ${message.subtype}, error=${message.isError}")
                            if (!message.isError) {
                                // 命令执行成功
                            }
                        }
                        else -> {
                            println("其他消息: ${message::class.simpleName}")
                        }
                    }
                }
            }

            println("\n3. 验证结果:")
            println("  - 收到消息类型: $messageTypes")
            println("  - 收到帮助内容: $helpReceived")

            assertTrue(messageTypes.isNotEmpty(), "应该收到消息")
            assertTrue(
                helpReceived || helpContent.isNotEmpty(),
                "/help 应该返回帮助信息"
            )

            println("\n✅ /help 命令执行成功")

        } catch (e: Exception) {
            // 如果 Claude CLI 未安装，跳过测试
            if (e.message?.contains("Claude CLI") == true ||
                e.message?.contains("not found") == true) {
                println("⚠️ Claude CLI 未安装，跳过真实执行测试")
            } else {
                throw e
            }
        } finally {
            try {
                client.disconnect()
            } catch (e: Exception) {
                // 忽略断开连接的错误
            }
        }
    }

    @Test
    fun `test parse various help formats`() {
        // 测试不同格式的帮助信息

        val helpFormats = listOf(
            // 格式1：简单文本列表
            """
            {
                "type": "assistant",
                "message": {
                    "content": [
                        {
                            "type": "text",
                            "text": "/help - Show help\n/exit - Exit\n/clear - Clear chat"
                        }
                    ],
                    "model": "claude-3-5-sonnet-20241022"
                }
            }
            """.trimIndent(),

            // 格式2：带分组的帮助
            """
            {
                "type": "assistant",
                "message": {
                    "content": [
                        {
                            "type": "text",
                            "text": "## Session Commands\n/exit - Exit session\n/clear - Clear messages\n\n## Context Commands\n/context - Show usage\n/compact - Compact history"
                        }
                    ],
                    "model": "claude-3-5-sonnet-20241022"
                }
            }
            """.trimIndent(),

            // 格式3：带描述的详细帮助
            """
            {
                "type": "assistant",
                "message": {
                    "content": [
                        {
                            "type": "text",
                            "text": "Claude Commands:\n\n• /help\n  Display this help message with all available commands\n\n• /context\n  Show current context usage and token counts\n\n• /compact\n  Compress conversation history to save tokens"
                        }
                    ],
                    "model": "claude-3-5-sonnet-20241022"
                }
            }
            """.trimIndent()
        )

        for ((index, helpJson) in helpFormats.withIndex()) {
            val message = parser.parseMessage(json.parseToJsonElement(helpJson))
            assertTrue(message is AssistantMessage, "格式${index + 1}应该是 AssistantMessage")

            val content = (message as AssistantMessage).content.first() as TextBlock
            assertTrue(content.text.contains("/"), "格式${index + 1}应包含命令")

            println("✅ 帮助格式${index + 1}解析成功")
        }
    }

    @Test
    fun `test help command with markdown formatting`() {
        // 测试包含 Markdown 格式的帮助信息
        val markdownHelp = """
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "# Claude Commands\n\n## Basic Commands\n\n- **`/help`** - Show this help message\n- **`/exit`** - Exit the session\n- **`/clear`** - Clear conversation history\n\n## Context Management\n\n- **`/context`** - Display context usage statistics\n- **`/compact`** - Compact the conversation to save tokens\n\n## Model Settings\n\n- **`/model`** - Show current model\n- **`/model <name>`** - Switch to a different model\n\n---\n\n*For more information, visit the [documentation](https://docs.claude.ai)*"
                    }
                ],
                "model": "claude-3-5-sonnet-20241022"
            }
        }
        """.trimIndent()

        val message = parser.parseMessage(json.parseToJsonElement(markdownHelp))
        assertTrue(message is AssistantMessage)

        val textBlock = (message as AssistantMessage).content.first() as TextBlock

        // 验证 Markdown 元素
        assertTrue(textBlock.text.contains("# Claude Commands"), "应包含标题")
        assertTrue(textBlock.text.contains("## Basic Commands"), "应包含子标题")
        assertTrue(textBlock.text.contains("**`/help`**"), "应包含加粗代码")
        assertTrue(textBlock.text.contains("---"), "应包含分隔线")
        assertTrue(textBlock.text.contains("[documentation]"), "应包含链接")

        println("✅ Markdown 格式的帮助信息解析成功")
        println("  - 包含标题: ✓")
        println("  - 包含格式化: ✓")
        println("  - 包含链接: ✓")
    }

    @Test
    fun `test help as system message`() {
        // 有些情况下 /help 可能返回 SystemMessage
        val helpSystemMessage = """
        {
            "type": "system",
            "subtype": "help",
            "data": {
                "commands": [
                    "/help",
                    "/context",
                    "/compact",
                    "/exit",
                    "/clear"
                ],
                "version": "1.0.0",
                "documentation": "https://docs.claude.ai"
            }
        }
        """.trimIndent()

        val message = parser.parseMessage(json.parseToJsonElement(helpSystemMessage))
        assertTrue(message is SystemMessage, "应该是 SystemMessage")

        val systemMsg = message as SystemMessage
        assertEquals("help", systemMsg.subtype)

        val data = systemMsg.data.jsonObject
        val commands = data["commands"]?.jsonArray
        assertNotNull(commands)
        assertTrue(commands.size >= 5, "应包含至少5个命令")

        println("✅ SystemMessage 格式的帮助信息解析成功")
        println("  - Subtype: help")
        println("  - 命令数量: ${commands.size}")
    }

    @Test
    fun `test complete help interaction flow`() {
        println("\n=== 测试完整的 /help 交互流程 ===")

        // 模拟完整的交互流程
        val interactionFlow = listOf(
            // 1. 用户输入 /help
            """{"type": "user", "content": "/help", "session_id": "test"}""",

            // 2. Claude 返回帮助信息
            """
            {
                "type": "assistant",
                "message": {
                    "content": [{
                        "type": "text",
                        "text": "Here are the available commands:\n\n/help - Display this help message\n/context - Show context usage\n/compact - Compact conversation\n/exit - Exit session\n\nType any command to execute it, or just chat normally."
                    }],
                    "model": "claude-3-5-sonnet-20241022"
                }
            }
            """.trimIndent(),

            // 3. 结果消息
            """
            {
                "type": "result",
                "subtype": "success",
                "duration_ms": 500,
                "duration_api_ms": 300,
                "is_error": false,
                "num_turns": 1,
                "session_id": "test"
            }
            """.trimIndent()
        )

        val messages = interactionFlow.map { msgJson ->
            parser.parseMessage(json.parseToJsonElement(msgJson))
        }

        // 验证流程
        assertTrue(messages[0] is UserMessage)
        assertEquals("/help", (messages[0] as UserMessage).content.jsonPrimitive.content)

        assertTrue(messages[1] is AssistantMessage)
        val helpText = ((messages[1] as AssistantMessage).content.first() as TextBlock).text
        assertTrue(helpText.contains("available commands"))
        assertTrue(helpText.contains("/help"))

        assertTrue(messages[2] is ResultMessage)
        assertFalse((messages[2] as ResultMessage).isError)

        println("✅ 完整的 /help 交互流程解析成功")
        println("  - 用户请求: /help")
        println("  - Claude 响应: 帮助信息")
        println("  - 执行结果: 成功")
    }
}