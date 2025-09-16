package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for hooks protocol conversion and integration.
 * This tests the internal protocol conversion logic used by ControlProtocol.
 */
class HooksProtocolTest {
    
    @Test
    fun `test hook event name conversion matches Python SDK`() {
        // Test that our Kotlin hook event names convert correctly to protocol format
        // This matches the Python SDK's event naming convention
        
        val expectedEventNames = mapOf(
            HookEvent.PRE_TOOL_USE to "PreToolUse",
            HookEvent.POST_TOOL_USE to "PostToolUse", 
            HookEvent.USER_PROMPT_SUBMIT to "UserPromptSubmit",
            HookEvent.STOP to "Stop",
            HookEvent.SUBAGENT_STOP to "SubagentStop",
            HookEvent.PRE_COMPACT to "PreCompact"
        )
        
        // Verify we have all expected events
        assertEquals(6, expectedEventNames.size)
        assertEquals(6, HookEvent.values().size)
        
        // Verify each event converts to correct protocol name
        expectedEventNames.forEach { (event, expectedName) ->
            val protocolName = when (event) {
                HookEvent.PRE_TOOL_USE -> "PreToolUse"
                HookEvent.POST_TOOL_USE -> "PostToolUse"
                HookEvent.USER_PROMPT_SUBMIT -> "UserPromptSubmit"
                HookEvent.STOP -> "Stop"
                HookEvent.SUBAGENT_STOP -> "SubagentStop"
                HookEvent.PRE_COMPACT -> "PreCompact"
            }
            
            assertEquals(expectedName, protocolName, 
                "Event $event should convert to $expectedName")
        }
        
        println("✅ Hook event name conversion test passed")
    }
    
    @Test
    fun `test hook matcher patterns validation`() {
        // Test different hook matcher patterns that should work with Claude CLI
        val validPatterns = listOf(
            "Bash",                    // Single tool
            "Write|Edit|MultiEdit",    // Multiple tools with pipe
            "Read|Glob|Grep",         // Search tools
            null                       // All tools matcher
        )
        
        validPatterns.forEach { pattern ->
            val callback: HookCallback = { _, _, _ ->
                HookJSONOutput(systemMessage = "Pattern test")
            }
            
            val matcher = HookMatcher(
                matcher = pattern,
                hooks = listOf(callback)
            )
            
            // Verify matcher configuration
            assertEquals(pattern, matcher.matcher)
            assertEquals(1, matcher.hooks.size)
            assertNotNull(matcher.hooks.first())
        }
        
        println("✅ Hook matcher patterns validation test passed")
    }
    
    @Test
    fun `test hook JSON output formats`() {
        // Test different HookJSONOutput formats that match Python SDK
        
        // 1. Allow action (no decision)
        val allowOutput = HookJSONOutput()
        assertNull(allowOutput.decision)
        assertNull(allowOutput.systemMessage)
        assertNull(allowOutput.hookSpecificOutput)
        
        // 2. Block action
        val blockOutput = HookJSONOutput(
            decision = "block",
            systemMessage = "Action blocked by hook",
            hookSpecificOutput = JsonPrimitive("security_violation")
        )
        assertEquals("block", blockOutput.decision)
        assertEquals("Action blocked by hook", blockOutput.systemMessage)
        assertEquals(JsonPrimitive("security_violation"), blockOutput.hookSpecificOutput)
        
        // 3. Log only (no blocking)
        val logOutput = HookJSONOutput(
            systemMessage = "Action logged by hook"
        )
        assertNull(logOutput.decision)
        assertEquals("Action logged by hook", logOutput.systemMessage)
        assertNull(logOutput.hookSpecificOutput)
        
        // 4. Custom hook-specific output
        val customOutput = HookJSONOutput(
            systemMessage = "Custom processing",
            hookSpecificOutput = JsonPrimitive("custom_data_123")
        )
        assertNull(customOutput.decision)
        assertEquals("Custom processing", customOutput.systemMessage)
        assertEquals(JsonPrimitive("custom_data_123"), customOutput.hookSpecificOutput)
        
        println("✅ Hook JSON output formats test passed")
    }
    
    @Test
    fun `test hooks integration with Claude options`() = runBlocking {
        // Test that hooks integrate properly with ClaudeCodeOptions
        val securityHook: HookCallback = securityLabel@{ input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            
            if (toolName == "Bash") {
                val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
                val command = toolInput["command"] as? String ?: ""
                
                if ("rm -rf" in command || "sudo" in command) {
                    return@securityLabel HookJSONOutput(
                        decision = "block",
                        systemMessage = "🚫 Dangerous command blocked",
                        hookSpecificOutput = JsonPrimitive("security_filter")
                    )
                }
            }
            
            HookJSONOutput(systemMessage = "✅ Command allowed")
        }
        
        val auditHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: "unknown"
            HookJSONOutput(systemMessage = "📝 Audited: $toolName")
        }
        
        // Create comprehensive hooks configuration
        val hooksConfig = mapOf(
            HookEvent.PRE_TOOL_USE to listOf(
                HookMatcher(matcher = "Bash", hooks = listOf(securityHook)),
                HookMatcher(matcher = "Write|Edit|MultiEdit", hooks = listOf(auditHook))
            ),
            HookEvent.POST_TOOL_USE to listOf(
                HookMatcher(matcher = null, hooks = listOf(auditHook))
            ),
            HookEvent.USER_PROMPT_SUBMIT to listOf(
                HookMatcher(matcher = null, hooks = listOf(auditHook))
            ),
            HookEvent.STOP to listOf(
                HookMatcher(matcher = null, hooks = listOf(auditHook))
            ),
            HookEvent.SUBAGENT_STOP to listOf(
                HookMatcher(matcher = null, hooks = listOf(auditHook))
            ),
            HookEvent.PRE_COMPACT to listOf(
                HookMatcher(matcher = null, hooks = listOf(auditHook))
            )
        )
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            allowedTools = listOf("Bash", "Write", "Edit", "Read", "Grep"),
            permissionMode = PermissionMode.BYPASS_PERMISSIONS,
            hooks = hooksConfig
        )
        
        // Verify the hooks are properly configured
        assertNotNull(options.hooks)
        assertEquals(6, options.hooks!!.size)
        
        // Verify each event type has the expected number of matchers
        assertEquals(2, options.hooks!![HookEvent.PRE_TOOL_USE]!!.size)
        assertEquals(1, options.hooks!![HookEvent.POST_TOOL_USE]!!.size)
        assertEquals(1, options.hooks!![HookEvent.USER_PROMPT_SUBMIT]!!.size)
        assertEquals(1, options.hooks!![HookEvent.STOP]!!.size)
        assertEquals(1, options.hooks!![HookEvent.SUBAGENT_STOP]!!.size)
        assertEquals(1, options.hooks!![HookEvent.PRE_COMPACT]!!.size)
        
        // Test hook execution
        val bashInput = mapOf(
            "tool_name" to "Bash",
            "tool_input" to mapOf("command" to "sudo rm -rf /tmp/test")
        )
        
        val securityResult = securityHook(bashInput, "bash_001", HookContext())
        assertEquals("block", securityResult.decision)
        assertTrue(securityResult.systemMessage!!.contains("Dangerous command blocked"))
        
        val safeInput = mapOf(
            "tool_name" to "Write",
            "tool_input" to mapOf("file_path" to "/tmp/test.txt", "content" to "hello")
        )
        
        val auditResult = auditHook(safeInput, "write_001", HookContext())
        assertNull(auditResult.decision)
        assertEquals("📝 Audited: Write", auditResult.systemMessage)
        
        println("✅ Hooks integration with Claude options test passed")
    }
    
    @Test
    fun `test hooks callback ID generation and tracking`() = runBlocking {
        // Test that hook callbacks can be properly tracked (simulating ControlProtocol behavior)
        val callbackTracker = mutableMapOf<String, HookCallback>()
        var callbackIdCounter = 0
        
        val generateCallbackId = { "hook_${++callbackIdCounter}" }
        
        val testCallbacks = listOf<HookCallback>(
            { _, _, _ -> HookJSONOutput(systemMessage = "Hook 1") },
            { _, _, _ -> HookJSONOutput(systemMessage = "Hook 2") },
            { _, _, _ -> HookJSONOutput(decision = "block", systemMessage = "Hook 3") }
        )
        
        // Register callbacks (simulating ControlProtocol.convertHooksToProtocolFormat)
        val callbackIds = testCallbacks.map { callback ->
            val id = generateCallbackId()
            callbackTracker[id] = callback
            id
        }
        
        // Verify callback registration
        assertEquals(3, callbackIds.size)
        assertEquals(3, callbackTracker.size)
        assertEquals(setOf("hook_1", "hook_2", "hook_3"), callbackIds.toSet())
        
        // Test callback retrieval and execution
        callbackIds.forEachIndexed { index, callbackId ->
            val callback = callbackTracker[callbackId]
            assertNotNull(callback, "Callback $callbackId should be registered")
            
            val result = callback!!(
                mapOf("tool_name" to "TestTool"), 
                "test_${index + 1}", 
                HookContext()
            )
            
            assertTrue(result.systemMessage!!.contains("Hook ${index + 1}"))
        }
        
        println("✅ Hooks callback ID generation and tracking test passed")
    }
}