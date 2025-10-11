/*
 * ChatInputModelAndPermissionSelector.kt
 * 
 * 聊天输入区域的模型选择器组件 - 使用Jewel标准组件
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.PermissionMode
import com.claudecodeplus.ui.jewel.components.utils.ComboBoxChevronIcon
import com.claudecodeplus.ui.jewel.components.utils.IconUtils
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*

/**
 * 模型选择器组件 - 使用标准Jewel ListComboBox组件
 *
 * @param currentModel 当前选择的模型别名（default/opus/sonnet/opusplan）
 * @param actualModelId 实际使用的模型ID（如 "claude-sonnet-4-5-20250929"），从 systemInit 消息获取
 * @param onModelChange 模型切换回调
 * @param enabled 是否启用
 * @param compact 是否紧凑显示
 */
@OptIn(ExperimentalJewelApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun ChatInputModelSelector(
    currentModel: AiModel,
    actualModelId: String? = null,
    onModelChange: (AiModel) -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val displayText = actualModelId?.takeIf { it.isNotBlank() } ?: currentModel.cliName
    Text(
        text = displayText,
        style = JewelTheme.defaultTextStyle.copy(
            fontSize = if (compact) 11.sp else 12.sp,
            color = JewelTheme.globalColors.text.info
        ),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        modifier = modifier
            .widthIn(min = if (compact) 120.dp else 180.dp)
    )
}

/**
 * 权限模式选择器组件 - 使用标准Jewel ListComboBox组件
 */
@OptIn(ExperimentalJewelApi::class)
@Composable
fun ChatInputPermissionSelector(
    currentPermissionMode: PermissionMode = PermissionMode.BYPASS,
    onPermissionModeChange: (PermissionMode) -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val permissionModes = listOf(
        PermissionMode.DEFAULT,
        PermissionMode.ACCEPT,
        PermissionMode.BYPASS,
        PermissionMode.PLAN
    )
    val currentIndex = permissionModes.indexOf(currentPermissionMode).takeIf { it >= 0 } ?: 0
    
    if (iconOnly) {
        // 图标模式：使用简单的文本按钮显示权限模式图标
        Box(
            modifier = modifier
                .size(32.dp)
                .background(
                    color = if (enabled) JewelTheme.globalColors.borders.normal else JewelTheme.globalColors.borders.disabled,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable(enabled = enabled) {
                    // 循环切换到下一个权限模式
                    val nextIndex = (currentIndex + 1) % permissionModes.size
                    onPermissionModeChange(permissionModes[nextIndex])
                }
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentPermissionMode.icon,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 14.sp,
                    color = if (enabled) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.disabled
                )
            )
        }
    } else {
        ListComboBox(
            items = permissionModes,
            selectedIndex = currentIndex,
            onSelectedItemChange = { index -> onPermissionModeChange(permissionModes[index]) },
            enabled = enabled,
            modifier = modifier.widthIn(
                min = if (compact) 100.dp else 160.dp,
                max = if (compact) 160.dp else 240.dp
            ),
            itemKeys = { _, item -> item.name },
            itemContent = { mode, isSelected, isActive ->
                if (compact) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = mode.icon,
                            style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            text = mode.shortName,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp,
                                color = when {
                                    !enabled -> JewelTheme.globalColors.text.disabled
                                    isSelected -> JewelTheme.globalColors.text.selected
                                    else -> JewelTheme.globalColors.text.normal
                                }
                            )
                        )
                    }
                } else {
                    Text(
                        text = mode.displayName,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = when {
                                !enabled -> JewelTheme.globalColors.text.disabled
                                isSelected -> JewelTheme.globalColors.text.selected
                                else -> JewelTheme.globalColors.text.normal
                            }
                        ),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
            }
        )
    }
}

/**
 * 权限跳过开关组件 - 使用 Jewel 原生 Checkbox 组件
 */
@Composable
fun SkipPermissionsToggle(
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (iconOnly) {
        // 仅图标模式：使用原生 Checkbox，无文本标签
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = modifier
        )
    } else {
        // 带标签模式：使用 Row 布局包含 Checkbox 和文本
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
            modifier = modifier
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
            
            Text(
                text = "bypass",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = if (compact) 10.sp else 11.sp,
                    color = if (enabled) 
                        JewelTheme.globalColors.text.normal 
                    else 
                        JewelTheme.globalColors.text.disabled
                )
            )
        }
    }
}

/**
 * 保持向后兼容性的别名函数
 */
@Composable
fun SkipPermissionsCheckbox(
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    SkipPermissionsToggle(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        compact = compact,
        iconOnly = iconOnly,
        modifier = modifier
    )
}

/**
 * 自动清理上下文复选框 - 已隐藏，默认启用
 * 注意：此功能现在默认为 true 且不显示 UI 控件
 */
@Composable
fun AutoCleanupContextsCheckbox(
    checked: Boolean = true, // 默认总是启用
    onCheckedChange: (Boolean) -> Unit = {}, // 不再接受用户输入
    enabled: Boolean = true,
    compact: Boolean = false,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 功能已隐藏 - 不渲染任何 UI
    // 自动清理上下文功能现在默认启用且无法关闭
}
