package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 队列指示器组件
 * 显示待处理的问题队列状态
 */
@Composable
fun QueueIndicator(
    queue: List<String>,
    isGenerating: Boolean,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onQueueClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 主状态文本
        when {
            isGenerating && queue.isEmpty() -> {
                Text(
                    "Generating...",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = JewelTheme.globalColors.text.disabled,
                        fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                    )
                )
            }
            isGenerating && queue.isNotEmpty() -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "${queue.size} in queue",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.info,
                            fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                        )
                    )
                    Text(
                        "•",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled,
                            fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                        )
                    )
                    Text(
                        "Generating...",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled,
                            fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                        )
                    )
                }
            }
            queue.isNotEmpty() -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 队列数量
                    Text(
                        "${queue.size} in queue",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.info,
                            fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                        )
                    )
                    
                    // 第一个问题预览
                    Text(
                        "•",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled,
                            fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                        )
                    )
                    
                    Text(
                        queue.first().take(50).let { 
                            if (queue.first().length > 50) "$it..." else it 
                        },
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled,
                            fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, false)
                    )
                }
            }
        }
        
        // 停止按钮提示（可选）
        if (isGenerating) {
            Text(
                "按 Stop 停止生成",
                style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.disabled,
                    fontSize = JewelTheme.defaultTextStyle.fontSize * 0.8f
                )
            )
        }
    }
}