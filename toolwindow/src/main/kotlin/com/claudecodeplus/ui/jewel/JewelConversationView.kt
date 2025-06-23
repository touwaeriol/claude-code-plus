package com.claudecodeplus.ui.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.ui.jewel.components.*
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.FileIndexService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import androidx.compose.material.LocalContentColor
import java.util.UUID

/**
 * 重新设计的对话视图
 * 集成了模型选择、Markdown 渲染、工具调用显示等功能
 */
@Composable
fun JewelConversationView(
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String = System.getProperty("user.dir"),
    fileIndexService: FileIndexService? = null,
    modifier: Modifier = Modifier
) {
    var messages by remember { mutableStateOf(listOf<EnhancedMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var contexts by remember { mutableStateOf(listOf<ContextReference>()) }
    var currentModel by remember { mutableStateOf(AiModel.OPUS) }
    var isGenerating by remember { mutableStateOf(false) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        // 顶部工具栏
        ConversationHeader(
            currentModel = currentModel,
            onModelChange = { currentModel = it },
            onClearChat = {
                messages = emptyList()
                currentSessionId = null
            },
            onShowHistory = { /* TODO: 实现历史记录 */ }
        )
        
        Divider(orientation = Orientation.Horizontal)
        
        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 欢迎消息
            if (messages.isEmpty()) {
                item {
                    WelcomeMessage()
                }
            }
            
            // 消息列表
            items(messages) { message ->
                MessageItem(
                    message = message,
                    onCodeAction = { code, language ->
                        // TODO: 实现代码插入到编辑器
                    }
                )
            }
            
            // 生成中的占位符
            if (isGenerating) {
                item {
                    GeneratingIndicator()
                }
            }
        }
        
        Divider(orientation = Orientation.Horizontal)
        
        // 输入区域
        SmartInputArea(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank() && !isGenerating) {
                    sendMessage(
                        scope = scope,
                        inputText = inputText,
                        contexts = contexts,
                        model = currentModel,
                        cliWrapper = cliWrapper,
                        workingDirectory = workingDirectory,
                        currentSessionId = currentSessionId,
                        onMessageUpdate = { messages = it },
                        onInputClear = { inputText = "" },
                        onContextsClear = { contexts = emptyList() },
                        onGeneratingChange = { isGenerating = it },
                        onSessionIdUpdate = { currentSessionId = it },
                        onJobUpdate = { currentJob = it }
                    )
                }
            },
            onStop = {
                currentJob?.cancel()
                isGenerating = false
            },
            contexts = contexts,
            onContextAdd = { contexts = contexts + it },
            onContextRemove = { contexts = contexts - it },
            isGenerating = isGenerating,
            enabled = true
        )
    }
    
    // 自动滚动到底部
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size + if (isGenerating) 1 else 0)
        }
    }
}

/**
 * 对话头部
 */
@Composable
private fun ConversationHeader(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    onClearChat: () -> Unit,
    onShowHistory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2B2B2B))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Claude Code Plus",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModelSelector(
                currentModel = currentModel,
                onModelChange = onModelChange
            )
            
            DefaultButton(onClick = onClearChat) {
                Text("清除对话")
            }
            
            DefaultButton(onClick = onShowHistory) {
                Text("历史记录")
            }
        }
    }
}

/**
 * 欢迎消息
 */
@Composable
private fun WelcomeMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "欢迎使用 Claude Code Plus！",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        )
        
        Text(
            "您可以：",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 14.sp,
                color = LocalContentColor.current.copy(alpha = 0.7f)
            )
        )
        
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("• 使用 @ 引用文件、符号或终端输出", fontSize = 14.sp)
            Text("• 选择不同的 AI 模型", fontSize = 14.sp)
            Text("• 直接运行命令并查看结果", fontSize = 14.sp)
            Text("• 使用 Shift+Enter 换行，Enter 发送", fontSize = 14.sp)
        }
    }
}

/**
 * 消息项
 */
@Composable
private fun MessageItem(
    message: EnhancedMessage,
    onCodeAction: (code: String, language: String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 角色和模型信息
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when (message.role) {
                    MessageRole.USER -> "👤 您"
                    MessageRole.ASSISTANT -> "🤖 Claude"
                    MessageRole.SYSTEM -> "⚙️ 系统"
                    MessageRole.ERROR -> "⚠️ 错误"
                },
                style = JewelTheme.defaultTextStyle.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            )
            
            message.model?.let { model ->
                Text(
                    "(${model.displayName})",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = LocalContentColor.current.copy(alpha = 0.6f)
                    )
                )
            }
            
            Spacer(Modifier.weight(1f))
            
            Text(
                formatTimestamp(message.timestamp),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = LocalContentColor.current.copy(alpha = 0.5f)
                )
            )
        }
        
        // 上下文引用
        if (message.contexts.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                message.contexts.forEach { context ->
                    ContextChip(context)
                }
            }
        }
        
        // 消息内容
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = when (message.role) {
                        MessageRole.USER -> Color(0xFF1E3A5F)
                        else -> Color(0xFF2B2B2B)
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            if (message.role == MessageRole.ASSISTANT) {
                MarkdownRenderer(
                    markdown = message.content,
                    onCodeAction = onCodeAction
                )
            } else {
                Text(message.content)
            }
        }
        
        // 工具调用
        if (message.toolCalls.isNotEmpty()) {
            ToolCallDisplay(
                toolCalls = message.toolCalls,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        // 错误状态
        if (message.isError) {
            Text(
                "❌ 发生错误",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = Color(0xFFFF6B6B)
                )
            )
        }
    }
}

/**
 * 生成中指示器
 */
@Composable
private fun GeneratingIndicator() {
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
 * 上下文标签（只读）
 */
@Composable
private fun ContextChip(context: ContextReference) {
    Box(
        modifier = Modifier
            .background(Color(0xFF3C3C3C), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = when (context) {
                is ContextReference.FileReference -> "📄 ${context.path.substringAfterLast('/')}"
                is ContextReference.FolderReference -> "📁 ${context.path}"
                is ContextReference.SymbolReference -> "🔤 ${context.name}"
                is ContextReference.TerminalReference -> "💻 终端"
                is ContextReference.ProblemsReference -> "⚠️ 问题"
                is ContextReference.GitReference -> "🔀 Git"
                ContextReference.SelectionReference -> "✂️ 选中内容"
                ContextReference.WorkspaceReference -> "🗂️ 工作空间"
            },
            fontSize = 11.sp
        )
    }
}

/**
 * 发送消息的逻辑
 */
private fun sendMessage(
    scope: kotlinx.coroutines.CoroutineScope,
    inputText: String,
    contexts: List<ContextReference>,
    model: AiModel,
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String,
    currentSessionId: String?,
    onMessageUpdate: (List<EnhancedMessage>) -> Unit,
    onInputClear: () -> Unit,
    onContextsClear: () -> Unit,
    onGeneratingChange: (Boolean) -> Unit,
    onSessionIdUpdate: (String?) -> Unit,
    onJobUpdate: (Job?) -> Unit
) {
    // 创建用户消息
    val userMessage = EnhancedMessage(
        role = MessageRole.USER,
        content = inputText,
        contexts = contexts,
        model = model
    )
    
    val currentMessages = onMessageUpdate.let { callback ->
        val messages = mutableListOf<EnhancedMessage>()
        callback(messages)
        messages
    }
    
    currentMessages.add(userMessage)
    onMessageUpdate(currentMessages.toList())
    
    onInputClear()
    onContextsClear()
    onGeneratingChange(true)
    
    // 创建 AI 响应消息
    val assistantMessage = EnhancedMessage(
        role = MessageRole.ASSISTANT,
        content = "",
        model = model,
        isStreaming = true
    )
    
    currentMessages.add(assistantMessage)
    onMessageUpdate(currentMessages.toList())
    
    // 启动协程处理 AI 响应
    val job = scope.launch {
        try {
            // 构建包含上下文的消息
            val messageWithContext = buildMessageWithContext(inputText, contexts)
            
            // 调用 CLI
            val options = ClaudeCliWrapper.QueryOptions(
                model = model.cliName,
                cwd = workingDirectory,
                resume = currentSessionId
            )
            
            val responseBuilder = StringBuilder()
            val toolCalls = mutableListOf<ToolCall>()
            
            cliWrapper.query(messageWithContext, options).collect { sdkMessage ->
                when (sdkMessage.type) {
                    MessageType.TEXT -> {
                        sdkMessage.data.text?.let { text ->
                            responseBuilder.append(text)
                            // 更新消息内容
                            val updatedMessage = assistantMessage.copy(
                                content = responseBuilder.toString(),
                                toolCalls = toolCalls.toList()
                            )
                            currentMessages[currentMessages.lastIndex] = updatedMessage
                            onMessageUpdate(currentMessages.toList())
                        }
                    }
                    
                    MessageType.TOOL_USE -> {
                        val toolCall = ToolCall(
                            name = sdkMessage.data.toolName ?: "unknown",
                            displayName = sdkMessage.data.toolName ?: "unknown",
                            parameters = sdkMessage.data.toolInput as? Map<String, Any> ?: emptyMap(),
                            status = ToolCallStatus.RUNNING
                        )
                        toolCalls.add(toolCall)
                        
                        // 更新消息显示工具调用
                        val updatedMessage = assistantMessage.copy(
                            content = responseBuilder.toString(),
                            toolCalls = toolCalls.toList()
                        )
                        currentMessages[currentMessages.lastIndex] = updatedMessage
                        onMessageUpdate(currentMessages.toList())
                    }
                    
                    MessageType.TOOL_RESULT -> {
                        // 更新工具调用结果
                        val lastToolCall = toolCalls.lastOrNull()
                        if (lastToolCall != null) {
                            val updatedToolCall = lastToolCall.copy(
                                status = ToolCallStatus.SUCCESS,
                                result = if (sdkMessage.data.error != null) {
                                    ToolResult.Failure(
                                        error = sdkMessage.data.error ?: "Unknown error"
                                    )
                                } else {
                                    ToolResult.Success(
                                        output = sdkMessage.data.toolResult?.toString() ?: ""
                                    )
                                },
                                endTime = System.currentTimeMillis()
                            )
                            toolCalls[toolCalls.lastIndex] = updatedToolCall
                            
                            val updatedMessage = assistantMessage.copy(
                                content = responseBuilder.toString(),
                                toolCalls = toolCalls.toList()
                            )
                            currentMessages[currentMessages.lastIndex] = updatedMessage
                            onMessageUpdate(currentMessages.toList())
                        }
                    }
                    
                    MessageType.START -> {
                        sdkMessage.data.sessionId?.let { id ->
                            onSessionIdUpdate(id)
                        }
                    }
                    
                    MessageType.ERROR -> {
                        val errorMsg = sdkMessage.data.error ?: "Unknown error"
                        val errorMessage = assistantMessage.copy(
                            content = "❌ 错误: $errorMsg",
                            status = MessageStatus.FAILED,
                            isError = true,
                            isStreaming = false
                        )
                        currentMessages[currentMessages.lastIndex] = errorMessage
                        onMessageUpdate(currentMessages.toList())
                    }
                    
                    MessageType.END -> {
                        // 完成流式传输
                        val finalMessage = assistantMessage.copy(
                            content = responseBuilder.toString(),
                            toolCalls = toolCalls.toList(),
                            status = MessageStatus.COMPLETE,
                            isStreaming = false
                        )
                        currentMessages[currentMessages.lastIndex] = finalMessage
                        onMessageUpdate(currentMessages.toList())
                    }
                    
                    else -> {
                        // 忽略其他消息类型
                    }
                }
            }
        } catch (e: Exception) {
            val errorMessage = assistantMessage.copy(
                content = "❌ 错误: ${e.message}",
                status = MessageStatus.FAILED,
                isError = true,
                isStreaming = false
            )
            currentMessages[currentMessages.lastIndex] = errorMessage
            onMessageUpdate(currentMessages.toList())
        } finally {
            onGeneratingChange(false)
        }
    }
    
    onJobUpdate(job)
}

/**
 * 构建包含上下文的消息
 */
private fun buildMessageWithContext(
    message: String,
    contexts: List<ContextReference>
): String {
    if (contexts.isEmpty()) {
        return message
    }
    
    val contextStrings = contexts.map { context ->
        when (context) {
            is ContextReference.FileReference -> {
                "文件: ${context.path}" + 
                    if (context.lines != null) " (行 ${context.lines})" else ""
            }
            is ContextReference.FolderReference -> "文件夹: ${context.path}"
            is ContextReference.SymbolReference -> "符号: ${context.name} (${context.type})"
            is ContextReference.TerminalReference -> "终端输出 (最近 ${context.lines} 行)"
            is ContextReference.ProblemsReference -> {
                val severity = context.severity?.name ?: "所有"
                "问题 ($severity)"
            }
            is ContextReference.GitReference -> "Git ${context.type.name}"
            ContextReference.SelectionReference -> "选中的代码"
            ContextReference.WorkspaceReference -> "整个工作空间"
        }
    }
    
    return buildString {
        appendLine("上下文引用:")
        contextStrings.forEach { appendLine("- $it") }
        appendLine()
        append(message)
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

