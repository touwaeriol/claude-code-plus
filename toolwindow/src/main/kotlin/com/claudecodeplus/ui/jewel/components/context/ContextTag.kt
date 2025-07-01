/*
 * ContextTag.kt
 * 
 * 上下文标签组件 - 显示已选择的上下文项
 * 支持文件和Web两种类型，包含悬停提示和删除功能
 */

package com.claudecodeplus.ui.jewel.components.context

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import com.claudecodeplus.ui.models.ContextReference

/**
 * 上下文标签组件
 * 显示已选择的上下文，支持悬停提示和删除功能
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContextTag(
    context: ContextReference,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHovered by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf(false) }
    
    // 悬停延迟显示工具提示
    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(500) // 500ms延迟
            showTooltip = true
        } else {
            showTooltip = false
        }
    }
    
    Box {
        // 主标签
        Row(
            modifier = modifier
                .background(
                    JewelTheme.globalColors.panelBackground, // 与输入框相同的背景色
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 标签文本
            Text(
                text = getDisplayText(context),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.normal
                )
            )
            
            // 删除按钮
            Text(
                text = "×",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = if (isHovered) 
                        JewelTheme.globalColors.text.normal 
                    else 
                        JewelTheme.globalColors.text.disabled
                ),
                modifier = Modifier
                    .clickable { onRemove() }
                    .padding(2.dp)
            )
        }
        
        // 工具提示
        if (showTooltip) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = with(LocalDensity.current) { androidx.compose.ui.unit.IntOffset(0, -8.dp.roundToPx()) },
                properties = PopupProperties(focusable = false)
            ) {
                ContextTooltip(context = context)
            }
        }
    }
}

/**
 * 获取上下文的显示文本
 */
private fun getDisplayText(context: ContextReference): String {
    return when (context) {
        is ContextReference.FileReference -> {
            // 文件：显示 @filename
            val filename = context.path.substringAfterLast('/')
                .ifEmpty { context.path.substringAfterLast('\\') }
                .ifEmpty { context.path }
            "@$filename"
        }
        is ContextReference.WebReference -> {
            // Web：显示完整URL
            "@${context.url}"
        }
        is ContextReference.FolderReference -> "@${context.path.substringAfterLast('/')}"
        is ContextReference.SymbolReference -> "@${context.name}"
        is ContextReference.TerminalReference -> "@terminal"
        is ContextReference.ProblemsReference -> "@problems"
        is ContextReference.GitReference -> "@git"
        ContextReference.SelectionReference -> "@selection"
        ContextReference.WorkspaceReference -> "@workspace"
    }
}

/**
 * 上下文工具提示组件
 */
@Composable
private fun ContextTooltip(
    context: ContextReference,
    modifier: Modifier = Modifier
) {
    val tooltipText = when (context) {
        is ContextReference.FileReference -> {
            // 文件：显示完整路径
            extractPathFromFileReference(context.fullPath)
        }
        is ContextReference.WebReference -> {
            // Web：显示标题或URL
            context.title ?: context.url
        }
        is ContextReference.FolderReference -> context.path
        is ContextReference.SymbolReference -> "${context.name} in ${context.file}:${context.line}"
        is ContextReference.TerminalReference -> "Terminal output (${context.lines} lines)"
        is ContextReference.ProblemsReference -> "Problems (${context.problems.size} items)"
        is ContextReference.GitReference -> "Git ${context.type.name}: ${context.content}"
        ContextReference.SelectionReference -> "Selected code"
        ContextReference.WorkspaceReference -> "Entire workspace"
    }
    
    Box(
        modifier = modifier
            .background(
                JewelTheme.globalColors.borders.normal,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = tooltipText,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 10.sp,
                color = JewelTheme.globalColors.text.normal
            )
        )
    }
}

/**
 * 从文件引用中提取路径
 * 支持从 @file:// 格式中提取
 */
fun extractPathFromFileReference(path: String): String {
    return when {
        path.startsWith("@file://") -> path.substring(8)
        path.startsWith("file://") -> path.substring(7)
        else -> path
    }
}

/**
 * 上下文标签列表组件
 * 用于显示多个上下文标签
 */
@Composable
fun ContextTagList(
    contexts: List<ContextReference>,
    onRemove: (ContextReference) -> Unit,
    modifier: Modifier = Modifier
) {
    if (contexts.isNotEmpty()) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            contexts.forEach { context ->
                ContextTag(
                    context = context,
                    onRemove = { onRemove(context) }
                )
            }
        }
    }
} 