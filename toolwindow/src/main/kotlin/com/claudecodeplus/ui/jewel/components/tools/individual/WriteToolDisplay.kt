package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.sdk.types.WriteToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.FileContentDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import com.claudecodeplus.ui.models.ToolCall

/**
 * Write 工具专用展示组件
 */
@Composable
fun WriteToolDisplay(
    toolCall: ToolCall,
    writeTool: WriteToolUse,
    showDetails: Boolean = true,
    onFileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!showDetails) {
            val fileName = writeTool.filePath.substringAfterLast('/')
                .substringAfterLast('\\')
            ToolHeaderDisplay(
                icon = "WRITE",
                toolName = "Write",
                subtitle = fileName,
                status = toolCall.status,
                onHeaderClick = onFileClick
            )
        }

        if (showDetails) {
            FileContentDisplay(
                content = writeTool.content,
                filePath = writeTool.filePath,
                maxLines = 20
            )

            toolCall.result?.let { result ->
                ToolResultDisplay(result)
            }
        }
    }
}
