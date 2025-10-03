package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.ui.viewmodels.tool.GlobToolDetail
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.SearchResultDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import org.jetbrains.jewel.ui.component.Text

/**
 * Glob工具专用展示组件
 *
 * 🎯 职责：专门处理Glob工具的展示
 * 🔧 特点：显示文件模式匹配、搜索结果
 */
@Composable
fun GlobToolDisplay(
    toolCall: ToolCall,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    val toolDetail = toolCall.viewModel?.toolDetail as? GlobToolDetail
    if (toolDetail == null) {
        Text("错误：无法获取 Glob 工具详情")
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!showDetails) {
            val subtitle = buildString {
                append("pattern: ${toolDetail.pattern}")
                if (toolDetail.path != null) {
                    append(" in ${toolDetail.path}")
                }
            }

            ToolHeaderDisplay(
                icon = "🔍",
                toolName = "Glob",
                subtitle = subtitle,
                status = toolCall.status
            )
        }

        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    val fileList = result.output.split('\n').filter { it.trim().isNotEmpty() }
                    SearchResultDisplay(
                        results = fileList,
                        searchTerm = toolDetail.pattern,
                        totalCount = fileList.size
                    )
                }
                is ToolResult.FileSearchResult -> {
                    SearchResultDisplay(
                        results = result.files.map { it.path },
                        searchTerm = toolDetail.pattern,
                        totalCount = result.totalCount
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}
