package com.asakii.codex.agent.sdk

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CodexSessionTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `run accumulates streamed events into TurnResult`() = runTest {
        val usage = Usage(inputTokens = 10, cachedInputTokens = 2, outputTokens = 5)
        val events = listOf(
            ThreadEvent(type = "thread.started", threadId = "thread_1"),
            ThreadEvent(
                type = "item.completed",
                item = ThreadItem(id = "message_1", type = "agent_message", text = "你好，世界"),
            ),
            ThreadEvent(type = "turn.completed", usage = usage),
        )
        val fakeExec = FakeCodexExecRunner(events, json)
        val session = CodexSession(fakeExec, CodexClientOptions(), ThreadOptions())

        val result = session.run("ping")

        assertEquals("thread_1", session.id)
        assertEquals("你好，世界", result.finalResponse)
        assertEquals(1, result.items.size)
        assertEquals(usage, result.usage)
        assertNotNull(fakeExec.capturedArgs)
        assertEquals("ping", fakeExec.capturedArgs?.input)
    }

    @Test
    fun `runStreamed emits events sequentially and forwards normalized inputs`() = runTest {
        val events = listOf(
            ThreadEvent(type = "thread.started", threadId = "thread_stream"),
            ThreadEvent(
                type = "item.completed",
                item = ThreadItem(id = "message_2", type = "agent_message", text = "流式响应"),
            ),
            ThreadEvent(type = "turn.completed", usage = null),
        )
        val fakeExec = FakeCodexExecRunner(events, json)
        val imagePath = Files.createTempFile("codex-test", ".png")
        imagePath.toFile().deleteOnExit()
        val inputs = listOf(
            UserInput.Text("第一段"),
            UserInput.Text("第二段"),
            UserInput.LocalImage(imagePath),
        )
        val session = CodexSession(fakeExec, CodexClientOptions(), ThreadOptions())

        val streamedTurn = session.runStreamed(inputs)
        val collected = streamedTurn.events.toList()

        assertEquals(events, collected)
        assertEquals("thread_stream", session.id)
        val capturedArgs = fakeExec.capturedArgs
        assertNotNull(capturedArgs)
        assertTrue(capturedArgs.input.contains("第一段"))
        assertTrue(capturedArgs.input.contains("第二段"))
        assertTrue(capturedArgs.input.contains("\n\n"))
        assertEquals(listOf(imagePath), capturedArgs.images)
        assertFalse(capturedArgs.skipGitRepoCheck)
    }

    private class FakeCodexExecRunner(
        private val events: List<ThreadEvent>,
        private val json: Json,
    ) : CodexExecRunner {
        var capturedArgs: CodexExecArgs? = null
            private set

        override fun run(args: CodexExecArgs): Flow<String> = flow {
            capturedArgs = args
            events.forEach { emit(json.encodeToString(ThreadEvent.serializer(), it)) }
        }
    }
}


