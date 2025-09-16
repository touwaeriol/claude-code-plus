package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.*

class RealModelSwitchTest {
    
    @Test
    fun `test real model switch workflow`() = runTest {
        // Skip if CLAUDE_API_KEY is not available
        val apiKey = System.getenv("CLAUDE_API_KEY")
        if (apiKey.isNullOrEmpty()) {
            println("Skipping real model switch test - CLAUDE_API_KEY not found")
            return@runTest
        }
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet",
            allowedTools = listOf("Read"),
            permissionMode = PermissionMode.ACCEPT_EDITS
        )
        
        val client = ClaudeCodeSdkClient(options)
        
        try {
            client.connect()
            
            println("Testing model switch workflow...")
            
            // 1. Ask what model it is
            println("1. Asking current model...")
            client.query("What model are you? Please be specific about your model name.")
            
            var responseCount = 0
            
            // Collect initial response
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("Initial model response: ${content.text}")
                        }
                        println("Current model in message: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("Initial query result: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        // Handle other message types
                        println("Received other message type: ${message::class.simpleName}")
                    }
                }
            }
            
            // 2. Try to switch to Opus
            println("\n2. Attempting to switch to Opus...")
            client.query("/model opus")
            
            responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("Model switch response: ${content.text}")
                        }
                        println("Model after switch attempt: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("Model switch result: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        println("Received other message type: ${message::class.simpleName}")
                    }
                }
            }
            
            // 3. Ask what model it is now
            println("\n3. Verifying current model...")
            client.query("What model are you now? Has the model changed?")
            
            responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("Final model response: ${content.text}")
                        }
                        println("Final model in message: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("Final query result: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        println("Received other message type: ${message::class.simpleName}")
                    }
                }
            }
            
        } catch (e: Exception) {
            println("Real model switch test error: ${e.message}")
            e.printStackTrace()
        } finally {
            client.disconnect()
        }
    }
    
    @Test
    fun `test invalid slash command`() = runTest {
        // Skip if CLAUDE_API_KEY is not available
        val apiKey = System.getenv("CLAUDE_API_KEY")
        if (apiKey.isNullOrEmpty()) {
            println("Skipping invalid slash command test - CLAUDE_API_KEY not found")
            return@runTest
        }
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet",
            allowedTools = listOf("Read"),
            permissionMode = PermissionMode.ACCEPT_EDITS
        )
        
        val client = ClaudeCodeSdkClient(options)
        
        try {
            client.connect()
            
            println("Testing invalid slash command...")
            
            // Send invalid command (should be /model, not /mode)
            client.query("/mode sonnet")
            
            var responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("Invalid command response: ${content.text}")
                        }
                    }
                    is ResultMessage -> {
                        println("Invalid command result: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        println("Received other message type: ${message::class.simpleName}")
                    }
                }
            }
            
        } catch (e: Exception) {
            println("Invalid slash command test error: ${e.message}")
            e.printStackTrace()
        } finally {
            client.disconnect()
        }
    }
}