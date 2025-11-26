package com.asakii.claude.agent.sdk.examples


import com.asakii.claude.agent.sdk.ClaudeCodeSdkClient
import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking

/**
 * Example demonstrating partial message streaming (new in v0.1.0).
 *
 * When includePartialMessages is enabled, you receive StreamEvent messages
 * containing partial updates as Claude generates the response.
 */
fun main() = runBlocking {
    println("=== Partial Message Streaming Example ===\n")

    val options = ClaudeAgentOptions(
        includePartialMessages = true, // NEW: Enable partial message streaming
        allowedTools = listOf("Read", "Bash"),
        systemPrompt = "You are a helpful assistant.",
        maxTurns = 1
    )

    val client = ClaudeCodeSdkClient(options)

    try {
        println("Connecting to Claude...")
        client.connect()

        println("Sending query...\n")
        client.query("Explain how Kotlin coroutines work in 3 sentences.")

        println("Receiving response (with partial updates):\n")
        client.receiveResponse().collect { message ->
            when (message) {
                is StreamEvent -> {
                    // Partial message updates in real-time
                    println("[STREAM] ${message.event}")
                }

                is AssistantMessage -> {
                    // Complete message
                    println("\n[COMPLETE MESSAGE]")
                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> println(block.text)
                            is ThinkingBlock -> {
                                println("[THINKING]")
                                println(block.thinking)
                            }
                            else -> {}
                        }
                    }
                }

                is ResultMessage -> {
                    println("\n[RESULT]")
                    println("  Status: ${if (message.isError) "Error" else "Success"}")
                    println("  Turns: ${message.numTurns}")
                    println("  Duration: ${message.durationMs}ms")
                    println("  Cost: $${message.totalCostUsd ?: 0.0}")
                }

                else -> {
                    println("[${message::class.simpleName}]")
                }
            }
        }

    } finally {
        client.disconnect()
        println("\nDisconnected")
    }

    println("\n=== End of Streaming Example ===")
}
