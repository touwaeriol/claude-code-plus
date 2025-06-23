package com.claudecodeplus.ui.redesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 用户消息项
 */
@Composable
fun UserMessageItem(message: EnhancedMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF3574F0).copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        // 头部
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "👤",
                style = JewelTheme.defaultTextStyle
            )
            Text(
                "你",
                style = JewelTheme.defaultTextStyle.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3574F0)
                )
            )
            message.model?.let { model ->
                Text(
                    "→ $model",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFF999999),
                        fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                    )
                )
            }
        }
        
        // 内容
        Text(
            message.content,
            style = JewelTheme.defaultTextStyle.copy(
                color = Color(0xFFBBBBBB)
            ),
            modifier = Modifier.padding(top = 8.dp)
        )
        
        // 上下文引用
        if (message.contexts.isNotEmpty()) {
            ContextReferences(
                contexts = message.contexts,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * 助手消息项
 */
@Composable
fun AssistantMessageItem(message: EnhancedMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF3C3F41),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        // 头部
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "🤖",
                style = JewelTheme.defaultTextStyle
            )
            Text(
                "Claude",
                style = JewelTheme.defaultTextStyle.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF59A869)
                )
            )
            message.model?.let { model ->
                Text(
                    "($model)",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFF999999),
                        fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                    )
                )
            }
            
            // 流式状态指示器
            if (message.isStreaming) {
                Text(
                    "▌",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFF59A869)
                    )
                )
            } else if (message.isError) {
                Text(
                    "❌",
                    style = JewelTheme.defaultTextStyle
                )
            }
        }
        
        // 工具调用
        if (message.toolCalls.isNotEmpty()) {
            ToolCallsDisplay(
                toolCalls = message.toolCalls,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        // 内容
        Text(
            message.content,
            style = JewelTheme.defaultTextStyle.copy(
                color = Color(0xFFBBBBBB)
            ),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * 系统消息项
 */
@Composable
fun SystemMessageItem(message: EnhancedMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF2B2B2B),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            message.content,
            style = JewelTheme.defaultTextStyle.copy(
                color = Color(0xFF999999)
            )
        )
    }
}

/**
 * 错误消息项
 */
@Composable
fun ErrorMessageItem(message: EnhancedMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFFE55765).copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                Color(0xFFE55765).copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "⚠️",
                style = JewelTheme.defaultTextStyle
            )
            Text(
                message.content,
                style = JewelTheme.defaultTextStyle.copy(
                    color = Color(0xFFE55765)
                )
            )
        }
    }
}

/**
 * 上下文引用显示
 */
@Composable
private fun ContextReferences(
    contexts: List<ContextReference>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        contexts.forEach { context ->
            Row(
                modifier = Modifier
                    .background(
                        Color(0xFF3574F0).copy(alpha = 0.05f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (context) {
                    is ContextReference.FileReference -> {
                        Text("📄", style = JewelTheme.defaultTextStyle)
                        Text(
                            context.path,
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFF3574F0),
                                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                            )
                        )
                        context.lines?.let {
                            Text(
                                ":${it.first}-${it.last}",
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = Color(0xFF999999),
                                    fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                                )
                            )
                        }
                    }
                    is ContextReference.SymbolReference -> {
                        Text("🔷", style = JewelTheme.defaultTextStyle)
                        Text(
                            "${context.name} (${context.type})",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFF3574F0),
                                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                            )
                        )
                    }
                    is ContextReference.TerminalReference -> {
                        Text("💻", style = JewelTheme.defaultTextStyle)
                        Text(
                            "终端输出",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFF3574F0),
                                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                            )
                        )
                    }
                    is ContextReference.ProblemsReference -> {
                        Text("⚠️", style = JewelTheme.defaultTextStyle)
                        Text(
                            "问题列表",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFFE55765),
                                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                            )
                        )
                    }
                    is ContextReference.GitReference -> {
                        Text("🔀", style = JewelTheme.defaultTextStyle)
                        Text(
                            "Git ${context.type}",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFF3574F0),
                                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                            )
                        )
                    }
                    is ContextReference.FolderReference -> {
                        Text("📁", style = JewelTheme.defaultTextStyle)
                        Text(
                            context.path,
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFF3574F0),
                                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                            )
                        )
                    }
                    ContextReference.SelectionReference -> {
                        Text("✂️", style = JewelTheme.defaultTextStyle)
                        Text(
                            "选中内容",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFF3574F0),
                                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                            )
                        )
                    }
                    ContextReference.WorkspaceReference -> {
                        Text("🗂️", style = JewelTheme.defaultTextStyle)
                        Text(
                            "工作空间",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFF3574F0),
                                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 工具调用显示
 */
@Composable
private fun ToolCallsDisplay(
    toolCalls: List<ToolCall>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color(0xFF2B2B2B),
                RoundedCornerShape(4.dp)
            )
            .clickable { expanded = !expanded }
            .padding(8.dp)
    ) {
        // 标题栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "🔧",
                    style = JewelTheme.defaultTextStyle
                )
                Text(
                    "工具调用 (${toolCalls.size})",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFF999999)
                    )
                )
            }
            
            Text(
                if (expanded) "▼" else "▶",
                style = JewelTheme.defaultTextStyle.copy(
                    color = Color(0xFF999999)
                )
            )
        }
        
        // 展开的详情
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                toolCalls.forEach { toolCall ->
                    ToolCallItem(toolCall)
                }
            }
        }
    }
}

/**
 * 单个工具调用项
 */
@Composable
private fun ToolCallItem(toolCall: ToolCall) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF3C3F41),
                RoundedCornerShape(4.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 图标
        Text(
            when {
                toolCall.name.contains("search", ignoreCase = true) -> "🔍"
                toolCall.name.contains("read", ignoreCase = true) -> "📖"
                toolCall.name.contains("edit", ignoreCase = true) -> "✏️"
                toolCall.name.contains("run", ignoreCase = true) || toolCall.name.contains("command", ignoreCase = true) -> "💻"
                toolCall.name.contains("symbol", ignoreCase = true) -> "🔷"
                toolCall.name.contains("problem", ignoreCase = true) -> "⚠️"
                toolCall.name.contains("git", ignoreCase = true) -> "🔀"
                toolCall.name.contains("web", ignoreCase = true) -> "🌐"
                else -> "🔧"
            },
            style = JewelTheme.defaultTextStyle
        )
        
        // 内容
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                toolCall.name,
                style = JewelTheme.defaultTextStyle.copy(
                    color = Color.White
                )
            )
            
            // 状态
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val (statusText, statusColor) = when (toolCall.status) {
                    ToolCallStatus.PENDING -> "等待中" to Color(0xFF999999)
                    ToolCallStatus.RUNNING -> "执行中" to Color(0xFF3574F0)
                    ToolCallStatus.SUCCESS -> "成功" to Color(0xFF59A869)
                    ToolCallStatus.FAILED -> "失败" to Color(0xFFE55765)
                    ToolCallStatus.CANCELLED -> "已取消" to Color(0xFF999999)
                }
                
                Text(
                    statusText,
                    style = JewelTheme.defaultTextStyle.copy(
                        color = statusColor,
                        fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                    )
                )
                
                // 执行时间
                toolCall.endTime?.let { endTime ->
                    val duration = endTime - toolCall.startTime
                    Text(
                        "(${duration}ms)",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = Color(0xFF999999),
                            fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                        )
                    )
                }
            }
            
            // 结果摘要
            toolCall.result?.let { result ->
                when (result) {
                    is ToolResult.Success -> {
                        Text(
                            result.summary,
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFF999999),
                                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                            )
                        )
                    }
                    is ToolResult.Failure -> {
                        Text(
                            result.error,
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFFE55765),
                                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                            )
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}