package com.claudecodeplus.ui.redesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.AiModel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text

/**
 * 控制栏 - 包含模型选择器和其他控制按钮
 */
@Composable
fun ConversationControlBar(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    onClearChat: () -> Unit,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2B2B2B))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 模型选择器
        ModelSelector(
            currentModel = currentModel,
            onModelChange = onModelChange
        )
        
        // 控制按钮组
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DefaultButton(
                onClick = onClearChat
            ) {
                Text("清除对话")
            }
            
            DefaultButton(
                onClick = onShowHistory
            ) {
                Text("历史记录")
            }
        }
    }
}

/**
 * 模型选择器组件
 */
@Composable
private fun ModelSelector(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        // 当前选中的模型
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .background(
                    Color(0xFF3C3F41),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "🤖",
                style = JewelTheme.defaultTextStyle
            )
            
            Column {
                Text(
                    currentModel.displayName,
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color.White
                    )
                )
                Text(
                    currentModel.description,
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFF999999),
                        fontSize = JewelTheme.defaultTextStyle.fontSize * 0.8f
                    )
                )
            }
            
            Text(
                "▼",
                style = JewelTheme.defaultTextStyle.copy(
                    color = Color(0xFF999999)
                )
            )
        }
        
        // 下拉菜单
        JewelDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listOf(AiModel.OPUS, AiModel.SONNET, AiModel.SONNET_35).forEach { model ->
                JewelDropdownMenuItem(
                    onClick = {
                        onModelChange(model)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (model == currentModel) Color(0xFF4C5052) else Color.Transparent
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            "•",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = if (model == currentModel) Color(0xFF3574F0) else Color(0xFF999999)
                            )
                        )
                        
                        Column {
                            Text(
                                model.displayName,
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = Color.White
                            )
                            )
                            Text(
                                model.description,
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = Color(0xFF999999),
                                    fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                                )
                            )
                            
                            // 显示模型特点 - 简化显示
                            Text(
                                model.description,
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = Color(0xFF59A869),
                                    fontSize = JewelTheme.defaultTextStyle.fontSize * 0.8f
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}