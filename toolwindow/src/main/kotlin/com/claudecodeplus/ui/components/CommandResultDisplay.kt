package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.jewel.components.tools.output.AnsiOutputView
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.ui.viewmodels.tool.BashToolDetail
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * Bash 命令执行结果展示组件
 */
@Composable
fun CommandResultDisplay(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    val result = toolCall.result
    val command = (toolCall.viewModel?.toolDetail as? BashToolDetail)?.command ?: ""
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 命令信息头部
        CommandHeader(command, result)
        
        // 命令输出 - 使用 AnsiOutputView
        when (result) {
            is ToolResult.CommandResult -> {
                if (result.output.isNotBlank()) {
                    AnsiOutputView(
                        text = result.output,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "(无输出)",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    )
                }
            }
            is ToolResult.Success -> {
                // 通用成功结果
                if (result.output.isNotBlank()) {
                    AnsiOutputView(
                        text = result.output,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "(无输出)",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    )
                }
            }
            is ToolResult.Failure -> {
                // 错误结果
                ErrorOutput(
                    error = result.error,
                    details = result.details
                )
            }
            else -> {}
        }
    }
}

/**
 * 命令头部信息
 */
@Composable
private fun CommandHeader(
    command: String,
    result: ToolResult?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 命令行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$",
                style = JewelTheme.defaultTextStyle.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                )
            )
            Text(
                text = command,
                style = JewelTheme.defaultTextStyle.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                ),
                modifier = Modifier.weight(1f)
            )
        }
        
        // 执行状态
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (result) {
                is ToolResult.CommandResult -> {
                    // 退出码
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "退出码:",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                            )
                        )
                        Text(
                            text = result.exitCode.toString(),
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp,
                                color = if (result.exitCode == 0) {
                                    Color(0xFF51CF66)
                                } else {
                                    Color(0xFFFF6B6B)
                                },
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                        )
                    }
                    
                    // 执行时间
                    Text(
                        text = "耗时: ${formatDuration(result.duration)}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                        )
                    )
                }
                is ToolResult.Success -> {
                    Text(
                        text = "✓ 执行成功",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = Color(0xFF51CF66)
                        )
                    )
                }
                is ToolResult.Failure -> {
                    Text(
                        text = "✗ 执行失败",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = Color(0xFFFF6B6B)
                        )
                    )
                }
                else -> {}
            }
        }
    }
}


/**
 * 错误输出
 */
@Composable
private fun ErrorOutput(
    error: String,
    details: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFF6B6B).copy(alpha = 0.1f))
            .padding(8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = error,
                style = JewelTheme.defaultTextStyle.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFFF6B6B)
                )
            )
            
            details?.let { detail ->
                Text(
                    text = detail,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                    )
                )
            }
        }
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
