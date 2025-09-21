package com.claudecodeplus.ui.jewel.components

import com.claudecodeplus.core.logging.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.ui.graphics.Color

/**
 * 简单的 Markdown 渲染器 - 使用 CommonMark 解析和 Jewel Text 渲染
 */
@Composable
fun SimpleMarkdownRenderer(
    markdown: String,
    onCodeAction: (code: String, language: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val content = remember(markdown) {
        val parsed = parseMarkdown(markdown)
        // 调试日志：检查解析结果
    //         logD("[SimpleMarkdownRenderer] 解析 Markdown 完成")
    //         logD("[SimpleMarkdownRenderer] - 原始文本长度: ${markdown.length}")
    //         logD("[SimpleMarkdownRenderer] - AnnotatedString 长度: ${parsed.length}")
    //         logD("[SimpleMarkdownRenderer] - 包含样式: ${parsed.spanStyles.size} 个")
        parsed.spanStyles.forEach { span ->
    //             logD("[SimpleMarkdownRenderer] 样式范围 [${span.start}-${span.end}]: ${span.item}")
        }
        parsed
    }

    SelectionContainer {
        // 使用 Jewel 的 Text 组件替代 BasicText
        Text(
            text = content,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 13.sp,
                lineHeight = 20.sp
            ),
            modifier = modifier.fillMaxWidth()
        )
    }
}

/**
 * 解析 Markdown 并构建 AnnotatedString
 */
private fun parseMarkdown(markdown: String): AnnotatedString {
    val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create()
    )

    val parser = Parser.builder()
        .extensions(extensions)
        .build()

    val document = parser.parse(markdown)

    return buildAnnotatedString {
        processNode(document, this)
    }
}

/**
 * 递归处理 CommonMark 节点
 */
private fun processNode(node: Node, builder: AnnotatedString.Builder) {
    when (node) {
        is Text -> {
            builder.append(node.literal)
        }
        is StrongEmphasis -> {
            builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                processChildren(node, this)
            }
        }
        is Emphasis -> {
            builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                processChildren(node, this)
            }
        }
        is Code -> {
            builder.withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x1A000000)  // 10% black background
                )
            ) {
                append(node.literal)
            }
        }
        is Heading -> {
            val fontSize = when (node.level) {
                1 -> 20.sp
                2 -> 18.sp
                3 -> 16.sp
                else -> 14.sp
            }
            builder.withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize
                )
            ) {
                processChildren(node, this)
            }
            builder.append("\n\n")
        }
        is Paragraph -> {
            processChildren(node, builder)
            builder.append("\n\n")
        }
        is BulletList, is OrderedList -> {
            processListItems(node, builder)
            builder.append("\n")
        }
        is ListItem -> {
            builder.append("  • ")
            processChildren(node, builder)
            builder.append("\n")
        }
        is BlockQuote -> {
            builder.append("│ ")
            processChildren(node, builder)
        }
        is FencedCodeBlock -> {
            builder.withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    background = Color(0x0D000000)  // 5% black background
                )
            ) {
                append(node.literal)
            }
            builder.append("\n")
        }
        is IndentedCodeBlock -> {
            builder.withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    background = Color(0x0D000000)  // 5% black background
                )
            ) {
                append(node.literal)
            }
            builder.append("\n")
        }
        is Link -> {
            builder.withStyle(
                SpanStyle(
                    color = Color(0xFF2196F3),  // Blue color for links
                    textDecoration = TextDecoration.Underline
                )
            ) {
                processChildren(node, this)
            }
        }
        is HardLineBreak, is SoftLineBreak -> {
            builder.append("\n")
        }
        is ThematicBreak -> {
            builder.append("─────────────────────\n")
        }
        else -> {
            // 处理其他未知节点的子节点
            processChildren(node, builder)
        }
    }
}

/**
 * 处理节点的所有子节点
 */
private fun processChildren(node: Node, builder: AnnotatedString.Builder) {
    var child = node.firstChild
    while (child != null) {
        processNode(child, builder)
        child = child.next
    }
}

/**
 * 处理列表项
 */
private fun processListItems(node: Node, builder: AnnotatedString.Builder) {
    var child = node.firstChild
    var index = 1
    while (child != null) {
        if (child is ListItem) {
            val marker = if (node is OrderedList) "$index. " else "• "
            builder.append("  $marker")
            processChildren(child, builder)
            if (child.next != null) {
                builder.append("\n")
            }
            index++
        }
        child = child.next
    }
}
