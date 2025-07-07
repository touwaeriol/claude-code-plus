package com.claudecodeplus.ui.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.session.models.*
import com.claudecodeplus.ui.jewel.components.*
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.ProjectService
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.util.UUID
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.rememberScrollState

/**
 * 聊天视图组件
 * 提供完整的聊天界面，支持会话管理、消息显示和输入
 */
@Composable
fun ChatView(
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String,
    fileIndexService: FileIndexService? = null,
    projectService: ProjectService? = null,
    sessionManager: ClaudeSessionManager = ClaudeSessionManager(),
    modifier: Modifier = Modifier
) {
    // 状态管理
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var currentSession by remember { mutableStateOf<SessionInfo?>(null) }
    var contexts by remember { mutableStateOf(listOf<ContextReference>()) }
    var isGenerating by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(AiModel.OPUS) }
    var messages by remember { mutableStateOf(listOf<EnhancedMessage>()) }
    var isLoadingSession by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // 初始化时加载最近会话或创建新会话
    LaunchedEffect(workingDirectory) {
        isLoadingSession = true
        try {
            // 尝试获取最近的会话
            val recentSession = sessionManager.getRecentSession(workingDirectory)
            if (recentSession != null) {
                // ChatView: Loading recent session ${recentSession.sessionId}
                currentSession = recentSession
                currentSessionId = recentSession.sessionId
                
                // 加载会话消息
                try {
                    val (sessionMessages, totalCount) = sessionManager.readSessionMessages(
                        sessionId = recentSession.sessionId,
                        projectPath = workingDirectory,
                        pageSize = 50 // 加载最近50条消息
                    )
                    
                    // 转换为 EnhancedMessage
                    val enhancedMessages = sessionMessages.mapNotNull { 
                        it.toEnhancedMessage() 
                    }
                    
                    messages = enhancedMessages
                    // ChatView: Loaded ${enhancedMessages.size} messages from recent session
                } catch (e: Exception) {
                    // ChatView: Error loading session messages: ${e.message}
                    // 如果加载失败，保持空消息列表
                }
            } else {
                // 没有历史会话，创建新会话但不使用 resume
                // ChatView: No recent session found, will create new session on first message
                currentSessionId = null
                currentSession = null
            }
        } catch (e: Exception) {
            // ChatView: Error during initialization: ${e.message}
            e.printStackTrace()
        } finally {
            isLoadingSession = false
        }
    }
    
    // 主聊天区域
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 工具栏
        ChatToolbar(
            currentSession = currentSession,
            onNewSession = {
                // 创建新会话
                currentSessionId = null
                currentSession = null
                contexts = emptyList()
                messages = emptyList()
                // ChatView: Starting new session
            }
        )
        
        Divider(orientation = org.jetbrains.jewel.ui.Orientation.Horizontal)
        
        // 聊天内容区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLoadingSession) {
                // 加载状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "正在加载会话...",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = JewelTheme.globalColors.text.disabled
                            )
                        )
                    }
                }
            } else {
                // 消息列表
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(JewelTheme.globalColors.panelBackground)
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
                            if (messages.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "发送消息开始对话",
                                        style = JewelTheme.defaultTextStyle.copy(
                                            color = JewelTheme.globalColors.text.disabled
                                        )
                                    )
                                }
                            } else {
                                messages.forEach { message ->
                                    when (message.role) {
                                        MessageRole.USER -> {
                                            // 用户消息使用 UnifiedInputArea 的 DISPLAY 模式
                                            UnifiedInputArea(
                                                mode = InputAreaMode.DISPLAY,
                                                message = message,
                                                onContextClick = { uri ->
                                                    // Context clicked: $uri
                                                    // 可以在这里处理文件点击等操作
                                                    if (uri.startsWith("file://") && projectService != null) {
                                                        val path = uri.removePrefix("file://")
                                                        projectService.openFile(path)
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        MessageRole.ASSISTANT, MessageRole.SYSTEM, MessageRole.ERROR -> {
                                            // AI、系统和错误消息使用专门的 AssistantMessageDisplay
                                            AssistantMessageDisplay(
                                                message = message,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Divider(orientation = org.jetbrains.jewel.ui.Orientation.Horizontal)
        
        // 输入区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            UnifiedInputArea(
                mode = InputAreaMode.INPUT,
                contexts = contexts,
                onContextAdd = { context ->
                    contexts = contexts + context
                },
                onContextRemove = { context ->
                    contexts = contexts - context
                },
                fileIndexService = fileIndexService,
                projectService = projectService,
                onSend = { markdownText ->
                    coroutineScope.launch {
                        isGenerating = true
                        try {
                            // 创建用户消息并添加到界面
                            val userMessage = EnhancedMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.USER,
                                content = markdownText,
                                timestamp = System.currentTimeMillis(),
                                model = selectedModel,
                                contexts = contexts
                            )
                            
                            // 添加到消息列表
                            messages = messages + userMessage
                            
                            // 记录当前会话ID（如果是新会话，会在响应中创建）
                            var sessionIdToUse = currentSessionId
                            
                            // 创建助手消息用于累积响应
                            var assistantMessageId = UUID.randomUUID().toString()
                            var assistantContent = ""
                            val toolCalls = mutableListOf<com.claudecodeplus.ui.models.ToolCall>()
                            val orderedElements = mutableListOf<MessageTimelineItem>()
                            var currentContentBuilder = StringBuilder()
                            
                            // 调用 Claude CLI
                            // 如果有 currentSessionId 且不为空，使用 resume；否则创建新会话
                            val useResume = currentSessionId != null && currentSessionId!!.isNotEmpty()
                            
                            // ChatView: Sending message, useResume=$useResume, sessionId=$currentSessionId
                            
                            cliWrapper.sendMessage(
                                message = markdownText,
                                sessionId = if (useResume) currentSessionId else null
                            ).collect { response ->
                                when (response) {
                                    is ClaudeCliWrapper.StreamResponse.SessionStart -> {
                                        // 新会话创建，保存会话ID
                                        if (currentSessionId == null) {
                                            currentSessionId = response.sessionId
                                            sessionIdToUse = response.sessionId
                                            // ChatView: New session created: $currentSessionId
                                        }
                                    }
                                    is ClaudeCliWrapper.StreamResponse.Content -> {
                                        // 累积内容
                                        assistantContent += response.content
                                        currentContentBuilder.append(response.content)
                                        
                                        // 更新或创建助手消息
                                        val assistantMessage = EnhancedMessage(
                                            id = assistantMessageId,
                                            role = MessageRole.ASSISTANT,
                                            content = assistantContent,
                                            timestamp = System.currentTimeMillis(),
                                            model = selectedModel,
                                            contexts = emptyList(),
                                            toolCalls = toolCalls.toList(),
                                            orderedElements = orderedElements.toList()
                                        )
                                        
                                        // 替换或添加消息
                                        messages = if (messages.any { it.id == assistantMessageId }) {
                                            messages.map { if (it.id == assistantMessageId) assistantMessage else it }
                                        } else {
                                            messages + assistantMessage
                                        }
                                    }
                                    is ClaudeCliWrapper.StreamResponse.Error -> {
                                        val errorMessage = EnhancedMessage(
                                            id = UUID.randomUUID().toString(),
                                            role = MessageRole.ASSISTANT,
                                            content = "错误: ${response.error}",
                                            timestamp = System.currentTimeMillis(),
                                            model = selectedModel,
                                            contexts = emptyList()
                                        )
                                        messages = messages + errorMessage
                                    }
                                    is ClaudeCliWrapper.StreamResponse.Complete -> {
                                        // 流结束
                                        // ChatView: Message complete
                                        
                                        // 如果还有未添加的内容，添加到时间线
                                        if (currentContentBuilder.isNotEmpty()) {
                                            orderedElements.add(
                                                MessageTimelineItem.ContentItem(
                                                    content = currentContentBuilder.toString(),
                                                    timestamp = System.currentTimeMillis()
                                                )
                                            )
                                            currentContentBuilder.clear()
                                        }
                                        
                                        // 最终更新消息
                                        val finalMessage = EnhancedMessage(
                                            id = assistantMessageId,
                                            role = MessageRole.ASSISTANT,
                                            content = assistantContent,
                                            timestamp = System.currentTimeMillis(),
                                            model = selectedModel,
                                            contexts = emptyList(),
                                            toolCalls = toolCalls.toList(),
                                            orderedElements = orderedElements.toList(),
                                            isStreaming = false
                                        )
                                        
                                        messages = messages.map { 
                                            if (it.id == assistantMessageId) finalMessage else it 
                                        }
                                    }
                                    is ClaudeCliWrapper.StreamResponse.ToolUse -> {
                                        // 工具调用
                                        // ChatView: Tool use - ${response.toolName}
                                        
                                        // 如果有累积的内容，先添加到时间线
                                        if (currentContentBuilder.isNotEmpty()) {
                                            orderedElements.add(
                                                MessageTimelineItem.ContentItem(
                                                    content = currentContentBuilder.toString(),
                                                    timestamp = System.currentTimeMillis()
                                                )
                                            )
                                            currentContentBuilder.clear()
                                        }
                                        
                                        val toolCall = com.claudecodeplus.ui.models.ToolCall(
                                            id = UUID.randomUUID().toString(),
                                            name = response.toolName,
                                            parameters = when (val input = response.toolInput) {
                                                is Map<*, *> -> {
                                                    @Suppress("UNCHECKED_CAST")
                                                    input as Map<String, Any>
                                                }
                                                else -> mapOf("input" to (input ?: ""))
                                            },
                                            status = com.claudecodeplus.ui.models.ToolCallStatus.RUNNING,
                                            startTime = System.currentTimeMillis()
                                        )
                                        toolCalls.add(toolCall)
                                        
                                        // 添加工具调用到时间线
                                        orderedElements.add(
                                            MessageTimelineItem.ToolCallItem(
                                                toolCall = toolCall,
                                                timestamp = toolCall.startTime
                                            )
                                        )
                                        
                                        // 更新助手消息，包含工具调用
                                        val assistantMessage = EnhancedMessage(
                                            id = assistantMessageId,
                                            role = MessageRole.ASSISTANT,
                                            content = assistantContent,
                                            timestamp = System.currentTimeMillis(),
                                            model = selectedModel,
                                            contexts = emptyList(),
                                            toolCalls = toolCalls.toList(),
                                            orderedElements = orderedElements.toList()
                                        )
                                        
                                        messages = if (messages.any { it.id == assistantMessageId }) {
                                            messages.map { if (it.id == assistantMessageId) assistantMessage else it }
                                        } else {
                                            messages + assistantMessage
                                        }
                                    }
                                    is ClaudeCliWrapper.StreamResponse.ToolResult -> {
                                        // 工具结果
                                        // ChatView: Tool result - ${response.toolName}
                                        // 更新对应的工具调用状态
                                        toolCalls.find { it.name == response.toolName }?.let { toolCall ->
                                            val index = toolCalls.indexOf(toolCall)
                                            val updatedToolCall = toolCall.copy(
                                                status = com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS,
                                                result = com.claudecodeplus.ui.models.ToolResult.Success(
                                                    output = response.result?.toString() ?: "No output"
                                                ),
                                                endTime = System.currentTimeMillis()
                                            )
                                            toolCalls[index] = updatedToolCall
                                            
                                            // 更新时间线中的工具调用
                                            val toolItemIndex = orderedElements.indexOfFirst { 
                                                it is MessageTimelineItem.ToolCallItem && it.toolCall.id == toolCall.id 
                                            }
                                            if (toolItemIndex != -1) {
                                                orderedElements[toolItemIndex] = MessageTimelineItem.ToolCallItem(
                                                    toolCall = updatedToolCall,
                                                    timestamp = updatedToolCall.startTime
                                                )
                                            }
                                            
                                            // 更新消息
                                            val assistantMessage = EnhancedMessage(
                                                id = assistantMessageId,
                                                role = MessageRole.ASSISTANT,
                                                content = assistantContent,
                                                timestamp = System.currentTimeMillis(),
                                                model = selectedModel,
                                                contexts = emptyList(),
                                                toolCalls = toolCalls.toList(),
                                                orderedElements = orderedElements.toList()
                                            )
                                            
                                            messages = messages.map { 
                                                if (it.id == assistantMessageId) assistantMessage else it 
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 清空上下文
                            contexts = emptyList()
                        } catch (e: Exception) {
                            // ChatView: Error sending message: ${e.message}
                            e.printStackTrace()
                            
                            val errorMessage = EnhancedMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.ASSISTANT,
                                content = "发送消息时出错: ${e.message}",
                                timestamp = System.currentTimeMillis(),
                                model = selectedModel,
                                contexts = emptyList()
                            )
                            messages = messages + errorMessage
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                selectedModel = selectedModel,
                onModelChange = { selectedModel = it },
                enabled = !isGenerating,
                isGenerating = isGenerating
            )
        }
    }
}

/**
 * 聊天工具栏
 */
@Composable
private fun ChatToolbar(
    currentSession: SessionInfo?,
    onNewSession: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(JewelTheme.globalColors.panelBackground)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 当前会话信息或标题
        Text(
            text = if (currentSession != null) {
                currentSession.firstMessage?.take(50) ?: "Claude AI Assistant"
            } else {
                "Claude AI Assistant"
            },
            style = JewelTheme.defaultTextStyle,
            modifier = Modifier.weight(1f)
        )
        
        // 新建会话按钮
        IconButton(onClick = onNewSession) {
            Icon(
                key = AllIconsKeys.General.Add,
                contentDescription = "新建会话",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}