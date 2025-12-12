package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.protocol.MessageParser
import com.asakii.claude.agent.sdk.types.AssistantMessage
import com.asakii.claude.agent.sdk.types.UserMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 测试 AssistantMessage 的 parentToolUseId 字段解析
 *
 * Claude CLI 对于子代理消息会输出 parent_tool_use_id 字段：
 * - 主会话消息: parent_tool_use_id = null
 * - 子代理消息: parent_tool_use_id = "toolu_xxx" (触发子代理的 Task 工具调用 ID)
 */
class AssistantMessageParentToolUseIdTest {

    private val parser = MessageParser()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test parse AssistantMessage with null parentToolUseId (main session)`() {
        // 主会话的 assistant 消息，parent_tool_use_id 为 null
        val jsonString = """
            {
                "type": "assistant",
                "message": {
                    "id": "msg_123",
                    "model": "claude-sonnet-4-20250514",
                    "content": [
                        {"type": "text", "text": "Hello, world!"}
                    ]
                },
                "parent_tool_use_id": null,
                "session_id": "default"
            }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(jsonString)
        val message = parser.parseMessage(jsonElement)

        assertNotNull(message)
        assert(message is AssistantMessage) { "Expected AssistantMessage, got ${message::class.simpleName}" }

        val assistantMessage = message as AssistantMessage
        assertNull(assistantMessage.parentToolUseId, "主会话消息的 parentToolUseId 应为 null")
        assertEquals("claude-sonnet-4-20250514", assistantMessage.model)
        assertEquals(1, assistantMessage.content.size)

        println("✅ 主会话 AssistantMessage 解析成功: parentToolUseId=null")
    }

    @Test
    fun `test parse AssistantMessage with parentToolUseId (subagent message)`() {
        // 子代理的 assistant 消息，parent_tool_use_id 有值
        val expectedParentToolUseId = "toolu_011tsVn7HwVz7wXzmY79Lzt1"
        val jsonString = """
            {
                "type": "assistant",
                "message": {
                    "id": "msg_456",
                    "model": "claude-haiku-3-5-20241022",
                    "content": [
                        {"type": "tool_use", "id": "toolu_read_123", "name": "Read", "input": {"file_path": "/path/to/file"}}
                    ]
                },
                "parent_tool_use_id": "$expectedParentToolUseId",
                "session_id": "agent-abc123"
            }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(jsonString)
        val message = parser.parseMessage(jsonElement)

        assertNotNull(message)
        assert(message is AssistantMessage) { "Expected AssistantMessage, got ${message::class.simpleName}" }

        val assistantMessage = message as AssistantMessage
        assertEquals(expectedParentToolUseId, assistantMessage.parentToolUseId,
            "子代理消息的 parentToolUseId 应为 Task 工具调用 ID")
        assertEquals("claude-haiku-3-5-20241022", assistantMessage.model)
        assertEquals(1, assistantMessage.content.size)

        println("✅ 子代理 AssistantMessage 解析成功: parentToolUseId=$expectedParentToolUseId")
    }

    @Test
    fun `test parse AssistantMessage without parentToolUseId field (backward compatibility)`() {
        // 没有 parent_tool_use_id 字段的旧格式消息（向后兼容）
        val jsonString = """
            {
                "type": "assistant",
                "message": {
                    "id": "msg_789",
                    "model": "claude-sonnet-4-20250514",
                    "content": [
                        {"type": "text", "text": "Legacy message format"}
                    ]
                },
                "session_id": "default"
            }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(jsonString)
        val message = parser.parseMessage(jsonElement)

        assertNotNull(message)
        assert(message is AssistantMessage) { "Expected AssistantMessage, got ${message::class.simpleName}" }

        val assistantMessage = message as AssistantMessage
        assertNull(assistantMessage.parentToolUseId, "旧格式消息的 parentToolUseId 应为 null")

        println("✅ 旧格式 AssistantMessage 解析成功（向后兼容）: parentToolUseId=null")
    }

    @Test
    fun `test UserMessage parentToolUseId still works`() {
        // 确保 UserMessage 的 parentToolUseId 解析仍然正常
        val expectedParentToolUseId = "toolu_user_test_123"
        val jsonString = """
            {
                "type": "user",
                "message": {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": "User prompt to subagent"}
                    ]
                },
                "parent_tool_use_id": "$expectedParentToolUseId",
                "session_id": "agent-def456"
            }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(jsonString)
        val message = parser.parseMessage(jsonElement)

        assertNotNull(message)
        assert(message is UserMessage) { "Expected UserMessage, got ${message::class.simpleName}" }

        val userMessage = message as UserMessage
        assertEquals(expectedParentToolUseId, userMessage.parentToolUseId,
            "UserMessage 的 parentToolUseId 解析应正常工作")

        println("✅ UserMessage parentToolUseId 解析正常: $expectedParentToolUseId")
    }

    @Test
    fun `test subagent routing scenario`() {
        // 模拟完整的子代理消息路由场景
        val taskToolUseId = "toolu_task_main_123"

        // 1. 子代理的 prompt (user message)
        val subagentPrompt = """
            {
                "type": "user",
                "message": {"role": "user", "content": [{"type": "text", "text": "Read CLAUDE.md"}]},
                "parent_tool_use_id": "$taskToolUseId",
                "session_id": "agent-xyz"
            }
        """.trimIndent()

        // 2. 子代理的 assistant 响应（包含 Read 工具调用）
        val subagentAssistant = """
            {
                "type": "assistant",
                "message": {
                    "id": "msg_subagent_001",
                    "model": "claude-haiku-3-5-20241022",
                    "content": [
                        {"type": "tool_use", "id": "toolu_read_001", "name": "Read", "input": {"file_path": "CLAUDE.md"}}
                    ]
                },
                "parent_tool_use_id": "$taskToolUseId",
                "session_id": "agent-xyz"
            }
        """.trimIndent()

        // 3. 子代理的 tool_result (user message)
        val subagentToolResult = """
            {
                "type": "user",
                "message": {"role": "user", "content": [{"type": "tool_result", "tool_use_id": "toolu_read_001", "content": "file content..."}]},
                "parent_tool_use_id": "$taskToolUseId",
                "session_id": "agent-xyz"
            }
        """.trimIndent()

        // 解析所有消息
        val prompt = parser.parseMessage(json.parseToJsonElement(subagentPrompt))
        val assistant = parser.parseMessage(json.parseToJsonElement(subagentAssistant))
        val toolResult = parser.parseMessage(json.parseToJsonElement(subagentToolResult))

        // 验证所有消息都有正确的 parentToolUseId
        assertEquals(taskToolUseId, (prompt as UserMessage).parentToolUseId,
            "子代理 prompt 应有正确的 parentToolUseId")
        assertEquals(taskToolUseId, (assistant as AssistantMessage).parentToolUseId,
            "子代理 assistant 应有正确的 parentToolUseId")
        assertEquals(taskToolUseId, (toolResult as UserMessage).parentToolUseId,
            "子代理 tool_result 应有正确的 parentToolUseId")

        println("✅ 子代理消息路由场景测试通过:")
        println("   - Prompt parentToolUseId: ${(prompt as UserMessage).parentToolUseId}")
        println("   - Assistant parentToolUseId: ${(assistant as AssistantMessage).parentToolUseId}")
        println("   - ToolResult parentToolUseId: ${(toolResult as UserMessage).parentToolUseId}")
    }
}
