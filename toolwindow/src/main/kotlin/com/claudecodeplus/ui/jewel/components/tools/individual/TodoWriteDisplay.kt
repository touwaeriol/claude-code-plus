package com.claudecodeplus.ui.jewel.components.tools.individual

import com.claudecodeplus.core.logging.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.viewmodels.tool.TodoWriteToolDetail
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.EnhancedTodoDisplay
import org.jetbrains.jewel.ui.component.Text

/**
 * TodoWrite工具专用展示组件（旧版本，保留作为备用）
 *
 * 🎯 职责：专门处理TodoWrite工具的展示
 * 🔧 特点：直接显示input.todos，不用result.content
 */
@Composable
fun TodoWriteDisplay(
    toolCall: ToolCall,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    val toolDetail = toolCall.viewModel?.toolDetail as? TodoWriteToolDetail
    if (toolDetail == null) {
        Text("错误：无法获取 TodoWrite 工具详情")
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!showDetails) {
            ToolHeaderDisplay(
                icon = "TODO",
                toolName = "TodoWrite",
                subtitle = "${toolDetail.todos.size} 个任务",
                status = toolCall.status
            )
        }

        if (showDetails) {
            logD("[TodoWriteDisplay] 🔧 显示input.todos内容，共计：${toolDetail.todos.size}")
            EnhancedTodoDisplay(
                todos = toolDetail.todos,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
