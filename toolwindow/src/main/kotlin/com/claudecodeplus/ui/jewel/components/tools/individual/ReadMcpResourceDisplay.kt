package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.sdk.types.ReadMcpResourceToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.FileContentDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay

/**
 * ReadMcpResource工具专用展示组件
 *
 * 🎯 职责：专门处理ReadMcpResource工具的展示
 * 🔧 特点：显示MCP服务器、资源URI、内容
 */
@Composable
fun ReadMcpResourceDisplay(
    toolCall: ToolCall,
    readMcpResourceTool: ReadMcpResourceToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 工具头部信息
        val subtitle = "${readMcpResourceTool.server}/${readMcpResourceTool.uri.substringAfterLast('/')}"

        ToolHeaderDisplay(
            icon = "📋",
            toolName = "ReadMcpResource",
            subtitle = subtitle,
            status = toolCall.status
        )

        // 显示资源内容
        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    FileContentDisplay(
                        content = result.output,
                        filePath = "${readMcpResourceTool.server}:${readMcpResourceTool.uri}",
                        maxLines = 20
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}