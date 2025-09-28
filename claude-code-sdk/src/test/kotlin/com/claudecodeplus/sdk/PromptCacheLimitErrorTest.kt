package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.transport.Transport
import com.claudecodeplus.sdk.types.ClaudeCodeOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Reproduces the upstream API error surface path by simulating a CLI response
 * that contains the Anthropic 400 invalid_request_error for cache_control limit.
 *
 * This does not call the real CLI or API. It validates that our parsing pipeline
 * can surface the error to receivers as a typed ResultMessage.isError=true, which
 * is what the UI relies on to show error feedback.
 */
class PromptCacheLimitErrorTest {

    private class FakeTransport : Transport {
        private val json = Json { ignoreUnknownKeys = true }
        private var connected = false

        override suspend fun connect() { connected = true }
        override suspend fun write(data: String) { /* ignore user writes in this fake */ }
        override fun readMessages(): Flow<JsonElement> = flow {
            // 1) System init (flattened) as sent by CLI
            val init = json.parseToJsonElement(
                """
                {
                  "type": "system",
                  "subtype": "init",
                  "session_id": "test-session",
                  "cwd": "/tmp",
                  "model": "claude-3-5-sonnet",
                  "permissionMode": "bypassPermissions",
                  "tools": ["Read","Write"],
                  "mcp_servers": []
                }
                """.trimIndent()
            )
            emit(init)

            // 2) Upstream error surfaced as a Result message with is_error=true
            val resultError = json.parseToJsonElement(
                """
                {
                  "type": "result",
                  "subtype": "error",
                  "duration_ms": 120,
                  "duration_api_ms": 120,
                  "is_error": true,
                  "num_turns": 1,
                  "session_id": "test-session",
                  "result": "API Error: 400 {\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"A maximum of 4 blocks with cache_control may be provided. Found 5.\"},\"request_id\":\"req_dummy\"}"
                }
                """.trimIndent()
            )
            emit(resultError)
        }
        override fun isReady(): Boolean = connected
        override suspend fun endInput() { /* no-op */ }
        override suspend fun close() { connected = false }
        override fun isConnected(): Boolean = connected
    }

    @Test
    fun `surface invalid_request_error from CLI as ResultMessage isError`() = runBlocking {
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(), transport = FakeTransport())

        client.connect()
        client.query("1+1=")

        var sawError = false
        var sawResult = false

        withTimeout(5_000) {
            client.receiveResponse().collect { msg ->
                when (msg) {
                    is com.claudecodeplus.sdk.types.ResultMessage -> {
                        sawResult = true
                        if (msg.isError) sawError = true
                    }
                    else -> { /* swallow */ }
                }
            }
        }

        assertTrue(sawResult, "should surface a ResultMessage from CLI stream")
        assertTrue(sawError, "ResultMessage should be marked isError=true for 400 invalid_request_error")
        client.disconnect()
    }
}
