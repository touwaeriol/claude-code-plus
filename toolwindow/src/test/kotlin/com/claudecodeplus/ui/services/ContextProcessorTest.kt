/*
 * ContextProcessorTest.kt
 * 
 * 上下文处理器单元测试
 */

package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.ContextReference
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ContextProcessorTest {
    
    @TempDir
    lateinit var tempDir: Path
    
    private lateinit var projectCwd: String
    private lateinit var testFile1: File
    private lateinit var testFile2: File
    private lateinit var testDir: File
    private lateinit var imageFile: File
    
    @BeforeEach
    fun setUp() {
        projectCwd = tempDir.toString()
        
        // 创建测试文件
        testFile1 = File(tempDir.toFile(), "test1.kt").apply {
            writeText("class TestClass1 {\n    fun test() {}\n}")
        }
        
        testFile2 = File(tempDir.toFile(), "test2.md").apply {
            writeText("# 测试文档\n\n这是一个测试文档。")
        }
        
        // 创建测试目录
        testDir = File(tempDir.toFile(), "testDir").apply {
            mkdirs()
        }
        
        // 创建测试图片文件
        imageFile = File(tempDir.toFile(), "test.png").apply {
            writeText("fake png content") // 模拟图片文件
        }
    }
    
    @AfterEach
    fun tearDown() {
        // JUnit 5 会自动清理 @TempDir
    }
    
    @Test
    fun `buildPromptWithContextFiles should return original prompt when no contexts`() {
        val contexts = emptyList<ContextReference>()
        val originalPrompt = "Hello, Claude!"
        
        val result = ContextProcessor.buildPromptWithContextFiles(contexts, projectCwd, originalPrompt)
        
        assertEquals(originalPrompt, result)
    }
    
    @Test
    fun `buildPromptWithContextFiles should wrap file content in XML`() {
        val contexts = listOf(
            ContextReference.FileReference(path = "test1.kt")
        )
        val originalPrompt = "请分析这个文件"
        
        val result = ContextProcessor.buildPromptWithContextFiles(contexts, projectCwd, originalPrompt)
        
        assertTrue(result.contains("<context_files>"))
        assertTrue(result.contains("<file path=\"test1.kt\">"))
        assertTrue(result.contains("class TestClass1"))
        assertTrue(result.contains("</file>"))
        assertTrue(result.contains("</context_files>"))
        assertTrue(result.endsWith(originalPrompt))
    }
    
    @Test
    fun `buildPromptWithContextFiles should handle multiple files`() {
        val contexts = listOf(
            ContextReference.FileReference(path = "test1.kt"),
            ContextReference.FileReference(path = "test2.md")
        )
        val originalPrompt = "请分析这些文件"
        
        val result = ContextProcessor.buildPromptWithContextFiles(contexts, projectCwd, originalPrompt)
        
        assertTrue(result.contains("<file path=\"test1.kt\">"))
        assertTrue(result.contains("class TestClass1"))
        assertTrue(result.contains("<file path=\"test2.md\">"))
        assertTrue(result.contains("# 测试文档"))
        assertTrue(result.endsWith(originalPrompt))
    }
    
    @Test
    fun `buildPromptWithContextFiles should skip non-existent files`() {
        val contexts = listOf(
            ContextReference.FileReference(path = "test1.kt"),
            ContextReference.FileReference(path = "non-existent.txt")
        )
        val originalPrompt = "请分析这些文件"
        
        val result = ContextProcessor.buildPromptWithContextFiles(contexts, projectCwd, originalPrompt)
        
        assertTrue(result.contains("<file path=\"test1.kt\">"))
        assertFalse(result.contains("non-existent.txt"))
        assertTrue(result.endsWith(originalPrompt))
    }
    
    @Test
    fun `generateFrontMatter should create correct YAML format`() {
        val contexts = listOf(
            ContextReference.FileReference(path = "docs/test.md"),
            ContextReference.WebReference(url = "https://example.com", title = "Example"),
            ContextReference.FolderReference(path = "src/main")
        )
        
        val result = ContextProcessor.generateFrontMatter(contexts)
        
        val expectedLines = listOf(
            "---",
            "contexts:",
            "  - file:@docs/test.md",
            "  - web:https://example.com",
            "  - folder:@src/main",
            "---"
        )
        
        val actualLines = result.trim().split("\n")
        assertEquals(expectedLines.size, actualLines.size)
        expectedLines.forEachIndexed { index, expected ->
            assertEquals(expected, actualLines[index])
        }
    }
    
    @Test
    fun `generateFrontMatter should return empty string for no contexts`() {
        val contexts = emptyList<ContextReference>()
        
        val result = ContextProcessor.generateFrontMatter(contexts)
        
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun `parseFrontMatter should parse file references correctly`() {
        val frontMatter = """
            ---
            contexts:
              - file:@docs/test.md
              - file:src/main.kt
            ---
        """.trimIndent()
        
        val result = ContextProcessor.parseFrontMatter(frontMatter)
        
        assertEquals(2, result.size)
        assertTrue(result[0] is ContextReference.FileReference)
        assertEquals("docs/test.md", (result[0] as ContextReference.FileReference).path)
        assertTrue(result[1] is ContextReference.FileReference)
        assertEquals("src/main.kt", (result[1] as ContextReference.FileReference).path)
    }
    
    @Test
    fun `parseFrontMatter should parse web references correctly`() {
        val frontMatter = """
            ---
            contexts:
              - web:https://example.com
              - web:https://docs.example.com/guide
            ---
        """.trimIndent()
        
        val result = ContextProcessor.parseFrontMatter(frontMatter)
        
        assertEquals(2, result.size)
        assertTrue(result[0] is ContextReference.WebReference)
        assertEquals("https://example.com", (result[0] as ContextReference.WebReference).url)
        assertTrue(result[1] is ContextReference.WebReference)
        assertEquals("https://docs.example.com/guide", (result[1] as ContextReference.WebReference).url)
    }
    
    @Test
    fun `parseFrontMatter should parse mixed references correctly`() {
        val frontMatter = """
            ---
            contexts:
              - file:@docs/test.md
              - web:https://example.com
              - folder:@src/main
              - symbol:TestClass
              - image:@assets/logo.png
            ---
        """.trimIndent()
        
        val result = ContextProcessor.parseFrontMatter(frontMatter)
        
        assertEquals(5, result.size)
        assertTrue(result[0] is ContextReference.FileReference)
        assertEquals("docs/test.md", (result[0] as ContextReference.FileReference).path)
        assertTrue(result[1] is ContextReference.WebReference)
        assertEquals("https://example.com", (result[1] as ContextReference.WebReference).url)
        assertTrue(result[2] is ContextReference.FolderReference)
        assertEquals("src/main", (result[2] as ContextReference.FolderReference).path)
        assertTrue(result[3] is ContextReference.SymbolReference)
        assertEquals("TestClass", (result[3] as ContextReference.SymbolReference).name)
        assertTrue(result[4] is ContextReference.ImageReference)
        assertEquals("assets/logo.png", (result[4] as ContextReference.ImageReference).path)
        assertEquals("logo.png", (result[4] as ContextReference.ImageReference).filename)
    }
    
    @Test
    fun `parseFrontMatter should handle empty front matter`() {
        val frontMatter = ""
        
        val result = ContextProcessor.parseFrontMatter(frontMatter)
        
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun `parseFrontMatter should handle front matter without contexts`() {
        val frontMatter = """
            ---
            title: Test Document
            author: Test User
            ---
        """.trimIndent()
        
        val result = ContextProcessor.parseFrontMatter(frontMatter)
        
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun `validateContext should validate file references correctly`() {
        val validFileContext = ContextReference.FileReference(path = "test1.kt")
        val invalidFileContext = ContextReference.FileReference(path = "non-existent.txt")
        
        assertTrue(ContextProcessor.validateContext(validFileContext, projectCwd))
        assertFalse(ContextProcessor.validateContext(invalidFileContext, projectCwd))
    }
    
    @Test
    fun `validateContext should validate web references correctly`() {
        val validWebContext = ContextReference.WebReference(url = "https://example.com", title = null)
        val invalidWebContext = ContextReference.WebReference(url = "invalid-url", title = null)
        
        assertTrue(ContextProcessor.validateContext(validWebContext, projectCwd))
        assertFalse(ContextProcessor.validateContext(invalidWebContext, projectCwd))
    }
    
    @Test
    fun `validateContext should validate folder references correctly`() {
        val validFolderContext = ContextReference.FolderReference(path = "testDir")
        val invalidFolderContext = ContextReference.FolderReference(path = "non-existent-dir")
        
        assertTrue(ContextProcessor.validateContext(validFolderContext, projectCwd))
        assertFalse(ContextProcessor.validateContext(invalidFolderContext, projectCwd))
    }
    
    @Test
    fun `validateContext should validate symbol references as always valid`() {
        val symbolContext = ContextReference.SymbolReference(
            name = "TestClass",
            type = com.claudecodeplus.ui.models.SymbolType.CLASS,
            file = "test.kt",
            line = 1
        )
        
        assertTrue(ContextProcessor.validateContext(symbolContext, projectCwd))
    }
    
    @Test
    fun `validateContext should validate image references correctly`() {
        val validImageContext = ContextReference.ImageReference(path = "test.png", filename = "test.png")
        val invalidImageContext = ContextReference.ImageReference(path = "non-existent.png", filename = "non-existent.png")
        
        assertTrue(ContextProcessor.validateContext(validImageContext, projectCwd))
        assertFalse(ContextProcessor.validateContext(invalidImageContext, projectCwd))
    }
    
    @Test
    fun `getContextStats should calculate statistics correctly`() {
        val contexts = listOf(
            ContextReference.FileReference(path = "test1.kt"),
            ContextReference.FileReference(path = "test2.md"),
            ContextReference.FileReference(path = "non-existent.txt"),
            ContextReference.WebReference(url = "https://example.com", title = null)
        )
        
        val stats = ContextProcessor.getContextStats(contexts, projectCwd)
        
        assertEquals(4, stats.totalContexts)
        assertEquals(3, stats.totalFiles)
        assertEquals(2, stats.validFiles)
        assertTrue(stats.totalSizeBytes > 0)
        assertTrue(stats.fileTypeCount.containsKey("kt"))
        assertTrue(stats.fileTypeCount.containsKey("md"))
    }
    
    @Test
    fun `getContextStats should handle empty context list`() {
        val contexts = emptyList<ContextReference>()
        
        val stats = ContextProcessor.getContextStats(contexts, projectCwd)
        
        assertEquals(0, stats.totalContexts)
        assertEquals(0, stats.totalFiles)
        assertEquals(0, stats.validFiles)
        assertEquals(0L, stats.totalSizeBytes)
        assertTrue(stats.fileTypeCount.isEmpty())
    }
    
    @Test
    fun `ContextStats getTotalSizeFormatted should format size correctly`() {
        val statsBytes = ContextStats(0, 0, 0, 500L, emptyMap())
        val statsKB = ContextStats(0, 0, 0, 2048L, emptyMap())
        val statsMB = ContextStats(0, 0, 0, 2097152L, emptyMap())
        val statsGB = ContextStats(0, 0, 0, 2147483648L, emptyMap())
        
        assertEquals("500B", statsBytes.getTotalSizeFormatted())
        assertEquals("2KB", statsKB.getTotalSizeFormatted())
        assertEquals("2MB", statsMB.getTotalSizeFormatted())
        assertEquals("2GB", statsGB.getTotalSizeFormatted())
    }
    
    @Test
    fun `buildPromptWithContextFiles should handle absolute paths`() {
        val absolutePath = testFile1.absolutePath
        val contexts = listOf(
            ContextReference.FileReference(path = absolutePath)
        )
        val originalPrompt = "请分析这个文件"
        
        val result = ContextProcessor.buildPromptWithContextFiles(contexts, projectCwd, originalPrompt)
        
        assertTrue(result.contains("<context_files>"))
        assertTrue(result.contains("<file path=\"$absolutePath\">"))
        assertTrue(result.contains("class TestClass1"))
    }
    
    @Test
    fun `parseFrontMatter should be case sensitive for context types`() {
        val frontMatter = """
            ---
            contexts:
              - FILE:@docs/test.md
              - Web:https://example.com
            ---
        """.trimIndent()
        
        val result = ContextProcessor.parseFrontMatter(frontMatter)
        
        // 因为是大小写敏感的，FILE 和 Web 不会被识别
        assertEquals(0, result.size)
    }
    
    @Test
    fun `generateFrontMatter and parseFrontMatter should be symmetric`() {
        val originalContexts = listOf(
            ContextReference.FileReference(path = "docs/test.md"),
            ContextReference.WebReference(url = "https://example.com", title = "Example"),
            ContextReference.FolderReference(path = "src/main"),
            ContextReference.SymbolReference(
                name = "TestClass",
                type = com.claudecodeplus.ui.models.SymbolType.CLASS,
                file = "test.kt",
                line = 1
            ),
            ContextReference.ImageReference(path = "assets/logo.png", filename = "logo.png")
        )
        
        val frontMatter = ContextProcessor.generateFrontMatter(originalContexts)
        val parsedContexts = ContextProcessor.parseFrontMatter(frontMatter)
        
        assertEquals(originalContexts.size, parsedContexts.size)
        
        // 验证每个上下文都被正确解析
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
                is ContextReference.GitReference,
                is ContextReference.ProblemsReference,
                is ContextReference.TerminalReference,
                ContextReference.SelectionReference,
                ContextReference.WorkspaceReference -> {
                    // 这些类型在基础测试中不使用
                }
            }
        }
    }
}