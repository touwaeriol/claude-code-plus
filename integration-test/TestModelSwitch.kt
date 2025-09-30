package integration

import com.claudecodeplus.core.preprocessor.SlashCommandInterceptor
import com.claudecodeplus.core.preprocessor.PreprocessResult
import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.ClaudeCodeOptions
import com.claudecodeplus.sdk.protocol.SystemInitMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import java.nio.file.Path

fun main() = runBlocking {
    println("=== 模型切换集成测试 ===\n")

    // 创建真实的 SDK 客户端
    val options = ClaudeCodeOptions(
        model = "claude-sonnet-4-20250514",  // 初始模型: Sonnet 4
        cwd = Path.of(System.getProperty("user.dir")),
        maxTurns = 3
    )

    val client = ClaudeCodeSdkClient(options)
    val interceptor = SlashCommandInterceptor()

    try {
        println("步骤 1: 连接 Claude CLI...")
        client.connect()
        println("✅ 连接成功\n")

        println("步骤 2: 发送初始查询（使用 Sonnet 4）")
        client.query("Hello", "test-session")
        val initialResponses = client.receiveResponse().toList()
        val initialSystemInit = initialResponses.filterIsInstance<SystemInitMessage>().firstOrNull()
        println("初始模型信息: ${initialSystemInit?.model ?: "未找到"}\n")

        println("步骤 3: 使用拦截器切换到 Opus")
        val result = interceptor.preprocess("/model opus", client, "test-session")
        when (result) {
            is PreprocessResult.Intercepted -> {
                println("✅ 命令被拦截")
                println("   反馈: ${result.feedback}")
            }
            is PreprocessResult.Continue -> {
                println("❌ 命令未被拦截（不应该发生）")
            }
        }
        println()

        println("步骤 4: 发送新查询验证模型切换")
        client.query("Hello again", "test-session")
        val newResponses = client.receiveResponse().toList()
        val newSystemInit = newResponses.filterIsInstance<SystemInitMessage>().firstOrNull()
        println("切换后模型信息: ${newSystemInit?.model ?: "未找到"}")

        if (newSystemInit?.model?.contains("opus", ignoreCase = true) == true) {
            println("✅ 模型切换成功！")
        } else {
            println("❌ 模型切换失败！")
        }
        println()

        println("步骤 5: 切换到 Haiku")
        val result2 = interceptor.preprocess("/model haiku", client, "test-session")
        when (result2) {
            is PreprocessResult.Intercepted -> {
                println("✅ 命令被拦截")
                println("   反馈: ${result2.feedback}")
            }
            is PreprocessResult.Continue -> {
                println("❌ 命令未被拦截（不应该发生）")
            }
        }
        println()

        println("步骤 6: 最后验证")
        client.query("Final hello", "test-session")
        val finalResponses = client.receiveResponse().toList()
        val finalSystemInit = finalResponses.filterIsInstance<SystemInitMessage>().firstOrNull()
        println("最终模型信息: ${finalSystemInit?.model ?: "未找到"}")

        if (finalSystemInit?.model?.contains("haiku", ignoreCase = true) == true) {
            println("✅ 模型切换成功！")
        } else {
            println("❌ 模型切换失败！")
        }

        println("\n=== 测试完成 ===")

    } catch (e: Exception) {
        println("❌ 测试失败: ${e.message}")
        e.printStackTrace()
    } finally {
        client.disconnect()
        println("\n✅ 已断开连接")
    }
}