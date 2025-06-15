package com.claudecodeplus.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.ui.JBColor
import com.claudecodeplus.service.ClaudeCodeService
import com.claudecodeplus.util.ResponseLogger
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet
import kotlinx.coroutines.*
import java.io.File

/**
 * 支持 Markdown 渲染的聊天窗口
 */
class MarkdownChatWindow(
    private val project: Project,
    private val service: ClaudeCodeService
) {
    private val editorPane = JEditorPane()
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
    
    // Markdown 解析器配置
    private val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create()
    )
    
    private val parser = Parser.builder()
        .extensions(extensions)
        .build()
    
    private val htmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .softbreak("<br />")
        .build()
    
    data class ChatMessage(
        val sender: String,
        val content: String,
        val isMarkdown: Boolean = false
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
            }
            add(toolbar, BorderLayout.NORTH)
            
            // 配置 HTML 编辑器
            setupEditorPane()
            
            val scrollPane = JBScrollPane(editorPane).apply {
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
    
    private fun setupEditorPane() {
        editorPane.apply {
            contentType = "text/html"
            isEditable = false
            
            // 设置 HTML 渲染器
            val kit = HTMLEditorKit()
            editorKit = kit
            
            // 应用样式
            kit.styleSheet.addCustomStyles()
        }
    }
    
    private fun StyleSheet.addCustomStyles() {
        // 基础样式
        val bgColor = JBColor.background().toHex()
        val fgColor = JBColor.foreground().toHex()
        val codeBlockBg = JBColor(0xf5f5f5, 0x2d2d2d).toHex()
        val linkColor = JBColor(0x0066cc, 0x4080ff).toHex()
        
        addRule("body { font-family: system-ui, -apple-system, sans-serif; font-size: 14px; color: $fgColor; background-color: $bgColor; margin: 10px; line-height: 1.5; }")
        
        // 消息容器
        addRule(".message { margin-bottom: 15px; padding: 10px; border-radius: 8px; }")
        addRule(".message-user { background-color: ${JBColor(0xe8f5e9, 0x1b5e20).toHex()}; }")
        addRule(".message-assistant { background-color: ${JBColor(0xe3f2fd, 0x0d47a1).toHex()}; }")
        addRule(".message-error { background-color: ${JBColor(0xffebee, 0x5d1f1f).toHex()}; }")
        
        // 发送者标签
        addRule(".sender { font-weight: bold; margin-bottom: 5px; }")
        addRule(".sender-user { color: ${JBColor(0x2e7d32, 0x4caf50).toHex()}; }")
        addRule(".sender-assistant { color: ${JBColor(0x1565c0, 0x2196f3).toHex()}; }")
        addRule(".sender-error { color: ${JBColor.RED.toHex()}; }")
        
        // Markdown 样式
        addRule("h1 { font-size: 1.5em; font-weight: bold; margin: 10px 0; }")
        addRule("h2 { font-size: 1.3em; font-weight: bold; margin: 8px 0; }")
        addRule("h3 { font-size: 1.1em; font-weight: bold; margin: 6px 0; }")
        
        // 代码样式
        addRule("pre { background-color: $codeBlockBg; padding: 10px; border-radius: 4px; overflow-x: auto; margin: 10px 0; }")
        addRule("code { background-color: $codeBlockBg; padding: 2px 4px; border-radius: 3px; font-family: 'JetBrains Mono', 'Consolas', monospace; font-size: 13px; }")
        addRule("pre code { background-color: transparent; padding: 0; }")
        
        // 其他元素
        addRule("blockquote { border-left: 4px solid ${JBColor.GRAY.toHex()}; padding-left: 10px; margin: 10px 0; color: ${JBColor.GRAY.toHex()}; }")
        addRule("a { color: $linkColor; text-decoration: none; }")
        addRule("a:hover { text-decoration: underline; }")
        addRule("ul, ol { margin: 5px 0 5px 20px; }")
        addRule("li { margin: 2px 0; }")
        addRule("table { border-collapse: collapse; margin: 10px 0; }")
        addRule("th, td { border: 1px solid ${JBColor.GRAY.toHex()}; padding: 6px 12px; }")
        addRule("th { background-color: ${JBColor(0xf0f0f0, 0x3c3c3c).toHex()}; font-weight: bold; }")
        addRule("hr { border: none; border-top: 1px solid ${JBColor.GRAY.toHex()}; margin: 10px 0; }")
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
                
                service.sendMessageStream(message, forceNewSession, options).collect { chunk ->
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
                        "text" -> {
                            chunk.content?.let { content ->
                                if (isFirstChunk) {
                                    isFirstChunk = false
                                    // 开始新的助手消息
                                    messages.add(ChatMessage("Claude", "", true))
                                }
                                responseBuilder.append(content)
                                updateLastAssistantMessage(responseBuilder.toString())
                            }
                        }
                        "error" -> {
                            addErrorMessage(chunk.error ?: "Unknown error")
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
        messages.add(ChatMessage("You", content, false))
        updateDisplay()
    }
    
    private fun addAssistantMessage(content: String) {
        messages.add(ChatMessage("Claude", content, true))
        updateDisplay()
    }
    
    private fun addErrorMessage(content: String) {
        messages.add(ChatMessage("Error", content, false))
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
            messages[messages.size - 1] = ChatMessage("Claude", content, true)
            updateDisplay()
        }
    }
    
    private fun updateDisplay() {
        val html = buildHtml()
        SwingUtilities.invokeLater {
            editorPane.text = html
            // 滚动到底部
            editorPane.caretPosition = editorPane.document.length
        }
    }
    
    private fun buildHtml(): String {
        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body>
        """.trimIndent())
        
        for (message in messages) {
            val messageClass = when (message.sender) {
                "You" -> "message-user"
                "Claude" -> "message-assistant"
                "Error" -> "message-error"
                else -> ""
            }
            
            val senderClass = when (message.sender) {
                "You" -> "sender-user"
                "Claude" -> "sender-assistant"
                "Error" -> "sender-error"
                else -> ""
            }
            
            sb.append("""<div class="message $messageClass">""")
            sb.append("""<div class="sender $senderClass">${message.sender}:</div>""")
            
            val content = if (message.isMarkdown && containsMarkdown(message.content)) {
                markdownToHtml(message.content)
            } else {
                "<p>${message.content.escapeHtml()}</p>"
            }
            
            sb.append(content)
            sb.append("</div>")
        }
        
        sb.append("</body></html>")
        return sb.toString()
    }
    
    private fun markdownToHtml(markdown: String): String {
        val document = parser.parse(markdown)
        return htmlRenderer.render(document)
    }
    
    private fun containsMarkdown(text: String): Boolean {
        val patterns = listOf(
            "^#{1,6}\\s+", "```", "\\*\\*.*?\\*\\*", "__.*?__",
            "\\[.*?\\]\\(.*?\\)", "^\\s*[-*+]\\s+", "^\\s*\\d+\\.\\s+",
            "^>\\s+", "\\|.*?\\|", "^---+$", "~~.*?~~"
        )
        return patterns.any { text.contains(Regex(it, RegexOption.MULTILINE)) }
    }
    
    private fun clearMessages() {
        messages.clear()
        updateDisplay()
    }
    
    private fun startNewSession() {
        forceNewSession = true
        addSystemMessage("下一条消息将开始新的对话会话")
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
                editorPane,
                logInfo,
                "日志信息",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }
    
    fun dispose() {
        currentLogFile?.let { 
            ResponseLogger.closeSessionLog(it)
        }
        ResponseLogger.cleanOldLogs(50, project)
        scope.cancel()
        service.clearSession()
    }
}

// 扩展函数
private fun java.awt.Color.toHex(): String {
    return String.format("#%02x%02x%02x", red, green, blue)
}

private fun String.escapeHtml(): String {
    return this.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}