package com.claudecodeplus.ui.redesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.AIModel
import com.claudecodeplus.ui.models.AIModels
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text

/**
 * 控制栏 - 包含模型选择器和其他控制按钮
 */
@Composable
fun ConversationControlBar(
    currentModel: AIModel,
    onModelChange: (AIModel) -> Unit,
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
    currentModel: AIModel,
    onModelChange: (AIModel) -> Unit,
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
            AIModels.DEFAULT_MODELS.forEach { model ->
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
                            
                            // 显示模型能力
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                model.capabilities.take(3).forEach { capability ->
                                    Text(
                                        when (capability) {
                                            com.claudecodeplus.ui.models.ModelCapability.DEEP_REASONING -> "深度推理"
                                            com.claudecodeplus.ui.models.ModelCapability.CODE_GENERATION -> "代码生成"
                                            com.claudecodeplus.ui.models.ModelCapability.CODE_REVIEW -> "代码审查"
                                            com.claudecodeplus.ui.models.ModelCapability.REFACTORING -> "重构"
                                            com.claudecodeplus.ui.models.ModelCapability.DEBUGGING -> "调试"
                                            com.claudecodeplus.ui.models.ModelCapability.FAST_RESPONSE -> "快速响应"
                                        },
                                        style = JewelTheme.defaultTextStyle.copy(
                                            color = Color(0xFF59A869),
                                            fontSize = JewelTheme.defaultTextStyle.fontSize * 0.8f
                                        ),
                                        modifier = Modifier
                                            .background(
                                                Color(0xFF59A869).copy(alpha = 0.2f),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}