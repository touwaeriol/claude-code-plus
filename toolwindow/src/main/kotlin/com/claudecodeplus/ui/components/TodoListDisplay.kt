package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.ui.viewmodels.tool.TodoWriteToolDetail
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*

/**
 * TodoWrite 工具的专属展示组件
 * 使用看板式设计展示待办事项
 */
@Composable
fun TodoListDisplay(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    val detail = toolCall.viewModel?.toolDetail as? TodoWriteToolDetail
    val todos = remember(toolCall) {
        detail?.todos.orEmpty().mapIndexed { index, todo ->
            TodoItem(
                id = (index + 1).toString(),
                content = todo.content,
                status = TodoStatus.from(todo.status),
                priority = TodoPriority.LOW,
                activeForm = todo.activeForm
            )
        }
    }
    
    // 计算统计信息
    val stats = remember(todos) {
        TodoStats(
            total = todos.size,
            completed = todos.count { it.status == TodoStatus.COMPLETED },
            inProgress = todos.count { it.status == TodoStatus.IN_PROGRESS },
            pending = todos.count { it.status == TodoStatus.PENDING }
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题栏
        TodoListHeader(stats)
        
        // 待办事项列表
        if (todos.isNotEmpty()) {
            TodoItemsList(todos)
        } else {
            EmptyTodoList()
        }
        
        // 结果信息（如果有）
        toolCall.result?.let { result ->
            TodoResultInfo(result)
        }
    }
}

/**
 * 待办列表头部
 */
@Composable
private fun TodoListHeader(stats: TodoStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 标题和图标
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp)
            )
            Text(
                text = "任务列表",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        
        // 统计信息
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 完成进度
            if (stats.total > 0) {
                val progress = stats.completed.toFloat() / stats.total
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stats.completed}/${stats.total}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                        )
                    )
                    ProgressBar(
                        progress = progress,
                        modifier = Modifier.width(60.dp).height(4.dp)
                    )
                }
            }
            
            // 状态统计
            TodoStatusBadge("✅", stats.completed, Color(0xFF4CAF50))
            TodoStatusBadge("▶️", stats.inProgress, Color(0xFF2196F3))
            TodoStatusBadge("⏸️", stats.pending, Color(0xFF9E9E9E))
        }
    }
}

/**
 * 待办事项列表
 */
@Composable
private fun TodoItemsList(todos: List<TodoItem>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        todos.forEach { todo ->
            TodoItemCard(todo)
        }
    }
}

/**
 * 单个待办事项卡片
 */
@Composable
private fun TodoItemCard(todo: TodoItem) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // 根据优先级确定边框颜色
    val borderColor = when (todo.priority) {
        TodoPriority.HIGH -> Color(0xFFFF5252)
        TodoPriority.MEDIUM -> Color(0xFFFFC107)
        TodoPriority.LOW -> JewelTheme.globalColors.borders.normal
    }
    
    // 背景色动画
    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) {
            JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f)
        } else {
            JewelTheme.globalColors.panelBackground.copy(alpha = 0.1f)
        },
        animationSpec = tween(150)
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = if (isHovered) 0.6f else 0.3f),
                shape = RoundedCornerShape(4.dp)
            )
            .hoverable(interactionSource)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态图标
        TodoStatusIcon(todo.status)
        
        // 任务内容
        Text(
            text = todo.content,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 13.sp,
                textDecoration = if (todo.status == TodoStatus.COMPLETED) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
                color = if (todo.status == TodoStatus.COMPLETED) {
                    JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                } else {
                    JewelTheme.globalColors.text.normal
                }
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        // 优先级标记
        if (todo.priority != TodoPriority.LOW) {
            PriorityIndicator(todo.priority)
        }
    }
}

/**
 * 待办状态图标
 */
@Composable
private fun TodoStatusIcon(status: TodoStatus) {
    when (status) {
        TodoStatus.PENDING -> {
            Text(
                text = "⏸️",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
            )
        }
        TodoStatus.IN_PROGRESS -> {
            // 使用动画效果表示进行中
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "▶️",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
                )
                // 添加脉冲效果
                PulsingDot(
                    size = 4.dp,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.offset(x = 8.dp, y = (-4).dp)
                )
            }
        }
        TodoStatus.COMPLETED -> {
            Text(
                text = "✅",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
            )
        }
    }
}

/**
 * 优先级指示器
 */
@Composable
private fun PriorityIndicator(priority: TodoPriority) {
    val (text, color) = when (priority) {
        TodoPriority.HIGH -> "高" to Color(0xFFFF5252)
        TodoPriority.MEDIUM -> "中" to Color(0xFFFFC107)
        TodoPriority.LOW -> "低" to Color(0xFF9E9E9E)
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 10.sp,
                color = color
            )
        )
    }
}

/**
 * 状态徽章
 */
@Composable
private fun TodoStatusBadge(
    icon: String,
    count: Int,
    color: Color
) {
    if (count > 0) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            Text(
                text = count.toString(),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

/**
 * 进度条
 */
@Composable
private fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF4CAF50))
        )
    }
}

/**
 * 空列表提示
 */
@Composable
private fun EmptyTodoList() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无待办事项",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 13.sp,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * 结果信息
 */
@Composable
private fun TodoResultInfo(result: ToolResult) {
    Divider(
        orientation = org.jetbrains.jewel.ui.Orientation.Horizontal,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    
    when (result) {
        is ToolResult.Success -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuccessCheckmark(size = 14.dp)
                Text(
                    text = "待办事项已更新",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50)
                    )
                )
            }
        }
        is ToolResult.Failure -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ErrorCross(size = 14.dp)
                Text(
                    text = result.error,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = Color(0xFFFF6B6B)
                    )
                )
            }
        }
        else -> {}
    }
}

/**
 * 解析待办事项数据
 */
private data class TodoItem(
    val id: String,
    val content: String,
    val status: TodoStatus,
    val priority: TodoPriority,
    val activeForm: String
)

private enum class TodoStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED;

    companion object {
        fun from(raw: String): TodoStatus = when (raw.lowercase()) {
            "completed" -> COMPLETED
            "in_progress" -> IN_PROGRESS
            else -> PENDING
        }
    }
}

private enum class TodoPriority {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * 统计信息
 */
private data class TodoStats(
    val total: Int,
    val completed: Int,
    val inProgress: Int,
    val pending: Int
)
