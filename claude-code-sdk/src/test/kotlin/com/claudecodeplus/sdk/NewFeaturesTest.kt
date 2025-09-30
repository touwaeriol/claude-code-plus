package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path

/**
 * 测试 v0.1.0 新增功能和参数
 */
class NewFeaturesTest {

    @Test
    fun `test SystemPromptPreset creation`() {
        val preset = SystemPromptPreset(
            preset = "claude_code",
            append = "Be concise."
        )

        assertEquals("preset", preset.type)
        assertEquals("claude_code", preset.preset)
        assertEquals("Be concise.", preset.append)
    }

    @Test
    fun `test SystemPromptPreset default values`() {
        val preset = SystemPromptPreset()

        assertEquals("preset", preset.type)
        assertEquals("claude_code", preset.preset)
        assertNull(preset.append)
    }

    @Test
    fun `test AgentDefinition creation`() {
        val agent = AgentDefinition(
            description = "Code reviewer",
            prompt = "Review code for quality",
            tools = listOf("Read", "Grep"),
            model = "sonnet"
        )

        assertEquals("Code reviewer", agent.description)
        assertEquals("Review code for quality", agent.prompt)
        assertEquals(listOf("Read", "Grep"), agent.tools)
        assertEquals("sonnet", agent.model)
    }

    @Test
    fun `test AgentDefinition with null tools and model`() {
        val agent = AgentDefinition(
            description = "Simple agent",
            prompt = "Do something"
        )

        assertNull(agent.tools)
        assertNull(agent.model)
    }

    @Test
    fun `test SettingSource enum values`() {
        assertEquals(3, SettingSource.values().size)

        val sources = SettingSource.values()
        assertTrue(sources.contains(SettingSource.USER))
        assertTrue(sources.contains(SettingSource.PROJECT))
        assertTrue(sources.contains(SettingSource.LOCAL))
    }

    @Test
    fun `test ClaudeAgentOptions with new fields`() {
        val agents = mapOf(
            "reviewer" to AgentDefinition("Code reviewer", "Review code")
        )

        val options = ClaudeAgentOptions(
            systemPrompt = SystemPromptPreset(preset = "claude_code"),
            agents = agents,
            settingSources = listOf(SettingSource.PROJECT, SettingSource.LOCAL),
            forkSession = true,
            includePartialMessages = true,
            stderr = { msg -> println("STDERR: $msg") }
        )

        // Verify systemPrompt
        assertNotNull(options.systemPrompt)
        assertTrue(options.systemPrompt is SystemPromptPreset)

        // Verify agents
        assertNotNull(options.agents)
        assertEquals(1, options.agents?.size)
        assertEquals("Code reviewer", options.agents?.get("reviewer")?.description)

        // Verify settingSources
        assertNotNull(options.settingSources)
        assertEquals(2, options.settingSources?.size)
        assertTrue(options.settingSources?.contains(SettingSource.PROJECT) == true)

        // Verify new boolean flags
        assertTrue(options.forkSession)
        assertTrue(options.includePartialMessages)

        // Verify stderr callback
        assertNotNull(options.stderr)
    }

    @Test
    fun `test ClaudeAgentOptions with string systemPrompt`() {
        val options = ClaudeAgentOptions(
            systemPrompt = "You are a helpful assistant"
        )

        assertNotNull(options.systemPrompt)
        assertTrue(options.systemPrompt is String)
        assertEquals("You are a helpful assistant", options.systemPrompt)
    }

    @Test
    fun `test ClaudeAgentOptions with SystemPromptPreset`() {
        val preset = SystemPromptPreset(
            preset = "claude_code",
            append = "Always explain your reasoning."
        )

        val options = ClaudeAgentOptions(
            systemPrompt = preset
        )

        assertNotNull(options.systemPrompt)
        assertTrue(options.systemPrompt is SystemPromptPreset)

        val actualPreset = options.systemPrompt as SystemPromptPreset
        assertEquals("claude_code", actualPreset.preset)
        assertEquals("Always explain your reasoning.", actualPreset.append)
    }

    @Test
    fun `test ClaudeAgentOptions default values`() {
        val options = ClaudeAgentOptions()

        assertTrue(options.allowedTools.isEmpty())
        assertTrue(options.disallowedTools.isEmpty())
        assertNull(options.systemPrompt)
        assertNull(options.agents)
        assertNull(options.settingSources)
        assertFalse(options.forkSession)
        assertFalse(options.includePartialMessages)
        assertNull(options.stderr)
    }

    @Test
    fun `test ClaudeAgentOptions with multiple agents`() {
        val agents = mapOf(
            "reviewer" to AgentDefinition(
                description = "Code reviewer",
                prompt = "Review code",
                tools = listOf("Read", "Grep"),
                model = "sonnet"
            ),
            "tester" to AgentDefinition(
                description = "Test writer",
                prompt = "Write tests",
                tools = listOf("Read", "Write", "Bash"),
                model = "haiku"
            ),
            "docs" to AgentDefinition(
                description = "Documentation expert",
                prompt = "Write docs",
                tools = listOf("Read", "Write"),
                model = "inherit"
            )
        )

        val options = ClaudeAgentOptions(agents = agents)

        assertEquals(3, options.agents?.size)
        assertEquals("Code reviewer", options.agents?.get("reviewer")?.description)
        assertEquals("Test writer", options.agents?.get("tester")?.description)
        assertEquals("Documentation expert", options.agents?.get("docs")?.description)
    }

    @Test
    fun `test ClaudeAgentOptions with all setting sources`() {
        val options = ClaudeAgentOptions(
            settingSources = listOf(
                SettingSource.USER,
                SettingSource.PROJECT,
                SettingSource.LOCAL
            )
        )

        assertEquals(3, options.settingSources?.size)
        assertTrue(options.settingSources?.contains(SettingSource.USER) == true)
        assertTrue(options.settingSources?.contains(SettingSource.PROJECT) == true)
        assertTrue(options.settingSources?.contains(SettingSource.LOCAL) == true)
    }

    @Test
    fun `test ClaudeAgentOptions with only project settings`() {
        val options = ClaudeAgentOptions(
            settingSources = listOf(SettingSource.PROJECT)
        )

        assertEquals(1, options.settingSources?.size)
        assertTrue(options.settingSources?.contains(SettingSource.PROJECT) == true)
        assertFalse(options.settingSources?.contains(SettingSource.USER) == true)
        assertFalse(options.settingSources?.contains(SettingSource.LOCAL) == true)
    }

    @Test
    fun `test StreamEvent message type`() {
        val event = StreamEvent(
            uuid = "test-uuid-123",
            sessionId = "session-1",
            event = kotlinx.serialization.json.JsonPrimitive("test-event"),
            parentToolUseId = "tool-123"
        )

        assertEquals("test-uuid-123", event.uuid)
        assertEquals("session-1", event.sessionId)
        assertEquals("tool-123", event.parentToolUseId)
    }

    @Test
    fun `test StreamEvent without parentToolUseId`() {
        val event = StreamEvent(
            uuid = "test-uuid-456",
            sessionId = "session-2",
            event = kotlinx.serialization.json.JsonPrimitive("test-event-2")
        )

        assertEquals("test-uuid-456", event.uuid)
        assertEquals("session-2", event.sessionId)
        assertNull(event.parentToolUseId)
    }

    @Test
    fun `test deprecated ClaudeCodeOptions alias`() {
        // 测试向后兼容性：ClaudeCodeOptions 应该等同于 ClaudeAgentOptions
        @Suppress("DEPRECATION")
        val options: ClaudeCodeOptions = ClaudeCodeOptions(
            systemPrompt = "Test prompt",
            allowedTools = listOf("Read", "Write")
        )

        // 应该能够被当作 ClaudeAgentOptions 使用
        val agentOptions: ClaudeAgentOptions = options
        assertEquals("Test prompt", agentOptions.systemPrompt)
        assertEquals(listOf("Read", "Write"), agentOptions.allowedTools)
    }

    @Test
    fun `test stderr callback functionality`() {
        var capturedMessage: String? = null

        val options = ClaudeAgentOptions(
            stderr = { msg ->
                capturedMessage = msg
            }
        )

        // 模拟调用 stderr 回调
        options.stderr?.invoke("Test error message")

        assertEquals("Test error message", capturedMessage)
    }

    @Test
    fun `test ClaudeAgentOptions with complex configuration`() {
        val options = ClaudeAgentOptions(
            allowedTools = listOf("Read", "Write", "Bash", "Grep"),
            disallowedTools = listOf("Task"),
            systemPrompt = SystemPromptPreset(
                preset = "claude_code",
                append = "Be concise and explain your reasoning."
            ),
            agents = mapOf(
                "reviewer" to AgentDefinition(
                    description = "Expert code reviewer",
                    prompt = "Review code for quality, security, and performance",
                    tools = listOf("Read", "Grep"),
                    model = "sonnet"
                )
            ),
            settingSources = listOf(SettingSource.PROJECT, SettingSource.LOCAL),
            forkSession = true,
            includePartialMessages = true,
            permissionMode = PermissionMode.ACCEPT_EDITS,
            model = "claude-sonnet-4-20250514",
            cwd = Path.of("/test/project"),
            maxTurns = 10,
            stderr = { msg -> println("STDERR: $msg") }
        )

        // Verify all fields
        assertEquals(4, options.allowedTools.size)
        assertEquals(1, options.disallowedTools.size)
        assertTrue(options.systemPrompt is SystemPromptPreset)
        assertEquals(1, options.agents?.size)
        assertEquals(2, options.settingSources?.size)
        assertTrue(options.forkSession)
        assertTrue(options.includePartialMessages)
        assertEquals(PermissionMode.ACCEPT_EDITS, options.permissionMode)
        assertEquals("claude-sonnet-4-20250514", options.model)
        assertEquals(Path.of("/test/project"), options.cwd)
        assertEquals(10, options.maxTurns)
        assertNotNull(options.stderr)
    }

    @Test
    fun `test multiple SystemPromptPreset configurations`() {
        // Test 1: Claude Code preset with append
        val preset1 = SystemPromptPreset(
            preset = "claude_code",
            append = "Additional instructions"
        )
        assertEquals("claude_code", preset1.preset)
        assertEquals("Additional instructions", preset1.append)

        // Test 2: Claude Code preset without append
        val preset2 = SystemPromptPreset(preset = "claude_code")
        assertEquals("claude_code", preset2.preset)
        assertNull(preset2.append)
    }
}