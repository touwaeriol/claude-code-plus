package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolType
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 智能工具调用展示组件
 * 按照新需求：简洁直观，每个工具调用独立显示，不进行分组
 * FR-2.14: 工具调用必须直接展示，不使用分组标题或计数显示
 */
@Composable
fun SmartToolCallDisplay(
    toolCalls: List<ToolCall>,
    modifier: Modifier = Modifier
) {
    // 如果没有工具调用，不显示任何内容
    if (toolCalls.isEmpty()) return
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 按照需求FR-2.14，去掉分组标题，直接显示每个工具调用
        toolCalls.forEach { toolCall ->
            println("[SmartToolCallDisplay] 处理工具调用：${toolCall.name}")
            when {
                // TodoWrite 工具特殊展示
                toolCall.name.contains("TodoWrite", ignoreCase = true) -> {
                    println("[SmartToolCallDisplay] ✅ 识别为TodoWrite工具，使用EnhancedTodoDisplay")
                    EnhancedTodoDisplay(
                        toolCall = toolCall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // 其他所有工具都使用统一的紧凑展示
                else -> {
                    println("[SmartToolCallDisplay] 使用CompactToolCallDisplay显示：${toolCall.name}")
                    CompactToolCallDisplay(
                        toolCalls = listOf(toolCall),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}


// 注意：工具函数已在 CompactToolCallDisplay.kt 中定义，这里不重复定义