package com.claudecodeplus.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.ui.JBColor
import com.claudecodeplus.service.ClaudeCodeService
import com.claudecodeplus.util.ResponseLogger
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.awt.BorderLayout
import javax.swing.*
import kotlinx.coroutines.*
import java.io.File
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * 使用 IntelliJ Markdown 组件的聊天窗口
 * 
 * 注意：由于 JCEF 的内存使用，不建议创建太多实例
 */
class IntelliJMarkdownChatWindow(
    private val project: Project,
    private val service: ClaudeCodeService
) : Disposable {
    
    companion object {
        private val LOG = logger<IntelliJMarkdownChatWindow>()
        
        // CommonMark 解析器和渲染器
        private val parser = Parser.builder().build()
        private val renderer = HtmlRenderer.builder().build()
    }
    
    private val inputField = FileReferenceEditorField(project) { text ->
        if (text.trim().isNotEmpty() && isInitialized) {
            sendMessage(text.trim())
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isInitialized = false
    private var currentLogFile: File? = null
    private var forceNewSession = false
    private val messages = mutableListOf<ChatMessage>()
    private var isFirstMessage = true  // 标记是否是第一条消息
    
    // 使用 MarkdownJCEFHtmlPanel
    private val markdownPanel: MarkdownJCEFHtmlPanel = MarkdownJCEFHtmlPanel(project, null).also {
        Disposer.register(this, it)
    }
    
    data class ChatMessage(
        val sender: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    fun createComponent(): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty()
            
            // 工具栏
            val toolbar = JToolBar().apply {
                isFloatable = false
                border = JBUI.Borders.empty(5)
                
                add(JButton("新会话").apply {
                    toolTipText = "开始新的对话会话"
                    addActionListener { startNewSession() }
                })
                
                addSeparator()
                
                add(JButton("清空").apply {
                    toolTipText = "清空聊天记录"
                    addActionListener { clearMessages() }
                })
                
                addSeparator()
                
                add(JButton("日志").apply {
                    toolTipText = "显示日志文件位置"
                    addActionListener { showLogInfo() }
                })
                
                addSeparator()
                
                add(JButton("导出").apply {
                    toolTipText = "导出对话为 Markdown"
                    addActionListener { exportConversation() }
                })
            }
            add(toolbar, BorderLayout.NORTH)
            
            // Markdown 显示区域
            val scrollPane = JBScrollPane(markdownPanel.component).apply {
                border = JBUI.Borders.empty()
            }
            add(scrollPane, BorderLayout.CENTER)
            
            // 输入区域
            val inputPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(10)
                
                inputField.apply {
                    font = font.deriveFont(14f)
                }
                add(inputField, BorderLayout.CENTER)
                
                val sendButton = JButton("Send").apply {
                    addActionListener { 
                        val text = inputField.text.trim()
                        if (text.isNotEmpty() && isInitialized) {
                            sendMessage(text)
                        }
                    }
                }
                add(sendButton, BorderLayout.EAST)
            }
            add(inputPanel, BorderLayout.SOUTH)
        }
        
        // 初始化会话
        initializeSession()
        
        return panel
    }
    
    
    private fun initializeSession() {
        scope.launch {
            try {
                currentLogFile = ResponseLogger.createSessionLog(project = project)
                
                // 显示日志文件位置
                if (currentLogFile != null) {
                    val logMessage = "日志文件: ${currentLogFile?.absolutePath}"
                    addSystemMessage(logMessage)
                }
                
                // 添加欢迎消息到消息列表
                messages.add(ChatMessage("System", """
                    # 欢迎使用 Claude Code Plus
                    
                    这是一个支持 **Markdown** 渲染的聊天界面。
                    
                    ## 功能特点
                    - 支持标准 Markdown 语法
                    - 代码高亮显示
                    - 表格渲染
                    - 文件引用（输入 `@` 触发）
                    
                    ---
                    
                    正在连接到 Claude SDK 服务器...
                """.trimIndent()))
                
                updateDisplay()
                
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
                    
                    isInitialized = true
                    inputField.isEnabled = true
                    
                    // 更新欢迎消息
                    if (messages.isNotEmpty() && messages.last().sender == "System") {
                        val lastMessage = messages.last()
                        val updatedContent = lastMessage.content.replace(
                            "正在连接到 Claude SDK 服务器...",
                            "✅ 已连接到 Claude SDK 服务器，可以开始对话了！"
                        )
                        messages[messages.size - 1] = ChatMessage("System", updatedContent, lastMessage.timestamp)
                        updateDisplay()
                    }
                    
                    
                } else {
                    addErrorMessage("无法连接到 Claude SDK 服务器。请确保服务器已在端口 18080 上运行。")
                }
            } catch (e: Exception) {
                addErrorMessage("初始化失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun sendMessage(message: String) {
        inputField.text = ""
        addUserMessage(message)
        
        scope.launch {
            try {
                // 处理文件引用，构建增强消息
                val enhancedMessage = buildEnhancedMessage(message)
                
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
                
                // 记录请求（使用增强后的消息）
                currentLogFile?.let { logFile ->
                    ResponseLogger.logRequest(logFile, "MESSAGE", enhancedMessage, options)
                }
                
                var isFirstChunk = true
                
                // 第一条消息或强制新会话时使用新会话
                val useNewSession = isFirstMessage || forceNewSession
                if (isFirstMessage) {
                    isFirstMessage = false
                }
                
                LOG.info("Starting to collect message stream (newSession=$useNewSession)")
                service.sendMessageStream(enhancedMessage, useNewSession, options).collect { chunk ->
                    LOG.info("Received chunk: type=${chunk.type}, content length=${chunk.content?.length}, error=${chunk.error}")
                    
                    // 记录响应块
                    currentLogFile?.let { logFile ->
                        ResponseLogger.logResponseChunk(
                            logFile,
                            chunk.type,
                            chunk.content,
                            chunk.error,
                            mapOf(
                                "session_id" to (chunk.session_id ?: ""),
                                "message_type" to (chunk.message_type ?: "")
                            )
                        )
                    }
                    
                    if (forceNewSession) {
                        forceNewSession = false
                    }
                    
                    when (chunk.type) {
                        "text", "message" -> {
                            chunk.content?.let { content ->
                                if (isFirstChunk) {
                                    isFirstChunk = false
                                    // 开始新的助手消息
                                    messages.add(ChatMessage("Claude", "", System.currentTimeMillis()))
                                }
                                responseBuilder.append(content)
                                updateLastAssistantMessage(responseBuilder.toString())
                            }
                        }
                        "error" -> {
                            LOG.error("Error chunk received: ${chunk.error}")
                            addErrorMessage(chunk.error ?: "Unknown error")
                        }
                        "done" -> {
                            LOG.info("Stream completed")
                        }
                        else -> {
                            LOG.warn("Unknown chunk type: ${chunk.type}")
                        }
                    }
                }
                
                // 记录完整响应
                if (responseBuilder.isNotEmpty()) {
                    currentLogFile?.let { logFile ->
                        ResponseLogger.logFullResponse(
                            logFile,
                            responseBuilder.toString(),
                            true,
                            null
                        )
                    }
                }
                
            } catch (e: Exception) {
                currentLogFile?.let { logFile ->
                    ResponseLogger.logFullResponse(
                        logFile,
                        "Error: ${e.message}",
                        false,
                        e.stackTraceToString()
                    )
                }
                addErrorMessage("发送消息失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun addUserMessage(content: String) {
        LOG.info("Adding user message: $content")
        messages.add(ChatMessage("You", content))
        LOG.info("Total messages: ${messages.size}")
        updateDisplay()
    }
    
    private fun addAssistantMessage(content: String) {
        messages.add(ChatMessage("Claude", content))
        updateDisplay()
    }
    
    private fun addErrorMessage(content: String) {
        messages.add(ChatMessage("Error", content))
        updateDisplay()
    }
    
    private fun addSystemMessage(content: String) {
        // 系统消息只记录到日志，不显示在界面
        currentLogFile?.let { logFile ->
            ResponseLogger.logRawContent(logFile, "System", content)
        }
    }
    
    private fun updateLastAssistantMessage(content: String) {
        if (messages.isNotEmpty() && messages.last().sender == "Claude") {
            messages[messages.size - 1] = ChatMessage("Claude", content, messages.last().timestamp)
            updateDisplay()
        }
    }
    
    private fun updateDisplay() {
        val markdown = buildMarkdown()
        LOG.info("Built markdown (${markdown.length} chars): ${markdown.take(200)}...")
        updateMarkdownDisplay(markdown)
    }
    
    private fun buildMarkdown(): String {
        val sb = StringBuilder()
        
        for (message in messages) {
            when (message.sender) {
                "You" -> {
                    sb.append("### 👤 You\n\n")
                    // 处理用户消息中的文件引用，转换为超链接
                    val processedContent = processFileReferences(message.content)
                    sb.append(processedContent)
                    sb.append("\n\n")
                }
                "Claude" -> {
                    sb.append("### 🤖 Claude\n\n")
                    // Claude 的消息可能包含文件路径，也进行处理
                    val processedContent = processFilePathsInResponse(message.content)
                    sb.append(processedContent)
                    sb.append("\n\n")
                }
                "Error" -> {
                    sb.append("### ❌ Error\n\n")
                    sb.append("> ${message.content}\n\n")
                }
                "System" -> {
                    sb.append(message.content)
                    sb.append("\n\n")
                }
            }
            sb.append("---\n\n")
        }
        
        return sb.toString()
    }
    
    private fun processFileReferences(content: String): String {
        // 匹配 @文件路径 格式
        val pattern = "@([^\\s]+(?:\\.[^\\s]+)?)"
        val regex = Regex(pattern)
        
        return regex.replace(content) { matchResult ->
            val filePath = matchResult.groupValues[1]
            val resolvedPath = resolveFilePath(filePath)
            
            if (resolvedPath != null) {
                // 转换为 Markdown 链接格式
                "[@$filePath](file://${resolvedPath.replace(" ", "%20")})"
            } else {
                // 如果无法解析，保持原样但转义
                "@${filePath.escapeMarkdown()}"
            }
        }
    }
    
    private fun processFilePathsInResponse(content: String): String {
        // 识别常见的文件路径模式
        val patterns = listOf(
            // 相对路径：src/main/kotlin/Example.kt
            "(?:^|\\s)((?:src|test|build|docs)/[^\\s:]+\\.[^\\s:]+)",
            // 绝对路径：/Users/xxx/project/file.kt
            "(?:^|\\s)(/[^\\s:]+\\.[^\\s:]+)",
            // 文件名带行号：Example.kt:42
            "(?:^|\\s)([^/\\s]+\\.[^\\s:]+):(\\d+)"
        )
        
        var result = content
        for (pattern in patterns) {
            val regex = Regex(pattern)
            result = regex.replace(result) { matchResult ->
                val prefix = if (matchResult.value.startsWith(" ")) " " else ""
                val filePath = matchResult.groupValues[1]
                val lineNumber = if (matchResult.groupValues.size > 2) matchResult.groupValues[2] else null
                
                val resolvedPath = resolveFilePath(filePath)
                if (resolvedPath != null) {
                    val link = if (lineNumber != null) {
                        "$prefix[$filePath:$lineNumber](file://${resolvedPath.replace(" ", "%20")}:$lineNumber)"
                    } else {
                        "$prefix[$filePath](file://${resolvedPath.replace(" ", "%20")})"
                    }
                    link
                } else {
                    matchResult.value
                }
            }
        }
        
        return result
    }
    
    private fun resolveFilePath(filePath: String): String? {
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
    
    private fun buildEnhancedMessage(message: String): String {
        // 提取 @文件引用
        val pattern = "@([^\\s]+(?:\\.[^\\s]+)?)"
        val regex = Regex(pattern)
        val fileContents = mutableListOf<Pair<String, String>>()
        
        regex.findAll(message).forEach { matchResult ->
            val filePath = matchResult.groupValues[1]
            val resolvedPath = resolveFilePath(filePath)
            
            if (resolvedPath != null) {
                try {
                    val content = File(resolvedPath).readText()
                    fileContents.add(filePath to content)
                    LOG.info("Read file content for: $filePath")
                } catch (e: Exception) {
                    LOG.error("Failed to read file: $filePath", e)
                }
            }
        }
        
        // 如果没有文件引用，直接返回原消息
        if (fileContents.isEmpty()) {
            return message
        }
        
        // 构建增强消息
        val sb = StringBuilder(message)
        sb.append("\n\n")
        
        fileContents.forEach { (filePath, content) ->
            sb.append("\n<file path=\"$filePath\">\n")
            sb.append(content)
            sb.append("\n</file>\n")
        }
        
        return sb.toString()
    }
    
    private fun updateMarkdownDisplay(markdown: String) {
        LOG.info("Updating markdown display with ${markdown.length} chars")
        SwingUtilities.invokeLater {
            try {
                // 使用正确的 API 生成 HTML
                val htmlContent = convertMarkdownToHtml(markdown)
                LOG.info("Converted to HTML: ${htmlContent.take(200)}...")
                
                val updatedHtml = """
                    <html>
                    <head>
                        <style>
                            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                            pre { background-color: #f5f5f5; padding: 10px; border-radius: 5px; overflow-x: auto; }
                            code { background-color: #f5f5f5; padding: 2px 4px; border-radius: 3px; }
                        </style>
                    </head>
                    <body>
                        <div id="root">
                            $htmlContent
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                
                LOG.info("Setting HTML to panel")
                markdownPanel.setHtml(updatedHtml, 0)
                LOG.info("HTML set successfully")
            } catch (e: Exception) {
                LOG.error("更新 Markdown 显示失败", e)
                e.printStackTrace()
            }
        }
    }
    
    
    private fun clearMessages() {
        messages.clear()
        updateDisplay()
    }
    
    private fun startNewSession() {
        forceNewSession = true
        isFirstMessage = true  // 重置为第一条消息
        messages.clear()  // 清空消息历史
        // 重新添加欢迎消息
        messages.add(ChatMessage("System", """
            # 新会话已开始
            
            可以开始新的对话了！
        """.trimIndent()))
        updateDisplay()
    }
    
    private fun showLogInfo() {
        if (currentLogFile != null) {
            val logInfo = """
                === 日志文件信息 ===
                文件名: ${currentLogFile?.name}
                完整路径: ${currentLogFile?.absolutePath}
                文件存在: ${currentLogFile?.exists()}
                文件大小: ${currentLogFile?.length() ?: 0} 字节
            """.trimIndent()
            
            JOptionPane.showMessageDialog(
                markdownPanel.component,
                logInfo,
                "日志信息",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }
    
    private fun exportConversation() {
        val markdown = buildMarkdown()
        val descriptor = FileSaverDescriptor(
            "导出对话",
            "选择保存位置",
            "md"
        )
        
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val virtualFile = project.baseDir
        val result = dialog.save(virtualFile, "conversation_${System.currentTimeMillis()}.md")
        
        result?.let { wrapper ->
            try {
                val file = wrapper.file
                file.writeText(markdown)
                JOptionPane.showMessageDialog(
                    markdownPanel.component,
                    "对话已导出到：${file.absolutePath}",
                    "导出成功",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    markdownPanel.component,
                    "导出失败：${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    override fun dispose() {
        currentLogFile?.let { 
            ResponseLogger.closeSessionLog(it)
        }
        ResponseLogger.cleanOldLogs(50, project)
        scope.cancel()
        service.clearSession()
    }
    
    private fun convertMarkdownToHtml(markdown: String): String {
        return try {
            val document = parser.parse(markdown)
            renderer.render(document)
        } catch (e: Exception) {
            LOG.error("转换 Markdown 到 HTML 失败", e)
            // 失败时返回原始文本
            "<pre>${markdown.escapeHtml()}</pre>"
        }
    }
    
    private fun String.escapeHtml(): String {
        return this
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

// 扩展函数：转义用户输入的 Markdown 特殊字符
private fun String.escapeMarkdown(): String {
    return this
        .replace("\\", "\\\\")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("`", "\\`")
        .replace("#", "\\#")
        .replace("+", "\\+")
        .replace("-", "\\-")
        .replace(".", "\\.")
        .replace("!", "\\!")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")
}