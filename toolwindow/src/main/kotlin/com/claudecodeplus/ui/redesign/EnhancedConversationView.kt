package com.claudecodeplus.ui.redesign

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.redesign.components.*
import com.claudecodeplus.ui.services.ContextProvider
import com.claudecodeplus.ui.services.ProjectService
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation

/**
 * 增强的对话视图
 * 支持上下文引用、模型选择、工具调用可视化等高级功能
 */
@Composable
fun EnhancedConversationView(
    projectService: ProjectService,
    contextProvider: ContextProvider,
    cliWrapper: ClaudeCliWrapper,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<EnhancedMessage>() }
    var currentModel by remember { mutableStateOf(AiModel.OPUS) }
    val isLoading = remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    
    // 不再需要转换函数
    
    // 会话状态
    var sessionId by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.paneBackground)
    ) {
        // 顶部工具栏
        ConversationToolBar(
            onRefresh = {
                // 刷新会话
            },
            onNewChat = {
                messages.clear()
                sessionId = null
                messages.add(createWelcomeMessage())
            },
            onSettings = {
                projectService.showSettings("com.claudecodeplus.settings")
            }
        )
        
        // 控制栏（模型选择等）
        ConversationControlBar(
            currentModel = currentModel,
            onModelChange = { currentModel = it },
            onClearChat = {
                messages.clear()
                sessionId = null
            },
            onShowHistory = {
                // 显示历史记录
            }
        )
        
        // 消息列表
        VerticallyScrollableContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                messages.forEach { message ->
                    MessageItems(
                        message = message,
                        onRegenerate = { /* TODO: 重新生成 */ },
                        onEdit = { /* TODO: 编辑消息 */ },
                        onCopy = { /* TODO: 复制消息 */ }
                    )
                }
                
                if (isLoading.value) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "生成中...",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = JewelTheme.globalColors.text.disabled,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }
        }
        
        // 智能输入区域
        SmartInputArea(
            contextProvider = contextProvider,
            isEnabled = !isLoading.value,
            onSend = { text, contexts ->
                if (text.isNotBlank() && !isLoading.value) {
                    // 创建用户消息
                    val userMessage = EnhancedMessage(
                        role = MessageRole.USER,
                        content = text,
                        contexts = contexts,
                        model = currentModel
                    )
                    messages.add(userMessage)
                    
                    // 发送到 Claude
                    scope.launch {
                        isLoading.value = true
                        
                        try {
                            // 准备查询选项
                            val options = ClaudeCliWrapper.QueryOptions(
                                model = currentModel.cliName,
                                resume = sessionId
                            )
                            
                            // 构建完整的提示词（包含上下文）
                            val prompt = buildPromptWithContext(text, contexts)
                            
                            // 创建助手消息
                            val assistantMessage = EnhancedMessage(
                                role = MessageRole.ASSISTANT,
                                content = "",
                                model = currentModel,
                                isStreaming = true
                            )
                            messages.add(assistantMessage)
                            val messageIndex = messages.lastIndex
                            
                            // 收集响应
                            cliWrapper.query(prompt, options).collect { sdkMessage ->
                                when (sdkMessage.type) {
                                    com.claudecodeplus.sdk.MessageType.TEXT -> {
                                        sdkMessage.data.text?.let { content ->
                                            // 更新消息内容
                                            messages[messageIndex] = messages[messageIndex].copy(
                                                content = messages[messageIndex].content + content
                                            )
                                            
                                            // 自动滚动
                                            listState.animateScrollToItem(messages.lastIndex)
                                        }
                                    }
                                    com.claudecodeplus.sdk.MessageType.START -> {
                                        // 保存会话ID
                                        sessionId = sdkMessage.data.sessionId
                                    }
                                    com.claudecodeplus.sdk.MessageType.ERROR -> {
                                        messages[messageIndex] = messages[messageIndex].copy(
                                            isError = true
                                        )
                                        messages.add(
                                            EnhancedMessage(
                                                role = MessageRole.ERROR,
                                                content = "错误: ${sdkMessage.data.error}"
                                            )
                                        )
                                    }
                                    com.claudecodeplus.sdk.MessageType.END -> {
                                        messages[messageIndex] = messages[messageIndex].copy(
                                            isStreaming = false
                                        )
                                    }
                                    else -> {
                                        // 处理工具调用等其他消息类型
                                        handleToolMessage(sdkMessage, messages, messageIndex)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            messages.add(
                                EnhancedMessage(
                                    role = MessageRole.ERROR,
                                    content = "发生错误: ${e.message}"
                                )
                            )
                        } finally {
                            isLoading.value = false
                        }
                    }
                }
            }
        )
    }
}

/**
 * 创建欢迎消息
 */
private fun createWelcomeMessage() = EnhancedMessage(
    role = MessageRole.SYSTEM,
    content = """
        欢迎使用 Claude Code Plus！
        
        您可以：
        • 使用 @ 引用文件、符号或终端输出
        • 选择不同的 AI 模型
        • 直接运行命令并查看结果
        
        快捷键：
        • Cmd/Ctrl + Enter: 发送消息
        • Shift + Enter: 换行
        • Tab: 接受建议
    """.trimIndent()
)

/**
 * 构建包含上下文的提示词
 */
private suspend fun buildPromptWithContext(
    text: String,
    contexts: List<ContextReference>
): String {
    if (contexts.isEmpty()) return text
    
    val contextBuilder = StringBuilder()
    contextBuilder.appendLine("用户消息：$text")
    contextBuilder.appendLine("\n相关上下文：")
    
    contexts.forEach { context ->
        when (context) {
            is ContextReference.FileReference -> {
                contextBuilder.appendLine("\n文件：${context.path}")
                context.lines?.let {
                    contextBuilder.appendLine("行范围：${it.first}-${it.last}")
                }
            }
            is ContextReference.SymbolReference -> {
                contextBuilder.appendLine("\n符号：${context.name} (${context.type})")
                contextBuilder.appendLine("文件：${context.file}")
                contextBuilder.appendLine("行号：${context.line}")
            }
            is ContextReference.TerminalReference -> {
                contextBuilder.appendLine("\n终端输出：")
                contextBuilder.appendLine("最近 ${context.lines} 行")
                if (context.isError) {
                    contextBuilder.appendLine("错误输出")
                }
            }
            is ContextReference.ProblemsReference -> {
                contextBuilder.appendLine("\n问题列表")
                contextBuilder.appendLine("问题数：${context.problems.size}")
                context.severity?.let {
                    contextBuilder.appendLine("过滤级别：$it")
                }
            }
            is ContextReference.GitReference -> {
                contextBuilder.appendLine("\nGit ${context.type}")
            }
            is ContextReference.FolderReference -> {
                contextBuilder.appendLine("\n文件夹：${context.path}")
            }
            ContextReference.SelectionReference -> {
                contextBuilder.appendLine("\n编辑器选中内容")
            }
            ContextReference.WorkspaceReference -> {
                contextBuilder.appendLine("\n工作空间")
            }
        }
    }
    
    return contextBuilder.toString()
}

/**
 * 处理工具相关消息
 */
private fun handleToolMessage(
    sdkMessage: com.claudecodeplus.sdk.SDKMessage,
    messages: MutableList<EnhancedMessage>,
    messageIndex: Int
) {
    // TODO: 解析工具调用信息并更新消息
    // 这里需要根据 Claude CLI 的实际输出格式来解析工具调用
}