package com.claudecodeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.Project
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset

/**
 * 项目标签栏组件
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectTabBar(
    projects: List<Project>,
    currentProjectId: String?,
    sessionCounts: Map<String, Int>,
    onProjectSelect: (Project) -> Unit,
    onProjectClose: ((Project) -> Unit)? = null,
    onNewProject: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 项目标签列表
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                items(projects) { project ->
                    ProjectTab(
                        project = project,
                        isActive = project.id == currentProjectId,
                        sessionCount = sessionCounts[project.id] ?: 0,
                        onSelect = { onProjectSelect(project) },
                        onClose = if (onProjectClose != null) {
                            { onProjectClose(project) }
                        } else null
                    )
                }
            }
            
            // 新建项目按钮
            if (onNewProject != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.1f))
                        .clickable { onNewProject() }
                        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新建项目",
                        modifier = Modifier.size(16.dp),
                        tint = JewelTheme.globalColors.text.normal
                    )
                }
            }
        }
    }
}

/**
 * 单个项目标签
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectTab(
    project: Project,
    isActive: Boolean,
    sessionCount: Int,
    onSelect: () -> Unit,
    onClose: (() -> Unit)?
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // Tooltip 实现
    Box {
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(
                    when {
                        isActive -> JewelTheme.globalColors.outlines.focused.copy(alpha = 0.2f)
                        isHovered -> JewelTheme.globalColors.outlines.focused.copy(alpha = 0.1f)
                        else -> Color.Transparent
                    }
                )
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = {
                        // 可以在这里添加右键菜单功能
                    }
                )
                .hoverable(interactionSource)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 项目名称
            Text(
                text = project.name,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 13.sp,
                    color = if (isActive) JewelTheme.globalColors.text.normal 
                           else JewelTheme.globalColors.text.disabled
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )
            
            // 会话数量指示器
            if (sessionCount > 0) {
                Box(
                    modifier = Modifier
                        .background(
                            JewelTheme.globalColors.outlines.focused.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = sessionCount.toString(),
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
            }
            
            // 关闭按钮
            if (onClose != null && (isActive || isHovered)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭项目",
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onClose() }
                        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))),
                    tint = JewelTheme.globalColors.text.disabled
                )
            }
        }
        
            // 活动指示器
            if (isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(JewelTheme.globalColors.outlines.focused)
                )
            }
        }
        
        // 鼠标悬停时显示完整路径
        if (isHovered) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .offset(y = 36.dp)
                    .background(
                        JewelTheme.globalColors.panelBackground,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        JewelTheme.globalColors.borders.normal,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = project.path,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = JewelTheme.globalColors.text.normal
                    )
                )
            }
        }
    }
}