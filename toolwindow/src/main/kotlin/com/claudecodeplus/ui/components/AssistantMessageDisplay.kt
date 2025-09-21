package com.claudecodeplus.ui.components

import com.claudecodeplus.core.logging.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.jewel.components.tools.CompactToolCallDisplay
import com.claudecodeplus.ui.jewel.components.markdown.MarkdownRenderer
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
    expandedTools: Map<String, Boolean> = emptyMap(),  // 外部传入的展开状态
    onExpandedChange: ((String, Boolean) -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)  // 增加垂直间距，避免内容重叠
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

        // 按照 SDK 输出的时间顺序显示所有元素
    logD("[AssistantMessageDisplay] 显示 orderedElements，共 ${message.orderedElements.size} 个元素")

        // 收集所有工具调用以便统一显示
        val allToolCalls = message.orderedElements
            .filterIsInstance<MessageTimelineItem.ToolCallItem>()
            .map { it.toolCall }

        // 如果有工具调用，使用 CompactToolCallDisplay 统一显示
        if (allToolCalls.isNotEmpty()) {
            logD("[AssistantMessageDisplay] 🎯 显示工具调用: ${allToolCalls.size} 个工具")
            CompactToolCallDisplay(
                toolCalls = allToolCalls,
                modifier = Modifier.fillMaxWidth(),
                ideIntegration = ideIntegration,
                expandedTools = expandedTools,
                onExpandedChange = onExpandedChange
            )
        }

        // 显示文本内容
        val textContent = message.orderedElements
            .filterIsInstance<MessageTimelineItem.ContentItem>()
            .joinToString("") { it.content }

        if (textContent.isNotBlank()) {
            logD("[AssistantMessageDisplay] 🎯 渲染文本内容: ${textContent.take(100)}...")
            MarkdownRenderer(
                markdown = textContent,
                onLinkClick = { url ->
    logD("[AssistantMessageDisplay] 链接点击: $url")
                },
                onCodeAction = { code, language ->
    logD("[AssistantMessageDisplay] 代码操作: 语言=$language")
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        
        // 流式状态指示器已移至工具调用状态区域，此处不再显示
    }
}

// StreamingIndicator 函数已移除，生成状态现在统一在工具调用状态区域显示
