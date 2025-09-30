package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * 真实模型切换测试 - 输出实际切换到的模型
 *
 * 这个测试会：
 * 1. 调用 client.setModel("opus")
 * 2. 发送查询并输出响应中的 model 字段
 * 3. 再切换到 "sonnet" 并输出模型字段
 */
class RealModelOutputTest {

    @Test
    fun `test model switch and print actual model used`() = runBlocking {
        val apiKey = System.getenv("CLAUDE_API_KEY")
        if (apiKey.isNullOrEmpty()) {
            println("⏭️  跳过测试 - 需要设置 CLAUDE_API_KEY 环境变量")
            println("   运行方式: export CLAUDE_API_KEY=\"your-key\" && ./gradlew test")
            return@runBlocking
        }

        println("=".repeat(60))
        println("🧪 真实模型切换测试")
        println("=".repeat(60))

        val options = ClaudeCodeOptions(
            model = "claude-sonnet-4-20250514",  // 初始: Sonnet 4
            maxTurns = 5
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            // === 步骤 1: 连接 ===
            println("\n[步骤 1] 连接 Claude CLI...")
            client.connect()
            println("✅ 连接成功")

            // === 步骤 2: 初始查询（Sonnet 4）===
            println("\n[步骤 2] 使用初始模型发送查询")
            println("初始模型配置: claude-sonnet-4-20250514")
            client.query("Hello", "test")

            var initialModel: String? = null
            client.receiveResponse().collect { message ->
                if (message is AssistantMessage) {
                    initialModel = message.model
                    println("📋 收到响应，模型字段: ${message.model}")
                    println("   内容: ${message.content.firstOrNull()?.toString()?.take(100)}")
                }
            }

            println("\n✅ 初始模型: $initialModel")

            // === 步骤 3: 切换到 opus ===
            println("\n[步骤 3] 调用 client.setModel(\"opus\")")
            client.setModel("opus")
            println("✅ setModel() 调用完成（无异常）")

            // === 步骤 4: 切换后查询 ===
            println("\n[步骤 4] 切换后发送查询")
            client.query("Hello again", "test")

            var opusModel: String? = null
            client.receiveResponse().collect { message ->
                if (message is AssistantMessage) {
                    opusModel = message.model
                    println("📋 收到响应，模型字段: ${message.model}")
                    println("   内容: ${message.content.firstOrNull()?.toString()?.take(100)}")
                }
            }

            println("\n✅ Opus 模型: $opusModel")

            // === 步骤 5: 切换到 sonnet ===
            println("\n[步骤 5] 调用 client.setModel(\"sonnet\")")
            client.setModel("sonnet")
            println("✅ setModel() 调用完成")

            // === 步骤 6: 最后查询 ===
            println("\n[步骤 6] 切换到 sonnet 后发送查询")
            client.query("Final test", "test")

            var sonnetModel: String? = null
            client.receiveResponse().collect { message ->
                if (message is AssistantMessage) {
                    sonnetModel = message.model
                    println("📋 收到响应，模型字段: ${message.model}")
                    println("   内容: ${message.content.firstOrNull()?.toString()?.take(100)}")
                }
            }

            println("\n✅ Sonnet 模型: $sonnetModel")

            // === 最终报告 ===
            println("\n" + "=".repeat(60))
            println("📊 测试结果汇总")
            println("=".repeat(60))
            println("初始模型 (claude-sonnet-4-20250514): $initialModel")
            println("切换后 (setModel(\"opus\")):         $opusModel")
            println("切换后 (setModel(\"sonnet\")):       $sonnetModel")
            println("=".repeat(60))

            // 分析结果
            println("\n🔍 分析:")
            if (opusModel?.contains("opus", ignoreCase = true) == true) {
                println("✅ setModel(\"opus\") 生效了！模型切换到: $opusModel")
            } else {
                println("⚠️  setModel(\"opus\") 可能没生效，模型仍然是: $opusModel")
            }

            if (sonnetModel?.contains("sonnet", ignoreCase = true) == true) {
                println("✅ setModel(\"sonnet\") 生效了！模型切换到: $sonnetModel")
            } else {
                println("⚠️  setModel(\"sonnet\") 可能没生效，模型仍然是: $sonnetModel")
            }

        } catch (e: Exception) {
            println("\n❌ 测试失败: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            client.disconnect()
            println("\n🔌 已断开连接")
        }
    }
}