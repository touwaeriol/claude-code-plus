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
import org.commonmark.ext.gfm.tables.*

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
    val parser = remember { 
        Parser.builder()
            .extensions(listOf(TablesExtension.create()))
            .build() 
    }
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
                
                is MarkdownBlock.Table -> {
                    MarkdownTable(
                        headers = block.headers,
                        rows = block.rows,
                        alignments = block.alignments
                    )
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
    data class Table(
        val headers: List<AnnotatedString>,
        val rows: List<List<AnnotatedString>>,
        val alignments: List<TableCellAlignment>
    ) : MarkdownBlock()
}

/**
 * 表格单元格对齐方式
 */
private enum class TableCellAlignment {
    LEFT, CENTER, RIGHT
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
            
            is TableBlock -> {
                val headers = mutableListOf<AnnotatedString>()
                val rows = mutableListOf<List<AnnotatedString>>()
                val alignments = mutableListOf<TableCellAlignment>()
                
                // 解析表格头部
                val head = child.firstChild as? TableHead
                head?.let { headerNode ->
                    var headerRow = headerNode.firstChild as? TableRow
                    headerRow?.let { row ->
                        var cell = row.firstChild
                        while (cell != null) {
                            if (cell is TableCell) {
                                headers.add(parseInlineContent(cell))
                                // 解析对齐方式
                                val alignment = when (cell.alignment) {
                                    TableCell.Alignment.LEFT -> TableCellAlignment.LEFT
                                    TableCell.Alignment.CENTER -> TableCellAlignment.CENTER
                                    TableCell.Alignment.RIGHT -> TableCellAlignment.RIGHT
                                    else -> TableCellAlignment.LEFT
                                }
                                alignments.add(alignment)
                            }
                            cell = cell.next
                        }
                    }
                }
                
                // 解析表格体
                val body = head?.next as? TableBody
                body?.let { bodyNode ->
                    var bodyRow = bodyNode.firstChild
                    while (bodyRow != null) {
                        if (bodyRow is TableRow) {
                            val rowCells = mutableListOf<AnnotatedString>()
                            var cell = bodyRow.firstChild
                            while (cell != null) {
                                if (cell is TableCell) {
                                    rowCells.add(parseInlineContent(cell))
                                }
                                cell = cell.next
                            }
                            rows.add(rowCells)
                        }
                        bodyRow = bodyRow.next
                    }
                }
                
                blocks.add(MarkdownBlock.Table(headers, rows, alignments))
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

/**
 * 表格渲染组件
 */
@Composable
private fun MarkdownTable(
    headers: List<AnnotatedString>,
    rows: List<List<AnnotatedString>>,
    alignments: List<TableCellAlignment>,
    modifier: Modifier = Modifier
) {
    SelectionContainer {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    JewelTheme.globalColors.borders.normal,
                    RoundedCornerShape(8.dp)
                )
                .padding(1.dp)
        ) {
            // 表头
            if (headers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                        .padding(vertical = 8.dp)
                ) {
                    headers.forEachIndexed { index, header ->
                        val alignment = alignments.getOrNull(index) ?: TableCellAlignment.LEFT
                        TableCell(
                            content = header,
                            alignment = alignment,
                            isHeader = true,
                            modifier = Modifier.weight(1f)
                        )
                        // 添加分隔符
                        if (index < headers.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(JewelTheme.globalColors.borders.normal)
                            )
                        }
                    }
                }
                
                // 表头分隔线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(JewelTheme.globalColors.borders.normal)
                )
            }
            
            // 表格行
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    row.forEachIndexed { cellIndex, cell ->
                        val alignment = alignments.getOrNull(cellIndex) ?: TableCellAlignment.LEFT
                        TableCell(
                            content = cell,
                            alignment = alignment,
                            isHeader = false,
                            modifier = Modifier.weight(1f)
                        )
                        // 添加分隔符
                        if (cellIndex < row.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
                
                // 行分隔线（除了最后一行）
                if (rowIndex < rows.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(JewelTheme.globalColors.borders.normal.copy(alpha = 0.2f))
                    )
                }
            }
        }
    }
}

/**
 * 表格单元格组件
 */
@Composable
private fun TableCell(
    content: AnnotatedString,
    alignment: TableCellAlignment,
    isHeader: Boolean,
    modifier: Modifier = Modifier
) {
    val horizontalAlignment = when (alignment) {
        TableCellAlignment.LEFT -> Alignment.CenterStart
        TableCellAlignment.CENTER -> Alignment.Center
        TableCellAlignment.RIGHT -> Alignment.CenterEnd
    }
    
    Box(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = horizontalAlignment
    ) {
        Text(
            text = content,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 13.sp,
                fontWeight = if (isHeader) FontWeight.Medium else FontWeight.Normal,
                color = JewelTheme.globalColors.text.normal
            )
        )
    }
}