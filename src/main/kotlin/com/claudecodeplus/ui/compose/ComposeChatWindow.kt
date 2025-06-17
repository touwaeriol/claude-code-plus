package com.claudecodeplus.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.claudecodeplus.service.ClaudeCodeService
import com.claudecodeplus.util.ResponseLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File

data class ChatMessage(
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ComposeChatViewModel(
    private val project: Project,
    private val service: ClaudeCodeService
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages
    
    private val _markdownContent = MutableStateFlow("")
    val markdownContent: StateFlow<String> = _markdownContent
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized
    
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending
    
    private var currentLogFile: File? = null
    private var forceNewSession = false
    private var isFirstMessage = true
    
    init {
        initializeSession()
    }
    
    private fun initializeSession() {
        scope.launch {
            try {
                currentLogFile = ResponseLogger.createSessionLog(project = project)
                
                // 添加欢迎消息
                val welcomeMessage = """
                    # 欢迎使用 Claude Code Plus (Compose 版本)
                    
                    这是一个使用 **Compose Desktop** 构建的聊天界面。
                    
                    ## 功能特点
                    - 原生 Compose UI 体验
                    - Jewel Markdown 渲染
                    - 代码高亮显示
                    - 文件引用（输入 `@` 触发）
                    
                    ---
                    
                    正在连接到 Claude SDK 服务器...
                """.trimIndent()
                
                addMessage(ChatMessage("System", welcomeMessage))
                
                // 检查服务器健康状态
                val isHealthy = service.checkServiceHealth()
                if (isHealthy) {
                    // 初始化服务
                    val projectPath = project.basePath ?: System.getProperty("user.dir")
                    val allTools = listOf(
                        "Read", "Write", "Edit", "MultiEdit",
                        "Bash", "Grep", "Glob", "LS",
                        "WebSearch", "WebFetch",
                        "TodoRead", "TodoWrite",
                        "NotebookRead", "NotebookEdit",
                        "Task", "exit_plan_mode"
                    )
                    
                    service.initializeWithConfig(
                        cwd = projectPath,
                        skipUpdateCheck = true,
                        systemPrompt = "You are a helpful assistant. The current working directory is: $projectPath",
                        allowedTools = allTools,
                        permissionMode = "default"
                    )
                    
                    _isInitialized.value = true
                    
                    // 更新欢迎消息
                    updateLastSystemMessage(
                        welcomeMessage.replace(
                            "正在连接到 Claude SDK 服务器...",
                            "✅ 已连接到 Claude SDK 服务器，可以开始对话了！"
                        )
                    )
                } else {
                    addMessage(ChatMessage("Error", "无法连接到 Claude SDK 服务器。请确保服务器已在端口 18080 上运行。"))
                }
            } catch (e: Exception) {
                addMessage(ChatMessage("Error", "初始化失败: ${e.message}"))
                e.printStackTrace()
            }
        }
    }
    
    fun sendMessage(message: String) {
        if (message.isBlank() || !_isInitialized.value || _isSending.value) return
        
        _isSending.value = true
        addMessage(ChatMessage("You", message))
        
        scope.launch {
            try {
                val responseBuilder = StringBuilder()
                val projectPath = project.basePath
                val allTools = listOf(
                    "Read", "Write", "Edit", "MultiEdit",
                    "Bash", "Grep", "Glob", "LS",
                    "WebSearch", "WebFetch",
                    "TodoRead", "TodoWrite",
                    "NotebookRead", "NotebookEdit",
                    "Task", "exit_plan_mode"
                )
                
                val options = mapOf(
                    "cwd" to (projectPath ?: System.getProperty("user.dir")),
                    "allowed_tools" to allTools
                )
                
                // 记录请求
                currentLogFile?.let { logFile ->
                    ResponseLogger.logRequest(logFile, "MESSAGE", message, options)
                }
                
                var isFirstChunk = true
                val useNewSession = isFirstMessage || forceNewSession
                if (isFirstMessage) {
                    isFirstMessage = false
                }
                
                service.sendMessageStream(message, useNewSession, options).collect { chunk ->
                    when (chunk.type) {
                        "text", "message" -> {
                            chunk.content?.let { content ->
                                if (isFirstChunk) {
                                    isFirstChunk = false
                                    // 开始新的助手消息
                                    addMessage(ChatMessage("Claude", ""))
                                }
                                responseBuilder.append(content)
                                updateLastAssistantMessage(responseBuilder.toString())
                            }
                        }
                        "error" -> {
                            addMessage(ChatMessage("Error", chunk.error ?: "Unknown error"))
                        }
                        "done" -> {
                            // 流完成
                        }
                    }
                }
                
                if (forceNewSession) {
                    forceNewSession = false
                }
                
            } catch (e: Exception) {
                addMessage(ChatMessage("Error", "发送消息失败: ${e.message}"))
                e.printStackTrace()
            } finally {
                _isSending.value = false
            }
        }
    }
    
    fun clearMessages() {
        _messages.value = emptyList()
        _markdownContent.value = ""
    }
    
    fun startNewSession() {
        forceNewSession = true
        isFirstMessage = true
        clearMessages()
        
        val newSessionMessage = """
            # 新会话已开始
            
            可以开始新的对话了！
        """.trimIndent()
        
        addMessage(ChatMessage("System", newSessionMessage))
    }
    
    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
        rebuildMarkdownContent()
    }
    
    private fun updateLastAssistantMessage(content: String) {
        val messages = _messages.value.toMutableList()
        if (messages.isNotEmpty() && messages.last().sender == "Claude") {
            messages[messages.size - 1] = messages.last().copy(content = content)
            _messages.value = messages
            rebuildMarkdownContent()
        }
    }
    
    private fun updateLastSystemMessage(newContent: String) {
        val messages = _messages.value.toMutableList()
        if (messages.isNotEmpty() && messages.last().sender == "System") {
            messages[messages.size - 1] = messages.last().copy(content = newContent)
            _messages.value = messages
            rebuildMarkdownContent()
        }
    }
    
    private fun rebuildMarkdownContent() {
        val sb = StringBuilder()
        
        _messages.value.forEachIndexed { index, message ->
            if (index > 0) {
                sb.append("\n\n---\n\n")
            }
            
            when (message.sender) {
                "You" -> {
                    sb.append("👤 **You**\n\n")
                    sb.append(message.content)
                }
                "Claude" -> {
                    sb.append("🤖 **Claude**\n\n")
                    sb.append(message.content)
                }
                "Error" -> {
                    sb.append("❌ **Error**\n\n")
                    sb.append("> ${message.content}")
                }
                "System" -> {
                    sb.append(message.content)
                }
            }
        }
        
        _markdownContent.value = sb.toString()
    }
    
    fun dispose() {
        currentLogFile?.let { 
            ResponseLogger.closeSessionLog(it)
        }
        service.clearSession()
    }
}

@Composable
fun ComposeChatWindow(
    project: Project,
    service: ClaudeCodeService,
    modifier: Modifier = Modifier
) {
    val viewModel = remember { ComposeChatViewModel(project, service) }
    val messages by viewModel.messages.collectAsState()
    val markdownContent by viewModel.markdownContent.collectAsState()
    val isInitialized by viewModel.isInitialized.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    
    // 监听消息变化自动滚动到底部
    LaunchedEffect(messages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispose()
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 工具栏
        ChatToolbar(
            onNewSession = { viewModel.startNewSession() },
            onClear = { viewModel.clearMessages() }
        )
        
        // 聊天内容区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(vertical = 16.dp)
            ) {
                if (markdownContent.isNotEmpty()) {
                    MarkdownView(
                        project = project,
                        markdown = markdownContent
                    )
                }
            }
        }
        
        // 输入区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && 
                            event.key == Key.Enter && 
                            !event.isShiftPressed) {
                            if (inputText.isNotBlank() && isInitialized && !isSending) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                            true
                        } else {
                            false
                        }
                    },
                placeholder = { Text("输入消息...") },
                enabled = isInitialized && !isSending,
                singleLine = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && isInitialized && !isSending) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = {
                    if (inputText.isNotBlank() && isInitialized && !isSending) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && isInitialized && !isSending
            ) {
                Text("发送")
            }
        }
    }
}

@Composable
private fun ChatToolbar(
    onNewSession: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onNewSession) {
            Text("新会话")
        }
        
        TextButton(onClick = onClear) {
            Text("清空")
        }
    }
}

@Composable
private fun MarkdownView(
    project: Project,
    markdown: String
) {
    val processedMarkdown = remember(markdown) {
        processFileReferences(project, markdown)
    }
    
    // 暂时使用简单的 Text 显示，后续添加 Markdown 渲染
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            text = processedMarkdown,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun processFileReferences(project: Project, content: String): String {
    // 处理 @文件引用 格式
    val pattern = "@([^\\s]+(?:\\.[^\\s]+)?)"
    val regex = Regex(pattern)
    
    return regex.replace(content) { matchResult ->
        val filePath = matchResult.groupValues[1]
        val resolvedPath = resolveFilePath(project, filePath)
        
        if (resolvedPath != null) {
            // 转换为 Markdown 链接格式
            "[@$filePath](file://${resolvedPath.replace(" ", "%20")})"
        } else {
            // 如果无法解析，保持原样
            "@$filePath"
        }
    }
}

private fun resolveFilePath(project: Project, filePath: String): String? {
    // 如果是绝对路径，直接返回
    if (filePath.startsWith("/")) {
        val file = File(filePath)
        return if (file.exists()) filePath else null
    }
    
    // 相对路径，基于项目根目录解析
    val projectPath = project.basePath ?: return null
    val file = File(projectPath, filePath)
    if (file.exists()) {
        return file.absolutePath
    }
    
    // 如果直接路径不存在，尝试在项目中搜索
    val scope = GlobalSearchScope.projectScope(project)
    val psiFiles = FilenameIndex.getFilesByName(project, File(filePath).name, scope)
    
    for (psiFile in psiFiles) {
        val virtualFile = psiFile.virtualFile
        if (virtualFile.path.endsWith(filePath)) {
            return virtualFile.path
        }
    }
    
    return null
}

private fun handleUrlClick(project: Project, url: String) {
    when {
        url.startsWith("file://") -> {
            // 处理文件链接
            val filePath = url.removePrefix("file://").replace("%20", " ")
            val colonIndex = filePath.lastIndexOf(':')
            
            val (path, lineNumber) = if (colonIndex != -1 && colonIndex < filePath.length - 1) {
                val possibleLineNumber = filePath.substring(colonIndex + 1).toIntOrNull()
                if (possibleLineNumber != null) {
                    filePath.substring(0, colonIndex) to possibleLineNumber
                } else {
                    filePath to null
                }
            } else {
                filePath to null
            }
            
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(path)
            if (virtualFile != null) {
                val descriptor = if (lineNumber != null) {
                    OpenFileDescriptor(project, virtualFile, lineNumber - 1, 0)
                } else {
                    OpenFileDescriptor(project, virtualFile)
                }
                FileEditorManager.getInstance(project).openEditor(descriptor, true)
            }
        }
        url.startsWith("http://") || url.startsWith("https://") -> {
            // 处理 Web 链接
            com.intellij.ide.BrowserUtil.browse(url)
        }
    }
}