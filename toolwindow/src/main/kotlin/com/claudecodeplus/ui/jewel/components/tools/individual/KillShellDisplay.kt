package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.sdk.types.KillShellToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * KillShell工具专用展示组件
 *
 * 🎯 职责：专门处理KillShell工具的展示
 * 🔧 特点：显示Shell进程ID、终止状态
 */
@Composable
fun KillShellDisplay(
    toolCall: ToolCall,
    killShellTool: KillShellToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 只在非详情模式下显示工具头部信息（避免展开时重复）
        if (!showDetails) {
            ToolHeaderDisplay(
                icon = "⚡",
                toolName = "KillShell",
                subtitle = "shell_id: ${killShellTool.shellId}",
                status = toolCall.status
            )
        }

        // 显示结果
        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    Text(
                        text = "✅ Shell进程已终止: ${killShellTool.shellId}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        )
                    )
                }
                is ToolResult.Failure -> {
                    Text(
                        text = "❌ 终止失败: ${result.error}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = androidx.compose.ui.graphics.Color(0xFFFF6B6B)
                        )
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}