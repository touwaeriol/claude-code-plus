package com.claudecodeplus.ui

import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.node.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*

/**
 * 测试 Markdown 渲染问题
 * 验证您提供的实际内容是否能正确解析
 */
class MarkdownRenderingTest {

    private lateinit var parser: Parser

    @BeforeEach
    fun setup() {
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
    fun testActualTodoWriteContent() {
        // 您提供的实际内容
        val markdown = """
## TodoWrite 工具演示总结

我刚才演示了 TodoWrite 工具的核心功能：

### 主要特性：
1. **三种任务状态**：
   - `pending` - 待处理的任务
   - `in_progress` - 正在进行中的任务（通常只有一个）
   - `completed` - 已完成的任务

2. **两种描述形式**：
   - `content` - 祈使句形式（如"演示 TodoWrite 工具"）
   - `activeForm` - 进行时形式（如"正在演示 TodoWrite 工具"）

3. **实时更新**：可以随时更新任务列表，添加新任务或修改现有任务状态

### 使用场景：
- 处理复杂多步骤任务时组织和跟踪进度
- 让用户清晰了解当前工作状态
- 确保不遗漏重要步骤

这个工具在你的 IDE 插件中会以可视化方式展示，用户可以直观地看到任务进展。
        """.trimIndent()

        val document = parser.parse(markdown)

        println("=== 解析您提供的实际内容 ===")
        println("原始内容长度: ${markdown.length}")
        println()

        // 分析节点树
        val nodeTypes = mutableMapOf<String, Int>()
        val headings = mutableListOf<Pair<Int, String>>()

        fun analyzeNode(node: Node) {
            val typeName = when (node) {
                is Heading -> {
                    val text = extractText(node)
                    headings.add(node.level to text)
                    "Heading(level=${node.level})"
                }
                is Text -> "Text"
                is Paragraph -> "Paragraph"
                is BulletList -> "BulletList"
                is OrderedList -> "OrderedList"
                is ListItem -> "ListItem"
                is Code -> "Code"
                is StrongEmphasis -> "StrongEmphasis"
                is Emphasis -> "Emphasis"
                is Document -> "Document"
                else -> node.javaClass.simpleName
            }

            nodeTypes[typeName] = nodeTypes.getOrDefault(typeName, 0) + 1

            var child = node.firstChild
            while (child != null) {
                analyzeNode(child)
                child = child.next
            }
        }

        analyzeNode(document)

        println("=== 节点统计 ===")
        nodeTypes.forEach { (type, count) ->
            println("$type: $count 个")
        }

        println()
        println("=== 找到的标题 ===")
        if (headings.isEmpty()) {
            println("❌ 没有找到任何标题节点！")
        } else {
            headings.forEach { (level, text) ->
                println("H$level: $text")
            }
        }

        // 断言
        assertTrue(headings.isNotEmpty(), "应该解析出标题节点")
        assertEquals(2, headings.count { it.first == 2 }, "应该有 2 个二级标题")
        assertEquals(2, headings.count { it.first == 3 }, "应该有 2 个三级标题")
        assertTrue(headings.any { it.second.contains("TodoWrite") }, "应该包含 TodoWrite 标题")
        assertTrue(nodeTypes.containsKey("OrderedList"), "应该包含有序列表")
        assertTrue(nodeTypes.containsKey("BulletList"), "应该包含无序列表")
        assertTrue(nodeTypes["StrongEmphasis"]!! > 0, "应该包含粗体文字")
        assertTrue(nodeTypes["Code"]!! > 0, "应该包含行内代码")

        println()
        println("✅ 测试通过：Markdown 内容能够正确解析")
    }

    @Test
    fun testSimplifiedContent() {
        // 测试简化版本
        val markdown = "## TodoWrite 工具演示总结"

        val document = parser.parse(markdown)

        println("=== 测试简单标题 ===")
        println("输入: $markdown")

        // 打印节点树
        printNodeTree(document, 0)

        var hasHeading = false
        fun findHeading(node: Node) {
            if (node is Heading) {
                hasHeading = true
                assertEquals(2, node.level, "应该是二级标题")
            }
            var child = node.firstChild
            while (child != null) {
                findHeading(child)
                child = child.next
            }
        }

        findHeading(document)

        assertTrue(hasHeading, "简单的 ## 标题应该被正确解析")
    }

    @Test
    fun testProblematicContent() {
        // 测试可能有问题的内容（例如前面有空格）
        val markdownWithSpace = " ## TodoWrite 工具演示总结"  // 前面有空格
        val markdownNoSpace = "##TodoWrite 工具演示总结"      // 没有空格

        val doc1 = parser.parse(markdownWithSpace)
        val doc2 = parser.parse(markdownNoSpace)

        println("=== 测试问题内容 ===")

        println("1. 前面有空格的情况:")
        printNodeTree(doc1, 0)

        println("2. ## 后没有空格的情况:")
        printNodeTree(doc2, 0)

        // 验证
        assertFalse(hasHeadingNode(doc1), "前面有空格的 ## 不会被解析为标题")
        assertFalse(hasHeadingNode(doc2), "## 后没有空格也不会被解析为标题")
    }

    private fun hasHeadingNode(node: Node): Boolean {
        if (node is Heading) return true
        var child = node.firstChild
        while (child != null) {
            if (hasHeadingNode(child)) return true
            child = child.next
        }
        return false
    }

    private fun extractText(node: Node): String {
        val sb = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            when (child) {
                is Text -> sb.append(child.literal)
                else -> sb.append(extractText(child))
            }
            child = child.next
        }
        return sb.toString()
    }

    private fun printNodeTree(node: Node, depth: Int) {
        val indent = "  ".repeat(depth)
        val nodeInfo = when (node) {
            is Document -> "Document"
            is Heading -> "Heading(level=${node.level})"
            is Paragraph -> "Paragraph"
            is Text -> "Text('${node.literal}')"
            is StrongEmphasis -> "StrongEmphasis"
            is Emphasis -> "Emphasis"
            is BulletList -> "BulletList"
            is OrderedList -> "OrderedList"
            is ListItem -> "ListItem"
            is Code -> "Code('${node.literal}')"
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