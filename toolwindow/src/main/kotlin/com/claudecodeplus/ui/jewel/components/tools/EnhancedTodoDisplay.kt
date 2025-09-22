package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.sdk.types.TodoWriteToolUse
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.services.StringResources
import com.claudecodeplus.ui.services.formatStringResource
import com.claudecodeplus.ui.services.stringResource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun EnhancedTodoDisplay(
    toolCall: ToolCall? = null,
    todos: List<TodoWriteToolUse.TodoItem>? = null,
    modifier: Modifier = Modifier
) {
    val todoItems = when {
        todos != null -> todos.mapIndexed { index, todo ->
            EnhancedTodoItem(
                id = (index + 1).toString(),
                content = todo.content,
                status = todo.status,
                activeForm = todo.activeForm ?: todo.content
            )
        }
        toolCall != null -> parseTodos(toolCall)
        else -> emptyList()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TodoSummary(todoItems)
        TodoList(todoItems)
    }
}

@Composable
private fun TodoSummary(todos: List<EnhancedTodoItem>) {
    val completed = todos.count { it.status.equals("completed", ignoreCase = true) }
    val total = todos.size

    if (total == 0) {
        Text(
            text = stringResource(StringResources.NO_TASKS),
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f),
                fontStyle = FontStyle.Italic
            )
        )
        return
    }

    Text(
        text = formatStringResource(StringResources.TASK_COMPLETED_COUNT, completed, total),
        style = JewelTheme.defaultTextStyle.copy(
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
        )
    )
}

@Composable
private fun TodoList(todos: List<EnhancedTodoItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.35f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        todos.sortedBy { it.id.toIntOrNull() ?: 0 }.forEach { todo ->
            TodoListItem(todo)
        }
    }
}

@Composable
private fun TodoListItem(todo: EnhancedTodoItem) {
    val statusColor = when (todo.status.lowercase()) {
        "completed" -> Color(0xFF4CAF50)
        "in_progress" -> Color(0xFF2196F3)
        "pending" -> Color(0xFFFFC107)
        else -> JewelTheme.globalColors.borders.normal
    }

    val statusLabel = when (todo.status.lowercase()) {
        "completed" -> "完成"
        "in_progress" -> "进行中"
        "pending" -> "待处理"
        else -> todo.status
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (todo.status.equals("in_progress", ignoreCase = true)) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "todoStatusPulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(animatedScale)
                .clip(RoundedCornerShape(50))
                .background(statusColor)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = todo.content,
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            if (!todo.activeForm.equals(todo.content, ignoreCase = true)) {
                Text(
                    text = todo.activeForm,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.65f)
                    )
                )
            }
        }

        Text(
            text = statusLabel,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                color = statusColor.copy(alpha = 0.9f)
            )
        )
    }
}

private data class EnhancedTodoItem(
    val id: String,
    val content: String,
    val status: String,
    val activeForm: String
)

private fun parseTodos(toolCall: ToolCall): List<EnhancedTodoItem> {
    val todosParam = toolCall.parameters["todos"] ?: return emptyList()

    return when (todosParam) {
        is List<*> -> todosParam.mapIndexedNotNull { index, item ->
            when (item) {
                is Map<*, *> -> {
                    EnhancedTodoItem(
                        id = item["id"]?.toString() ?: (index + 1).toString(),
                        content = item["content"]?.toString() ?: "",
                        status = item["status"]?.toString() ?: "pending",
                        activeForm = item["activeForm"]?.toString() ?: item["content"]?.toString()
                            ?: ""
                    )
                }
                else -> EnhancedTodoItem(
                    id = (index + 1).toString(),
                    content = item.toString(),
                    status = "pending",
                    activeForm = item.toString()
                )
            }
        }
        is String -> runCatching {
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<List<JsonObject>>(todosParam).mapIndexed { index, jsonObj ->
                EnhancedTodoItem(
                    id = (index + 1).toString(),
                    content = jsonObj["content"]?.jsonPrimitive?.content ?: "",
                    status = jsonObj["status"]?.jsonPrimitive?.content ?: "pending",
                    activeForm = jsonObj["activeForm"]?.jsonPrimitive?.content
                        ?: jsonObj["content"]?.jsonPrimitive?.content ?: ""
                )
            }
        }.getOrElse { emptyList() }
        else -> emptyList()
    }
}
