package com.claudecodeplus.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * 快速对话框 - 轻量级 Claude 对话界面
 * 用于快速问答，不需要切换到主工具窗口
 */
class QuickChatDialog(
    project: Project,
    private val selectedCode: String? = null,
    private val fileName: String? = null
) : DialogWrapper(project) {
    
    private val messageArea = JBTextArea()
    private val inputField = JBTextArea(3, 50)
    private val responseArea = JBTextArea()
    
    init {
        title = "Quick Claude"
        setOKButtonText("发送")
        setCancelButtonText("关闭")
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(600, 400)
        
        // 顶部：显示选中的代码（如果有）
        if (selectedCode != null) {
            val codePanel = JPanel(BorderLayout())
            codePanel.border = JBUI.Borders.empty(5)
            
            val label = JLabel("选中代码: ${fileName ?: "未知文件"}")
            codePanel.add(label, BorderLayout.NORTH)
            
            val codeArea = JBTextArea(selectedCode)
            codeArea.isEditable = false
            codeArea.font = codeArea.font.deriveFont(12f)
            val codeScroll = JBScrollPane(codeArea)
            codeScroll.preferredSize = Dimension(580, 100)
            codePanel.add(codeScroll, BorderLayout.CENTER)
            
            panel.add(codePanel, BorderLayout.NORTH)
        }
        
        // 中部：对话显示区域
        val chatPanel = JPanel(BorderLayout())
        chatPanel.border = JBUI.Borders.empty(5)
        
        responseArea.isEditable = false
        responseArea.lineWrap = true
        responseArea.wrapStyleWord = true
        responseArea.text = "请输入您的问题..."
        
        val responseScroll = JBScrollPane(responseArea)
        chatPanel.add(responseScroll, BorderLayout.CENTER)
        
        panel.add(chatPanel, BorderLayout.CENTER)
        
        // 底部：输入区域
        val inputPanel = JPanel(BorderLayout())
        inputPanel.border = JBUI.Borders.empty(5)
        
        inputField.lineWrap = true
        inputField.wrapStyleWord = true
        inputField.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
            JBUI.Borders.empty(5)
        )
        
        val inputScroll = JBScrollPane(inputField)
        inputScroll.preferredSize = Dimension(580, 80)
        inputPanel.add(inputScroll, BorderLayout.CENTER)
        
        // 添加快捷操作按钮
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        
        val applyButton = JButton("应用建议")
        applyButton.isEnabled = false
        applyButton.addActionListener { applyChanges() }
        
        val convertButton = JButton("转为完整会话")
        convertButton.addActionListener { convertToFullSession() }
        
        buttonPanel.add(Box.createHorizontalGlue())
        buttonPanel.add(applyButton)
        buttonPanel.add(Box.createRigidArea(Dimension(10, 0)))
        buttonPanel.add(convertButton)
        
        inputPanel.add(buttonPanel, BorderLayout.SOUTH)
        panel.add(inputPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    override fun doOKAction() {
        // 发送消息到 Claude
        val message = inputField.text
        if (message.isNotBlank()) {
            sendMessage(message)
            inputField.text = ""
        }
    }
    
    private fun sendMessage(message: String) {
        // 这里集成 Claude SDK
        responseArea.text = "正在处理: $message\n\n"
        
        // TODO: 实际调用 Claude API
        // 模拟响应
        SwingUtilities.invokeLater {
            responseArea.append("Claude: 这是一个模拟响应。\n")
            responseArea.append("您的代码看起来不错，但可以考虑添加错误处理。")
        }
    }
    
    private fun applyChanges() {
        // 应用 Claude 的建议到代码
        // TODO: 实现代码应用逻辑
    }
    
    private fun convertToFullSession() {
        // 转换为完整的 Claude 会话
        // TODO: 将当前对话内容传递到主工具窗口
        close(OK_EXIT_CODE)
    }
}