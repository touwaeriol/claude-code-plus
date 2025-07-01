package com.claudecodeplus.ui.jewel

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposePanel
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.ui.models.*
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
        // 聊天状态 - 使用 remember 确保状态持久化
        val messages = remember { mutableStateOf(listOf<EnhancedMessage>()) }
        val inputText = remember { mutableStateOf("") }
        val contexts = remember { mutableStateOf(listOf<ContextReference>()) }
        val isGenerating = remember { mutableStateOf(false) }
        val currentSessionId = remember { mutableStateOf<String?>(null) }
        val currentJob = remember { mutableStateOf<Job?>(null) }
        val selectedModel = remember { mutableStateOf(AiModel.OPUS) }
        
        val scope = rememberCoroutineScope()
        
        // 添加调试输出
        println("ChatPanelContent: selectedModel = ${selectedModel.value.displayName}")
        
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
            inputText = inputText.value,
            onInputChange = { inputText.value = it },
            onSend = {
                if (inputText.value.isNotBlank() && !isGenerating.value) {
                    sendMessage(
                        scope = scope,
                        inputText = inputText.value,
                        contexts = contexts.value,
                        selectedModel = selectedModel.value,
                        cliWrapper = cliWrapper,
                        workingDirectory = workingDirectory,
                        currentSessionId = currentSessionId.value,
                        onMessageUpdate = { messages.value = it },
                        onInputClear = { inputText.value = "" },
                        onContextsClear = { contexts.value = emptyList() },
                        onGeneratingChange = { isGenerating.value = it },
                        onSessionIdUpdate = { currentSessionId.value = it },
                        onJobUpdate = { currentJob.value = it }
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
                println("=== JewelChatPanel.onModelChange CALLED ===")
                println("DEBUG: Current selectedModel.value = ${selectedModel.value.displayName}")
                println("DEBUG: New model parameter = ${model.displayName}")
                println("DEBUG: About to update selectedModel.value")
                selectedModel.value = model
                println("DEBUG: After update selectedModel.value = ${selectedModel.value.displayName}")
                println("=== JewelChatPanel.onModelChange FINISHED ===")
            },
            onClearChat = { 
                messages.value = listOf(
                    EnhancedMessage(
                        role = MessageRole.ASSISTANT,
                        content = "聊天记录已清空。有什么可以帮助您的吗？",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        )
    }
    
    /**
     * 发送消息的逻辑
     */
    private fun sendMessage(
        scope: CoroutineScope,
        inputText: String,
        contexts: List<ContextReference>,
        selectedModel: AiModel,
        cliWrapper: ClaudeCliWrapper,
        workingDirectory: String,
        currentSessionId: String?,
        onMessageUpdate: (List<EnhancedMessage>) -> Unit,
        onInputClear: () -> Unit,
        onContextsClear: () -> Unit,
        onGeneratingChange: (Boolean) -> Unit,
        onSessionIdUpdate: (String?) -> Unit,
        onJobUpdate: (Job?) -> Unit
    ) {
        // 构建包含上下文的消息
        val messageWithContext = buildMessageWithContext(inputText, contexts)
        
        // 创建用户消息
        val userMessage = EnhancedMessage(
            role = MessageRole.USER,
            content = inputText,
            contexts = contexts,
            timestamp = System.currentTimeMillis()
        )
        
        val currentMessages = mutableListOf<EnhancedMessage>()
        onMessageUpdate(currentMessages)
        currentMessages.add(userMessage)
        onMessageUpdate(currentMessages.toList())
        
        onInputClear()
        onContextsClear()
        onGeneratingChange(true)
        
        // 创建 AI 响应消息
        val assistantMessage = EnhancedMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            timestamp = System.currentTimeMillis(),
            isStreaming = true
        )
        
        currentMessages.add(assistantMessage)
        onMessageUpdate(currentMessages.toList())
        
        // 启动协程处理 AI 响应
        val job = scope.launch {
            try {
                println("DEBUG: Sending message to Claude CLI: $messageWithContext")
                println("DEBUG: Working directory: $workingDirectory")
                println("DEBUG: Selected model: ${selectedModel.displayName} (CLI: ${selectedModel.cliName})")
                
                // 调用 CLI  
                val options = ClaudeCliWrapper.QueryOptions(
                    model = selectedModel.cliName, // 使用选定的模型
                    cwd = workingDirectory,
                    resume = currentSessionId
                )
                
                val responseBuilder = StringBuilder()
                val toolCalls = mutableListOf<ToolCall>()
                
                println("DEBUG: Starting to collect messages from Claude CLI...")
                cliWrapper.query(messageWithContext, options).collect { sdkMessage ->
                    println("DEBUG: Received message type: ${sdkMessage.type}")
                    when (sdkMessage.type) {
                        MessageType.TEXT -> {
                            sdkMessage.data.text?.let { text ->
                                println("DEBUG: Received text: $text")
                                responseBuilder.append(text)
                                // 更新消息内容
                                val updatedMessage = assistantMessage.copy(
                                    content = responseBuilder.toString(),
                                    toolCalls = toolCalls.toList()
                                )
                                currentMessages[currentMessages.lastIndex] = updatedMessage
                                onMessageUpdate(currentMessages.toList())
                            }
                        }
                        
                        MessageType.TOOL_USE -> {
                            val toolCall = ToolCall(
                                name = sdkMessage.data.toolName ?: "unknown",
                                displayName = sdkMessage.data.toolName ?: "unknown",
                                parameters = sdkMessage.data.toolInput as? Map<String, Any> ?: emptyMap(),
                                status = ToolCallStatus.RUNNING
                            )
                            toolCalls.add(toolCall)
                            
                            // 更新消息显示工具调用
                            val updatedMessage = assistantMessage.copy(
                                content = responseBuilder.toString(),
                                toolCalls = toolCalls.toList()
                            )
                            currentMessages[currentMessages.lastIndex] = updatedMessage
                            onMessageUpdate(currentMessages.toList())
                        }
                        
                        MessageType.TOOL_RESULT -> {
                            // 更新工具调用结果
                            val lastToolCall = toolCalls.lastOrNull()
                            if (lastToolCall != null) {
                                val updatedToolCall = lastToolCall.copy(
                                    status = ToolCallStatus.SUCCESS,
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
                                
                                val updatedMessage = assistantMessage.copy(
                                    content = responseBuilder.toString(),
                                    toolCalls = toolCalls.toList()
                                )
                                currentMessages[currentMessages.lastIndex] = updatedMessage
                                onMessageUpdate(currentMessages.toList())
                            }
                        }
                        
                        MessageType.START -> {
                            sdkMessage.data.sessionId?.let { id ->
                                onSessionIdUpdate(id)
                            }
                        }
                        
                        MessageType.ERROR -> {
                            val errorMsg = sdkMessage.data.error ?: "Unknown error"
                            val errorMessage = assistantMessage.copy(
                                content = "❌ 错误: $errorMsg",
                                status = MessageStatus.FAILED,
                                isError = true,
                                isStreaming = false
                            )
                            currentMessages[currentMessages.lastIndex] = errorMessage
                            onMessageUpdate(currentMessages.toList())
                        }
                        
                        MessageType.END -> {
                            // 完成流式传输
                            val finalMessage = assistantMessage.copy(
                                content = responseBuilder.toString(),
                                toolCalls = toolCalls.toList(),
                                status = MessageStatus.COMPLETE,
                                isStreaming = false
                            )
                            currentMessages[currentMessages.lastIndex] = finalMessage
                            onMessageUpdate(currentMessages.toList())
                        }
                        
                        else -> {
                            // 忽略其他消息类型
                        }
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: Error occurred: ${e.message}")
                e.printStackTrace()
                val errorMessage = assistantMessage.copy(
                    content = "❌ 错误: ${e.message}",
                    status = MessageStatus.FAILED,
                    isError = true,
                    isStreaming = false
                )
                currentMessages[currentMessages.lastIndex] = errorMessage
                onMessageUpdate(currentMessages.toList())
            } finally {
                println("DEBUG: Finished processing message")
                onGeneratingChange(false)
            }
        }
        
        onJobUpdate(job)
    }
    
    /**
     * 构建包含上下文的消息
     */
    private fun buildMessageWithContext(
        message: String,
        contexts: List<ContextReference>
    ): String {
        if (contexts.isEmpty()) {
            return message
        }
        
        val contextStrings = contexts.map { context ->
            when (context) {
                            is ContextReference.FileReference -> {
                "文件: ${context.path}"
            }
            is ContextReference.WebReference -> {
                "网页: ${context.title ?: context.url}"
            }
                is ContextReference.FolderReference -> "文件夹: ${context.path}"
                is ContextReference.SymbolReference -> "符号: ${context.name} (${context.type})"
                is ContextReference.TerminalReference -> "终端输出 (最近 ${context.lines} 行)"
                is ContextReference.ProblemsReference -> {
                    val severity = context.severity?.name ?: "所有"
                    "问题 ($severity)"
                }
                is ContextReference.GitReference -> "Git ${context.type.name}"
                ContextReference.SelectionReference -> "选中的代码"
                ContextReference.WorkspaceReference -> "整个工作空间"
            }
        }
        
        return buildString {
            appendLine("上下文引用:")
            contextStrings.forEach { appendLine("- $it") }
            appendLine()
            append(message)
        }
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