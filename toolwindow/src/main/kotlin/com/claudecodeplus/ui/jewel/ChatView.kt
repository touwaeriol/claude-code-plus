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
import java.time.Instant
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
    initialMessages: List<EnhancedMessage>? = null,
    sessionId: String? = null,
    tabManager: com.claudecodeplus.ui.services.ChatTabManager? = null,
    currentTabId: String? = null,
    currentProject: com.claudecodeplus.ui.models.Project? = null,
    projectManager: com.claudecodeplus.ui.services.ProjectManager? = null,
    modifier: Modifier = Modifier
) {
    // 状态管理
    var currentSessionId by remember { mutableStateOf(sessionId) }
    var currentSession by remember { mutableStateOf<SessionInfo?>(null) }
    var contexts by remember { mutableStateOf(listOf<ContextReference>()) }
    var isGenerating by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(AiModel.OPUS) }
    var messages by remember { mutableStateOf(initialMessages ?: listOf<EnhancedMessage>()) }
    var isLoadingSession by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // 只在组件首次创建时设置初始值
    LaunchedEffect(Unit) {
        if (sessionId != null) {
            currentSessionId = sessionId
        }
        if (initialMessages != null) {
            messages = initialMessages
        }
    }
    
    // 监听 sessionId 变化，但不重置消息
    LaunchedEffect(sessionId) {
        if (sessionId != null && sessionId != currentSessionId) {
            currentSessionId = sessionId
        }
    }
    
    // 初始化时加载最近会话或创建新会话
    LaunchedEffect(workingDirectory) {
        // 如果明确传入了 initialMessages（即使是空列表），就不加载最近会话
        if (initialMessages != null) {
            return@LaunchedEffect
        }
        
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
                            
                            // 如果是占位会话的第一条消息，立即更新会话名称和标签标题
                            if ((messages.size == 1 || (messages.size == 2 && messages.firstOrNull()?.role == MessageRole.SYSTEM)) 
                                && currentSessionId != null 
                                && projectManager != null 
                                && currentProject != null) {
                                val sessionIdCopy = currentSessionId!!
                                coroutineScope.launch {
                                    projectManager.updateSessionName(
                                        sessionId = sessionIdCopy,
                                        projectId = currentProject.id,
                                        firstMessage = markdownText
                                    )
                                    
                                    // 同时更新标签标题
                                    if (tabManager != null && currentTabId != null) {
                                        tabManager.updateTabTitleFromFirstMessage(
                                            tabId = currentTabId,
                                            messageContent = markdownText,
                                            project = currentProject
                                        )
                                    }
                                }
                            }
                            
                            // 记录当前会话ID（如果是新会话，会在响应中创建）
                            var sessionIdToUse = currentSessionId
                            
                            // 检查是否是新项目的第一条消息（标签没有关联会话ID）
                            val isFirstMessageForNewProject = currentSessionId == null && messages.size == 1
                            
                            // 创建助手消息用于累积响应
                            var assistantMessageId = UUID.randomUUID().toString()
                            var assistantContent = ""
                            val toolCalls = mutableListOf<com.claudecodeplus.ui.models.ToolCall>()
                            val orderedElements = mutableListOf<MessageTimelineItem>()
                            var currentContentBuilder = StringBuilder()
                            
                            // 调用 Claude CLI
                            // 对于占位会话（第一条消息），不传递 sessionId，让 Claude CLI 创建新会话
                            val isPlaceholderSession = currentSessionId != null && messages.size == 1
                            val useResume = currentSessionId != null && currentSessionId!!.isNotEmpty() && !isPlaceholderSession
                            
                            // ChatView: Sending message, useResume=$useResume, sessionId=$currentSessionId, isPlaceholder=$isPlaceholderSession
                            
                            val options = ClaudeCliWrapper.QueryOptions(
                                resume = if (useResume) currentSessionId else null,
                                cwd = workingDirectory,
                                model = selectedModel?.cliName
                            )
                            
                            cliWrapper.query(markdownText, options).collect { message ->
                                when (message.type) {
                                    com.claudecodeplus.sdk.MessageType.START -> {
                                        // 新会话创建，保存会话ID
                                        if (currentSessionId == null || isPlaceholderSession) {
                                            val oldSessionId = currentSessionId
                                            
                                            // 如果是占位会话，更新会话ID而不是删除
                                            if (isPlaceholderSession && oldSessionId != null && projectManager != null && currentProject != null) {
                                                coroutineScope.launch {
                                                    // 更新占位会话的ID为真实的Claude会话ID
                                                    projectManager.updateSessionId(
                                                        oldSessionId = oldSessionId,
                                                        newSessionId = message.data.sessionId ?: "",
                                                        projectId = currentProject.id
                                                    )
                                                }
                                            }
                                            
                                            currentSessionId = message.data.sessionId
                                            sessionIdToUse = message.data.sessionId
                                            // ChatView: New session created: $currentSessionId
                                            
                                            // 如果是新项目的第一条消息，更新标签标题和关联会话ID
                                            if ((isFirstMessageForNewProject || isPlaceholderSession) && tabManager != null && currentTabId != null) {
                                                tabManager.updateTabTitleFromFirstMessage(
                                                    tabId = currentTabId,
                                                    messageContent = markdownText,
                                                    project = currentProject
                                                )
                                                
                                                // 更新标签的会话ID和消息列表
                                                tabManager.updateTab(currentTabId) { tab ->
                                                    tab.copy(
                                                        sessionId = message.data.sessionId ?: "",
                                                        messages = messages
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    com.claudecodeplus.sdk.MessageType.TEXT -> {
                                        // 累积内容
                                        val text = message.data.text ?: ""
                                        assistantContent += text
                                        currentContentBuilder.append(text)
                                        
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
                                    com.claudecodeplus.sdk.MessageType.ERROR -> {
                                        val errorMessage = EnhancedMessage(
                                            id = UUID.randomUUID().toString(),
                                            role = MessageRole.ASSISTANT,
                                            content = "错误: ${message.data.error ?: "未知错误"}",
                                            timestamp = System.currentTimeMillis(),
                                            model = selectedModel,
                                            contexts = emptyList()
                                        )
                                        messages = messages + errorMessage
                                    }
                                    com.claudecodeplus.sdk.MessageType.END -> {
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
                                        
                                        // 响应完成后，同步一次标签中的消息列表
                                        if (tabManager != null && currentTabId != null) {
                                            tabManager.updateTab(currentTabId) { tab ->
                                                tab.copy(
                                                    messages = messages
                                                )
                                            }
                                        }
                                    }
                                    com.claudecodeplus.sdk.MessageType.TOOL_USE -> {
                                        // 工具调用
                                        val toolName = message.data.toolName ?: "unknown"
                                        val toolCallId = message.data.toolCallId ?: UUID.randomUUID().toString()
                                        
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
                                            id = toolCallId,
                                            name = toolName,
                                            displayName = toolName,
                                            parameters = when (val input = message.data.toolInput) {
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
                                    com.claudecodeplus.sdk.MessageType.TOOL_RESULT -> {
                                        // 工具结果
                                        val toolCallId = message.data.toolCallId
                                        // 更新对应的工具调用状态
                                        toolCalls.find { it.id == toolCallId }?.let { toolCall ->
                                            val index = toolCalls.indexOf(toolCall)
                                            val updatedToolCall = toolCall.copy(
                                                status = com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS,
                                                result = com.claudecodeplus.ui.models.ToolResult.Success(
                                                    output = message.data.toolResult?.toString() ?: "No output"
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
                                    else -> {
                                        // 忽略其他消息类型
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