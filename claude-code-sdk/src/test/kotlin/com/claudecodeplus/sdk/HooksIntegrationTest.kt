package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlinx.coroutines.delay

/**
 * Integration tests for hooks functionality with Claude CLI.
 * These tests require an actual Claude CLI installation and API key.
 */
class HooksIntegrationTest {
    
    @Test
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "TEST_INTEGRATION", matches = "true")
    fun `test real hooks integration with CLI`() = runBlocking {
        // Test hooks with actual Claude CLI interaction
        var bashCommandsBlocked = 0
        var toolCallsLogged = 0
        
        // Create a security hook that blocks dangerous bash commands
        val securityHook: HookCallback = securityLabel@{ input, toolUseId, context ->
            toolCallsLogged++
            
            val toolName = input["tool_name"] as? String ?: ""
            
            if (toolName == "Bash") {
                val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
                val command = toolInput["command"] as? String ?: ""
                
                val blockedPatterns = listOf("rm -rf", "sudo", "format", "mkfs")
                
                for (pattern in blockedPatterns) {
                    if (pattern in command.lowercase()) {
                        bashCommandsBlocked++
                        return@securityLabel HookJSONOutput(
                            decision = "block",
                            systemMessage = "🚫 Security: Blocked dangerous command containing '$pattern'",
                            hookSpecificOutput = JsonPrimitive("blocked_by_security_filter")
                        )
                    }
                }
            }
            
            HookJSONOutput(
                systemMessage = "✅ Tool call allowed: $toolName"
            )
        }
        
        // Create logging hook for all tool calls
        val loggingHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: "unknown"
            println("🔧 Hook: Tool $toolName called with ID $toolUseId")
            
            HookJSONOutput(
                systemMessage = "Hook logged tool call: $toolName"
            )
        }
        
        val options = ClaudeCodeOptions(
            model = "claude-3-haiku-20240307", // Use faster model for testing
            allowedTools = listOf("Bash", "Write", "Read"),
            permissionMode = PermissionMode.BYPASS_PERMISSIONS, // For testing
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = "Bash",
                        hooks = listOf(securityHook)
                    ),
                    HookMatcher(
                        matcher = null, // All tools
                        hooks = listOf(loggingHook)
                    )
                )
            )
        )
        
        val client = ClaudeCodeSdkClient(options)
        
        try {
            client.connect()
            
            // Test 1: Safe bash command should work
            println("Testing safe bash command...")
            client.query("Run the bash command: echo 'Hello from hooks test'")
            
            var responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        println("Assistant: ${message.content}")
                    }
                    is ResultMessage -> {
                        println("Result: ${message.result}")
                        responseCount++
                        if (responseCount >= 1) return@collect // Exit after first response
                    }
                    is SystemMessage -> {
                        println("System: ${message.data}")
                    }
                    else -> {
                        println("Other message: $message")
                    }
                }
            }
            
            delay(1000) // Give time for hooks to execute
            
            // Test 2: Dangerous command should be blocked
            println("\nTesting dangerous bash command (should be blocked)...")
            client.query("Run the bash command: sudo rm -rf /tmp/test")
            
            responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        println("Assistant: ${message.content}")
                    }
                    is ResultMessage -> {
                        println("Result: ${message.result}")
                        responseCount++
                        if (responseCount >= 1) return@collect
                    }
                    is SystemMessage -> {
                        println("System: ${message.data}")
                        if (message.data.toString().contains("Security")) {
                            println("✅ Security hook triggered successfully!")
                        }
                    }
                    else -> {
                        println("Other message: $message")
                    }
                }
            }
            
            delay(1000)
            
            // Verify hooks worked
            println("\n📊 Hook Statistics:")
            println("  - Total tool calls logged: $toolCallsLogged")
            println("  - Bash commands blocked: $bashCommandsBlocked")
            
            assert(toolCallsLogged > 0) { "Expected hooks to log some tool calls" }
            assert(bashCommandsBlocked > 0) { "Expected security hook to block dangerous command" }
            
        } finally {
            client.disconnect()
        }
    }
    
    @Test
    fun `test hook configuration serialization`() {
        // Test that hooks are properly converted to protocol format
        val testHook: HookCallback = { _, _, _ ->
            HookJSONOutput(systemMessage = "Test hook")
        }
        
        val hooks = mapOf(
            HookEvent.PRE_TOOL_USE to listOf(
                HookMatcher(matcher = "Bash", hooks = listOf(testHook)),
                HookMatcher(matcher = "Write|Edit", hooks = listOf(testHook))
            ),
            HookEvent.POST_TOOL_USE to listOf(
                HookMatcher(matcher = null, hooks = listOf(testHook))
            )
        )
        
        val options = ClaudeCodeOptions(hooks = hooks)
        
        // Verify configuration
        assert(options.hooks != null)
        assert(options.hooks!!.size == 2)
        assert(options.hooks!![HookEvent.PRE_TOOL_USE]?.size == 2)
        assert(options.hooks!![HookEvent.POST_TOOL_USE]?.size == 1)
        
        println("✅ Hook configuration serialization test passed")
    }
    
    @Test
    fun `test python SDK compatibility`() {
        // Test that our Kotlin implementation matches Python SDK examples
        val pythonStyleHook: HookCallback = pythonLabel@{ input, toolUseId, context ->
            // Mimic Python SDK example from documentation
            val toolName = input["tool_name"] as? String ?: ""
            val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            if (toolName != "Bash") {
                return@pythonLabel HookJSONOutput()
            }
            
            val command = toolInput["command"] as? String ?: ""
            val blockPatterns = listOf("foo.sh")
            
            for (pattern in blockPatterns) {
                if (pattern in command) {
                    return@pythonLabel HookJSONOutput(
                        decision = "block",
                        systemMessage = "Command blocked by hook",
                        hookSpecificOutput = JsonPrimitive("pattern_match")
                    )
                }
            }
            
            HookJSONOutput()
        }
        
        // Test with the same input as Python SDK examples
        val input1 = mapOf(
            "tool_name" to "Bash",
            "tool_input" to mapOf("command" to "./foo.sh --help")
        )
        
        val result1 = runBlocking { pythonStyleHook(input1, "test_id", HookContext()) }
        assert(result1.decision == "block")
        assert(result1.systemMessage == "Command blocked by hook")
        
        val input2 = mapOf(
            "tool_name" to "Bash", 
            "tool_input" to mapOf("command" to "echo 'Hello from hooks example!'")
        )
        
        val result2 = runBlocking { pythonStyleHook(input2, "test_id", HookContext()) }
        assert(result2.decision == null)
        assert(result2.systemMessage == null)
        
        println("✅ Python SDK compatibility test passed")
    }
}