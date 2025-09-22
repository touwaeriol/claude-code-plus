package com.claudecodeplus.ui.jewel.components.tools.individual

import com.claudecodeplus.core.logging.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.sdk.types.TodoWriteToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.EnhancedTodoDisplay

/**
 * TodoWrite工具专用展示组件
 *
 * ?? 职责：专门处理TodoWrite工具的展示
 * ?? 解决的核心问题：直接显示input.todos而不是result.content
 */
@Composable
fun TodoWriteDisplay(
    toolCall: ToolCall,
    todoWriteTool: TodoWriteToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 只在非详情模式下显示工具头部信息（避免展开时重复）
        if (!showDetails) {
            ToolHeaderDisplay(
                icon = "TODO",
                toolName = "TodoWrite",
                subtitle = "${todoWriteTool.todos.size}个任务",
                status = toolCall.status
            )
        }

        // ?? 核心改进：直接显示input中的todos，完全忽略result
        if (showDetails) {
            logD("[TodoWriteDisplay] ?? 显示input.todos，任务数量：${todoWriteTool.todos.size}")

            // 使用现有的EnhancedTodoDisplay，传入强类型的todos
            EnhancedTodoDisplay(
                todos = todoWriteTool.todos,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
