package standalone

import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.types.AssistantMessage
import com.claudecodeplus.sdk.types.ClaudeCodeOptions
import com.claudecodeplus.sdk.types.ResultMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

fun main() = runBlocking {
    // 注意：Gradle run 任务可能无法正确传递环境变量
    // 如果 CLAUDE_API_KEY 未设置，程序会在连接时失败
    val apiKey = System.getenv("CLAUDE_API_KEY")
    println("环境变量检查: CLAUDE_API_KEY = ${if (apiKey.isNullOrEmpty()) "未设置" else "已设置(${apiKey.take(10)}...)"}")

    if (apiKey.isNullOrEmpty()) {
        println("⚠️  警告: CLAUDE_API_KEY 未设置，将尝试继续运行...")
        println("   如果失败，请直接设置环境变量并运行")
    }

    println("=".repeat(60))
    println("🧪 真实模型切换测试")
    println("=".repeat(60))

    val options = ClaudeCodeOptions(
        model = "claude-sonnet-4-20250514",
        maxTurns = 5
    )

    val client = ClaudeCodeSdkClient(options)

    try {
        println("\n[步骤 1] 连接 Claude CLI...")
        client.connect()
        println("✅ 连接成功\n")

        // 初始查询
        println("[步骤 2] 初始模型查询...")
        client.query("What is 2+2?", "test")

        var initialModel: String? = null
        withTimeout(15000) {
            client.receiveResponse().collect { message ->
                if (message is AssistantMessage) {
                    initialModel = message.model
                    println("📋 收到响应")
                    println("   模型字段: ${message.model}")
                    println("   内容预览: ${message.content.firstOrNull()?.toString()?.take(80)}...")
                }
                if (message is ResultMessage) {
                    println("✅ 收到ResultMessage，结束收集")
                }
            }
        }

        // 切换到 opus
        println("\n[步骤 3] 调用 setModel(\"opus\")...")
        client.setModel("opus")
        println("✅ setModel() 调用完成\n")

        // Opus 查询
        println("[步骤 4] 使用 Opus 查询...")
        client.query("What is 3+3?", "test")

        var opusModel: String? = null
        withTimeout(15000) {
            client.receiveResponse().collect { message ->
                if (message is AssistantMessage) {
                    opusModel = message.model
                    println("📋 收到响应")
                    println("   模型字段: ${message.model}")
                    println("   内容预览: ${message.content.firstOrNull()?.toString()?.take(80)}...")
                }
                if (message is ResultMessage) {
                    println("✅ 收到ResultMessage，结束收集")
                }
            }
        }

        // 切换到 sonnet
        println("\n[步骤 5] 调用 setModel(\"sonnet\")...")
        client.setModel("sonnet")
        println("✅ setModel() 调用完成\n")

        // Sonnet 查询
        println("[步骤 6] 使用 Sonnet 查询...")
        client.query("What is 5+5?", "test")

        var sonnetModel: String? = null
        withTimeout(15000) {
            client.receiveResponse().collect { message ->
                if (message is AssistantMessage) {
                    sonnetModel = message.model
                    println("📋 收到响应")
                    println("   模型字段: ${message.model}")
                    println("   内容预览: ${message.content.firstOrNull()?.toString()?.take(80)}...")
                }
                if (message is ResultMessage) {
                    println("✅ 收到ResultMessage，结束收集")
                }
            }
        }

        // 结果汇总
        println("\n" + "=".repeat(60))
        println("📊 测试结果汇总")
        println("=".repeat(60))
        println("初始模型 (配置: claude-sonnet-4-20250514)")
        println("  实际: $initialModel")
        println("")
        println("调用 setModel(\"opus\") 后")
        println("  实际: $opusModel")
        println("")
        println("调用 setModel(\"sonnet\") 后")
        println("  实际: $sonnetModel")
        println("=".repeat(60))

        // 分析
        println("\n🔍 分析:")
        if (opusModel?.contains("opus", ignoreCase = true) == true) {
            println("✅ setModel(\"opus\") 生效！模型切换到: $opusModel")
        } else {
            println("⚠️  setModel(\"opus\") 可能没生效，模型是: $opusModel")
        }

        if (sonnetModel?.contains("sonnet", ignoreCase = true) == true) {
            println("✅ setModel(\"sonnet\") 生效！模型切换到: $sonnetModel")
        } else {
            println("⚠️  setModel(\"sonnet\") 可能没生效，模型是: $sonnetModel")
        }

    } catch (e: Exception) {
        println("\n❌ 测试失败: ${e.message}")
        e.printStackTrace()
    } finally {
        client.disconnect()
        println("\n🔌 已断开连接")
    }
}