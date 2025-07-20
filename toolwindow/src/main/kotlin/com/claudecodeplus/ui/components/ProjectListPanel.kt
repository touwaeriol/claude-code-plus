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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Tooltip
import androidx.compose.foundation.hoverable
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity

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
    selectedProject: Project?,
    selectedSession: ProjectSession?,
    onProjectSelect: (Project) -> Unit,
    onSessionSelect: (ProjectSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val projects by projectManager.projects.collectAsState()
    val sessions by projectManager.sessions.collectAsState()
    val scope = rememberCoroutineScope()
    
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

    Column(modifier = modifier.width(250.dp).fillMaxHeight().padding(8.dp)) {
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
                                    imageVector = if (expandedProjects.contains(project.id)) 
                                        Icons.Default.KeyboardArrowDown 
                                    else 
                                        Icons.Default.KeyboardArrowRight,
                                    contentDescription = if (expandedProjects.contains(project.id)) "折叠" else "展开",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            
                            // 项目名称
                            Text(
                                text = project.name,
                                fontWeight = FontWeight.Bold,
                                color = if (project.id == selectedProject?.id) Color.Blue else Color.Unspecified
                            )
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
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 4.dp)
                                    .onPointerEvent(PointerEventType.Enter) {
                                        showTooltip = true
                                    }
                                    .onPointerEvent(PointerEventType.Exit) {
                                        showTooltip = false
                                    }
                            ) {
                                Text(
                                    text = session.name,
                                    color = if (session.id == selectedSession?.id) Color.Blue else Color.Unspecified,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { onSessionSelect(session) },
                                            onLongClick = {
                                                menuSession = session
                                                showSessionMenu = true
                                            }
                                        )
                                        .padding(vertical = 2.dp, horizontal = 4.dp)
                                )
                                
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
}