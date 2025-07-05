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
import com.claudecodeplus.sdk.MessageType
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.rememberScrollState

/**
 * 带会话管理的聊天应用
 */
@Composable
fun ChatAppWithSessions(
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String,
    fileIndexService: FileIndexService? = null,
    projectService: ProjectService? = null,
    sessionManager: ClaudeSessionManager = ClaudeSessionManager(),
    modifier: Modifier = Modifier
) {
    // 状态管理
    var showSessionPanel by remember { mutableStateOf(true) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var currentSession by remember { mutableStateOf<SessionInfo?>(null) }
    var contexts by remember { mutableStateOf(listOf<ContextReference>()) }
    var isGenerating by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(AiModel.OPUS) }
    var messages by remember { mutableStateOf(listOf<EnhancedMessage>()) }
    var isLoadingSession by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // 初始化时加载最近会话
    LaunchedEffect(workingDirectory) {
        val recentSession = sessionManager.getRecentSession(workingDirectory)
        if (recentSession != null) {
            currentSession = recentSession
            currentSessionId = recentSession.sessionId
        } else {
            // 创建新会话
            val newSessionId = sessionManager.createSession()
            currentSessionId = newSessionId
        }
    }
    
    Row(modifier = modifier.fillMaxSize()) {
        // 会话历史面板
        if (showSessionPanel) {
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
            ) {
                SessionListPanel(
                    projectPath = workingDirectory,
                    sessionManager = sessionManager,
                    currentSessionId = currentSessionId,
                    onSessionSelect = { session ->
                        coroutineScope.launch {
                            isLoadingSession = true
                            try {
                                // 加载会话消息
                                val (sessionMessages, totalCount) = sessionManager.readSessionMessages(
                                    sessionId = session.sessionId,
                                    projectPath = workingDirectory,
                                    pageSize = 100 // 加载更多消息以获得完整上下文
                                )
                                
                                // 转换为 EnhancedMessage
                                val enhancedMessages = sessionMessages.mapNotNull { 
                                    it.toEnhancedMessage() 
                                }
                                
                                // 更新状态
                                currentSession = session
                                currentSessionId = session.sessionId
                                messages = enhancedMessages
                                
                                println("ChatAppWithSessions: Loaded ${enhancedMessages.size} messages from session ${session.sessionId}")
                            } catch (e: Exception) {
                                println("ChatAppWithSessions: Error loading session: ${e.message}")
                                e.printStackTrace()
                            } finally {
                                isLoadingSession = false
                            }
                        }
                    },
                    onNewSession = {
                        val newSessionId = sessionManager.createSession()
                        currentSessionId = newSessionId
                        currentSession = null
                        contexts = emptyList()
                        messages = emptyList()
                    },
                    onDeleteSession = { session ->
                        coroutineScope.launch {
                            sessionManager.deleteSession(session.sessionId, workingDirectory)
                            if (session.sessionId == currentSessionId) {
                                val newSessionId = sessionManager.createSession()
                                currentSessionId = newSessionId
                                currentSession = null
                                contexts = emptyList()
                                messages = emptyList()
                            }
                        }
                    }
                )
            }
            
            Divider(orientation = org.jetbrains.jewel.ui.Orientation.Vertical)
        }
        
        // 主聊天区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // 工具栏
            ChatToolbar(
                showSessionPanel = showSessionPanel,
                onToggleSessionPanel = { showSessionPanel = !showSessionPanel },
                currentSession = currentSession,
                onNewSession = {
                    val newSessionId = sessionManager.createSession()
                    currentSessionId = newSessionId
                    currentSession = null
                    contexts = emptyList()
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
                } else if (currentSessionId != null) {
                    // 只显示消息列表，不包含输入框
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
                                messages.forEach { message ->
                                    // 使用 UnifiedInputArea 的 DISPLAY 模式显示消息
                                    UnifiedInputArea(
                                        mode = InputAreaMode.DISPLAY,
                                        message = message,
                                        onContextClick = { uri ->
                                            println("Context clicked: $uri")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // 空状态
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "选择或创建一个会话开始对话",
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = JewelTheme.globalColors.text.disabled
                                )
                            )
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
                    onSend = { markdownText ->
                        if (currentSessionId == null) return@UnifiedInputArea
                        
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
                                
                                // 简化的会话管理策略：
                                // 暂时不使用 --resume，每次都创建新会话
                                // TODO: 后续需要正确处理 Claude CLI 的会话ID映射
                                println("ChatAppWithSessions: Sending message without resume (temporary fix)")
                                
                                // 创建助手消息用于累积响应
                                var assistantMessageId = UUID.randomUUID().toString()
                                var assistantContent = ""
                                
                                // 调用 Claude CLI - 不使用 sessionId 参数
                                cliWrapper.sendMessage(
                                    message = markdownText,
                                    sessionId = null  // 暂时不使用 resume
                                ).collect { response ->
                                    when (response) {
                                        is com.claudecodeplus.sdk.ClaudeCliWrapper.StreamResponse.SessionStart -> {
                                            // 新会话创建，保存会话ID
                                            if (currentSessionId == null) {
                                                currentSessionId = response.sessionId
                                                println("ChatAppWithSessions: New session created: $currentSessionId")
                                            }
                                        }
                                        is com.claudecodeplus.sdk.ClaudeCliWrapper.StreamResponse.Content -> {
                                            // 累积内容
                                            assistantContent += response.content
                                            
                                            // 更新或创建助手消息
                                            val assistantMessage = EnhancedMessage(
                                                id = assistantMessageId,
                                                role = MessageRole.ASSISTANT,
                                                content = assistantContent,
                                                timestamp = System.currentTimeMillis(),
                                                model = selectedModel,
                                                contexts = emptyList()
                                            )
                                            
                                            // 替换或添加消息
                                            messages = if (messages.any { it.id == assistantMessageId }) {
                                                messages.map { if (it.id == assistantMessageId) assistantMessage else it }
                                            } else {
                                                messages + assistantMessage
                                            }
                                        }
                                        is com.claudecodeplus.sdk.ClaudeCliWrapper.StreamResponse.Error -> {
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
                                        is com.claudecodeplus.sdk.ClaudeCliWrapper.StreamResponse.Complete -> {
                                            // 流结束
                                        }
                                    }
                                }
                                
                                // 清空上下文
                                contexts = emptyList()
                            } catch (e: Exception) {
                                println("ChatAppWithSessions: Error sending message: ${e.message}")
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
                    enabled = !isGenerating && currentSessionId != null,
                    isGenerating = isGenerating,
                    fileIndexService = fileIndexService
                )
            }
        }
    }
}

/**
 * 聊天工具栏
 */
@Composable
private fun ChatToolbar(
    showSessionPanel: Boolean,
    onToggleSessionPanel: () -> Unit,
    currentSession: SessionInfo?,
    onNewSession: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(JewelTheme.globalColors.panelBackground)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 切换会话面板按钮
        IconButton(onClick = onToggleSessionPanel) {
            Icon(
                key = if (showSessionPanel) {
                    AllIconsKeys.Actions.ArrowCollapse
                } else {
                    AllIconsKeys.Actions.ArrowExpand
                },
                contentDescription = if (showSessionPanel) "隐藏会话列表" else "显示会话列表",
                modifier = Modifier.size(16.dp)
            )
        }
        
        // 当前会话信息
        Text(
            text = currentSession?.firstMessage?.take(50) ?: "新会话",
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