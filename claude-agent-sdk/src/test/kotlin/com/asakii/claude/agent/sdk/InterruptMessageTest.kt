package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test

/**
 * 专门测试打断后 Claude Code CLI 输出的消息内容
 * 详细打印每条消息的完整内容，查看是否有打断标记字段
 */
class InterruptMessageTest {

    @Test
    fun `test interrupt message content details`() = runBlocking {
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
            client.query("Write a long story, at least 500 words")

            val allMessages = mutableListOf<Message>()

            val collectJob = launch {
                client.receiveResponse().collect { message ->
                    allMessages.add(message)

                    when (message) {
                        is UserMessage -> {
                            println("\n" + "=".repeat(50))
                            println("UserMessage:")
                            println("  content: ${message.content}")
                            println("  parentToolUseId: ${message.parentToolUseId}")
                            println("  sessionId: ${message.sessionId}")
                            println("=".repeat(50))
                        }
                        is ResultMessage -> {
                            println("\n" + "=".repeat(50))
                            println("ResultMessage:")
                            println("  subtype: ${message.subtype}")
                            println("  isError: ${message.isError}")
                            println("  numTurns: ${message.numTurns}")
                            println("  durationMs: ${message.durationMs}")
                            println("  result: ${message.result}")
                            println("=".repeat(50))
                        }
                        is AssistantMessage -> {
                            println("AssistantMessage: ${message.content.size} blocks")
                        }
                        is StreamEvent -> {
                            val eventStr = message.event.toString()
                            if (eventStr.contains("message_stop") || eventStr.contains("interrupt")) {
                                println("StreamEvent (key): $eventStr")
                            }
                        }
                        is SystemMessage -> {
                            println("\n" + "=".repeat(50))
                            println("SystemMessage:")
                            println("  subtype: ${message.subtype}")
                            println("  data: ${message.data}")
                            println("=".repeat(50))
                        }
                        else -> {
                            println("Other: ${message::class.simpleName}")
                        }
                    }
                }
            }

            delay(3000)

            println("\n" + "#".repeat(60))
            println("### CALLING interrupt() ###")
            println("#".repeat(60) + "\n")

            client.interrupt()

            println("\n" + "#".repeat(60))
            println("### interrupt() RETURNED ###")
            println("#".repeat(60) + "\n")

            withTimeout(30_000) {
                collectJob.join()
            }

            println("\n" + "=".repeat(60))
            println("=== SUMMARY ===")
            println("=".repeat(60))
            println("Total messages: ${allMessages.size}")

            val userMessages = allMessages.filterIsInstance<UserMessage>()
            println("\nUserMessage count: ${userMessages.size}")
            userMessages.forEachIndexed { index, msg ->
                println("  [$index] content: ${msg.content}")
            }

            val resultMessages = allMessages.filterIsInstance<ResultMessage>()
            println("\nResultMessage count: ${resultMessages.size}")
            resultMessages.forEachIndexed { index, msg ->
                println("  [$index] subtype=${msg.subtype}, isError=${msg.isError}")
            }

            println("\nMessage type distribution:")
            allMessages.groupingBy { it::class.simpleName }.eachCount().forEach { (type, count) ->
                println("  $type: $count")
            }

        } finally {
            client.disconnect()
        }
    }
}
