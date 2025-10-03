package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.viewmodels.tool.WriteToolDetail
import com.claudecodeplus.ui.jewel.components.tools.shared.FileContentDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import com.claudecodeplus.ui.models.ToolCall
import org.jetbrains.jewel.ui.component.Text

/**
 * Write 工具专用展示组件
 */
@Composable
fun WriteToolDisplay(
    toolCall: ToolCall,
    showDetails: Boolean = true,
    onFileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 从 ViewModel 获取工具详情
    val toolDetail = toolCall.viewModel?.toolDetail as? WriteToolDetail
    if (toolDetail == null) {
        Text("错误：无法获取 Write 工具详情")
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!showDetails) {
            val fileName = toolDetail.filePath.substringAfterLast('/')
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
                content = toolDetail.content,
                filePath = toolDetail.filePath,
                maxLines = 20
            )

            toolCall.result?.let { result ->
                ToolResultDisplay(result)
            }
        }
    }
}
