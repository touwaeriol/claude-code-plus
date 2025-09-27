package com.claudecodeplus.ui.components

import com.claudecodeplus.core.logging.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.jewel.components.*
import com.claudecodeplus.ui.jewel.components.markdown.MarkdownRenderer
import com.claudecodeplus.ui.models.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.foundation.rememberScrollState
import java.text.SimpleDateFormat
import java.util.*

/**
 * 重新设计的对话视图
 * 集成了模型选择、Markdown 渲染、工具调用显示等功能
 * 使用 Jewel 组件替代原生 Compose 组件以获得更好的 IntelliJ 集成
 */
@Composable
fun JewelConversationView(
    messages: List<EnhancedMessage>,
    onSend: (textWithMarkdown: String) -> Unit,
    onStop: (() -> Unit)? = null,
    contexts: List<ContextReference> = emptyList(),
    onContextAdd: (ContextReference) -> Unit = {},
    onContextRemove: (ContextReference) -> Unit = {},
    isGenerating: Boolean = false,
    selectedModel: AiModel = AiModel.OPUS,
    onModelChange: (AiModel) -> Unit = {},
    selectedPermissionMode: PermissionMode = PermissionMode.BYPASS,
    onPermissionModeChange: (PermissionMode) -> Unit = {},
    // skipPermissions 默认为 true，不再可修改
    onClearChat: () -> Unit = {},
    fileIndexService: com.claudecodeplus.ui.services.FileIndexService? = null,
    projectService: com.claudecodeplus.core.services.ProjectService? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 聊天头部工具栏
        ChatHeader(
            onClearChat = onClearChat
        )
        
        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.height(1.dp)
        )
        
        // 消息列表区域 - 使用 Jewel 的滚动容器
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            VerticallyScrollableContainer(
                scrollState = rememberScrollState(),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    messages.forEach { message ->
                        MessageBubble(message = message)
                    }
                }
            }
        }
        
        // 分隔线 - 使用 Jewel 的 Divider
        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.height(1.dp)
        )
        
        // 输入区域 - 使用新的 UnifiedInputArea 组件
        UnifiedInputArea(
            mode = InputAreaMode.INPUT,
            onSend = onSend,
            onStop = onStop,
            contexts = contexts,
            onContextAdd = onContextAdd,
            onContextRemove = onContextRemove,
            isGenerating = isGenerating,
            enabled = !isGenerating,
            selectedModel = selectedModel,
            onModelChange = onModelChange,
            selectedPermissionMode = selectedPermissionMode,
            onPermissionModeChange = onPermissionModeChange,
            // skipPermissions 默认为 true，不再传递
            fileIndexService = fileIndexService,
            projectService = projectService,
            modifier = Modifier.fillMaxWidth()
        )
    }
}



/**
 * 简化的上下文显示组件 - 不包含删除功能
 */
@Composable
private fun SimpleContextDisplay(
    context: ContextReference,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 类型图标
        Text(
            text = when (context) {
                is ContextReference.FileReference -> "📄"
                is ContextReference.WebReference -> "🌐"
                is ContextReference.FolderReference -> "📁"
                is ContextReference.SymbolReference -> "🔗"
                is ContextReference.TerminalReference -> "💻"
                is ContextReference.ProblemsReference -> "⚠️"
                is ContextReference.GitReference -> "🔀"
                is ContextReference.ImageReference -> "🖼"
                ContextReference.SelectionReference -> "✏️"
                ContextReference.WorkspaceReference -> "🏠"
            },
            style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp)
        )
        
        // 显示文本
        Text(
            text = when (context) {
                is ContextReference.FileReference -> {
                    val filename = context.path.substringAfterLast('/')
                        .ifEmpty { context.path.substringAfterLast('\\') }
                        .ifEmpty { context.path }
                    filename
                }
                is ContextReference.WebReference -> {
                    context.title ?: context.url.substringAfterLast('/')
                }
                is ContextReference.FolderReference -> "${context.path.substringAfterLast('/')} (${context.fileCount}个文件)"
                is ContextReference.SymbolReference -> context.name
                is ContextReference.TerminalReference -> "终端输出"
                is ContextReference.ProblemsReference -> "问题报告 (${context.problems.size}个)"
                is ContextReference.GitReference -> "Git ${context.type.name}"
                is ContextReference.ImageReference -> context.filename
                ContextReference.SelectionReference -> "选择内容"
                ContextReference.WorkspaceReference -> "工作区"
            },
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                color = JewelTheme.globalColors.text.normal
            )
        )
    }
}



/**
 * 消息气泡组件 - 使用 Jewel Text 组件，优化显示格式
 */
@Composable
private fun MessageBubble(
    message: EnhancedMessage,
    modifier: Modifier = Modifier
) {
    // 所有消息统一布局，不再左右区分
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (message.role == MessageRole.USER) 
                    JewelTheme.globalColors.borders.focused.copy(alpha = 0.08f)
                else 
                    JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (message.role == MessageRole.USER)
                    JewelTheme.globalColors.borders.focused.copy(alpha = 0.15f)
                else
                    JewelTheme.globalColors.borders.normal.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .padding(
                if (message.role == MessageRole.USER) 
                    PaddingValues(0.dp) // UnifiedInputArea has its own padding
                else 
                    PaddingValues(16.dp)
            )
    ) {
        if (message.role == MessageRole.USER) {
            // 用户消息 - 使用新的 UnifiedInputArea 的 DISPLAY 模式
            UnifiedInputArea(
                mode = InputAreaMode.DISPLAY,
                message = message,
                onContextClick = { uri ->
                    // 处理上下文点击
                    // Context clicked: $uri
                    // TODO: 实现实际的点击处理逻辑
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Assistant消息内容
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 显示模型信息（如果有，且消息有内容）
                if (message.content.isNotBlank() || message.orderedElements.isNotEmpty()) {
                    message.model?.let { model ->
                        if (model.displayName.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🤖"
                                )
                                Text(
                                    text = model.displayName
                                )
                            }
                        }
                    }
                }
                
                // 按 SDK 输出的时间顺序显示消息元素
    logD("[JewelConversationView] 显示 AI消息 orderedElements: ${message.orderedElements.size} 个元素")

                message.orderedElements.forEach { element ->
                    when (element) {
                        is MessageTimelineItem.ToolCallItem -> {
                            SimpleToolCallDisplay(element.toolCall)
                        }
                        is MessageTimelineItem.ContentItem -> {
                            if (element.content.isNotBlank()) {
    logD("[JewelConversationView] 渲染文本内容: ${element.content.take(100)}")
                                MarkdownRenderer(
                                    markdown = element.content,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        is MessageTimelineItem.StatusItem -> {
                            if (element.isStreaming) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "▌"
                                    )
                                    Text(
                                        text = element.status
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 流式状态指示器
                if (message.isStreaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "▌"
                        )
                        Text(
                            text = "正在生成..."
                        )
                    }
                }
                
                // 移除时间戳行，避免在工具调用后出现“时间条/空白”
            }
        }
    }
}

/**
 * 简洁的工具调用显示 - 类似 Cursor 的样式
 */
@Composable
private fun SimpleToolCallDisplay(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    // DEBUG: Rendering tool call: ${toolCall.name} with status: ${toolCall.status}
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 工具图标
        Text(
            text = when {
                toolCall.name.contains("LS", ignoreCase = true) -> "📁"
                toolCall.name.contains("Read", ignoreCase = true) -> "📖"
                toolCall.name.contains("Edit", ignoreCase = true) -> "✏️"
                toolCall.name.contains("Write", ignoreCase = true) -> "📝"
                toolCall.name.contains("Bash", ignoreCase = true) -> "💻"
                toolCall.name.contains("Search", ignoreCase = true) -> "🔍"
                toolCall.name.contains("Grep", ignoreCase = true) -> "🔍"
                toolCall.name.contains("Web", ignoreCase = true) -> "🌐"
                toolCall.name.contains("Git", ignoreCase = true) -> "🔀"
                else -> "🔧"
            }
        )
        
        // 工具名称
        Text(
            text = toolCall.name
        )
        
        // 状态指示
        when (toolCall.status) {
            ToolCallStatus.PENDING -> {
                Text(
                    text = "⏳"
                )
            }
            ToolCallStatus.RUNNING -> {
                Text(
                    text = "⚡"
                )
            }
            ToolCallStatus.SUCCESS -> {
                Text(
                    text = "✅"
                )
            }
            ToolCallStatus.FAILED -> {
                Text(
                    text = "❌"
                )
            }
            ToolCallStatus.CANCELLED -> {
                Text(
                    text = "🚫"
                )
            }
        }
    }
}

/**
 * 聊天头部工具栏
 */
@Composable
private fun ChatHeader(
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Claude Code Plus"
        )
    }
}




