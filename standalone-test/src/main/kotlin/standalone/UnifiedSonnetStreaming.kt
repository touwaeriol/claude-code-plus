package standalone

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.client.AgentMessageInput
import com.asakii.ai.agent.sdk.client.UnifiedAgentClientFactory
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.connect.ClaudeOverrides
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Instant

/**
 * 使用 ai-agent-sdk 直连 Claude Sonnet 并验证流式事件输出。
 */
fun main() = runBlocking {
    val conversationId = "sonnet-stream-${Instant.now().epochSecond}"
    val client = UnifiedAgentClientFactory.create(AiAgentProvider.CLAUDE)

    val connectOptions = AiAgentConnectOptions(
        provider = AiAgentProvider.CLAUDE,
        model = "claude-sonnet-4-20250514",
        // 避免 --continue-conversation 选项与新版 CLI 不兼容
        sessionId = null,
        claude = ClaudeOverrides(
            options = ClaudeAgentOptions(
                includePartialMessages = true,
                print = true,
                verbose = true,
                // 与用户要求的 CLI 参数保持一致
                dangerouslySkipPermissions = true,
                allowDangerouslySkipPermissions = true,
                extraArgs = mapOf("output-format" to "stream-json")
            )
        )
    )

    println("会话 ID: $conversationId")
    println("正在连接 Claude Sonnet 流式通道...")

    try {
        client.connect(connectOptions)
        println("连接成功，开始监听事件流\n")

        val streamJob = launch {
            client.streamEvents().collect { event ->
                println("[stream] $event")
            }
        }

        println("发送测试消息以验证流式返回...")
        client.sendMessage(
            AgentMessageInput(
                text = "请用一两句话回应，确保可以看到流式分段输出。",
                sessionId = conversationId
            )
        )

        // 等待一小段时间观察流式输出
        withTimeout(15_000) {
            delay(10_000)
        }

        println("\n断开连接...")
        client.disconnect()
        streamJob.cancel()
        println("测试完成")
    } catch (t: Throwable) {
        println("测试失败: ${t.message}")
        t.printStackTrace()
    }
}
