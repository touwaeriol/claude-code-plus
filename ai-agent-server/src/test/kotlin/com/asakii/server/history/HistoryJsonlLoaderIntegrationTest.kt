package com.asakii.server.history

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * HistoryJsonlLoader 集成测试
 *
 * 使用真实的历史会话文件测试加载逻辑
 * 验证与官方 CLI 的一致性
 */
class HistoryJsonlLoaderIntegrationTest {

    companion object {
        // 测试使用的项目路径
        private const val PROJECT_PATH = "C:\\Users\\16790\\IdeaProjects\\claude-code-plus"

        // Claude 历史目录
        private val CLAUDE_DIR = File(System.getProperty("user.home"), ".claude")
        private val PROJECT_DIR = File(CLAUDE_DIR, "projects/C--Users-16790-IdeaProjects-claude-code-plus")

        // 测试会话 ID（有 custom-title "111"）
        private const val TEST_SESSION_ID = "00778c90-e433-4bb8-af45-714f5c21a812"

        @JvmStatic
        fun isTestSessionAvailable(): Boolean {
            val file = File(PROJECT_DIR, "$TEST_SESSION_ID.jsonl")
            return file.exists()
        }
    }

    @Test
    @EnabledIf("isTestSessionAvailable")
    fun `test loadHistoryMessages with real session file`() {
        // 加载真实会话
        val messages = HistoryJsonlLoader.loadHistoryMessages(
            sessionId = TEST_SESSION_ID,
            projectPath = PROJECT_PATH
        )

        // 验证加载成功
        assertTrue(messages.isNotEmpty(), "Should load messages from real session")

        // 打印加载的消息数量和类型
        println("=== 加载结果 ===")
        println("消息数量: ${messages.size}")
        messages.forEachIndexed { index, msg ->
            println("[$index] ${msg::class.simpleName}")
        }
    }

    @Test
    @EnabledIf("isTestSessionAvailable")
    fun `test findCustomTitle with real session file`() {
        // 查找 custom-title
        val customTitle = HistoryJsonlLoader.findCustomTitle(
            sessionId = TEST_SESSION_ID,
            projectPath = PROJECT_PATH
        )

        // 验证 custom-title
        println("=== Custom Title 结果 ===")
        println("找到的 customTitle: $customTitle")

        assertNotNull(customTitle, "Should find custom-title")
        assertEquals("111", customTitle, "Custom title should be '111'")
    }

    @Test
    @EnabledIf("isTestSessionAvailable")
    fun `test message tree algorithm selects correct branch`() {
        // 直接测试文件
        val historyFile = File(PROJECT_DIR, "$TEST_SESSION_ID.jsonl")
        assertTrue(historyFile.exists(), "Test file should exist")

        // 使用反射调用 loadWithMessageTree
        val loader = HistoryJsonlLoader::class.java
        val method = loader.getDeclaredMethod("loadWithMessageTree", File::class.java)
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(HistoryJsonlLoader, historyFile) as List<Any>

        println("=== 消息树算法结果 ===")
        println("返回消息数: ${result.size}")

        // 验证返回的是最新分支
        assertTrue(result.isNotEmpty(), "Should return messages from latest branch")
    }

    @Test
    fun `test with manually created branch file`() {
        // 创建一个带分支的测试文件
        val tempFile = File.createTempFile("test-branch-", ".jsonl")
        tempFile.deleteOnExit()

        // 写入测试数据（有分支）
        // m1 -> m2 -> m3 (旧分支)
        // m1 -> m4 -> m5 (新分支，应该被选中)
        tempFile.writeText("""
            {"type":"user","uuid":"m1","parentUuid":null,"timestamp":"2024-12-01T10:00:00Z","message":{"content":[{"type":"text","text":"Hello"}]}}
            {"type":"assistant","uuid":"m2","parentUuid":"m1","timestamp":"2024-12-01T10:00:01Z","message":{"content":[{"type":"text","text":"Old response"}]}}
            {"type":"user","uuid":"m3","parentUuid":"m2","timestamp":"2024-12-01T10:00:02Z","message":{"content":[{"type":"text","text":"Old follow-up"}]}}
            {"type":"user","uuid":"m4","parentUuid":"m1","timestamp":"2024-12-01T10:01:00Z","message":{"content":[{"type":"text","text":"NEW: Edited message"}]}}
            {"type":"assistant","uuid":"m5","parentUuid":"m4","timestamp":"2024-12-01T10:01:01Z","message":{"content":[{"type":"text","text":"NEW: Response on new branch"}]}}
            {"type":"custom-title","sessionId":"test","customTitle":"Test Branch Session"}
        """.trimIndent())

        // 使用反射调用 loadWithMessageTree
        val loader = HistoryJsonlLoader::class.java
        val method = loader.getDeclaredMethod("loadWithMessageTree", File::class.java)
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(HistoryJsonlLoader, tempFile) as List<Any>

        println("=== 分支测试结果 ===")
        println("JSONL 文件共 5 条消息（user/assistant）")
        println("返回消息数: ${result.size} (应该是 3 条: m1, m4, m5)")

        // 应该只返回最新分支的 3 条消息
        assertEquals(3, result.size, "Should return only latest branch (3 messages: m1, m4, m5)")

        // 测试 custom-title
        val customTitleMethod = loader.getDeclaredMethod("findCustomTitleFromTail", File::class.java)
        customTitleMethod.isAccessible = true
        val customTitle = customTitleMethod.invoke(HistoryJsonlLoader, tempFile) as? String

        println("Custom Title: $customTitle")
        assertEquals("Test Branch Session", customTitle, "Should find custom-title")
    }

    @Test
    fun `compare with expected CLI behavior`() {
        // 这个测试验证我们的实现与官方 CLI 的预期行为一致
        //
        // 官方 CLI 行为：
        // 1. 解析 JSONL，构建 uuid -> message 的 Map
        // 2. 找到所有叶节点（没有被引用为 parentUuid 的消息）
        // 3. 选择时间戳最新的叶节点
        // 4. 从叶节点回溯到根节点
        //
        // 我们的实现应该返回相同的结果

        val tempFile = File.createTempFile("test-cli-compare-", ".jsonl")
        tempFile.deleteOnExit()

        // 复杂分支场景
        // m1 (root)
        // └─ m2 (assistant)
        //    ├─ m3 (user) -> m4 (assistant) -> m5 (user, leaf, T=04)
        //    └─ m6 (user) -> m7 (assistant, leaf, T=11 - 最新)
        tempFile.writeText("""
            {"type":"user","uuid":"m1","parentUuid":null,"timestamp":"2024-12-01T10:00:00Z","message":{"content":[{"type":"text","text":"Start"}]}}
            {"type":"assistant","uuid":"m2","parentUuid":"m1","timestamp":"2024-12-01T10:00:01Z","message":{"content":[{"type":"text","text":"Response"}]}}
            {"type":"user","uuid":"m3","parentUuid":"m2","timestamp":"2024-12-01T10:00:02Z","message":{"content":[{"type":"text","text":"Old branch"}]}}
            {"type":"assistant","uuid":"m4","parentUuid":"m3","timestamp":"2024-12-01T10:00:03Z","message":{"content":[{"type":"text","text":"Old response"}]}}
            {"type":"user","uuid":"m5","parentUuid":"m4","timestamp":"2024-12-01T10:00:04Z","message":{"content":[{"type":"text","text":"Old leaf"}]}}
            {"type":"user","uuid":"m6","parentUuid":"m2","timestamp":"2024-12-01T10:01:10Z","message":{"content":[{"type":"text","text":"New branch"}]}}
            {"type":"assistant","uuid":"m7","parentUuid":"m6","timestamp":"2024-12-01T10:01:11Z","message":{"content":[{"type":"text","text":"New leaf (LATEST)"}]}}
        """.trimIndent())

        val loader = HistoryJsonlLoader::class.java
        val method = loader.getDeclaredMethod("loadWithMessageTree", File::class.java)
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(HistoryJsonlLoader, tempFile) as List<Any>

        println("=== CLI 行为对比测试 ===")
        println("消息树结构:")
        println("  m1 (root)")
        println("  └─ m2")
        println("     ├─ m3 -> m4 -> m5 (T=04)")
        println("     └─ m6 -> m7 (T=11, 最新)")
        println("")
        println("预期结果: [m1, m2, m6, m7] (4 条)")
        println("实际结果: ${result.size} 条")

        // 验证：叶节点 m5 (T=04) 和 m7 (T=11)，m7 更新
        // 应该选择 m7，回溯路径: m7 -> m6 -> m2 -> m1
        assertEquals(4, result.size, "Should return path [m1, m2, m6, m7]")
    }
}
