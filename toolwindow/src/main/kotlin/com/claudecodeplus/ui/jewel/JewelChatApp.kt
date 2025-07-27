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
import com.claudecodeplus.ui.services.SessionHistoryService
import com.claudecodeplus.ui.services.MessageProcessor
import com.claudecodeplus.ui.services.SessionLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.Orientation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Jewel 聊天应用主组件
 * 包含完整的业务逻辑，用于与 Claude API 交互
 * 测试应用只需要简单地使用这个组件即可
 */
@Composable
fun JewelChatApp(
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String,
    fileIndexService: com.claudecodeplus.ui.services.FileIndexService? = null,
    projectService: com.claudecodeplus.ui.services.ProjectService? = null,
    themeProvider: JewelThemeProvider = DefaultJewelThemeProvider(),
    modifier: Modifier = Modifier,
    showToolbar: Boolean = true,
    onThemeChange: ((JewelThemeStyle) -> Unit)? = null,
    onCompactCompleted: (() -> Unit)? = null  // 压缩完成回调
) {
    // 应用状态
    var messages by remember { mutableStateOf(listOf<EnhancedMessage>()) }
    var contexts by remember { mutableStateOf(listOf<ContextReference>()) }
    var isGenerating by remember { mutableStateOf(false) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var messageJob by remember { mutableStateOf<Job?>(null) }
    var selectedModel by remember { mutableStateOf(AiModel.OPUS) }
    var selectedPermissionMode by remember { mutableStateOf(PermissionMode.BYPASS_PERMISSIONS) }
    // skipPermissions 默认为 true，不再可修改
    val skipPermissions = true
    
    val scope = rememberCoroutineScope()
    val sessionHistoryService = remember { SessionHistoryService() }
    val messageProcessor = remember { MessageProcessor() }
    val sessionLoader = remember { SessionLoader(sessionHistoryService, messageProcessor) }
    
    // 启动时加载历史会话（使用流式加载）
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                // 获取最近的会话文件
                val sessionFile = sessionHistoryService.getLatestSessionFile(workingDirectory)
                if (sessionFile != null) {
                    println("找到历史会话文件: ${sessionFile.name}")
                    
                    // 使用流式加载，每条消息都经过与实时消息相同的处理流程
                    sessionLoader.loadSessionAsMessageFlow(sessionFile, maxMessages = 50)
                        .collect { result ->
                            when (result) {
                                is SessionLoader.LoadResult.MessageCompleted -> {
                                    // 每完成一条消息就更新UI
                                    withContext(Dispatchers.Main) {
                                        messages = messages + result.message
                                    }
                                }
                                is SessionLoader.LoadResult.MessageUpdated -> {
                                    // 消息更新（用于流式内容）
                                    withContext(Dispatchers.Main) {
                                        val index = messages.indexOfFirst { it.id == result.message.id }
                                        if (index != -1) {
                                            val updatedMessages = messages.toMutableList()
                                            updatedMessages[index] = result.message
                                            messages = updatedMessages
                                        }
                                    }
                                }
                                is SessionLoader.LoadResult.LoadComplete -> {
                                    // 加载完成
                                    println("历史会话加载完成，共 ${result.messages.size} 条消息")
                                }
                                is SessionLoader.LoadResult.Error -> {
                                    println("加载历史会话出错: ${result.error}")
                                }
                            }
                        }
                } else {
                    println("未找到历史会话文件")
                }
            } catch (e: Exception) {
                println("加载历史会话失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    // 添加调试输出
    // JewelChatApp: selectedModel = ${selectedModel.displayName}
    
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
            onSend = { textWithMarkdown ->
                if (textWithMarkdown.isNotBlank() && !isGenerating) {
                    messageJob?.cancel()
                    messageJob = sendMessage(
                        scope = scope,
                        inputText = textWithMarkdown,
                        contexts = contexts,
                        selectedModel = selectedModel,
                        selectedPermissionMode = selectedPermissionMode,
                        skipPermissions = skipPermissions,
                        cliWrapper = cliWrapper,
                        workingDirectory = workingDirectory,
                        currentSessionId = currentSessionId,
                        currentMessages = messages,
                        onMessageUpdate = { messages = it },
                        onContextsClear = { contexts = emptyList() },
                        onGeneratingChange = { isGenerating = it },
                        onSessionIdUpdate = { currentSessionId = it },
                        onCompactCompleted = onCompactCompleted
                    )
                }
            },
            onStop = {
                // 立即终止 CLI wrapper 进程
                val terminated = cliWrapper.terminate()
                // DEBUG: CLI wrapper terminated: $terminated
                
                // 取消协程任务
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
            selectedModel = selectedModel,
            onModelChange = { model ->
                // === JewelChatApp.onModelChange CALLED ===
                // DEBUG: Current selectedModel = ${selectedModel.displayName}
                // DEBUG: New model parameter = ${model.displayName}
                // DEBUG: About to update selectedModel
                selectedModel = model
                // DEBUG: After update selectedModel = ${selectedModel.displayName}
                // === JewelChatApp.onModelChange FINISHED ===
            },
            selectedPermissionMode = selectedPermissionMode,
            onPermissionModeChange = { mode ->
                selectedPermissionMode = mode
            },
            // skipPermissions 默认为 true，不再传递
            fileIndexService = fileIndexService,
            projectService = projectService,
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
    selectedModel: AiModel,
    selectedPermissionMode: PermissionMode,
    skipPermissions: Boolean,
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String,
    currentSessionId: String?,
    currentMessages: List<EnhancedMessage>,
    onMessageUpdate: (List<EnhancedMessage>) -> Unit,
    onContextsClear: () -> Unit,
    onGeneratingChange: (Boolean) -> Unit,
    onSessionIdUpdate: (String?) -> Unit,
    onCompactCompleted: (() -> Unit)? = null
): Job {
    return scope.launch(Dispatchers.IO) {
        try {
            onGeneratingChange(true)
            
            // 构建包含上下文的消息 - 使用新的Markdown格式
            val messageWithContext = buildFinalMessage(contexts, inputText)
            
            // 创建用户消息
            val userMessage = EnhancedMessage(
                id = generateMessageId(),
                role = MessageRole.USER,
                content = inputText,  // 使用原始输入文本，不包含上下文标记
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.COMPLETE,
                isError = false,
                model = selectedModel,
                contexts = contexts  // 上下文单独保存
            )
            
            val currentMessagesMutable = currentMessages.toMutableList()
            currentMessagesMutable.add(userMessage)
            onMessageUpdate(currentMessagesMutable.toList())
            
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
            
            val messagesWithAssistant = currentMessagesMutable + assistantMessage
            onMessageUpdate(messagesWithAssistant)
            
            // DEBUG: Sending message to Claude CLI: $messageWithContext
            // DEBUG: Working directory: $workingDirectory
            // DEBUG: Selected model: ${selectedModel.displayName} (CLI: ${selectedModel.cliName})
            
            // 启动消息流
            val messageFlow = cliWrapper.query(
                prompt = messageWithContext,  // 使用包含上下文的完整消息
                options = ClaudeCliWrapper.QueryOptions(
                    model = selectedModel.cliName,
                    resume = currentSessionId,  // 新建会话时为null是正常的
                    cwd = workingDirectory,
                    permissionMode = selectedPermissionMode.cliName,
                    skipPermissions = skipPermissions
                )
            )
            
            // DEBUG: Starting to collect messages from Claude CLI...
            
            val responseBuilder = StringBuilder()
            val toolCalls = mutableListOf<ToolCall>()
            val orderedElements = mutableListOf<MessageTimelineItem>()
            
            messageFlow.collect { sdkMessage ->
                when (sdkMessage.type) {
                    com.claudecodeplus.sdk.MessageType.TEXT -> {
                        // 流式内容更新
                        sdkMessage.data.text?.let { text ->
                            responseBuilder.append(text)
                            
                            // 如果已有内容元素，更新最后一个；否则添加新的
                            val lastElement = orderedElements.lastOrNull()
                            if (lastElement is MessageTimelineItem.ContentItem) {
                                // 更新最后一个内容元素
                                orderedElements[orderedElements.lastIndex] = lastElement.copy(
                                    content = responseBuilder.toString()
                                )
                            } else {
                                // 添加新的内容元素
                                orderedElements.add(
                                    MessageTimelineItem.ContentItem(
                                        content = responseBuilder.toString(),
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                            
                            // 检查是否包含压缩完成标记
                            if (text.contains("<local-command-stdout>Compacted. ctrl+r to see full summary</local-command-stdout>")) {
                                // 压缩完成，触发会话刷新
                                withContext(Dispatchers.Main) {
                                    // 延迟一下让用户看到完成消息
                                    delay(2000)
                                    
                                    // 调用压缩完成回调
                                    onCompactCompleted?.invoke()
                                }
                            }
                            
                            // 更新消息
                            val updatedMessage = assistantMessage.copy(
                                content = responseBuilder.toString(),
                                toolCalls = toolCalls.toList(),
                                orderedElements = orderedElements.toList()
                            )
                            val mutableMessages = messagesWithAssistant.toMutableList()
                            mutableMessages[mutableMessages.lastIndex] = updatedMessage
                            onMessageUpdate(mutableMessages.toList())
                        }
                    }
                    
                    com.claudecodeplus.sdk.MessageType.TOOL_USE -> {
                        // 工具调用开始
                        // DEBUG: Tool use detected - ${sdkMessage.data.toolName}
                        val toolCall = ToolCall(
                            name = sdkMessage.data.toolName ?: "unknown",
                            displayName = sdkMessage.data.toolName ?: "unknown",
                            parameters = sdkMessage.data.toolInput as? Map<String, Any> ?: emptyMap(),
                            status = ToolCallStatus.RUNNING
                        )
                        toolCalls.add(toolCall)
                        // DEBUG: Added tool call, total: ${toolCalls.size}
                        
                        // 添加工具调用元素到有序列表
                        orderedElements.add(
                            MessageTimelineItem.ToolCallItem(
                                toolCall = toolCall,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        
                        // 更新消息显示工具调用
                        val updatedMessage = assistantMessage.copy(
                            content = responseBuilder.toString(),
                            toolCalls = toolCalls.toList(),
                            orderedElements = orderedElements.toList()
                        )
                        val mutableMessages = messagesWithAssistant.toMutableList()
                        mutableMessages[mutableMessages.lastIndex] = updatedMessage
                        onMessageUpdate(mutableMessages.toList())
                    }
                    
                    com.claudecodeplus.sdk.MessageType.TOOL_RESULT -> {
                        // 工具调用结果
                        // DEBUG: Tool result received
                        val lastToolCall = toolCalls.lastOrNull()
                        if (lastToolCall != null) {
                            // DEBUG: Updating tool call result
                            val updatedToolCall = lastToolCall.copy(
                                status = if (sdkMessage.data.error != null) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
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
                            
                            // 更新有序列表中对应的工具调用元素
                            for (i in orderedElements.indices.reversed()) {
                                val element = orderedElements[i]
                                if (element is MessageTimelineItem.ToolCallItem && 
                                    element.toolCall.id == lastToolCall.id) {
                                    orderedElements[i] = element.copy(toolCall = updatedToolCall)
                                    break
                                }
                            }
                            
                            val updatedMessage = assistantMessage.copy(
                                content = responseBuilder.toString(),
                                toolCalls = toolCalls.toList(),
                                orderedElements = orderedElements.toList()
                            )
                            val mutableMessages = messagesWithAssistant.toMutableList()
                            mutableMessages[mutableMessages.lastIndex] = updatedMessage
                            onMessageUpdate(mutableMessages.toList())
                        } else {
                            // DEBUG: No tool call found to update
                        }
                    }
                    
                    com.claudecodeplus.sdk.MessageType.START -> {
                        // 会话开始，获取会话ID
                        sdkMessage.data.sessionId?.let { sessionId ->
                            onSessionIdUpdate(sessionId)
                        }
                    }
                    
                    com.claudecodeplus.sdk.MessageType.ERROR -> {
                        // 错误处理
                        val errorMessage = assistantMessage.copy(
                            content = "❌ 错误: ${sdkMessage.data.error ?: "Unknown error"}",
                            status = MessageStatus.FAILED,
                            isError = true,
                            isStreaming = false
                        )
                        val mutableMessages = messagesWithAssistant.toMutableList()
                        mutableMessages[mutableMessages.lastIndex] = errorMessage
                        onMessageUpdate(mutableMessages.toList())
                    }
                    
                    com.claudecodeplus.sdk.MessageType.END -> {
                        // 完成流式传输
                        val finalMessage = assistantMessage.copy(
                            content = responseBuilder.toString(),
                            status = MessageStatus.COMPLETE,
                            isStreaming = false,
                            toolCalls = toolCalls.toList(),
                            orderedElements = orderedElements.toList()
                        )
                        val mutableMessages = messagesWithAssistant.toMutableList()
                        mutableMessages[mutableMessages.lastIndex] = finalMessage
                        onMessageUpdate(mutableMessages.toList())
                    }
                }
            }
        } catch (e: Exception) {
            // ERROR: ${e.message}
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
 * 构建包含上下文的完整消息 - 只处理TAG类型上下文（Add Context按钮添加的）
 */
private fun buildFinalMessage(contexts: List<ContextReference>, userMessage: String): String {
    // 所有的上下文都是TAG类型（Add Context按钮添加的）
    // @符号添加的上下文不会进入contexts列表，直接在userMessage中
    
    if (contexts.isEmpty()) {
        return userMessage
    }
    
    val contextSection = buildString {
        appendLine("> **上下文资料**")
        appendLine("> ")
        
        contexts.forEach { context ->
            val contextLine = when (context) {
                is ContextReference.FileReference -> {
                    "> - 📄 `${context.path}`"
                }
                is ContextReference.WebReference -> {
                    val title = context.title?.let { " ($it)" } ?: ""
                    "> - 🌐 ${context.url}$title"
                }
                is ContextReference.FolderReference -> {
                    "> - 📁 `${context.path}` (${context.fileCount}个文件)"
                }
                is ContextReference.SymbolReference -> {
                    "> - 🔗 `${context.name}` (${context.type}) - ${context.file}:${context.line}"
                }
                is ContextReference.TerminalReference -> {
                    val errorFlag = if (context.isError) " ⚠️" else ""
                    "> - 💻 终端输出 (${context.lines}行)$errorFlag"
                }
                is ContextReference.ProblemsReference -> {
                    val severityText = context.severity?.let { " [$it]" } ?: ""
                    "> - ⚠️ 问题报告 (${context.problems.size}个)$severityText"
                }
                is ContextReference.GitReference -> {
                    "> - 🔀 Git ${context.type}"
                }
                is ContextReference.ImageReference -> {
                    "> - 🖼 `${context.filename}` (${context.size / 1024}KB)"
                }
                is ContextReference.SelectionReference -> {
                    "> - ✏️ 当前选择内容"
                }
                is ContextReference.WorkspaceReference -> {
                    "> - 🏠 当前工作区"
                }
            }
            appendLine(contextLine)
        }
        
        appendLine()
    }
    
    return contextSection + userMessage
}

/**
 * 构建包含上下文的消息 - 保留旧版本作为向后兼容
 */
@Deprecated("Use buildFinalMessage instead", ReplaceWith("buildFinalMessage(contexts, message)"))
private fun buildMessageWithContext(
    message: String,
    contexts: List<ContextReference>
): String {
    return buildFinalMessage(contexts, message)
}

/**
 * 生成消息ID
 */
private fun generateMessageId(): String {
    return "msg_${System.currentTimeMillis()}_${(0..999).random()}"
} 