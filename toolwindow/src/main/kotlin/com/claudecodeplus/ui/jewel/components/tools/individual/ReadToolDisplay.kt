package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.ui.viewmodels.tool.ReadToolDetail
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.FileContentDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import org.jetbrains.jewel.ui.component.Text

/**
 * Read工具专用展示组件
 *
 * 🎯 职责：专门处理Read工具的展示
 * 🔧 特点：显示文件路径、读取范围、内容预览
 */
@Composable
fun ReadToolDisplay(
    toolCall: ToolCall,
    showDetails: Boolean = true,
    onFileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 从 ViewModel 获取工具详情
    val toolDetail = toolCall.viewModel?.toolDetail as? ReadToolDetail
    if (toolDetail == null) {
        Text("错误：无法获取 Read 工具详情")
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 只在非详情模式下显示工具头部信息（避免展开时重复）
        if (!showDetails) {
            val fileName = toolDetail.filePath.substringAfterLast('/')
            val rangeInfo = buildString {
                if (toolDetail.offset != null || toolDetail.limit != null) {
                    append(" (")
                    if (toolDetail.offset != null) append("offset: ${toolDetail.offset}")
                    if (toolDetail.limit != null) {
                        if (toolDetail.offset != null) append(", ")
                        append("limit: ${toolDetail.limit}")
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
                        filePath = toolDetail.filePath,
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