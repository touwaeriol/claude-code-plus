package com.asakii.server.history

import com.asakii.ai.agent.sdk.model.ContentStatus
import com.asakii.ai.agent.sdk.model.TextContent
import com.asakii.ai.agent.sdk.model.ThinkingContent
import com.asakii.ai.agent.sdk.model.ToolResultContent
import com.asakii.ai.agent.sdk.model.ToolUseContent
import com.asakii.ai.agent.sdk.model.UiAssistantMessage
import com.asakii.ai.agent.sdk.model.UiStreamEvent
import com.asakii.ai.agent.sdk.model.UiUserMessage
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class HistoryJsonlLoaderTest {
    private lateinit var tempHome: Path
    private var originalHome: String? = null

    @BeforeTest
    fun setUp() {
        tempHome = Files.createTempDirectory("claude-home-")
        originalHome = System.getProperty("user.home")
        System.setProperty("user.home", tempHome.toString())
    }

    @AfterTest
    fun tearDown() {
        originalHome?.let { System.setProperty("user.home", it) }
        tempHome.toFile().deleteRecursively()
    }

    @Test
    fun `loadHistory should parse user and assistant messages`() {
        val projectPath = "C:/Users/test/demo"
        val projectId = "C--Users-test-demo"
        val sessionId = "session-123"

        val historyDir = tempHome
            .resolve(".claude")
            .resolve("projects")
            .resolve(projectId)
        historyDir.createDirectories()

        val historyFile = historyDir.resolve("$sessionId.jsonl")
        val jsonl = buildString {
            appendLine("""{"type":"user","message":{"role":"user","content":[{"type":"text","text":"hello"}]}}""")
            appendLine("""{"type":"assistant","message":{"role":"assistant","model":"claude-opus","content":[{"type":"text","text":"hi"}]}}""")
        }
        historyFile.writeText(jsonl)

        val messages: List<UiStreamEvent> = HistoryJsonlLoader
            .loadHistoryMessages(sessionId, projectPath, offset = 0, limit = 10)

        assertEquals(2, messages.size, "Should load two messages")

        val user = assertIs<UiUserMessage>(messages[0])
        val assistant = assertIs<UiAssistantMessage>(messages[1])

        assertEquals("hello", (user.content[0] as TextContent).text)
        assertEquals("hi", (assistant.content[0] as TextContent).text)
    }

    @Test
    fun `loadHistory should respect offset and limit`() {
        val projectPath = "C:/Users/test/demo"
        val projectId = "C--Users-test-demo"
        val sessionId = "session-456"

        val historyDir = tempHome
            .resolve(".claude")
            .resolve("projects")
            .resolve(projectId)
        historyDir.createDirectories()

        val historyFile = historyDir.resolve("$sessionId.jsonl")
        val lines = (1..5).joinToString("\n") { idx ->
            """{"type":"user","message":{"role":"user","content":[{"type":"text","text":"msg-$idx"}]}}"""
        }
        historyFile.writeText(lines)

        val messages: List<UiStreamEvent> = HistoryJsonlLoader
            .loadHistoryMessages(sessionId, projectPath, offset = 2, limit = 2)

        assertEquals(2, messages.size, "Should load only limited messages")
        val texts = messages.map { (it as UiUserMessage).content[0] as TextContent }
        assertTrue(texts[0].text.contains("msg-3"))
        assertTrue(texts[1].text.contains("msg-4"))
    }

    @Test
    fun `loadHistory should parse tool blocks and thinking`() {
        val projectPath = "C:/Users/test/tool-demo"
        val projectId = "C--Users-test-tool-demo"
        val sessionId = "session-tools"

        val historyDir = tempHome
            .resolve(".claude")
            .resolve("projects")
            .resolve(projectId)
        historyDir.createDirectories()

        val historyFile = historyDir.resolve("$sessionId.jsonl")
        val jsonl = buildString {
            appendLine(
                """{"type":"assistant","message":{"role":"assistant","model":"claude-opus","content":[{"type":"thinking","thinking":"plan"},{"type":"tool_use","id":"call-1","name":"Read","input":{"file_path":"foo.txt"}}]}}"""
            )
            appendLine(
                """{"type":"user","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"call-1","content":"ok","is_error":false}]}}"""
            )
            appendLine(
                """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"done"}]}}"""
            )
            // These types are filtered out by shouldDisplay()
            appendLine("""{"type":"summary","summary":"compressed"}""")
            appendLine("""{"type":"compact_boundary","status":"compressed","sessionId":"$sessionId"}""")
            appendLine("""{"type":"file-history-snapshot","messageId":"snap-1","snapshot":{"messageId":"snap-1"},"isSnapshotUpdate":false}""")
        }
        historyFile.writeText(jsonl)

        val messages: List<UiStreamEvent> = HistoryJsonlLoader
            .loadHistoryMessages(sessionId, projectPath, offset = 0, limit = 0)

        // Only user, assistant, and summary types are displayable
        // summary returns null in buildResultMessage, so only 3 messages
        assertEquals(3, messages.size, "Should load displayable messages (user and assistant)")

        val assistantWithTool = assertIs<UiAssistantMessage>(messages[0])
        val thinking = assertIs<ThinkingContent>(assistantWithTool.content[0])
        assertEquals("plan", thinking.thinking)
        val toolUse = assertIs<ToolUseContent>(assistantWithTool.content[1])
        assertEquals("call-1", toolUse.id)
        assertEquals("Read", toolUse.name)
        assertEquals(ContentStatus.IN_PROGRESS, toolUse.status)
        assertEquals("foo.txt", toolUse.input?.jsonObject?.get("file_path")?.jsonPrimitive?.content)

        val toolResultMsg = assertIs<UiUserMessage>(messages[1])
        val toolResult = assertIs<ToolResultContent>(toolResultMsg.content[0])
        assertEquals("call-1", toolResult.toolUseId)
        assertEquals(false, toolResult.isError)

        val reply = assertIs<UiAssistantMessage>(messages[2])
        val replyText = assertIs<TextContent>(reply.content[0])
        assertEquals("done", replyText.text)
    }
}
