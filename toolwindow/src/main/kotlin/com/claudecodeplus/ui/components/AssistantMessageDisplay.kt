package com.claudecodeplus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.jewel.components.tools.CompactToolCallDisplay
import com.claudecodeplus.ui.jewel.components.MarkdownRenderer
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
    modifier: Modifier = Modifier,
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration? = null,
    onExpandedChange: ((String, Boolean) -> Unit)? = null
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
        
        // 显示工具调用（如果有）
        if (message.toolCalls.isNotEmpty()) {
            CompactToolCallDisplay(
                toolCalls = message.toolCalls,
                modifier = Modifier.fillMaxWidth(),
                ideIntegration = ideIntegration,
                onExpandedChange = onExpandedChange
            )
        }
        
        // 显示主要文本内容
        if (message.content.isNotBlank()) {
            SelectionContainer {
                MarkdownRenderer(
                    markdown = message.content,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // 如果有按时间顺序的元素，也显示它们（用于流式内容）
        if (message.orderedElements.isNotEmpty()) {
            message.orderedElements.forEach { element ->
                when (element) {
                    is MessageTimelineItem.ToolCallItem -> {
                        // 工具调用已在上面显示，这里跳过避免重复
                    }
                    is MessageTimelineItem.ContentItem -> {
                        if (element.content.isNotBlank() && element.content != message.content) {
                            SelectionContainer {
                                MarkdownRenderer(
                                    markdown = element.content,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    is MessageTimelineItem.StatusItem -> {
                        if (element.isStreaming) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = element.status,
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = 12.sp,
                                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                                    )
                                )
                                JumpingDots()
                            }
                        }
                    }
                }
            }
        }
        
        // 流式状态指示器已移至工具调用状态区域，此处不再显示
    }
}

// StreamingIndicator 函数已移除，生成状态现在统一在工具调用状态区域显示