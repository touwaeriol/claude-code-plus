package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.delay

/**
 * Tests for hooks functionality in Claude Code SDK.
 */
class HooksTest {
    
    private lateinit var client: ClaudeCodeSdkClient
    
    @BeforeEach
    fun setUp() {
        // Note: We'll test hooks without actually connecting to Claude CLI
        // to avoid external dependencies in unit tests
    }
    
    @Test
    fun `test hook configuration creation`() {
        // Test creating hook configuration like Python SDK examples
        val blockingCallback: HookCallback = lambda@{ input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            if (toolName == "Bash") {
                val command = toolInput["command"] as? String ?: ""
                val blockPatterns = listOf("rm -rf", "sudo", "foo.sh")
                
                for (pattern in blockPatterns) {
                    if (pattern in command) {
                        return@lambda HookJSONOutput(
                            decision = "block",
                            systemMessage = "Command blocked for safety: contains '$pattern'",
                            hookSpecificOutput = JsonPrimitive("Command blocked by safety filter")
                        )
                    }
                }
            }
            
            // Allow the action
            HookJSONOutput()
        }
        
        val allowCallback: HookCallback = { input, toolUseId, context ->
            HookJSONOutput(
                systemMessage = "Hook executed successfully"
            )
        }
        
        val hooks = mapOf(
            HookEvent.PRE_TOOL_USE to listOf(
                HookMatcher(
                    matcher = "Bash", 
                    hooks = listOf(blockingCallback)
                ),
                HookMatcher(
                    matcher = "Write|Edit|MultiEdit",
                    hooks = listOf(allowCallback)
                )
            ),
            HookEvent.POST_TOOL_USE to listOf(
                HookMatcher(
                    matcher = null, // Match all tools
                    hooks = listOf(allowCallback)
                )
            )
        )
        
        val options = ClaudeCodeOptions(
            hooks = hooks,
            allowedTools = listOf("Bash", "Write", "Edit")
        )
        
        // Verify hooks configuration
        assertNotNull(options.hooks)
        assertEquals(2, options.hooks!!.size)
        assertTrue(options.hooks!!.containsKey(HookEvent.PRE_TOOL_USE))
        assertTrue(options.hooks!!.containsKey(HookEvent.POST_TOOL_USE))
        
        val preToolUseMatchers = options.hooks!![HookEvent.PRE_TOOL_USE]!!
        assertEquals(2, preToolUseMatchers.size)
        assertEquals("Bash", preToolUseMatchers[0].matcher)
        assertEquals("Write|Edit|MultiEdit", preToolUseMatchers[1].matcher)
        assertEquals(1, preToolUseMatchers[0].hooks.size)
        assertEquals(1, preToolUseMatchers[1].hooks.size)
    }
    
    @Test
    fun `test hook callback execution`() = runBlocking {
        // Test hook callback execution logic
        val executedCallbacks = mutableListOf<String>()
        
        val testCallback: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: "unknown"
            executedCallbacks.add("$toolName:$toolUseId")
            
            HookJSONOutput(
                systemMessage = "Hook executed for $toolName"
            )
        }
        
        // Test with different inputs
        val bashInput = mapOf(
            "tool_name" to "Bash",
            "tool_input" to mapOf("command" to "echo hello")
        )
        
        val result1 = testCallback(bashInput, "tool_123", HookContext())
        assertEquals("Hook executed for Bash", result1.systemMessage)
        assertEquals(listOf("Bash:tool_123"), executedCallbacks)
        
        val writeInput = mapOf(
            "tool_name" to "Write",
            "tool_input" to mapOf("file_path" to "/tmp/test.txt", "content" to "test")
        )
        
        val result2 = testCallback(writeInput, "tool_456", HookContext())
        assertEquals("Hook executed for Write", result2.systemMessage)
        assertEquals(listOf("Bash:tool_123", "Write:tool_456"), executedCallbacks)
    }
    
    @Test
    fun `test hook blocking functionality`() = runBlocking {
        // Test that hooks can block dangerous operations
        val dangerousCommandCallback: HookCallback = dangerous@{ input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            
            if (toolName == "Bash") {
                val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
                val command = toolInput["command"] as? String ?: ""
                
                val dangerousPatterns = listOf("rm -rf", "sudo rm", "dd if=", "format", "mkfs")
                
                for (pattern in dangerousPatterns) {
                    if (pattern in command.lowercase()) {
                        return@dangerous HookJSONOutput(
                            decision = "block",
                            systemMessage = "BLOCKED: Dangerous command detected: $pattern",
                            hookSpecificOutput = JsonPrimitive("security_block")
                        )
                    }
                }
            }
            
            HookJSONOutput() // Allow
        }
        
        // Test safe command
        val safeInput = mapOf(
            "tool_name" to "Bash",
            "tool_input" to mapOf("command" to "echo 'Hello World'")
        )
        
        val safeResult = dangerousCommandCallback(safeInput, "tool_1", HookContext())
        assertNull(safeResult.decision)
        assertNull(safeResult.systemMessage)
        
        // Test dangerous command
        val dangerousInput = mapOf(
            "tool_name" to "Bash", 
            "tool_input" to mapOf("command" to "sudo rm -rf /")
        )
        
        val dangerousResult = dangerousCommandCallback(dangerousInput, "tool_2", HookContext())
        assertEquals("block", dangerousResult.decision)
        assertTrue(dangerousResult.systemMessage!!.contains("BLOCKED"))
        assertTrue(dangerousResult.systemMessage!!.contains("rm -rf"))
        assertEquals(JsonPrimitive("security_block"), dangerousResult.hookSpecificOutput)
    }
    
    @Test
    fun `test hook event types mapping`() {
        // Test that our Kotlin enum matches Python SDK exactly
        val events = HookEvent.values()
        assertEquals(6, events.size)
        
        assertTrue(events.contains(HookEvent.PRE_TOOL_USE))
        assertTrue(events.contains(HookEvent.POST_TOOL_USE))
        assertTrue(events.contains(HookEvent.USER_PROMPT_SUBMIT))
        assertTrue(events.contains(HookEvent.STOP))
        assertTrue(events.contains(HookEvent.SUBAGENT_STOP))
        assertTrue(events.contains(HookEvent.PRE_COMPACT))
    }
    
    @Test
    fun `test hook matcher patterns`() {
        // Test different matcher patterns like Python SDK
        val patterns = listOf(
            "Bash",
            "Write|Edit|MultiEdit", 
            "Read|Glob|Grep",
            null // Match all tools
        )
        
        patterns.forEach { pattern ->
            val matcher = HookMatcher(
                matcher = pattern,
                hooks = listOf { _, _, _ -> HookJSONOutput() }
            )
            
            assertEquals(pattern, matcher.matcher)
            assertEquals(1, matcher.hooks.size)
        }
    }
    
    @Test 
    fun `test async hook execution`() = runBlocking {
        // Test that hooks work with async operations
        val asyncCallback: HookCallback = { input, toolUseId, context ->
            // Simulate async work
            delay(10)
            
            val toolName = input["tool_name"] as? String ?: "unknown"
            HookJSONOutput(
                systemMessage = "Async hook completed for $toolName after delay"
            )
        }
        
        val input = mapOf(
            "tool_name" to "TestTool",
            "tool_input" to mapOf("param" to "value")
        )
        
        val startTime = System.currentTimeMillis()
        val result = asyncCallback(input, "async_tool", HookContext())
        val endTime = System.currentTimeMillis()
        
        assertTrue(endTime - startTime >= 10) // At least 10ms delay
        assertEquals("Async hook completed for TestTool after delay", result.systemMessage)
    }

    @Test
    fun `test all hook event types configuration`() {
        // Test comprehensive configuration with all 6 hook event types
        val logCallback: HookCallback = { input, toolUseId, context ->
            HookJSONOutput(systemMessage = "Hook executed")
        }
        
        val blockCallback: HookCallback = { input, toolUseId, context ->
            HookJSONOutput(decision = "block", systemMessage = "Action blocked")
        }
        
        // Test all 6 hook event types from Python SDK
        val fullHooksConfig = mapOf(
            HookEvent.PRE_TOOL_USE to listOf(
                HookMatcher(matcher = "Bash", hooks = listOf(blockCallback)),
                HookMatcher(matcher = "Write|Edit", hooks = listOf(logCallback))
            ),
            HookEvent.POST_TOOL_USE to listOf(
                HookMatcher(matcher = null, hooks = listOf(logCallback)) // All tools
            ),
            HookEvent.USER_PROMPT_SUBMIT to listOf(
                HookMatcher(matcher = null, hooks = listOf(logCallback))
            ),
            HookEvent.STOP to listOf(
                HookMatcher(matcher = null, hooks = listOf(logCallback))
            ),
            HookEvent.SUBAGENT_STOP to listOf(
                HookMatcher(matcher = null, hooks = listOf(logCallback))
            ),
            HookEvent.PRE_COMPACT to listOf(
                HookMatcher(matcher = null, hooks = listOf(logCallback))
            )
        )
        
        val options = ClaudeCodeOptions(hooks = fullHooksConfig)
        
        // Verify all 6 hook event types are configured
        assertNotNull(options.hooks)
        assertEquals(6, options.hooks!!.size)
        
        // Verify each hook event type exists
        assertTrue(options.hooks!!.containsKey(HookEvent.PRE_TOOL_USE))
        assertTrue(options.hooks!!.containsKey(HookEvent.POST_TOOL_USE))
        assertTrue(options.hooks!!.containsKey(HookEvent.USER_PROMPT_SUBMIT))
        assertTrue(options.hooks!!.containsKey(HookEvent.STOP))
        assertTrue(options.hooks!!.containsKey(HookEvent.SUBAGENT_STOP))
        assertTrue(options.hooks!!.containsKey(HookEvent.PRE_COMPACT))
        
        // Verify hook matchers are configured correctly
        assertEquals(2, options.hooks!![HookEvent.PRE_TOOL_USE]!!.size)
        assertEquals(1, options.hooks!![HookEvent.POST_TOOL_USE]!!.size)
        assertEquals(1, options.hooks!![HookEvent.USER_PROMPT_SUBMIT]!!.size)
        assertEquals(1, options.hooks!![HookEvent.STOP]!!.size)
        assertEquals(1, options.hooks!![HookEvent.SUBAGENT_STOP]!!.size)
        assertEquals(1, options.hooks!![HookEvent.PRE_COMPACT]!!.size)
        
        println("✅ All 6 hook event types configuration test passed")
    }
    
    @Test
    fun `test hook callback execution for different event types`() = runBlocking {
        // Test that hook callbacks work correctly for different event types
        val executionLog = mutableListOf<String>()
        
        val trackingCallback: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: "unknown"
            val eventType = input["event_type"] as? String ?: "unknown_event"
            executionLog.add("$eventType:$toolName:$toolUseId")
            
            HookJSONOutput(
                systemMessage = "Tracked $eventType for $toolName"
            )
        }
        
        // Simulate different hook events
        val testScenarios = listOf(
            Triple("PRE_TOOL_USE", "Bash", "bash_001"),
            Triple("POST_TOOL_USE", "Write", "write_001"), 
            Triple("USER_PROMPT_SUBMIT", "UserPrompt", "prompt_001"),
            Triple("STOP", "StopEvent", "stop_001"),
            Triple("SUBAGENT_STOP", "SubagentEvent", "subagent_001"),
            Triple("PRE_COMPACT", "CompactEvent", "compact_001")
        )
        
        testScenarios.forEach { (eventType, toolName, toolId) ->
            val input = mapOf(
                "event_type" to eventType,
                "tool_name" to toolName,
                "tool_input" to mapOf("param" to "value")
            )
            
            val result = trackingCallback(input, toolId, HookContext())
            assertEquals("Tracked $eventType for $toolName", result.systemMessage)
        }
        
        // Verify all events were tracked
        assertEquals(6, executionLog.size)
        assertTrue(executionLog.contains("PRE_TOOL_USE:Bash:bash_001"))
        assertTrue(executionLog.contains("POST_TOOL_USE:Write:write_001"))
        assertTrue(executionLog.contains("USER_PROMPT_SUBMIT:UserPrompt:prompt_001"))
        assertTrue(executionLog.contains("STOP:StopEvent:stop_001"))
        assertTrue(executionLog.contains("SUBAGENT_STOP:SubagentEvent:subagent_001"))
        assertTrue(executionLog.contains("PRE_COMPACT:CompactEvent:compact_001"))
        
        println("✅ Hook callback execution for all event types test passed")
    }
}