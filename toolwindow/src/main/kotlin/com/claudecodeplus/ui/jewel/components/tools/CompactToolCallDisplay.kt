package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle

/**
 * 紧凑的工具调用显示组件
 * 默认单行显示，点击展开详情
 */
@Composable
fun CompactToolCallDisplay(
    toolCalls: List<ToolCall>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        toolCalls.forEach { toolCall ->
            CompactToolCallItem(toolCall)
        }
    }
}

/**
 * 单个工具调用的紧凑显示
 */
@Composable
private fun CompactToolCallItem(
    toolCall: ToolCall
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // 背景色动画
    val backgroundColor by animateColorAsState(
        targetValue = when {
            expanded -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f)
            isHovered -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f)
            else -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.1f)
        },
        animationSpec = tween(150)
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .hoverable(interactionSource)
    ) {
        // 紧凑的单行显示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧内容
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 工具图标
                Text(
                    text = getToolIcon(toolCall.name),
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
                )
                
                // 工具名称
                Text(
                    text = toolCall.name,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 13.sp,
                        color = JewelTheme.globalColors.text.normal
                    )
                )
                
                // 简要信息
                val briefInfo = getToolBriefInfo(toolCall)
                if (briefInfo.isNotBlank()) {
                    Text(
                        text = briefInfo,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, false)
                    )
                }
            }
            
            // 右侧状态和时间
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 执行时间（如果已完成）
                if (toolCall.status != ToolCallStatus.PENDING && toolCall.endTime != null) {
                    val duration = toolCall.endTime - toolCall.startTime
                    Text(
                        text = formatDuration(duration),
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                        )
                    )
                }
                
                // 状态指示器
                ToolStatusIndicator(
                    status = when (toolCall.status) {
                        ToolCallStatus.PENDING -> ToolExecutionStatus.PENDING
                        ToolCallStatus.RUNNING -> ToolExecutionStatus.RUNNING
                        ToolCallStatus.SUCCESS -> ToolExecutionStatus.SUCCESS
                        ToolCallStatus.FAILED -> ToolExecutionStatus.ERROR
                    },
                    size = 14.dp
                )
                
                // 展开/折叠图标
                Text(
                    text = if (expanded) "▼" else "▶",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                    )
                )
            }
        }
        
        // 展开的详细内容
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
        ) {
            ToolCallDetails(toolCall)
        }
    }
}

/**
 * 工具调用的详细信息
 */
@Composable
private fun ToolCallDetails(
    toolCall: ToolCall
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 参数
        if (toolCall.parameters.isNotEmpty()) {
            Text(
                "参数",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Text(
                    text = formatParameters(toolCall.parameters),
                    style = JewelTheme.defaultTextStyle.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                    )
                )
            }
        }
        
        // 结果
        toolCall.result?.let { result ->
            Divider(
                orientation = org.jetbrains.jewel.ui.Orientation.Horizontal,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            Text(
                "结果",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            )
            
            when (result) {
                is ToolResult.Success -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = result.output,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
                is ToolResult.Failure -> {
                    Text(
                        text = "❌ ${result.error}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = Color(0xFFFF6B6B)
                        )
                    )
                }
                is ToolResult.FileSearchResult -> {
                    Text(
                        text = "📁 找到 ${result.files.size} 个文件 (总计 ${result.totalCount})",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                    )
                }
                is ToolResult.FileReadResult -> {
                    Text(
                        text = "📖 读取 ${result.lineCount} 行 (${formatBytes(result.size)})",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                    )
                }
                is ToolResult.FileEditResult -> {
                    Text(
                        text = "✏️ 修改了 ${result.changedLines} 行",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                    )
                }
                is ToolResult.CommandResult -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "💻 退出码: ${result.exitCode} | 耗时: ${formatDuration(result.duration)}",
                            style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                        )
                        if (result.output.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 100.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = result.output,
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 获取工具图标
 */
private fun getToolIcon(toolName: String): String {
    return when {
        toolName.contains("LS", ignoreCase = true) -> "📁"
        toolName.contains("Read", ignoreCase = true) -> "📖"
        toolName.contains("Edit", ignoreCase = true) || toolName.contains("Write", ignoreCase = true) -> "✏️"
        toolName.contains("Bash", ignoreCase = true) -> "💻"
        toolName.contains("Search", ignoreCase = true) || toolName.contains("Grep", ignoreCase = true) -> "🔍"
        toolName.contains("Web", ignoreCase = true) -> "🌐"
        toolName.contains("Git", ignoreCase = true) -> "🔀"
        toolName.contains("Task", ignoreCase = true) -> "🤖"
        toolName.contains("Todo", ignoreCase = true) -> "📋"
        else -> "🔧"
    }
}

/**
 * 获取工具的简要信息
 */
private fun getToolBriefInfo(toolCall: ToolCall): String {
    return when {
        toolCall.name.contains("Bash", ignoreCase = true) -> 
            toolCall.parameters["command"]?.toString()?.take(40) ?: ""
        toolCall.name.contains("Read", ignoreCase = true) -> 
            toolCall.parameters["file_path"]?.toString()?.substringAfterLast('/')?.take(30) ?: 
            toolCall.parameters["path"]?.toString()?.substringAfterLast('/')?.take(30) ?: ""
        toolCall.name.contains("Write", ignoreCase = true) || toolCall.name.contains("Edit", ignoreCase = true) -> 
            toolCall.parameters["file_path"]?.toString()?.substringAfterLast('/')?.take(30) ?: 
            toolCall.parameters["path"]?.toString()?.substringAfterLast('/')?.take(30) ?: ""
        toolCall.name.contains("Search", ignoreCase = true) || toolCall.name.contains("Grep", ignoreCase = true) -> 
            "\"${toolCall.parameters["pattern"] ?: toolCall.parameters["query"] ?: ""}\""
        toolCall.name.contains("LS", ignoreCase = true) -> 
            toolCall.parameters["path"]?.toString()?.take(30) ?: ""
        toolCall.name.contains("Task", ignoreCase = true) ->
            toolCall.parameters["description"]?.toString()?.take(40) ?: ""
        toolCall.name.contains("Web", ignoreCase = true) ->
            toolCall.parameters["url"]?.toString()?.take(40) ?: ""
        toolCall.name.contains("Todo", ignoreCase = true) ->
            "待办事项管理"
        else -> {
            // 尝试显示第一个参数
            toolCall.parameters.values.firstOrNull()?.toString()?.take(40) ?: ""
        }
    }
}

/**
 * 格式化参数显示
 */
private fun formatParameters(parameters: Map<String, Any>): String {
    return parameters.entries.joinToString("\n") { (key, value) ->
        "$key: ${formatValue(value)}"
    }
}

private fun formatValue(value: Any): String {
    return when (value) {
        is String -> if (value.length > 80) "\"${value.take(80)}...\"" else "\"$value\""
        is List<*> -> "[${value.size} items]"
        is Map<*, *> -> "{${value.size} entries}"
        else -> value.toString()
    }
}

/**
 * 格式化时间长度
 */
private fun formatDuration(millis: Long): String {
    return when {
        millis < 1000 -> "${millis}ms"
        millis < 60000 -> "${millis / 1000}.${(millis % 1000) / 100}s"
        else -> "${millis / 60000}m ${(millis % 60000) / 1000}s"
    }
}

/**
 * 格式化字节数
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}