package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.sdk.types.GlobToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.SearchResultDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay

/**
 * Glob工具专用展示组件
 *
 * 🎯 职责：专门处理Glob工具的展示
 * 🔧 特点：显示文件模式匹配、搜索结果
 */
@Composable
fun GlobToolDisplay(
    toolCall: ToolCall,
    globTool: GlobToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 只在非详情模式下显示工具头部信息（避免展开时重复）
        if (!showDetails) {
            val subtitle = buildString {
                append("pattern: ${globTool.pattern}")
                if (globTool.path != null) {
                    append(" in ${globTool.path}")
                }
            }

            ToolHeaderDisplay(
                icon = "🔍",
                toolName = "Glob",
                subtitle = subtitle,
                status = toolCall.status
            )
        }

        // 显示搜索结果
        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    // 解析输出为文件列表
                    val fileList = result.output
                        .split('\n')
                        .filter { it.trim().isNotEmpty() }

                    SearchResultDisplay(
                        results = fileList,
                        searchTerm = globTool.pattern,
                        totalCount = fileList.size
                    )
                }
                is ToolResult.FileSearchResult -> {
                    SearchResultDisplay(
                        results = result.files.map { it.path },
                        searchTerm = globTool.pattern,
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