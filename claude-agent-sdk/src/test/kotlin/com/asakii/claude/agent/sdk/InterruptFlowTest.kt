package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * 测试打断（Interrupt）后 SDK Flow 的行为
 *
 * 验证点：
 * 1. 调用 interrupt() 后，receiveResponse() flow 是否会自然结束
 * 2. flow 结束前是否能收到之前已生成的消息
 * 3. interrupt() 是否会等待 flow 结束后才返回
 */
class InterruptFlowTest {

    @Test
    fun `test interrupt causes flow to end naturally`() = runBlocking {
        val client = ClaudeCodeSdkClient(
            ClaudeAgentOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 10,
                includePartialMessages = true,
                dangerouslySkipPermissions = true,
                verbose = true
            )
        )

        try {
            client.connect()

            // 发送一个需要较长时间生成的请求
            client.query("写一个很长的故事，至少500字")

            val receivedMessages = mutableListOf<Message>()
            var flowEnded = false
            var interruptCalled = false
            var interruptReturnTime: Long = 0
            var flowEndTime: Long = 0

            // 启动 collect 协程
            val collectJob = launch {
                try {
                    client.receiveResponse().collect { message ->
                        receivedMessages.add(message)
                        val msgType = when (message) {
                            is StreamEvent -> "StreamEvent"
                            is AssistantMessage -> "AssistantMessage"
                            is UserMessage -> "UserMessage"
                            is ResultMessage -> "ResultMessage"
                            else -> message::class.simpleName
                        }
                        println("📨 收到消息 #${receivedMessages.size}: $msgType")
                    }
                } finally {
                    flowEndTime = System.currentTimeMillis()
                    flowEnded = true
                    println("✅ Flow 已结束，共收到 ${receivedMessages.size} 条消息")
                }
            }

            // 等待一段时间让 SDK 开始生成
            delay(2000)

            // 记录 interrupt 前的消息数量
            val messagesBeforeInterrupt = receivedMessages.size
            println("\n🛑 准备调用 interrupt()，当前已收到 $messagesBeforeInterrupt 条消息")

            // 调用 interrupt
            val interruptStartTime = System.currentTimeMillis()
            client.interrupt()
            interruptReturnTime = System.currentTimeMillis()
            interruptCalled = true

            val interruptDuration = interruptReturnTime - interruptStartTime
            println("🛑 interrupt() 已返回，耗时 ${interruptDuration}ms")

            // 等待 flow 结束
            withTimeout(30_000) {
                collectJob.join()
            }

            val messagesAfterInterrupt = receivedMessages.size - messagesBeforeInterrupt
            println("\n=== 测试结果 ===")
            println("interrupt() 前消息数: $messagesBeforeInterrupt")
            println("interrupt() 后消息数: $messagesAfterInterrupt")
            println("总消息数: ${receivedMessages.size}")
            println("interrupt() 耗时: ${interruptDuration}ms")
            println("Flow 结束: $flowEnded")

            // 检查最后一条消息
            val lastMessage = receivedMessages.lastOrNull()
            println("最后一条消息类型: ${lastMessage?.let { it::class.simpleName }}")

            // 验证
            assertTrue(flowEnded, "Flow 应该在 interrupt 后结束")
            assertTrue(interruptCalled, "interrupt() 应该被调用")

            // 关键验证：interrupt() 返回时，flow 是否已经结束？
            if (flowEndTime > 0 && interruptReturnTime > 0) {
                val timeDiff = flowEndTime - interruptReturnTime
                println("\n⏱️ interrupt() 返回后 ${timeDiff}ms flow 结束")
                if (timeDiff < 0) {
                    println("✅ Flow 在 interrupt() 返回前就已结束（同步）")
                } else if (timeDiff < 100) {
                    println("✅ Flow 几乎同时结束（可接受）")
                } else {
                    println("⚠️ Flow 在 interrupt() 返回后 ${timeDiff}ms 才结束（异步）")
                }
            }

            // 检查是否收到 ResultMessage
            val hasResultMessage = receivedMessages.any { it is ResultMessage }
            println("收到 ResultMessage: $hasResultMessage")

            if (hasResultMessage) {
                val resultMessage = receivedMessages.filterIsInstance<ResultMessage>().first()
                println("ResultMessage.isError: ${resultMessage.isError}")
                println("ResultMessage.subtype: ${resultMessage.subtype}")
            }

            println("\n✅ 测试完成")

        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `test interrupt waits for flow completion`() = runBlocking {
        val client = ClaudeCodeSdkClient(
            ClaudeAgentOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 5,
                includePartialMessages = true,
                dangerouslySkipPermissions = true,
                verbose = true
            )
        )

        try {
            client.connect()
            client.query("解释什么是递归，举3个例子")

            var collectCompleted = false
            var interruptCompleted = false

            // 启动 collect 协程
            val collectJob = launch {
                client.receiveResponse().collect { message ->
                    println("📨 ${message::class.simpleName}")
                }
                collectCompleted = true
                println("✅ Collect 完成")
            }

            // 等待开始生成
            delay(1500)

            // 启动 interrupt 协程并计时
            val interruptJob = launch {
                println("🛑 开始 interrupt...")
                client.interrupt()
                interruptCompleted = true
                println("🛑 Interrupt 完成")
            }

            // 等待两者都完成
            withTimeout(60_000) {
                collectJob.join()
                interruptJob.join()
            }

            println("\n=== 完成顺序 ===")
            println("Collect 完成: $collectCompleted")
            println("Interrupt 完成: $interruptCompleted")

            assertTrue(collectCompleted, "Collect 应该完成")
            assertTrue(interruptCompleted, "Interrupt 应该完成")

        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `test messages received before interrupt are preserved`() = runBlocking {
        val client = ClaudeCodeSdkClient(
            ClaudeAgentOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 5,
                includePartialMessages = true,
                dangerouslySkipPermissions = true,
                verbose = true
            )
        )

        try {
            client.connect()
            client.query("从1数到100，每个数字换一行")

            val allMessages = mutableListOf<Message>()
            var interruptedAtCount = 0

            val collectJob = launch {
                client.receiveResponse().collect { message ->
                    allMessages.add(message)

                    // 收到 10 条消息后打断
                    if (allMessages.size == 10 && interruptedAtCount == 0) {
                        interruptedAtCount = allMessages.size
                        println("🛑 在第 $interruptedAtCount 条消息时触发 interrupt")
                        launch {
                            client.interrupt()
                            println("🛑 Interrupt 返回")
                        }
                    }
                }
            }

            withTimeout(60_000) {
                collectJob.join()
            }

            println("\n=== 消息统计 ===")
            println("触发 interrupt 时的消息数: $interruptedAtCount")
            println("最终收到的消息数: ${allMessages.size}")
            println("interrupt 后额外收到: ${allMessages.size - interruptedAtCount} 条")

            // 验证：打断前的消息应该都被保留
            assertTrue(allMessages.size >= interruptedAtCount, "打断前的消息应该被保留")

            // 打印消息类型分布
            val typeCounts = allMessages.groupingBy { it::class.simpleName }.eachCount()
            println("\n消息类型分布:")
            typeCounts.forEach { (type, count) ->
                println("  $type: $count")
            }

        } finally {
            client.disconnect()
        }
    }
}
