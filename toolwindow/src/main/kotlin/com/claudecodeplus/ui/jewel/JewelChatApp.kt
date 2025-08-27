package com.claudecodeplus.ui.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.services.UnifiedSessionService
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.SessionHistoryService
import com.claudecodeplus.ui.services.MessageProcessor
import com.claudecodeplus.ui.services.SessionLoader
import com.claudecodeplus.ui.services.MessageConverter.toEnhancedMessage
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
import com.claudecodeplus.ui.utils.MessageBuilderUtils
import com.claudecodeplus.ui.utils.IdGenerator
import com.claudecodeplus.ui.utils.Constants
import com.claudecodeplus.ui.utils.DefaultConfigs

/**
 * Jewel 聊天应用主组件 - 完整的 AI 聊天界面
 * 
 * 这是一个独立的、完整的聊天 UI 组件，包含了与 Claude 交互所需的所有业务逻辑。
 * 可以直接在测试应用或独立场景中使用，无需额外的配置。
 * 
 * 主要功能：
 * - 完整的消息发送和接收流程
 * - 流式响应显示（实时显示 AI 生成的内容）
 * - 工具调用显示（文件操作、代码执行等）
 * - 上下文管理（文件、网页、图片等）
 * - 模型选择（Opus、Sonnet 等）
 * - 中断生成功能
 * - 历史会话加载
 * - 主题切换（可选）
 * 
 * 状态管理：
 * - messages: 消息列表
 * - contexts: 上下文引用列表
 * - isGenerating: AI 是否正在生成响应
 * - currentSessionId: 当前 Claude 会话 ID
 * - selectedModel: 选中的 AI 模型
 * 
 * @param cliWrapper Claude CLI 包装器实例
 * @param workingDirectory 工作目录（AI 执行命令的基础路径）
 * @param fileIndexService 文件索引服务（可选，用于文件搜索）
 * @param projectService 项目服务（可选，用于打开文件等操作）
 * @param themeProvider 主题提供器
 * @param modifier Compose 修饰符
 * @param showToolbar 是否显示顶部工具栏
 * @param onThemeChange 主题切换回调
 * @param onCompactCompleted 会话压缩完成回调
 */
@Composable
fun JewelChatApp(
    unifiedSessionService: UnifiedSessionService,
    workingDirectory: String,
    fileIndexService: com.claudecodeplus.ui.services.FileIndexService? = null,
    projectService: com.claudecodeplus.core.interfaces.ProjectService? = null,
    themeProvider: JewelThemeProvider = DefaultJewelThemeProvider(),
    modifier: Modifier = Modifier,
    showToolbar: Boolean = true,
    onThemeChange: ((JewelThemeStyle) -> Unit)? = null,
    onCompactCompleted: (() -> Unit)? = null  // 压缩完成回调
) {
    /**
     * 组件内部状态管理
     * 使用 Compose 的 remember 和 mutableStateOf 管理所有 UI 状态
     */
    // 消息列表 - 存储所有的聊天消息
    var messages by remember { mutableStateOf(listOf<EnhancedMessage>()) }
    var contexts by remember { mutableStateOf(listOf<ContextReference>()) }
    var isGenerating by remember { mutableStateOf(false) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var messageJob by remember { mutableStateOf<Job?>(null) }
    var selectedModel by remember { mutableStateOf(AiModel.OPUS) }
    var selectedPermissionMode by remember { mutableStateOf(PermissionMode.BYPASS) }
    // skipPermissions 默认为 true，不再可修改
    val skipPermissions = true
    
    val scope = rememberCoroutineScope()
    val sessionHistoryService = remember { SessionHistoryService() }
    val messageProcessor = remember { MessageProcessor() }
    val sessionLoader = remember { SessionLoader(sessionHistoryService, messageProcessor) }
    
    /**
     * 启动时加载历史会话
     * 使用 LaunchedEffect(Unit) 确保只在组件首次加载时执行一次
     * 采用流式加载方式，避免一次性加载大量数据
     */
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                // 获取最近的会话文件
                val sessionFile = sessionHistoryService.getLatestSessionFile(workingDirectory)
                if (sessionFile != null) {
                    println("找到历史会话文件: ${sessionFile.name}")
                    
                    // 使用流式加载，每条消息都经过与实时消息相同的处理流程
                    sessionLoader.loadSessionAsMessageFlow(sessionFile, maxMessages = DefaultConfigs.Session.MAX_MESSAGES)
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
        /**
         * 可选的工具栏区域
         * 
         * 工具栏显示条件：
         * 1. showToolbar 为 true
         * 2. onThemeChange 回调不为 null
         * 
         * 工具栏主要用于独立桌面应用，
         * 在 IntelliJ 插件模式下通常不显示。
         */
        if (showToolbar && onThemeChange != null) {
            TopToolbar(
                themeProvider = themeProvider,
                onThemeChange = onThemeChange
            )
            
            // 水平分隔线
            Divider(
                orientation = Orientation.Horizontal,
                modifier = Modifier.height(1.dp),
                color = JewelTheme.globalColors.borders.normal
            )
        }
        
        /**
         * 主要聊天界面区域
         * 
         * JewelConversationView 是核心的对话视图组件，包含：
         * - 消息列表显示
         * - 输入框和发送按钮
         * - 上下文选择器
         * - 模型选择器
         * - 停止生成按钮
         * 
         * 通过各种回调函数与父组件通信，
         * 实现状态更新和事件处理。
         */
        // 主要聊天界面
        JewelConversationView(
            messages = messages,
            onSend = { textWithMarkdown ->
                /**
                 * 发送消息前的验证
                 * 
                 * 检查条件：
                 * 1. 输入内容不为空
                 * 2. AI 不在生成中
                 * 
                 * 如果有正在进行的消息任务，
                 * 先取消它再启动新任务。
                 */
                if (textWithMarkdown.isNotBlank() && !isGenerating) {
                    messageJob?.cancel()
                    messageJob = sendMessage(
                        scope = scope,
                        inputText = textWithMarkdown,
                        contexts = contexts,
                        selectedModel = selectedModel,
                        selectedPermissionMode = selectedPermissionMode,
                        skipPermissions = skipPermissions,
                        unifiedSessionService = unifiedSessionService,
                        workingDirectory = workingDirectory,
                        currentSessionId = currentSessionId,
                        currentMessages = messages,
                        onMessageUpdate = { updatedMessages -> messages = updatedMessages },
                        onContextsClear = { contexts = emptyList() },
                        onGeneratingChange = { generating -> isGenerating = generating },
                        onSessionIdUpdate = { sessionId -> currentSessionId = sessionId },
                        onJobUpdate = { job -> messageJob = job }
                    )
                }
            },
            onStop = {
                /**
                 * 停止生成处理
                 * 
                 * 当用户点击停止按钮时：
                 * 1. 终止 Claude CLI 进程
                 * 2. 取消协程任务
                 * 3. 重置生成状态
                 * 
                 * cliWrapper.terminate() 会向 CLI 进程
                 * 发送终止信号，强制停止生成。
                 */
                // 立即终止 CLI wrapper 进程
                unifiedSessionService.terminate()
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
 * 顶部工具栏组件 - 可选的主题切换工具栏
 * 
 * 提供主题切换功能，支持：
 * - 亮色主题
 * - 暗色主题  
 * - 跟随系统主题
 * 
 * 工具栏只在 showToolbar 为 true 时显示，
 * 主要用于独立应用模式，插件模式下通常隐藏。
 * 
 * @param themeProvider 主题提供器，获取当前主题状态
 * @param onThemeChange 主题切换回调
 * @param modifier Compose 修饰符
 */
@Composable
fun TopToolbar(
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
 * 
 * 将主题枚举值转换为用户友好的中文名称。
 * 
 * @param theme 主题样式枚举
 * @return 中文主题名称
 */
private fun getThemeDisplayName(theme: JewelThemeStyle): String = when (theme) {
    JewelThemeStyle.LIGHT -> "亮色"
    JewelThemeStyle.DARK -> "暗色"
    JewelThemeStyle.SYSTEM -> "系统"
    JewelThemeStyle.HIGH_CONTRAST_LIGHT -> "高对比度亮色"
    JewelThemeStyle.HIGH_CONTRAST_DARK -> "高对比度暗色"
}

private fun sendMessage(
    scope: CoroutineScope,
    inputText: String,
    contexts: List<ContextReference>,
    selectedModel: AiModel,
    selectedPermissionMode: PermissionMode,
    skipPermissions: Boolean,
    unifiedSessionService: UnifiedSessionService,
    workingDirectory: String,
    currentSessionId: String?,
    currentMessages: List<EnhancedMessage>,
    onMessageUpdate: (List<EnhancedMessage>) -> Unit,
    onContextsClear: () -> Unit,
    onGeneratingChange: (Boolean) -> Unit,
    onSessionIdUpdate: (String?) -> Unit,
    onJobUpdate: (Job?) -> Unit = {}
): Job {
    return scope.launch {
        try {
            // 标记为正在生成
            onGeneratingChange(true)
            
            // 构建包含上下文的消息
            val messageWithContext = MessageBuilderUtils.buildFinalMessage(contexts, inputText)
            
            // 创建用户消息
            val userMessage = EnhancedMessage(
                id = IdGenerator.generateMessageId(),
                role = MessageRole.USER,
                content = messageWithContext,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.COMPLETE,
                contexts = contexts.toList()
            )
            
            // 立即更新消息列表（添加用户消息）
            val messagesWithUser = currentMessages + userMessage
            onMessageUpdate(messagesWithUser)
            
            // 清空上下文
            onContextsClear()
            
            // 创建助手消息（初始为空）
            val assistantMessage = EnhancedMessage(
                id = IdGenerator.generateMessageId(),
                role = MessageRole.ASSISTANT,
                content = "",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.STREAMING,
                isStreaming = true
            )
            
            // 添加助手消息到列表
            val messagesWithAssistant = messagesWithUser + assistantMessage
            onMessageUpdate(messagesWithAssistant)
            
            // 准备查询选项
            val options = ClaudeCliWrapper.QueryOptions(
                model = selectedModel.cliName,
                cwd = workingDirectory,
                permissionMode = selectedPermissionMode.cliName,
                skipPermissions = skipPermissions,
                resume = currentSessionId
            )
            
            // 发送请求并处理响应
            val responseBuilder = StringBuilder()
            val toolCalls = mutableListOf<ToolCall>()
            val orderedElements = mutableListOf<MessageTimelineItem>()
            
            // 使用事件驱动的方式执行查询和监听输出
            try {
                // 创建事件服务实例
                val processHandler = com.claudecodeplus.sdk.ClaudeProcessEventHandler()
                val cliWrapper = com.claudecodeplus.sdk.ClaudeCliWrapper()
                val historyLoader = com.claudecodeplus.sdk.SessionHistoryLoader()
                val eventService = com.claudecodeplus.sdk.ClaudeEventService(processHandler, cliWrapper, historyLoader)
                
                // 决定是新会话还是恢复会话
                val eventFlow = if (currentSessionId != null) {
                    // 恢复现有会话
                    eventService.resumeExistingSession(
                        sessionId = currentSessionId,
                        projectPath = workingDirectory,
                        prompt = messageWithContext,
                        options = options
                    )
                } else {
                    // 启动新会话
                    eventService.startNewSession(
                        projectPath = workingDirectory,
                        prompt = messageWithContext,
                        options = options
                    )
                }
                
                // 在IO线程中监听事件流，在主线程更新UI
                launch(Dispatchers.IO) {
                    try {
                        eventFlow.collect { event ->
                            when (event) {
                                is com.claudecodeplus.sdk.ClaudeEvent.MessageReceived -> {
                                    // 在主线程更新消息
                                    launch(Dispatchers.Main) {
                                        val enhancedMessage = event.message.toEnhancedMessage()
                                        println("[JewelChatApp] 收到增强消息: role=${enhancedMessage.role}, toolCalls=${enhancedMessage.toolCalls.size}")
                                        
                                        // 根据消息类型处理
                                        when (enhancedMessage.role) {
                                            MessageRole.ASSISTANT -> {
                                                // 检查是否有工具调用信息
                                                if (enhancedMessage.toolCalls.isNotEmpty()) {
                                                    println("[JewelChatApp] 🔧 检测到工具调用，直接添加消息")
                                                    // 如果有工具调用，直接添加这个消息，不要合并
                                                    onMessageUpdate(messagesWithAssistant + enhancedMessage)
                                                } else {
                                                    // 没有工具调用的普通助手消息，进行内容合并
                                                    val currentMessages = messagesWithAssistant.dropLast(1) // 移除空的助手消息
                                                    val updatedAssistantMessage = assistantMessage.copy(
                                                        content = enhancedMessage.content,
                                                        status = if (enhancedMessage.isStreaming) MessageStatus.STREAMING else MessageStatus.COMPLETE,
                                                        isStreaming = enhancedMessage.isStreaming,
                                                        // 保持原有的工具调用信息（如果有的话）
                                                        toolCalls = assistantMessage.toolCalls + enhancedMessage.toolCalls,
                                                        tokenUsage = enhancedMessage.tokenUsage ?: assistantMessage.tokenUsage
                                                    )
                                                    onMessageUpdate(currentMessages + updatedAssistantMessage)
                                                }
                                            }
                                            else -> {
                                                // 其他类型消息直接添加
                                                onMessageUpdate(messagesWithAssistant + enhancedMessage)
                                            }
                                        }
                                    }
                                }
                                is com.claudecodeplus.sdk.ClaudeEvent.ProcessError -> {
                                    launch(Dispatchers.Main) {
                                        val errorMessage = EnhancedMessage(
                                            id = IdGenerator.generateMessageId(),
                                            role = MessageRole.ASSISTANT,
                                            content = "❌ 错误: ${event.error}",
                                            timestamp = System.currentTimeMillis(),
                                            status = MessageStatus.FAILED,
                                            isError = true
                                        )
                                        onMessageUpdate(messagesWithAssistant + errorMessage)
                                    }
                                }
                                is com.claudecodeplus.sdk.ClaudeEvent.SessionComplete -> {
                                    launch(Dispatchers.Main) {
                                        // 会话完成，停止生成状态
                                        onGeneratingChange(false)
                                    }
                                }
                                else -> {
                                    // 其他事件类型的处理
                                    println("[JewelChatApp] 收到事件: $event")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        launch(Dispatchers.Main) {
                            val errorMessage = EnhancedMessage(
                                id = IdGenerator.generateMessageId(),
                                role = MessageRole.ASSISTANT,
                                content = "❌ 处理响应时出错: ${e.message}",
                                timestamp = System.currentTimeMillis(),
                                status = MessageStatus.FAILED,
                                isError = true
                            )
                            onMessageUpdate(messagesWithAssistant + errorMessage)
                            onGeneratingChange(false)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMessage = EnhancedMessage(
                    id = IdGenerator.generateMessageId(),
                    role = MessageRole.ASSISTANT,
                    content = "❌ 启动查询时出错: ${e.message}",
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.FAILED,
                    isError = true
                )
                onMessageUpdate(messagesWithAssistant + errorMessage)
                onGeneratingChange(false)
            }
        } catch (e: Exception) {
            // 异常处理
            e.printStackTrace()
            
            // 添加错误消息
            val errorMessage = EnhancedMessage(
                id = IdGenerator.generateMessageId(),
                role = MessageRole.ASSISTANT,
                content = "❌ 发送消息时出错: ${e.message}",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.FAILED,
                isError = true
            )
            
            val errorMessages = currentMessages + errorMessage
            onMessageUpdate(errorMessages)
        } finally {
            // 恢复生成状态
            onGeneratingChange(false)
        }
    }
}


 