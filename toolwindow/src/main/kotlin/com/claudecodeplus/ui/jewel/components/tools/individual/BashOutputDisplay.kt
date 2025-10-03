package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.ui.viewmodels.tool.BashOutputToolDetail
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.TerminalOutputDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import org.jetbrains.jewel.ui.component.Text

/**
 * BashOutput工具专用展示组件
 *
 * 🎯 职责：专门处理BashOutput工具的展示
 * 🔧 特点：显示Bash进程ID、过滤器、输出内容
 */
@Composable
fun BashOutputDisplay(
    toolCall: ToolCall,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    val toolDetail = toolCall.viewModel?.toolDetail as? BashOutputToolDetail
    if (toolDetail == null) {
        Text("错误：无法获取 BashOutput 工具详情")
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!showDetails) {
            val subtitle = buildString {
                append("bash_id: ${toolDetail.bashId}")
                if (toolDetail.filter != null) {
                    append(" | filter: ${toolDetail.filter}")
                }
            }

            ToolHeaderDisplay(
                icon = "📤",
                toolName = "BashOutput",
                subtitle = subtitle,
                status = toolCall.status
            )
        }

        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    TerminalOutputDisplay(
                        output = result.output,
                        command = "BashOutput ${toolDetail.bashId}"
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}
