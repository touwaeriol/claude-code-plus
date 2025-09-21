package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.sdk.types.ListMcpResourcesToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.SearchResultDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay

/**
 * ListMcpResources工具专用展示组件
 *
 * 🎯 职责：专门处理ListMcpResources工具的展示
 * 🔧 特点：显示MCP服务器、资源列表
 */
@Composable
fun ListMcpResourcesDisplay(
    toolCall: ToolCall,
    listMcpResourcesTool: ListMcpResourcesToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 只在非详情模式下显示工具头部信息（避免展开时重复）
        if (!showDetails) {
            val subtitle = if (listMcpResourcesTool.server != null) {
                "server: ${listMcpResourcesTool.server}"
            } else {
                "列出所有MCP服务器资源"
            }

            ToolHeaderDisplay(
                icon = "🔌",
                toolName = "ListMcpResources",
                subtitle = subtitle,
                status = toolCall.status
            )
        }

        // 显示资源列表
        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    // 解析资源列表（假设是换行分隔的资源URI）
                    val resources = result.output
                        .split('\n')
                        .filter { it.trim().isNotEmpty() }

                    SearchResultDisplay(
                        results = resources,
                        searchTerm = "MCP Resources",
                        totalCount = resources.size
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}