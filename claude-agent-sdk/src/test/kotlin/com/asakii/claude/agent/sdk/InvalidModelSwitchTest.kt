package com.asakii.claude.agent.sdk


import com.asakii.claude.agent.sdk.types.ClaudeCodeOptions
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InvalidModelSwitchTest {

    private lateinit var client: ClaudeCodeSdkClient

    @BeforeEach
    fun setUp() = runBlocking {
        client = ClaudeCodeSdkClient(
            ClaudeCodeOptions(model = "claude-sonnet-4-20250514", maxTurns = 1)
        )
        client.connect()
    }

    @AfterEach
    fun tearDown() = runBlocking {
        if (this@InvalidModelSwitchTest::client.isInitialized) {
            client.disconnect()
        }
    }

    @Test
    fun testSetModelWithInvalidId() = runBlocking {
        println("尝试 setModel(\"aaa\")")
        try {
            client.setModel("aaa")
            println("setModel 调用完成（未抛异常）")
            println("向 CLI 询问当前模型以验证响应")
            client.query("现在你使用的模型是什么？只回答模型 ID。")
            client.receiveResponse().collect { message ->
                if (message is com.asakii.claude.agent.sdk.types.AssistantMessage) {
                    println("CLI 实际返回模型: ${message.model}")
                }
                if (message is com.asakii.claude.agent.sdk.types.ResultMessage) {
                    return@collect
                }
            }
        } catch (e: Exception) {
            println("setModel 抛出异常: ${e::class.simpleName} - ${e.message}")
            e.printStackTrace()
        }
    }
}
