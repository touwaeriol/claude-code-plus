package com.claudecodeplus.ui.jewel.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import org.commonmark.parser.Parser
import org.commonmark.node.*
import org.commonmark.ext.gfm.tables.*
import org.commonmark.ext.gfm.strikethrough.*
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension

/**
 * 简化的Markdown渲染器 - 完全基于CommonMark开源库
 * 我们只负责UI渲染，解析完全交给开源库
 */
@Composable
fun SimpleMarkdownRenderer(
    markdown: String,
    onCodeAction: (code: String, language: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val parser = remember {
        Parser.builder()
            .extensions(listOf(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create(),
                TaskListItemsExtension.create(),
                HeadingAnchorExtension.create()
            ))
            .build()
    }

    val document = remember(markdown) { parser.parse(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 直接渲染AST节点，无需自定义数据结构
        RenderNode(
            node = document,
            onCodeAction = onCodeAction
        )
    }
}

/**
 * 递归渲染CommonMark AST节点
 * 利用开源库的完整解析能力
 */
@Composable
private fun RenderNode(
    node: Node,
    onCodeAction: (code: String, language: String) -> Unit = { _, _ -> }
) {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is Paragraph -> {
                val content = buildInlineContent(child)

                // 检测是否为标题样式的段落（如 **目录结构：**）
                val isHeadingLike = isHeadingLikeParagraph(child)

                SelectionContainer {
                    Text(
                        text = content,
                        style = if (isHeadingLike) {
                            JewelTheme.defaultTextStyle.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = JewelTheme.globalColors.text.normal
                            )
                        } else {
                            JewelTheme.defaultTextStyle
                        },
                        modifier = if (isHeadingLike) {
                            Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    )
                }
            }

            is Heading -> {
                val heading = child as Heading
                SelectionContainer {
                    Text(
                        text = buildInlineContent(heading),
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = when (heading.level) {
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

            is FencedCodeBlock, is IndentedCodeBlock -> {
                val code = when (child) {
                    is FencedCodeBlock -> child.literal ?: ""
                    is IndentedCodeBlock -> child.literal ?: ""
                    else -> ""
                }
                val language = when (child) {
                    is FencedCodeBlock -> child.info ?: ""
                    else -> ""
                }

                SimpleCodeBlock(
                    code = code,
                    language = language,
                    onCodeAction = onCodeAction
                )
            }

            is BulletList, is OrderedList -> {
                RenderList(
                    listNode = child,
                    level = 0
                )
            }

            is BlockQuote -> {
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
                    Column(
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        RenderNode(child)
                    }
                }
            }

            is TableBlock -> {
                RenderTable(child)
            }
        }
        child = child.next
    }
}

/**
 * 渲染列表 - 支持嵌套
 */
@Composable
private fun RenderList(
    listNode: Node,
    level: Int
) {
    val isOrdered = listNode is OrderedList

    Column(
        modifier = Modifier.padding(start = (level * 20).dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        var itemIndex = 1
        var child = listNode.firstChild

        while (child != null) {
            if (child is ListItem) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 渲染列表项内容
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 列表标记
                        Text(
                            text = if (isOrdered) "$itemIndex." else "•",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = JewelTheme.globalColors.text.normal
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        // 列表项内容
                        Column(modifier = Modifier.weight(1f)) {
                            RenderListItemContent(child)
                        }
                    }

                    itemIndex++
                }
            }
            child = child.next
        }
    }
}

/**
 * 渲染列表项内容，处理嵌套情况
 */
@Composable
private fun RenderListItemContent(listItem: ListItem) {
    var child = listItem.firstChild
    while (child != null) {
        when (child) {
            is Paragraph -> {
                SelectionContainer {
                    Text(
                        text = buildInlineContent(child),
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.normal,
                            lineHeight = 20.sp
                        )
                    )
                }
            }
            is BulletList, is OrderedList -> {
                // 嵌套列表，递归渲染
                RenderList(child, level = 1)
            }
            else -> {
                // 其他类型的内容
                RenderNode(child)
            }
        }
        child = child.next
    }
}

/**
 * 构建内联内容的AnnotatedString
 */
private fun buildInlineContent(node: Node): AnnotatedString {
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
                    // 增强加粗效果，使其更加突出
                    addStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold
                        ),
                        start,
                        length
                    )
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
                is SoftLineBreak, is HardLineBreak -> {
                    append("\n")
                }
                else -> {
                    // 递归处理其他节点
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
 * 简化的代码块组件
 */
@Composable
private fun SimpleCodeBlock(
    code: String,
    language: String,
    onCodeAction: (code: String, language: String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
            .hoverable(interactionSource)
    ) {
        Column {
            // 语言标识
            if (language.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = language,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                        )
                    )
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

        // 悬浮操作按钮
        AnimatedVisibility(
            visible = isHovered,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(
                        JewelTheme.globalColors.panelBackground.copy(alpha = 0.9f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                // 复制按钮
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            clipboardManager.setText(AnnotatedString(code))
                            copied = true
                        }
                        .background(
                            if (copied) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (copied) "✓" else "📋",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = if (copied) Color(0xFF4CAF50)
                                   else JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                        )
                    )
                }

                // 插入按钮
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onCodeAction(code, language) }
                        .background(Color.Transparent, RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⬇",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                        )
                    )
                }
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
 * 检测段落是否为类似标题的格式（如 **目录结构：**）
 */
private fun isHeadingLikeParagraph(paragraph: Paragraph): Boolean {
    // 检查段落是否只包含一个StrongEmphasis节点，且以冒号结尾
    val firstChild = paragraph.firstChild
    if (firstChild is StrongEmphasis && firstChild.next == null) {
        // 获取StrongEmphasis的文本内容
        val textContent = extractTextContent(firstChild)
        return textContent.endsWith(":") || textContent.endsWith("：")
    }
    return false
}

/**
 * 提取节点的纯文本内容
 */
private fun extractTextContent(node: Node): String {
    val sb = StringBuilder()

    fun collectText(n: Node) {
        when (n) {
            is Text -> sb.append(n.literal)
            else -> {
                var child = n.firstChild
                while (child != null) {
                    collectText(child)
                    child = child.next
                }
            }
        }
    }

    collectText(node)
    return sb.toString()
}

/**
 * 渲染表格
 */
@Composable
private fun RenderTable(tableNode: TableBlock) {
    SelectionContainer {
        Column(
            modifier = Modifier
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
                .padding(8.dp)
        ) {
            // 简单表格渲染 - 暂时显示为文本
            Text(
                text = "表格内容",
                style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.normal
                )
            )
        }
    }
}