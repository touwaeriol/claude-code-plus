package com.claudecodeplus.ui.jewel.components.markdown

import com.claudecodeplus.core.logging.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.*
import org.commonmark.node.*

/**
 * 高级 Markdown 渲染器
 * 使用专门的组件渲染每种 Markdown 元素
 */
@Composable
fun MarkdownRenderer(
    markdown: String,
    onLinkClick: (String) -> Unit = {},
    onCodeAction: (code: String, language: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    // logD("[MarkdownRenderer] 🎯 开始渲染 Markdown，长度: ${markdown.length}")
    // logD("[MarkdownRenderer] 📝 Markdown 内容预览: ${markdown.take(100)}")
    // 打印前20个字符的Unicode码，检查是否有特殊字符
    // logD("[MarkdownRenderer] 🔬 前20字符的Unicode码: ${markdown.take(20).map { it.code.toString(16) }.joinToString(" ")}")

    val parser = remember { MarkdownParser.getInstance() }
    val document = remember(markdown) {
        val doc = parser.parse(markdown)
    //         logD("[MarkdownRenderer] ✅ Markdown 解析完成")
        doc
    }

    Column(modifier = modifier.fillMaxWidth()) {
        RenderNode(document, onLinkClick, onCodeAction)
    }
}

/**
 * 递归渲染节点
 */
@Composable
private fun RenderNode(
    node: Node,
    onLinkClick: (String) -> Unit,
    onCodeAction: (code: String, language: String) -> Unit
) {
    // logD("[MarkdownRenderer] 🔍 渲染节点: ${node.javaClass.simpleName}")

    when (node) {
        is Document -> {
            // Document 节点的子节点是顶级块元素，需要间距
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RenderChildren(node, onLinkClick, onCodeAction)
            }
        }

        is Heading -> {
            MarkdownHeading(
                level = node.level,
                content = {
                    RenderChildren(node, onLinkClick, onCodeAction)
                }
            )
        }

        is Paragraph -> {
            MarkdownParagraph(
                content = {
                    RenderChildren(node, onLinkClick, onCodeAction)
                }
            )
        }

        is BlockQuote -> {
            MarkdownBlockQuote(
                content = {
                    RenderChildren(node, onLinkClick, onCodeAction)
                }
            )
        }

        is FencedCodeBlock -> {
            val language = node.info?.takeIf { it.isNotBlank() }
            MarkdownCodeBlock(
                code = node.literal,
                language = language
            )
        }

        is IndentedCodeBlock -> {
            MarkdownCodeBlock(code = node.literal)
        }

        is Code -> {
            MarkdownInlineCode(code = node.literal)
        }

        is BulletList -> {
            val items = mutableListOf<@Composable () -> Unit>()
            var child = node.firstChild
            while (child != null) {
                if (child is ListItem) {
                    val currentChild = child
                    items.add {
                        // 如果 ListItem 有多个子节点，需要用 Column 布局
                        if (currentChild.firstChild != null && currentChild.firstChild.next != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                RenderChildren(currentChild, onLinkClick, onCodeAction)
                            }
                        } else {
                            RenderChildren(currentChild, onLinkClick, onCodeAction)
                        }
                    }
                }
                child = child.next
            }
            MarkdownList(items = items, ordered = false)
        }

        is OrderedList -> {
            val items = mutableListOf<@Composable () -> Unit>()
            var child = node.firstChild
            while (child != null) {
                if (child is ListItem) {
                    val currentChild = child
                    items.add {
                        // 如果 ListItem 有多个子节点，需要用 Column 布局
                        if (currentChild.firstChild != null && currentChild.firstChild.next != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                RenderChildren(currentChild, onLinkClick, onCodeAction)
                            }
                        } else {
                            RenderChildren(currentChild, onLinkClick, onCodeAction)
                        }
                    }
                }
                child = child.next
            }
            MarkdownList(items = items, ordered = true)
        }

        is ListItem -> {
            // Check if this is a task list item (CommonMark doesn't have built-in task list support)
            val isTaskItem = node.firstChild?.let { child ->
                child is Paragraph && child.firstChild?.let { text ->
                    text is Text && (text.literal.startsWith("[ ] ") || text.literal.startsWith("[x] "))
                } ?: false
            } ?: false

            if (isTaskItem) {
                val firstText = (node.firstChild as? Paragraph)?.firstChild as? Text
                val checked = firstText?.literal?.startsWith("[x] ") ?: false
                val content = firstText?.literal?.drop(4) ?: ""
                MarkdownTaskListItem(
                    checked = checked,
                    content = {
                        MarkdownText(text = content)
                    }
                )
            } else {
                // Regular list item is handled by the parent list
                RenderChildren(node, onLinkClick, onCodeAction)
            }
        }

        is ThematicBreak -> {
            MarkdownHorizontalRule()
        }

        is Link -> {
            val text = extractText(node)
            MarkdownLink(
                text = text,
                url = node.destination,
                onClick = onLinkClick
            )
        }

        is Image -> {
            MarkdownImage(
                alt = extractText(node),
                url = node.destination
            )
        }

        is TableBlock -> {
            RenderTable(node, onLinkClick, onCodeAction)
        }

        is Text -> {
            val parent = node.parent
            MarkdownText(
                text = node.literal,
                bold = parent is StrongEmphasis,
                italic = parent is Emphasis,
                strikethrough = parent is Strikethrough
            )
        }

        is StrongEmphasis -> {
            MarkdownText(text = extractText(node), bold = true)
        }

        is Emphasis -> {
            MarkdownText(text = extractText(node), italic = true)
        }

        is Strikethrough -> {
            MarkdownText(text = extractText(node), strikethrough = true)
        }

        is HardLineBreak, is SoftLineBreak -> {
            // 换行符在布局中自然处理
        }

        else -> {
            // 对于未知节点，尝试渲染其子节点
            RenderChildren(node, onLinkClick, onCodeAction)
        }
    }
}

/**
 * 渲染所有子节点
 */
@Composable
private fun RenderChildren(
    node: Node,
    onLinkClick: (String) -> Unit,
    onCodeAction: (code: String, language: String) -> Unit
) {
    var child = node.firstChild
    while (child != null) {
        RenderNode(child, onLinkClick, onCodeAction)
        child = child.next
    }
}

/**
 * 渲染表格
 */
@Composable
private fun RenderTable(
    tableBlock: TableBlock,
    onLinkClick: (String) -> Unit,
    onCodeAction: (code: String, language: String) -> Unit
) {
    val headers = mutableListOf<String>()
    val rows = mutableListOf<List<String>>()

    var child = tableBlock.firstChild
    while (child != null) {
        when (child) {
            is TableHead -> {
                var row = child.firstChild
                while (row != null) {
                    if (row is TableRow) {
                        var cell = row.firstChild
                        while (cell != null) {
                            if (cell is TableCell) {
                                headers.add(extractText(cell))
                            }
                            cell = cell.next
                        }
                    }
                    row = row.next
                }
            }
            is TableBody -> {
                var row = child.firstChild
                while (row != null) {
                    if (row is TableRow) {
                        val rowData = mutableListOf<String>()
                        var cell = row.firstChild
                        while (cell != null) {
                            if (cell is TableCell) {
                                rowData.add(extractText(cell))
                            }
                            cell = cell.next
                        }
                        if (rowData.isNotEmpty()) {
                            rows.add(rowData)
                        }
                    }
                    row = row.next
                }
            }
        }
        child = child.next
    }

    if (headers.isNotEmpty()) {
        MarkdownTable(headers = headers, rows = rows)
    }
}

/**
 * 提取节点中的纯文本
 */
private fun extractText(node: Node): String {
    val builder = StringBuilder()
    extractTextRecursive(node, builder)
    return builder.toString()
}

/**
 * 递归提取文本
 */
private fun extractTextRecursive(node: Node, builder: StringBuilder) {
    if (node is Text) {
        builder.append(node.literal)
    }

    var child = node.firstChild
    while (child != null) {
        extractTextRecursive(child, builder)
        child = child.next
    }
}
