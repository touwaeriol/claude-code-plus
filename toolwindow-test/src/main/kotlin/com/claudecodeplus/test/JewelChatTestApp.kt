package com.claudecodeplus.test

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 完整功能的 Jewel 聊天测试应用
 * 恢复原始 toolwindow 的所有功能，包括多行输入、消息历史、上下文管理等
 */
@Preview
@Composable
fun JewelChatTestApp() {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var isGenerating by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf("Claude 3.5 Sonnet") }
    var contexts by remember { mutableStateOf(listOf<ContextItem>()) }
    
    val focusRequester = remember { FocusRequester() }

    // 初始化欢迎消息（类似原始 toolwindow）
    LaunchedEffect(Unit) {
        messages = listOf(
            ChatMessage(
                id = generateId(),
                role = MessageRole.ASSISTANT,
                content = "你好！我是Claude，很高兴为您提供代码和技术方面的帮助。您可以询问任何关于编程、代码审查、调试或技术问题。",
                timestamp = System.currentTimeMillis(),
                isError = false,
                toolCalls = emptyList()
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 顶部工具栏（类似原始 ChatHeader）
        ChatHeader(
            selectedModel = selectedModel,
            onModelChange = { selectedModel = it },
            onClearChat = { 
                messages = listOf(
                    ChatMessage(
                        id = generateId(),
                        role = MessageRole.ASSISTANT,
                        content = "聊天记录已清空。有什么可以帮助您的吗？",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        )
        
        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.height(1.dp)
        )
        
        // 上下文显示区域（恢复原始功能）
        if (contexts.isNotEmpty()) {
            ContextArea(
                contexts = contexts,
                onRemoveContext = { context ->
                    contexts = contexts - context
                }
            )
            
            Divider(
                orientation = Orientation.Horizontal,
                modifier = Modifier.height(1.dp)
            )
        }
        
        // 消息列表（使用 VerticallyScrollableContainer，类似原始代码）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            VerticallyScrollableContainer(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    messages.forEach { message ->
                        MessageBubble(message = message)
                    }
                    
                    if (isGenerating) {
                        LoadingIndicator()
                    }
                }
            }
        }
        
        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.height(1.dp)
        )
        
        // 输入区域（恢复多行输入功能）
        ChatInputArea(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                if (inputText.text.isNotBlank() && !isGenerating) {
                    val userMessage = ChatMessage(
                        id = generateId(),
                        role = MessageRole.USER,
                        content = inputText.text,
                        timestamp = System.currentTimeMillis(),
                        contexts = contexts.toList()
                    )
                    
                    messages = messages + userMessage
                    val currentInput = inputText.text
                    inputText = TextFieldValue("")
                    contexts = emptyList()
                    isGenerating = true
                    
                    // 模拟AI回复（类似原始的流式处理）
                    GlobalScope.launch {
                        delay(1000 + (Math.random() * 2000).toLong()) // 1-3秒随机延迟
                        
                        val responses = listOf(
                            "这是一个很好的问题！让我来详细解答：\n\n1. 首先，我们需要考虑...\n2. 其次，这个方法的优势是...\n3. 最后，建议您采用以下最佳实践...",
                            "根据您的问题，我建议采用以下方案：\n\n```kotlin\nfun example() {\n    println(\"Hello, World!\")\n}\n```\n\n这种方法的好处是...",
                            "您提到的这个问题确实很常见。在实际开发中，我们通常会这样处理：\n\n• 首先检查输入参数\n• 然后验证业务逻辑\n• 最后返回处理结果\n\n希望这个回答对您有帮助！",
                            "让我为您分析一下这个问题的几个关键点：\n\n**技术方案：**\n- 方案A：简单直接，适合小型项目\n- 方案B：功能完善，适合大型系统\n\n**推荐：**\n根据您的使用场景，我建议选择方案B。"
                        )
                        
                        val randomResponse = responses.random()
                        val aiMessage = ChatMessage(
                            id = generateId(),
                            role = MessageRole.ASSISTANT,
                            content = "针对您的问题「$currentInput」，我的回答是：\n\n$randomResponse",
                            timestamp = System.currentTimeMillis(),
                            toolCalls = if (Math.random() > 0.7) {
                                listOf(
                                    ToolCall(
                                        id = "tool_${generateId()}",
                                        name = "code_search",
                                        input = mapOf("query" to currentInput),
                                        output = "找到了相关的代码示例和文档"
                                    )
                                )
                            } else emptyList()
                        )
                        
                        messages = messages + aiMessage
                        isGenerating = false
                    }
                }
            },
            onAddContext = { context ->
                contexts = contexts + context
            },
            isEnabled = !isGenerating,
            focusRequester = focusRequester,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 聊天标题栏（类似原始的 ChatHeader）
 */
@Composable
private fun ChatHeader(
    selectedModel: String,
    onModelChange: (String) -> Unit,
    onClearChat: () -> Unit,
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
            "Claude Assistant (Jewel Test)",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 模型选择器
            ModelSelector(
                selectedModel = selectedModel,
                onModelChange = onModelChange
            )
            
            // 清空聊天按钮
            DefaultButton(
                onClick = onClearChat
            ) {
                Text("清空对话")
            }
        }
    }
}

/**
 * 简化的模型选择器（移除 Dropdown，使用按钮切换）
 */
@Composable
private fun ModelSelector(
    selectedModel: String,
    onModelChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val models = listOf(
        "Claude 3.5 Sonnet",
        "Claude 3 Opus", 
        "Claude 3 Haiku",
        "GPT-4",
        "GPT-3.5 Turbo"
    )
    
    // 简化版本：点击切换到下一个模型
    DefaultButton(
        onClick = { 
            val currentIndex = models.indexOf(selectedModel)
            val nextIndex = (currentIndex + 1) % models.size
            onModelChange(models[nextIndex])
        },
        modifier = modifier
    ) {
        Text("$selectedModel ▼")
    }
}

/**
 * 上下文显示区域（恢复原始功能）
 */
@Composable
private fun ContextArea(
    contexts: List<ContextItem>,
    onRemoveContext: (ContextItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.borders.focused.copy(alpha = 0.1f))
            .padding(8.dp)
    ) {
        Text(
            "上下文 (${contexts.size})",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.info
            )
        )
        
        HorizontallyScrollableContainer(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                contexts.forEach { context ->
                    ContextChip(
                        context = context,
                        onRemove = { onRemoveContext(context) }
                    )
                }
            }
        }
    }
}

/**
 * 上下文标签（恢复原始功能）
 */
@Composable
private fun ContextChip(
    context: ContextItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                JewelTheme.globalColors.borders.normal,
                RoundedCornerShape(4.dp)
            )
            .padding(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                context.name,
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            
            DefaultButton(
                onClick = onRemove
            ) {
                Text("×", fontSize = 10.sp)
            }
        }
    }
}

/**
 * 输入区域（恢复多行输入功能，类似原始 ChatInputArea）
 */
@Composable
private fun ChatInputArea(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onAddContext: (ContextItem) -> Unit,
    isEnabled: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(JewelTheme.globalColors.panelBackground)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // 多行文本输入框（使用 TextArea，类似原始代码）
        TextArea(
            value = text,
            onValueChange = onTextChange,
            enabled = isEnabled,
            placeholder = { Text("输入消息... (Shift+Enter 换行，Enter 发送)") },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 60.dp, max = 150.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    when {
                        // Enter 发送，Shift+Enter 换行（类似原始功能）
                        event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                            if (!event.isShiftPressed && text.text.isNotBlank()) {
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
        
        // 操作按钮列
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 添加上下文按钮（恢复原始功能）
            DefaultButton(
                onClick = {
                    val contextTypes = listOf("文件", "选中代码", "当前项目", "错误日志")
                    val randomType = contextTypes.random()
                    val contextItem = ContextItem(
                        id = generateId(),
                        name = "$randomType-${generateId().substring(0, 4)}",
                        type = randomType,
                        content = "模拟的$randomType 内容"
                    )
                    onAddContext(contextItem)
                },
                enabled = isEnabled
            ) {
                Text("@")
            }
            
            // 发送按钮
            DefaultButton(
                onClick = onSend,
                enabled = isEnabled && text.text.isNotBlank()
            ) {
                Text("发送")
            }
        }
    }
}

/**
 * 消息气泡（恢复原始样式和功能）
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // 消息头部信息（角色和时间戳）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isUser && message.toolCalls.isNotEmpty()) {
                Text(
                    "🔧 ${message.toolCalls.size} 个工具调用",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = Color(0xFF59A869)
                    )
                )
            }
            
            Text(
                if (isUser) "You" else "Claude",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) Color(0xFF3574F0) else Color(0xFF59A869)
                )
            )
            
            Text(
                formatTimestamp(message.timestamp),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.disabled
                )
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 消息内容（类似原始 MessageItem）
        Box(
            modifier = Modifier
                .background(
                    if (isUser) Color(0xFF1E3A5F) else Color(0xFF2B2B2B),
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
                .widthIn(max = 600.dp)
        ) {
            Column {
                // 上下文信息
                if (isUser && message.contexts.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            "📎 包含上下文:",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 10.sp,
                                color = Color(0xFF888888)
                            )
                        )
                        message.contexts.forEach { context ->
                            Text(
                                "• ${context.name}",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 10.sp,
                                    color = Color(0xFF888888)
                                )
                            )
                        }
                    }
                }
                
                // 主要内容
                Text(
                    text = message.content,
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color.White,
                        fontFamily = if (message.content.contains("```")) FontFamily.Monospace else FontFamily.Default
                    )
                )
                
                // 工具调用信息（类似原始的 ToolCallDisplay）
                if (message.toolCalls.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        message.toolCalls.forEach { toolCall ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color(0xFF1A1A1A),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(8.dp)
                                    .fillMaxWidth()
                            ) {
                                Column {
                                    Text(
                                        "🛠️ ${toolCall.name}",
                                        style = JewelTheme.defaultTextStyle.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF59A869)
                                        )
                                    )
                                    if (toolCall.output.isNotBlank()) {
                                        Text(
                                            toolCall.output,
                                            style = JewelTheme.defaultTextStyle.copy(
                                                fontSize = 10.sp,
                                                color = Color(0xFFCCCCCC)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 错误状态指示
        if (message.isError) {
            Text(
                "⚠️ 消息发送失败",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = Color(0xFFFF6B6B)
                ),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * 加载指示器（类似原始 LoadingIndicator）
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
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "●●●",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFF59A869)
                    )
                )
                Text(
                    "Claude 正在思考...",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFFCCCCCC)
                    )
                )
            }
        }
    }
}

// 数据类定义（类似原始 models）
data class ChatMessage(
    val id: String = generateId(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val contexts: List<ContextItem> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val isError: Boolean = false,
    val isStreaming: Boolean = false
)

data class ContextItem(
    val id: String,
    val name: String,
    val type: String,
    val content: String
)

data class ToolCall(
    val id: String,
    val name: String,
    val input: Map<String, Any>,
    val output: String = ""
)

enum class MessageRole {
    USER, ASSISTANT, SYSTEM
}

// 工具函数
private fun generateId(): String = UUID.randomUUID().toString()

private fun formatTimestamp(timestamp: Long): String {
    val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return format.format(Date(timestamp))
}