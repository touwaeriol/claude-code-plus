package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.sdk.types.NotebookEditToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.FileContentDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import com.claudecodeplus.ui.models.ToolCall
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * NotebookEdit 工具展示组件
 */
@Composable
fun NotebookEditToolDisplay(
    toolCall: ToolCall,
    notebookEditTool: NotebookEditToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!showDetails) {
            val fileName = notebookEditTool.notebookPath.substringAfterLast('/')
                .substringAfterLast('\\')
            ToolHeaderDisplay(
                icon = "NB",
                toolName = "NotebookEdit",
                subtitle = fileName,
                status = toolCall.status
            )
        }

        if (showDetails) {
            Text(
                text = "Notebook：${notebookEditTool.notebookPath}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            notebookEditTool.cellId?.let { cellId ->
                Text(
                    text = "单元格：$cellId (${notebookEditTool.cellType ?: "unknown"})",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                )
            }

            notebookEditTool.editMode?.let { mode ->
                Text(
                    text = "模式：$mode",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                )
            }

            FileContentDisplay(
                content = notebookEditTool.newSource,
                filePath = null,
                maxLines = 30
            )

            toolCall.result?.let { result ->
                ToolResultDisplay(result)
            }
        }
    }
}
