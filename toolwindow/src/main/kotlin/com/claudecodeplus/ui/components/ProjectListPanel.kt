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
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Tooltip
import androidx.compose.foundation.hoverable
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.foundation.gestures.detectTapGestures

// 简单的上下文菜单组件
@Composable
fun ContextMenu(
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

// 菜单项组件
@Composable
fun MenuItem(
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

@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ProjectListPanel(
    projectManager: ProjectManager,
    tabManager: com.claudecodeplus.ui.services.ChatTabManager? = null,
    selectedProject: Project?,
    selectedSession: ProjectSession?,
    hoveredSessionId: String? = null,
    onProjectSelect: (Project) -> Unit,
    onSessionSelect: (ProjectSession) -> Unit,
    onSessionHover: ((String?) -> Unit)? = null,
    onCreateProject: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val projects by projectManager.projects.collectAsState()
    val sessions by projectManager.sessions.collectAsState()
    val scope = rememberCoroutineScope()
    
    // 获取已打开的标签信息
    val openedTabs = tabManager?.tabs ?: emptyList()
    val activeTabId = tabManager?.activeTabId
    
    // 右键菜单状态
    var showProjectMenu by remember { mutableStateOf(false) }
    var showSessionMenu by remember { mutableStateOf(false) }
    var menuProject by remember { mutableStateOf<Project?>(null) }
    var menuSession by remember { mutableStateOf<ProjectSession?>(null) }
    
    // 项目展开/折叠状态（默认展开当前选中的项目）
    var expandedProjects by remember { 
        mutableStateOf(
            if (selectedProject != null) setOf(selectedProject.id) else setOf()
        ) 
    }
    
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
            // 新建项目按钮
            onCreateProject?.let { onCreateCallback ->
                DefaultButton(
                    onClick = onCreateCallback,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("+ 新建项目")
                }
            }
            
            // --- 列表 ---
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(projects) { project ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 项目标题
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { 
                                            // 单击切换展开/折叠
                                            expandedProjects = if (expandedProjects.contains(project.id)) {
                                                expandedProjects - project.id
                                            } else {
                                                expandedProjects + project.id
                                            }
                                            onProjectSelect(project) 
                                        },
                                        onLongClick = {
                                            // 长按显示菜单（作为右键菜单的替代）
                                            menuProject = project
                                            showProjectMenu = true
                                        }
                                    )
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 展开/折叠图标
                                val projectSessions = sessions[project.id] ?: emptyList()
                                if (projectSessions.isNotEmpty()) {
                                    Icon(
                                        key = if (expandedProjects.contains(project.id)) 
                                            AllIconsKeys.Actions.FindAndShowNextMatches 
                                        else 
                                            AllIconsKeys.General.ArrowRight,
                                        contentDescription = if (expandedProjects.contains(project.id)) "折叠" else "展开",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                
                                // 项目名称和路径
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = project.name,
                                        fontWeight = FontWeight.Bold,
                                        color = if (project.id == selectedProject?.id) Color.Blue else Color.Unspecified
                                    )
                                    Text(
                                        text = project.path,
                                        style = JewelTheme.defaultTextStyle.copy(
                                            fontSize = JewelTheme.defaultTextStyle.fontSize * 0.85f,
                                            color = JewelTheme.globalColors.text.disabled
                                        ),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            // 项目右键菜单
                            if (showProjectMenu && menuProject == project) {
                                ContextMenu(
                                    onDismiss = { showProjectMenu = false }
                                ) {
                                    MenuItem(
                                        text = "删除项目及所有会话",
                                        onClick = {
                                            scope.launch {
                                                projectManager.deleteProject(project)
                                            }
                                            showProjectMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // 会话列表（只在展开时显示）
                        if (expandedProjects.contains(project.id)) {
                            val projectSessions = sessions[project.id] ?: emptyList()
                            projectSessions.forEach { session ->
                                var showTooltip by remember { mutableStateOf(false) }
                                
                                // 检查该会话是否已打开标签
                                val hasOpenTab = openedTabs.any { it.sessionId == session.id }
                                val isActiveTab = openedTabs.find { it.sessionId == session.id }?.id == activeTabId
                                val isHovered = hoveredSessionId == session.id
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 24.dp, end = 4.dp)
                                        .onPointerEvent(PointerEventType.Enter) {
                                            showTooltip = true
                                            onSessionHover?.invoke(session.id)
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
                                                    val tab = openedTabs.find { it.sessionId == session.id }
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
                                                    else -> Color.Transparent
                                                },
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(vertical = 2.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 标签指示器
                                        if (hasOpenTab) {
                                            Icon(
                                                key = AllIconsKeys.Nodes.Bookmark, // 使用 Bookmark 图标作为标签指示器
                                                contentDescription = "已打开标签",
                                                modifier = Modifier.size(14.dp),
                                                tint = if (isActiveTab) Color(0xFF2675BF) else Color(0xFF4CAF50)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        
                                        val displayName = session.name.also { name ->
                                            // 调试：检查会话列表中显示的名称
                                            if (name.matches(Regex("\\d+"))) {
                                                println("警告：会话列表显示纯数字名称！")
                                                println("  - session.id: ${session.id}")
                                                println("  - session.name: '$name'")
                                                println("  - session.projectId: ${session.projectId}")
                                            }
                                        }
                                        Text(
                                            text = displayName,
                                            color = if (session.id == selectedSession?.id) Color.Blue else Color.Unspecified,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        // 移除三个点按钮，使用右键菜单代替
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
                                        ContextMenu(
                                            onDismiss = { showSessionMenu = false }
                                        ) {
                                            MenuItem(
                                                text = "删除会话",
                                                onClick = {
                                                    scope.launch {
                                                        projectManager.deleteSession(session, project)
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