package com.claudecodeplus.ui.jewel.components.tools

import com.claudecodeplus.core.logging.*
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
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.sdk.types.TodoWriteToolUse
import org.jetbrains.jewel.foundation.theme.JewelTheme
import com.claudecodeplus.ui.jewel.components.tools.LinearProgressBar
import org.jetbrains.jewel.ui.component.Text
import kotlinx.serialization.json.*
import com.claudecodeplus.ui.services.stringResource
import com.claudecodeplus.ui.services.formatStringResource
import com.claudecodeplus.ui.services.StringResources

/**
 * 增强的 TodoWrite 看板式展示组件
 */
@Composable
fun EnhancedTodoDisplay(
    toolCall: ToolCall? = null,
    todos: List<TodoWriteToolUse.TodoItem>? = null,
    modifier: Modifier = Modifier
) {
    val todoItems = when {
        todos != null -> {
            // 直接使用传入的强类型数据
            todos.mapIndexed { index, todo ->
                EnhancedTodoItem(
                    id = (index + 1).toString(),
                    content = todo.content,
                    status = todo.status,
                    activeForm = todo.activeForm,
                    priority = "normal"
                )
            }
        }
        toolCall != null -> parseTodos(toolCall)  // 兼容旧的解析方式
        else -> emptyList()
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 任务统计和进度条
        TodoSummaryHeader(todoItems)

        // 单列任务列表（默认展开）
        TodoListView(todoItems)
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
        // 任务统计行（去掉重复的标题）
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatStringResource(StringResources.TASK_COMPLETED_COUNT, completedCount, totalCount),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            )
        }
        
        // 进度条
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressBar(
                progress = progress,
                modifier = Modifier.weight(1f).height(6.dp)
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
    // 按任务ID的数字部分排序，保持创建顺序 (1 -> 2 -> 3 -> 4)
    val sortedTodos = todos.sortedBy { 
        it.id.toIntOrNull() ?: 0 
    }
    
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
                text = stringResource(StringResources.NO_TASKS),
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
                when (todo.status.lowercase()) {
                    "completed" -> Color(0xFF4CAF50).copy(alpha = 0.1f)  // 绿色背景 - 已完成
                    "in_progress" -> Color(0xFF2196F3).copy(alpha = 0.15f)  // 蓝色背景 - 进行中 
                    "pending" -> Color(0xFFFFC107).copy(alpha = 0.1f)  // 黄色背景 - 待办
                    else -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f)
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态指示器（带动画效果）
        val animatedScale by animateFloatAsState(
            targetValue = if (todo.status.lowercase() == "in_progress") 1.2f else 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "statusIndicatorScale"
        )
        
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(if (todo.status.lowercase() == "in_progress") animatedScale else 1.0f)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    when (todo.status.lowercase()) {
                        "completed" -> Color(0xFF4CAF50)  // 绿色 - 已完成
                        "in_progress" -> Color(0xFF2196F3)  // 蓝色 - 进行中
                        "pending" -> Color(0xFFFFC107)  // 黄色 - 待办
                        else -> JewelTheme.globalColors.borders.normal
                    }
                )
        )
        
        // 状态图标
        Text(
            text = when (todo.status.lowercase()) {
                "completed" -> "✅"
                "in_progress" -> "🔄"
                "pending" -> "⏳"
                else -> "❓"
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
        
        // 状态标签
        Text(
            text = when (todo.status.lowercase()) {
                "completed" -> stringResource(StringResources.TASK_STATUS_COMPLETED)
                "in_progress" -> stringResource(StringResources.TASK_STATUS_IN_PROGRESS)
                "pending" -> stringResource(StringResources.TASK_STATUS_PENDING)
                else -> "Unknown"
            },
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 10.sp,
                color = when (todo.status.lowercase()) {
                    "completed" -> Color(0xFF4CAF50)
                    "in_progress" -> Color(0xFF2196F3)
                    "pending" -> Color(0xFFFFC107)
                    else -> JewelTheme.globalColors.text.normal
                },
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when (todo.status.lowercase()) {
                        "completed" -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        "in_progress" -> Color(0xFF2196F3).copy(alpha = 0.1f)
                        "pending" -> Color(0xFFFFC107).copy(alpha = 0.1f)
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}


/**
 * 任务数据模型
 */
private data class EnhancedTodoItem(
    val id: String,
    val content: String,
    val status: String,
    val activeForm: String,
    val priority: String = "normal"
)

/**
 * 解析任务列表
 */
private fun parseTodos(toolCall: ToolCall): List<EnhancedTodoItem> {
    //     logD("[EnhancedTodoDisplay] 开始解析TodoWrite工具调用")
    //     logD("[EnhancedTodoDisplay] 工具名称：${toolCall.name}")

    // 检查是否有todos参数
    val todosParam = toolCall.parameters["todos"]
    if (todosParam == null) {
    //         logD("[EnhancedTodoDisplay] ❌ 未找到todos参数")
        return emptyList()
    }
    
    return when (todosParam) {
        is List<*> -> {
    //             logD("[EnhancedTodoDisplay] 解析List类型，共${todosParam.size}个项目")
            todosParam.mapIndexed { index, item ->
    //                 logD("[EnhancedTodoDisplay] 处理第${index}个项目，类型：${item?.javaClass}")
                when (item) {
                    is Map<*, *> -> {
                        // 从Map中提取字段，兼容不同的数据格式
                        val id = item["id"]?.toString() ?: (index + 1).toString()
                        val content = item["content"]?.toString() ?: ""
                        val status = item["status"]?.toString() ?: "pending"
                        // activeForm是可选字段，如果不存在则使用content作为默认值
                        val activeForm = item["activeForm"]?.toString() ?: content
                        // priority字段也是可选的
                        val priority = item["priority"]?.toString() ?: "normal"

    //                         logD("[EnhancedTodoDisplay] ✅ Map项目 $index: id='$id', content='$content', status='$status', activeForm='$activeForm', priority='$priority'")

                        EnhancedTodoItem(
                            id = id,
                            content = content,
                            status = status,
                            activeForm = activeForm,
                            priority = priority
                        )
                    }
                    else -> {
                        // 如果是字符串形式，尝试解析为简单任务
                        val content = item.toString()
    //                         logD("[EnhancedTodoDisplay] ⚠️ 简单项目 $index: '$content'")
                        
                        EnhancedTodoItem(
                            id = (index + 1).toString(),
                            content = content,
                            status = "pending",
                            activeForm = content,
                            priority = "normal"
                        )
                    }
                }
            }
        }
        is String -> {
    //             logD("[EnhancedTodoDisplay] 解析JSON字符串类型")
            try {
                // 使用 kotlinx.serialization.json 解析JSON字符串
                val json = Json { ignoreUnknownKeys = true }
                val todoList = json.decodeFromString<List<JsonObject>>(todosParam)
                
    //                 logD("[EnhancedTodoDisplay] ✅ JSON解析成功，共${todoList.size}个项目")
                
                todoList.mapIndexed { index, jsonObj ->
    //                     logD("[EnhancedTodoDisplay] 处理第${index}个JSON项目")
                    
                    val content = jsonObj["content"]?.jsonPrimitive?.content ?: ""
                    val status = jsonObj["status"]?.jsonPrimitive?.content ?: "pending"
                    val activeForm = jsonObj["activeForm"]?.jsonPrimitive?.content ?: content
                    
    //                     logD("[EnhancedTodoDisplay] ✅ JSON项目 $index: content='$content', status='$status', activeForm='$activeForm'")
                    
                    EnhancedTodoItem(
                        id = (index + 1).toString(),
                        content = content,
                        status = status,
                        activeForm = activeForm,
                        priority = "normal"
                    )
                }
            } catch (e: Exception) {
    //                 logD("[EnhancedTodoDisplay] ❌ JSON解析失败: ${e.message}")
                emptyList()
            }
        }
        else -> {
    //             logD("[EnhancedTodoDisplay] ❌ 未知的todos参数类型：${todosParam::class}")
            emptyList()
        }
    }
}
