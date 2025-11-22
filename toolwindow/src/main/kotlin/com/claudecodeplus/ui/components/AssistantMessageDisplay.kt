package com.claudecodeplus.ui.components

import com.claudecodeplus.core.logging.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.jewel.components.markdown.MarkdownRenderer
import com.claudecodeplus.ui.jewel.components.tools.CompactToolCallDisplay
import com.claudecodeplus.ui.jewel.components.tools.JumpingDots
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageTimelineItem
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun AssistantMessageDisplay(
    message: EnhancedMessage,
    modifier: Modifier = Modifier,
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration? = null,
    expandedTools: Map<String, Boolean> = emptyMap(),
    onExpandedChange: ((String, Boolean) -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        message.model?.let { model ->
            SelectionContainer {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "AI",
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

        val orderedElements = message.orderedElements
        if (orderedElements.isNotEmpty()) {
            logD("[AssistantMessageDisplay] 顺序渲染 ${orderedElements.size} 个 orderedElements")
            orderedElements.forEachIndexed { index, element ->
                val elementKey = "${message.id}-${element.timestamp}-$index-${element::class.simpleName}"
                when (element) {
                    is MessageTimelineItem.ContentItem -> {
                        val content = element.content
                        if (content.isNotBlank()) {
                            key(elementKey) {
                                logD("[AssistantMessageDisplay] 渲染文本片段(${index + 1}/${orderedElements.size}): ${content.take(80)}...")
                                MarkdownRenderer(
                                    markdown = content,
                                    onLinkClick = { url -> logD("[AssistantMessageDisplay] 链接点击: $url") },
                                    onCodeAction = { _, language -> logD("[AssistantMessageDisplay] 代码操作: 语言=$language") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    is MessageTimelineItem.ToolCallItem -> {
                        val toolCall = element.toolCall
                        key(elementKey) {
                            logD("[AssistantMessageDisplay] 渲染工具调用: ${toolCall.name} (${toolCall.id})")
                            CompactToolCallDisplay(
                                toolCalls = listOf(toolCall),
                                modifier = Modifier.fillMaxWidth(),
                                ideIntegration = ideIntegration,
                                expandedTools = expandedTools,
                                onExpandedChange = onExpandedChange
                            )
                        }
                    }
                    is MessageTimelineItem.StatusItem -> {
                        key(elementKey) {
                            StatusMessageRow(status = element)
                        }
                    }
                }
            }
        } else if (message.content.isNotBlank()) {
            logD("[AssistantMessageDisplay] 无 orderedElements，渲染整体文本: ${message.content.take(80)}...")
            MarkdownRenderer(
                markdown = message.content,
                onLinkClick = { url -> logD("[AssistantMessageDisplay] 链接点击: $url") },
                onCodeAction = { _, language -> logD("[AssistantMessageDisplay] 代码操作: 语言=$language") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusMessageRow(status: MessageTimelineItem.StatusItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (status.isStreaming) {
            JumpingDots(
                modifier = Modifier.padding(end = 2.dp)
            )
        }
        Text(
            text = status.status,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
            )
        )
    }
}
