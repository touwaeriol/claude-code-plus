package com.claudecodeplus.ui.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.services.UnifiedSessionService
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
    unifiedSessionService: UnifiedSessionService,
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
    val coroutineScope = rememberCoroutineScope()
    
    // 添加调试信息
    println("=== ChatView 组件开始渲染 ===")
    println("tabId: $tabId")
    println("sessionId: $sessionId")
    println("workingDirectory: $workingDirectory")
    println("initialMessages size: ${initialMessages?.size ?: 0}")
    println("===============================")
    
    // CLI 调用现在通过 UnifiedSessionService 处理
    
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
    
    // 文件监听触发器
    var fileWatchTrigger by remember { mutableStateOf(0L) }
    
    
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
    
    // 文件监听：只在有 sessionId 且存在会话文件时启动
    LaunchedEffect(sessionObject.sessionId, fileWatchTrigger) {
        val currentSessionId = sessionObject.sessionId
        
        if (currentSessionId != null && currentSessionId.isNotEmpty()) {
            // 检查会话文件是否存在
            if (unifiedSessionService.sessionExists(currentSessionId)) {
                println("[ChatView] 会话文件存在，开始订阅文件监听: $currentSessionId")
                
                try {
                    // 订阅实时更新 - 这会自动加载初始消息并监听后续更新
                    unifiedSessionService.subscribeToSession(currentSessionId)
                        .collect { updatedMessages ->
                            // 更新消息列表
                            sessionObject.messages = updatedMessages
                            println("[ChatView] 收到实时消息更新，消息数: ${updatedMessages.size}")
                        }
                } catch (e: Exception) {
                    println("[ChatView] 文件监听订阅失败: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                println("[ChatView] 会话文件不存在，跳过文件监听: $currentSessionId")
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
                            
                            // 添加到消息列表 - 注释掉，改为通过文件监听获取
                            // sessionObject.addMessage(userMessage)
                            
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
                            // 现在总是使用预设的 sessionId（如果有的话）
                            val currentSessionId = sessionObject.sessionId
                            
                            println("[ChatView] 准备调用 Claude CLI, sessionId=$currentSessionId")
                            
                            // 使用新的简化API执行CLI命令
                            val options = ClaudeCliWrapper.QueryOptions(
                                sessionId = currentSessionId,  // 使用预设的 sessionId
                                cwd = workingDirectory,
                                model = selectedModel?.cliName,
                                permissionMode = selectedPermissionMode.cliName
                            )
                            
                            println("[ChatView] 开始执行 CLI 命令")
                            
                            try {
                                // 执行 CLI 命令
                                val result = unifiedSessionService.query(processedText, options)
                                
                                if (result.success) {
                                    // 命令执行成功，确认会话ID
                                    val resultSessionId = result.sessionId ?: currentSessionId
                                    sessionIdToUse = resultSessionId
                                    
                                    // 确保 sessionObject 中的 sessionId 是正确的
                                    if (sessionObject.sessionId != resultSessionId) {
                                        sessionObject.updateSessionId(resultSessionId)
                                    }
                                    
                                    // 等待文件写入后触发文件监听
                                    if (result.sessionId != null) {
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(200) // 等待文件写入
                                            // 触发文件监听检查
                                            fileWatchTrigger = System.currentTimeMillis()
                                        }
                                    }
                                } else {
                                    // 命令执行失败
                                    val errorMessage = EnhancedMessage(
                                        id = "error_${System.currentTimeMillis()}",
                                        role = MessageRole.SYSTEM,
                                        content = "错误: ${result.errorMessage ?: "未知错误"}",
                                        timestamp = System.currentTimeMillis(),
                                        toolCalls = emptyList(),
                                        orderedElements = emptyList()
                                    )
                                    sessionObject.addMessage(errorMessage)
                                }
                            } catch (e: Exception) {
                                // 处理异常
                                val errorMessage = EnhancedMessage(
                                    id = "error_${System.currentTimeMillis()}",
                                    role = MessageRole.SYSTEM,
                                    content = "执行失败: ${e.message}",
                                    timestamp = System.currentTimeMillis(),
                                    toolCalls = emptyList(),
                                    orderedElements = emptyList()
                                )
                                sessionObject.addMessage(errorMessage)
                            } finally {
                                // 清理状态
                                sessionObject.stopGenerating()
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
                                    // TODO: 实现队列处理逻辑，使用新的统一API
                                    println("[ChatView] 队列处理需要实现新的统一API调用")
                                }
                            }
                        }
                        }
                        sessionObject.startGenerating(job)
                    }
                },
                onInterruptAndSend = { markdownText ->
                    println("[ChatView] onInterruptAndSend 被调用，新消息: $markdownText")
                    
                    // 立即发送新消息
                    coroutineScope.launch {
                        println("[ChatView] 开始中断流程")
                        
                        // 1. 中断当前任务
                        sessionObject.interruptGeneration()
                        println("[ChatView] 进程已终止")
                        
                        // 2. 进程已结束，设置 isGenerating = false
                        sessionObject.isGenerating = false
                        println("[ChatView] 已设置 isGenerating = false")
                        
                        // 3. 现在开始新的查询
                        println("[ChatView] 准备开始新查询: $markdownText")
                        val job = coroutineScope.launch {
                            sessionObject.isGenerating = true
                            println("[ChatView] 新查询协程已启动，isGenerating = true")
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
                                sessionId = sessionObject.sessionId?.takeIf { it.isNotEmpty() },
                                cwd = workingDirectory,
                                model = selectedModel?.cliName,
                                permissionMode = selectedPermissionMode.cliName
                            )
                            
                            println("[ChatView] [中断后] 执行统一API调用")
                            val result = unifiedSessionService.query(processedText, options)
                            if (result.success) {
                                val resultSessionId = result.sessionId ?: sessionObject.sessionId
                                println("[ChatView] [中断后] 命令执行成功, sessionId: $resultSessionId")
                                
                                // 确认会话ID并启用实时监听
                                if (resultSessionId != null) {
                                    if (sessionObject.sessionId != resultSessionId) {
                                        sessionObject.updateSessionId(resultSessionId)
                                    }
                                    
                                    // 注意：文件监听订阅现在由专门的 LaunchedEffect 管理，避免重复订阅
                                }
                            } else {
                                println("[ChatView] [中断后] 命令执行失败: ${result.errorMessage}")
                                
                                // 显示错误消息
                                val errorMessage = EnhancedMessage(
                                    id = UUID.randomUUID().toString(),
                                    role = MessageRole.ASSISTANT,
                                    content = "执行命令时出错: ${result.errorMessage}",
                                    timestamp = System.currentTimeMillis(),
                                    model = selectedModel,
                                    contexts = emptyList()
                                )
                                sessionObject.addMessage(errorMessage)
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
                        // 使用 startGenerating 确保状态正确管理
                        sessionObject.startGenerating(job)
                    }
                    
                    sessionObject.inputResetTrigger = System.currentTimeMillis() // 清空输入框
                    currentInputText = "" // 清空临时输入
                },
                enabled = true, // 始终启用输入框
                isGenerating = sessionObject.isGenerating
            )
        }
    }
    
}

