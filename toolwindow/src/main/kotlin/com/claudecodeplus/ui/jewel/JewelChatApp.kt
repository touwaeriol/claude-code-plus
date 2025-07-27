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
    cliWrapper: ClaudeCliWrapper,
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
    var selectedPermissionMode by remember { mutableStateOf(PermissionMode.BYPASS_PERMISSIONS) }
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

/**
 * 发送消息的业务逻辑 - 核心消息处理函数
 * 
 * 这是整个聊天系统的核心函数，负责处理从用户输入到 AI 响应的完整流程。
 * 该函数实现了与 Claude CLI 的完整交互逻辑，包括消息构建、流式响应处理、
 * 工具调用管理、错误处理等。
 * 
 * 主要工作流程：
 * 1. 构建带上下文的消息（将上下文引用转换为 Markdown 格式）
 * 2. 创建并显示用户消息
 * 3. 创建空的助手消息（用于流式更新）
 * 4. 调用 Claude CLI 并处理响应流
 * 5. 解析不同类型的消息（文本、工具调用、结果等）
 * 6. 实时更新 UI 显示
 * 7. 处理特殊标记（如压缩完成标记）
 * 
 * 消息流处理类型：
 * - TEXT: 流式文本内容，累积并更新显示
 * - TOOL_USE: 工具调用开始，创建 ToolCall 对象
 * - TOOL_RESULT: 工具执行结果，更新对应的 ToolCall
 * - START: 会话开始，获取会话 ID
 * - ERROR: 错误处理，显示错误信息
 * - END: 流式传输结束，标记消息完成
 * 
 * 特殊功能：
 * - 压缩完成检测：检查响应中的特定标记，触发会话刷新
 * - 有序元素管理：保持内容和工具调用的时间顺序
 * - 状态同步：确保 UI 状态与消息处理状态一致
 * 
 * @param scope 协程作用域，用于启动异步任务
 * @param inputText 用户输入的原始文本
 * @param contexts 上下文引用列表（文件、网页等）
 * @param selectedModel 选中的 AI 模型
 * @param selectedPermissionMode 权限模式（绕过权限等）
 * @param skipPermissions 是否跳过权限检查
 * @param cliWrapper Claude CLI 包装器
 * @param workingDirectory 工作目录路径
 * @param currentSessionId 当前会话 ID（可能为 null）
 * @param currentMessages 当前消息列表
 * @param onMessageUpdate 消息更新回调
 * @param onContextsClear 清空上下文回调
 * @param onGeneratingChange AI 生成状态变化回调
 * @param onSessionIdUpdate 会话 ID 更新回调
 * @param onCompactCompleted 压缩完成回调
 * @return Job 协程任务，可用于取消操作
 */
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
            // 设置生成状态为 true，禁用输入框
            onGeneratingChange(true)
            
            /**
             * 构建最终消息
             * MessageBuilderUtils 会将上下文引用转换为 Markdown 格式，
             * 例如：
             * <context>
             * <file path="/path/to/file.kt">
             * 文件内容...
             * </file>
             * </context>
             * 
             * 用户消息内容
             */
            val messageWithContext = MessageBuilderUtils.buildFinalMessage(contexts, inputText)
            
            /**
             * 创建用户消息对象
             * 
             * 注意：消息内容使用原始输入文本，不包含上下文标记。
             * 上下文信息单独保存在 contexts 字段中，这样可以：
             * 1. 在 UI 上干净地显示用户输入
             * 2. 单独渲染上下文引用（如文件名、图标等）
             * 3. 方便后续处理和引用
             */
            val userMessage = EnhancedMessage(
                id = IdGenerator.generateMessageId(),
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
            
            /**
             * 创建空的助手消息
             * 
             * 这个消息将用于流式更新。初始状态为 STREAMING，
             * 随着 Claude CLI 返回的内容逐步填充消息内容。
             * isStreaming 标志会触发 UI 显示加载动画。
             */
            val assistantMessage = EnhancedMessage(
                id = IdGenerator.generateMessageId(),
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
            
            /**
             * 启动 Claude CLI 查询
             * 
             * 这里调用 ClaudeCliWrapper 的 query 方法，该方法会：
             * 1. 启动 claude 命令行进程
             * 2. 传递查询参数（模型、会话、工作目录等）
             * 3. 返回一个 Flow，用于接收流式响应
             * 
             * 参数说明：
             * - prompt: 包含上下文的完整消息（Markdown 格式）
             * - model: AI 模型的 CLI 名称（如 "claude-3-5-sonnet-20241022"）
             * - resume: 恢复会话 ID（新会话时为 null）
             * - cwd: 工作目录，AI 执行命令的基础路径
             * - permissionMode: 权限模式（如 "bypass-permissions"）
             * - skipPermissions: 是否跳过权限确认
             */
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
            
            /**
             * 响应处理的状态变量
             * 
             * - responseBuilder: 累积文本内容的字符串构建器
             * - toolCalls: 存储所有工具调用的列表
             * - orderedElements: 保持内容和工具调用时间顺序的元素列表
             *   这个列表用于在 UI 上按照实际发生顺序显示内容和工具调用
             */
            val responseBuilder = StringBuilder()
            val toolCalls = mutableListOf<ToolCall>()
            val orderedElements = mutableListOf<MessageTimelineItem>()
            
            /**
             * 收集并处理 Claude CLI 返回的消息流
             * 
             * 每个 sdkMessage 都有特定的类型，需要根据类型进行不同处理。
             * 这个循环会持续运行，直到收到 END 消息或发生错误。
             */
            messageFlow.collect { sdkMessage ->
                when (sdkMessage.type) {
                    /**
                     * 处理文本消息
                     * 
                     * TEXT 类型的消息是 AI 响应的主要内容，以流式方式传输。
                     * 每次接收到的可能只是几个字符或一个词，需要累积起来。
                     * 
                     * 处理流程：
                     * 1. 将新文本追加到 responseBuilder
                     * 2. 更新或添加内容元素到 orderedElements
                     * 3. 检查特殊标记（如压缩完成标记）
                     * 4. 更新助手消息并触发 UI 更新
                     */
                    com.claudecodeplus.sdk.MessageType.TEXT -> {
                        // 流式内容更新
                        sdkMessage.data.text?.let { text ->
                            responseBuilder.append(text)
                            
                            /**
                             * 管理有序元素列表
                             * 
                             * orderedElements 用于保持内容和工具调用的时间顺序。
                             * 这使得 UI 可以按照 AI 实际生成的顺序显示
                             * 文本段落和工具调用。
                             * 
                             * 例如：
                             * 1. 文本："让我看看这个文件..."
                             * 2. 工具调用：Read(file.txt)
                             * 3. 文本："根据文件内容..."
                             * 4. 工具调用：Write(new_file.txt)
                             * 
                             * 如果最后一个元素是内容元素，则更新它；
                             * 否则创建新的内容元素。
                             */
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
                            
                            /**
                             * 压缩完成检测
                             * 
                             * 当 AI 完成会话压缩时，会在响应中包含特定标记。
                             * 检测到该标记后，需要：
                             * 1. 给用户一定时间看到完成消息
                             * 2. 触发会话刷新，重新加载压缩后的会话
                             * 
                             * 这个机制确保用户能看到压缩操作的结果，
                             * 并自动更新到新的会话状态。
                             */
                            if (text.contains(Constants.Messages.COMPACT_COMPLETE_MARKER)) {
                                // 压缩完成，触发会话刷新
                                withContext(Dispatchers.Main) {
                                    // 延迟一下让用户看到完成消息
                                    delay(Constants.UI.COMPACT_DISPLAY_DELAY)
                                    
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
                    
                    /**
                     * 处理工具调用开始
                     * 
                     * TOOL_USE 消息表示 AI 决定调用某个工具（如文件操作、命令执行等）。
                     * 这时需要创建一个 ToolCall 对象来跟踪工具调用的状态。
                     * 
                     * ToolCall 包含：
                     * - name: 工具名称（如 Read、Write、Bash 等）
                     * - parameters: 工具参数（如文件路径、命令内容等）
                     * - status: 初始状态为 RUNNING
                     * 
                     * 工具调用会被添加到 orderedElements 中，
                     * 以保持与文本内容的时间顺序。
                     */
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
                    
                    /**
                     * 处理工具调用结果
                     * 
                     * TOOL_RESULT 消息包含工具执行的结果。需要找到对应的 ToolCall
                     * 并更新其状态和结果。
                     * 
                     * 结果类型：
                     * - 成功：更新状态为 SUCCESS，保存输出内容
                     * - 失败：更新状态为 FAILED，保存错误信息
                     * 
                     * 注意：工具结果可能很大（如读取大文件），
                     * UI 组件会根据结果大小决定是否折叠显示。
                     */
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
                    
                    /**
                     * 处理会话开始消息
                     * 
                     * START 消息通常在新会话开始时出现，
                     * 包含 Claude 分配的会话 ID。
                     * 
                     * 会话 ID 用于：
                     * - 恢复会话（resume 参数）
                     * - 关联消息历史
                     * - 持久化存储
                     */
                    com.claudecodeplus.sdk.MessageType.START -> {
                        // 会话开始，获取会话ID
                        sdkMessage.data.sessionId?.let { sessionId ->
                            onSessionIdUpdate(sessionId)
                        }
                    }
                    
                    /**
                     * 处理错误消息
                     * 
                     * ERROR 消息表示 Claude CLI 或 API 遇到错误。
                     * 常见错误原因：
                     * - API 配额耗尽
                     * - 网络连接问题
                     * - 请求超时
                     * - 模型过载
                     * - 输入内容违规
                     * 
                     * 处理方式：显示友好的错误消息，
                     * 并标记消息状态为 FAILED。
                     */
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
                    
                    /**
                     * 处理消息结束
                     * 
                     * END 消息表示 AI 响应完成，所有内容和工具调用都已完成。
                     * 这时需要：
                     * 1. 更新消息状态为 COMPLETE
                     * 2. 关闭流式传输标志
                     * 3. 最终化消息内容
                     * 
                     * END 后不会再有新的内容，
                     * 但在历史模式下可能还有工具结果。
                     */
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
            /**
             * 异常处理
             * 
             * 可能的异常原因：
             * - Claude CLI 进程启动失败
             * - 网络连接问题
             * - API 限制或配额问题
             * - 消息格式错误
             * - 文件访问权限问题
             * 
             * 处理方式：
             * 1. 记录异常信息（用于调试）
             * 2. 创建用户友好的错误消息
             * 3. 更新消息列表显示错误
             */
            // ERROR: ${e.message}
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
            /**
             * 清理工作
             * 
             * 无论成功还是失败，都需要：
             * - 恢复生成状态，重新启用输入框
             * - 这确保用户始终可以继续交互
             */
            onGeneratingChange(false)
        }
    }
}


 