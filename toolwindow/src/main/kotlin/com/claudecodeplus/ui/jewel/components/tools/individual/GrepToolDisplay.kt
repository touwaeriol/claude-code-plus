package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.ui.viewmodels.tool.GrepToolDetail
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.SearchResultDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import org.jetbrains.jewel.ui.component.Text

/**
 * Grep工具专用展示组件
 *
 * 🎯 职责：专门处理Grep工具的展示
 * 🔧 特点：显示文本搜索、匹配结果、搜索选项
 */
@Composable
fun GrepToolDisplay(
    toolCall: ToolCall,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    val toolDetail = toolCall.viewModel?.toolDetail as? GrepToolDetail
    if (toolDetail == null) {
        Text("错误：无法获取 Grep 工具详情")
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!showDetails) {
            val subtitle = buildString {
                append("search: ${toolDetail.pattern}")
                when {
                    toolDetail.glob != null -> append(" in ${toolDetail.glob}")
                    toolDetail.path != null -> append(" in ${toolDetail.path}")
                }
            }

            ToolHeaderDisplay(
                icon = "🔍",
                toolName = "Grep",
                subtitle = subtitle,
                status = toolCall.status
            )
        }

        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    val searchResults = result.output.split('\n').filter { it.trim().isNotEmpty() }
                    SearchResultDisplay(
                        results = searchResults,
                        searchTerm = toolDetail.pattern,
                        totalCount = searchResults.size
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}
