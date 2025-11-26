package standalone

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.client.AgentMessageInput
import com.asakii.ai.agent.sdk.client.ClaudeAgentClientImpl
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.connect.ClaudeOverrides
import com.asakii.ai.agent.sdk.model.*
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.collect

/**
 * AI Agent SDK 流式返回测试
 * 使用 Sonnet 模型测试流式响应
 */
fun main() = runBlocking {
    println("=".repeat(60))
    println("🧪 AI Agent SDK 流式返回测试 (Sonnet 模型)")
    println("=".repeat(60))

    // 检查环境变量
    val apiKey = System.getenv("CLAUDE_API_KEY")
    println("📋 环境变量检查:")
    println("   CLAUDE_API_KEY = ${if (apiKey.isNullOrEmpty()) "❌ 未设置" else "✅ 已设置(${apiKey.take(8)}...)"}")
    println()

    // 创建 Claude Agent 客户端
    val client = ClaudeAgentClientImpl()

    // 配置 Claude 选项
    val claudeOptions = ClaudeAgentOptions(
        model = "claude-sonnet-4-20250514",
        maxTurns = 3,
        print = true,
        verbose = true,
        includePartialMessages = true,
        dangerouslySkipPermissions = true,
        allowDangerouslySkipPermissions = true
    )

    // 连接选项
    val connectOptions = AiAgentConnectOptions(
        provider = AiAgentProvider.CLAUDE,
        sessionId = "test-stream-${System.currentTimeMillis()}",
        claude = ClaudeOverrides(options = claudeOptions)
    )

    try {
        println("[步骤 1] 连接到 Claude...")
        client.connect(connectOptions)
        println("✅ 连接成功\n")

        // 启动事件收集协程
        println("[步骤 2] 启动流式事件监听...")
        var eventCount = 0
        var textContent = StringBuilder()
        var receivedComplete = false

        val collectJob = launch {
            client.streamEvents()
                .onEach { event ->
                    eventCount++
                    handleStreamEvent(event, eventCount, textContent)
                    if (event is UiMessageComplete || event is UiError) {
                        receivedComplete = true
                    }
                }
                .catch { e ->
                    println("❌ 流式事件错误: ${e.message}")
                }
                .collect()
        }

        // 发送测试消息
        println("[步骤 3] 发送测试消息...")
        println("   问题: \"请用中文简短介绍一下 Kotlin 语言的特点\"\n")

        client.sendMessage(AgentMessageInput(
            text = "请用中文简短介绍一下 Kotlin 语言的特点，限制在100字以内"
        ))

        // 等待响应完成
        println("\n[步骤 4] 等待响应完成...")
        withTimeout(60000) {
            while (!receivedComplete) {
                delay(100)
            }
        }

        // 取消收集任务
        collectJob.cancelAndJoin()

        // 结果汇总
        println("\n" + "=".repeat(60))
        println("📊 测试结果汇总")
        println("=".repeat(60))
        println("✅ 流式事件总数: $eventCount")
        println("✅ 最终文本内容:\n$textContent")
        println("=".repeat(60))

    } catch (e: Exception) {
        println("\n❌ 测试失败: ${e.message}")
        e.printStackTrace()
    } finally {
        println("\n[清理] 断开连接...")
        client.disconnect()
        println("🔌 已断开连接")
    }
}

/**
 * 处理不同类型的流式事件
 */
private fun handleStreamEvent(event: UiStreamEvent, count: Int, textContent: StringBuilder) {
    when (event) {
        is UiMessageStart -> {
            println("   📨 [$count] MessageStart - 消息ID: ${event.messageId}")
        }
        is UiTextDelta -> {
            print(event.text) // 实时输出文本增量
            textContent.append(event.text)
        }
        is UiThinkingDelta -> {
            println("   🤔 [$count] ThinkingDelta: ${event.thinking.take(50)}...")
        }
        is UiToolStart -> {
            println("   🔧 [$count] ToolStart - 工具: ${event.toolName}, ID: ${event.toolId}")
        }
        is UiToolProgress -> {
            println("   ⏳ [$count] ToolProgress - ID: ${event.toolId}")
        }
        is UiToolComplete -> {
            println("   ✅ [$count] ToolComplete - ID: ${event.toolId}")
        }
        is UiMessageComplete -> {
            println("\n   🎉 [$count] MessageComplete - 消息结束")
        }
        is UiError -> {
            println("   ❌ [$count] Error: ${event.message}")
        }
        else -> {
            println("   📌 [$count] 其他事件: ${event::class.simpleName}")
        }
    }
}
