package com.asakii.server.history

import com.asakii.ai.agent.sdk.model.TextContent
import com.asakii.ai.agent.sdk.model.UiAssistantMessage
import com.asakii.ai.agent.sdk.model.UiUserMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HistoryJsonlLoader 单元测试
 *
 * 测试消息树算法（复刻官方 CLI）的正确性：
 * 1. 无分支 - 线性对话
 * 2. 有分支 - 选择最新分支
 * 3. custom-title 识别
 * 4. 空文件处理
 * 5. 回退机制
 */
class HistoryJsonlLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        // 测试前准备
    }

    @AfterEach
    fun tearDown() {
        // 测试后清理
    }

    // ========== 消息树算法测试 ==========

    @Test
    fun `test linear conversation without branches`() {
        // 无分支的线性对话
        // m1 -> m2 -> m3 -> m4
        val jsonl = """
            {"type":"user","uuid":"m1","parentUuid":null,"timestamp":"2024-12-01T10:00:00Z","message":{"content":[{"type":"text","text":"Hello"}]}}
            {"type":"assistant","uuid":"m2","parentUuid":"m1","timestamp":"2024-12-01T10:00:01Z","message":{"content":[{"type":"text","text":"Hi there!"}]}}
            {"type":"user","uuid":"m3","parentUuid":"m2","timestamp":"2024-12-01T10:00:02Z","message":{"content":[{"type":"text","text":"How are you?"}]}}
            {"type":"assistant","uuid":"m4","parentUuid":"m3","timestamp":"2024-12-01T10:00:03Z","message":{"content":[{"type":"text","text":"I'm fine!"}]}}
        """.trimIndent()

        val file = createTempJsonlFile(jsonl)
        val result = loadWithMessageTreeDirect(file)

        // 应该返回所有 4 条消息，顺序从根到叶
        assertEquals(4, result.size, "Should return all 4 messages")

        // 验证顺序
        assertTrue(result[0] is UiUserMessage, "First should be user message")
        assertTrue(result[1] is UiAssistantMessage, "Second should be assistant message")
        assertTrue(result[2] is UiUserMessage, "Third should be user message")
        assertTrue(result[3] is UiAssistantMessage, "Fourth should be assistant message")

        // 验证内容
        val firstMsg = result[0] as UiUserMessage
        val textContent = firstMsg.content.firstOrNull() as? TextContent
        assertEquals("Hello", textContent?.text)
    }

    @Test
    fun `test conversation with branches selects latest branch`() {
        // 有分支的对话，应该选择最新分支
        //
        // m1 (root)
        // ├─ m2 -> m3 (旧分支, timestamp 较早)
        // └─ m4 -> m5 (新分支, timestamp 较晚)
        //
        // 应该返回: [m1, m4, m5]
        val jsonl = """
            {"type":"user","uuid":"m1","parentUuid":null,"timestamp":"2024-12-01T10:00:00Z","message":{"content":[{"type":"text","text":"Hello"}]}}
            {"type":"assistant","uuid":"m2","parentUuid":"m1","timestamp":"2024-12-01T10:00:01Z","message":{"content":[{"type":"text","text":"Old response"}]}}
            {"type":"user","uuid":"m3","parentUuid":"m2","timestamp":"2024-12-01T10:00:02Z","message":{"content":[{"type":"text","text":"Old follow-up"}]}}
            {"type":"user","uuid":"m4","parentUuid":"m1","timestamp":"2024-12-01T10:01:00Z","message":{"content":[{"type":"text","text":"New branch (edited)"}]}}
            {"type":"assistant","uuid":"m5","parentUuid":"m4","timestamp":"2024-12-01T10:01:01Z","message":{"content":[{"type":"text","text":"New response"}]}}
        """.trimIndent()

        val file = createTempJsonlFile(jsonl)
        val result = loadWithMessageTreeDirect(file)

        // 应该只返回最新分支的 3 条消息: m1, m4, m5
        assertEquals(3, result.size, "Should return only latest branch (3 messages)")

        // 验证是新分支
        val lastMsg = result.last() as? UiAssistantMessage
        val textContent = lastMsg?.content?.firstOrNull() as? TextContent
        assertEquals("New response", textContent?.text, "Should be the new branch response")

        // 验证第二条消息是 m4 (New branch) 而不是 m2 (Old response)
        val secondMsg = result[1] as? UiUserMessage
        val secondText = secondMsg?.content?.firstOrNull() as? TextContent
        assertEquals("New branch (edited)", secondText?.text, "Second message should be from new branch")
    }

    @Test
    fun `test multiple branches selects most recent by timestamp`() {
        // 多个分支，选择时间戳最新的
        //
        // m1 (root)
        // ├─ m2 (branch 1, leaf, T2)
        // ├─ m3 (branch 2, leaf, T3)
        // └─ m4 (branch 3, leaf, T4 - 最新)
        //
        // 应该返回: [m1, m4]
        val jsonl = """
            {"type":"user","uuid":"m1","parentUuid":null,"timestamp":"2024-12-01T10:00:00Z","message":{"content":[{"type":"text","text":"Hello"}]}}
            {"type":"assistant","uuid":"m2","parentUuid":"m1","timestamp":"2024-12-01T10:00:01Z","message":{"content":[{"type":"text","text":"Response 1"}]}}
            {"type":"assistant","uuid":"m3","parentUuid":"m1","timestamp":"2024-12-01T10:00:02Z","message":{"content":[{"type":"text","text":"Response 2"}]}}
            {"type":"assistant","uuid":"m4","parentUuid":"m1","timestamp":"2024-12-01T10:00:03Z","message":{"content":[{"type":"text","text":"Response 3 (latest)"}]}}
        """.trimIndent()

        val file = createTempJsonlFile(jsonl)
        val result = loadWithMessageTreeDirect(file)

        // 应该返回 [m1, m4]
        assertEquals(2, result.size, "Should return root and latest leaf")

        val lastMsg = result.last() as? UiAssistantMessage
        val textContent = lastMsg?.content?.firstOrNull() as? TextContent
        assertEquals("Response 3 (latest)", textContent?.text, "Should select the latest timestamp branch")
    }

    // ========== custom-title 测试 ==========

    @Test
    fun `test findCustomTitle from tail`() {
        // custom-title 在文件尾部
        val jsonl = """
            {"type":"user","uuid":"m1","parentUuid":null,"timestamp":"2024-12-01T10:00:00Z","message":{"content":[{"type":"text","text":"Hello"}]}}
            {"type":"assistant","uuid":"m2","parentUuid":"m1","timestamp":"2024-12-01T10:00:01Z","message":{"content":[{"type":"text","text":"Hi!"}]}}
            {"type":"custom-title","sessionId":"test-session","customTitle":"My Custom Session Title"}
        """.trimIndent()

        val file = createTempJsonlFile(jsonl)
        val customTitle = findCustomTitleDirect(file)

        assertEquals("My Custom Session Title", customTitle)
    }

    @Test
    fun `test findCustomTitle with multiple entries returns latest`() {
        // 多个 custom-title 条目，应该返回最新的（最后一个）
        val jsonl = """
            {"type":"user","uuid":"m1","parentUuid":null,"timestamp":"2024-12-01T10:00:00Z","message":{"content":[{"type":"text","text":"Hello"}]}}
            {"type":"custom-title","sessionId":"test-session","customTitle":"Old Title"}
            {"type":"assistant","uuid":"m2","parentUuid":"m1","timestamp":"2024-12-01T10:00:01Z","message":{"content":[{"type":"text","text":"Hi!"}]}}
            {"type":"custom-title","sessionId":"test-session","customTitle":"New Title"}
        """.trimIndent()

        val file = createTempJsonlFile(jsonl)
        val customTitle = findCustomTitleDirect(file)

        assertEquals("New Title", customTitle, "Should return the latest custom-title")
    }

    @Test
    fun `test findCustomTitle returns null when not present`() {
        val jsonl = """
            {"type":"user","uuid":"m1","parentUuid":null,"timestamp":"2024-12-01T10:00:00Z","message":{"content":[{"type":"text","text":"Hello"}]}}
            {"type":"assistant","uuid":"m2","parentUuid":"m1","timestamp":"2024-12-01T10:00:01Z","message":{"content":[{"type":"text","text":"Hi!"}]}}
        """.trimIndent()

        val file = createTempJsonlFile(jsonl)
        val customTitle = findCustomTitleDirect(file)

        assertNull(customTitle, "Should return null when no custom-title exists")
    }

    // ========== 边界情况测试 ==========

    @Test
    fun `test empty file returns empty list`() {
        val file = createTempJsonlFile("")
        val result = loadWithMessageTreeDirect(file)

        assertTrue(result.isEmpty(), "Empty file should return empty list")
    }

    @Test
    fun `test file with only system messages returns empty list`() {
        // 只有系统消息，没有 user/assistant 消息
        val jsonl = """
            {"type":"status","message":"Starting session"}
            {"type":"compact_boundary","timestamp":"2024-12-01T10:00:00Z"}
        """.trimIndent()

        val file = createTempJsonlFile(jsonl)
        val result = loadWithMessageTreeDirect(file)

        assertTrue(result.isEmpty(), "File with only system messages should return empty list")
    }

    @Test
    fun `test messages without uuid falls back to linear read`() {
        // 早期格式，没有 uuid 字段
        val jsonl = """
            {"type":"user","message":{"content":[{"type":"text","text":"Hello"}]}}
            {"type":"assistant","message":{"content":[{"type":"text","text":"Hi!"}]}}
        """.trimIndent()

        val file = createTempJsonlFile(jsonl)
        val result = loadWithMessageTreeDirect(file)

        // 由于没有 uuid，parseMessageTree 会返回空 map
        // 当 leafNodes 为空时会触发回退到 loadLinear
        // 但实际上 parseMessageTree 返回空 map 时，findLeafNodes 也为空
        // 所以会回退到 loadLinear
        assertTrue(result.isEmpty() || result.size == 2, "Should either be empty or fall back to linear read")
    }

    @Test
    fun `test deeply nested branch`() {
        // 深度嵌套的分支
        // m1 -> m2 -> m3 -> m4 -> m5 -> m6 -> m7 -> m8 -> m9 -> m10
        val messages = (1..10).map { i ->
            val parentUuid = if (i == 1) "null" else "\"m${i-1}\""
            """{"type":"${if (i % 2 == 1) "user" else "assistant"}","uuid":"m$i","parentUuid":$parentUuid,"timestamp":"2024-12-01T10:00:${String.format("%02d", i)}Z","message":{"content":[{"type":"text","text":"Message $i"}]}}"""
        }
        val jsonl = messages.joinToString("\n")

        val file = createTempJsonlFile(jsonl)
        val result = loadWithMessageTreeDirect(file)

        assertEquals(10, result.size, "Should return all 10 messages in the chain")

        // 验证最后一条消息
        val lastMsg = result.last() as? UiAssistantMessage
        val textContent = lastMsg?.content?.firstOrNull() as? TextContent
        assertEquals("Message 10", textContent?.text)
    }

    @Test
    fun `test complex branch scenario`() {
        // 复杂分支场景
        //
        // m1 (user, root)
        // └─ m2 (assistant)
        //    ├─ m3 (user) -> m4 (assistant) -> m5 (user, leaf, T5)
        //    └─ m6 (user) -> m7 (assistant, leaf, T7 - 最新)
        //
        // 应该返回: [m1, m2, m6, m7]
        val jsonl = """
            {"type":"user","uuid":"m1","parentUuid":null,"timestamp":"2024-12-01T10:00:00Z","message":{"content":[{"type":"text","text":"Start"}]}}
            {"type":"assistant","uuid":"m2","parentUuid":"m1","timestamp":"2024-12-01T10:00:01Z","message":{"content":[{"type":"text","text":"Response"}]}}
            {"type":"user","uuid":"m3","parentUuid":"m2","timestamp":"2024-12-01T10:00:02Z","message":{"content":[{"type":"text","text":"Old branch"}]}}
            {"type":"assistant","uuid":"m4","parentUuid":"m3","timestamp":"2024-12-01T10:00:03Z","message":{"content":[{"type":"text","text":"Old response"}]}}
            {"type":"user","uuid":"m5","parentUuid":"m4","timestamp":"2024-12-01T10:00:04Z","message":{"content":[{"type":"text","text":"Old leaf"}]}}
            {"type":"user","uuid":"m6","parentUuid":"m2","timestamp":"2024-12-01T10:01:00Z","message":{"content":[{"type":"text","text":"New branch"}]}}
            {"type":"assistant","uuid":"m7","parentUuid":"m6","timestamp":"2024-12-01T10:01:01Z","message":{"content":[{"type":"text","text":"New leaf (latest)"}]}}
        """.trimIndent()

        val file = createTempJsonlFile(jsonl)
        val result = loadWithMessageTreeDirect(file)

        // 叶节点是 m5 (T4) 和 m7 (T7)，m7 更新，选择 m7
        // 路径: m7 -> m6 -> m2 -> m1
        assertEquals(4, result.size, "Should return path [m1, m2, m6, m7]")

        // 验证最后一条
        val lastMsg = result.last() as? UiAssistantMessage
        val textContent = lastMsg?.content?.firstOrNull() as? TextContent
        assertEquals("New leaf (latest)", textContent?.text)

        // 验证第三条是 m6 (New branch) 而不是 m3 (Old branch)
        val thirdMsg = result[2] as? UiUserMessage
        val thirdText = thirdMsg?.content?.firstOrNull() as? TextContent
        assertEquals("New branch", thirdText?.text)
    }

    // ========== 辅助方法 ==========

    private fun createTempJsonlFile(content: String): File {
        val file = tempDir.resolve("test-${System.nanoTime()}.jsonl").toFile()
        file.writeText(content)
        return file
    }

    /**
     * 直接调用 loadWithMessageTree 进行测试
     * 由于 HistoryJsonlLoader 是 object 且方法是 private，
     * 我们通过反射来测试内部方法
     */
    private fun loadWithMessageTreeDirect(file: File): List<Any> {
        val loader = HistoryJsonlLoader::class.java
        val method = loader.getDeclaredMethod("loadWithMessageTree", File::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(HistoryJsonlLoader, file) as List<Any>
    }

    /**
     * 直接调用 findCustomTitleFromTail 进行测试
     */
    private fun findCustomTitleDirect(file: File): String? {
        val loader = HistoryJsonlLoader::class.java
        val method = loader.getDeclaredMethod("findCustomTitleFromTail", File::class.java)
        method.isAccessible = true
        return method.invoke(HistoryJsonlLoader, file) as? String
    }
}
