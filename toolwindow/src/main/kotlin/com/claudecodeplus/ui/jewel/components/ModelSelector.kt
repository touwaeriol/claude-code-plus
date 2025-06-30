package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.AiModel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*

/**
 * 模型选择器组件 - 简化版本
 */
@Composable
fun ModelSelector(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🤖")
        
        DefaultButton(
            onClick = { 
                // 简单的模型切换逻辑
                val nextModel = when (currentModel) {
                    AiModel.SONNET -> AiModel.OPUS
                    AiModel.OPUS -> AiModel.SONNET
                }
                onModelChange(nextModel)
            },
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
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
                Text("↻")
            }
        }
    }
}