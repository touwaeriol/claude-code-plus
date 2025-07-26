package com.claudecodeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.Project
import com.claudecodeplus.ui.models.ProjectSession
import com.claudecodeplus.ui.services.ProjectManager
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.foundation.theme.JewelTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import org.jetbrains.jewel.ui.component.Icon
import androidx.compose.foundation.hoverable
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.ExperimentalComposeUiApi

/**
 * 会话列表面板（仅显示当前项目的会话）
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SessionListPanel(
    projectManager: ProjectManager,
    tabManager: com.claudecodeplus.ui.services.ChatTabManager? = null,
    currentProject: Project,
    selectedSession: ProjectSession?,
    hoveredSessionId: String? = null,
    onSessionSelect: (ProjectSession) -> Unit,
    onSessionHover: ((String?) -> Unit)? = null,
    onCreateSession: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sessions by projectManager.sessions.collectAsState()
    val projectSessions = sessions[currentProject.id] ?: emptyList()
    val scope = rememberCoroutineScope()
    
    // 获取已打开的标签信息
    val openedTabs = tabManager?.tabs ?: emptyList()
    val activeTabId = tabManager?.activeTabId
    
    // 右键菜单状态
    var showSessionMenu by remember { mutableStateOf(false) }
    var menuSession by remember { mutableStateOf<ProjectSession?>(null) }
    
    // 面板宽度状态（可拖拽调整）
    var panelWidth by remember { mutableStateOf(250.dp) }
    val minWidth = 200.dp
    val maxWidth = 400.dp
    val density = LocalDensity.current
    
    Box(modifier = modifier.fillMaxHeight()) {
        // 主面板内容
        Column(
            modifier = Modifier
                .width(panelWidth)
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            // 顶部信息栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentProject.name} 的会话",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = JewelTheme.defaultTextStyle.fontSize * 0.95f
                    )
                )
                
                Text(
                    text = "(${projectSessions.size})",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = JewelTheme.globalColors.text.disabled,
                        fontSize = JewelTheme.defaultTextStyle.fontSize * 0.85f
                    )
                )
            }
            
            Divider(
                orientation = Orientation.Horizontal,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            // 新建会话按钮
            println("[DEBUG] SessionListPanel - onCreateSession 是否为 null: ${onCreateSession == null}")
            if (onCreateSession != null) {
                println("[DEBUG] 显示新建会话按钮")
                DefaultButton(
                    onClick = onCreateSession,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("+ 新建会话")
                }
            } else {
                println("[DEBUG] 不显示新建会话按钮，因为 onCreateSession 为 null")
            }
            
            // 会话列表
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(projectSessions) { session ->
                    var showTooltip by remember { mutableStateOf(false) }
                    
                    // 检查该会话是否已打开标签
                    val hasOpenTab = session.id?.let { id -> openedTabs.any { it.sessionId == id } } ?: false
                    val isActiveTab = session.id?.let { id -> openedTabs.find { it.sessionId == id }?.id == activeTabId } ?: false
                    val isHovered = session.id?.let { it == hoveredSessionId } ?: false
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPointerEvent(PointerEventType.Enter) {
                                showTooltip = true
                                session.id?.let { onSessionHover?.invoke(it) }
                            }
                            .onPointerEvent(PointerEventType.Exit) {
                                showTooltip = false
                                onSessionHover?.invoke(null)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onSessionSelect(session) },
                                    onLongClick = {
                                        // 长按显示菜单（作为右键菜单的替代）
                                        menuSession = session
                                        showSessionMenu = true
                                    },
                                    onDoubleClick = {
                                        // 双击切换到对应的标签
                                        val tab = session.id?.let { id -> openedTabs.find { it.sessionId == id } }
                                        if (tab != null) {
                                            tabManager?.setActiveTab(tab.id)
                                        }
                                    }
                                )
                                .background(
                                    when {
                                        isActiveTab -> Color(0xFF2675BF).copy(alpha = 0.2f)
                                        isHovered -> Color(0xFFFFEB3B).copy(alpha = 0.2f) // 黄色高亮表示悬停
                                        hasOpenTab -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                                        selectedSession?.id == session.id -> Color(0xFF2196F3).copy(alpha = 0.1f)
                                        else -> Color.Transparent
                                    },
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 标签指示器
                            if (hasOpenTab) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "已打开标签",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isActiveTab) Color(0xFF2675BF) else Color(0xFF4CAF50)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            
                            // 会话名称
                            Text(
                                text = session.name,
                                color = if (session.id == selectedSession?.id) 
                                    JewelTheme.globalColors.text.normal
                                else 
                                    JewelTheme.globalColors.text.disabled,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        // 显示完整文本的 Tooltip
                        if (showTooltip && session.name.length > 30) {
                            Popup(
                                alignment = Alignment.TopStart,
                                offset = IntOffset(0, -40)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .shadow(4.dp, RoundedCornerShape(4.dp))
                                        .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(4.dp))
                                        .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = session.name,
                                        style = JewelTheme.defaultTextStyle,
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    
                        // 会话右键菜单
                        if (showSessionMenu && menuSession == session) {
                            SessionContextMenu(
                                onDismiss = { showSessionMenu = false }
                            ) {
                                SessionMenuItem(
                                    text = "删除会话",
                                    onClick = {
                                        scope.launch {
                                            projectManager.deleteSession(session, currentProject)
                                        }
                                        showSessionMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // 右侧拖拽边界
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .offset(x = panelWidth - 2.dp)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val newWidth = with(density) { 
                            (panelWidth.toPx() + change.position.x).toDp()
                        }
                        panelWidth = newWidth.coerceIn(minWidth, maxWidth)
                    }
                }
                .background(Color.Transparent)
        )
    }
}

// 使用 SessionContextMenu 和 SessionMenuItem 避免命名冲突
@Composable
private fun SessionContextMenu(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        offset = IntOffset(8, 0)
    ) {
        Box(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(JewelTheme.globalColors.panelBackground)
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(4.dp))
                .padding(4.dp)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SessionMenuItem(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text)
    }
}