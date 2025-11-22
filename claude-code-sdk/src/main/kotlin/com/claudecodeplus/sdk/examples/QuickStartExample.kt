package com.claudecodeplus.sdk.examples


import com.claudecodeplus.sdk.query
import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.runBlocking

/**
 * Quick start example demonstrating the simplest way to use Claude Agent SDK.
 *
 * This example shows:
 * 1. Simple query with default options
 * 2. Query with custom options
 * 3. Using system prompt presets
 * 4. Working with different message types
 */
fun main() = runBlocking {
    println("=== Claude Agent SDK - Quick Start ===\n")

    // Example 1: Simple query
    println("1. Simple query:")
    query("What is 2 + 2?").collect { message ->
        when (message) {
            is AssistantMessage -> {
                message.content.forEach { block ->
                    if (block is TextBlock) {
                        println("Claude: ${block.text}")
                    }
                }
            }
            is ResultMessage -> {
                println("Done! (${message.numTurns} turns, ${message.durationMs}ms)")
            }
            else -> {} // Ignore other message types
        }
    }

    println("\n" + "=".repeat(50) + "\n")

    // Example 2: Query with custom options
    println("2. Query with custom options:")
    val options = ClaudeAgentOptions(
        model = "claude-sonnet-4-20250514",
        maxTurns = 1,
        allowedTools = listOf("Read"),
        systemPrompt = "You are a helpful assistant. Be concise."
    )

    query("Explain what Kotlin is in one sentence", options).collect { message ->
        when (message) {
            is AssistantMessage -> {
                message.content.forEach { block ->
                    when (block) {
                        is TextBlock -> println("Claude: ${block.text}")
                        is ThinkingBlock -> println("[Thinking: ${block.thinking.take(50)}...]")
                        else -> {}
                    }
                }
            }
            is ResultMessage -> {
                println("Cost: $${message.totalCostUsd ?: 0.0}")
            }
            else -> {}
        }
    }

    println("\n" + "=".repeat(50) + "\n")

    // Example 3: Using Claude Code preset
    println("3. Using Claude Code system prompt preset:")
    val claudeCodeOptions = ClaudeAgentOptions(
        systemPrompt = SystemPromptPreset(
            preset = "claude_code",
            append = "Always explain your reasoning."
        ),
        permissionMode = PermissionMode.ACCEPT_EDITS,
        allowedTools = listOf("Read", "Write", "Bash")
    )

    query("What files are in the current directory?", claudeCodeOptions).collect { message ->
        when (message) {
            is AssistantMessage -> {
                message.content.forEach { block ->
                    when (block) {
                        is TextBlock -> println(block.text)
                        is ToolUseBlock -> println("[Tool: ${block.name}]")
                        else -> {}
                    }
                }
            }
            is ResultMessage -> {
                if (message.isError) {
                    println("Error: ${message.result}")
                } else {
                    println("✓ Success")
                }
            }
            else -> {}
        }
    }

    println("\n=== End of Quick Start ===")
}
