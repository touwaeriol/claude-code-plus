package com.claudecodeplus.core.preprocessor

import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * SlashCommandInterceptor 单元测试
 */
class SlashCommandInterceptorTest {

    private val interceptor = SlashCommandInterceptor()
    private val mockClient = mockk<ClaudeCodeSdkClient>(relaxed = true)
    private val testSessionId = "test-session-123"

    @Test
    fun `test non-slash message continues`() = runBlocking {
        // Given: 普通消息
        val message = "帮我优化这段代码"

        // When: 预处理
        val result = interceptor.preprocess(message, mockClient, testSessionId)

        // Then: 应该继续发送
        assertIs<PreprocessResult.Continue>(result)
        assertEquals(message, result.message)

        // 不应该调用任何 SDK 方法
        coVerify(exactly = 0) { mockClient.setModel(any()) }
    }

    @Test
    fun `test unknown slash command continues`() = runBlocking {
        // Given: 未知命令
        val message = "/unknown-command arg1 arg2"

        // When: 预处理
        val result = interceptor.preprocess(message, mockClient, testSessionId)

        // Then: 应该继续发送（交给 Claude 处理）
        assertIs<PreprocessResult.Continue>(result)
        assertEquals(message, result.message)
    }

    @Test
    fun `test model command without args shows help`() = runBlocking {
        // Given: /model 命令但没有参数
        val message = "/model"

        // When: 预处理
        val result = interceptor.preprocess(message, mockClient, testSessionId)

        // Then: 应该被拦截并显示帮助信息
        assertIs<PreprocessResult.Intercepted>(result)
        assertTrue(result.feedback!!.contains("用法"))
        assertTrue(result.feedback!!.contains("opus"))
        assertTrue(result.feedback!!.contains("sonnet"))

        // 不应该调用 setModel
        coVerify(exactly = 0) { mockClient.setModel(any()) }
    }

    @Test
    fun `test model command with opus alias`() = runBlocking {
        // Given: /model opus 命令
        val message = "/model opus"

        // Mock: setModel 成功
        coEvery { mockClient.setModel(any()) } returns Unit

        // When: 预处理
        val result = interceptor.preprocess(message, mockClient, testSessionId)

        // Then: 应该被拦截并调用 setModel
        assertIs<PreprocessResult.Intercepted>(result)
        assertTrue(result.feedback!!.contains("✅"))
        assertTrue(result.feedback!!.contains("opus"))

        // 应该直接传递别名给 setModel（不转换）
        coVerify(exactly = 1) { mockClient.setModel("opus") }
    }

    @Test
    fun `test model command with sonnet-4_5 alias`() = runBlocking {
        // Given: /model sonnet-4.5 命令
        val message = "/model sonnet-4.5"

        coEvery { mockClient.setModel(any()) } returns Unit

        // When: 预处理
        val result = interceptor.preprocess(message, mockClient, testSessionId)

        // Then: 应该被拦截
        assertIs<PreprocessResult.Intercepted>(result)
        assertTrue(result.feedback!!.contains("✅"))
        assertTrue(result.feedback!!.contains("sonnet-4.5"))

        // 直接传递用户输入
        coVerify(exactly = 1) { mockClient.setModel("sonnet-4.5") }
    }

    @Test
    fun `test model command with haiku alias`() = runBlocking {
        // Given: /model haiku 命令
        val message = "/model haiku"

        coEvery { mockClient.setModel(any()) } returns Unit

        // When: 预处理
        val result = interceptor.preprocess(message, mockClient, testSessionId)

        // Then: 应该被拦截
        assertIs<PreprocessResult.Intercepted>(result)
        assertTrue(result.feedback!!.contains("✅"))
        assertTrue(result.feedback!!.contains("haiku"))

        // 直接传递用户输入
        coVerify(exactly = 1) { mockClient.setModel("haiku") }
    }

    @Test
    fun `test model command with full model ID`() = runBlocking {
        // Given: /model 完整模型 ID
        val fullModelId = "claude-opus-4-20250514"
        val message = "/model $fullModelId"

        coEvery { mockClient.setModel(any()) } returns Unit

        // When: 预处理
        val result = interceptor.preprocess(message, mockClient, testSessionId)

        // Then: 应该直接使用该 ID
        assertIs<PreprocessResult.Intercepted>(result)
        coVerify(exactly = 1) { mockClient.setModel(fullModelId) }
    }

    @Test
    fun `test model command handles errors`() = runBlocking {
        // Given: /model 命令
        val message = "/model opus"

        // Mock: setModel 抛出异常
        coEvery { mockClient.setModel(any()) } throws RuntimeException("Connection failed")

        // When: 预处理
        val result = interceptor.preprocess(message, mockClient, testSessionId)

        // Then: 应该被拦截并显示错误
        assertIs<PreprocessResult.Intercepted>(result)
        assertTrue(result.feedback!!.contains("❌"))
        assertTrue(result.feedback!!.contains("Connection failed"))

        // 即使失败也调用了 setModel
        coVerify(exactly = 1) { mockClient.setModel("opus") }
    }

    @Test
    fun `test model command with extra whitespace`() = runBlocking {
        // Given: /model 命令，带额外空格
        val message = "  /model   opus  "

        coEvery { mockClient.setModel(any()) } returns Unit

        // When: 预处理
        val result = interceptor.preprocess(message, mockClient, testSessionId)

        // Then: 应该正确解析（空格被 trim 掉）
        assertIs<PreprocessResult.Intercepted>(result)
        coVerify(exactly = 1) { mockClient.setModel("opus") }
    }

    @Test
    fun `test model command case insensitive`() = runBlocking {
        // Given: /MODEL OPUS (大写)
        val message = "/MODEL OPUS"

        coEvery { mockClient.setModel(any()) } returns Unit

        // When: 预处理
        val result = interceptor.preprocess(message, mockClient, testSessionId)

        // Then: 应该正确识别（命令大小写不敏感，但参数保持原样）
        assertIs<PreprocessResult.Intercepted>(result)
        coVerify(exactly = 1) { mockClient.setModel("OPUS") }
    }

    @Test
    fun `test model command with multiple args uses first`() = runBlocking {
        // Given: /model 命令，多个参数
        val message = "/model opus extra-arg another-arg"

        coEvery { mockClient.setModel(any()) } returns Unit

        // When: 预处理
        val result = interceptor.preprocess(message, mockClient, testSessionId)

        // Then: 应该只使用第一个参数
        assertIs<PreprocessResult.Intercepted>(result)
        coVerify(exactly = 1) { mockClient.setModel("opus") }
    }
}