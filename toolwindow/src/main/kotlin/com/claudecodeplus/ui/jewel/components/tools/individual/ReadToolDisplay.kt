package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.sdk.types.ReadToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.FileContentDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay

/**
 * Read工具专用展示组件
 *
 * 🎯 职责：专门处理Read工具的展示
 * 🔧 特点：显示文件路径、读取范围、内容预览
 */
@Composable
fun ReadToolDisplay(
    toolCall: ToolCall,
    readTool: ReadToolUse,
    showDetails: Boolean = true,
    onFileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 只在非详情模式下显示工具头部信息（避免展开时重复）
        if (!showDetails) {
            val fileName = readTool.filePath.substringAfterLast('/')
            val rangeInfo = buildString {
                if (readTool.offset != null || readTool.limit != null) {
                    append(" (")
                    if (readTool.offset != null) append("offset: ${readTool.offset}")
                    if (readTool.limit != null) {
                        if (readTool.offset != null) append(", ")
                        append("limit: ${readTool.limit}")
                    }
                    append(")")
                }
            }

            ToolHeaderDisplay(
                icon = "📖",
                toolName = "Read",
                subtitle = "$fileName$rangeInfo",
                status = toolCall.status,
                onHeaderClick = onFileClick
            )
        }

        // 显示文件内容
        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    FileContentDisplay(
                        content = result.output,
                        filePath = readTool.filePath,
                        maxLines = 15  // 限制显示行数
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}