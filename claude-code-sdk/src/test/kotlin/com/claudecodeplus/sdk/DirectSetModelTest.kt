package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * 测试 setModel() API 是否真的切换模型
 *
 * 与其他测试的区别：
 * - 其他测试: client.query("/model opus") - 将 /model 当作文本发送
 * - 这个测试: client.setModel("opus") - 直接调用 SDK API
 */
class DirectSetModelTest {

    @Test
    fun `test setModel API with real CLI`() = runTest {
        // 跳过测试如果没有 API Key
        val apiKey = System.getenv("CLAUDE_API_KEY")
        if (apiKey.isNullOrEmpty()) {
            println("⏭️  跳过测试 - CLAUDE_API_KEY 环境变量未设置")
            return@runTest
        }

        println("=== 测试 setModel() API 是否真的切换模型 ===\n")

        val options = ClaudeCodeOptions(
            model = "claude-sonnet-4-20250514",  // 初始: Sonnet 4
            allowedTools = listOf("Read"),
            permissionMode = PermissionMode.ACCEPT_EDITS,
            maxTurns = 5
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            // 步骤 1: 连接
            println("步骤 1: 连接 Claude CLI...")
            client.connect()
            println("✅ 连接成功\n")

            // 步骤 2: 发送初始查询，看看当前模型
            println("步骤 2: 发送初始查询（Sonnet 4）")
            client.query("Hello", "test-session")

            val initialMessages = mutableListOf<Message>()
            client.receiveResponse().collect { message ->
                initialMessages.add(message)

                when (message) {
                    is AssistantMessage -> {
                        println("🤖 Assistant: ${message.content.firstOrNull()?.toString()?.take(50)}")
                        println("   - Model field: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("🏁 Result: ${message.subtype}")
                    }
                    else -> {
                        println("📨 Other: ${message::class.simpleName}")
                    }
                }
            }

            // 检查初始模型名（从 AssistantMessage 的 model 字段获取）
            val initialAssistant = initialMessages.filterIsInstance<AssistantMessage>().firstOrNull()
            val initialModel = initialAssistant?.model
            println("\n初始模型: $initialModel")
            assertTrue(initialModel?.contains("sonnet", ignoreCase = true) ?: false,
                "初始模型应该是 Sonnet")

            // 步骤 3: 使用 setModel() API 切换到 Opus
            println("\n步骤 3: 调用 client.setModel(\"claude-opus-4-20250514\")")

            try {
                client.setModel("claude-opus-4-20250514")
                println("✅ setModel() 调用成功（没有抛出异常）")
            } catch (e: Exception) {
                println("❌ setModel() 失败: ${e.message}")
                throw e
            }

            // 步骤 4: 发送新查询，验证模型是否切换
            println("\n步骤 4: 发送新查询验证模型切换")
            client.query("Hello again", "test-session")

            val afterSwitchMessages = mutableListOf<Message>()
            client.receiveResponse().collect { message ->
                afterSwitchMessages.add(message)

                when (message) {
                    is AssistantMessage -> {
                        println("🤖 Assistant: ${message.content.firstOrNull()?.toString()?.take(50)}")
                        println("   - Model field: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("🏁 Result: ${message.subtype}")
                    }
                    else -> {
                        println("📨 Other: ${message::class.simpleName}")
                    }
                }
            }

            // 检查切换后的模型名（从 AssistantMessage 的 model 字段获取）
            val afterAssistant = afterSwitchMessages.filterIsInstance<AssistantMessage>().firstOrNull()
            val afterModel = afterAssistant?.model
            println("\n切换后模型: $afterModel")

            // 验证：应该变成 Opus
            if (afterModel?.contains("opus", ignoreCase = true) == true) {
                println("✅ 模型切换成功！从 $initialModel -> $afterModel")
            } else {
                println("❌ 模型切换失败！仍然是: $afterModel")
                fail("setModel() 没有生效，模型没有切换到 Opus")
            }

            // 步骤 5: 再次切换到 Haiku
            println("\n步骤 5: 调用 client.setModel(\"claude-haiku-4-20250514\")")
            client.setModel("claude-haiku-4-20250514")
            println("✅ setModel() 调用成功")

            // 步骤 6: 最终验证
            println("\n步骤 6: 最终验证")
            client.query("Final test", "test-session")

            val finalMessages = mutableListOf<Message>()
            client.receiveResponse().collect { message ->
                finalMessages.add(message)

                when (message) {
                    is AssistantMessage -> {
                        println("🤖 Assistant: ${message.content.firstOrNull()?.toString()?.take(50)}")
                        println("   - Model field: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("🏁 Result: ${message.subtype}")
                    }
                    else -> {}
                }
            }

            val finalAssistant = finalMessages.filterIsInstance<AssistantMessage>().firstOrNull()
            val finalModel = finalAssistant?.model
            println("\n最终模型: $finalModel")

            // 验证：应该是 Haiku
            if (finalModel?.contains("haiku", ignoreCase = true) == true) {
                println("✅ 第二次切换成功！从 $afterModel -> $finalModel")
            } else {
                println("❌ 第二次切换失败！仍然是: $finalModel")
                fail("第二次 setModel() 没有生效")
            }

            println("\n=== 🎉 测试完成 ===")
            println("总结:")
            println("  初始模型: $initialModel")
            println("  第一次切换: $afterModel")
            println("  第二次切换: $finalModel")

        } catch (e: Exception) {
            println("❌ 测试失败: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            client.disconnect()
            println("\n🔌 已断开连接")
        }
    }

    @Test
    fun `test setModel with mock transport`() = runTest {
        println("=== 测试 setModel() 控制协议 ===\n")

        val mockTransport = MockTransport()
        val options = ClaudeCodeOptions(model = "claude-sonnet-4-20250514")
        val client = ClaudeCodeSdkClient(options, transport = mockTransport)

        // 连接
        val connectJob = launch { client.connect() }
        delay(50)
        mockTransport.sendMessage("""
            {
                "type": "control_response",
                "response": {
                    "subtype": "success",
                    "request_id": "req_1",
                    "response": {}
                }
            }
        """.trimIndent())
        connectJob.join()

        mockTransport.clearWrittenData()

        // 调用 setModel
        println("调用 client.setModel(\"claude-opus-4-20250514\")")
        val setModelJob = launch {
            client.setModel("claude-opus-4-20250514")
        }

        delay(100)

        // 检查发送的控制请求
        val writtenData = mockTransport.getWrittenData()
        println("发送的数据: $writtenData")

        assertTrue(writtenData.isNotEmpty(), "应该发送控制请求")

        val lastMessage = writtenData.last()
        assertTrue(lastMessage.contains("\"type\":\"control_request\""),
            "应该是控制请求")
        assertTrue(lastMessage.contains("\"set_model\""),
            "应该包含 set_model 类型")
        assertTrue(lastMessage.contains("claude-opus-4-20250514"),
            "应该包含目标模型 ID")

        println("✅ 控制请求格式正确")

        // 模拟成功响应
        mockTransport.sendMessage("""
            {
                "type": "control_response",
                "response": {
                    "subtype": "success",
                    "request_id": "req_2",
                    "response": {}
                }
            }
        """.trimIndent())

        setModelJob.join()
        println("✅ setModel() 完成")

        client.disconnect()
        println("\n=== 测试完成 ===")
    }
}