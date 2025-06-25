package com.claudecodeplus.ui.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.Orientation

/**
 * Jewel 聊天应用主组件
 * 包含完整的业务逻辑，用于与 Claude API 交互
 * 测试应用只需要简单地使用这个组件即可
 */
@Composable
fun JewelChatApp(
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String,
    themeProvider: JewelThemeProvider = DefaultJewelThemeProvider(),
    modifier: Modifier = Modifier,
    showToolbar: Boolean = true,
    onThemeChange: ((JewelThemeStyle) -> Unit)? = null
) {
    // 应用状态
    var messages by remember { mutableStateOf(listOf<EnhancedMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var contexts by remember { mutableStateOf(listOf<ContextReference>()) }
    var isGenerating by remember { mutableStateOf(false) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var messageJob by remember { mutableStateOf<Job?>(null) }
    
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 可选的工具栏
        if (showToolbar && onThemeChange != null) {
            TopToolbar(
                themeProvider = themeProvider,
                onThemeChange = onThemeChange
            )
            
            Divider(
                orientation = Orientation.Horizontal,
                modifier = Modifier.height(1.dp),
                color = JewelTheme.globalColors.borders.normal
            )
        }
        
        // 主要聊天界面
        JewelConversationView(
            messages = messages,
            inputText = inputText,
            onInputChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank() && !isGenerating) {
                    messageJob?.cancel()
                    messageJob = sendMessage(
                        scope = scope,
                        inputText = inputText,
                        contexts = contexts,
                        cliWrapper = cliWrapper,
                        workingDirectory = workingDirectory,
                        currentSessionId = currentSessionId,
                        currentMessages = messages,
                        onMessageUpdate = { messages = it },
                        onInputClear = { inputText = "" },
                        onContextsClear = { contexts = emptyList() },
                        onGeneratingChange = { isGenerating = it },
                        onSessionIdUpdate = { currentSessionId = it }
                    )
                }
            },
            onStop = {
                messageJob?.cancel()
                isGenerating = false
            },
            contexts = contexts,
            onContextAdd = { context ->
                contexts = contexts + context
            },
            onContextRemove = { context ->
                contexts = contexts - context
            },
            isGenerating = isGenerating,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 顶部工具栏组件
 */
@Composable
private fun TopToolbar(
    themeProvider: JewelThemeProvider,
    onThemeChange: (JewelThemeStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTheme = themeProvider.getCurrentThemeStyle()
    val actualTheme = JewelThemeStyle.getActualTheme(currentTheme, themeProvider.isSystemDark())
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：标题
        Text(
            "Claude Code Plus",
            style = JewelTheme.defaultTextStyle.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        )
        
        // 右侧：主题控制
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "主题:",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            
            // 主题切换按钮组
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 亮色主题按钮
                DefaultButton(
                    onClick = { onThemeChange(JewelThemeStyle.LIGHT) }
                ) {
                    Text(
                        "☀️ 亮色",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                    )
                }
                
                // 暗色主题按钮
                DefaultButton(
                    onClick = { onThemeChange(JewelThemeStyle.DARK) }
                ) {
                    Text(
                        "🌙 暗色",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                    )
                }
                
                // 跟随系统按钮
                DefaultButton(
                    onClick = { onThemeChange(JewelThemeStyle.SYSTEM) }
                ) {
                    Text(
                        "🔄 系统",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                    )
                }
            }
            
            // 当前主题指示器
            Text(
                "当前: ${getThemeDisplayName(actualTheme)}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                )
            )
        }
    }
}

/**
 * 获取主题显示名称
 */
private fun getThemeDisplayName(theme: JewelThemeStyle): String = when (theme) {
    JewelThemeStyle.LIGHT -> "亮色"
    JewelThemeStyle.DARK -> "暗色"
    JewelThemeStyle.SYSTEM -> "系统"
    JewelThemeStyle.HIGH_CONTRAST_LIGHT -> "高对比度亮色"
    JewelThemeStyle.HIGH_CONTRAST_DARK -> "高对比度暗色"
}

/**
 * 发送消息的业务逻辑
 * 这里包含了与 Claude CLI 的完整交互逻辑
 */
private fun sendMessage(
    scope: CoroutineScope,
    inputText: String,
    contexts: List<ContextReference>,
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String,
    currentSessionId: String?,
    currentMessages: List<EnhancedMessage>,
    onMessageUpdate: (List<EnhancedMessage>) -> Unit,
    onInputClear: () -> Unit,
    onContextsClear: () -> Unit,
    onGeneratingChange: (Boolean) -> Unit,
    onSessionIdUpdate: (String?) -> Unit
): Job {
    return scope.launch(Dispatchers.IO) {
        try {
            onGeneratingChange(true)
            
            // 构建包含上下文的消息
            val messageWithContext = buildMessageWithContext(inputText, contexts)
            
            // 添加用户消息
            val userMessage = EnhancedMessage(
                id = generateMessageId(),
                role = MessageRole.USER,
                content = messageWithContext,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.COMPLETE,
                isError = false
            )
            
            val updatedMessages = currentMessages + userMessage
            onMessageUpdate(updatedMessages)
            onInputClear()
            onContextsClear()
            
            // 创建空的助手消息
            val assistantMessage = EnhancedMessage(
                id = generateMessageId(),
                role = MessageRole.ASSISTANT,
                content = "",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.STREAMING,
                isStreaming = true,
                isError = false,
                toolCalls = emptyList()
            )
            
            val messagesWithAssistant = updatedMessages + assistantMessage
            onMessageUpdate(messagesWithAssistant)
            
            // 启动消息流
            val messageFlow = cliWrapper.sendMessage(
                message = inputText,
                sessionId = currentSessionId
            )
            
            val responseBuilder = StringBuilder()
            val toolCalls = mutableListOf<ToolCall>()
            
            messageFlow.collect { streamResponse ->
                when (streamResponse) {
                    is ClaudeCliWrapper.StreamResponse.Content -> {
                        // 流式内容更新
                        responseBuilder.append(streamResponse.content)
                        
                        // 更新消息内容
                        val updatedMessage = assistantMessage.copy(
                            content = responseBuilder.toString(),
                            toolCalls = toolCalls.toList()
                        )
                        val mutableMessages = messagesWithAssistant.toMutableList()
                        mutableMessages[mutableMessages.lastIndex] = updatedMessage
                        onMessageUpdate(mutableMessages.toList())
                    }
                    
                    is ClaudeCliWrapper.StreamResponse.Error -> {
                        // 错误处理
                        val errorMessage = assistantMessage.copy(
                            content = "❌ 错误: ${streamResponse.error}",
                            status = MessageStatus.FAILED,
                            isError = true,
                            isStreaming = false
                        )
                        val mutableMessages = messagesWithAssistant.toMutableList()
                        mutableMessages[mutableMessages.lastIndex] = errorMessage
                        onMessageUpdate(mutableMessages.toList())
                    }
                    
                    ClaudeCliWrapper.StreamResponse.Complete -> {
                        // 完成流式传输
                        val finalMessage = assistantMessage.copy(
                            content = responseBuilder.toString(),
                            status = MessageStatus.COMPLETE,
                            isStreaming = false,
                            toolCalls = toolCalls.toList()
                        )
                        val mutableMessages = messagesWithAssistant.toMutableList()
                        mutableMessages[mutableMessages.lastIndex] = finalMessage
                        onMessageUpdate(mutableMessages.toList())
                    }
                }
            }
        } catch (e: Exception) {
            println("ERROR: ${e.message}")
            e.printStackTrace()
            
            // 添加错误消息
            val errorMessage = EnhancedMessage(
                id = generateMessageId(),
                role = MessageRole.ASSISTANT,
                content = "❌ 发送消息时出错: ${e.message}",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.FAILED,
                isError = true
            )
            
            val errorMessages = currentMessages + errorMessage
            onMessageUpdate(errorMessages)
        } finally {
            onGeneratingChange(false)
        }
    }
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
 * 生成消息ID
 */
private fun generateMessageId(): String {
    return "msg_${System.currentTimeMillis()}_${(0..999).random()}"
} 