/*
 * SmartToolCallDisplay.kt
 * 
 * 智能工具调用显示组件
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 智能工具调用显示组件 - 支持单个工具调用
 */
@Composable
fun SmartToolCallDisplay(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(6.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 工具标题
        Text(
            text = "⚡ ${toolCall.name}",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 14.sp,
                color = JewelTheme.globalColors.text.normal
            )
        )
        
        // 简化的参数显示
        if (toolCall.parameters.isNotEmpty()) {
            Text(
                text = "参数: ${toolCall.parameters.size} 个",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.disabled
                )
            )
        }
    }
}

/**
 * 智能工具调用显示组件 - 支持多个工具调用
 */
@Composable
fun SmartToolCallDisplay(
    toolCalls: List<ToolCall>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        toolCalls.forEach { toolCall ->
            SmartToolCallDisplay(
                toolCall = toolCall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}