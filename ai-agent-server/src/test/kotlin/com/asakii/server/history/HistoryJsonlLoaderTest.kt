package com.asakii.server.history

import com.asakii.rpc.api.RpcAssistantMessage
import com.asakii.rpc.api.RpcContentStatus
import com.asakii.rpc.api.RpcMessage
import com.asakii.rpc.api.RpcProvider
import com.asakii.rpc.api.RpcResultMessage
import com.asakii.rpc.api.RpcStatusSystemMessage
import com.asakii.rpc.api.RpcTextBlock
import com.asakii.rpc.api.RpcThinkingBlock
import com.asakii.rpc.api.RpcToolResultBlock
import com.asakii.rpc.api.RpcToolUseBlock
import com.asakii.rpc.api.RpcUserMessage
import com.asakii.rpc.api.RpcUnknownBlock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
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
    fun `loadHistory should parse user and assistant messages`() = runBlocking {
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

        val messages: List<RpcMessage> = HistoryJsonlLoader
            .loadHistoryMessages(sessionId, projectPath, offset = 0, limit = 10)
            .toList()

        assertEquals(2, messages.size, "Should load two messages")

        val user = messages[0] as RpcUserMessage
        val assistant = messages[1] as RpcAssistantMessage

        assertEquals(RpcProvider.CLAUDE, user.provider)
        assertEquals("hello", (user.message.content[0] as RpcTextBlock).text)
        assertEquals(0, user.metadata?.replaySeq)
        assertEquals(2, user.metadata?.historyTotal)
        assertEquals(true, user.metadata?.isDisplayable)

        assertEquals(RpcProvider.CLAUDE, assistant.provider)
        assertEquals("hi", (assistant.message.content[0] as RpcTextBlock).text)
        assertEquals("claude-opus", assistant.message.model)
        assertEquals(1, assistant.metadata?.replaySeq)
        assertEquals(2, assistant.metadata?.historyTotal)
        assertEquals(true, assistant.metadata?.isDisplayable)
    }

    @Test
    fun `loadHistory should respect offset and limit`() = runBlocking {
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

        val messages: List<RpcMessage> = HistoryJsonlLoader
            .loadHistoryMessages(sessionId, projectPath, offset = 2, limit = 2)
            .toList()

        assertEquals(2, messages.size, "Should load only limited messages")
        val texts = messages.map { (it as RpcUserMessage).message.content[0] as RpcTextBlock }
        assertTrue(texts[0].text.contains("msg-3"))
        assertTrue(texts[1].text.contains("msg-4"))
        assertEquals(2, (messages[0] as RpcUserMessage).metadata?.replaySeq)
        assertEquals(3, (messages[1] as RpcUserMessage).metadata?.replaySeq)
        assertEquals(5, (messages[0] as RpcUserMessage).metadata?.historyTotal)
    }

    @Test
    fun `loadHistory should parse tool blocks thinking and system messages`() {
        runBlocking {
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
                """{"type":"assistant","message":{"role":"assistant","model":"claude-opus","content":[{"type":"thinking","thinking":"plan"},{"type":"tool_use","id":"call-1","name":"Read","type":"tool_use","input":{"file_path":"foo.txt"}}]}}"""
            )
            appendLine(
                """{"type":"user","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"call-1","content":"ok","is_error":false}]}}"""
            )
            appendLine(
                """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"done"}]}}"""
            )
            appendLine("""{"type":"summary","summary":"compressed"}""")
            appendLine("""{"type":"compact_boundary","status":"compressed","sessionId":"$sessionId"}""")
            appendLine("""{"type":"file-history-snapshot","messageId":"snap-1","snapshot":{"messageId":"snap-1"},"isSnapshotUpdate":false}""")
        }
        historyFile.writeText(jsonl)

        val messages: List<RpcMessage> = HistoryJsonlLoader
            .loadHistoryMessages(sessionId, projectPath, offset = 0, limit = 0)
            .toList()

        assertEquals(6, messages.size, "Should load all messages including non-displayable")

        val assistantWithTool = assertIs<RpcAssistantMessage>(messages[0])
        val thinking = assertIs<RpcThinkingBlock>(assistantWithTool.message.content[0])
        assertEquals("plan", thinking.thinking)
        val toolUse = assertIs<RpcToolUseBlock>(assistantWithTool.message.content[1])
        assertEquals("call-1", toolUse.id)
        assertEquals("Read", toolUse.toolName)
        assertEquals(RpcContentStatus.IN_PROGRESS, toolUse.status)
        assertEquals("foo.txt", toolUse.input?.jsonObject?.get("file_path")?.jsonPrimitive?.content)
        assertEquals(true, assistantWithTool.metadata?.isDisplayable)
        assertEquals(6, assistantWithTool.metadata?.historyTotal)
        assertEquals(0, assistantWithTool.metadata?.replaySeq)

        val toolResultMsg = assertIs<RpcUserMessage>(messages[1])
        val toolResult = assertIs<RpcToolResultBlock>(toolResultMsg.message.content[0])
        assertEquals("call-1", toolResult.toolUseId)
        assertEquals(false, toolResult.isError)
        assertEquals(1, toolResultMsg.metadata?.replaySeq)

        val reply = assertIs<RpcAssistantMessage>(messages[2])
        val replyText = assertIs<RpcTextBlock>(reply.message.content[0])
        assertEquals("done", replyText.text)

        val summary = assertIs<RpcResultMessage>(messages[3])
        assertEquals("summary", summary.subtype)
        assertEquals(true, summary.metadata?.isDisplayable)

        val status = assertIs<RpcStatusSystemMessage>(messages[4])
        assertEquals(false, status.metadata?.isDisplayable)

        val snapshot = assertIs<RpcAssistantMessage>(messages[5])
        assertEquals(false, snapshot.metadata?.isDisplayable)
        assertIs<RpcUnknownBlock>(snapshot.message.content[0])
    }
    }
}
