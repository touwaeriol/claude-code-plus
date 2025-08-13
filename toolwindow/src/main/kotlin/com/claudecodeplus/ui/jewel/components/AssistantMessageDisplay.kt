package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.jewel.components.tools.SmartToolCallDisplay
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
            SelectionContainer {
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
        }
        
        // 按时间顺序显示消息元素
        if (message.orderedElements.isNotEmpty()) {
            // 收集所有工具调用以便批量显示
            val toolCalls = mutableListOf<ToolCall>()
            val contentItems = mutableListOf<MessageTimelineItem.ContentItem>()
            
            message.orderedElements.forEach { element ->
                when (element) {
                    is MessageTimelineItem.ToolCallItem -> {
                        toolCalls.add(element.toolCall)
                    }
                    is MessageTimelineItem.ContentItem -> {
                        // 如果之前有工具调用，先显示它们
                        if (toolCalls.isNotEmpty()) {
                            SmartToolCallDisplay(
                                toolCalls = toolCalls.toList(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            toolCalls.clear()
                        }
                        contentItems.add(element)
                    }
                    is MessageTimelineItem.StatusItem -> {
                        // 如果之前有工具调用，先显示它们
                        if (toolCalls.isNotEmpty()) {
                            SmartToolCallDisplay(
                                toolCalls = toolCalls.toList(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            toolCalls.clear()
                        }
                        // 显示之前的内容
                        contentItems.forEach { item ->
                            if (item.content.isNotBlank()) {
                                MarkdownRenderer(
                                    markdown = item.content,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        contentItems.clear()
                        
                        if (element.isStreaming) {
                            StreamingIndicator(status = element.status)
                        }
                    }
                }
            }
            
            // 显示剩余的工具调用
            if (toolCalls.isNotEmpty()) {
                SmartToolCallDisplay(
                    toolCalls = toolCalls,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // 显示剩余的内容
            contentItems.forEach { item ->
                if (item.content.isNotBlank()) {
                    MarkdownRenderer(
                        markdown = item.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            // 向后兼容：如果没有 orderedElements，使用旧的显示方式
            // 使用智能工具调用显示
            if (message.toolCalls.isNotEmpty()) {
                SmartToolCallDisplay(
                    toolCalls = message.toolCalls,
                    modifier = Modifier.fillMaxWidth()
                )
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