package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.ClaudeCodeOptions
import com.claudecodeplus.sdk.types.Message
import com.claudecodeplus.sdk.types.ResultMessage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClaudeCodeSdkClientCacheControlErrorTest {

    @Test
    fun `query emits cache control error result`() = runBlocking {
        val client = ClaudeCodeSdkClient(
            ClaudeCodeOptions(
                model = null,
                allowedTools = emptyList(),
                verbose = true
            )
        )

        try {
            client.connect()
            client.query("演示edit工具")

            val responses: List<Message> = withTimeout(60_000) {
                client.receiveResponse().toList()
            }
            val resultMessage = responses.filterIsInstance<ResultMessage>().single()

            assertTrue(resultMessage.isError, "Result message should be marked as error")

            val resultText = resultMessage.result
            assertNotNull(resultText, "Result message should contain error text")
            assertTrue(
                resultText.contains("A maximum of 4 blocks with cache_control may be provided. Found 5."),
                "Result should mention cache_control limit violation"
            )
        } finally {
            client.disconnect()
        }
    }
}
