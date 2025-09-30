package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.test.assertTrue

/**
 * Integration test that uses the real SubprocessTransport (Claude CLI)
 * to try reproducing the Anthropic 400 invalid_request_error about
 * cache_control blocks exceeding the maximum of 4.
 *
 * The test is conditionally executed only when:
 * - `claude` CLI is available in PATH
 * - `ANTHROPIC_API_KEY` is set
 *
 * NOTE: Because upstream behavior may change across CLI versions/models,
 * this test asserts the error only when the env var `EXPECT_CACHE_LIMIT_ERROR=true` is set.
 * Otherwise, it will just print the outcome and assert that a ResultMessage was received.
 */
class PromptCacheLimitIntegrationTest {

    private fun hasCli(): Boolean {
        return try {
            val pb = ProcessBuilder("bash", "-lc", "command -v claude >/dev/null 2>&1; echo $?")
            val p = pb.start()
            val code = p.waitFor()
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText().trim()
            code == 0 && out == "0"
        } catch (_: Exception) {
            false
        }
    }

    @Test
    fun `reproduce upstream cache_control limit error if environment matches`() = runBlocking {
        assumeTrue(hasCli(), "claude CLI not found; skipping integration test")
        assumeTrue(System.getenv("ANTHROPIC_API_KEY")?.isNotBlank() == true, "ANTHROPIC_API_KEY not set; skipping integration test")

        val expectError = System.getenv("EXPECT_CACHE_LIMIT_ERROR") == "true"

        val options = ClaudeCodeOptions(
            model = System.getenv("CLAUDE_MODEL") ?: "claude-3-5-sonnet-20241022",
            // Intentionally add both prompts to increase the chance of >4 cacheable system blocks
            systemPrompt = "You are helpful.",
            permissionMode = PermissionMode.BYPASS_PERMISSIONS,
            verbose = true
        )

        val client = ClaudeCodeSdkClient(options)

        client.connect()
        client.query("1+1=")

        var sawResult = false
        var sawCacheLimitError = false

        withTimeout(60_000) {
            client.receiveResponse().collect { msg ->
                when (msg) {
                    is ResultMessage -> {
                        sawResult = true
                        if (msg.isError && (msg.result?.contains("maximum of 4 blocks", ignoreCase = true) == true ||
                                msg.result?.contains("cache_control", ignoreCase = true) == true)) {
                            sawCacheLimitError = true
                        }
                    }
                    else -> { /* ignore other messages */ }
                }
            }
        }

        client.disconnect()

        if (expectError) {
            assertTrue(sawCacheLimitError, "Expected to reproduce cache_control limit error, but it did not occur")
        } else {
            // At least ensure we received a complete result.
            assertTrue(sawResult, "Should receive a ResultMessage from CLI")
        }
    }
}

