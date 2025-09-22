package com.claudecodeplus.ui.jewel.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text

/**
 * Markdown 标题组件
 */
@Composable
fun MarkdownHeading(
    level: Int,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontSize = when (level) {
        1 -> 24.sp
        2 -> 20.sp
        3 -> 18.sp
        4 -> 16.sp
        5 -> 14.sp
        6 -> 13.sp
        else -> 14.sp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column {
            SelectionContainer {
                Row {
                    content()
                }
            }
            // H1 和 H2 添加下划线
            if (level <= 2) {
                Spacer(modifier = Modifier.height(4.dp))
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    orientation = Orientation.Horizontal,
                    color = JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f)
                )
            }
        }
    }
}

/**
 * Markdown 段落组件
 */
@Composable
fun MarkdownParagraph(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        SelectionContainer {
            content()
        }
    }
}

/**
 * Markdown 列表组件
 */
@Composable
fun MarkdownList(
    items: List<@Composable () -> Unit>,
    ordered: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),  // 增加列表前后间距
        verticalArrangement = Arrangement.spacedBy(4.dp) // 增加列表项之间的垂直间距
    ) {
        items.forEachIndexed { index, item ->
            MarkdownListItem(
                marker = if (ordered) "${index + 1}." else "•",
                content = item
            )
        }
    }
}

/**
 * Markdown 列表项组件
 */
@Composable
fun MarkdownListItem(
    marker: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
        verticalAlignment = Alignment.Top // 确保顶部对齐
    ) {
        Text(
            text = marker,
            style = JewelTheme.defaultTextStyle.copy(
                fontFamily = if (marker.contains(".")) FontFamily.Default else FontFamily.Monospace
            )
            // 移除固定宽度，让标记自然占用所需空间
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            SelectionContainer {
                content()
            }
        }
    }
}

/**
 * Markdown 代码块组件
 */
@Composable
fun MarkdownCodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier
) {
    val isDark = JewelTheme.isDark
    val backgroundColor = if (isDark) {
        Color(0xFF2B2B2B)
    } else {
        Color(0xFFF5F5F5)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 语言标签
        language?.let {
            if (it.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .background(
                            JewelTheme.globalColors.borders.normal.copy(alpha = 0.1f),
                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = it,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        }

        // 代码内容
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .border(
                    1.dp,
                    JewelTheme.globalColors.borders.normal.copy(alpha = 0.2f),
                    RoundedCornerShape(4.dp)
                )
                .padding(12.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            SelectionContainer {
                Text(
                    text = code,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

/**
 * Markdown 内联代码组件
 */
@Composable
fun MarkdownInlineCode(
    code: String,
    modifier: Modifier = Modifier
) {
    val isDark = JewelTheme.isDark
    val backgroundColor = if (isDark) {
        Color(0xFF3C3F41)
    } else {
        Color(0xFFEEEEEE)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(backgroundColor)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = code,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}

/**
 * Markdown 引用块组件
 */
@Composable
fun MarkdownBlockQuote(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // 左侧竖线
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    JewelTheme.globalColors.borders.normal.copy(alpha = 0.5f),
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        // 引用内容
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            SelectionContainer {
                content()
            }
        }
    }
}

/**
 * Markdown 链接组件
 */
@Composable
fun MarkdownLink(
    text: String,
    url: String,
    onClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Link(
        text = text,
        onClick = { onClick(url) },
        modifier = modifier
    )
}

/**
 * Markdown 图片组件（简化版，仅显示占位符）
 */
@Composable
fun MarkdownImage(
    alt: String,
    url: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(JewelTheme.globalColors.borders.normal.copy(alpha = 0.1f))
            .border(
                1.dp,
                JewelTheme.globalColors.borders.normal.copy(alpha = 0.2f),
                RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🖼️",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 24.sp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = alt.ifBlank { "Image" },
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                )
            )
        }
    }
}

/**
 * Markdown 表格组件
 */
@Composable
fun MarkdownTable(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier
) {
    val isDark = JewelTheme.isDark
    val backgroundColor = if (isDark) {
        Color(0xFF2B2B2B)
    } else {
        Color(0xFFF9F9F9)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(
                1.dp,
                JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f),
                RoundedCornerShape(4.dp)
            )
            .horizontalScroll(rememberScrollState())
    ) {
        Column {
            // 表头
            Row(
                modifier = Modifier
                    .background(backgroundColor)
                    .padding(8.dp)
            ) {
                headers.forEach { header ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = header,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Divider(
                orientation = Orientation.Horizontal,
                color = JewelTheme.globalColors.borders.normal.copy(alpha = 0.2f)
            )

            // 表格行
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                ) {
                    row.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = cell,
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }
                }
                if (row != rows.last()) {
                    Divider(
                        orientation = Orientation.Horizontal,
                        color = JewelTheme.globalColors.borders.normal.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

/**
 * Markdown 水平分割线组件
 */
@Composable
fun MarkdownHorizontalRule(
    modifier: Modifier = Modifier
) {
    Divider(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        orientation = Orientation.Horizontal,
        color = JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f)
    )
}

/**
 * Markdown 任务列表项组件
 */
@Composable
fun MarkdownTaskListItem(
    checked: Boolean,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = if (checked) "☑" else "☐",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 13.sp
            ),
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            SelectionContainer {
                content()
            }
        }
    }
}

/**
 * Markdown 文本样式组件
 */
@Composable
fun MarkdownText(
    text: String,
    bold: Boolean = false,
    italic: Boolean = false,
    strikethrough: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = JewelTheme.defaultTextStyle.copy(
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
        ),
        modifier = modifier
    )
}