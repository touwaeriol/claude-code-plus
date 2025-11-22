package com.claudecodeplus.sdk


import com.claudecodeplus.sdk.types.AssistantMessage
import com.claudecodeplus.sdk.types.ClaudeCodeOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertTrue

class ModelSwitchIntegrationTest {

    private lateinit var client: ClaudeCodeSdkClient
    private lateinit var sessionId: String

    @BeforeEach
    fun setUp() {
        sessionId = "test-session-${UUID.randomUUID()}"
        client = ClaudeCodeSdkClient(
            ClaudeCodeOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 3,
                includePartialMessages = false
            )
        )
        runBlocking { client.connect() }
    }

    @AfterEach
    fun tearDown() {
        runBlocking {
            if (this@ModelSwitchIntegrationTest::client.isInitialized) {
                client.disconnect()
            }
        }
    }

    @Test
    fun `switch between sonnet and opus via SDK`() = runBlocking {
        val sonnetModel = queryCurrentModel("初始模型检测")
        assertTrue(sonnetModel.contains("sonnet", ignoreCase = true))

        client.setModel("opus")
        val opusModel = queryCurrentModel("切换到 Opus 后")
        assertTrue(opusModel.contains("opus", ignoreCase = true), "期望切换到 Opus，实际: $opusModel")

        client.setModel("sonnet")
        val backToSonnet = queryCurrentModel("再次切换回 Sonnet 后")
        assertTrue(backToSonnet.contains("sonnet", ignoreCase = true), "期望切换回 Sonnet，实际: $backToSonnet")
    }

    private suspend fun queryCurrentModel(stage: String): String {
        var reportedModel: String? = null
        client.query(
            prompt = "请直接告诉我你当前使用的 Claude 模型，只输出模型 ID。",
            sessionId = sessionId
        )

        client.receiveResponse().collect { message ->
            if (message is AssistantMessage) {
                reportedModel = message.model
            }
        }

        val model = reportedModel ?: error("未收到模型信息")
        println("[$stage] CLI 返回模型: $model")
        return model
    }
}
