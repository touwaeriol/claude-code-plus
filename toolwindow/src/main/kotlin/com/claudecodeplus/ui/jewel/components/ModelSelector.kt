package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.claudecodeplus.ui.models.AiModel
import org.jetbrains.jewel.ui.component.*
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle

/**
 * 模型选择器组件
 * 提供下拉选择 AI 模型的功能
 */
@Composable
fun ModelSelector(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🤖")
        
        Box {
            OutlinedButton(
                onClick = { expanded = !expanded },
                enabled = enabled,
                modifier = Modifier.widthIn(min = 200.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(currentModel.displayName)
                        Text(
                            currentModel.description,
                            style = LocalTextStyle.current.copy(
                                fontSize = LocalTextStyle.current.fontSize * 0.8f,
                                color = LocalContentColor.current.copy(alpha = 0.6f)
                            )
                        )
                    }
                    Text(if (expanded) "▲" else "▼")
                }
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 200.dp)
            ) {
                AiModel.values().forEach { model ->
                    DropdownMenuItem(
                        onClick = {
                            onModelChange(model)
                            expanded = false
                        }
                    ) {
                        Column {
                            Text(
                                model.displayName,
                                color = if (model == currentModel) Color.White else Color.LightGray
                            )
                            Text(
                                model.description,
                                style = LocalTextStyle.current.copy(
                                    fontSize = LocalTextStyle.current.fontSize * 0.8f,
                                    color = Color.Gray
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}