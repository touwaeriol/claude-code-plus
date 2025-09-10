/*
 * PillContextTag.kt
 * 
 * 胶囊形上下文标签组件 - 现代化设计
 * 支持类型图标、悬浮效果和优雅的关闭按钮
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ContextReference
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * 胶囊形上下文标签
 * 
 * @param context 上下文引用
 * @param onRemove 移除回调
 * @param enabled 是否启用交互
 * @param modifier 修饰符
 */
@Composable
fun PillContextTag(
    context: ContextReference,
    onRemove: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // 动画状态
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isHovered && enabled) 0.15f else 0.1f,
        animationSpec = tween(200),
        label = "background alpha"
    )
    
    val closeButtonAlpha by animateFloatAsState(
        targetValue = if (isHovered && enabled) 1f else 0f,
        animationSpec = tween(200),
        label = "close button alpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered && enabled) 1.02f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )
    
    Row(
        modifier = modifier
            .scale(scale)
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                JewelTheme.globalColors.outlines.focused.copy(alpha = backgroundAlpha)
            )
            .hoverable(interactionSource, enabled)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 类型图标
        ContextIcon(
            context = context,
            modifier = Modifier.size(14.dp)
        )
        
        // 标签文本
        Text(
            text = getDisplayText(context),
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                color = JewelTheme.globalColors.text.normal
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        
        // 关闭按钮（悬浮时显示）
        Box(
            modifier = Modifier
                .size(12.dp)
                .alpha(closeButtonAlpha)
                .clip(CircleShape)
                .clickable(enabled = enabled && isHovered) { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                key = AllIconsKeys.Actions.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(10.dp),
                tint = JewelTheme.globalColors.text.normal
            )
        }
    }
}

/**
 * 上下文类型图标
 */
@Composable
private fun ContextIcon(
    context: ContextReference,
    modifier: Modifier = Modifier
) {
    val (iconKey, iconColor) = when (context) {
        is ContextReference.FileReference -> {
            val fileType = context.path.substringAfterLast('.', "")
            val icon = when (fileType.lowercase()) {
                "kt", "java" -> AllIconsKeys.FileTypes.Java
                "py" -> AllIconsKeys.FileTypes.Text
                "js", "ts", "jsx", "tsx" -> AllIconsKeys.FileTypes.JavaScript
                "html" -> AllIconsKeys.FileTypes.Html
                "css", "scss", "sass" -> AllIconsKeys.FileTypes.Css
                "json" -> AllIconsKeys.FileTypes.Json
                "xml" -> AllIconsKeys.FileTypes.Xml
                "md" -> AllIconsKeys.FileTypes.Text
                else -> AllIconsKeys.FileTypes.Text
            }
            icon to Color(0xFF5B9BD5)
        }
        is ContextReference.WebReference -> {
            AllIconsKeys.General.Web to Color(0xFF70AD47)
        }
        is ContextReference.FolderReference -> {
            AllIconsKeys.Nodes.Folder to Color(0xFFFFC000)
        }
        is ContextReference.SymbolReference -> {
            when (context.type.name.lowercase()) {
                "class" -> AllIconsKeys.Nodes.Class
                "function", "method" -> AllIconsKeys.Nodes.Method
                "variable", "field" -> AllIconsKeys.Nodes.Field
                "interface" -> AllIconsKeys.Nodes.Interface
                else -> AllIconsKeys.Nodes.Class
            } to Color(0xFF8B5CF6)
        }
        is ContextReference.ImageReference -> {
            AllIconsKeys.FileTypes.Image to Color(0xFFEC4899)
        }
        is ContextReference.TerminalReference -> {
            AllIconsKeys.Debugger.Console to Color(0xFF10B981)
        }
        is ContextReference.ProblemsReference -> {
            AllIconsKeys.General.Error to Color(0xFFEF4444)
        }
        is ContextReference.GitReference -> {
            AllIconsKeys.Vcs.Branch to Color(0xFFF59E0B)
        }
        ContextReference.SelectionReference -> {
            AllIconsKeys.Actions.Edit to Color(0xFF3B82F6)
        }
        ContextReference.WorkspaceReference -> {
            AllIconsKeys.Actions.ProjectDirectory to Color(0xFF6366F1)
        }
    }
    
    Icon(
        key = iconKey,
        contentDescription = null,
        modifier = modifier,
        tint = iconColor
    )
}

/**
 * 获取上下文的显示文本
 */
private fun getDisplayText(context: ContextReference): String {
    return when (context) {
        is ContextReference.FileReference -> {
            context.path.substringAfterLast('/')
                .ifEmpty { context.path.substringAfterLast('\\') }
                .ifEmpty { context.path }
        }
        is ContextReference.WebReference -> {
            context.title ?: context.url
                .removePrefix("https://")
                .removePrefix("http://")
                .take(30)
        }
        is ContextReference.FolderReference -> {
            context.path.substringAfterLast('/')
                .ifEmpty { context.path.substringAfterLast('\\') }
        }
        is ContextReference.SymbolReference -> context.name
        is ContextReference.ImageReference -> context.filename
        is ContextReference.TerminalReference -> "Terminal (${context.lines} lines)"
        is ContextReference.ProblemsReference -> "Problems (${context.problems.size})"
        is ContextReference.GitReference -> "Git ${context.type.name.lowercase()}"
        ContextReference.SelectionReference -> "Selection"
        ContextReference.WorkspaceReference -> "Workspace"
    }
}

/**
 * 上下文标签组（用于显示多个标签）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillContextTagGroup(
    contexts: List<ContextReference>,
    onRemove: (ContextReference) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        contexts.forEach { context ->
            key(context) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    PillContextTag(
                        context = context,
                        onRemove = { onRemove(context) },
                        enabled = enabled
                    )
                }
            }
        }
    }
}