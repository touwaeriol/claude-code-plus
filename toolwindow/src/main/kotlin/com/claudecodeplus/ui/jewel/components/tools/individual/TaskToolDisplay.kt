package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.sdk.types.TaskToolUse
import com.claudecodeplus.sdk.types.TodoWriteToolUse
import com.claudecodeplus.ui.jewel.components.tools.EnhancedTodoDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import com.claudecodeplus.ui.models.ToolCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * Task 工具专用展示组件
 */
@Composable
fun TaskToolDisplay(
    toolCall: ToolCall,
    taskTool: TaskToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!showDetails) {
            val summary = taskTool.description.ifBlank { taskTool.subagentType }
            ToolHeaderDisplay(
                icon = "TASK",
                toolName = "Task",
                subtitle = summary,
                status = toolCall.status
            )
        }

        if (showDetails) {
            Text(
                text = "描述：${taskTool.description}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = "子代理：${taskTool.subagentType}",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
            )

            if (taskTool.prompt.isNotBlank()) {
                Text(
                    text = "提示词：",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                )
                SelectionContainer {
                    Text(
                        text = taskTool.prompt,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                        )
                    )
                }
            }

            val todos = extractTaskTodos(taskTool, toolCall.parameters)
            if (todos.isNotEmpty()) {
                EnhancedTodoDisplay(
                    todos = todos,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "此任务未提供待办列表",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                    )
                )
            }

            toolCall.result?.let { result ->
                ToolResultDisplay(result)
            }
        }
    }
}

private fun extractTaskTodos(
    taskTool: TaskToolUse,
    parameters: Map<String, Any>
): List<TodoWriteToolUse.TodoItem> {
    convertAnyToTodos(parameters["todos"])?.let { return it }
    convertAnyToTodos(parameters["tasks"])?.let { return it }

    val fromOriginal = runCatching {
        convertJsonArrayToTodos(taskTool.originalParameters.jsonObject["todos"])
    }.getOrNull()

    if (!fromOriginal.isNullOrEmpty()) {
        return fromOriginal
    }

    return emptyList()
}

private fun convertAnyToTodos(source: Any?): List<TodoWriteToolUse.TodoItem>? {
    return when (source) {
        null -> null
        is List<*> -> source.mapNotNull { convertMapToTodo(it) }.takeIf { it.isNotEmpty() }
        is JsonElement -> convertJsonArrayToTodos(source)
        else -> null
    }
}

private fun convertJsonArrayToTodos(element: JsonElement?): List<TodoWriteToolUse.TodoItem>? {
    val array = when (element) {
        is JsonArray -> element
        is JsonObject -> element["todos"]?.jsonArray
        else -> element?.jsonArray
    } ?: return null

    val todos = array.mapNotNull { convertJsonToTodo(it) }
    return todos.takeIf { it.isNotEmpty() }
}

private fun convertJsonToTodo(element: JsonElement): TodoWriteToolUse.TodoItem? {
    if (element === JsonNull) return null
    val obj = element as? JsonObject ?: return null
    val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: return null
    val status = obj["status"]?.jsonPrimitive?.contentOrNull
        ?: obj["state"]?.jsonPrimitive?.contentOrNull
        ?: "pending"
    val activeForm = obj["activeForm"]?.jsonPrimitive?.contentOrNull
        ?: obj["active_form"]?.jsonPrimitive?.contentOrNull
        ?: status
    return TodoWriteToolUse.TodoItem(
        content = content,
        status = status,
        activeForm = activeForm
    )
}

private fun convertMapToTodo(item: Any?): TodoWriteToolUse.TodoItem? {
    val data = item as? Map<*, *> ?: return null
    val content = data["content"]?.toString() ?: return null
    val status = (data["status"] ?: data["state"])?.toString() ?: "pending"
    val activeRaw = data["activeForm"] ?: data["active_form"] ?: data["status"]
    val activeForm = activeRaw?.toString()?.takeIf { it.isNotBlank() } ?: status

    return TodoWriteToolUse.TodoItem(
        content = content,
        status = status,
        activeForm = activeForm
    )
}

