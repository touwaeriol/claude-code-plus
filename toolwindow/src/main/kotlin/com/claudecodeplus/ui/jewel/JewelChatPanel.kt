package com.claudecodeplus.ui.jewel

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposePanel
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.core.interfaces.ProjectService
import com.claudecodeplus.ui.services.MessageProcessor
import com.claudecodeplus.ui.utils.MessageBuilderUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * Jewel 聊天面板 - Swing 包装器
 * 将 Compose UI 集成到 Swing 应用中
 * 基于 ClaudeCliWrapper 实现 AI 对话
 */
class JewelChatPanel(
    private val cliWrapper: ClaudeCliWrapper = ClaudeCliWrapper(),
    private val workingDirectory: String = System.getProperty("user.dir"),
    private val fileIndexService: FileIndexService? = null,
    private val projectService: ProjectService? = null,
    themeStyle: JewelThemeStyle = JewelThemeStyle.LIGHT,
    isSystemDark: Boolean = false,
    themeConfig: JewelThemeConfig = JewelThemeConfig.DEFAULT
) : JPanel(BorderLayout()) {
    
    var themeStyle: JewelThemeStyle = themeStyle
        set(value) {
            if (field != value) {
                field = value
                updateContent()
            }
        }
    
    private var isSystemDark: Boolean = isSystemDark
        set(value) {
            if (field != value && themeStyle == JewelThemeStyle.SYSTEM) {
                field = value
                updateContent()
            }
        }
    
    var themeConfig: JewelThemeConfig = themeConfig
        set(value) {
            if (field != value) {
                field = value
                updateContent()
            }
        }
    
    private val composePanel = ComposePanel()
    private val messageProcessor = MessageProcessor()
    
    init {
        add(composePanel, BorderLayout.CENTER)
        updateContent()
    }
    
    /**
     * 更新内容
     */
    fun updateContent() {
        composePanel.setContent {
            val actualTheme = JewelThemeStyle.getActualTheme(themeStyle, isSystemDark)
            
            val theme = when (actualTheme) {
                JewelThemeStyle.DARK, JewelThemeStyle.HIGH_CONTRAST_DARK -> {
                    JewelTheme.darkThemeDefinition()
                }
                JewelThemeStyle.LIGHT, JewelThemeStyle.HIGH_CONTRAST_LIGHT -> {
                    JewelTheme.lightThemeDefinition()
                }
                else -> JewelTheme.lightThemeDefinition() // 默认亮色
            }
            
            IntUiTheme(
                theme = theme,
                styling = ComponentStyling.provide()
            ) {
                ChatPanelContent()
            }
        }
    }
    
    @Composable
    private fun ChatPanelContent() {
        // 聊天状态
        val messages = remember { mutableStateOf(listOf<EnhancedMessage>()) }
        val contexts = remember { mutableStateOf(listOf<ContextReference>()) }
        val isGenerating = remember { mutableStateOf(false) }
        val currentSessionId = remember { mutableStateOf<String?>(null) }
        val currentJob = remember { mutableStateOf<Job?>(null) }
        val selectedModel = remember { mutableStateOf(AiModel.OPUS) }
        
        val scope = rememberCoroutineScope()
        
        // 初始欢迎消息
        LaunchedEffect(Unit) {
            messages.value = listOf(
                EnhancedMessage(
                    role = MessageRole.ASSISTANT,
                    content = "你好！我是Claude，很高兴为您提供代码和技术方面的帮助。您可以询问任何关于编程、代码审查、调试或技术问题。",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        
        JewelConversationView(
            messages = messages.value,
            onSend = { textWithMarkdown ->
                if (textWithMarkdown.isNotBlank() && !isGenerating.value) {
                    sendMessage(
                        scope = scope,
                        textWithMarkdown = textWithMarkdown,
                        contexts = contexts.value,
                        selectedModel = selectedModel.value,
                        cliWrapper = cliWrapper,
                        workingDirectory = workingDirectory,
                        currentSessionId = currentSessionId.value,
                        onMessageUpdate = { messages.value = it },
                        onContextsClear = { contexts.value = emptyList() },
                        onGeneratingChange = { isGenerating.value = it },
                        onSessionIdUpdate = { currentSessionId.value = it },
                        onJobUpdate = { currentJob.value = it },
                        currentMessages = messages.value  // 传入当前消息列表
                    )
                }
            },
            onStop = {
                currentJob.value?.cancel()
                isGenerating.value = false
            },
            contexts = contexts.value,
            onContextAdd = { context ->
                contexts.value = contexts.value + context
            },
            onContextRemove = { context ->
                contexts.value = contexts.value - context
            },
            isGenerating = isGenerating.value,
            selectedModel = selectedModel.value,
            onModelChange = { model ->
                // === JewelChatPanel.onModelChange CALLED ===
                // DEBUG: Current selectedModel.value = ${selectedModel.value.displayName}
                // DEBUG: New model parameter = ${model.displayName}
                // DEBUG: About to update selectedModel.value
                selectedModel.value = model
                // DEBUG: After update selectedModel.value = ${selectedModel.value.displayName}
                // === JewelChatPanel.onModelChange FINISHED ===
            },
            onClearChat = { 
                messages.value = listOf(
                    EnhancedMessage(
                        role = MessageRole.ASSISTANT,
                        content = "聊天记录已清空。有什么可以帮助您的吗？",
                        timestamp = System.currentTimeMillis()
                    )
                )
            },
            fileIndexService = fileIndexService,
            projectService = projectService
        )
    }
    
    /**
     * 发送消息的逻辑
     */
    private fun sendMessage(
        scope: CoroutineScope,
        textWithMarkdown: String,
        contexts: List<ContextReference>,
        selectedModel: AiModel,
        cliWrapper: ClaudeCliWrapper,
        workingDirectory: String,
        currentSessionId: String?,
        onMessageUpdate: (List<EnhancedMessage>) -> Unit,
        onContextsClear: () -> Unit,
        onGeneratingChange: (Boolean) -> Unit,
        onSessionIdUpdate: (String?) -> Unit,
        onJobUpdate: (Job?) -> Unit,
        currentMessages: List<EnhancedMessage> = emptyList()  // 添加当前消息列表参数
    ) {
        // 构建包含上下文的消息 - 使用新的Markdown格式
        val messageWithContext = MessageBuilderUtils.buildFinalMessage(contexts, textWithMarkdown)
        
        // 创建用户消息
        val userMessage = EnhancedMessage(
            role = MessageRole.USER,
            content = textWithMarkdown, // 使用原始输入文本，不包含上下文标记
            contexts = contexts,
            timestamp = System.currentTimeMillis()
        )
        
        val updatedMessages = currentMessages.toMutableList()
        updatedMessages.add(userMessage)
        onMessageUpdate(updatedMessages.toList())
        
        onContextsClear()
        onGeneratingChange(true)
        
        // 创建 AI 响应消息
        val assistantMessage = EnhancedMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            timestamp = System.currentTimeMillis(),
            isStreaming = true
        )
        
        updatedMessages.add(assistantMessage)
        onMessageUpdate(updatedMessages.toList())
        
        // 启动协程处理 AI 响应
        val job = scope.launch {
            try {
                // DEBUG: Sending message to Claude CLI: $messageWithContext
                // DEBUG: Working directory: $workingDirectory
                // DEBUG: Selected model: ${selectedModel.displayName} (CLI: ${selectedModel.cliName})
                
                // 调用 CLI  
                val options = ClaudeCliWrapper.QueryOptions(
                    model = selectedModel.cliName, // 使用选定的模型
                    cwd = workingDirectory,
                    resume = currentSessionId
                )
                
                val responseBuilder = StringBuilder()
                val toolCalls = mutableListOf<ToolCall>()
                
                // DEBUG: Starting to collect messages from Claude CLI...
                cliWrapper.query(messageWithContext, options).collect { sdkMessage ->
                    // DEBUG: Received message type: ${sdkMessage.type}
                    
                    // 使用 MessageProcessor 处理消息
                    val result = messageProcessor.processMessage(
                        sdkMessage = sdkMessage,
                        currentMessage = assistantMessage,
                        responseBuilder = responseBuilder,
                        toolCalls = toolCalls
                    )
                    
                    when (result) {
                        is MessageProcessor.ProcessResult.Updated -> {
                            updatedMessages[updatedMessages.lastIndex] = result.message
                            onMessageUpdate(updatedMessages.toList())
                        }
                        is MessageProcessor.ProcessResult.Complete -> {
                            updatedMessages[updatedMessages.lastIndex] = result.message
                            onMessageUpdate(updatedMessages.toList())
                        }
                        is MessageProcessor.ProcessResult.Error -> {
                            updatedMessages[updatedMessages.lastIndex] = result.message
                            onMessageUpdate(updatedMessages.toList())
                        }
                        is MessageProcessor.ProcessResult.SessionStart -> {
                            onSessionIdUpdate(result.sessionId)
                        }
                        MessageProcessor.ProcessResult.NoChange -> {
                            // 不需要更新
                        }
                    }
                }
            } catch (e: Exception) {
                // DEBUG: Error occurred: ${e.message}
                e.printStackTrace()
                val errorMessage = assistantMessage.copy(
                    content = "❌ 错误: ${e.message}",
                    status = MessageStatus.FAILED,
                    isError = true,
                    isStreaming = false
                )
                updatedMessages[updatedMessages.lastIndex] = errorMessage
                onMessageUpdate(updatedMessages.toList())
            } finally {
                // DEBUG: Finished processing message
                onGeneratingChange(false)
            }
        }
        
        onJobUpdate(job)
    }
    
    
    /**
     * 获取 CLI Wrapper
     */
    fun getCliWrapper(): ClaudeCliWrapper = cliWrapper
    
    
    /**
     * 设置系统主题（仅在 themeStyle 为 SYSTEM 时生效）
     */
    fun setSystemTheme(isDark: Boolean) {
        isSystemDark = isDark
    }
    
    
    /**
     * 获取实际使用的主题
     */
    fun getActualTheme(): JewelThemeStyle = JewelThemeStyle.getActualTheme(themeStyle, isSystemDark)
    
    
}