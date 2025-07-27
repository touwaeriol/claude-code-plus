/*
 * ChatInputModelAndPermissionSelector.kt
 * 
 * 聊天输入区域的模型选择器组件
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.PermissionMode
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Checkbox

/**
 * 模型选择器组件
 * 
 * @param currentModel 当前选择的模型
 * @param onModelChange 模型变化回调
 * @param enabled 是否启用
 * @param modifier 修饰符
 */
@Composable
fun ChatInputModelSelector(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }
    val models = listOf(AiModel.OPUS, AiModel.SONNET)
    
    Box(modifier = modifier) {
            // 主按钮
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .background(
                        Color.Transparent,
                        RoundedCornerShape(3.dp)
                    )
                    .clickable(enabled = enabled) {
                        showDropdown = !showDropdown
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currentModel.displayName,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 9.sp,
                            color = JewelTheme.globalColors.text.normal
                        )
                    )
                    Text(
                        text = if (showDropdown) "▲" else "▼",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 7.sp,
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
            }
            
            // 下拉列表
            if (showDropdown) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, -8),
                    onDismissRequest = { showDropdown = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                JewelTheme.globalColors.panelBackground,
                                RoundedCornerShape(4.dp)
                            )
                            .width(180.dp)
                            .padding(4.dp)
                    ) {
                        models.forEach { model ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onModelChange(model)
                                        showDropdown = false
                                    }
                                    .background(
                                        if (model == currentModel) 
                                            JewelTheme.globalColors.borders.focused.copy(alpha = 0.15f)
                                        else 
                                            Color.Transparent,
                                        RoundedCornerShape(2.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Column {
                                    Text(
                                        text = model.displayName,
                                        style = JewelTheme.defaultTextStyle.copy(
                                            fontSize = 11.sp,
                                            color = if (model == currentModel) 
                                                JewelTheme.globalColors.text.normal 
                                            else 
                                                JewelTheme.globalColors.text.disabled
                                        )
                                    )
                                    Text(
                                        text = model.description,
                                        style = JewelTheme.defaultTextStyle.copy(
                                            fontSize = 9.sp,
                                            color = JewelTheme.globalColors.text.disabled
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

/**
 * 权限模式选择器组件
 * 
 * @param currentPermissionMode 当前权限模式
 * @param onPermissionModeChange 权限模式变化回调
 * @param enabled 是否启用
 * @param modifier 修饰符
 */
@Composable
fun ChatInputPermissionSelector(
    currentPermissionMode: PermissionMode = PermissionMode.BYPASS_PERMISSIONS,
    onPermissionModeChange: (PermissionMode) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }
    val permissionModes = listOf(
        PermissionMode.DEFAULT,
        PermissionMode.ACCEPT_EDITS,
        PermissionMode.BYPASS_PERMISSIONS,
        PermissionMode.PLAN
    )
    
    Box(modifier = modifier) {
        // 主按钮
        Box(
            modifier = Modifier
                .height(24.dp)
                .background(
                    Color.Transparent,
                    RoundedCornerShape(3.dp)
                )
                .clickable(enabled = enabled) {
                    showDropdown = !showDropdown
                }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = currentPermissionMode.displayName,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 9.sp,
                        color = JewelTheme.globalColors.text.normal
                    )
                )
                Text(
                    text = if (showDropdown) "▲" else "▼",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 7.sp,
                        color = JewelTheme.globalColors.text.disabled
                    )
                )
            }
        }
        
        // 下拉列表
        if (showDropdown) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, -8),
                onDismissRequest = { showDropdown = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            JewelTheme.globalColors.panelBackground,
                            RoundedCornerShape(4.dp)
                        )
                        .width(200.dp)
                        .padding(4.dp)
                ) {
                    permissionModes.forEach { mode ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPermissionModeChange(mode)
                                    showDropdown = false
                                }
                                .background(
                                    if (mode == currentPermissionMode) 
                                        JewelTheme.globalColors.borders.focused.copy(alpha = 0.15f)
                                    else 
                                        Color.Transparent,
                                    RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text(
                                    text = mode.displayName,
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = 11.sp,
                                        color = if (mode == currentPermissionMode) 
                                            JewelTheme.globalColors.text.normal 
                                        else 
                                            JewelTheme.globalColors.text.disabled
                                    )
                                )
                                Text(
                                    text = mode.description,
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = 9.sp,
                                        color = JewelTheme.globalColors.text.disabled
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 跳过权限指示器组件（只读，始终为真）
 * 
 * @param modifier 修饰符
 */
@Composable
fun SkipPermissionsIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Checkbox(
            checked = true,
            onCheckedChange = {}, // 空操作，因为不可更改
            enabled = false
        )
        Text(
            text = "跳过权限",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 9.sp,
                color = JewelTheme.globalColors.text.disabled
            )
        )
    }
}