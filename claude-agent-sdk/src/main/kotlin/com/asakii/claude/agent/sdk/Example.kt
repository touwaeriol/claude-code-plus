package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking

/**
 * Example usage of the Claude Code SDK Kotlin client.
 */
fun main() = runBlocking {
    // Basic configuration
    val options = ClaudeAgentOptions(
        model = "claude-3-5-sonnet",
        allowedTools = listOf("Read", "Write", "Bash"),
        permissionMode = PermissionMode.ACCEPT_EDITS
    )
    
    val client = ClaudeCodeSdkClient(options)
    
    try {
        println("Connecting to Claude...")
        client.connect()
        
        println("Sending query...")
        client.query("Hello Claude! Can you help me understand how to use this SDK?")
        
        println("Receiving response...")
        client.receiveResponse().take(5).collect { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> println("Claude: ${block.text}")
                            is ToolUseBlock -> println("Tool: ${block.name} with ID ${block.id}")
                            else -> println("Other content: $block")
                        }
                    }
                }
                is ResultMessage -> {
                    println("Result: ${message.result}, Duration: ${message.durationMs}ms")
                }
                else -> println("Other message: $message")
            }
        }
        
    } catch (e: Exception) {
        println("Error: ${e.message}")
        e.printStackTrace()
    } finally {
        client.disconnect()
        println("Disconnected.")
    }
}

/**
 * Example using the convenience functions.
 */
fun simpleExample() = runBlocking {
    try {
        val messages = claudeQuery(
            "What is 2 + 2?",
            ClaudeAgentOptions(model = "claude-3-5-sonnet")
        )
        
        messages.forEach { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.filterIsInstance<TextBlock>().forEach { block ->
                        println("Claude: ${block.text}")
                    }
                }
                else -> println("Message: $message")
            }
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}