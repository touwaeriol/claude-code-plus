package com.claudecodeplus.plugin.ui.input

import com.claudecodeplus.plugin.types.ContextReference
import com.claudecodeplus.plugin.types.ContextType
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 上下文标签面板（Top Toolbar）
 * 
 * 完全复刻 frontend/src/components/chat/ChatInput.vue 第29-69行的 top-toolbar
 * 
 * 样式特性：
 * - 12px字体
 * - 4px 8px内边距
 * - 20px高度
 * - 底部边框分隔
 */
class ContextTagPanel(
    private val contextManager: ContextManager
) {
    
    private val panel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 6))
    
    init {
        panel.isOpaque = false
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor(Color(0xE1E4E8), Color(0x3C3C3C))),  // 底部边框
            EmptyBorder(JBUI.insets(6, 12))  // 内边距 6px 12px
        )
        
        // 监听上下文变化
        contextManager.onContextsChanged { contexts ->
            updateContexts(contexts)
        }
    }
    
    fun create(): JComponent {
        return panel
    }
    
    private fun updateContexts(contexts: List<ContextReference>) {
        panel.removeAll()
        
        // 添加上下文按钮
        val addButton = createAddContextButton()
        panel.add(addButton)
        
        // 添加上下文标签
        for (context in contexts) {
            val tag = createContextTag(context)
            panel.add(tag)
        }
        
        panel.revalidate()
        panel.repaint()
    }
    
    private fun createAddContextButton(): JButton {
        val button = JButton("📎 添加上下文")
        button.font = button.font.deriveFont(12f)  // 12px字体
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(0xE1E4E8), Color(0x3C3C3C)), 1),
            EmptyBorder(JBUI.insets(4, 8))  // 4px 8px内边距
        )
        button.preferredSize = Dimension(button.preferredSize.width, 20)  // 20px高度
        button.isContentAreaFilled = false
        button.background = JBColor(Color.WHITE, Color(0x2B2B2B))
        button.foreground = JBColor(Color(0x24292E), Color(0xE0E0E0))
        
        // 悬停效果
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                button.background = JBColor(Color(0xF6F8FA), Color(0x3C3F41))
                button.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JBColor(Color(0x0366D6), Color(0x0366D6)), 1),
                    EmptyBorder(JBUI.insets(4, 8))
                )
            }
            
            override fun mouseExited(e: MouseEvent) {
                button.background = JBColor(Color.WHITE, Color(0x2B2B2B))
                button.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JBColor(Color(0xE1E4E8), Color(0x3C3C3C)), 1),
                    EmptyBorder(JBUI.insets(4, 8))
                )
            }
        })
        
        button.addActionListener {
            showAddContextDialog()
        }
        return button
    }
    
    private fun createContextTag(context: ContextReference): JPanel {
        val tag = JPanel()
        tag.layout = BoxLayout(tag, BoxLayout.X_AXIS)
        tag.background = JBColor(Color.WHITE, Color(0x2B2B2B))
        tag.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(0xE1E4E8), Color(0x3C3C3C)), 1),
            EmptyBorder(JBUI.insets(4, 8))
        )
        tag.preferredSize = Dimension(tag.preferredSize.width, 20)  // 20px高度
        
        // 图标
        val icon = JLabel(getContextIcon(context))
        icon.font = icon.font.deriveFont(14f)
        tag.add(icon)
        tag.add(Box.createHorizontalStrut(6))
        
        // 文本
        val text = JLabel(getContextDisplay(context))
        text.font = text.font.deriveFont(12f)  // 12px字体
        text.foreground = JBColor(Color(0x0366D6), Color(0x42A5F5))
        tag.add(text)
        
        tag.add(Box.createHorizontalStrut(6))
        
        // 删除按钮
        val removeButton = JLabel("×")
        removeButton.font = removeButton.font.deriveFont(16f)
        removeButton.foreground = JBColor(Color(0x586069), Color(0xAAAAAA))
        removeButton.cursor = Cursor(Cursor.HAND_CURSOR)
        removeButton.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                contextManager.removeContext(context)
            }
            override fun mouseEntered(e: MouseEvent) {
                removeButton.foreground = JBColor(Color(0xD73A49), Color(0xD73A49))
            }
            override fun mouseExited(e: MouseEvent) {
                removeButton.foreground = JBColor(Color(0x586069), Color(0xAAAAAA))
            }
        })
        tag.add(removeButton)
        
        return tag
    }
    
    private fun getContextIcon(context: ContextReference): String {
        return when (context.type) {
            ContextType.FILE -> "📄"
            ContextType.FOLDER -> "📁"
            ContextType.IMAGE -> "🖼️"
            ContextType.WEB -> "🌐"
        }
    }
    
    private fun getContextDisplay(context: ContextReference): String {
        return when (context.type) {
            ContextType.FILE -> context.name ?: context.path ?: context.uri
            ContextType.FOLDER -> context.name ?: context.path ?: context.uri
            ContextType.IMAGE -> context.name ?: context.uri
            ContextType.WEB -> context.title ?: context.url ?: context.uri
        }
    }
    
    private fun showAddContextDialog() {
        // 简化版：使用文件选择器
        val fileChooser = JFileChooser()
        fileChooser.fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        fileChooser.isMultiSelectionEnabled = true
        
        val result = fileChooser.showOpenDialog(panel)
        if (result == JFileChooser.APPROVE_OPTION) {
            for (file in fileChooser.selectedFiles) {
                if (file.isDirectory) {
                    contextManager.addFolderContext(file.absolutePath)
                } else {
                    contextManager.addFileContext(file.absolutePath)
                }
            }
        }
    }
}


