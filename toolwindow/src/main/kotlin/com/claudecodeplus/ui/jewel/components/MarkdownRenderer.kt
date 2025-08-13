package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import androidx.compose.material.LocalContentColor
import org.commonmark.parser.Parser
import org.commonmark.node.*

/**
 * Markdown 渲染器组件
 * 使用 CommonMark 库进行解析
 */
@Composable
fun MarkdownRenderer(
    markdown: String,
    onCodeAction: (code: String, language: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val parser = remember { Parser.builder().build() }
    val document = remember(markdown) { parser.parse(markdown) }
    val blocks = remember(document) { parseCommonMarkDocument(document) }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    SelectionContainer {
                        Text(
                            text = block.content,
                            style = JewelTheme.defaultTextStyle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                is MarkdownBlock.Header -> {
                    SelectionContainer {
                        Text(
                            text = block.content,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = when (block.level) {
                                    1 -> 24.sp
                                    2 -> 20.sp
                                    3 -> 18.sp
                                    else -> 16.sp
                                },
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                
                is MarkdownBlock.Code -> {
                    CodeBlock(
                        code = block.content,
                        language = block.language,
                        onCopy = { /* Handled internally */ },
                        onInsert = { onCodeAction(block.content, block.language) }
                    )
                }
                
                is MarkdownBlock.ListBlock -> {
                    Column(
                        modifier = Modifier.padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for ((index, item) in block.items.withIndex()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = if (block.ordered) "${index + 1}." else "•",
                                    style = JewelTheme.defaultTextStyle
                                )
                                SelectionContainer {
                                    Text(
                                        text = item,
                                        style = JewelTheme.defaultTextStyle,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF5C5C5C))
                        )
                        SelectionContainer {
                            Text(
                                text = block.content,
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = LocalContentColor.current.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 代码块组件
 */
@Composable
fun CodeBlock(
    code: String,
    language: String,
    onCopy: () -> Unit,
    onInsert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
    ) {
        // 头部工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 语言标识
            Text(
                text = language.ifEmpty { "text" },
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                )
            )
            
            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DefaultButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        onCopy()
                        copied = true
                    }
                ) {
                    Text(
                        text = if (copied) "✓ 已复制" else "复制",
                        fontSize = 12.sp
                    )
                }
                
                DefaultButton(
                    onClick = { onInsert() }
                ) {
                    Text(
                        text = "插入",
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        // 代码内容
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = JewelTheme.globalColors.text.normal
                    )
                )
            }
        }
    }
    
    // 重置复制状态
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }
}

/**
 * 简单的 Markdown 解析
 */
private sealed class MarkdownBlock {
    data class Paragraph(val content: AnnotatedString) : MarkdownBlock()
    data class Header(val level: Int, val content: AnnotatedString) : MarkdownBlock()
    data class Code(val content: String, val language: String) : MarkdownBlock()
    data class ListBlock(val items: List<AnnotatedString>, val ordered: Boolean) : MarkdownBlock()
    data class Blockquote(val content: AnnotatedString) : MarkdownBlock()
}

/**
 * 使用 CommonMark 解析 Markdown 文档
 */
private fun parseCommonMarkDocument(document: Node): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    
    var child = document.firstChild
    while (child != null) {
        when (child) {
            is Paragraph -> {
                val content = parseInlineContent(child)
                blocks.add(MarkdownBlock.Paragraph(content))
            }
            
            is Heading -> {
                val content = parseInlineContent(child)
                blocks.add(MarkdownBlock.Header(child.level, content))
            }
            
            is IndentedCodeBlock -> {
                blocks.add(MarkdownBlock.Code(child.literal ?: "", ""))
            }
            
            is FencedCodeBlock -> {
                blocks.add(MarkdownBlock.Code(child.literal ?: "", child.info ?: ""))
            }
            
            is BlockQuote -> {
                val content = parseInlineContent(child)
                blocks.add(MarkdownBlock.Blockquote(content))
            }
            
            is BulletList -> {
                val items = mutableListOf<AnnotatedString>()
                var listItem = child.firstChild
                while (listItem != null) {
                    if (listItem is ListItem) {
                        val content = parseInlineContent(listItem)
                        items.add(content)
                    }
                    listItem = listItem.next
                }
                blocks.add(MarkdownBlock.ListBlock(items, ordered = false))
            }
            
            is OrderedList -> {
                val items = mutableListOf<AnnotatedString>()
                var listItem = child.firstChild
                while (listItem != null) {
                    if (listItem is ListItem) {
                        val content = parseInlineContent(listItem)
                        items.add(content)
                    }
                    listItem = listItem.next
                }
                blocks.add(MarkdownBlock.ListBlock(items, ordered = true))
            }
        }
        child = child.next
    }
    
    return blocks
}

/**
 * 解析内联内容为 AnnotatedString
 */
private fun parseInlineContent(node: Node): AnnotatedString {
    return buildAnnotatedString {
        fun visitNode(node: Node) {
            when (node) {
                is Text -> {
                    append(node.literal)
                }
                
                is Emphasis -> {
                    val start = length
                    var child = node.firstChild
                    while (child != null) {
                        visitNode(child)
                        child = child.next
                    }
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                }
                
                is StrongEmphasis -> {
                    val start = length
                    var child = node.firstChild
                    while (child != null) {
                        visitNode(child)
                        child = child.next
                    }
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                }
                
                is Code -> {
                    val start = length
                    append(node.literal)
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFFE8E8E8),
                            color = Color(0xFFE91E63)
                        ), 
                        start, 
                        length
                    )
                }
                
                is Link -> {
                    val start = length
                    var child = node.firstChild
                    while (child != null) {
                        visitNode(child)
                        child = child.next
                    }
                    addStyle(
                        SpanStyle(
                            color = Color(0xFF2196F3),
                            textDecoration = TextDecoration.Underline
                        ), 
                        start, 
                        length
                    )
                }
                
                is SoftLineBreak -> {
                    append("\n")
                }
                
                is HardLineBreak -> {
                    append("\n")
                }
                
                else -> {
                    // 对于其他类型的节点，遍历其子节点
                    var child = node.firstChild
                    while (child != null) {
                        visitNode(child)
                        child = child.next
                    }
                }
            }
        }
        
        var child = node.firstChild
        while (child != null) {
            visitNode(child)
            child = child.next
        }
    }
}