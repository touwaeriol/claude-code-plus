package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.*
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
                        ToolCallDisplay(
                            toolCalls = listOf(element.toolCall),
                            modifier = Modifier.fillMaxWidth()
                        )
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
                message.toolCalls.forEach { toolCall ->
                    ToolCallDisplay(
                        toolCalls = listOf(toolCall),
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
    status: String = "正在生成...",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "▌",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 13.sp,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
            )
        )
        Text(
            text = status,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 13.sp,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
            )
        )
    }
}