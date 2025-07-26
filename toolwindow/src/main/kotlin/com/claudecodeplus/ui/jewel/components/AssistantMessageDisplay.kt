package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.jewel.components.tools.CompactToolCallDisplay
import com.claudecodeplus.ui.jewel.components.tools.TodoListDisplay
import com.claudecodeplus.ui.jewel.components.tools.JumpingDots
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import java.text.SimpleDateFormat
import java.util.*

/**
 * AI 助手消息显示组件
 * 专门用于显示 AI 的回复，支持 Markdown 渲染和工具调用显示
 */
@Composable
fun AssistantMessageDisplay(
    message: EnhancedMessage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 显示模型信息（如果有）
        message.model?.let { model ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "🤖",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                )
                Text(
                    text = model.displayName,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                    )
                )
            }
        }
        
        // 按时间顺序显示消息元素
        if (message.orderedElements.isNotEmpty()) {
            message.orderedElements.forEach { element ->
                when (element) {
                    is MessageTimelineItem.ToolCallItem -> {
                        // 使用新的紧凑工具调用显示
                        if (element.toolCall.name.contains("TodoWrite", ignoreCase = true)) {
                            // TodoWrite 使用专属展示
                            TodoListDisplay(
                                toolCall = element.toolCall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // 其他工具使用紧凑展示
                            CompactToolCallDisplay(
                                toolCalls = listOf(element.toolCall),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    is MessageTimelineItem.ContentItem -> {
                        if (element.content.isNotBlank()) {
                            MarkdownRenderer(
                                markdown = element.content,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    is MessageTimelineItem.StatusItem -> {
                        if (element.isStreaming) {
                            StreamingIndicator(status = element.status)
                        }
                    }
                }
            }
        } else {
            // 向后兼容：如果没有 orderedElements，使用旧的显示方式
            // 先显示工具调用
            if (message.toolCalls.isNotEmpty()) {
                // 将工具按类型分组
                val todoToolCalls = message.toolCalls.filter { it.name.contains("TodoWrite", ignoreCase = true) }
                val otherToolCalls = message.toolCalls.filter { !it.name.contains("TodoWrite", ignoreCase = true) }
                
                // 显示 TodoWrite 工具
                todoToolCalls.forEach { toolCall ->
                    TodoListDisplay(
                        toolCall = toolCall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // 显示其他工具
                if (otherToolCalls.isNotEmpty()) {
                    CompactToolCallDisplay(
                        toolCalls = otherToolCalls,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // 然后显示消息内容
            if (message.content.isNotBlank()) {
                MarkdownRenderer(
                    markdown = message.content,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // 流式状态指示器
        if (message.isStreaming) {
            StreamingIndicator()
        }
    }
}

/**
 * 流式响应指示器
 */
@Composable
private fun StreamingIndicator(
    status: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 使用跳动的点代替文字
        JumpingDots(
            dotSize = 5.dp,
            dotSpacing = 3.dp,
            jumpHeight = 3.dp,
            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
        )
        
        // 如果有自定义状态文字，显示它
        if (status.isNotBlank()) {
            Text(
                text = status,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 13.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                )
            )
        }
    }
}