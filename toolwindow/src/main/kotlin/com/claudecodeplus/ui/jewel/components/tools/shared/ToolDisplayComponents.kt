@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.claudecodeplus.ui.jewel.components.tools.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

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
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = toolName,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
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
        Text(
            text = when (status) {
                ToolCallStatus.PENDING -> "待处理"
                ToolCallStatus.RUNNING -> "执行中"
                ToolCallStatus.SUCCESS -> "已完成"
                ToolCallStatus.FAILED -> "已失败"
                ToolCallStatus.CANCELLED -> "已取消"
            },
            style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
        )
    }
}

@Composable
fun FileContentDisplay(
    content: String,
    filePath: String? = null,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        filePath?.let {
            Text(
                text = it.substringAfterLast('/'),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        val lines = content.lines().take(maxLines)
        Column(
            modifier = Modifier
                .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.2f))
                .padding(8.dp)
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
            if (content.lines().size > lines.size) {
                Text(
                    text = "...",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}

@Composable
fun SearchResultDisplay(
    results: List<String>,
    searchTerm: String? = null,
    totalCount: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val count = totalCount ?: results.size
        Text(
            text = buildString {
                append("搜索结果")
                if (!searchTerm.isNullOrBlank()) append(" \"$searchTerm\"")
                append(" · $count 项")
            },
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Column(
            modifier = Modifier
                .heightIn(max = 160.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            results.take(50).forEach { r ->
                Text(
                    text = r,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (results.size > 50) {
                Text(
                    text = "... 以及 ${results.size - 50} 项",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp)
                )
            }
        }
    }
}

@Composable
fun WebContentDisplay(
    content: String,
    url: String? = null,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🌐", style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp))
            Text(
                text = title ?: (url ?: "Web 内容"),
                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (content.length > 400) content.take(397) + "..." else content,
            style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp)
        )
    }
}

@Composable
fun DiffDisplay(
    oldContent: String?,
    newContent: String?,
    filePath: String? = null,
    changeCount: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        filePath?.let {
            Text(
                text = it.substringAfterLast('/'),
                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
            )
        }
        oldContent?.takeIf { it.isNotEmpty() }?.let { old ->
            Text(
                text = "- ${old.take(120)}${if (old.length > 120) "..." else ""}",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFD66A6A))
            )
        }
        newContent?.takeIf { it.isNotEmpty() }?.let { new ->
            Text(
                text = "+ ${new.take(120)}${if (new.length > 120) "..." else ""}",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF4CAF50))
            )
        }
        changeCount?.let {
            Text(text = "更改数: $it", style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp))
        }
    }
}

@Composable
fun TerminalOutputDisplay(
    output: String,
    command: String? = null,
    exitCode: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🖥", style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp))
            Text(
                text = buildString {
                    append(command?.take(40) ?: "命令输出")
                    if (command != null && command.length > 40) append("...")
                    exitCode?.let { append(" (exit=$it)") }
                },
                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
            )
        }
        Column(
            modifier = Modifier
                .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.2f))
                .padding(8.dp)
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            output.lines().take(200).forEach { line ->
                Text(
                    text = line,
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

@Composable
fun ToolResultDisplay(
    result: ToolResult,
    modifier: Modifier = Modifier
) {
    when (result) {
        is ToolResult.Success -> {
            Text(
                text = if (result.output.length > 200) result.output.take(197) + "..." else result.output,
                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
            )
        }
        is ToolResult.Failure -> {
            Text(
                text = "错误: ${result.error}",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp, color = Color(0xFFD66A6A))
            )
        }
        is ToolResult.CommandResult -> {
            Text(
                text = "exit=${result.exitCode}: ${result.output.take(160)}",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
            )
        }
        is ToolResult.FileSearchResult -> {
            Text(
                text = "共 ${result.totalCount} 个结果",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
            )
        }
        else -> {
            Text(text = result.toString(), style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp))
        }
    }
}

