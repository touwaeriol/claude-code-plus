package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.sdk.types.GrepToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.SearchResultDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay

/**
 * Grep工具专用展示组件
 *
 * 🎯 职责：专门处理Grep工具的展示
 * 🔧 特点：显示文本搜索、匹配结果、搜索选项
 */
@Composable
fun GrepToolDisplay(
    toolCall: ToolCall,
    grepTool: GrepToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 工具头部信息
        val subtitle = buildString {
            append("search: ${grepTool.pattern}")
            when {
                grepTool.glob != null -> append(" in ${grepTool.glob}")
                grepTool.type != null -> append(" in *.${grepTool.type}")
                grepTool.path != null -> append(" in ${grepTool.path}")
            }
            if (grepTool.caseInsensitive) append(" [忽略大小写]")
            if (grepTool.showLineNumbers) append(" [显示行号]")
        }

        ToolHeaderDisplay(
            icon = "🔍",
            toolName = "Grep",
            subtitle = subtitle,
            status = toolCall.status
        )

        // 显示搜索结果
        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    // 解析搜索结果
                    val searchResults = result.output
                        .split('\n')
                        .filter { it.trim().isNotEmpty() }

                    SearchResultDisplay(
                        results = searchResults,
                        searchTerm = grepTool.pattern,
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