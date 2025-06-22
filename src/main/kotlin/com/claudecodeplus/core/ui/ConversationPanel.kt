package com.claudecodeplus.core.ui

import com.claudecodeplus.core.interfaces.FileSearchService
import com.claudecodeplus.core.interfaces.ProjectService
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.ui.MessageRenderer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.awt.*
import javax.swing.*
import javax.swing.text.*

/**
 * 共享的对话面板组件
 * 可以被IDEA插件和独立测试应用使用
 */
class ConversationPanel(
    private val projectService: ProjectService,
    private val fileSearchService: FileSearchService,
    private val cliWrapper: ClaudeCliWrapper = ClaudeCliWrapper()
) : JPanel(BorderLayout()) {
    
    // UI组件
    private val conversationArea = JTextPane()
    private val conversationDocument = conversationArea.styledDocument
    private lateinit var inputPanel: InputPanel
    private var currentStreamJob: Job? = null
    private var currentSessionId: String? = null
    
    // 样式
    private val normalStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
    private val userStyle = conversationDocument.addStyle("user", normalStyle).apply {
        StyleConstants.setBold(this, true)
        StyleConstants.setForeground(this, Color(0, 102, 204))
    }
    private val aiStyle = conversationDocument.addStyle("ai", normalStyle).apply {
        StyleConstants.setForeground(this, Color(51, 51, 51))
    }
    private val toolStyle = conversationDocument.addStyle("tool", normalStyle).apply {
        StyleConstants.setForeground(this, Color(102, 102, 102))
        StyleConstants.setItalic(this, true)
    }
    private val errorStyle = conversationDocument.addStyle("error", normalStyle).apply {
        StyleConstants.setForeground(this, Color(204, 0, 0))
    }
    
    init {
        setupUI()
        showWelcomeMessage()
    }
    
    private fun setupUI() {
        // 创建分隔面板
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)
        splitPane.isOneTouchExpandable = true
        splitPane.dividerLocation = 450
        
        // 上部：对话区域
        splitPane.topComponent = createConversationPanel()
        
        // 下部：输入面板
        val newInputPanel = InputPanel(projectService, fileSearchService) { message ->
            handleSendMessage(message)
        }
        inputPanel = newInputPanel
        splitPane.bottomComponent = newInputPanel
        
        add(splitPane, BorderLayout.CENTER)
        
        // 添加工具栏
        add(createToolBar(), BorderLayout.NORTH)
    }
    
    private fun createConversationPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        conversationArea.apply {
            isEditable = false
            font = Font("Monospaced", Font.PLAIN, 13)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        
        val scrollPane = JScrollPane(conversationArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5),
                BorderFactory.createLineBorder(Color.GRAY)
            )
        }
        
        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }
    
    private fun createToolBar(): JToolBar {
        return JToolBar().apply {
            isFloatable = false
            
            add(JButton("Clear").apply {
                toolTipText = "Clear conversation"
                addActionListener {
                    clearConversation()
                }
            })
            
            add(JButton("New Session").apply {
                toolTipText = "Start a new conversation session"
                addActionListener {
                    currentSessionId = null
                    appendMessage("\n--- New Session Started ---\n\n", normalStyle)
                }
            })
            
            addSeparator()
            
            add(JButton("Stop").apply {
                toolTipText = "Stop current Claude response"
                addActionListener {
                    currentStreamJob?.cancel()
                    appendMessage("\n（已停止生成）\n\n", toolStyle)
                }
            })
            
            // 如果文件搜索服务有索引，显示索引状态
            fileSearchService.getIndexService()?.let { indexService ->
                addSeparator()
                
                val indexStatusLabel = JLabel("  Index: Loading...  ")
                add(indexStatusLabel)
                
                // 启动协程更新索引状态
                GlobalScope.launch {
                    while (!indexService.isIndexReady()) {
                        delay(500)
                        SwingUtilities.invokeLater {
                            indexStatusLabel.text = "  Index: Building...  "
                        }
                    }
                    SwingUtilities.invokeLater {
                        indexStatusLabel.text = "  Index: Ready ✓  "
                        indexStatusLabel.foreground = Color(0, 128, 0)
                    }
                }
                
                add(JButton("Refresh Index").apply {
                    toolTipText = "Rebuild file index"
                    addActionListener {
                        GlobalScope.launch {
                            indexStatusLabel.text = "  Index: Refreshing...  "
                            indexService.refreshIndex(null)
                            SwingUtilities.invokeLater {
                                indexStatusLabel.text = "  Index: Ready ✓  "
                                appendMessage("System: File index refreshed\n\n", toolStyle)
                            }
                        }
                    }
                })
            }
        }
    }
    
    private fun showWelcomeMessage() {
        appendMessage("欢迎使用 Claude Code Plus！\n\n", normalStyle)
        appendMessage("当前项目: ${projectService.getProjectName()}\n", normalStyle)
        appendMessage("项目路径: ${projectService.getProjectPath()}\n\n", normalStyle)
        appendMessage("您可以：\n", normalStyle)
        appendMessage("• 输入消息与 Claude 对话\n", normalStyle)
        appendMessage("• 使用 @ 引用项目中的文件\n", normalStyle)
        appendMessage("• 使用工具栏按钮管理对话\n\n", normalStyle)
        appendMessage("---\n\n", normalStyle)
    }
    
    private fun clearConversation() {
        conversationDocument.remove(0, conversationDocument.length)
        showWelcomeMessage()
    }
    
    private fun handleSendMessage(message: String) {
        // 处理文件引用
        val processedMessage = processFileReferences(message)
        
        // 显示用户消息
        appendMessage("You: ", userStyle)
        appendMessage("$processedMessage\n\n", normalStyle)
        
        // 添加AI响应占位符
        val aiResponseStart = conversationDocument.length
        appendMessage("Claude: ", aiStyle)
        val placeholderStart = conversationDocument.length
        appendMessage("_Generating..._", toolStyle)
        
        // 取消之前的任务
        currentStreamJob?.cancel()
        
        // 发送到Claude
        currentStreamJob = GlobalScope.launch {
            try {
                val responseBuilder = StringBuilder()
                var hasContent = false
                
                val options = ClaudeCliWrapper.QueryOptions(
                    model = "claude-opus-4-20250514",
                    cwd = projectService.getProjectPath(),
                    resume = currentSessionId
                )
                
                val stream = cliWrapper.query(message, options)
                stream.collect { sdkMessage ->
                    when (sdkMessage.type) {
                        MessageType.TEXT -> {
                            val text = sdkMessage.data.text ?: ""
                            responseBuilder.append(text)
                            hasContent = true
                        }
                        MessageType.ERROR -> {
                            val errorMsg = MessageRenderer.renderMessage(sdkMessage)
                            responseBuilder.append(errorMsg)
                            hasContent = true
                        }
                        MessageType.TOOL_USE -> {
                            val toolMsg = MessageRenderer.renderCompactToolCall(
                                sdkMessage.data.toolName ?: "未知工具",
                                sdkMessage.data.toolInput
                            )
                            responseBuilder.append("\n\n").append(toolMsg).append("\n\n")
                            hasContent = true
                        }
                        MessageType.START -> {
                            sdkMessage.data.sessionId?.let { sessionId ->
                                currentSessionId = sessionId
                            }
                        }
                        else -> {}
                    }
                    
                    // 实时更新内容
                    SwingUtilities.invokeLater {
                        updateAIResponse(placeholderStart, responseBuilder.toString())
                    }
                }
                
                // 完成后的处理
                SwingUtilities.invokeLater {
                    updateAIResponse(placeholderStart, responseBuilder.toString() + "\n\n", true)
                }
                
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    updateAIResponse(placeholderStart, "\n❌ 错误: ${e.message}\n\n", true)
                }
                e.printStackTrace()
            }
        }
    }
    
    private fun updateAIResponse(startPos: Int, response: String, isFinal: Boolean = false) {
        try {
            // 计算需要替换的长度
            val currentLength = conversationDocument.length - startPos
            
            // 移除占位符和现有内容
            if (currentLength > 0) {
                conversationDocument.remove(startPos, currentLength)
            }
            
            // 插入新内容
            conversationDocument.insertString(startPos, response, aiStyle)
            
            // 如果不是最终更新，添加光标
            if (!isFinal) {
                conversationDocument.insertString(conversationDocument.length, "▌", aiStyle)
            }
            
            // 滚动到底部
            conversationArea.caretPosition = conversationDocument.length
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun processFileReferences(input: String): String {
        val pattern = "@([^\\s]+)".toRegex()
        return pattern.replace(input) { matchResult ->
            val filePath = matchResult.groupValues[1]
            val relativePath = projectService.getRelativePath(filePath)
            "[@$relativePath](file://${projectService.getProjectPath()}/$filePath)"
        }
    }
    
    private fun appendMessage(text: String, style: AttributeSet) {
        try {
            conversationDocument.insertString(conversationDocument.length, text, style)
            conversationArea.caretPosition = conversationDocument.length
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // 公共方法
    fun requestInputFocus() {
        inputPanel.requestFocusInWindow()
    }
    
    fun getCurrentSessionId(): String? = currentSessionId
    
    fun setCurrentSessionId(sessionId: String?) {
        currentSessionId = sessionId
    }
}