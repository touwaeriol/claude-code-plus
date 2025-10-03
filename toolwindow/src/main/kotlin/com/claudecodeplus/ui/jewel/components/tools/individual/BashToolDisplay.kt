package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.ui.viewmodels.tool.BashToolDetail
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.TerminalOutputDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import org.jetbrains.jewel.ui.component.Text

/**
 * Bash工具专用展示组件
 *
 * 🎯 职责：专门处理Bash工具的展示
 * 🔧 特点：显示命令、执行状态、终端输出
 */
@Composable
fun BashToolDisplay(
    toolCall: ToolCall,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 从 ViewModel 获取工具详情
    val toolDetail = toolCall.viewModel?.toolDetail as? BashToolDetail
    if (toolDetail == null) {
        Text("错误：无法获取 Bash 工具详情")
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 只在非详情模式下显示工具头部信息（避免展开时重复）
        if (!showDetails) {
            val command = if (toolDetail.command.length > 30) {
                toolDetail.command.take(27) + "..."
            } else {
                toolDetail.command
            }

            val subtitle = buildString {
                append(command)
                if (toolDetail.timeout != null) {
                    append(" (timeout: ${toolDetail.timeout}ms)")
                }
                if (toolDetail.runInBackground) {
                    append(" [后台]")
                }
            }

            ToolHeaderDisplay(
                icon = "💻",
                toolName = "Bash",
                subtitle = subtitle,
                status = toolCall.status
            )
        }

        // 显示命令执行结果
        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    TerminalOutputDisplay(
                        output = result.output,
                        command = toolDetail.command
                    )
                }
                is ToolResult.CommandResult -> {
                    TerminalOutputDisplay(
                        output = result.output,
                        command = toolDetail.command,
                        exitCode = result.exitCode
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}