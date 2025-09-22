package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.sdk.types.EditToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.DiffDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text

/**
 * Edit 工具专用展示组件
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
        if (!showDetails) {
            val fileName = editTool.filePath.substringAfterLast('/')
            val editType = if (editTool.replaceAll) "替换全部" else "单次替换"

            ToolHeaderDisplay(
                icon = "EDIT",
                toolName = "Edit",
                subtitle = "$fileName ($editType)",
                status = toolCall.status,
                onHeaderClick = onFileClick
            )
        }

        if (showDetails) {
            if (onFileClick != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DefaultButton(onClick = onFileClick) {
                        Text("在 IDE 中查看差异")
                    }
                }
            }

            when (val result = toolCall.result) {
                is ToolResult.Success -> {
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
