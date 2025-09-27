package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.sdk.types.TodoWriteToolUse
import com.claudecodeplus.ui.jewel.components.tools.EnhancedTodoDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.services.StringResources
import com.claudecodeplus.ui.services.formatStringResource

/**
 * TodoWrite 工具展示（V2：国际化进度 + 无原始 todos 参数回显）
 */
@Composable
fun TodoWriteDisplayV2(
    toolCall: ToolCall,
    todoWriteTool: TodoWriteToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!showDetails) {
            val total = todoWriteTool.todos.size
            ToolHeaderDisplay(
                icon = "TODO",
                toolName = "TodoWrite",
                subtitle = formatStringResource(StringResources.UPDATE_TASKS, total),
                status = toolCall.status
            )
        }

        if (showDetails) {
            EnhancedTodoDisplay(
                todos = todoWriteTool.todos,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

