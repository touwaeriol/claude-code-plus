package com.claudecodeplus.ui

import com.claudecodeplus.service.ClaudeHttpClient
import com.claudecodeplus.service.ClaudeCodeService
import com.claudecodeplus.util.EnhancedAnsiParser
import com.claudecodeplus.util.ProjectPathDebugger
import com.claudecodeplus.util.ResponseLogger
import java.io.File
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import javax.swing.JTextPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.JToolBar
import com.intellij.ui.JBColor

/**
 * 简化版的聊天窗口，用于快速测试
 */
class SimpleChatWindow(
    private val project: Project,
    private val service: ClaudeCodeService
) {
    private val messageArea = JTextPane()
    private val inputField = FileReferenceEditorField(project) { text ->
        if (text.trim().isNotEmpty() && isInitialized) {
            sendMessage(text.trim())
        }
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isInitialized = false
    private var lastMessageStart = -1
    private var forceNewSession = false
    private var currentLogFile: File? = null
    
    fun createComponent(): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty()
            
            // 添加工具栏
            val toolbar = JToolBar().apply {
                isFloatable = false
                border = JBUI.Borders.empty(5)
                
                add(JButton("新会话").apply {
                    toolTipText = "开始新的对话会话"
                    addActionListener {
                        startNewSession()
                    }
                })
                
                addSeparator()
                
                add(JButton("清空").apply {
                    toolTipText = "清空聊天记录"
                    addActionListener {
                        clearMessages()
                    }
                })
            }
            add(toolbar, BorderLayout.NORTH)
            
            // 消息显示区域
            messageArea.apply {
                isEditable = false
                font = font.deriveFont(14f)
                // 设置边距
                border = JBUI.Borders.empty(10)
            }
            
            val scrollPane = JBScrollPane(messageArea).apply {
                border = JBUI.Borders.empty()
            }
            add(scrollPane, BorderLayout.CENTER)
            
            // 输入区域
            val inputPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(10)
                
                inputField.apply {
                    font = font.deriveFont(14f)
                    // Enter 键处理已经在 FileReferenceTextField 中实现
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
                // 创建新的会话日志
                try {
                    currentLogFile = ResponseLogger.createSessionLog(project = project)
                    appendMessage("System", "正在连接到 Claude SDK 服务器...")
                    appendMessage("System", "日志文件: ${currentLogFile?.absolutePath}")
                    appendMessage("System", "日志文件存在: ${currentLogFile?.exists()}")
                } catch (e: Exception) {
                    appendMessage("Error", "创建日志文件失败: ${e.message}")
                    e.printStackTrace()
                }
                
                // 检查服务器健康状态
                val isHealthy = service.checkServiceHealth()
                if (isHealthy) {
                    appendMessage("System", "成功连接到服务器！")
                    
                    // 使用项目路径初始化服务
                    val debugInfo = ProjectPathDebugger.debugProjectPath(project)
                    appendMessage("System", "=== 项目路径调试信息 ===")
                    debugInfo.lines().forEach { line ->
                        if (line.isNotBlank()) {
                            appendMessage("System", line)
                        }
                    }
                    
                    // 获取实际的工作目录
                    val projectPath = ProjectPathDebugger.getProjectWorkingDirectory(project) 
                        ?: project.basePath 
                        ?: System.getProperty("user.dir")
                    
                    appendMessage("System", "最终使用的工作目录: $projectPath")
                    
                    // 定义允许的所有工具
                    val allTools = listOf(
                        "Read", "Write", "Edit", "MultiEdit",
                        "Bash", "Grep", "Glob", "LS",
                        "WebSearch", "WebFetch",
                        "TodoRead", "TodoWrite",
                        "NotebookRead", "NotebookEdit",
                        "Task", "exit_plan_mode"
                    )
                    
                    val initialized = service.initializeWithConfig(
                        cwd = projectPath,
                        skipUpdateCheck = true,
                        systemPrompt = "You are a helpful assistant. The current working directory is: $projectPath",
                        allowedTools = allTools,
                        permissionMode = "default"
                    )
                    if (initialized) {
                        appendMessage("System", "服务已初始化，工作目录: $projectPath")
                    } else {
                        appendMessage("Error", "服务初始化失败，但仍可尝试使用")
                    }
                    
                    // 不需要创建新会话，服务器会自动管理默认会话
                    appendMessage("System", "连接成功，可以开始对话了！")
                    isInitialized = true
                    inputField.isEnabled = true
                } else {
                    appendMessage("Error", "无法连接到 Claude SDK 服务器。请确保服务器已在端口 18080 上运行。")
                }
                
            } catch (e: Exception) {
                appendMessage("Error", "初始化失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun sendMessage() {
        val message = inputField.text.trim()
        if (message.isEmpty() || !isInitialized) return
        
        sendMessage(message)
    }
    
    private fun sendMessage(message: String) {
        inputField.text = ""
        appendMessage("You", message)
        
        scope.launch {
            try {
                val responseBuilder = StringBuilder()
                var currentSender = "Claude"
                
                // 获取当前项目的工作目录
                val projectPath = project.basePath
                // 定义允许的所有工具
                val allTools = listOf(
                    "Read", "Write", "Edit", "MultiEdit",
                    "Bash", "Grep", "Glob", "LS",
                    "WebSearch", "WebFetch",
                    "TodoRead", "TodoWrite",
                    "NotebookRead", "NotebookEdit",
                    "Task", "exit_plan_mode"
                )
                
                val options = if (projectPath != null) {
                    appendMessage("System", "[DEBUG] 发送消息时使用工作目录: $projectPath")
                    mapOf(
                        "cwd" to projectPath,
                        "allowed_tools" to allTools
                    )
                } else {
                    appendMessage("System", "[DEBUG] 警告：项目路径为空")
                    mapOf("allowed_tools" to allTools)
                }
                
                // 记录请求
                currentLogFile?.let { logFile ->
                    ResponseLogger.logRequest(logFile, "MESSAGE", message, options)
                }
                
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
                    
                    // 重置 forceNewSession 标志
                    if (forceNewSession) {
                        forceNewSession = false
                    }
                    when (chunk.type) {
                        "text" -> {
                            chunk.content?.let { content ->
                                responseBuilder.append(content)
                                // 实时更新显示
                                SwingUtilities.invokeLater {
                                    updateLastMessage(currentSender, responseBuilder.toString())
                                }
                            }
                        }
                        "error" -> {
                            SwingUtilities.invokeLater {
                                appendMessage("Error", chunk.error ?: "Unknown error")
                            }
                        }
                        else -> {
                            // 处理其他类型的消息
                            if (chunk.content != null) {
                                SwingUtilities.invokeLater {
                                    appendMessage("System", "[${chunk.type}] ${chunk.content}")
                                }
                            }
                        }
                    }
                }
                
                // 确保最终消息已添加
                if (responseBuilder.isNotEmpty()) {
                    val fullResponse = responseBuilder.toString()
                    
                    // 记录完整响应到日志
                    currentLogFile?.let { logFile ->
                        ResponseLogger.logFullResponse(
                            logFile,
                            fullResponse,
                            true,
                            null
                        )
                    }
                    
                    SwingUtilities.invokeLater {
                        finalizeLastMessage(currentSender, fullResponse)
                    }
                }
                
            } catch (e: Exception) {
                // 记录错误到日志
                currentLogFile?.let { logFile ->
                    ResponseLogger.logFullResponse(
                        logFile,
                        "Error: ${e.message}",
                        false,
                        e.stackTraceToString()
                    )
                }
                
                SwingUtilities.invokeLater {
                    appendMessage("Error", "发送消息失败: ${e.message}")
                }
                e.printStackTrace()
            }
        }
    }
    
    private fun appendMessage(sender: String, message: String) {
        SwingUtilities.invokeLater {
            val doc = messageArea.styledDocument
            val senderStyle = javax.swing.text.SimpleAttributeSet().apply {
                javax.swing.text.StyleConstants.setBold(this, true)
                javax.swing.text.StyleConstants.setForeground(this, 
                    when(sender) {
                        "You" -> JBColor(java.awt.Color(0, 128, 0), java.awt.Color(0, 192, 0))
                        "Claude" -> JBColor(java.awt.Color(0, 0, 128), java.awt.Color(64, 128, 255))
                        "System" -> JBColor.GRAY
                        "Error" -> JBColor.RED
                        else -> JBColor.BLACK
                    }
                )
            }
            
            // 添加发送者
            doc.insertString(doc.length, "$sender: ", senderStyle)
            
            // 解析并添加带 ANSI 样式的消息
            val segments = EnhancedAnsiParser.parse(message)
            if (segments.isEmpty()) {
                // 如果没有 ANSI 编码，直接添加纯文本
                doc.insertString(doc.length, message, null)
            } else {
                // 添加每个样式段
                segments.forEach { segment ->
                    doc.insertString(doc.length, segment.text, segment.attributes)
                }
            }
            
            // 添加换行
            doc.insertString(doc.length, "\n\n", null)
            
            // 滚动到底部
            messageArea.caretPosition = doc.length
        }
    }
    
    private fun updateLastMessage(sender: String, message: String) {
        val doc = messageArea.styledDocument
        
        if (lastMessageStart == -1) {
            lastMessageStart = doc.length
            
            val senderStyle = javax.swing.text.SimpleAttributeSet().apply {
                javax.swing.text.StyleConstants.setBold(this, true)
                javax.swing.text.StyleConstants.setForeground(this, JBColor(java.awt.Color(0, 0, 128), java.awt.Color(64, 128, 255)))
            }
            
            doc.insertString(doc.length, "$sender: ", senderStyle)
        }
        
        val currentLength = doc.length
        val senderPrefixLength = "$sender: ".length
        
        if (currentLength > lastMessageStart + senderPrefixLength) {
            // 删除旧内容
            doc.remove(lastMessageStart + senderPrefixLength, currentLength - lastMessageStart - senderPrefixLength)
        }
        
        // 解析并添加新内容
        val segments = EnhancedAnsiParser.parse(message)
        if (segments.isEmpty()) {
            doc.insertString(doc.length, message, null)
        } else {
            segments.forEach { segment ->
                doc.insertString(doc.length, segment.text, segment.attributes)
            }
        }
        
        messageArea.caretPosition = doc.length
    }
    
    private fun finalizeLastMessage(sender: String, message: String) {
        if (lastMessageStart != -1) {
            updateLastMessage(sender, message)
            val doc = messageArea.styledDocument
            doc.insertString(doc.length, "\n\n", null)
            lastMessageStart = -1
        }
    }
    
    fun dispose() {
        // 关闭日志
        currentLogFile?.let { 
            ResponseLogger.closeSessionLog(it)
        }
        
        // 清理旧日志（保留最近50个）
        ResponseLogger.cleanOldLogs(50, project)
        
        scope.cancel()
        service.clearSession()
    }
    
    private fun startNewSession() {
        scope.launch {
            try {
                appendMessage("System", "正在创建新会话...")
                
                // 设置标志，下一条消息将使用新会话
                forceNewSession = true
                
                appendMessage("System", "下一条消息将开始新的对话会话")
            } catch (e: Exception) {
                appendMessage("Error", "设置新会话时出错: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun clearMessages() {
        SwingUtilities.invokeLater {
            val doc = messageArea.styledDocument
            doc.remove(0, doc.length)
            appendMessage("System", "聊天记录已清空")
        }
    }
}