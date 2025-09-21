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
 * 专门测试 Markdown 解析器对实际内容的解析能力
 * 验证用户提供的TodoWrite工具演示内容是否能正确解析
 */
class MarkdownParserValidationTest {

    private lateinit var parser: Parser

    @BeforeEach
    fun setup() {
        // 使用与实际应用相同的解析器配置
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
    fun testCompleteMarkdownContent() {
        // 用户提供的完整Markdown内容
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

        println("=== 测试完整Markdown内容解析 ===")
        println("内容长度: ${markdown.length} 字符")
        println("内容前100字符: ${markdown.take(100)}")
        println()

        // 解析Markdown
        val document = parser.parse(markdown)

        // 收集解析结果统计
        val stats = NodeStats()
        analyzeNode(document, stats, 0)

        // 打印解析统计
        println("=== 解析统计 ===")
        println("Document节点: ${stats.documents} 个")
        println("Heading节点: ${stats.headings} 个")
        println("  - H2标题: ${stats.h2Count} 个")
        println("  - H3标题: ${stats.h3Count} 个")
        println("Paragraph节点: ${stats.paragraphs} 个")
        println("BulletList节点: ${stats.bulletLists} 个")
        println("OrderedList节点: ${stats.orderedLists} 个")
        println("ListItem节点: ${stats.listItems} 个")
        println("StrongEmphasis节点: ${stats.strongEmphasis} 个")
        println("Code节点: ${stats.codes} 个")
        println("Text节点: ${stats.texts} 个")
        println()

        // 打印标题内容
        println("=== 解析出的标题 ===")
        stats.headingTexts.forEach { (level, text) ->
            println("H$level: $text")
        }
        println()

        // 打印完整的节点树
        println("=== 完整节点树 ===")
        printNodeTree(document, 0)
        println()

        // 验证解析结果
        println("=== 验证结果 ===")

        // 1. 验证标题解析
        assertTrue(stats.headings > 0, "❌ 没有解析出任何标题节点")
        assertEquals(1, stats.h2Count, "❌ H2标题数量不正确，期望1个，实际${stats.h2Count}个")
        assertEquals(2, stats.h3Count, "❌ H3标题数量不正确，期望2个，实际${stats.h3Count}个")

        // 2. 验证标题文本
        assertTrue(stats.headingTexts.any { it.second.contains("TodoWrite 工具演示总结") },
            "❌ 未找到'TodoWrite 工具演示总结'标题")
        assertTrue(stats.headingTexts.any { it.second.contains("主要特性") },
            "❌ 未找到'主要特性'标题")
        assertTrue(stats.headingTexts.any { it.second.contains("使用场景") },
            "❌ 未找到'使用场景'标题")

        // 3. 验证列表解析
        assertTrue(stats.orderedLists > 0, "❌ 没有解析出有序列表")
        assertTrue(stats.bulletLists > 0, "❌ 没有解析出无序列表")
        assertTrue(stats.listItems >= 6, "❌ 列表项数量不足，期望至少6个，实际${stats.listItems}个")

        // 4. 验证强调和代码
        assertTrue(stats.strongEmphasis >= 3, "❌ 粗体文本数量不足，期望至少3个，实际${stats.strongEmphasis}个")
        assertTrue(stats.codes >= 5, "❌ 行内代码数量不足，期望至少5个，实际${stats.codes}个")

        // 打印最终结论
        if (stats.headings == 0 || stats.h2Count == 0 || stats.h3Count == 0) {
            println("❌ 解析失败：Markdown语法未被正确识别")
            println("可能的原因：")
            println("1. 内容编码问题")
            println("2. 换行符格式问题（CRLF vs LF）")
            println("3. 内容被HTML转义")
            println("4. 解析器配置问题")
        } else {
            println("✅ 解析成功：Markdown语法被正确识别")
            println("所有Markdown元素都被正确解析为对应的AST节点")
        }
    }

    @Test
    fun testIndividualMarkdownElements() {
        println("=== 测试单个Markdown元素 ===")

        // 测试H2标题
        val h2 = "## TodoWrite 工具演示总结"
        val h2Doc = parser.parse(h2)
        val h2Stats = NodeStats()
        analyzeNode(h2Doc, h2Stats, 0)
        println("H2标题测试: ${if (h2Stats.h2Count == 1) "✅ 通过" else "❌ 失败"}")

        // 测试H3标题
        val h3 = "### 主要特性："
        val h3Doc = parser.parse(h3)
        val h3Stats = NodeStats()
        analyzeNode(h3Doc, h3Stats, 0)
        println("H3标题测试: ${if (h3Stats.h3Count == 1) "✅ 通过" else "❌ 失败"}")

        // 测试粗体
        val bold = "**三种任务状态**"
        val boldDoc = parser.parse(bold)
        val boldStats = NodeStats()
        analyzeNode(boldDoc, boldStats, 0)
        println("粗体测试: ${if (boldStats.strongEmphasis == 1) "✅ 通过" else "❌ 失败"}")

        // 测试行内代码
        val code = "`pending`"
        val codeDoc = parser.parse(code)
        val codeStats = NodeStats()
        analyzeNode(codeDoc, codeStats, 0)
        println("行内代码测试: ${if (codeStats.codes == 1) "✅ 通过" else "❌ 失败"}")

        // 测试有序列表
        val orderedList = """
1. 第一项
2. 第二项
        """.trimIndent()
        val olDoc = parser.parse(orderedList)
        val olStats = NodeStats()
        analyzeNode(olDoc, olStats, 0)
        println("有序列表测试: ${if (olStats.orderedLists == 1) "✅ 通过" else "❌ 失败"}")

        // 测试无序列表
        val bulletList = """
- 项目一
- 项目二
        """.trimIndent()
        val blDoc = parser.parse(bulletList)
        val blStats = NodeStats()
        analyzeNode(blDoc, blStats, 0)
        println("无序列表测试: ${if (blStats.bulletLists == 1) "✅ 通过" else "❌ 失败"}")
    }

    // 统计类
    private class NodeStats {
        var documents = 0
        var headings = 0
        var h2Count = 0
        var h3Count = 0
        var paragraphs = 0
        var bulletLists = 0
        var orderedLists = 0
        var listItems = 0
        var strongEmphasis = 0
        var codes = 0
        var texts = 0
        val headingTexts = mutableListOf<Pair<Int, String>>()
    }

    // 分析节点并收集统计信息
    private fun analyzeNode(node: Node, stats: NodeStats, depth: Int) {
        when (node) {
            is Document -> stats.documents++
            is Heading -> {
                stats.headings++
                when (node.level) {
                    2 -> stats.h2Count++
                    3 -> stats.h3Count++
                }
                val text = extractText(node)
                stats.headingTexts.add(node.level to text)
            }
            is Paragraph -> stats.paragraphs++
            is BulletList -> stats.bulletLists++
            is OrderedList -> stats.orderedLists++
            is ListItem -> stats.listItems++
            is StrongEmphasis -> stats.strongEmphasis++
            is Code -> stats.codes++
            is Text -> stats.texts++
        }

        var child = node.firstChild
        while (child != null) {
            analyzeNode(child, stats, depth + 1)
            child = child.next
        }
    }

    // 提取节点文本
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

    // 打印节点树
    private fun printNodeTree(node: Node, depth: Int) {
        val indent = "  ".repeat(depth)
        val nodeInfo = when (node) {
            is Document -> "Document"
            is Heading -> "Heading(H${node.level}): \"${extractText(node).take(30)}\""
            is Paragraph -> "Paragraph"
            is Text -> "Text: \"${node.literal.take(30)}\""
            is StrongEmphasis -> "StrongEmphasis: \"${extractText(node)}\""
            is Code -> "Code: \"${node.literal}\""
            is BulletList -> "BulletList"
            is OrderedList -> "OrderedList"
            is ListItem -> "ListItem"
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