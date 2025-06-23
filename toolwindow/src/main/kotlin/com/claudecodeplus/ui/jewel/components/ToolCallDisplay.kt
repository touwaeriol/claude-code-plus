package com.claudecodeplus.ui.jewel.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import androidx.compose.material.LocalContentColor

/**
 * 工具调用显示组件
 * 展示 AI 使用的工具及其执行状态
 */
@Composable
fun ToolCallDisplay(
    toolCalls: List<ToolCall>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3C3C3C), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "工具调用",
            style = JewelTheme.defaultTextStyle.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 14.sp
            )
        )
        
        toolCalls.forEach { toolCall ->
            ToolCallItem(toolCall)
        }
    }
}

/**
 * 单个工具调用项
 */
@Composable
private fun ToolCallItem(
    toolCall: ToolCall
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2B2B2B), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF3C3C3C), RoundedCornerShape(4.dp))
    ) {
        // 头部（可点击展开/折叠）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态图标
                Text(
                    when (toolCall.status) {
                        ToolCallStatus.PENDING -> "⏳"
                        ToolCallStatus.RUNNING -> "🔄"
                        ToolCallStatus.SUCCESS -> "✅"
                        ToolCallStatus.FAILED -> "❌"
                        ToolCallStatus.CANCELLED -> "🚫"
                    }
                )
                
                // 工具名称
                Text(
                    getToolDisplayName(toolCall.name),
                    style = JewelTheme.defaultTextStyle.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                )
                
                // 简要信息
                Text(
                    getToolBriefInfo(toolCall),
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = LocalContentColor.current.copy(alpha = 0.6f)
                    )
                )
            }
            
            // 展开/折叠图标
            Text(if (expanded) "▲" else "▼")
        }
        
        // 详细内容（可展开）
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 参数
                if (toolCall.parameters.isNotEmpty()) {
                    Text(
                        "参数:",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2B2B2B), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            formatParameters(toolCall.parameters),
                            style = JewelTheme.defaultTextStyle.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
                
                // 结果
                toolCall.result?.let { result ->
                    Text(
                        "结果:",
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
                                    .heightIn(max = 200.dp)
                                    .background(Color(0xFF2B2B2B), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    result.output,
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                )
                            }
                        }
                        is ToolResult.Failure -> {
                            Text(
                                "错误: ${result.error}",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF6B6B)
                                )
                            )
                        }
                        is ToolResult.FileSearchResult -> {
                            Text(
                                "找到 ${result.files.size} 个文件 (总计 ${result.totalCount})",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 12.sp
                                )
                            )
                        }
                        is ToolResult.FileReadResult -> {
                            Text(
                                "读取文件: ${result.lineCount} 行",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 12.sp
                                )
                            )
                        }
                        is ToolResult.FileEditResult -> {
                            Text(
                                "编辑文件: 修改行 ${result.changedLines}",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 12.sp
                                )
                            )
                        }
                        is ToolResult.CommandResult -> {
                            Text(
                                "命令执行: 退出码 ${result.exitCode}, 耗时 ${result.duration}ms",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
                
                // 执行时间
                if (toolCall.status != ToolCallStatus.PENDING) {
                    val duration = if (toolCall.endTime != null) {
                        "${toolCall.endTime - toolCall.startTime}ms"
                    } else {
                        "运行中..."
                    }
                    
                    Text(
                        "耗时: $duration",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = LocalContentColor.current.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    }
}

/**
 * 获取工具的显示名称
 */
private fun getToolDisplayName(toolName: String): String {
    return when (toolName) {
        "bash" -> "🖥️ 执行命令"
        "read_file" -> "📖 读取文件"
        "write_file" -> "✏️ 写入文件"
        "search" -> "🔍 搜索"
        "list_files" -> "📁 列出文件"
        else -> toolName
    }
}

/**
 * 获取工具的简要信息
 */
private fun getToolBriefInfo(toolCall: ToolCall): String {
    return when (toolCall.name) {
        "bash" -> toolCall.parameters["command"]?.toString()?.take(50) ?: ""
        "read_file" -> toolCall.parameters["path"]?.toString()?.substringAfterLast('/') ?: ""
        "write_file" -> toolCall.parameters["path"]?.toString()?.substringAfterLast('/') ?: ""
        "search" -> "\"${toolCall.parameters["query"]}\"" 
        "list_files" -> toolCall.parameters["path"]?.toString() ?: ""
        else -> ""
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
        is String -> if (value.length > 100) "\"${value.take(100)}...\"" else "\"$value\""
        is List<*> -> "[${value.size} items]"
        is Map<*, *> -> "{${value.size} entries}"
        else -> value.toString()
    }
}