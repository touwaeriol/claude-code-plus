package com.claudecodeplus.ui.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.services.UnifiedSessionService
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.jewel.components.*
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.SessionManager
import com.claudecodeplus.ui.components.AssistantMessageDisplay
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.core.interfaces.ProjectService
import kotlinx.coroutines.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 优化后的聊天视图组件
 * 
 * 改进点：
 * 1. 使用 remember 替代 derivedStateOf
 * 2. 正确使用 LaunchedEffect 和 snapshotFlow
 * 3. 拆分组件，减少重组范围
 * 4. 修复协程作用域问题
 */
@Composable
fun ChatViewOptimized(
    unifiedSessionService: UnifiedSessionService,
    workingDirectory: String,
    fileIndexService: FileIndexService? = null,
    projectService: ProjectService? = null,
    sessionManager: ClaudeSessionManager = remember { ClaudeSessionManager() },
    tabId: String,
    initialMessages: List<EnhancedMessage>? = null,
    sessionId: String? = null,
    tabManager: com.claudecodeplus.ui.services.ChatTabManager? = null,
    currentTabId: String? = null,
    currentProject: Project? = null,
    projectManager: com.claudecodeplus.ui.services.ProjectManager? = null,
    modifier: Modifier = Modifier
) {
    // 使用 DisposableEffect 管理协程作用域，避免泄漏
    val scope = rememberCoroutineScope()
    
    println("=== ChatViewOptimized 渲染 ===")
    println("tabId: $tabId")
    println("sessionId: $sessionId")
    println("workingDirectory: $workingDirectory")
    
    // 获取或创建会话对象（使用 remember 确保稳定）
    val sessionObject = remember(tabId) {
        val project = currentProject ?: Project(
            id = "temp",
            name = "临时项目",
            path = workingDirectory
        )
        
        project.getOrCreateSession(
            tabId = tabId,
            initialSessionId = sessionId,
            initialMessages = initialMessages ?: emptyList()
        )
    }
    
    // 使用 LaunchedEffect 处理会话初始化
    LaunchedEffect(sessionObject, sessionId, initialMessages) {
        // 只在必要时更新
        if (sessionId != null && sessionObject.sessionId != sessionId) {
            sessionObject.updateSessionId(sessionId)
        }
        
        if (initialMessages != null && initialMessages.isNotEmpty()) {
            if (sessionObject.messages.isEmpty() || sessionObject.messages.size < initialMessages.size) {
                sessionObject.messages = initialMessages
                println("[ChatViewOptimized] 恢复会话消息: ${initialMessages.size} 条")
            }
        }
    }
    
    // 从 sessionObject 读取状态（使用直接访问，避免 derivedStateOf）
    val messages = sessionObject.messages
    val contexts = sessionObject.contexts
    val isGenerating = sessionObject.isGenerating
    val selectedModel = sessionObject.selectedModel
    val selectedPermissionMode = sessionObject.selectedPermissionMode
    val skipPermissions = sessionObject.skipPermissions
    val inputResetTrigger = sessionObject.inputResetTrigger
    
    // 发送消息函数
    val sendMessage: (String) -> Unit = remember(sessionObject) {
        { markdownText ->
            if (!sessionObject.isGenerating && markdownText.isNotBlank()) {
                // 添加用户消息
                val userMessage = EnhancedMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    role = MessageRole.USER,
                    content = markdownText,
                    timestamp = System.currentTimeMillis(),
                    model = sessionObject.selectedModel,
                    contexts = sessionObject.contexts
                )
                sessionObject.addMessage(userMessage)
                
                // 发送到 CLI
                scope.launch {
                    try {
                        sessionObject.sendMessage(markdownText, workingDirectory)
                    } catch (e: Exception) {
                        println("[ChatViewOptimized] 发送消息失败: ${e.message}")
                    }
                }
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 工具调用状态区域
        ToolCallStatusArea(
            messages = messages,
            modifier = Modifier.fillMaxWidth()
        )
        
        // 消息列表区域
        MessageListArea(
            messages = messages,
            sessionObject = sessionObject,
            projectService = projectService,
            modifier = Modifier.weight(1f)
        )
        
        Divider(orientation = Orientation.Horizontal)
        
        // 输入区域
        InputArea(
            sessionObject = sessionObject,
            isGenerating = isGenerating,
            contexts = contexts,
            selectedModel = selectedModel,
            selectedPermissionMode = selectedPermissionMode,
            skipPermissions = skipPermissions,
            inputResetTrigger = inputResetTrigger,
            fileIndexService = fileIndexService,
            projectService = projectService,
            onSend = sendMessage,
            onInterruptAndSend = { markdownText ->
                sessionObject.interruptGeneration()
                sendMessage(markdownText)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
    
    // 使用 DisposableEffect 清理资源
    DisposableEffect(sessionObject) {
        onDispose {
            // 清理时保存滚动位置等状态
            println("[ChatViewOptimized] 组件销毁，保存状态")
        }
    }
}

/**
 * 工具调用状态区域（独立组件，减少重组）
 */
@Composable
private fun ToolCallStatusArea(
    messages: List<EnhancedMessage>,
    modifier: Modifier = Modifier
) {
    // 获取最近的工具调用
    val recentToolCalls = remember(messages) {
        messages.lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.toolCalls
            ?: emptyList()
    }
    
    if (recentToolCalls.isNotEmpty()) {
        Column(modifier = modifier) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.95f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                com.claudecodeplus.ui.jewel.components.tools.CompactToolCallDisplay(
                    toolCalls = recentToolCalls,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Divider(orientation = Orientation.Horizontal)
        }
    }
}

/**
 * 消息列表区域（独立组件）
 */
@Composable
private fun MessageListArea(
    messages: List<EnhancedMessage>,
    sessionObject: SessionObject,
    projectService: ProjectService?,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    // 使用 snapshotFlow 监听消息变化，自动滚动
    LaunchedEffect(scrollState) {
        snapshotFlow { messages.size }
            .distinctUntilChanged()
            .collect { size ->
                if (size > 0) {
                    delay(100) // 等待UI更新
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
    }
    
    // 监听并保存滚动位置
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .collect { position ->
                sessionObject.scrollPosition = position.toFloat()
            }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        VerticallyScrollableContainer(
            scrollState = scrollState,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty()) {
                    EmptyStateView()
                } else {
                    messages.forEach { message ->
                        MessageItem(
                            message = message,
                            sessionObject = sessionObject,
                            projectService = projectService
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单条消息组件
 */
@Composable
private fun MessageItem(
    message: EnhancedMessage,
    sessionObject: SessionObject,
    projectService: ProjectService?
) {
    when (message.role) {
        MessageRole.USER -> {
            UnifiedInputArea(
                mode = InputAreaMode.DISPLAY,
                message = message,
                onContextClick = { uri ->
                    if (uri.startsWith("file://") && projectService != null) {
                        val path = uri.removePrefix("file://")
                        projectService.openFile(path)
                    }
                },
                sessionObject = sessionObject,
                modifier = Modifier.fillMaxWidth()
            )
        }
        MessageRole.ASSISTANT, MessageRole.SYSTEM, MessageRole.ERROR -> {
            AssistantMessageDisplay(
                message = message,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 空状态视图
 */
@Composable
private fun EmptyStateView() {
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
}

/**
 * 输入区域（独立组件）
 */
@Composable
private fun InputArea(
    sessionObject: SessionObject,
    isGenerating: Boolean,
    contexts: List<ContextReference>,
    selectedModel: AiModel,
    selectedPermissionMode: PermissionMode,
    skipPermissions: Boolean,
    inputResetTrigger: Any?,
    fileIndexService: FileIndexService?,
    projectService: ProjectService?,
    onSend: (String) -> Unit,
    onInterruptAndSend: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .wrapContentHeight()
            .padding(16.dp)
    ) {
        // 生成状态显示
        if (isGenerating) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "Generating",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                com.claudecodeplus.ui.jewel.components.tools.JumpingDots()
            }
        }
        
        // 输入组件
        UnifiedChatInput(
            contexts = contexts,
            onContextAdd = { sessionObject.addContext(it) },
            onContextRemove = { sessionObject.removeContext(it) },
            selectedModel = selectedModel,
            onModelChange = { sessionObject.selectedModel = it },
            selectedPermissionMode = selectedPermissionMode,
            onPermissionModeChange = { sessionObject.selectedPermissionMode = it },
            skipPermissions = skipPermissions,
            onSkipPermissionsChange = { sessionObject.skipPermissions = it },
            fileIndexService = fileIndexService,
            projectService = projectService,
            resetTrigger = inputResetTrigger,
            sessionObject = sessionObject,
            onSend = onSend,
            onInterruptAndSend = onInterruptAndSend,
            enabled = true,
            isGenerating = isGenerating,
            modifier = Modifier.let {
                if (isGenerating) {
                    it.padding(top = 32.dp)
                } else {
                    it
                }
            }
        )
    }
}