package com.claudecodeplus.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.ui.JBColor
import com.claudecodeplus.service.ClaudeCodeService
import com.claudecodeplus.util.ResponseLogger
import java.awt.BorderLayout
import javax.swing.*
import kotlinx.coroutines.*
import java.io.File
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet
import java.awt.Font

/**
 * 使用 JEditorPane 的简单 Markdown 聊天窗口
 * 更稳定可靠的实现
 */
class SimpleMarkdownChatWindow(
    private val project: Project,
    private val service: ClaudeCodeService
) : Disposable {
    
    companion object {
        private val LOG = logger<SimpleMarkdownChatWindow>()
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
    private var isFirstMessage = true
    
    // 使用 JEditorPane 显示 HTML
    private val displayPane = JEditorPane().apply {
        isEditable = false
        contentType = "text/html"
        
        // 设置自定义样式
        val kit = HTMLEditorKit()
        editorKit = kit
        
        val styleSheet = kit.styleSheet
        styleSheet.addRule("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 10px; }")
        styleSheet.addRule("h3 { color: #333; margin-top: 15px; }")
        styleSheet.addRule("pre { background-color: #f5f5f5; padding: 10px; border-radius: 5px; overflow-x: auto; }")
        styleSheet.addRule("code { background-color: #f5f5f5; padding: 2px 4px; border-radius: 3px; font-family: monospace; }")
        styleSheet.addRule("a { color: #0066cc; text-decoration: none; }")
        styleSheet.addRule("a:hover { text-decoration: underline; }")
        styleSheet.addRule("blockquote { border-left: 4px solid #ddd; margin-left: 0; padding-left: 15px; color: #666; }")
        styleSheet.addRule("hr { border: none; border-top: 1px solid #e0e0e0; margin: 20px 0; }")
        
        // 处理超链接点击
        addHyperlinkListener { e ->
            if (e.eventType == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                if (e.url?.protocol == "file") {
                    // 在 IDE 中打开文件
                    val path = e.url.path
                    val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path)
                    if (virtualFile != null) {
                        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(virtualFile, true)
                    }
                }
            }
        }
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
            
            // 显示区域
            val scrollPane = JBScrollPane(displayPane).apply {
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
                
                // 记录请求
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
        val html = buildHtml()
        LOG.info("Updating display with HTML (${html.length} chars)")
        SwingUtilities.invokeLater {
            displayPane.text = html
            // 滚动到底部
            displayPane.caretPosition = displayPane.document.length
        }
    }
    
    private fun buildHtml(): String {
        val sb = StringBuilder()
        sb.append("<html><body>")
        
        for (message in messages) {
            when (message.sender) {
                "You" -> {
                    sb.append("<h3>👤 You</h3>")
                    sb.append("<p>")
                    sb.append(processFileReferences(message.content).escapeHtml())
                    sb.append("</p>")
                }
                "Claude" -> {
                    sb.append("<h3>🤖 Claude</h3>")
                    sb.append(convertMarkdownToHtml(processFilePathsInResponse(message.content)))
                }
                "Error" -> {
                    sb.append("<h3>❌ Error</h3>")
                    sb.append("<blockquote>${message.content.escapeHtml()}</blockquote>")
                }
                "System" -> {
                    sb.append(convertMarkdownToHtml(message.content))
                }
            }
            sb.append("<hr>")
        }
        
        sb.append("</body></html>")
        return sb.toString()
    }
    
    // ... 其他方法与 IntelliJMarkdownChatWindow 相同 ...
    
    private fun clearMessages() {
        messages.clear()
        updateDisplay()
    }
    
    private fun startNewSession() {
        forceNewSession = true
        isFirstMessage = true
        messages.clear()
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
                displayPane,
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
                    displayPane,
                    "对话已导出到：${file.absolutePath}",
                    "导出成功",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    displayPane,
                    "导出失败：${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    private fun buildMarkdown(): String {
        val sb = StringBuilder()
        
        for (message in messages) {
            when (message.sender) {
                "You" -> {
                    sb.append("### 👤 You\n\n")
                    sb.append(message.content)
                    sb.append("\n\n")
                }
                "Claude" -> {
                    sb.append("### 🤖 Claude\n\n")
                    sb.append(message.content)
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
    
    // 复制相同的辅助方法
    private fun processFileReferences(content: String): String {
        val pattern = "@([^\\s]+(?:\\.[^\\s]+)?)"
        val regex = Regex(pattern)
        
        return regex.replace(content) { matchResult ->
            val filePath = matchResult.groupValues[1]
            val resolvedPath = resolveFilePath(filePath)
            
            if (resolvedPath != null) {
                "<a href=\"file://${resolvedPath.replace(" ", "%20")}\">@$filePath</a>"
            } else {
                "@$filePath"
            }
        }
    }
    
    private fun processFilePathsInResponse(content: String): String {
        // 简化处理，直接返回
        return content
    }
    
    private fun resolveFilePath(filePath: String): String? {
        if (filePath.startsWith("/")) {
            val file = File(filePath)
            return if (file.exists()) filePath else null
        }
        
        val projectPath = project.basePath ?: return null
        val file = File(projectPath, filePath)
        if (file.exists()) {
            return file.absolutePath
        }
        
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
        
        if (fileContents.isEmpty()) {
            return message
        }
        
        val sb = StringBuilder(message)
        sb.append("\n\n")
        
        fileContents.forEach { (filePath, content) ->
            sb.append("\n<file path=\"$filePath\">\n")
            sb.append(content)
            sb.append("\n</file>\n")
        }
        
        return sb.toString()
    }
    
    private fun convertMarkdownToHtml(markdown: String): String {
        // 简单的 Markdown 到 HTML 转换
        var html = markdown
            .escapeHtml()
            .replace(Regex("^# (.+)$", RegexOption.MULTILINE), "<h1>$1</h1>")
            .replace(Regex("^## (.+)$", RegexOption.MULTILINE), "<h2>$1</h2>")
            .replace(Regex("^### (.+)$", RegexOption.MULTILINE), "<h3>$1</h3>")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
            .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
            .replace(Regex("`(.+?)`"), "<code>$1</code>")
            .replace(Regex("^- (.+)$", RegexOption.MULTILINE), "<li>$1</li>")
            .replace(Regex("```\\w*\\n([^`]+)```", RegexOption.MULTILINE), "<pre><code>$1</code></pre>")
            .replace("\n\n", "</p><p>")
            .replace("\n", "<br>")
        
        return "<p>$html</p>"
    }
    
    override fun dispose() {
        currentLogFile?.let { 
            ResponseLogger.closeSessionLog(it)
        }
        ResponseLogger.cleanOldLogs(50, project)
        scope.cancel()
        service.clearSession()
    }
}

// 扩展函数
private fun String.escapeHtml(): String {
    return this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}