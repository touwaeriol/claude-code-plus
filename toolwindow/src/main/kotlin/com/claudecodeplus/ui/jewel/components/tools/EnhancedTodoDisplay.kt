package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import org.jetbrains.jewel.foundation.theme.JewelTheme
import androidx.compose.material.LinearProgressIndicator
import org.jetbrains.jewel.ui.component.Text

/**
 * 增强的 TodoWrite 看板式展示组件
 */
@Composable
fun EnhancedTodoDisplay(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    val todos = parseTodos(toolCall)
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 任务统计和进度条
        TodoSummaryHeader(todos)
        
        // 单列任务列表（默认展开）
        TodoListView(todos)
    }
}

/**
 * 任务统计头部
 */
@Composable
private fun TodoSummaryHeader(
    todos: List<EnhancedTodoItem>
) {
    val completedCount = todos.count { it.status == "completed" }
    val totalCount = todos.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 标题行
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp)
            )
            Text(
                text = "任务管理",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            )
            Text(
                text = "($completedCount/$totalCount 完成)",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                )
            )
        }
        
        // 进度条
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.weight(1f).height(6.dp),
                color = JewelTheme.globalColors.text.info,
                backgroundColor = JewelTheme.globalColors.borders.normal
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                )
            )
        }
    }
}

/**
 * 单列任务列表视图
 */
@Composable
private fun TodoListView(todos: List<EnhancedTodoItem>) {
    // 按优先级排序：高 -> 中 -> 低，同优先级按状态排序
    val sortedTodos = todos.sortedWith(compareBy(
        { when(it.priority) {
            "high" -> 0
            "medium" -> 1
            "low" -> 2
            else -> 3
        }},
        { when(it.status) {
            "in_progress" -> 0
            "pending" -> 1
            "completed" -> 2
            else -> 3
        }}
    ))
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (sortedTodos.isEmpty()) {
            Text(
                text = "暂无任务",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            sortedTodos.forEach { todo ->
                TodoListItem(todo)
            }
        }
    }
}

/**
 * 单个任务列表项
 */
@Composable
private fun TodoListItem(todo: EnhancedTodoItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(
                when (todo.priority) {
                    "high" -> Color(0xFFFF6B6B).copy(alpha = 0.1f)
                    "medium" -> Color(0xFFFFD93D).copy(alpha = 0.1f)
                    else -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f)
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 优先级指示器
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    when (todo.priority) {
                        "high" -> Color(0xFFFF6B6B)
                        "medium" -> Color(0xFFFFD93D)
                        else -> JewelTheme.globalColors.borders.normal
                    }
                )
        )
        
        // 状态图标
        Text(
            text = when (todo.status) {
                "completed" -> "✅"
                "in_progress" -> "▶️"
                else -> "⏸️"
            },
            style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
        )
        
        // 任务内容
        Text(
            text = todo.content,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 13.sp,
                color = if (todo.status == "completed") {
                    JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                } else {
                    JewelTheme.globalColors.text.normal
                },
                textDecoration = if (todo.status == "completed") {
                    androidx.compose.ui.text.style.TextDecoration.LineThrough
                } else {
                    androidx.compose.ui.text.style.TextDecoration.None
                }
            ),
            modifier = Modifier.weight(1f)
        )
        
        // 优先级标签（仅在中高优先级时显示）
        if (todo.priority != "low") {
            Text(
                text = when (todo.priority) {
                    "high" -> "高"
                    "medium" -> "中"
                    else -> ""
                },
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = when (todo.priority) {
                        "high" -> Color(0xFFFF6B6B)
                        "medium" -> Color(0xFFFFD93D)
                        else -> JewelTheme.globalColors.text.normal
                    },
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when (todo.priority) {
                            "high" -> Color(0xFFFF6B6B).copy(alpha = 0.1f)
                            "medium" -> Color(0xFFFFD93D).copy(alpha = 0.1f)
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}


/**
 * 任务数据模型
 */
private data class EnhancedTodoItem(
    val id: String,
    val content: String,
    val status: String,
    val priority: String
)

/**
 * 解析任务列表
 */
private fun parseTodos(toolCall: ToolCall): List<EnhancedTodoItem> {
    val todosParam = toolCall.parameters["todos"] ?: return emptyList()
    
    if (todosParam is List<*>) {
        return todosParam.mapNotNull { item ->
            if (item is Map<*, *>) {
                EnhancedTodoItem(
                    id = item["id"]?.toString() ?: "",
                    content = item["content"]?.toString() ?: "",
                    status = item["status"]?.toString() ?: "pending",
                    priority = item["priority"]?.toString() ?: "medium"
                )
            } else {
                null
            }
        }
    }
    
    return emptyList()
}