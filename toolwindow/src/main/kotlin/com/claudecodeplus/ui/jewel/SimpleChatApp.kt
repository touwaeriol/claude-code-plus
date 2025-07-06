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
 * 简化的聊天应用 - 无会话历史面板
 * 自动加载最近会话或创建新会话
 */
@Composable
fun SimpleChatApp(
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
                println("SimpleChatApp: Loading recent session ${recentSession.sessionId}")
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
                    println("SimpleChatApp: Loaded ${enhancedMessages.size} messages from recent session")
                } catch (e: Exception) {
                    println("SimpleChatApp: Error loading session messages: ${e.message}")
                    // 如果加载失败，保持空消息列表
                }
            } else {
                // 没有历史会话，创建新会话但不使用 resume
                println("SimpleChatApp: No recent session found, will create new session on first message")
                currentSessionId = null
                currentSession = null
            }
        } catch (e: Exception) {
            println("SimpleChatApp: Error during initialization: ${e.message}")
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
        // 简化的工具栏
        SimpleChatToolbar(
            currentSession = currentSession,
            onNewSession = {
                // 创建新会话
                currentSessionId = null
                currentSession = null
                contexts = emptyList()
                messages = emptyList()
                println("SimpleChatApp: Starting new session")
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
                                    // 使用 UnifiedInputArea 的 DISPLAY 模式显示消息
                                    UnifiedInputArea(
                                        mode = InputAreaMode.DISPLAY,
                                        message = message,
                                        onContextClick = { uri ->
                                            println("Context clicked: $uri")
                                            // 可以在这里处理文件点击等操作
                                            if (uri.startsWith("file://") && projectService != null) {
                                                val path = uri.removePrefix("file://")
                                                projectService.openFile(path)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
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
                            
                            // 调用 Claude CLI
                            // 如果有 currentSessionId 且不为空，使用 resume；否则创建新会话
                            val useResume = currentSessionId != null && currentSessionId!!.isNotEmpty()
                            
                            println("SimpleChatApp: Sending message, useResume=$useResume, sessionId=$currentSessionId")
                            
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
                                            println("SimpleChatApp: New session created: $currentSessionId")
                                        }
                                    }
                                    is ClaudeCliWrapper.StreamResponse.Content -> {
                                        // 累积内容
                                        assistantContent += response.content
                                        
                                        // 更新或创建助手消息
                                        val assistantMessage = EnhancedMessage(
                                            id = assistantMessageId,
                                            role = MessageRole.ASSISTANT,
                                            content = assistantContent,
                                            timestamp = System.currentTimeMillis(),
                                            model = selectedModel,
                                            contexts = emptyList(),
                                            toolCalls = toolCalls.toList()
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
                                        println("SimpleChatApp: Message complete")
                                    }
                                    is ClaudeCliWrapper.StreamResponse.ToolUse -> {
                                        // 工具调用
                                        println("SimpleChatApp: Tool use - ${response.toolName}")
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
                                            status = com.claudecodeplus.ui.models.ToolCallStatus.RUNNING
                                        )
                                        toolCalls.add(toolCall)
                                        
                                        // 更新助手消息，包含工具调用
                                        val assistantMessage = EnhancedMessage(
                                            id = assistantMessageId,
                                            role = MessageRole.ASSISTANT,
                                            content = assistantContent,
                                            timestamp = System.currentTimeMillis(),
                                            model = selectedModel,
                                            contexts = emptyList(),
                                            toolCalls = toolCalls.toList()
                                        )
                                        
                                        messages = if (messages.any { it.id == assistantMessageId }) {
                                            messages.map { if (it.id == assistantMessageId) assistantMessage else it }
                                        } else {
                                            messages + assistantMessage
                                        }
                                    }
                                    is ClaudeCliWrapper.StreamResponse.ToolResult -> {
                                        // 工具结果
                                        println("SimpleChatApp: Tool result - ${response.toolName}")
                                        // 更新对应的工具调用状态
                                        toolCalls.find { it.name == response.toolName }?.let { toolCall ->
                                            val index = toolCalls.indexOf(toolCall)
                                            toolCalls[index] = toolCall.copy(
                                                status = com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS,
                                                result = com.claudecodeplus.ui.models.ToolResult.Success(
                                                    output = response.result?.toString() ?: "No output"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // 清空上下文
                            contexts = emptyList()
                        } catch (e: Exception) {
                            println("SimpleChatApp: Error sending message: ${e.message}")
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
                isGenerating = isGenerating,
                fileIndexService = fileIndexService
            )
        }
    }
}

/**
 * 简化的聊天工具栏
 */
@Composable
private fun SimpleChatToolbar(
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