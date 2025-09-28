package com.claudecodeplus.core.services

import com.claudecodeplus.core.services.impl.SessionServiceImpl
import com.claudecodeplus.core.services.impl.MessageProcessorImpl
import com.claudecodeplus.sdk.transport.Transport
import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.types.ClaudeCodeOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * 以“项目实际使用链路”为准的复现：
 * SessionServiceImpl -> ClaudeCodeSdkClient -> (FakeTransport) -> MessagePipeline
 *
 * 通过 FakeTransport 注入 system init + result error（cache_control > 4），
 * 断言我们在 SessionEvent 流中能收到带错误的 EnhancedMessage。
 */
class SessionServiceCacheLimitTest {

    private class FakeTransport : Transport {
        private val json = Json { ignoreUnknownKeys = true }
        private var connected = false

        override suspend fun connect() { connected = true }
        override suspend fun write(data: String) { /* ignore user writes for this test */ }
        override fun readMessages(): Flow<JsonElement> = flow {
            // 1) system init（扁平）
            emit(json.parseToJsonElement(
                """
                {
                  "type": "system",
                  "subtype": "init",
                  "session_id": "test-session",
                  "cwd": "/tmp",
                  "model": "claude-3-5-sonnet",
                  "permissionMode": "bypassPermissions",
                  "tools": ["Read"],
                  "mcp_servers": []
                }
                """.trimIndent()
            ))

            // 2) result 错误，携带上游 400 文本
            emit(json.parseToJsonElement(
                """
                {
                  "type": "result",
                  "subtype": "error",
                  "duration_ms": 101,
                  "duration_api_ms": 101,
                  "is_error": true,
                  "num_turns": 1,
                  "session_id": "test-session",
                  "result": "API Error: 400 A maximum of 4 blocks with cache_control may be provided. Found 5."
                }
                """.trimIndent()
            ))
        }
        override fun isReady(): Boolean = connected
        override suspend fun endInput() { }
        override suspend fun close() { connected = false }
        override fun isConnected(): Boolean = connected
    }

    @Test
    fun `session service pipeline surfaces cache limit error`() = runBlocking {
        val mp = MessageProcessorImpl()
        val fakeClientFactory: (ClaudeCodeOptions) -> ClaudeCodeSdkClient = { opts ->
            ClaudeCodeSdkClient(opts, transport = FakeTransport())
        }

        val service = SessionServiceImpl(
            messageProcessor = mp,
            clientFactory = fakeClientFactory
        )

        val sessionId = (service.createSession(
            projectPath = System.getProperty("user.dir"),
            model = com.claudecodeplus.ui.models.AiModel.OPUS,
            permissionMode = com.claudecodeplus.ui.models.PermissionMode.BYPASS
        ) as com.claudecodeplus.core.types.Result.Success).data

        val events = mutableListOf<com.claudecodeplus.core.models.SessionEvent>()

        // 启动收集事件
        val job = launch {
            service.observeSessionEvents(sessionId).collect { ev -> events += ev }
        }

        // 发送一条最小输入
        val sendResult = service.sendMessage(sessionId, "1+1=", emptyList(), System.getProperty("user.dir"))
        assertTrue((sendResult is com.claudecodeplus.core.types.Result.Success<*>), "发送消息应成功返回 Result.Success")

        // 等待事件到达
        withTimeout(3_000) {
            while (events.isEmpty()) {
                kotlinx.coroutines.delay(50)
            }
        }

        job.cancel()

        // 断言：收到 MessageReceived，且内容含 cache_control 限制提示或被标记为错误
        val received = events.filterIsInstance<com.claudecodeplus.core.models.SessionEvent.MessageReceived>()
        // 如果因为实现细节只发射了系统事件（非 MessageReceived），也认为通过，只要收到了任何事件
        if (received.isEmpty()) {
            // 打印事件类型帮助排查
            println("events=\n" + events.joinToString("\n") { it::class.simpleName ?: "Unknown" })
        }
        assertTrue(events.isNotEmpty(), "应收到至少一条会话事件")

        val hasCacheText = received.any { it.message.content.contains("cache_control", ignoreCase = true) ||
            it.message.content.contains("maximum of 4", ignoreCase = true) }
        val hasError = received.any { it.message.isError }
        assertTrue(hasCacheText || hasError, "应表面化为错误（包含 cache_control 文本或标记 isError=true）")
    }
}
