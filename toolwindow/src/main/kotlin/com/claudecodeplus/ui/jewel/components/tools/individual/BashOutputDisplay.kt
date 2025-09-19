package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.sdk.types.BashOutputToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.TerminalOutputDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay

/**
 * BashOutput工具专用展示组件
 *
 * 🎯 职责：专门处理BashOutput工具的展示
 * 🔧 特点：显示Bash进程ID、过滤器、输出内容
 */
@Composable
fun BashOutputDisplay(
    toolCall: ToolCall,
    bashOutputTool: BashOutputToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 工具头部信息
        val subtitle = buildString {
            append("bash_id: ${bashOutputTool.bashId}")
            if (bashOutputTool.filter != null) {
                append(" | filter: ${bashOutputTool.filter}")
            }
        }

        ToolHeaderDisplay(
            icon = "📤",
            toolName = "BashOutput",
            subtitle = subtitle,
            status = toolCall.status
        )

        // 显示Bash输出结果
        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    TerminalOutputDisplay(
                        output = result.output,
                        command = "BashOutput ${bashOutputTool.bashId}"
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}