package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.*

class CompleteModelSwitchTest {
    
    @Test
    fun `test complete model switch workflow`() = runTest {
        // Skip if CLAUDE_API_KEY is not available
        val apiKey = System.getenv("CLAUDE_API_KEY")
        if (apiKey.isNullOrEmpty()) {
            println("Skipping complete model switch test - CLAUDE_API_KEY not found")
            return@runTest
        }
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet",
            allowedTools = listOf("Read", "Write"),
            permissionMode = PermissionMode.ACCEPT_EDITS
        )
        
        val client = ClaudeCodeSdkClient(options)
        
        try {
            // 1. Connect
            println("=== 1. Connecting to Claude Code CLI ===")
            client.connect()
            println("✅ Connected successfully")
            
            // 2. Switch to Opus
            println("\n=== 2. Switching to Opus ===")
            client.query("/model opus")
            
            var responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("🔄 Model switch response: ${content.text}")
                        }
                        println("📝 Model in response: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("✅ Model switch result: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        println("📨 Other message: ${message::class.simpleName}")
                    }
                }
            }
            
            // 3. Ask for model ID after switching to Opus
            println("\n=== 3. Asking for model ID (should be Opus) ===")
            client.query("What is your model ID? Please be specific about your exact model name and version.")
            
            responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("🤖 Opus model ID response: ${content.text}")
                        }
                        println("📋 Current model field: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("✅ Model ID query result: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        println("📨 Other message: ${message::class.simpleName}")
                    }
                }
            }
            
            // 4. Switch to Sonnet (note: /modle should be /model, testing typo)
            println("\n=== 4. Switching back to Sonnet (testing typo) ===")
            client.query("/modle sonnet")  // Intentional typo as requested
            
            responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("❌ Typo command response: ${content.text}")
                        }
                        println("📝 Model in response: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("⚠️ Typo command result: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        println("📨 Other message: ${message::class.simpleName}")
                    }
                }
            }
            
            // 5. Correct the command - switch to Sonnet properly
            println("\n=== 5. Switching to Sonnet (correct command) ===")
            client.query("/model sonnet")  // Correct command
            
            responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("🔄 Sonnet switch response: ${content.text}")
                        }
                        println("📝 Model in response: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("✅ Sonnet switch result: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        println("📨 Other message: ${message::class.simpleName}")
                    }
                }
            }
            
            // 6. Ask for model ID after switching back to Sonnet
            println("\n=== 6. Asking for final model ID (should be Sonnet) ===")
            client.query("What is your model ID now? Please confirm which model you are currently using.")
            
            responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("🤖 Final model ID response: ${content.text}")
                        }
                        println("📋 Final model field: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("✅ Final model ID query result: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        println("📨 Other message: ${message::class.simpleName}")
                    }
                }
            }
            
            println("\n=== 🎉 Test completed successfully ===")
            
        } catch (e: Exception) {
            println("❌ Complete model switch test error: ${e.message}")
            e.printStackTrace()
        } finally {
            client.disconnect()
            println("🔌 Disconnected")
        }
    }
    
    @Test
    fun `test model switching commands summary`() = runTest {
        println("=== Model Switching Command Summary ===")
        println("✅ /model opus    - Switch to Claude 3 Opus")
        println("✅ /model sonnet  - Switch to Claude 3.5 Sonnet") 
        println("✅ /model haiku   - Switch to Claude 3 Haiku")
        println("❌ /modle sonnet  - Invalid command (typo)")
        println("❌ /mode opus     - Invalid command (wrong name)")
        println("")
        println("Valid model names:")
        println("- opus")
        println("- sonnet") 
        println("- haiku")
        println("")
        println("Usage pattern:")
        println("1. client.connect()")
        println("2. client.query('/model <model_name>')")
        println("3. client.query('What model are you?')")
        println("4. Process responses to verify switch")
    }
}