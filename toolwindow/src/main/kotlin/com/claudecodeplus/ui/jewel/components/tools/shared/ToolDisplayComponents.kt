@file:OptIn(ExperimentalFoundationApi::class, org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.claudecodeplus.ui.jewel.components.tools.shared

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 底层可复用组件集合
 *
 * 这些组件可以被多个工具展示组件复用，遵循DRY原则。
 * 每个组件都有明确的职责和接口。
 */

/**
 * 工具头部显示组件（可复用）
 * 用于显示工具的基本信息：图标、名称、副标题、状态
 */
@Composable
fun ToolHeaderDisplay(
    icon: String,
    toolName: String,
    subtitle: String,
    status: ToolCallStatus,
    onHeaderClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (onHeaderClick != null) {
                    Modifier.clickable { onHeaderClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 工具图标
        Text(
            text = icon,
            style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
        )

        // 工具名称和副标题
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = toolName,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = JewelTheme.globalColors.text.normal
                )
            )
            Text(
                text = subtitle,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 状态指示器
        Text(
            text = when (status) {
                ToolCallStatus.PENDING -> "⏳"
                ToolCallStatus.RUNNING -> "🔄"
                ToolCallStatus.SUCCESS -> "✅"
                ToolCallStatus.FAILED -> "❌"
                ToolCallStatus.CANCELLED -> "⚠️"
            },
            style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
        )
    }
}

/**
 * 文件内容展示组件（可复用）
 * Read/Write/Edit等文件操作工具可以复用
 */
@Composable
fun FileContentDisplay(
    content: String,
    filePath: String? = null,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 文件路径（如果提供）
        filePath?.let { path ->
            Text(
                text = "📄 ${path.substringAfterLast('/')}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                )
            )
        }

        // 文件内容
        SelectionContainer {
            Text(
                text = content,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f),
                    lineHeight = 14.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (maxLines != Int.MAX_VALUE) {
                            Modifier.heightIn(max = (maxLines * 14).dp)
                        } else {
                            Modifier
                        }
                    )
                    .verticalScroll(rememberScrollState()),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 搜索结果展示组件（可复用）
 * Glob/Grep等搜索工具可以复用
 */
@Composable
fun SearchResultDisplay(
    results: List<String>,
    searchTerm: String? = null,
    totalCount: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 搜索统计
        val count = totalCount ?: results.size
        Text(
            text = buildString {
                append("🔍 ")
                if (searchTerm != null) {
                    append("搜索 \"$searchTerm\"：")
                }
                append("找到 $count 个结果")
            },
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
            )
        )

        // 结果列表
        if (results.isEmpty()) {
            Text(
                text = "未找到匹配结果",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                )
            )
        } else {
            Column(
                modifier = Modifier
                    .heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                results.take(20).forEach { result ->
                    Text(
                        text = result,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (results.size > 20) {
                    Text(
                        text = "... 还有 ${results.size - 20} 个结果",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 9.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    }
}

/**
 * 网页内容展示组件（可复用）
 * WebFetch/WebSearch等网络工具可以复用
 */
@Composable
fun WebContentDisplay(
    content: String,
    url: String? = null,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // URL或标题
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🌐",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            Text(
                text = title ?: url?.let {
                    it.removePrefix("https://").removePrefix("http://").substringBefore("/")
                } ?: "网页内容",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 内容摘要
        SelectionContainer {
            Text(
                text = if (content.length > 300) content.take(297) + "..." else content,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f),
                    lineHeight = 14.sp
                ),
                modifier = Modifier
                    .heightIn(max = 80.dp)
                    .verticalScroll(rememberScrollState())
            )
        }

        // 内容统计
        Text(
            text = "内容长度: ${content.length} 字符",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 9.sp,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * 差异展示组件（可复用）
 * Edit/MultiEdit等编辑工具可以复用
 */
@Composable
fun DiffDisplay(
    oldContent: String?,
    newContent: String?,
    filePath: String? = null,
    changeCount: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 文件信息
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✏️",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            Text(
                text = buildString {
                    append(filePath?.substringAfterLast('/') ?: "文件编辑")
                    if (changeCount != null) {
                        append(" ($changeCount 处修改)")
                    }
                },
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                )
            )
        }

        // 差异内容（简化显示）
        if (oldContent != null && newContent != null) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 删除的内容
                if (oldContent.isNotEmpty()) {
                    Text(
                        text = "- ${oldContent.take(100)}${if (oldContent.length > 100) "..." else ""}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFF6B6B)
                        )
                    )
                }

                // 添加的内容
                if (newContent.isNotEmpty()) {
                    Text(
                        text = "+ ${newContent.take(100)}${if (newContent.length > 100) "..." else ""}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF4CAF50)
                        )
                    )
                }
            }
        }
    }
}

/**
 * 终端输出展示组件（可复用）
 * Bash等命令工具可以复用
 */
@Composable
fun TerminalOutputDisplay(
    output: String,
    command: String? = null,
    exitCode: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 命令信息
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💻",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            Text(
                text = buildString {
                    append(command?.take(30) ?: "命令执行")
                    if (command != null && command.length > 30) append("...")
                    if (exitCode != null) {
                        append(" (退出码: $exitCode)")
                    }
                },
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                )
            )
        }

        // 输出内容
        SelectionContainer {
            Text(
                text = output,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f),
                    lineHeight = 12.sp
                ),
                modifier = Modifier
                    .heightIn(max = 100.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

/**
 * 通用工具结果展示组件（可复用）
 * 处理各种ToolResult类型
 */
@Composable
fun ToolResultDisplay(
    result: ToolResult,
    modifier: Modifier = Modifier
) {
    when (result) {
        is ToolResult.Success -> {
            Text(
                text = if (result.output.length > 200) {
                    result.output.take(197) + "..."
                } else {
                    result.output
                },
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                ),
                modifier = modifier
            )
        }
        is ToolResult.Failure -> {
            Text(
                text = "❌ ${result.error}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = Color(0xFFFF6B6B)
                ),
                modifier = modifier
            )
        }
        else -> {
            Text(
                text = result.toString(),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                ),
                modifier = modifier
            )
        }
    }
}