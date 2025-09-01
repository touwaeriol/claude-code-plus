/*
 * ChatInputModelAndPermissionSelector.kt
 * 
 * 聊天输入区域的模型选择器组件 - 使用Jewel标准组件
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.PermissionMode
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*

/**
 * 模型选择器组件 - 使用Jewel ListComboBox，支持紧凑和标准模式
 */
@OptIn(ExperimentalJewelApi::class)
@Composable
fun ChatInputModelSelector(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val models = listOf(AiModel.OPUS, AiModel.SONNET, AiModel.OPUS_PLAN, AiModel.OPUS_4)
    val currentIndex = models.indexOf(currentModel).takeIf { it >= 0 } ?: 0
    
    ListComboBox(
        items = models,
        selectedIndex = currentIndex,
        onSelectedItemChange = { index ->
            if (index in models.indices) {
                onModelChange(models[index])
            }
        },
        enabled = enabled,
        modifier = modifier.widthIn(
            min = if (compact) 60.dp else 80.dp, 
            max = if (compact) 90.dp else 120.dp
        ),
        itemKeys = { _, model -> model.name },
        itemContent = { model, isSelected, _ ->
            if (compact) {
                // 紧凑模式：仅显示简称
                Text(
                    text = model.shortName,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = if (isSelected) 
                            JewelTheme.globalColors.text.selected 
                        else 
                            JewelTheme.globalColors.text.normal
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            } else {
                // 标准模式：显示完整信息
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = model.displayName,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = if (isSelected) 
                                JewelTheme.globalColors.text.selected 
                            else 
                                JewelTheme.globalColors.text.normal
                        )
                    )
                    Text(
                        text = model.description,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 10.sp,
                            color = JewelTheme.globalColors.text.disabled
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    )
}

/**
 * 权限模式选择器组件 - 使用Jewel ListComboBox，支持紧凑和标准模式
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
        // 图标模式：仅显示图标按钮
        IconActionButton(
            onClick = {
                // 循环切换到下一个权限模式
                val nextIndex = (currentIndex + 1) % permissionModes.size
                onPermissionModeChange(permissionModes[nextIndex])
            },
            enabled = enabled,
            modifier = modifier.size(32.dp)
        ) {
            Text(
                text = currentPermissionMode.icon,
                style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
            )
        }
    } else {
        ListComboBox(
            items = permissionModes,
            selectedIndex = currentIndex,
            onSelectedItemChange = { index ->
                if (index in permissionModes.indices) {
                    onPermissionModeChange(permissionModes[index])
                }
            },
            enabled = enabled,
            modifier = modifier.widthIn(
                min = if (compact) 70.dp else 90.dp,
                max = if (compact) 100.dp else 130.dp
            ),
            itemKeys = { _, mode -> mode.name },
            itemContent = { mode, isSelected, _ ->
                if (compact) {
                    // 紧凑模式：显示图标+简称
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = mode.icon,
                            style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                        )
                        Text(
                            text = mode.shortName,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp,
                                color = if (isSelected) 
                                    JewelTheme.globalColors.text.selected 
                                else 
                                    JewelTheme.globalColors.text.normal
                            )
                        )
                    }
                } else {
                    // 标准模式：显示完整信息
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = mode.displayName,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 12.sp,
                                color = if (isSelected) 
                                    JewelTheme.globalColors.text.selected 
                                else 
                                    JewelTheme.globalColors.text.normal
                            )
                        )
                        Text(
                            text = mode.description,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 10.sp,
                                color = JewelTheme.globalColors.text.disabled
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        )
    }
}

/**
 * 跳过权限复选框组件 - 使用Jewel Checkbox组件，现代化外观
 */
@Composable
fun SkipPermissionsCheckbox(
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { newValue ->
                println("[SkipPermissionsCheckbox] Jewel Checkbox 点击 - checked: $checked -> $newValue")
                onCheckedChange(newValue)
            },
            enabled = enabled
        )
        Text(
            text = "bypass",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                color = if (enabled) 
                    JewelTheme.globalColors.text.normal 
                else 
                    JewelTheme.globalColors.text.disabled
            )
        )
    }
}