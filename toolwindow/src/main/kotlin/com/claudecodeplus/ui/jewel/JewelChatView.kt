package com.claudecodeplus.ui.jewel

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.ui.models.Message
import com.claudecodeplus.ui.models.MessageRole
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation

/**
 * 基于 Jewel 的聊天视图组件
 * 完全独立于 IDEA 平台，可在任何 Compose Desktop 应用中使用
 * 直接使用 ClaudeCliWrapper 进行 AI 对话
 */
@Preview
@Composable
fun JewelChatView(
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String = System.getProperty("user.dir"),
    themeConfig: JewelThemeConfig = JewelThemeConfig.DEFAULT,
    modifier: Modifier = Modifier
) {
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 标题栏
        ChatHeader(
            onClearChat = { messages = emptyList() },
            themeConfig = themeConfig
        )
        
        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.height(1.dp)
        )
        
        // 消息列表 - 恢复 LazyColumn 以支持自动滚动
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(themeConfig.messageSpacing.dp)
        ) {
            items(messages) { message ->
                MessageItem(message, themeConfig)
            }
            
            if (isLoading) {
                item {
                    LoadingIndicator()
                }
            }
        }
        
        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.height(1.dp)
        )
        
        // 输入区域
        ChatInputArea(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank() && !isLoading) {
                    val userMessage = Message(
                        role = MessageRole.USER.name,
                        content = inputText,
                        timestamp = System.currentTimeMillis()
                    )
                    messages = messages + userMessage
                    
                    scope.launch {
                        isLoading = true
                        inputText = ""
                        
                        // 滚动到底部
                        listState.animateScrollToItem(messages.size - 1)
                        
                        try {
                            // 使用 ClaudeCliWrapper 发送消息
                            val options = ClaudeCliWrapper.QueryOptions(
                                model = "claude-opus-4-20250514",
                                cwd = workingDirectory,
                                resume = currentSessionId
                            )
                            
                            val responseBuilder = StringBuilder()
                            val stream = cliWrapper.query(userMessage.content, options)
                            
                            stream.collect { sdkMessage ->
                                when (sdkMessage.type) {
                                    MessageType.TEXT -> {
                                        sdkMessage.data.text?.let { 
                                            responseBuilder.append(it) 
                                        }
                                    }
                                    MessageType.ERROR -> {
                                        val errorMsg = sdkMessage.data.error ?: "Unknown error"
                                        throw Exception(errorMsg)
                                    }
                                    MessageType.START -> {
                                        sdkMessage.data.sessionId?.let { 
                                            currentSessionId = it 
                                        }
                                    }
                                    MessageType.TOOL_USE -> {
                                        responseBuilder.append("\n\n🔧 ${sdkMessage.data.toolName}: ")
                                        responseBuilder.append(sdkMessage.data.toolInput?.toString() ?: "")
                                        responseBuilder.append("\n\n")
                                    }
                                    else -> {
                                        // 忽略其他消息类型
                                    }
                                }
                            }
                            
                            val assistantMessage = Message(
                                role = MessageRole.ASSISTANT.name,
                                content = responseBuilder.toString(),
                                timestamp = System.currentTimeMillis()
                            )
                            messages = messages + assistantMessage
                            
                            // 滚动到最新消息
                            listState.animateScrollToItem(messages.size - 1)
                            
                        } catch (e: Exception) {
                            val errorMessage = Message(
                                role = MessageRole.ASSISTANT.name,
                                content = "❌ 错误: ${e.message}",
                                timestamp = System.currentTimeMillis()
                            )
                            messages = messages + errorMessage
                        }
                        
                        isLoading = false
                    }
                }
            },
            isEnabled = !isLoading,
            themeConfig = themeConfig,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 聊天标题栏
 */
@Composable
private fun ChatHeader(
    onClearChat: () -> Unit,
    themeConfig: JewelThemeConfig = JewelThemeConfig.DEFAULT,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Claude Assistant",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = JewelTheme.defaultTextStyle.fontSize * 1.2f * themeConfig.fontScale
            )
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DefaultButton(
                onClick = onClearChat
            ) {
                Text("清空对话")
            }
        }
    }
}

/**
 * 消息项
 */
@Composable
private fun MessageItem(
    message: Message,
    themeConfig: JewelThemeConfig = JewelThemeConfig.DEFAULT,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER.name
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // 角色标签
        Text(
            text = if (isUser) "You" else "Claude",
            style = JewelTheme.defaultTextStyle.copy(
                color = if (isUser) Color(0xFF3574F0) else Color(0xFF5FB865),
                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f * themeConfig.fontScale
            ),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        // 消息内容
        Box(
            modifier = Modifier
                .background(
                    color = if (isUser) 
                        Color(0xFF1E3A5F) 
                    else 
                        Color(0xFF2B2B2B),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
                .widthIn(max = 600.dp)
        ) {
            Text(
                text = message.content,
                style = JewelTheme.defaultTextStyle.copy(
                    fontFamily = if (themeConfig.useMonospaceForCode && message.content.contains("```")) FontFamily.Monospace else FontFamily.Default,
                    fontSize = JewelTheme.defaultTextStyle.fontSize * themeConfig.fontScale
                )
            )
        }
        
        // 时间戳
        if (themeConfig.showTimestamp) {
            Text(
                text = formatTimestamp(message.timestamp),
                style = JewelTheme.defaultTextStyle.copy(
                    color = Color(0xFF7F7F7F),
                    fontSize = JewelTheme.defaultTextStyle.fontSize * 0.8f * themeConfig.fontScale
                ),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * 输入区域
 */
@Composable
private fun ChatInputArea(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean,
    themeConfig: JewelThemeConfig = JewelThemeConfig.DEFAULT,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(JewelTheme.globalColors.panelBackground)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        TextArea(
            value = text,
            onValueChange = onTextChange,
            enabled = isEnabled,
            placeholder = "输入消息... (Shift+Enter 换行，Enter 发送)",
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 60.dp, max = 150.dp)
                .onPreviewKeyEvent { event ->
                    when {
                        // Enter 发送，Shift+Enter 换行
                        event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                            if (!event.isShiftPressed && text.isNotBlank()) {
                                onSend()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
        )
        
        DefaultButton(
            onClick = onSend,
            enabled = isEnabled && text.isNotBlank()
        ) {
            Text("发送")
        }
    }
}

/**
 * 加载指示器
 */
@Composable
private fun LoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    Color(0xFF2B2B2B),
                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                "Claude 正在思考...",
                style = JewelTheme.defaultTextStyle.copy(
                    color = Color(0xFF7F7F7F)
                )
            )
        }
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("HH:mm:ss")
    return format.format(date)
}