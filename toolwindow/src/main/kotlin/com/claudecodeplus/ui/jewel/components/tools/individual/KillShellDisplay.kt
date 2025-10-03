package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.viewmodels.tool.KillShellToolDetail
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import org.jetbrains.jewel.ui.component.Text

@Composable
fun KillShellDisplay(
    toolCall: ToolCall,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    val toolDetail = toolCall.viewModel?.toolDetail as? KillShellToolDetail
    if (toolDetail == null) {
        Text("错误：无法获取 KillShell 工具详情")
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!showDetails) {
            ToolHeaderDisplay(
                icon = "❌",
                toolName = "KillShell",
                subtitle = "shell_id: ${toolDetail.shellId}",
                status = toolCall.status
            )
        }

        if (showDetails && toolCall.result != null) {
            ToolResultDisplay(toolCall.result!!)
        }
    }
}
