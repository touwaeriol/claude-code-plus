package com.claudecodeplus.ui

import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.node.*
import org.commonmark.ext.gfm.tables.TableBlock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*

/**
 * Markdown解析器单元测试
 * 验证CommonMark库对各种Markdown元素的解析能力
 */
class MarkdownParserTest {

    private lateinit var parser: Parser

    @BeforeEach
    fun setup() {
        // 初始化解析器，使用与项目相同的扩展配置
        parser = Parser.builder()
            .extensions(listOf(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create(),
                TaskListItemsExtension.create(),
                HeadingAnchorExtension.create()
            ))
            .build()
    }

    @Test
    fun testBasicTextParsing() {
        val markdown = "这是一段普通文本"
        val document = parser.parse(markdown)

        assertNotNull(document)
        assertTrue(document.firstChild is Paragraph)

        val paragraph = document.firstChild as Paragraph
        val text = paragraph.firstChild as Text
        assertEquals("这是一段普通文本", text.literal)

        println("✅ 基础文本解析成功")
    }

    @Test
    fun testHeadingsParsing() {
        val markdown = """
            # 一级标题
            ## 二级标题
            ### 三级标题
            #### 四级标题
            ##### 五级标题
            ###### 六级标题
        """.trimIndent()

        val document = parser.parse(markdown)
        val headings = mutableListOf<Heading>()

        var node = document.firstChild
        while (node != null) {
            if (node is Heading) {
                headings.add(node)
            }
            node = node.next
        }

        assertEquals(6, headings.size, "应该解析出6个标题")
        assertEquals(1, headings[0].level)
        assertEquals(2, headings[1].level)
        assertEquals(3, headings[2].level)
        assertEquals(4, headings[3].level)
        assertEquals(5, headings[4].level)
        assertEquals(6, headings[5].level)

        println("✅ 标题解析成功，共解析出${headings.size}个标题")
    }

    @Test
    fun testEmphasisParsing() {
        val markdown = """
            这是**粗体文字**
            这是*斜体文字*
            这是***粗斜体文字***
            这是~~删除线文字~~
        """.trimIndent()

        val document = parser.parse(markdown)
        var strongCount = 0
        var emphasisCount = 0

        fun visitNode(node: Node) {
            when (node) {
                is StrongEmphasis -> strongCount++
                is Emphasis -> emphasisCount++
            }
            var child = node.firstChild
            while (child != null) {
                visitNode(child)
                child = child.next
            }
        }

        visitNode(document)

        assertTrue(strongCount > 0, "应该包含粗体文字")
        assertTrue(emphasisCount > 0, "应该包含斜体文字")

        println("✅ 文字强调解析成功：粗体=$strongCount, 斜体=$emphasisCount")
    }

    @Test
    fun testListParsing() {
        val markdown = """
            无序列表：
            - 项目1
            - 项目2
              - 子项目2.1
              - 子项目2.2
            - 项目3

            有序列表：
            1. 第一项
            2. 第二项
            3. 第三项
        """.trimIndent()

        val document = parser.parse(markdown)
        var bulletListCount = 0
        var orderedListCount = 0
        var listItemCount = 0

        fun visitNode(node: Node) {
            when (node) {
                is BulletList -> bulletListCount++
                is OrderedList -> orderedListCount++
                is ListItem -> listItemCount++
            }
            var child = node.firstChild
            while (child != null) {
                visitNode(child)
                child = child.next
            }
        }

        visitNode(document)

        assertTrue(bulletListCount > 0, "应该包含无序列表")
        assertTrue(orderedListCount > 0, "应该包含有序列表")
        assertTrue(listItemCount >= 8, "应该包含至少8个列表项")

        println("✅ 列表解析成功：无序列表=$bulletListCount, 有序列表=$orderedListCount, 列表项=$listItemCount")
    }

    @Test
    fun testCodeBlockParsing() {
        val markdown = """
            ```javascript
            // 用户请求：实现一个完整的用户认证系统
            todos: [
              { content: "设计数据库表结构", activeForm: "正在设计数据库表结构", status: "pending" },
              { content: "实现注册接口", activeForm: "正在实现注册接口", status: "pending" }
            ]
            ```

            这是`内联代码`的示例
        """.trimIndent()

        val document = parser.parse(markdown)
        var fencedCodeBlock: FencedCodeBlock? = null
        var inlineCodeCount = 0

        fun visitNode(node: Node) {
            when (node) {
                is FencedCodeBlock -> fencedCodeBlock = node
                is Code -> inlineCodeCount++
            }
            var child = node.firstChild
            while (child != null) {
                visitNode(child)
                child = child.next
            }
        }

        visitNode(document)

        assertNotNull(fencedCodeBlock, "应该包含代码块")
        assertEquals("javascript", fencedCodeBlock?.info, "代码块应该标记为javascript")
        assertTrue(fencedCodeBlock?.literal?.contains("todos:") == true, "代码块应该包含内容")
        assertTrue(inlineCodeCount > 0, "应该包含内联代码")

        println("✅ 代码块解析成功：语言=${fencedCodeBlock?.info}, 内联代码数=$inlineCodeCount")
    }

    @Test
    fun testLinkParsing() {
        val markdown = """
            这是一个[链接](https://example.com)
            这是自动链接：https://example.com
            这是另一个[带标题的链接](https://example.com "标题")
        """.trimIndent()

        val document = parser.parse(markdown)
        var linkCount = 0
        val linkUrls = mutableListOf<String>()

        fun visitNode(node: Node) {
            when (node) {
                is Link -> {
                    linkCount++
                    linkUrls.add(node.destination)
                }
            }
            var child = node.firstChild
            while (child != null) {
                visitNode(child)
                child = child.next
            }
        }

        visitNode(document)

        assertTrue(linkCount >= 2, "应该包含至少2个链接")
        assertTrue(linkUrls.any { it.contains("example.com") }, "链接应该包含example.com")

        println("✅ 链接解析成功：链接数=$linkCount, URLs=$linkUrls")
    }

    @Test
    fun testBlockQuoteParsing() {
        val markdown = """
            > 这是一个引用块
            > 可以有多行
            >
            > > 嵌套引用
        """.trimIndent()

        val document = parser.parse(markdown)
        var blockQuoteCount = 0

        fun visitNode(node: Node) {
            if (node is BlockQuote) {
                blockQuoteCount++
            }
            var child = node.firstChild
            while (child != null) {
                visitNode(child)
                child = child.next
            }
        }

        visitNode(document)

        assertTrue(blockQuoteCount >= 1, "应该包含引用块")

        println("✅ 引用块解析成功：引用块数=$blockQuoteCount")
    }

    @Test
    fun testTableParsing() {
        val markdown = """
            | 列1 | 列2 | 列3 |
            |-----|-----|-----|
            | 数据1 | 数据2 | 数据3 |
            | 数据4 | 数据5 | 数据6 |
        """.trimIndent()

        val document = parser.parse(markdown)
        var tableBlock: TableBlock? = null

        fun visitNode(node: Node) {
            if (node is TableBlock) {
                tableBlock = node
            }
            var child = node.firstChild
            while (child != null) {
                visitNode(child)
                child = child.next
            }
        }

        visitNode(document)

        assertNotNull(tableBlock, "应该包含表格")

        println("✅ 表格解析成功：找到表格=${tableBlock != null}")
    }

    @Test
    fun testComplexTodoWriteExample() {
        // 测试TodoWrite文档中的完整示例
        val markdown = """
            ## TodoWrite 工具使用说明

            ### 核心概念：
            1. **三种任务状态**：
               - `pending`：待处理任务
               - `in_progress`：正在进行（同时只能有一个）
               - `completed`：已完成

            2. **两种描述形式**：
               - `content`：命令式描述（如"实现登录功能"）
               - `activeForm`：进行时描述（如"正在实现登录功能"）

            ### 使用场景示例：

            **场景1：处理复杂的多步骤任务**
            ```javascript
            // 用户请求：实现一个完整的用户认证系统
            todos: [
              { content: "设计数据库表结构", activeForm: "正在设计数据库表结构", status: "pending" },
              { content: "实现注册接口", activeForm: "正在实现注册接口", status: "pending" }
            ]
            ```
        """.trimIndent()

        val document = parser.parse(markdown)

        // 统计各种元素
        var headingCount = 0
        var listCount = 0
        var codeBlockCount = 0
        var emphasisCount = 0

        fun visitNode(node: Node) {
            when (node) {
                is Heading -> headingCount++
                is BulletList, is OrderedList -> listCount++
                is FencedCodeBlock, is IndentedCodeBlock -> codeBlockCount++
                is StrongEmphasis, is Emphasis -> emphasisCount++
            }
            var child = node.firstChild
            while (child != null) {
                visitNode(child)
                child = child.next
            }
        }

        visitNode(document)

        assertTrue(headingCount >= 3, "应该包含至少3个标题")
        assertTrue(listCount >= 2, "应该包含至少2个列表")
        assertTrue(codeBlockCount >= 1, "应该包含至少1个代码块")
        assertTrue(emphasisCount >= 4, "应该包含至少4个强调文字")

        println("✅ TodoWrite示例解析成功：")
        println("  - 标题数: $headingCount")
        println("  - 列表数: $listCount")
        println("  - 代码块数: $codeBlockCount")
        println("  - 强调文字数: $emphasisCount")
    }

    @Test
    fun testParseAndPrintStructure() {
        // 解析并打印文档结构，用于调试
        val markdown = """
            ### 标题

            这是**粗体**和*斜体*文字。

            - 列表项1
            - 列表项2

            ```kotlin
            fun test() {
                println("Hello")
            }
            ```
        """.trimIndent()

        val document = parser.parse(markdown)

        println("\n=== 文档结构树 ===")
        printNodeTree(document, 0)

        assertNotNull(document, "文档应该被成功解析")
    }

    private fun printNodeTree(node: Node, depth: Int) {
        val indent = "  ".repeat(depth)
        val nodeInfo = when (node) {
            is Document -> "Document"
            is Heading -> "Heading(level=${(node as Heading).level})"
            is Paragraph -> "Paragraph"
            is Text -> "Text('${(node as Text).literal}')"
            is StrongEmphasis -> "StrongEmphasis"
            is Emphasis -> "Emphasis"
            is BulletList -> "BulletList"
            is OrderedList -> "OrderedList"
            is ListItem -> "ListItem"
            is FencedCodeBlock -> "FencedCodeBlock(lang=${(node as FencedCodeBlock).info})"
            is Code -> "Code"
            is Link -> "Link(url=${(node as Link).destination})"
            is BlockQuote -> "BlockQuote"
            is TableBlock -> "TableBlock"
            else -> node.javaClass.simpleName
        }

        println("$indent- $nodeInfo")

        var child = node.firstChild
        while (child != null) {
            printNodeTree(child, depth + 1)
            child = child.next
        }
    }
}