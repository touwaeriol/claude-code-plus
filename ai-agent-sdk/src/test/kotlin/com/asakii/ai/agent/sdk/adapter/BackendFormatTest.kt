package com.asakii.ai.agent.sdk.adapter

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.connect.ClaudeOverrides
import com.asakii.ai.agent.sdk.connect.CodexOverrides
import com.asakii.ai.agent.sdk.connect.normalize
import com.asakii.ai.agent.sdk.model.AssistantMessageEvent
import com.asakii.ai.agent.sdk.model.ContentCompletedEvent
import com.asakii.ai.agent.sdk.model.ContentDeltaEvent
import com.asakii.ai.agent.sdk.model.ContentStartedEvent
import com.asakii.ai.agent.sdk.model.MessageStartedEvent
import com.asakii.ai.agent.sdk.model.ToolDeltaPayload
import com.asakii.ai.agent.sdk.model.ToolUseContent
import com.asakii.ai.agent.sdk.model.UnifiedContentBlock
import com.asakii.claude.agent.sdk.types.AssistantMessage
import com.asakii.claude.agent.sdk.types.PermissionMode
import com.asakii.claude.agent.sdk.types.StreamEvent
import com.asakii.claude.agent.sdk.types.TextBlock
import com.asakii.claude.agent.sdk.types.ThinkingBlock
import com.asakii.claude.agent.sdk.types.ToolUseBlock
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import com.asakii.codex.agent.sdk.ApprovalMode
import com.asakii.codex.agent.sdk.ThreadOptions
import com.asakii.codex.agent.sdk.appserver.AppServerEvent
import com.asakii.codex.agent.sdk.appserver.McpToolCallStatus
import com.asakii.codex.agent.sdk.appserver.ThreadItem
import com.asakii.codex.agent.sdk.appserver.TurnInfo
import com.asakii.codex.agent.sdk.appserver.TurnStatus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackendFormatTest {
    @Test
    fun `claude assistant message should preserve block order and tool fields`() {
        val adapter = ClaudeStreamAdapter()
        val toolInput = buildJsonObject {
            put("path", "README.md")
        }
        val message = AssistantMessage(
            id = "msg_001",
            content = listOf(
                ThinkingBlock(thinking = "think", signature = "sig"),
                ToolUseBlock(id = "toolu_1", name = "Read", input = toolInput),
                TextBlock(text = "done")
            ),
            model = "claude-test"
        )

        val events = adapter.convert(message)
        val assistantEvent = events.filterIsInstance<AssistantMessageEvent>().single()
        assertEquals(AiAgentProvider.CLAUDE, assistantEvent.provider)

        val types = assistantEvent.content.map { it.type }
        assertEquals(listOf("thinking", "tool_use", "text"), types)

        val toolBlock = assistantEvent.content[1] as ToolUseContent
        assertEquals("toolu_1", toolBlock.id)
        assertEquals("Read", toolBlock.name)
        assertEquals(toolInput, toolBlock.input)
    }

    @Test
    fun `claude stream tool event should map to normalized events`() {
        val adapter = ClaudeStreamAdapter()
        val sessionId = "session_test"

        val messageStart = buildJsonObject {
            put("type", "message_start")
            put("message", buildJsonObject {
                put("id", "msg_100")
                put("type", "message")
                put("role", "assistant")
                put("model", "claude-test")
                put("content", buildJsonArray { })
            })
        }
        val toolStart = buildJsonObject {
            put("type", "content_block_start")
            put("index", 0)
            put("content_block", buildJsonObject {
                put("type", "tool_use")
                put("id", "toolu_100")
                put("name", "Read")
                put("input", buildJsonObject {
                    put("path", "README.md")
                })
            })
        }
        val toolDelta = buildJsonObject {
            put("type", "content_block_delta")
            put("index", 0)
            put("delta", buildJsonObject {
                put("type", "input_json_delta")
                put("partial_json", """{"path":"README.md"}""")
            })
        }
        val toolStop = buildJsonObject {
            put("type", "content_block_stop")
            put("index", 0)
        }

        val startEvents = adapter.convert(StreamEvent(uuid = "u1", sessionId = sessionId, event = messageStart))
        val toolStartEvents = adapter.convert(StreamEvent(uuid = "u2", sessionId = sessionId, event = toolStart))
        val toolDeltaEvents = adapter.convert(StreamEvent(uuid = "u3", sessionId = sessionId, event = toolDelta))
        val toolStopEvents = adapter.convert(StreamEvent(uuid = "u4", sessionId = sessionId, event = toolStop))

        val messageStarted = startEvents.filterIsInstance<MessageStartedEvent>().single()
        assertEquals("msg_100", messageStarted.messageId)

        val contentStarted = toolStartEvents.filterIsInstance<ContentStartedEvent>().single()
        val toolUse = contentStarted.content as ToolUseContent
        assertEquals("toolu_100", toolUse.id)
        assertEquals("Read", toolUse.name)

        val contentDelta = toolDeltaEvents.filterIsInstance<ContentDeltaEvent>().single()
        val delta = contentDelta.delta as ToolDeltaPayload
        assertEquals("""{"path":"README.md"}""", delta.partialJson)

        val contentCompleted = toolStopEvents.filterIsInstance<ContentCompletedEvent>().single()
        val completedTool = contentCompleted.content as ToolUseContent
        assertEquals("toolu_100", completedTool.id)
        val inputPath = completedTool.input?.jsonObject?.get("path")?.jsonPrimitive?.content
        assertEquals("README.md", inputPath)
    }

    @Test
    fun `codex assistant message should follow stream index order`() {
        val adapter = CodexAppServerStreamAdapter(sessionIdProvider = { "session-1" })
        val threadId = "thread-1"
        val turnId = "turn-1"

        adapter.convert(
            AppServerEvent.TurnStarted(
                threadId = threadId,
                turn = TurnInfo(id = turnId, status = TurnStatus.InProgress)
            )
        )

        val toolCall = ThreadItem.McpToolCall(
            id = "tool-1",
            server = "ide-file",
            tool = "ReadFile",
            status = McpToolCallStatus.InProgress,
            arguments = buildJsonObject {
                put("filePath", "README.md")
            }
        )
        adapter.convert(AppServerEvent.ItemStarted(threadId, turnId, toolCall))
        adapter.convert(AppServerEvent.AgentMessageDelta(threadId, turnId, "msg-1", "text after tool"))
        adapter.convert(AppServerEvent.ReasoningDelta(threadId, turnId, "reason-1", "thinking later"))

        val completed = adapter.convert(
            AppServerEvent.TurnCompleted(
                threadId = threadId,
                turn = TurnInfo(id = turnId, status = TurnStatus.Completed, items = listOf(toolCall))
            )
        )

        val assistant = completed.filterIsInstance<AssistantMessageEvent>().single()
        val types = assistant.content.map(UnifiedContentBlock::type)
        assertEquals(listOf("tool_use", "text", "thinking"), types)

        val tool = assistant.content.first() as ToolUseContent
        assertEquals("mcp__ide-file__ReadFile", tool.name)
        assertEquals("tool-1", tool.id)
    }

    @Test
    fun `connect options should keep bypass settings`() {
        val claudeContext = AiAgentConnectOptions(
            provider = AiAgentProvider.CLAUDE,
            claude = ClaudeOverrides(
                options = ClaudeAgentOptions(permissionMode = PermissionMode.BYPASS_PERMISSIONS)
            )
        ).normalize()
        assertEquals(PermissionMode.BYPASS_PERMISSIONS, claudeContext.claudeOptions?.permissionMode)

        val codexContext = AiAgentConnectOptions(
            provider = AiAgentProvider.CODEX,
            codex = CodexOverrides(
                threadOptions = ThreadOptions(approvalPolicy = ApprovalMode.NEVER)
            )
        ).normalize()
        assertEquals(ApprovalMode.NEVER, codexContext.codexThreadOptions?.approvalPolicy)
    }
}
