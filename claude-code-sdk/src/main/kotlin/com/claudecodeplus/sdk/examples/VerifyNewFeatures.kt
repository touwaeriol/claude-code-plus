package com.claudecodeplus.sdk.examples

import com.claudecodeplus.sdk.types.*
import java.nio.file.Path

/**
 * 验证 v0.1.0 新功能是否正常工作
 * 这是一个独立的验证程序，不依赖测试框架
 */
fun main() {
    println("=== Claude Agent SDK v0.1.0 新功能验证 ===\n")

    var testsPassed = 0
    var testsFailed = 0

    // Test 1: SystemPromptPreset
    println("Test 1: SystemPromptPreset")
    try {
        val preset = SystemPromptPreset(
            preset = "claude_code",
            append = "Be concise."
        )
        assert(preset.type == "preset")
        assert(preset.preset == "claude_code")
        assert(preset.append == "Be concise.")
        println("  ✅ SystemPromptPreset 创建成功")
        testsPassed++
    } catch (e: Exception) {
        println("  ❌ 失败: ${e.message}")
        testsFailed++
    }

    // Test 2: AgentDefinition
    println("\nTest 2: AgentDefinition")
    try {
        val agent = AgentDefinition(
            description = "Code reviewer",
            prompt = "Review code for quality",
            tools = listOf("Read", "Grep"),
            model = "sonnet"
        )
        assert(agent.description == "Code reviewer")
        assert(agent.prompt == "Review code for quality")
        assert(agent.tools == listOf("Read", "Grep"))
        assert(agent.model == "sonnet")
        println("  ✅ AgentDefinition 创建成功")
        testsPassed++
    } catch (e: Exception) {
        println("  ❌ 失败: ${e.message}")
        testsFailed++
    }

    // Test 3: SettingSource enum
    println("\nTest 3: SettingSource")
    try {
        val sources = SettingSource.values()
        assert(sources.size == 3)
        assert(sources.contains(SettingSource.USER))
        assert(sources.contains(SettingSource.PROJECT))
        assert(sources.contains(SettingSource.LOCAL))
        println("  ✅ SettingSource 枚举正确: ${sources.toList()}")
        testsPassed++
    } catch (e: Exception) {
        println("  ❌ 失败: ${e.message}")
        testsFailed++
    }

    // Test 4: ClaudeAgentOptions with String systemPrompt
    println("\nTest 4: ClaudeAgentOptions with String systemPrompt")
    try {
        val options = ClaudeAgentOptions(
            systemPrompt = "You are a helpful assistant"
        )
        assert(options.systemPrompt is String)
        assert(options.systemPrompt == "You are a helpful assistant")
        println("  ✅ String systemPrompt 正常工作")
        testsPassed++
    } catch (e: Exception) {
        println("  ❌ 失败: ${e.message}")
        testsFailed++
    }

    // Test 5: ClaudeAgentOptions with SystemPromptPreset
    println("\nTest 5: ClaudeAgentOptions with SystemPromptPreset")
    try {
        val preset = SystemPromptPreset(
            preset = "claude_code",
            append = "Always explain."
        )
        val options = ClaudeAgentOptions(
            systemPrompt = preset
        )
        assert(options.systemPrompt is SystemPromptPreset)
        val actualPreset = options.systemPrompt as SystemPromptPreset
        assert(actualPreset.preset == "claude_code")
        assert(actualPreset.append == "Always explain.")
        println("  ✅ SystemPromptPreset systemPrompt 正常工作")
        testsPassed++
    } catch (e: Exception) {
        println("  ❌ 失败: ${e.message}")
        testsFailed++
    }

    // Test 6: ClaudeAgentOptions with agents
    println("\nTest 6: ClaudeAgentOptions with agents")
    try {
        val agents = mapOf(
            "reviewer" to AgentDefinition(
                description = "Code reviewer",
                prompt = "Review code"
            ),
            "tester" to AgentDefinition(
                description = "Test writer",
                prompt = "Write tests"
            )
        )
        val options = ClaudeAgentOptions(agents = agents)
        assert(options.agents?.size == 2)
        assert(options.agents?.get("reviewer")?.description == "Code reviewer")
        assert(options.agents?.get("tester")?.description == "Test writer")
        println("  ✅ agents 字段正常工作 (${options.agents?.size} 个代理)")
        testsPassed++
    } catch (e: Exception) {
        println("  ❌ 失败: ${e.message}")
        testsFailed++
    }

    // Test 7: ClaudeAgentOptions with settingSources
    println("\nTest 7: ClaudeAgentOptions with settingSources")
    try {
        val options = ClaudeAgentOptions(
            settingSources = listOf(
                SettingSource.PROJECT,
                SettingSource.LOCAL
            )
        )
        assert(options.settingSources?.size == 2)
        assert(options.settingSources?.contains(SettingSource.PROJECT) == true)
        assert(options.settingSources?.contains(SettingSource.LOCAL) == true)
        println("  ✅ settingSources 字段正常工作")
        testsPassed++
    } catch (e: Exception) {
        println("  ❌ 失败: ${e.message}")
        testsFailed++
    }

    // Test 8: ClaudeAgentOptions with new boolean flags
    println("\nTest 8: ClaudeAgentOptions with new boolean flags")
    try {
        val options = ClaudeAgentOptions(
            forkSession = true,
            includePartialMessages = true
        )
        assert(options.forkSession == true)
        assert(options.includePartialMessages == true)
        println("  ✅ forkSession 和 includePartialMessages 字段正常工作")
        testsPassed++
    } catch (e: Exception) {
        println("  ❌ 失败: ${e.message}")
        testsFailed++
    }

    // Test 9: ClaudeAgentOptions with stderr callback
    println("\nTest 9: ClaudeAgentOptions with stderr callback")
    try {
        var capturedMessage: String? = null
        val options = ClaudeAgentOptions(
            stderr = { msg ->
                capturedMessage = msg
            }
        )
        options.stderr?.invoke("Test error")
        assert(capturedMessage == "Test error")
        println("  ✅ stderr 回调正常工作")
        testsPassed++
    } catch (e: Exception) {
        println("  ❌ 失败: ${e.message}")
        testsFailed++
    }

    // Test 10: StreamEvent message type
    println("\nTest 10: StreamEvent message type")
    try {
        val event = StreamEvent(
            uuid = "test-uuid",
            sessionId = "test-session",
            event = kotlinx.serialization.json.JsonPrimitive("test"),
            parentToolUseId = "tool-id"
        )
        assert(event.uuid == "test-uuid")
        assert(event.sessionId == "test-session")
        assert(event.parentToolUseId == "tool-id")
        println("  ✅ StreamEvent 消息类型正常工作")
        testsPassed++
    } catch (e: Exception) {
        println("  ❌ 失败: ${e.message}")
        testsFailed++
    }

    // Test 11: Backward compatibility (ClaudeCodeOptions alias)
    println("\nTest 11: Backward compatibility")
    try {
        @Suppress("DEPRECATION")
        val options: ClaudeCodeOptions = ClaudeCodeOptions(
            systemPrompt = "Test",
            allowedTools = listOf("Read")
        )
        val agentOptions: ClaudeAgentOptions = options
        assert(agentOptions.systemPrompt == "Test")
        assert(agentOptions.allowedTools == listOf("Read"))
        println("  ✅ 向后兼容性别名正常工作")
        testsPassed++
    } catch (e: Exception) {
        println("  ❌ 失败: ${e.message}")
        testsFailed++
    }

    // Test 12: Complex configuration
    println("\nTest 12: Complex configuration")
    try {
        val options = ClaudeAgentOptions(
            allowedTools = listOf("Read", "Write", "Bash"),
            systemPrompt = SystemPromptPreset(preset = "claude_code", append = "Be concise"),
            agents = mapOf(
                "reviewer" to AgentDefinition("Reviewer", "Review code", listOf("Read"), "sonnet")
            ),
            settingSources = listOf(SettingSource.PROJECT),
            forkSession = true,
            includePartialMessages = true,
            permissionMode = PermissionMode.ACCEPT_EDITS,
            model = "claude-sonnet-4-20250514",
            cwd = Path.of("/test"),
            maxTurns = 5,
            stderr = { }
        )
        assert(options.allowedTools.size == 3)
        assert(options.systemPrompt is SystemPromptPreset)
        assert(options.agents?.size == 1)
        assert(options.settingSources?.size == 1)
        assert(options.forkSession)
        assert(options.includePartialMessages)
        assert(options.permissionMode == PermissionMode.ACCEPT_EDITS)
        assert(options.model == "claude-sonnet-4-20250514")
        assert(options.maxTurns == 5)
        println("  ✅ 复杂配置正常工作")
        testsPassed++
    } catch (e: Exception) {
        println("  ❌ 失败: ${e.message}")
        testsFailed++
    }

    // Summary
    println("\n" + "=".repeat(50))
    println("测试总结:")
    println("  ✅ 通过: $testsPassed")
    println("  ❌ 失败: $testsFailed")
    println("  📊 总计: ${testsPassed + testsFailed}")

    if (testsFailed == 0) {
        println("\n🎉 所有新功能测试通过！SDK v0.1.0 更新成功！")
    } else {
        println("\n⚠️  有 $testsFailed 个测试失败，需要修复")
        kotlin.system.exitProcess(1)
    }
}