package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.ui.markdown.CodeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 代码片段展示组件
 * 
 * 对应 frontend/src/components/tools/CodeSnippet.vue
 */
class CodeSnippetPanel(
    private val code: String,
    private val language: String? = null,
    private val startLine: Int = 1,
    private val project: Project? = null
) {
    
    private val codeHighlighter = CodeHighlighter(project)
    
    fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createLineBorder(Color(0xE0E0E0), 1)
        panel.background = Color(0x2B2B2B)
        
        // 创建代码区域
        val codeArea = createCodeArea()
        val scrollPane = JScrollPane(codeArea)
        scrollPane.border = BorderFactory.createEmptyBorder()
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 复制按钮
        val copyButton = JButton("复制")
        copyButton.addActionListener {
            copyToClipboard()
        }
        
        val buttonPanel = JPanel(BorderLayout())
        buttonPanel.background = Color(0xF5F5F5)
        buttonPanel.border = EmptyBorder(JBUI.insets(4))
        buttonPanel.add(copyButton, BorderLayout.EAST)
        
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createCodeArea(): JTextPane {
        val textPane = JTextPane()
        textPane.isEditable = false
        textPane.background = Color(0x2B2B2B)
        textPane.foreground = Color(0xE6E6E6)
        textPane.font = Font("JetBrains Mono", Font.PLAIN, 12)
        textPane.border = EmptyBorder(JBUI.insets(8))
        
        // 应用语法高亮
        if (language != null) {
            val segments = codeHighlighter.highlight(code, language)
            val doc = textPane.styledDocument
            
            for (segment in segments) {
                val attrs = javax.swing.text.SimpleAttributeSet()
                javax.swing.text.StyleConstants.setForeground(attrs, segment.color)
                javax.swing.text.StyleConstants.setFontFamily(attrs, "JetBrains Mono")
                javax.swing.text.StyleConstants.setFontSize(attrs, 12)
                
                when (segment.fontStyle) {
                    Font.BOLD -> javax.swing.text.StyleConstants.setBold(attrs, true)
                    Font.ITALIC -> javax.swing.text.StyleConstants.setItalic(attrs, true)
                }
                
                doc.insertString(doc.length, segment.text, attrs)
            }
        } else {
            textPane.text = code
        }
        
        return textPane
    }
    
    private fun copyToClipboard() {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val selection = java.awt.datatransfer.StringSelection(code)
        clipboard.setContents(selection, selection)
    }
}

