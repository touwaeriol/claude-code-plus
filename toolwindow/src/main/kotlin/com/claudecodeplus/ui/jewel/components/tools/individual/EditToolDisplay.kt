package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.viewmodels.tool.EditToolDetail
import com.claudecodeplus.ui.jewel.components.tools.shared.DiffDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import org.jetbrains.jewel.ui.component.Text

/**
 * Edit 工具专用展示组件
 */
@Composable
fun EditToolDisplay(
    toolCall: ToolCall,
    showDetails: Boolean = true,
    onFileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 从 ViewModel 获取工具详情
    val toolDetail = toolCall.viewModel?.toolDetail as? EditToolDetail
    if (toolDetail == null) {
        Text("错误：无法获取 Edit 工具详情")
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!showDetails) {
            val fileName = toolDetail.filePath.substringAfterLast('/')
            val editType = if (toolDetail.replaceAll) "替换全部" else "单次替换"

            ToolHeaderDisplay(
                icon = "EDIT",
                toolName = "Edit",
                subtitle = "$fileName ($editType)",
                status = toolCall.status,
                onHeaderClick = onFileClick
            )
        }

        if (showDetails) {
            // 注意：点击工具本身会在 IDEA 中显示 diff，无需额外按钮
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    DiffDisplay(
                        oldContent = toolDetail.oldString,
                        newContent = toolDetail.newString,
                        filePath = toolDetail.filePath,
                        changeCount = 1
                    )
                }
                is ToolResult.Failure -> {
                    ToolResultDisplay(result)
                }
                null -> {
                    DiffDisplay(
                        oldContent = toolDetail.oldString,
                        newContent = toolDetail.newString,
                        filePath = toolDetail.filePath,
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
