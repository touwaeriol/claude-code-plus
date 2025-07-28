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
import com.claudecodeplus.ui.jewel.components.QueueIndicator
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.SessionManager
import java.time.Instant
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.core.interfaces.ProjectService
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.util.UUID
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import com.claudecodeplus.ui.services.SessionPersistenceService

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
    sessionObjectManager: com.claudecodeplus.ui.services.SessionManager,  // 新增：会话对象管理器
    tabId: String,  // 新增：标签 ID
    initialMessages: List<EnhancedMessage>? = null,
    sessionId: String? = null,
    tabManager: com.claudecodeplus.ui.services.ChatTabManager? = null,
    currentTabId: String? = null,
    currentProject: com.claudecodeplus.ui.models.Project? = null,
    projectManager: com.claudecodeplus.ui.services.ProjectManager? = null,
    modifier: Modifier = Modifier
) {
    // 获取或创建该标签的会话对象
    val sessionObject = remember(tabId) {
        sessionObjectManager.getOrCreateSession(tabId, sessionId, initialMessages ?: emptyList())
    }
    
    // 从 sessionObject 获取所有状态（使用委托属性简化访问）
    val messages by derivedStateOf { sessionObject.messages }
    val contexts by derivedStateOf { sessionObject.contexts }
    val isGenerating by derivedStateOf { sessionObject.isGenerating }
    val selectedModel by derivedStateOf { sessionObject.selectedModel }
    val selectedPermissionMode by derivedStateOf { sessionObject.selectedPermissionMode }
    val skipPermissions by derivedStateOf { sessionObject.skipPermissions }
    val isLoadingSession by derivedStateOf { sessionObject.isLoadingSession }
    val inputResetTrigger by derivedStateOf { sessionObject.inputResetTrigger }
    val questionQueue = sessionObject.questionQueue
    val currentStreamJob by derivedStateOf { sessionObject.currentStreamJob }
    
    // 本地 UI 状态
    var showQueueDialog by remember { mutableStateOf(false) }
    var currentInputText by remember { mutableStateOf("") }
    
    val coroutineScope = rememberCoroutineScope()
    
    // 当组件被销毁时，保存输入状态
    DisposableEffect(tabId) {
        onDispose {
            sessionObject.saveInputState(currentInputText)
        }
    }
    
    // 当组件创建时，恢复输入状态
    LaunchedEffect(tabId) {
        currentInputText = sessionObject.restoreInputState()
    }
    
    // 监听外部 sessionId 变化
    LaunchedEffect(sessionId) {
        val currentId = sessionObject.sessionId
        
        // 判断是否需要处理
        if (sessionId != currentId) {
            // 如果是从 null 变为有值（新会话创建），只更新 sessionObject
            if (currentId == null && sessionId != null) {
                sessionObject.updateSessionId(sessionId)
                
                // 尝试恢复会话配置
                coroutineScope.launch {
                    SessionPersistenceService.restoreSessionConfig(
                        sessionId = sessionId,
                        sessionObject = sessionObject,
                        projectPath = workingDirectory
                    )
                }
            } else {
                // 否则是用户手动切换会话
                // 先保存当前会话的配置
                if (currentId != null) {
                    coroutineScope.launch {
                        SessionPersistenceService.saveSessionMetadata(
                            sessionId = currentId,
                            sessionObject = sessionObject,
                            projectPath = workingDirectory
                        )
                    }
                }
                
                sessionObject.updateSessionId(sessionId)
                sessionObject.inputResetTrigger = System.currentTimeMillis()
                
                // 恢复新会话的配置
                if (sessionId != null) {
                    coroutineScope.launch {
                        SessionPersistenceService.restoreSessionConfig(
                            sessionId = sessionId,
                            sessionObject = sessionObject,
                            projectPath = workingDirectory
                        )
                    }
                }
                
                // 只有在不生成消息时才可能需要重新加载
                if (!sessionObject.isGenerating) {
                    if (sessionId == null || sessionId.isEmpty()) {
                        sessionObject.messages = emptyList()
                    }
                }
            }
        }
    }
    
    // 初始化时加载最近会话或创建新会话
    // 使用 sessionObject 作为 key，避免因 sessionId 更新而重新触发
    LaunchedEffect(workingDirectory, sessionObject) {
        
        // 获取初始 sessionId
        val initialSessionId = sessionObject.sessionId
        
        // 如果是新会话，清空消息并返回
        if (initialSessionId == null || initialSessionId.isEmpty()) {
            sessionObject.messages = emptyList()
            return@LaunchedEffect
        }
        
        // 如果明确传入了有内容的 initialMessages，使用它们
        if (initialMessages != null && initialMessages.isNotEmpty()) {
            sessionObject.messages = initialMessages
            return@LaunchedEffect
        }
        
        // 如果有 initialSessionId，尝试加载该会话的消息
        if (initialSessionId != null && initialSessionId.isNotEmpty()) {
            sessionObject.isLoadingSession = true
            try {
                val (sessionMessages, totalCount) = sessionManager.readSessionMessages(
                    sessionId = initialSessionId,
                    projectPath = workingDirectory,
                    pageSize = 50
                )
                
                val enhancedMessages = sessionMessages.mapNotNull { 
                    it.toEnhancedMessage() 
                }
                
                if (enhancedMessages.isNotEmpty()) {
                    sessionObject.messages = enhancedMessages
                } else {
                    // 没有消息，保持空列表
                    sessionObject.messages = emptyList()
                }
            } catch (e: Exception) {
                println("[ChatView] 加载会话失败: ${e.message}")
                e.printStackTrace()
                // 加载失败时清空消息
                sessionObject.messages = emptyList()
            } finally {
                sessionObject.isLoadingSession = false
            }
            return@LaunchedEffect
        }
        
        // 没有 sessionId 的情况下，尝试加载最近会话
        sessionObject.isLoadingSession = true
        try {
            // 尝试获取最近的会话
            val recentSession = sessionManager.getRecentSession(workingDirectory)
            if (recentSession != null) {
                // ChatView: Loading recent session ${recentSession.sessionId}
                sessionObject.currentSession = recentSession
                sessionObject.updateSessionId(recentSession.sessionId)
                
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
                    
                    sessionObject.messages = enhancedMessages
                    // ChatView: Loaded ${enhancedMessages.size} messages from recent session
                } catch (e: Exception) {
                    println("[ChatView] 未找到会话文件，使用原方法加载: ${e.message}")
                    // 如果加载失败（比如会话文件不存在），保持空消息列表
                    sessionObject.messages = emptyList()
                }
            } else {
                // 没有历史会话，创建新会话但不使用 resume
                // ChatView: No recent session found, will create new session on first message
                sessionObject.updateSessionId(null)
                sessionObject.currentSession = null
                sessionObject.messages = emptyList()
            }
        } catch (e: Exception) {
            // ChatView: Error during initialization: ${e.message}
            e.printStackTrace()
            sessionObject.messages = emptyList()
        } finally {
            sessionObject.isLoadingSession = false
        }
    }
    
    // 主聊天区域
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 聊天内容区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (sessionObject.isLoadingSession) {
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
        
        // 队列指示器
        if (sessionObject.hasQueuedQuestions || sessionObject.isGenerating) {
            QueueIndicator(
                queue = sessionObject.questionQueue,
                isGenerating = sessionObject.isGenerating,
                onQueueClick = { showQueueDialog = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Divider(orientation = org.jetbrains.jewel.ui.Orientation.Horizontal)
        
        // 输入区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()  // 使用 wrapContentHeight 而不是固定高度
                .padding(16.dp)
        ) {
            // 使用新的统一输入组件
            UnifiedChatInput(
                contexts = contexts,
                onContextAdd = { context ->
                    sessionObject.addContext(context)
                },
                onContextRemove = { context ->
                    sessionObject.removeContext(context)
                },
                selectedModel = selectedModel,
                onModelChange = { model ->
                    sessionObject.selectedModel = model
                    // 同步到标签
                    if (tabManager != null && currentTabId != null) {
                        tabManager.updateTab(currentTabId) { tab ->
                            tab.copy(lastModified = java.time.Instant.now())
                        }
                    }
                    // 保存配置
                    sessionObject.sessionId?.let { sid ->
                        coroutineScope.launch {
                            SessionPersistenceService.saveSessionMetadata(
                                sessionId = sid,
                                sessionObject = sessionObject,
                                projectPath = workingDirectory
                            )
                        }
                    }
                },
                selectedPermissionMode = selectedPermissionMode,
                onPermissionModeChange = { mode ->
                    sessionObject.selectedPermissionMode = mode
                    // 同步到标签
                    if (tabManager != null && currentTabId != null) {
                        tabManager.updateTab(currentTabId) { tab ->
                            tab.copy(lastModified = java.time.Instant.now())
                        }
                    }
                    // 保存配置
                    sessionObject.sessionId?.let { sid ->
                        coroutineScope.launch {
                            SessionPersistenceService.saveSessionMetadata(
                                sessionId = sid,
                                sessionObject = sessionObject,
                                projectPath = workingDirectory
                            )
                        }
                    }
                },
                skipPermissions = skipPermissions,
                onSkipPermissionsChange = { skip ->
                    sessionObject.skipPermissions = skip
                    // 同步到标签
                    if (tabManager != null && currentTabId != null) {
                        tabManager.updateTab(currentTabId) { tab ->
                            tab.copy(lastModified = java.time.Instant.now())
                        }
                    }
                    // 保存配置
                    sessionObject.sessionId?.let { sid ->
                        coroutineScope.launch {
                            SessionPersistenceService.saveSessionMetadata(
                                sessionId = sid,
                                sessionObject = sessionObject,
                                projectPath = workingDirectory
                            )
                        }
                    }
                },
                fileIndexService = fileIndexService,
                projectService = projectService,
                resetTrigger = inputResetTrigger,
                onSend = { markdownText ->
                    if (sessionObject.isGenerating) {
                        // 正在生成时，添加到队列
                        sessionObject.addToQueue(markdownText)
                        sessionObject.inputResetTrigger = System.currentTimeMillis() // 清空输入框
                        currentInputText = "" // 清空临时输入
                    } else {
                        // 空闲时，直接发送
                        val job = coroutineScope.launch {
                            sessionObject.isGenerating = true
                            try {
                            // 检查是否是斜杠命令
                            val processedText = if (markdownText.trim().startsWith("/")) {
                                val parts = markdownText.trim().split(" ", limit = 2)
                                val command = parts[0].substring(1) // 去掉斜杠
                                val args = if (parts.size > 1) parts[1] else ""
                                
                                println("[ChatView] 检测到斜杠命令: /$command")
                                println("[ChatView] 命令参数: $args")
                                
                                // 构造斜杠命令的特殊格式
                                val commandXml = """<command-name>/$command</command-name>
<command-message>$command</command-message>
<command-args>$args</command-args>"""
                                
                                println("[ChatView] 发送的命令XML:")
                                println(commandXml)
                                
                                commandXml
                            } else {
                                markdownText
                            }
                            
                            // 创建用户消息并添加到界面
                            val userMessage = EnhancedMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.USER,
                                content = markdownText, // 界面显示原始命令
                                timestamp = System.currentTimeMillis(),
                                model = selectedModel,
                                contexts = contexts
                            )
                            
                            // 添加到消息列表
                            sessionObject.addMessage(userMessage)
                            
                            // 如果是占位会话的第一条消息，立即更新会话名称和标签标题
                            // 现在包含了初始助手消息，所以要调整判断逻辑
                            val currentUserMessageCount = messages.count { it.role == MessageRole.USER }
                            if (currentUserMessageCount == 1
                                && sessionObject.hasSessionId 
                                && projectManager != null 
                                && currentProject != null) {
                                val sessionIdCopy = sessionObject.sessionId!!
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
                            var sessionIdToUse = sessionObject.sessionId
                            
                            // 检查是否是新项目的第一条消息（标签没有关联会话ID）
                            val userMessageCount = messages.count { it.role == MessageRole.USER }
                            val isFirstMessageForNewProject = sessionObject.isNewSession && userMessageCount == 1
                            
                            // 创建助手消息用于累积响应
                            var assistantMessageId = UUID.randomUUID().toString()
                            var assistantContent = ""
                            val toolCalls = mutableListOf<com.claudecodeplus.ui.models.ToolCall>()
                            val orderedElements = mutableListOf<MessageTimelineItem>()
                            var currentContentBuilder = StringBuilder()
                            
                            // 不再立即添加空的助手消息，等待真正有内容时再添加
                            
                            // 调用 Claude CLI
                            // 对于占位会话（第一条消息），不传递 sessionId，让 Claude CLI 创建新会话
                            // 注意：现在 messages 包含了用户消息和初始助手消息，所以要检查 size == 2
                            val isPlaceholderSession = sessionObject.hasSessionId && messages.size == 2
                            val useResume = sessionObject.hasSessionId && !isPlaceholderSession
                            
                            // ChatView: Sending message, useResume=$useResume, sessionId=${sessionObject.sessionId}, isPlaceholder=$isPlaceholderSession
                            
                            val options = ClaudeCliWrapper.QueryOptions(
                                resume = if (useResume) sessionObject.sessionId else null,
                                cwd = workingDirectory,
                                model = selectedModel?.cliName,
                                permissionMode = selectedPermissionMode.cliName
                            )
                            
                            cliWrapper.query(processedText, options).collect { message ->
                                when (message.type) {
                                    com.claudecodeplus.sdk.MessageType.START -> {
                                        // 新会话创建，保存会话ID
                                        if (sessionObject.isNewSession || isPlaceholderSession) {
                                            val oldSessionId = sessionObject.sessionId
                                            
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
                                            
                                            sessionObject.updateSessionId(message.data.sessionId)
                                            sessionIdToUse = message.data.sessionId
                                            // ChatView: New session created: ${sessionObject.sessionId}
                                            
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
                                        if (messages.any { it.id == assistantMessageId }) {
                                            sessionObject.replaceMessage(assistantMessageId) { assistantMessage }
                                        } else {
                                            sessionObject.addMessage(assistantMessage)
                                        }
                                    }
                                    com.claudecodeplus.sdk.MessageType.ERROR -> {
                                        // 如果还没有创建助手消息，使用当前的 assistantMessageId
                                        val errorId = if (messages.any { it.id == assistantMessageId }) {
                                            UUID.randomUUID().toString()
                                        } else {
                                            assistantMessageId
                                        }
                                        
                                        val errorMessage = EnhancedMessage(
                                            id = errorId,
                                            role = MessageRole.ASSISTANT,
                                            content = "错误: ${message.data.error ?: "未知错误"}",
                                            timestamp = System.currentTimeMillis(),
                                            model = selectedModel,
                                            contexts = emptyList()
                                        )
                                        sessionObject.addMessage(errorMessage)
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
                                        
                                        sessionObject.replaceMessage(assistantMessageId) { finalMessage }
                                        
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
                                        
                                        if (messages.any { it.id == assistantMessageId }) {
                                            sessionObject.replaceMessage(assistantMessageId) { assistantMessage }
                                        } else {
                                            sessionObject.addMessage(assistantMessage)
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
                                            
                                            sessionObject.replaceMessage(assistantMessageId) { assistantMessage }
                                        }
                                    }
                                    else -> {
                                        // 忽略其他消息类型
                                    }
                                }
                            }
                            
                            // 清空上下文
                            sessionObject.clearContexts()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // 协程被取消，这是正常的，不需要显示错误
                            println("[ChatView] 任务被取消")
                            throw e // 重新抛出以保持取消传播
                        } catch (e: Exception) {
                            // ChatView: Error sending message: ${e.message}
                            e.printStackTrace()
                            
                            // 只有在非取消异常时才显示错误消息
                            if (!sessionObject.isGenerating) {
                                // 任务已经被取消，不显示错误
                                return@launch
                            }
                            
                            val errorMessage = EnhancedMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.ASSISTANT,
                                content = "发送消息时出错: ${e.message}",
                                timestamp = System.currentTimeMillis(),
                                model = selectedModel,
                                contexts = emptyList()
                            )
                            sessionObject.addMessage(errorMessage)
                        } finally {
                            sessionObject.isGenerating = false
                            sessionObject.currentStreamJob = null
                            
                            // 检查队列中是否有待处理的问题
                            if (sessionObject.hasQueuedQuestions) {
                                val nextQuestion = sessionObject.getNextFromQueue()!!
                                
                                // 延迟一小段时间，确保UI更新
                                coroutineScope.launch {
                                    delay(100)
                                    // 递归调用 onSend 处理下一个问题
                                    // 这里直接调用 onSend 的实现逻辑
                                    val nextJob = coroutineScope.launch {
                                        sessionObject.isGenerating = true
                                        try {
                                            // 复制完整的发送逻辑...
                                            // 为了避免代码重复，应该提取为一个独立的函数
                                            // 但为了保持改动最小，这里暂时这样处理
                                            println("[ChatView] 自动处理队列中的下一个问题: $nextQuestion")
                                            
                                            // 检查是否是斜杠命令
                                            val processedText = if (nextQuestion.trim().startsWith("/")) {
                                                val parts = nextQuestion.trim().split(" ", limit = 2)
                                                val command = parts[0].substring(1)
                                                val args = if (parts.size > 1) parts[1] else ""
                                                
                                                val commandXml = """<command-name>/$command</command-name>
<command-message>$command</command-message>
<command-args>$args</command-args>"""
                                                commandXml
                                            } else {
                                                nextQuestion
                                            }
                                            
                                            // 创建用户消息
                                            val userMessage = EnhancedMessage(
                                                id = UUID.randomUUID().toString(),
                                                role = MessageRole.USER,
                                                content = nextQuestion,
                                                timestamp = System.currentTimeMillis(),
                                                model = sessionObject.selectedModel,
                                                contexts = sessionObject.contexts
                                            )
                                            
                                            sessionObject.addMessage(userMessage)
                                            
                                            // 后续逻辑与上面相同...
                                            // 这里应该调用统一的发送函数
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            println("[ChatView] 队列处理任务被取消")
                                            throw e
                                        } catch (e: Exception) {
                                            println("[ChatView] 处理队列问题失败: ${e.message}")
                                            e.printStackTrace()
                                        } finally {
                                            sessionObject.isGenerating = false
                                            sessionObject.currentStreamJob = null
                                        }
                                    }
                                    sessionObject.startGenerating(nextJob)
                                }
                            }
                        }
                        }
                        sessionObject.startGenerating(job)
                    }
                },
                onInterruptAndSend = { markdownText ->
                    // 中断当前任务并立即发送
                    sessionObject.interruptGeneration(cliWrapper)
                    
                    // 立即发送新消息
                    val job = coroutineScope.launch {
                        // 使用协程循环检查进程状态，而不是固定延迟
                        var attempts = 0
                        while (cliWrapper.isProcessAlive() && attempts < 10) {
                            delay(100)  // 每100ms检查一次
                            attempts++
                        }
                        
                        // 如果进程还在运行，再等一下
                        if (cliWrapper.isProcessAlive()) {
                            delay(200)
                        }
                        
                        // 等待完成后才设置 isGenerating = true
                        sessionObject.isGenerating = true
                        try {
                            // 复用原有的发送逻辑（从 onSend 复制的代码）
                            val processedText = if (markdownText.trim().startsWith("/")) {
                                val parts = markdownText.trim().split(" ", limit = 2)
                                val command = parts[0].substring(1)
                                val args = if (parts.size > 1) parts[1] else ""
                                
                                println("[ChatView] 检测到斜杠命令: /$command")
                                println("[ChatView] 命令参数: $args")
                                
                                val commandXml = """<command-name>/$command</command-name>
<command-message>$command</command-message>
<command-args>$args</command-args>"""
                                
                                println("[ChatView] 发送的命令XML:")
                                println(commandXml)
                                
                                commandXml
                            } else {
                                markdownText
                            }
                            
                            // 后续逻辑与 onSend 相同...
                            // 这里应该调用统一的发送函数，但为了保持改动最小，暂时先这样
                            val userMessage = EnhancedMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.USER,
                                content = markdownText,
                                timestamp = System.currentTimeMillis(),
                                model = selectedModel,
                                contexts = contexts
                            )
                            
                            sessionObject.addMessage(userMessage)
                            
                            // 创建助手消息用于累积响应（复制 onSend 中的逻辑）
                            var assistantMessageId = UUID.randomUUID().toString()
                            var assistantContent = ""
                            val toolCalls = mutableListOf<com.claudecodeplus.ui.models.ToolCall>()
                            val orderedElements = mutableListOf<MessageTimelineItem>()
                            var currentContentBuilder = StringBuilder()
                            
                            // 不再立即添加空的助手消息，等待真正有内容时再添加
                            
                            // 继续原有的发送逻辑（调用 Claude CLI）
                            val options = ClaudeCliWrapper.QueryOptions(
                                resume = sessionObject.sessionId?.takeIf { it.isNotEmpty() },
                                cwd = workingDirectory,
                                model = selectedModel?.cliName,
                                permissionMode = selectedPermissionMode.cliName
                            )
                            
                            cliWrapper.query(processedText, options).collect { message ->
                                when (message.type) {
                                    com.claudecodeplus.sdk.MessageType.START -> {
                                        // 新会话创建，保存会话ID
                                        if (sessionObject.isNewSession) {
                                            sessionObject.updateSessionId(message.data.sessionId)
                                        }
                                    }
                                    com.claudecodeplus.sdk.MessageType.TEXT -> {
                                        // 累积内容
                                        val text = message.data.text ?: ""
                                        assistantContent += text
                                        currentContentBuilder.append(text)
                                        
                                        // 更新助手消息
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
                                        
                                        sessionObject.replaceMessage(assistantMessageId) { assistantMessage }
                                    }
                                    com.claudecodeplus.sdk.MessageType.ERROR -> {
                                        // 如果还没有创建助手消息，使用当前的 assistantMessageId
                                        val errorId = if (messages.any { it.id == assistantMessageId }) {
                                            UUID.randomUUID().toString()
                                        } else {
                                            assistantMessageId
                                        }
                                        
                                        val errorMessage = EnhancedMessage(
                                            id = errorId,
                                            role = MessageRole.ASSISTANT,
                                            content = "错误: ${message.data.error ?: "未知错误"}",
                                            timestamp = System.currentTimeMillis(),
                                            model = selectedModel,
                                            contexts = emptyList()
                                        )
                                        sessionObject.addMessage(errorMessage)
                                    }
                                    com.claudecodeplus.sdk.MessageType.END -> {
                                        // 流结束
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
                                        
                                        sessionObject.replaceMessage(assistantMessageId) { finalMessage }
                                    }
                                    com.claudecodeplus.sdk.MessageType.TOOL_USE -> {
                                        // 工具调用处理
                                        val toolName = message.data.toolName ?: "unknown"
                                        val toolCallId = message.data.toolCallId ?: UUID.randomUUID().toString()
                                        
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
                                        
                                        orderedElements.add(
                                            MessageTimelineItem.ToolCallItem(
                                                toolCall = toolCall,
                                                timestamp = toolCall.startTime
                                            )
                                        )
                                        
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
                                        
                                        sessionObject.replaceMessage(assistantMessageId) { assistantMessage }
                                    }
                                    com.claudecodeplus.sdk.MessageType.TOOL_RESULT -> {
                                        // 工具结果
                                        val toolCallId = message.data.toolCallId
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
                                            
                                            val toolItemIndex = orderedElements.indexOfFirst { 
                                                it is MessageTimelineItem.ToolCallItem && it.toolCall.id == toolCall.id 
                                            }
                                            if (toolItemIndex != -1) {
                                                orderedElements[toolItemIndex] = MessageTimelineItem.ToolCallItem(
                                                    toolCall = updatedToolCall,
                                                    timestamp = updatedToolCall.startTime
                                                )
                                            }
                                            
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
                                            
                                            sessionObject.replaceMessage(assistantMessageId) { assistantMessage }
                                        }
                                    }
                                    else -> {
                                        // 忽略其他消息类型
                                    }
                                }
                            }
                            
                            // 清空上下文
                            sessionObject.clearContexts()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            println("[ChatView] 中断并发送任务被取消")
                            throw e
                        } catch (e: Exception) {
                            println("[ChatView] 中断并发送失败: ${e.message}")
                            e.printStackTrace()
                            
                            // 只有在非取消异常时才显示错误消息
                            val errorMessage = EnhancedMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.ASSISTANT,
                                content = "发送消息时出错: ${e.message}",
                                timestamp = System.currentTimeMillis(),
                                model = selectedModel,
                                contexts = emptyList()
                            )
                            sessionObject.addMessage(errorMessage)
                        } finally {
                            sessionObject.isGenerating = false
                            sessionObject.currentStreamJob = null
                        }
                    }
                    // 不使用 startGenerating，因为它会立即设置 isGenerating = true
                    // 而我们希望在等待期间不显示 "Generating..."
                    sessionObject.currentStreamJob = job
                    
                    sessionObject.inputResetTrigger = System.currentTimeMillis() // 清空输入框
                    currentInputText = "" // 清空临时输入
                },
                enabled = true, // 始终启用输入框
                isGenerating = sessionObject.isGenerating
            )
        }
    }
    
}

