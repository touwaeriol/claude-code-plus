package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.sdk.types.EditToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.DiffDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay

/**
 * Edit工具专用展示组件
 *
 * 🎯 职责：专门处理Edit工具的展示
 * 🔧 特点：显示文件路径、编辑内容、差异对比
 */
@Composable
fun EditToolDisplay(
    toolCall: ToolCall,
    editTool: EditToolUse,
    showDetails: Boolean = true,
    onFileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 工具头部信息
        val fileName = editTool.filePath.substringAfterLast('/')
        val editType = if (editTool.replaceAll) "替换全部" else "单次替换"

        ToolHeaderDisplay(
            icon = "✏️",
            toolName = "Edit",
            subtitle = "$fileName ($editType)",
            status = toolCall.status,
            onHeaderClick = onFileClick
        )

        // 显示编辑差异
        if (showDetails) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    // 显示编辑前后的差异
                    DiffDisplay(
                        oldContent = editTool.oldString,
                        newContent = editTool.newString,
                        filePath = editTool.filePath,
                        changeCount = 1
                    )
                }
                is ToolResult.Failure -> {
                    ToolResultDisplay(result)
                }
                null -> {
                    // 工具还在运行，显示即将进行的编辑
                    DiffDisplay(
                        oldContent = editTool.oldString,
                        newContent = editTool.newString,
                        filePath = editTool.filePath,
                        changeCount = 1
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}