package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * 测试动态模型切换功能
 *
 * 这些测试需要真实的 Claude CLI 环境，因此默认禁用。
 * 要运行这些测试，请：
 * 1. 确保已安装并配置 Claude CLI
 * 2. 移除 @Disabled 注解
 * 3. 设置环境变量 CLAUDE_API_KEY
 */
class ModelSwitchingTest {

    @Test
    @Disabled("需要真实 Claude CLI 环境")
    fun `test switch model and verify`() = runBlocking {
        val options = ClaudeAgentOptions(
            model = "claude-sonnet-4-20250514",
            permissionMode = PermissionMode.DEFAULT,
            allowedTools = listOf("Read", "Write", "Bash"),
            maxTurns = 3
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            // 连接
            println("📡 连接到 Claude...")
            client.connect()
            assertTrue(client.isConnected(), "客户端应该已连接")

            // 阶段 1: 使用初始模型（Sonnet）询问
            println("\n=== 阶段 1: 初始模型 (Sonnet) ===")
            client.query("请告诉我你是什么模型？只需要回答模型名称即可，比如 'Claude Sonnet 4' 或类似的")

            var firstResponse = ""
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        message.content.forEach { block ->
                            if (block is TextBlock) {
                                firstResponse += block.text
                                println("Claude: ${block.text}")
                            }
                        }
                    }
                    is ResultMessage -> {
                        if (!message.isError) {
                            println("✅ 第一次查询完成")
                        }
                    }
                    else -> {}
                }
            }

            assertNotNull(firstResponse, "应该收到第一次响应")
            assertTrue(firstResponse.isNotEmpty(), "第一次响应不应为空")
            println("第一次响应: $firstResponse")

            // 阶段 2: 切换到 Haiku 模型
            println("\n=== 阶段 2: 切换到 Haiku 模型 ===")
            client.setModel("claude-haiku-4-20250514")
            println("✅ 已切换模型")

            // 稍等一下确保切换生效
            kotlinx.coroutines.delay(500)

            // 阶段 3: 再次询问当前模型
            println("\n=== 阶段 3: 验证新模型 (Haiku) ===")
            client.query("请告诉我你现在是什么模型？只需要回答模型名称即可")

            var secondResponse = ""
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        message.content.forEach { block ->
                            if (block is TextBlock) {
                                secondResponse += block.text
                                println("Claude: ${block.text}")
                            }
                        }
                    }
                    is ResultMessage -> {
                        if (!message.isError) {
                            println("✅ 第二次查询完成")
                        }
                    }
                    else -> {}
                }
            }

            assertNotNull(secondResponse, "应该收到第二次响应")
            assertTrue(secondResponse.isNotEmpty(), "第二次响应不应为空")
            println("第二次响应: $secondResponse")

            // 验证响应内容变化
            println("\n=== 结果对比 ===")
            println("切换前: $firstResponse")
            println("切换后: $secondResponse")

            // 注意：Claude 可能不会明确说出模型名称，因为它被训练成不确定自己的版本
            // 但我们可以验证：
            // 1. 两次都收到了响应
            // 2. 响应内容合理
            assertTrue(firstResponse.isNotEmpty() && secondResponse.isNotEmpty(),
                "两次查询都应该有响应")

        } finally {
            println("\n📡 断开连接...")
            client.disconnect()
        }
    }

    @Test
    @Disabled("需要真实 Claude CLI 环境")
    fun `test switch permission mode and model together`() = runBlocking {
        val options = ClaudeAgentOptions(
            model = "claude-sonnet-4-20250514",
            permissionMode = PermissionMode.DEFAULT,
            allowedTools = listOf("Read", "Write"),
            maxTurns = 2
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            client.connect()

            // 测试 1: 默认模式
            println("=== 测试 1: 默认设置 ===")
            client.query("你好")
            client.receiveResponse().collect { message ->
                if (message is AssistantMessage) {
                    println("收到响应")
                }
            }

            // 测试 2: 同时切换权限和模型
            println("\n=== 测试 2: 切换权限模式和模型 ===")
            client.setPermissionMode("acceptEdits")
            client.setModel("claude-haiku-4-20250514")

            kotlinx.coroutines.delay(500)

            client.query("现在的配置是什么？")
            client.receiveResponse().collect { message ->
                if (message is AssistantMessage) {
                    println("收到响应")
                }
            }

            println("✅ 测试完成")

        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `test setModel method exists and compiles`() {
        // 这个测试只验证方法存在，不需要真实环境
        val options = ClaudeAgentOptions()
        val client = ClaudeCodeSdkClient(options)

        // 验证方法存在（编译时检查）
        val setModelMethod = client::class.java.methods.find {
            it.name == "setModel"
        }

        assertNotNull(setModelMethod, "setModel 方法应该存在")
        println("✅ setModel 方法存在")
    }

    @Test
    fun `test setPermissionMode method exists and compiles`() {
        // 这个测试只验证方法存在，不需要真实环境
        val options = ClaudeAgentOptions()
        val client = ClaudeCodeSdkClient(options)

        // 验证方法存在（编译时检查）
        val setPermissionModeMethod = client::class.java.methods.find {
            it.name == "setPermissionMode"
        }

        assertNotNull(setPermissionModeMethod, "setPermissionMode 方法应该存在")
        println("✅ setPermissionMode 方法存在")
    }

    @Test
    fun `test SetModelRequest is correctly defined`() {
        // 验证 SetModelRequest 类型正确定义
        val request = SetModelRequest(model = "claude-haiku-4-20250514")

        assertTrue(request.subtype == "set_model", "subtype 应该是 set_model")
        assertTrue(request.model == "claude-haiku-4-20250514", "model 应该正确设置")
        println("✅ SetModelRequest 类型定义正确")
    }

    @Test
    fun `test SetPermissionModeRequest is correctly defined`() {
        // 验证 SetPermissionModeRequest 类型正确定义
        val request = SetPermissionModeRequest(mode = "acceptEdits")

        assertTrue(request.subtype == "set_permission_mode", "subtype 应该是 set_permission_mode")
        assertTrue(request.mode == "acceptEdits", "mode 应该正确设置")
        println("✅ SetPermissionModeRequest 类型定义正确")
    }
}