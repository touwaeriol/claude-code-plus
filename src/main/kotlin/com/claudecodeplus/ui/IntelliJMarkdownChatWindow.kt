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
import javax.swing.Timer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager

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
    
    // 查找工具窗口
    private fun findToolWindow(): ToolWindow? {
        return try {
            val toolWindowManager = ToolWindowManager.getInstance(project)
            toolWindowManager.getToolWindow("Claude Code Plus")
        } catch (e: Exception) {
            LOG.warn("Failed to find tool window", e)
            null
        }
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
    
    // 维护完整的 Markdown 内容
    private val markdownContent = StringBuilder()
    
    // 防抖机制 - 基于官方实现（降低防抖时间以提高响应速度）
    private val updateDebouncer = Timer(20) { 
        pendingMarkdown?.let { markdown ->
            if (!isUpdating) {
                doUpdateMarkdownDisplay(markdown)
            }
        }
    }
    private var pendingMarkdown: String? = null
    private var isUpdating = false
    
    // 使用 MarkdownJCEFHtmlPanel
    private val markdownPanel: MarkdownJCEFHtmlPanel = MarkdownJCEFHtmlPanel(project, null).also {
        Disposer.register(this, it)
        
        // 设置初始内容以确保 JCEF 初始化
        it.setHtml("<html><body><p>Loading...</p></body></html>", 0)
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
        
        // 延迟初始化，确保组件已完全加载
        SwingUtilities.invokeLater {
            initializeSession()
        }
        
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
                
                // 添加欢迎消息
                val welcomeMessage = """
                    # 欢迎使用 Claude Code Plus
                    
                    这是一个支持 **Markdown** 渲染的聊天界面。
                    
                    ## 功能特点
                    - 支持标准 Markdown 语法
                    - 代码高亮显示
                    - 表格渲染
                    - 文件引用（输入 `@` 触发）
                    
                    ---
                    
                    正在连接到 Claude SDK 服务器...
                """.trimIndent()
                
                messages.add(ChatMessage("System", welcomeMessage))
                appendToMarkdown("System", welcomeMessage)
                
                // 延迟更新以确保组件已就绪
                SwingUtilities.invokeLater {
                    updateMarkdownDisplay()
                }
                
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
                        
                        // 重新构建 Markdown 内容
                        rebuildMarkdownContent()
                        
                        // 延迟更新以确保组件已就绪
                        SwingUtilities.invokeLater {
                            updateMarkdownDisplay()
                        }
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
                                    // 添加分隔符和发送者标识到 Markdown
                                    if (markdownContent.isNotEmpty()) {
                                        markdownContent.append("\n\n---\n\n")
                                    }
                                    markdownContent.append("🤖 **Claude**\n\n")
                                }
                                responseBuilder.append(content)
                                // 直接更新 Markdown 内容，而不是通过 updateLastAssistantMessage
                                updateLastMessageInMarkdown(responseBuilder.toString())
                                // 更新消息列表
                                if (messages.isNotEmpty() && messages.last().sender == "Claude") {
                                    val lastMessage = messages.last()
                                    messages[messages.size - 1] = ChatMessage("Claude", responseBuilder.toString(), lastMessage.timestamp)
                                }
                                // 更新显示
                                updateMarkdownDisplay()
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
        
        // 追加到 Markdown 内容
        appendToMarkdown("You", content)
        
        // 立即更新显示，不使用防抖
        doUpdateMarkdownDisplay(markdownContent.toString())
    }
    
    private fun addAssistantMessage(content: String) {
        messages.add(ChatMessage("Claude", content))
        appendToMarkdown("Claude", content)
        updateMarkdownDisplay()
    }
    
    private fun addErrorMessage(content: String) {
        messages.add(ChatMessage("Error", content))
        appendToMarkdown("Error", content)
        updateMarkdownDisplay()
    }
    
    private fun addSystemMessage(content: String) {
        // 系统消息只记录到日志，不显示在界面
        currentLogFile?.let { logFile ->
            ResponseLogger.logRawContent(logFile, "System", content)
        }
    }
    
    private fun updateLastAssistantMessage(content: String) {
        if (messages.isNotEmpty() && messages.last().sender == "Claude") {
            val lastMessage = messages.last()
            messages[messages.size - 1] = ChatMessage("Claude", content, lastMessage.timestamp)
            
            // 更新 Markdown 内容中的最后一条消息
            updateLastMessageInMarkdown(content)
            
            // 更新显示
            if (markdownPanel.component.isShowing) {
                updateMarkdownDisplay()
            } else {
                // 如果组件不可见，延迟更新
                SwingUtilities.invokeLater {
                    updateMarkdownDisplay()
                }
            }
        }
    }
    
    private fun updateLastMessageIncremental(content: String) {
        LOG.info("Incremental update for last message")
        // 由于反射访问 cefBrowser 失败，直接使用完整更新
        // MarkdownJCEFHtmlPanel 内部会处理增量更新优化
        updateDisplay()
    }
    
    private fun updateDisplay() {
        val markdown = buildMarkdown()
        LOG.info("Built markdown (${markdown.length} chars): ${markdown.take(200)}...")
        
        // 使用防抖机制
        pendingMarkdown = markdown
        updateDebouncer.restart()
    }
    
    private fun updateMarkdownDisplay() {
        val markdown = markdownContent.toString()
        LOG.info("Updating markdown display (${markdown.length} chars)")
        
        // 使用防抖机制
        pendingMarkdown = markdown
        updateDebouncer.restart()
    }
    
    private fun appendToMarkdown(sender: String, content: String) {
        // 添加分隔符（如果不是第一条消息）
        if (markdownContent.isNotEmpty()) {
            markdownContent.append("\n\n---\n\n")
        }
        
        // 根据发送者添加不同的格式
        when (sender) {
            "You" -> {
                markdownContent.append("👤 **You**\n\n")
                val processedContent = processFileReferences(content)
                markdownContent.append(processedContent)
            }
            "Claude" -> {
                markdownContent.append("🤖 **Claude**\n\n")
                val processedContent = processFilePathsInResponse(content)
                markdownContent.append(processedContent)
            }
            "Error" -> {
                markdownContent.append("❌ **Error**\n\n")
                markdownContent.append("> $content")
            }
            "System" -> {
                markdownContent.append(content)
            }
        }
    }
    
    private fun updateLastMessageInMarkdown(newContent: String) {
        // 找到最后一个 Claude 消息的位置
        val lastClaudeIndex = markdownContent.lastIndexOf("🤖 **Claude**")
        if (lastClaudeIndex != -1) {
            // 找到消息内容的开始位置（跳过标题和换行）
            val contentStart = markdownContent.indexOf("\n\n", lastClaudeIndex) + 2
            
            // 找到下一个分隔符或结尾
            val nextSeparator = markdownContent.indexOf("\n\n---\n\n", contentStart)
            val contentEnd = if (nextSeparator != -1) nextSeparator else markdownContent.length
            
            // 替换内容
            val processedContent = processFilePathsInResponse(newContent)
            markdownContent.replace(contentStart, contentEnd, processedContent)
        }
    }
    
    private fun rebuildMarkdownContent() {
        markdownContent.clear()
        messages.forEachIndexed { index, message ->
            if (index > 0) {
                markdownContent.append("\n\n---\n\n")
            }
            appendMessageToMarkdown(message)
        }
    }
    
    private fun appendMessageToMarkdown(message: ChatMessage) {
        when (message.sender) {
            "You" -> {
                markdownContent.append("👤 **You**\n\n")
                val processedContent = processFileReferences(message.content)
                markdownContent.append(processedContent)
            }
            "Claude" -> {
                markdownContent.append("🤖 **Claude**\n\n")
                val processedContent = processFilePathsInResponse(message.content)
                markdownContent.append(processedContent)
            }
            "Error" -> {
                markdownContent.append("❌ **Error**\n\n")
                markdownContent.append("> ${message.content}")
            }
            "System" -> {
                markdownContent.append(message.content)
            }
        }
    }
    
    private fun buildMarkdown(): String {
        // 为了支持增量更新，生成更结构化的 HTML
        val sb = StringBuilder()
        
        for ((index, message) in messages.withIndex()) {
            // 为每条消息创建独立的容器
            sb.append("<div class=\"message message-${message.sender.lowercase()}\" data-index=\"$index\">\n")
            
            when (message.sender) {
                "You" -> {
                    sb.append("<div class=\"sender\">👤 You</div>\n")
                    sb.append("<div class=\"content\">\n")
                    val processedContent = processFileReferences(message.content)
                    sb.append(convertMarkdownToHtml(processedContent))
                    sb.append("\n</div>\n")
                }
                "Claude" -> {
                    sb.append("<div class=\"sender\">🤖 Claude</div>\n")
                    sb.append("<div class=\"content\">\n")
                    val processedContent = processFilePathsInResponse(message.content)
                    sb.append(convertMarkdownToHtml(processedContent))
                    sb.append("\n</div>\n")
                }
                "Error" -> {
                    sb.append("<div class=\"sender error\">❌ Error</div>\n")
                    sb.append("<div class=\"content\">\n")
                    sb.append("<blockquote>${message.content.escapeHtml()}</blockquote>")
                    sb.append("\n</div>\n")
                }
                "System" -> {
                    sb.append("<div class=\"content\">\n")
                    sb.append(convertMarkdownToHtml(message.content))
                    sb.append("\n</div>\n")
                }
            }
            
            sb.append("</div>\n")
            if (index < messages.size - 1) {
                sb.append("<hr>\n")
            }
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
    
    private fun doUpdateMarkdownDisplay(markdown: String) {
        if (isUpdating) {
            LOG.info("Already updating, skip this update")
            return
        }
        
        isUpdating = true
        LOG.info("Starting markdown display update with ${markdown.length} chars")
        
        // 确保在 EDT 线程执行
        if (SwingUtilities.isEventDispatchThread()) {
            // 如果组件还未完全初始化，延迟执行
            if (!markdownPanel.component.isShowing) {
                SwingUtilities.invokeLater {
                    performUpdate(markdown)
                }
            } else {
                performUpdate(markdown)
            }
        } else {
            SwingUtilities.invokeLater {
                performUpdate(markdown)
            }
        }
    }
    
    private fun performUpdate(markdown: String) {
        try {
            // 使用 CommonMark 将 Markdown 转换为 HTML
            val htmlContent = convertMarkdownToHtml(markdown)
            LOG.info("Converted to HTML: ${htmlContent.take(200)}...")
            
            val updatedHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { 
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                            padding: 10px;
                            line-height: 1.6;
                            color: #333;
                        }
                        pre { 
                            background-color: #f5f5f5; 
                            padding: 10px; 
                            border-radius: 5px; 
                            overflow-x: auto;
                        }
                        code { 
                            background-color: #f5f5f5; 
                            padding: 2px 4px; 
                            border-radius: 3px;
                            font-family: 'JetBrains Mono', monospace;
                        }
                        pre code {
                            background-color: transparent;
                            padding: 0;
                        }
                        hr {
                            border: none;
                            border-top: 1px solid #e1e4e8;
                            margin: 20px 0;
                        }
                        blockquote {
                            border-left: 4px solid #dfe2e5;
                            margin: 0;
                            padding-left: 16px;
                            color: #6a737d;
                        }
                        a {
                            color: #0366d6;
                            text-decoration: none;
                        }
                        a:hover {
                            text-decoration: underline;
                        }
                        h1, h2, h3, h4, h5, h6 {
                            margin-top: 24px;
                            margin-bottom: 16px;
                            font-weight: 600;
                            line-height: 1.25;
                        }
                        h1 { font-size: 2em; }
                        h2 { font-size: 1.5em; }
                        h3 { font-size: 1.25em; }
                        table {
                            border-collapse: collapse;
                            width: 100%;
                            margin: 16px 0;
                        }
                        table th,
                        table td {
                            padding: 6px 13px;
                            border: 1px solid #dfe2e5;
                        }
                        table tr {
                            background-color: #fff;
                            border-top: 1px solid #c6cbd1;
                        }
                        table tr:nth-child(2n) {
                            background-color: #f6f8fa;
                        }
                    </style>
                </head>
                <body><div data-md-root="true">$htmlContent</div></body>
                </html>
            """.trimIndent()
            
            LOG.info("Setting HTML to panel")
            
            // 使用同步更新确保内容显示
            if (SwingUtilities.isEventDispatchThread()) {
                markdownPanel.setHtml(updatedHtml, 0)
                refreshUI()
            } else {
                SwingUtilities.invokeAndWait {
                    markdownPanel.setHtml(updatedHtml, 0)
                    refreshUI()
                }
            }
            
            LOG.info("HTML set successfully, UI refreshed")
        } catch (e: Exception) {
            LOG.error("更新 Markdown 显示失败", e)
            e.printStackTrace()
        } finally {
            isUpdating = false
        }
    }
    
    private fun refreshUI() {
        // 获取包装容器
        var container = markdownPanel.component.parent
        
        // 向上查找 JBScrollPane 或其他容器
        while (container != null && container !is JBScrollPane) {
            container = container.parent
        }
        
        // 如果找到滚动面板，刷新它
        container?.let { scrollPane ->
            if (scrollPane.isShowing) {
                scrollPane.validate()
                scrollPane.repaint()
            }
        }
        
        // 刷新 JCEF 组件本身
        if (markdownPanel.component.isShowing) {
            markdownPanel.component.validate()
            markdownPanel.component.repaint()
        }
        
        // 获取工具窗口并刷新
        findToolWindow()?.let { toolWindow ->
            toolWindow.component.revalidate()
            toolWindow.component.repaint()
        }
    }
    
    
    private fun clearMessages() {
        messages.clear()
        markdownContent.clear()
        updateMarkdownDisplay()
    }
    
    private fun startNewSession() {
        forceNewSession = true
        isFirstMessage = true  // 重置为第一条消息
        messages.clear()  // 清空消息历史
        markdownContent.clear()  // 清空 Markdown 内容
        
        // 重新添加欢迎消息
        val newSessionMessage = """
            # 新会话已开始
            
            可以开始新的对话了！
        """.trimIndent()
        
        messages.add(ChatMessage("System", newSessionMessage))
        appendToMarkdown("System", newSessionMessage)
        updateMarkdownDisplay()
    }
    
    private fun showLogInfo() {
        val logFile = currentLogFile
        if (logFile != null) {
            val logInfo = """
                === 日志文件信息 ===
                文件名: ${logFile.name}
                完整路径: ${logFile.absolutePath}
                文件存在: ${logFile.exists()}
                文件大小: ${logFile.length()} 字节
            """.trimIndent()
            
            // 使用 SwingUtilities 确保在 EDT 线程上执行
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(
                    null, // 使用 null 作为父组件，避免组件未初始化的问题
                    logInfo,
                    "日志信息",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        } else {
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(
                    null,
                    "尚未创建日志文件",
                    "日志信息",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        }
    }
    
    private fun exportConversation() {
        val markdown = markdownContent.toString()
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
        // 停止防抖定时器
        updateDebouncer.stop()
        
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