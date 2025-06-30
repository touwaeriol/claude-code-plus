package com.claudecodeplus.ui.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.jewel.components.*
import com.claudecodeplus.ui.models.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import androidx.compose.material.LocalContentColor
import java.text.SimpleDateFormat
import java.util.*

/**
 * 重新设计的对话视图
 * 集成了模型选择、Markdown 渲染、工具调用显示等功能
 */
@Composable
fun JewelConversationView(
    messages: List<EnhancedMessage>,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: (() -> Unit)? = null,
    contexts: List<ContextReference> = emptyList(),
    onContextAdd: (ContextReference) -> Unit = {},
    onContextRemove: (ContextReference) -> Unit = {},
    isGenerating: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 消息列表区域 - 占据剩余空间
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
            }
        }
        
        // 分隔线
        Divider(
            orientation = org.jetbrains.jewel.ui.Orientation.Horizontal,
            modifier = Modifier.height(1.dp),
            color = JewelTheme.globalColors.borders.normal
        )
        
        // 输入区域 - 使用 EnhancedSmartInputArea
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.panelBackground)
                .padding(8.dp)
        ) {
            EnhancedSmartInputArea(
                text = inputText,
                onTextChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
                contexts = contexts,
                onContextAdd = onContextAdd,
                onContextRemove = onContextRemove,
                isGenerating = isGenerating,
                enabled = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 消息气泡组件
 */
@Composable
private fun MessageBubble(
    message: EnhancedMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (message.role == MessageRole.USER) Arrangement.End else Arrangement.Start
    ) {
        if (message.role != MessageRole.USER) {
            // Assistant消息 - 左对齐，占据更多空间
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(
                        JewelTheme.globalColors.panelBackground,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 按时间顺序显示消息元素
                    if (message.orderedElements.isNotEmpty()) {
                        message.orderedElements.forEach { element ->
                            when (element) {
                                is MessageTimelineItem.ToolCallItem -> {
                                    SimpleToolCallDisplay(element.toolCall)
                                }
                                is MessageTimelineItem.ContentItem -> {
                                    if (element.content.isNotBlank()) {
                                        MarkdownRenderer(
                                            markdown = element.content,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                is MessageTimelineItem.StatusItem -> {
                                    if (element.isStreaming) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                "▌",
                                                style = JewelTheme.defaultTextStyle.copy(
                                                    color = Color(0xFF59A869)
                                                )
                                            )
                                            Text(
                                                element.status,
                                                style = JewelTheme.defaultTextStyle.copy(
                                                    color = JewelTheme.globalColors.text.disabled,
                                                    fontSize = 12.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 向后兼容：如果没有orderedElements，回退到原来的显示方式
                        // 先显示工具调用（按照添加顺序）
                        message.toolCalls.forEach { toolCall ->
                            SimpleToolCallDisplay(toolCall)
                        }
                        
                        // 然后显示消息内容
                        if (message.content.isNotBlank()) {
                            MarkdownRenderer(
                                markdown = message.content,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    // 流式状态指示器
                    if (message.isStreaming) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "▌",
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = Color(0xFF59A869)
                                )
                            )
                            Text(
                                "正在生成...",
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = JewelTheme.globalColors.text.disabled,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                    
                    // 时间戳
                    Text(
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp)),
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        } else {
            // User消息 - 右对齐，较小宽度
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .background(
                        JewelTheme.globalColors.borders.focused,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MarkdownRenderer(
                        markdown = message.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // 时间戳
                    Text(
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp)),
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * 简洁的工具调用显示 - 类似 Cursor 的样式
 */
@Composable
private fun SimpleToolCallDisplay(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 工具图标
        Text(
            when {
                toolCall.name.contains("LS", ignoreCase = true) -> "📁"
                toolCall.name.contains("Read", ignoreCase = true) -> "📖"
                toolCall.name.contains("Edit", ignoreCase = true) -> "✏️"
                toolCall.name.contains("Write", ignoreCase = true) -> "📝"
                toolCall.name.contains("Bash", ignoreCase = true) -> "💻"
                toolCall.name.contains("Search", ignoreCase = true) -> "🔍"
                toolCall.name.contains("Grep", ignoreCase = true) -> "🔍"
                toolCall.name.contains("Web", ignoreCase = true) -> "🌐"
                toolCall.name.contains("Git", ignoreCase = true) -> "🔀"
                else -> "🔧"
            },
            style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
        )
        
        // 工具调用描述
        Text(
            buildToolCallDescription(toolCall),
            style = JewelTheme.defaultTextStyle.copy(
                color = JewelTheme.globalColors.text.disabled,
                fontSize = 12.sp
            )
        )
        
        // 状态指示
        when (toolCall.status) {
            ToolCallStatus.RUNNING -> {
                Text(
                    "...",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFF3574F0),
                        fontSize = 12.sp
                    )
                )
            }
            ToolCallStatus.SUCCESS -> {
                // 成功时不显示额外指示
            }
            ToolCallStatus.FAILED -> {
                Text(
                    "失败",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFFE55765),
                        fontSize = 12.sp
                    )
                )
            }
            else -> {}
        }
    }
}

/**
 * 构建工具调用的简洁描述
 */
private fun buildToolCallDescription(toolCall: ToolCall): String {
    return when (toolCall.name) {
        "LS" -> {
            val path = toolCall.parameters["path"]?.toString() ?: "."
            "列出文件 $path"
        }
        "Read" -> {
            val path = toolCall.parameters["path"]?.toString() ?: 
                     toolCall.parameters["target_file"]?.toString() ?: "文件"
            "读取 ${path.substringAfterLast('/')}"
        }
        "Edit" -> {
            val path = toolCall.parameters["path"]?.toString() ?: 
                     toolCall.parameters["target_file"]?.toString() ?: "文件"
            "编辑 ${path.substringAfterLast('/')}"
        }
        "Write" -> {
            val path = toolCall.parameters["path"]?.toString() ?: 
                     toolCall.parameters["target_file"]?.toString() ?: "文件"
            "写入 ${path.substringAfterLast('/')}"
        }
        "Bash" -> {
            val command = toolCall.parameters["command"]?.toString()?.take(30) ?: "命令"
            "执行 $command${if (command.length > 30) "..." else ""}"
        }
        "Grep" -> {
            val query = toolCall.parameters["query"]?.toString()?.take(20) ?: "搜索"
            "搜索 \"$query${if (query.length > 20) "..." else ""}\""
        }
        "WebSearch" -> {
            val query = toolCall.parameters["search_term"]?.toString()?.take(20) ?: "搜索"
            "网络搜索 \"$query${if (query.length > 20) "..." else ""}\""
        }
        else -> toolCall.name
    }
}




