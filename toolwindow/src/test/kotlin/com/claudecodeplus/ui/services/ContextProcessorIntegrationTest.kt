/*
 * ContextProcessorIntegrationTest.kt
 * 
 * 集成测试专注于 ContextProcessor 和相关功能的测试
 */

package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ContextProcessorIntegrationTest {

    @Test
    fun `front matter round trip conversion should be consistent`() {
        // 创建测试上下文
        val originalContexts = listOf(
            ContextReference.FileReference(path = "docs/test.md"),
            ContextReference.WebReference(url = "https://example.com", title = "Example"),
            ContextReference.FolderReference(path = "src/main"),
            ContextReference.SymbolReference(
                name = "TestClass",
                type = SymbolType.CLASS,
                file = "test.kt",
                line = 1
            ),
            ContextReference.ImageReference(path = "assets/logo.png", filename = "logo.png")
        )

        // 生成前缀信息
        val frontMatter = ContextProcessor.generateFrontMatter(originalContexts)
        
        // 解析前缀信息
        val parsedContexts = ContextProcessor.parseFrontMatter(frontMatter)
        
        // 验证往返转换一致性
        assertEquals(originalContexts.size, parsedContexts.size)
        
        originalContexts.forEachIndexed { index, original ->
            val parsed = parsedContexts[index]
            
            when (original) {
                is ContextReference.FileReference -> {
                    assertTrue(parsed is ContextReference.FileReference)
                    assertEquals(original.path, (parsed as ContextReference.FileReference).path)
                }
                is ContextReference.WebReference -> {
                    assertTrue(parsed is ContextReference.WebReference)
                    assertEquals(original.url, (parsed as ContextReference.WebReference).url)
                }
                is ContextReference.FolderReference -> {
                    assertTrue(parsed is ContextReference.FolderReference)
                    assertEquals(original.path, (parsed as ContextReference.FolderReference).path)
                }
                is ContextReference.SymbolReference -> {
                    assertTrue(parsed is ContextReference.SymbolReference)
                    assertEquals(original.name, (parsed as ContextReference.SymbolReference).name)
                }
                is ContextReference.ImageReference -> {
                    assertTrue(parsed is ContextReference.ImageReference)
                    assertEquals(original.path, (parsed as ContextReference.ImageReference).path)
                    assertEquals(original.filename, (parsed as ContextReference.ImageReference).filename)
                }
                else -> fail("Unexpected context type: $original")
            }
        }
    }

    @Test
    fun `context processor should handle @ symbol in file paths correctly`() {
        val frontMatter = """
            ---
            contexts:
              - file:@src/main/kotlin/Test.kt
              - file:docs/README.md
            ---
        """.trimIndent()

        val contexts = ContextProcessor.parseFrontMatter(frontMatter)
        
        assertEquals(2, contexts.size)
        
        // 验证带@符号的路径被正确处理
        val fileRef1 = contexts[0] as ContextReference.FileReference
        assertEquals("src/main/kotlin/Test.kt", fileRef1.path)
        
        // 验证不带@符号的路径保持不变
        val fileRef2 = contexts[1] as ContextReference.FileReference
        assertEquals("docs/README.md", fileRef2.path)
    }

    @Test
    fun `context processor should handle mixed context types`() {
        val frontMatter = """
            ---
            contexts:
              - file:@src/test.kt
              - web:https://docs.example.com
              - folder:@assets
              - symbol:TestClass
              - image:@images/logo.png
            ---
        """.trimIndent()

        val contexts = ContextProcessor.parseFrontMatter(frontMatter)

        assertEquals(5, contexts.size)
        assertTrue(contexts[0] is ContextReference.FileReference)
        assertTrue(contexts[1] is ContextReference.WebReference)
        assertTrue(contexts[2] is ContextReference.FolderReference)
        assertTrue(contexts[3] is ContextReference.SymbolReference)
        assertTrue(contexts[4] is ContextReference.ImageReference)
    }

    @Test
    fun `context processor should handle malformed front matter gracefully`() {
        val frontMatter = """
            ---
            contexts:
              - invalid:syntax
              - file:valid.txt
              - malformed line without prefix
            ---
        """.trimIndent()

        val contexts = ContextProcessor.parseFrontMatter(frontMatter)

        // 应该只解析出有效的上下文
        assertEquals(1, contexts.size)
        assertTrue(contexts[0] is ContextReference.FileReference)
    }

    @Test
    fun `context processor should handle empty contexts section`() {
        val frontMatter = """
            ---
            contexts:
            ---
        """.trimIndent()

        val contexts = ContextProcessor.parseFrontMatter(frontMatter)
        assertTrue(contexts.isEmpty())
    }

    @Test
    fun `context processor should handle edge cases`() {
        val testCases = mapOf(
            // 空字符串
            "" to 0,
            
            // 只有分隔符
            "---\n---" to 0,
            
            // 没有contexts部分
            "---\ntitle: test\n---" to 0,
            
            // contexts为空
            "---\ncontexts:\n---" to 0,
            
            // 无效的上下文行
            "---\ncontexts:\n  - invalid\n  - file:@valid.txt\n---" to 1
        )

        testCases.forEach { (frontMatter, expectedCount) ->
            val contexts = ContextProcessor.parseFrontMatter(frontMatter)
            assertEquals(expectedCount, contexts.size, "Failed for input: $frontMatter")
        }
    }

    @Test
    fun `context processor generate and parse should be symmetric`() {
        val contexts = listOf(
            ContextReference.FileReference(path = "test.kt"),
            ContextReference.WebReference(url = "https://example.com", title = null)
        )

        val frontMatter = ContextProcessor.generateFrontMatter(contexts)
        val parsedContexts = ContextProcessor.parseFrontMatter(frontMatter)

        assertEquals(contexts.size, parsedContexts.size)
        assertEquals("test.kt", (parsedContexts[0] as ContextReference.FileReference).path)
        assertEquals("https://example.com", (parsedContexts[1] as ContextReference.WebReference).url)
    }
}