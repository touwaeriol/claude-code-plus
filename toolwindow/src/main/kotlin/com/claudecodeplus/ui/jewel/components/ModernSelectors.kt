/*
 * ModernSelectors.kt
 * 
 * 现代化选择器组件 - 悬浮卡片式设计
 * 包含模型选择器和权限模式选择器
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.PermissionMode
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * 现代化模型选择器
 * 
 * @param currentModel 当前选择的模型
 * @param onModelChange 模型变更回调
 * @param enabled 是否启用
 * @param modifier 修饰符
 */
@Composable
fun ModernModelSelector(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val models = listOf(
        AiModel.OPUS to ModelInfo(
            icon = AllIconsKeys.General.GearPlain,
            color = Color(0xFF8B5CF6),
            description = "最强大的模型，适合复杂任务"
        ),
        AiModel.SONNET to ModelInfo(
            icon = AllIconsKeys.General.Settings,
            color = Color(0xFF3B82F6),
            description = "平衡性能和速度"
        )
    )
    
    Box(modifier = modifier) {
        // 主按钮
        SelectorButton(
            text = currentModel.displayName,
            icon = models.find { it.first == currentModel }?.second?.icon,
            iconColor = models.find { it.first == currentModel }?.second?.color,
            isExpanded = showDropdown,
            isHovered = isHovered,
            enabled = enabled,
            onClick = { showDropdown = !showDropdown },
            interactionSource = interactionSource
        )
        
        // 下拉菜单
        if (showDropdown) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, -8),
                onDismissRequest = { showDropdown = false },
                properties = PopupProperties(focusable = true)
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(150)) + slideInVertically(
                        animationSpec = tween(150),
                        initialOffsetY = { it }
                    ),
                    exit = fadeOut(tween(100))
                ) {
                    DropdownMenu(
                        items = models,
                        selectedItem = currentModel,
                        onItemSelect = { model ->
                            onModelChange(model)
                            showDropdown = false
                        },
                        itemContent = { model, info ->
                            ModelMenuItem(
                                model = model,
                                info = info,
                                isSelected = model == currentModel
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * 现代化权限模式选择器
 * 
 * @param currentMode 当前权限模式
 * @param onModeChange 权限模式变更回调
 * @param enabled 是否启用
 * @param modifier 修饰符
 */
@Composable
fun ModernPermissionSelector(
    currentMode: PermissionMode,
    onModeChange: (PermissionMode) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val modes = listOf(
        PermissionMode.DEFAULT to PermissionModeInfo(
            icon = AllIconsKeys.General.Settings,
            color = Color(0xFF10B981),
            description = "标准权限模式"
        ),
        PermissionMode.ACCEPT_EDITS to PermissionModeInfo(
            icon = AllIconsKeys.Actions.Edit,
            color = Color(0xFF3B82F6),
            description = "自动接受编辑建议"
        ),
        PermissionMode.BYPASS_PERMISSIONS to PermissionModeInfo(
            icon = AllIconsKeys.Actions.Commit,
            color = Color(0xFFF59E0B),
            description = "跳过权限确认"
        ),
        PermissionMode.PLAN to PermissionModeInfo(
            icon = AllIconsKeys.General.Note,
            color = Color(0xFF8B5CF6),
            description = "计划模式，不执行操作"
        )
    )
    
    Box(modifier = modifier) {
        // 主按钮
        SelectorButton(
            text = currentMode.displayName,
            icon = modes.find { it.first == currentMode }?.second?.icon,
            iconColor = modes.find { it.first == currentMode }?.second?.color,
            isExpanded = showDropdown,
            isHovered = isHovered,
            enabled = enabled,
            onClick = { showDropdown = !showDropdown },
            interactionSource = interactionSource
        )
        
        // 下拉菜单
        if (showDropdown) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, -8),
                onDismissRequest = { showDropdown = false },
                properties = PopupProperties(focusable = true)
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(150)) + slideInVertically(
                        animationSpec = tween(150),
                        initialOffsetY = { it }
                    ),
                    exit = fadeOut(tween(100))
                ) {
                    DropdownMenu(
                        items = modes,
                        selectedItem = currentMode,
                        onItemSelect = { mode ->
                            onModeChange(mode)
                            showDropdown = false
                        },
                        itemContent = { mode, info ->
                            PermissionModeMenuItem(
                                mode = mode,
                                info = info,
                                isSelected = mode == currentMode
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * 选择器按钮组件
 */
@Composable
private fun SelectorButton(
    text: String,
    icon: org.jetbrains.jewel.ui.icon.IconKey? = null,
    iconColor: Color? = null,
    isExpanded: Boolean,
    isHovered: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier
) {
    val backgroundAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            isHovered -> 0.1f
            else -> 0f
        },
        animationSpec = tween(200),
        label = "background alpha"
    )
    
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(200),
        label = "arrow rotation"
    )
    
    Row(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                JewelTheme.globalColors.outlines.focused.copy(alpha = backgroundAlpha)
            )
            .clickable(enabled = enabled) { onClick() }
            .hoverable(interactionSource, enabled)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 图标（如果有）
        if (icon != null) {
            Icon(
                key = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = iconColor ?: JewelTheme.globalColors.text.normal
            )
        }
        
        // 文本
        Text(
            text = text,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                color = if (enabled) 
                    JewelTheme.globalColors.text.normal 
                else 
                    JewelTheme.globalColors.text.disabled
            )
        )
        
        // 箭头
        Icon(
            key = AllIconsKeys.General.ChevronDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier
                .size(12.dp)
                .rotate(arrowRotation),
            tint = if (enabled) 
                JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
            else 
                JewelTheme.globalColors.text.disabled
        )
    }
}

/**
 * 下拉菜单容器
 */
@Composable
private fun <T, I> DropdownMenu(
    items: List<Pair<T, I>>,
    selectedItem: T,
    onItemSelect: (T) -> Unit,
    itemContent: @Composable (T, I) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(240.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(6.dp),
                clip = false
            )
            .clip(RoundedCornerShape(6.dp))
            .background(JewelTheme.globalColors.panelBackground)
            .padding(4.dp)
    ) {
        items.forEach { (item, info) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onItemSelect(item) }
                    .padding(2.dp)
            ) {
                itemContent(item, info)
            }
        }
    }
}

/**
 * 模型菜单项
 */
@Composable
private fun ModelMenuItem(
    model: AiModel,
    info: ModelInfo,
    isSelected: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val backgroundAlpha by animateFloatAsState(
        targetValue = when {
            isSelected -> 0.15f
            isHovered -> 0.1f
            else -> 0f
        },
        animationSpec = tween(150),
        label = "background alpha"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                JewelTheme.globalColors.outlines.focused.copy(alpha = backgroundAlpha),
                RoundedCornerShape(4.dp)
            )
            .hoverable(interactionSource)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 图标
        Icon(
            key = info.icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = info.color
        )
        
        // 文本内容
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = model.displayName,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.normal
                )
            )
            Text(
                text = info.description,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                )
            )
        }
        
        // 选中标记
        if (isSelected) {
            Icon(
                key = AllIconsKeys.Actions.Checked,
                contentDescription = "Selected",
                modifier = Modifier.size(14.dp),
                tint = JewelTheme.globalColors.outlines.focused
            )
        }
    }
}

/**
 * 权限模式菜单项
 */
@Composable
private fun PermissionModeMenuItem(
    mode: PermissionMode,
    info: PermissionModeInfo,
    isSelected: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val backgroundAlpha by animateFloatAsState(
        targetValue = when {
            isSelected -> 0.15f
            isHovered -> 0.1f
            else -> 0f
        },
        animationSpec = tween(150),
        label = "background alpha"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                JewelTheme.globalColors.outlines.focused.copy(alpha = backgroundAlpha),
                RoundedCornerShape(4.dp)
            )
            .hoverable(interactionSource)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 图标
        Icon(
            key = info.icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = info.color
        )
        
        // 文本内容
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = mode.displayName,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.normal
                )
            )
            Text(
                text = info.description,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                )
            )
        }
        
        // 选中标记
        if (isSelected) {
            Icon(
                key = AllIconsKeys.Actions.Checked,
                contentDescription = "Selected",
                modifier = Modifier.size(14.dp),
                tint = JewelTheme.globalColors.outlines.focused
            )
        }
    }
}

// 数据类
private data class ModelInfo(
    val icon: org.jetbrains.jewel.ui.icon.IconKey,
    val color: Color,
    val description: String
)

private data class PermissionModeInfo(
    val icon: org.jetbrains.jewel.ui.icon.IconKey,
    val color: Color,
    val description: String
)