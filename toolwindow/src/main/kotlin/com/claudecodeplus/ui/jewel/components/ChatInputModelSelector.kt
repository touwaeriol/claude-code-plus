/*
 * ChatInputModelSelector.kt
 * 
 * 聊天输入区域的紧凑模型选择器组件
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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 紧凑的模型选择器组件
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
    var internalModel by remember(currentModel) { mutableStateOf(currentModel) }
    var showDropdown by remember { mutableStateOf(false) }
    val models = listOf(AiModel.OPUS, AiModel.SONNET)
    
    LaunchedEffect(currentModel) {
        if (internalModel != currentModel) {
            internalModel = currentModel
        }
    }
    
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
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = internalModel.displayName,
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
                        .width(100.dp)
                        .padding(2.dp)
                ) {
                    models.forEach { model ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    internalModel = model
                                    showDropdown = false
                                    onModelChange(model)
                                }
                                .background(
                                    if (model == internalModel) 
                                        JewelTheme.globalColors.borders.focused.copy(alpha = 0.15f)
                                    else 
                                        Color.Transparent,
                                    RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = model.displayName,
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 9.sp,
                                    color = if (model == internalModel) 
                                        JewelTheme.globalColors.text.normal 
                                    else 
                                        JewelTheme.globalColors.text.disabled
                                )
                            )
                        }
                    }
                }
            }
        }
    }
} 