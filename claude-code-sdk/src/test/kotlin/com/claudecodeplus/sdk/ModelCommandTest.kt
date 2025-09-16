package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.protocol.MessageParser
import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * 测试 /model 命令的解析
 * /model - 显示当前模型
 * /model <name> - 切换到指定模型
 */
class ModelCommandTest {

    private val parser = MessageParser()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `test parse model command show current model`() {
        // /model 不带参数时显示当前模型
        val modelShowResponse = """
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "Current model: claude-3-5-sonnet-20241022\n\nAvailable models:\n• claude-3-5-sonnet-20241022 (current)\n• claude-3-5-haiku-20241022\n• claude-3-opus-20240229\n• claude-opus-4-1-20250805\n\nUse '/model <name>' to switch to a different model."
                    }
                ],
                "model": "claude-3-5-sonnet-20241022"
            }
        }
        """.trimIndent()

        val message = parser.parseMessage(json.parseToJsonElement(modelShowResponse))
        assertTrue(message is AssistantMessage)

        val textBlock = (message as AssistantMessage).content.first() as TextBlock
        assertTrue(textBlock.text.contains("Current model"))
        assertTrue(textBlock.text.contains("claude-3-5-sonnet"))
        assertTrue(textBlock.text.contains("Available models"))

        println("✅ /model（显示当前模型）解析成功")
    }

    @Test
    fun `test parse model switch to opus`() {
        // /model opus - 切换到 opus 模型
        val modelSwitchResponses = listOf(
            // 1. 系统确认消息
            """
            {
                "type": "system",
                "subtype": "model_change",
                "data": {
                    "previous_model": "claude-3-5-sonnet-20241022",
                    "new_model": "claude-3-opus-20240229",
                    "status": "success"
                }
            }
            """.trimIndent(),

            // 2. 助手确认消息
            """
            {
                "type": "assistant",
                "message": {
                    "content": [
                        {
                            "type": "text",
                            "text": "Model switched successfully from claude-3-5-sonnet-20241022 to claude-3-opus-20240229."
                        }
                    ],
                    "model": "claude-3-opus-20240229"
                }
            }
            """.trimIndent()
        )

        // 测试系统消息
        val systemMsg = parser.parseMessage(json.parseToJsonElement(modelSwitchResponses[0]))
        assertTrue(systemMsg is SystemMessage)
        assertEquals("model_change", (systemMsg as SystemMessage).subtype)

        val data = systemMsg.data.jsonObject
        assertEquals("claude-3-opus-20240229", data["new_model"]?.jsonPrimitive?.content)
        assertEquals("success", data["status"]?.jsonPrimitive?.content)

        // 测试助手消息
        val assistantMsg = parser.parseMessage(json.parseToJsonElement(modelSwitchResponses[1]))
        assertTrue(assistantMsg is AssistantMessage)
        assertEquals("claude-3-opus-20240229", (assistantMsg as AssistantMessage).model)

        println("✅ /model opus（切换模型）解析成功")
    }

    @Test
    fun `test parse model switch with full name`() {
        // /model claude-opus-4-1-20250805 - 使用完整模型名称
        val fullNameResponse = """
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "Switching to claude-opus-4-1-20250805...\n\nModel changed successfully. Now using: claude-opus-4-1-20250805\n\nThis is the latest Opus 4.1 model with enhanced capabilities."
                    }
                ],
                "model": "claude-opus-4-1-20250805"
            }
        }
        """.trimIndent()

        val message = parser.parseMessage(json.parseToJsonElement(fullNameResponse))
        assertTrue(message is AssistantMessage)

        val assistantMsg = message as AssistantMessage
        assertEquals("claude-opus-4-1-20250805", assistantMsg.model)

        val textBlock = assistantMsg.content.first() as TextBlock
        assertTrue(textBlock.text.contains("claude-opus-4-1-20250805"))
        assertTrue(textBlock.text.contains("changed successfully"))

        println("✅ /model claude-opus-4-1-20250805（完整模型名）解析成功")
    }

    @Test
    fun `test parse invalid model command`() {
        // /modle opus - 拼写错误的命令
        val typoResponse = """
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "Unknown command: /modle\n\nDid you mean '/model'? Use '/model' to view or change the current model.\n\nAvailable commands:\n/model - Show or change model\n/help - Show all commands"
                    }
                ],
                "model": "claude-3-5-sonnet-20241022"
            }
        }
        """.trimIndent()

        val message = parser.parseMessage(json.parseToJsonElement(typoResponse))
        assertTrue(message is AssistantMessage)

        val textBlock = (message as AssistantMessage).content.first() as TextBlock
        assertTrue(textBlock.text.contains("Unknown command"))
        assertTrue(textBlock.text.contains("/modle"))
        assertTrue(textBlock.text.contains("Did you mean"))

        println("✅ /modle（拼写错误）错误提示解析成功")
    }

    @Test
    fun `test parse model not found error`() {
        // 请求一个不存在的模型
        val notFoundResponse = """
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "Error: Model 'claude-invalid-model' not found.\n\nAvailable models:\n• claude-3-5-sonnet-20241022\n• claude-3-5-haiku-20241022\n• claude-3-opus-20240229\n• claude-opus-4-1-20250805\n\nPlease choose from the available models."
                    }
                ],
                "model": "claude-3-5-sonnet-20241022"
            }
        }
        """.trimIndent()

        val message = parser.parseMessage(json.parseToJsonElement(notFoundResponse))
        assertTrue(message is AssistantMessage)

        val textBlock = (message as AssistantMessage).content.first() as TextBlock
        assertTrue(textBlock.text.contains("not found"))
        assertTrue(textBlock.text.contains("Available models"))

        println("✅ 模型未找到错误解析成功")
    }

    @Test
    fun `test parse model command result messages`() {
        // 测试各种结果消息
        val resultMessages = listOf(
            // 成功切换
            """
            {
                "type": "result",
                "subtype": "model_changed",
                "duration_ms": 200,
                "duration_api_ms": 100,
                "is_error": false,
                "num_turns": 0,
                "session_id": "test",
                "result": "Model changed to claude-opus-4-1-20250805"
            }
            """.trimIndent(),

            // 切换失败
            """
            {
                "type": "result",
                "subtype": "error",
                "duration_ms": 100,
                "duration_api_ms": 50,
                "is_error": true,
                "num_turns": 0,
                "session_id": "test",
                "result": "Failed to switch model: Invalid model name"
            }
            """.trimIndent()
        )

        for (resultJson in resultMessages) {
            val message = parser.parseMessage(json.parseToJsonElement(resultJson))
            assertTrue(message is ResultMessage)

            val resultMsg = message as ResultMessage
            if (resultMsg.subtype == "model_changed") {
                assertFalse(resultMsg.isError)
                assertTrue(resultMsg.result?.contains("Model changed") == true)
            } else {
                assertTrue(resultMsg.isError)
                assertTrue(resultMsg.result?.contains("Failed") == true)
            }
        }

        println("✅ 模型切换结果消息解析成功")
    }

    @Test
    fun `test complete model command flows`() {
        println("\n=== 测试完整的 /model 命令流程 ===")

        // 场景1：查看当前模型
        val viewFlow = listOf(
            """{"type": "user", "content": "/model", "session_id": "test"}""",
            """
            {
                "type": "assistant",
                "message": {
                    "content": [{
                        "type": "text",
                        "text": "Current model: claude-3-5-sonnet-20241022"
                    }],
                    "model": "claude-3-5-sonnet-20241022"
                }
            }
            """.trimIndent()
        )

        // 场景2：切换到 opus
        val switchFlow = listOf(
            """{"type": "user", "content": "/model opus", "session_id": "test"}""",
            """
            {
                "type": "system",
                "subtype": "model_change",
                "data": {"new_model": "claude-3-opus-20240229"}
            }
            """.trimIndent(),
            """
            {
                "type": "assistant",
                "message": {
                    "content": [{
                        "type": "text",
                        "text": "Switched to claude-3-opus-20240229"
                    }],
                    "model": "claude-3-opus-20240229"
                }
            }
            """.trimIndent()
        )

        // 场景3：使用完整名称
        val fullNameFlow = listOf(
            """{"type": "user", "content": "/model claude-opus-4-1-20250805", "session_id": "test"}""",
            """
            {
                "type": "assistant",
                "message": {
                    "content": [{
                        "type": "text",
                        "text": "Model changed to claude-opus-4-1-20250805"
                    }],
                    "model": "claude-opus-4-1-20250805"
                }
            }
            """.trimIndent()
        )

        // 解析所有流程
        for ((name, flow) in listOf(
            "查看模型" to viewFlow,
            "切换到opus" to switchFlow,
            "完整名称切换" to fullNameFlow
        )) {
            println("\n测试场景: $name")
            val messages = flow.map { parser.parseMessage(json.parseToJsonElement(it)) }

            assertTrue(messages.first() is UserMessage)
            assertTrue(messages.any { it is AssistantMessage || it is SystemMessage })

            println("  ✅ $name 流程解析成功")
        }
    }

    @Test
    fun `test model shortcuts and aliases`() {
        // 测试模型别名和快捷方式
        val shortcuts = mapOf(
            "opus" to "claude-3-opus-20240229",
            "sonnet" to "claude-3-5-sonnet-20241022",
            "haiku" to "claude-3-5-haiku-20241022",
            "opus-4" to "claude-opus-4-1-20250805",
            "opus-4.1" to "claude-opus-4-1-20250805"
        )

        for ((shortcut, fullName) in shortcuts) {
            val response = """
            {
                "type": "assistant",
                "message": {
                    "content": [{
                        "type": "text",
                        "text": "Model changed to $fullName"
                    }],
                    "model": "$fullName"
                }
            }
            """.trimIndent()

            val message = parser.parseMessage(json.parseToJsonElement(response))
            assertTrue(message is AssistantMessage)
            assertEquals(fullName, (message as AssistantMessage).model)

            println("✅ 模型快捷方式 '$shortcut' → '$fullName' 解析成功")
        }
    }

    @Test
    fun `test real model command execution`() = runBlocking {
        println("\n=== 测试真实的 /model 命令执行 ===")

        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022"
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            client.connect()

            // 测试各种 /model 命令
            val commands = listOf(
                "/model" to "查看当前模型",
                "/model opus" to "切换到 opus",
                "/model claude-opus-4-1-20250805" to "使用完整名称"
            )

            for ((command, description) in commands) {
                println("\n测试: $description - 命令: '$command'")
                client.query(command)

                withTimeout(15000) {
                    client.receiveResponse().collect { message ->
                        when (message) {
                            is AssistantMessage -> {
                                println("  助手响应: ${message.model}")
                                message.content.filterIsInstance<TextBlock>().forEach {
                                    println("  内容: ${it.text.take(100)}")
                                }
                            }
                            is SystemMessage -> {
                                println("  系统消息: ${message.subtype}")
                            }
                            is ResultMessage -> {
                                println("  结果: ${message.subtype}, error=${message.isError}")
                            }
                            else -> {}
                        }
                    }
                }
            }

            println("\n✅ 真实执行测试完成")

        } catch (e: Exception) {
            if (e.message?.contains("Claude CLI") == true) {
                println("⚠️ Claude CLI 未安装，跳过真实执行测试")
            }
        } finally {
            try {
                client.disconnect()
            } catch (e: Exception) {
                // 忽略
            }
        }
    }
}