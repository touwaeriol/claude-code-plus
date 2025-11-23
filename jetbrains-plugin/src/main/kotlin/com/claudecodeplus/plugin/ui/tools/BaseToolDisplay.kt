package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.*
import com.claudecodeplus.server.tools.IdeTools
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 工具展示基类
 * 
 * 提供所有工具组件的公共功能
 */
abstract class BaseToolDisplay(
    protected val toolCall: ToolCallItem,
    protected val ideTools: IdeTools
) {
    
    /**
     * 创建工具展示组件
     */
    abstract fun create(): JComponent
    
    /**
     * 创建工具头部（工具名称 + 状态）
     */
    protected fun createToolHeader(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = EmptyBorder(JBUI.insets(8))
        panel.background = getHeaderBackground()
        
        // 左侧：图标 + 工具名称
        val leftPanel = JPanel()
        leftPanel.layout = BoxLayout(leftPanel, BoxLayout.X_AXIS)
        leftPanel.isOpaque = false
        
        val icon = JLabel(getToolIcon())
        icon.font = icon.font.deriveFont(16f)
        leftPanel.add(icon)
        leftPanel.add(Box.createHorizontalStrut(8))
        
        val nameLabel = JLabel(getToolDisplayName())
        nameLabel.font = nameLabel.font.deriveFont(java.awt.Font.BOLD)
        leftPanel.add(nameLabel)
        
        panel.add(leftPanel, BorderLayout.WEST)
        
        // 右侧：状态指示器
        val statusLabel = createStatusLabel()
        panel.add(statusLabel, BorderLayout.EAST)
        
        return panel
    }
    
    /**
     * 创建状态标签
     */
    protected fun createStatusLabel(): JLabel {
        val (text, color) = when (toolCall.status) {
            ToolCallStatus.RUNNING -> "运行中..." to Color(0x2196F3)
            ToolCallStatus.SUCCESS -> "✓ 成功" to Color(0x4CAF50)
            ToolCallStatus.FAILED -> "✗ 失败" to Color(0xF44336)
        }
        
        val label = JLabel(text)
        label.foreground = color
        label.font = label.font.deriveFont(12f)
        return label
    }
    
    /**
     * 获取工具图标（emoji）
     */
    protected open fun getToolIcon(): String {
        return when (toolCall.toolType) {
            ToolConstants.READ -> "📖"
            ToolConstants.WRITE -> "✍️"
            ToolConstants.EDIT -> "✏️"
            ToolConstants.MULTI_EDIT -> "📝"
            ToolConstants.BASH -> "⚡"
            ToolConstants.GREP -> "🔍"
            ToolConstants.GLOB -> "📁"
            ToolConstants.WEB_SEARCH -> "🌐"
            ToolConstants.WEB_FETCH -> "🌍"
            ToolConstants.TODO_WRITE -> "✅"
            else -> "🔧"
        }
    }
    
    /**
     * 获取工具显示名称
     */
    protected open fun getToolDisplayName(): String {
        return toolCall.toolType
    }
    
    /**
     * 获取头部背景色
     */
    protected fun getHeaderBackground(): Color {
        return when (toolCall.status) {
            ToolCallStatus.RUNNING -> Color(0xF5F5F5)
            ToolCallStatus.SUCCESS -> Color(0xE8F5E9)
            ToolCallStatus.FAILED -> Color(0xFFEBEE)
        }
    }
    
    /**
     * 创建参数展示面板
     */
    protected fun createInputPanel(inputs: List<Pair<String, String>>): JPanel? {
        if (inputs.isEmpty()) return null
        
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = EmptyBorder(JBUI.insets(8))
        panel.isOpaque = false
        
        for ((key, value) in inputs) {
            val row = JPanel(BorderLayout())
            row.isOpaque = false
            row.border = EmptyBorder(JBUI.insets(2, 0))
            
            val keyLabel = JLabel("$key:")
            keyLabel.font = keyLabel.font.deriveFont(java.awt.Font.BOLD, 11f)
            keyLabel.foreground = Color(0x666666)
            row.add(keyLabel, BorderLayout.WEST)
            
            val valueLabel = JLabel(value.take(100) + if (value.length > 100) "..." else "")
            valueLabel.font = valueLabel.font.deriveFont(11f)
            row.add(valueLabel, BorderLayout.CENTER)
            
            panel.add(row)
        }
        
        return panel
    }
    
    /**
     * 创建结果展示面板
     */
    protected fun createResultPanel(): JPanel? {
        val result = toolCall.result ?: return null
        
        val panel = JPanel(BorderLayout())
        panel.border = EmptyBorder(JBUI.insets(8))
        panel.background = when (result) {
            is ToolResult.Success -> Color(0xF1F8F4)
            is ToolResult.Error -> Color(0xFFF3F3)
        }
        
        when (result) {
            is ToolResult.Success -> {
                val textArea = JTextArea(result.output)
                textArea.isEditable = false
                textArea.lineWrap = true
                textArea.wrapStyleWord = true
                textArea.font = textArea.font.deriveFont(12f)
                textArea.background = panel.background
                
                val scrollPane = JScrollPane(textArea)
                scrollPane.border = BorderFactory.createEmptyBorder()
                scrollPane.preferredSize = java.awt.Dimension(600, Math.min(result.output.lines().size * 20 + 20, 200))
                
                panel.add(scrollPane, BorderLayout.CENTER)
            }
            is ToolResult.Error -> {
                val errorLabel = JLabel("<html><span style='color: #D32F2F;'>${escapeHtml(result.error)}</span></html>")
                errorLabel.font = errorLabel.font.deriveFont(12f)
                panel.add(errorLabel, BorderLayout.CENTER)
            }
        }
        
        return panel
    }
    
    /**
     * HTML 转义
     */
    protected fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}


