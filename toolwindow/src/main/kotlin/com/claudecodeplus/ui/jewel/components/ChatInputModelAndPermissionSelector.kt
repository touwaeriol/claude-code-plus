/*
 * ChatInputModelAndPermissionSelector.kt
 * 
 * 聊天输入区域的模型选择器组件
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/**
 * 模型选择器组件
 */
@Composable
fun ChatInputModelSelector(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }
    val models = listOf(AiModel.OPUS, AiModel.SONNET, AiModel.OPUS_PLAN, AiModel.OPUS_4)
    
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
 */
@Composable
fun ChatInputPermissionSelector(
    currentPermissionMode: PermissionMode = PermissionMode.BYPASS,
    onPermissionModeChange: (PermissionMode) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }
    val permissionModes = listOf(
        PermissionMode.DEFAULT,
        PermissionMode.ACCEPT,
        PermissionMode.BYPASS,
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
 * 跳过权限复选框组件 - 强制使用文本格式的 []bypass 样式
 * 需求：必须显示为 []bypass 格式，而不是标准复选框
 */
@Composable
fun SkipPermissionsCheckbox(
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 强制使用文本格式，确保显示为 []bypass
    Row(
        modifier = modifier
            .clickable(enabled = enabled) {
                println("[SkipPermissionsCheckbox] 点击 - checked: $checked -> ${!checked}")
                onCheckedChange(!checked)
            }
            .background(
                if (checked && enabled) 
                    JewelTheme.globalColors.borders.focused.copy(alpha = 0.15f)
                else 
                    Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 强制文本格式的复选框 - 绝不使用 Jewel Checkbox
        Text(
            text = if (checked) "[✓]" else "[ ]",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                color = when {
                    !enabled -> JewelTheme.globalColors.text.disabled
                    checked -> JewelTheme.globalColors.borders.focused
                    else -> JewelTheme.globalColors.text.normal
                }
            )
        )
        
        Text(
            text = "bypass",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                color = when {
                    !enabled -> JewelTheme.globalColors.text.disabled
                    checked -> JewelTheme.globalColors.borders.focused
                    else -> JewelTheme.globalColors.text.normal
                }
            )
        )
    }
}