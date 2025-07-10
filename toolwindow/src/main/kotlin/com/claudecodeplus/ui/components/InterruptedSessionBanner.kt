package com.claudecodeplus.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import com.claudecodeplus.ui.models.InterruptedSession
import com.claudecodeplus.ui.services.ChatSessionStateManager
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * 中断会话横幅
 */
@Composable
fun InterruptedSessionBanner(
    session: InterruptedSession,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when (session.reason) {
                        InterruptedSession.InterruptReason.ERROR -> Color(0xFFFF5252).copy(alpha = 0.1f)
                        InterruptedSession.InterruptReason.TIMEOUT -> Color(0xFFFFB74D).copy(alpha = 0.1f)
                        else -> Color(0xFF64B5F6).copy(alpha = 0.1f)
                    }
                )
                .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // 主要内容
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 图标
                        val icon = when (session.reason) {
                            InterruptedSession.InterruptReason.USER_CANCELLED -> Icons.Default.Info
                            InterruptedSession.InterruptReason.ERROR -> Icons.Default.Warning
                            InterruptedSession.InterruptReason.TIMEOUT -> Icons.Default.Refresh
                            InterruptedSession.InterruptReason.SYSTEM_SHUTDOWN -> Icons.Default.Close
                        }
                        
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = when (session.reason) {
                                InterruptedSession.InterruptReason.ERROR -> Color(0xFFFF5252)
                                InterruptedSession.InterruptReason.TIMEOUT -> Color(0xFFFFB74D)
                                else -> Color.Gray
                            }
                        )
                        
                        // 文本
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = getReasonText(session.reason),
                                style = JewelTheme.defaultTextStyle
                            )
                            
                            Text(
                                text = "中断时间: ${formatTime(session.timestamp)}",
                                style = JewelTheme.defaultTextStyle,
                                color = Color.Gray
                            )
                        }
                    }
                    
                    // 操作按钮
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { isExpanded = !isExpanded }) {
                            Text(if (isExpanded) "收起" else "详情")
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                        
                        OutlinedButton(onClick = onDiscard) {
                            Text("放弃")
                        }
                        
                        DefaultButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("继续对话")
                        }
                    }
                }
                
                // 展开的详情
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Divider(orientation = Orientation.Horizontal)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 未发送的输入
                        if (!session.pendingInput.isNullOrBlank()) {
                            Column {
                                Text(
                                    "未发送的消息：",
                                    style = JewelTheme.defaultTextStyle
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.95f))
                                ) {
                                    Text(
                                        text = session.pendingInput,
                                        style = JewelTheme.defaultTextStyle,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                        
                        // 上下文信息
                        if (session.context.isNotEmpty()) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    "上下文 (${session.context.size} 项)：",
                                    style = JewelTheme.defaultTextStyle
                                )
                                
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    session.context.take(3).forEach { context ->
                                        Chip(
                                            onClick = {},
                                            enabled = false
                                        ) {
                                            val text = when (context) {
                                                is com.claudecodeplus.ui.models.ContextItem.File -> 
                                                    java.io.File(context.path).name
                                                is com.claudecodeplus.ui.models.ContextItem.Folder -> 
                                                    java.io.File(context.path).name
                                                is com.claudecodeplus.ui.models.ContextItem.CodeBlock -> 
                                                    context.language
                                            }
                                            Text(
                                                text = text,
                                                style = JewelTheme.defaultTextStyle,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    
                                    if (session.context.size > 3) {
                                        Text(
                                            "+${session.context.size - 3} 更多",
                                            style = JewelTheme.defaultTextStyle,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 中断会话管理面板
 */
@Composable
fun InterruptedSessionsPanel(
    sessionStateManager: ChatSessionStateManager,
    onResumeSession: (InterruptedSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val interruptedSessions by sessionStateManager.interruptedSessions.collectAsState()
    
    if (interruptedSessions.isNotEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.panelBackground)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Text(
                        "中断的会话 (${interruptedSessions.size})",
                        style = JewelTheme.defaultTextStyle
                    )
                }
                
                OutlinedButton(
                    onClick = {
                        sessionStateManager.cleanupExpiredSessions()
                    }
                ) {
                    Text("清理过期")
                }
            }
            
            Divider(orientation = Orientation.Horizontal)
            
            // 会话列表
            interruptedSessions.values
                .sortedByDescending { it.timestamp }
                .forEach { session ->
                    InterruptedSessionCard(
                        session = session,
                        canResume = sessionStateManager.canResumeSession(session.sessionId),
                        onResume = { onResumeSession(session) },
                        onDelete = { 
                            sessionStateManager.deleteInterruptedSession(session.sessionId)
                        }
                    )
                }
        }
    }
}

/**
 * 中断会话卡片
 */
@Composable
private fun InterruptedSessionCard(
    session: InterruptedSession,
    canResume: Boolean,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "会话 ${session.sessionId.take(8)}...",
                        style = JewelTheme.defaultTextStyle
                    )
                    
                    Chip(
                        onClick = {},
                        enabled = false
                    ) {
                        Text(
                            getReasonText(session.reason),
                            style = JewelTheme.defaultTextStyle
                        )
                    }
                }
                
                Text(
                    text = "${formatRelativeTime(session.timestamp)} • ${session.context.size} 个上下文",
                    style = JewelTheme.defaultTextStyle,
                    color = Color.Gray
                )
                
                if (!session.pendingInput.isNullOrBlank()) {
                    Text(
                        text = "未发送: ${session.pendingInput.take(50)}...",
                        style = JewelTheme.defaultTextStyle,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }
            
            // 操作
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (canResume) {
                    DefaultButton(
                        onClick = onResume,
                        modifier = Modifier.size(width = 60.dp, height = 32.dp)
                    ) {
                        Text("恢复")
                    }
                } else {
                    Text(
                        "已过期",
                        style = JewelTheme.defaultTextStyle,
                        color = Color(0xFFFF5252)
                    )
                }
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// 辅助函数

private fun getReasonText(reason: InterruptedSession.InterruptReason): String {
    return when (reason) {
        InterruptedSession.InterruptReason.USER_CANCELLED -> "用户取消"
        InterruptedSession.InterruptReason.ERROR -> "发生错误"
        InterruptedSession.InterruptReason.TIMEOUT -> "超时"
        InterruptedSession.InterruptReason.SYSTEM_SHUTDOWN -> "系统关闭"
    }
}

private fun formatTime(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

private fun formatRelativeTime(instant: Instant): String {
    val duration = Duration.between(instant, Instant.now())
    
    return when {
        duration.toMinutes() < 1 -> "刚刚"
        duration.toMinutes() < 60 -> "${duration.toMinutes()} 分钟前"
        duration.toHours() < 24 -> "${duration.toHours()} 小时前"
        duration.toDays() < 7 -> "${duration.toDays()} 天前"
        else -> formatTime(instant)
    }
}