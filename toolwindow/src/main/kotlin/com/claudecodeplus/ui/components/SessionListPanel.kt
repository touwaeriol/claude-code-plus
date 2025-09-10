package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.session.models.SessionInfo
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.text.SimpleDateFormat
import java.util.*

/**
 * 会话列表面板
 */
@Composable
fun SessionListPanel(
    projectPath: String,
    sessionManager: ClaudeSessionManager,
    currentSessionId: String?,
    onSessionSelect: (SessionInfo) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (SessionInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var visitedSessionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val coroutineScope = rememberCoroutineScope()
    
    // 加载会话列表
    LaunchedEffect(projectPath) {
        // SessionListPanel: Loading sessions for project: $projectPath
        isLoading = true
        sessions = sessionManager.getSessionList(projectPath)
        // SessionListPanel: Loaded ${sessions.size} sessions
        isLoading = false
    }
    
    // 当前会话变化时，自动添加到已访问列表
    LaunchedEffect(currentSessionId) {
        if (currentSessionId != null) {
            visitedSessionIds = visitedSessionIds + currentSessionId
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "会话历史",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 新建会话按钮
                IconButton(
                    onClick = onNewSession
                ) {
                    Icon(
                        key = AllIconsKeys.General.Add,
                        contentDescription = "新建会话",
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // 刷新按钮
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            sessions = sessionManager.getSessionList(projectPath)
                            isLoading = false
                        }
                    }
                ) {
                    Icon(
                        key = AllIconsKeys.Actions.Refresh,
                        contentDescription = "刷新",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        Divider(orientation = org.jetbrains.jewel.ui.Orientation.Horizontal)
        
        // 会话列表
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // SessionListPanel render: isLoading=$isLoading, sessions.size=${sessions.size}
            when {
                isLoading -> {
                    // 加载状态
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                sessions.isEmpty() -> {
                    // 空状态
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "暂无会话历史",
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = JewelTheme.globalColors.text.disabled
                                )
                            )
                            DefaultButton(onClick = onNewSession) {
                                Text("创建新会话")
                            }
                        }
                    }
                }
                else -> {
                    // 会话列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = sessions,
                            key = { it.sessionId }
                        ) { session ->
                            SessionItem(
                                session = session,
                                isSelected = session.sessionId == currentSessionId,
                                isVisited = visitedSessionIds.contains(session.sessionId),
                                onClick = { 
                                    visitedSessionIds = visitedSessionIds + session.sessionId
                                    onSessionSelect(session) 
                                },
                                onDelete = { onDeleteSession(session) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 会话列表项
 */
@Composable
private fun SessionItem(
    session: SessionInfo,
    isSelected: Boolean,
    isVisited: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isSelected -> Color(0xFF1976D2) // 深蓝色背景，当前选中
                    isVisited -> Color(0xFF1976D2).copy(alpha = 0.1f) // 浅蓝色背景，已访问
                    else -> Color.Transparent // 透明背景，未访问
                }
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // 会话信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 第一条消息预览，包含压缩标记
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 压缩会话标记
                    if (session.isCompactSummary) {
                        Text(
                            text = "🗜️",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 14.sp,
                                color = if (isSelected) Color.White else Color(0xFF1976D2)  // 蓝色
                            )
                        )
                    }
                    
                    Text(
                        text = session.firstMessage ?: "新会话",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 14.sp,
                            color = if (isSelected) Color.White else JewelTheme.globalColors.text.normal
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(Modifier.height(4.dp))
                
                // 时间和消息数
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatSessionTime(session.lastModified),
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else JewelTheme.globalColors.text.disabled
                        )
                    )
                    Text(
                        text = "·",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else JewelTheme.globalColors.text.disabled
                        )
                    )
                    Text(
                        text = "${session.messageCount} 条消息",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else JewelTheme.globalColors.text.disabled
                        )
                    )
                }
            }
            
            // 删除按钮
            if (showDeleteConfirm) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            onDelete()
                            showDeleteConfirm = false
                        }
                    ) {
                        Icon(
                            key = AllIconsKeys.Actions.CheckOut,
                            contentDescription = "确认删除",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFE53935)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = false }
                    ) {
                        Icon(
                            key = AllIconsKeys.Actions.Close,
                            contentDescription = "取消",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = { showDeleteConfirm = true }
                ) {
                    Icon(
                        key = AllIconsKeys.Actions.GC,
                        contentDescription = "删除",
                        modifier = Modifier.size(16.dp),
                        tint = JewelTheme.globalColors.text.disabled
                    )
                }
            }
        }
    }
}

/**
 * 格式化会话时间
 */
private fun formatSessionTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        diff < 604800_000 -> "${diff / 86400_000} 天前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}