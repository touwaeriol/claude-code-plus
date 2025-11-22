package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.viewmodels.tool.TodoWriteToolDetail
import com.claudecodeplus.ui.jewel.components.tools.EnhancedTodoDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.services.StringResources
import com.claudecodeplus.ui.services.formatStringResource
import org.jetbrains.jewel.ui.component.Text

/**
 * TodoWrite 工具展示（V2：国际化进度 + 无原始 todos 参数回显）
 */
@Composable
fun TodoWriteDisplayV2(
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
            // 优先显示正在进行的任务，如果没有则显示统计信息
            val inProgressTasks = toolDetail.todos.filter {
                it.status.equals("in_progress", ignoreCase = true)
            }

            val subtitle = if (inProgressTasks.isNotEmpty()) {
                // 显示第一个进行中的任务内容（限制长度避免过长）
                val taskContent = inProgressTasks.first().content
                if (taskContent.length > 50) {
                    taskContent.take(47) + "..."
                } else {
                    taskContent
                }
            } else {
                // 没有进行中的任务，显示统计信息
                val completed = toolDetail.todos.count {
                    it.status.equals("completed", ignoreCase = true)
                }
                "$completed / ${toolDetail.todos.size} 已完成"
            }

            ToolHeaderDisplay(
                icon = "TODO",
                toolName = "TodoWrite",
                subtitle = subtitle,
                status = toolCall.status
            )
        }

        if (showDetails) {
            EnhancedTodoDisplay(
                todos = toolDetail.todos,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
