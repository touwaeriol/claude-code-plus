package com.claudecodeplus.plugin.ui.display

import com.claudecodeplus.plugin.types.UserMessageItem
import com.claudecodeplus.server.tools.IdeTools
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 用户消息展示组件
 * 
 * 对应 frontend/src/components/chat/UserMessageDisplay.vue
 */
class UserMessageDisplay(
    private val message: UserMessageItem,
    private val ideTools: IdeTools
) {
    
    fun create(): JComponent {
        val container = JBPanel<JBPanel<*>>(BorderLayout())
        container.isOpaque = false
        container.border = JBUI.Borders.empty(8, 0)
        
        // 消息气泡（右对齐）
        val bubble = createMessageBubble()
        
        // 右对齐布局
        val wrapper = JBPanel<JBPanel<*>>(BorderLayout())
        wrapper.isOpaque = false
        wrapper.add(Box.createHorizontalStrut(JBUI.scale(100)), BorderLayout.WEST) // 左侧留白
        wrapper.add(bubble, BorderLayout.EAST)
        
        container.add(wrapper, BorderLayout.CENTER)
        
        return container
    }
    
    private fun createMessageBubble(): JComponent {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = JBColor(
            Color(0xE3F2FD),  // Light theme - 浅蓝色
            Color(0x1E3A5F)   // Dark theme - 深蓝色
        )
        panel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor(Color(0x2196F3), Color(0x42A5F5)), 1),
            JBUI.Borders.empty(12)
        )
        
        // 上下文标签
        if (message.contexts.isNotEmpty()) {
            val contextsPanel = createContextsPanel()
            panel.add(contextsPanel)
            panel.add(Box.createVerticalStrut(8))
        }
        
        // 消息文本
        val textArea = JTextArea(message.content)
        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.isOpaque = false
        textArea.font = Font("Dialog", Font.PLAIN, 13)
        textArea.foreground = Color(0x212121)
        panel.add(textArea)
        
        return panel
    }
    
    private fun createContextsPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.isOpaque = false
        
        for (context in message.contexts) {
            val chip = JBLabel(getContextDisplay(context))
            chip.background = JBColor(Color(0xBBDEFB), Color(0x2C5F8D))
            chip.foreground = JBColor(Color(0x1976D2), Color(0x64B5F6))
            chip.isOpaque = true
            chip.border = JBUI.Borders.empty(2, 6)
            chip.font = JBUI.Fonts.smallFont()
            
            panel.add(chip)
            panel.add(Box.createHorizontalStrut(JBUI.scale(4)))
        }
        
        return panel
    }
    
    private fun getContextDisplay(context: com.claudecodeplus.plugin.types.ContextReference): String {
        return when (context.type) {
            com.claudecodeplus.plugin.types.ContextType.FILE -> "📎 ${context.path ?: context.uri}"
            com.claudecodeplus.plugin.types.ContextType.WEB -> "🌐 ${context.title ?: context.url ?: context.uri}"
            com.claudecodeplus.plugin.types.ContextType.FOLDER -> "📁 ${context.path ?: context.uri}"
            com.claudecodeplus.plugin.types.ContextType.IMAGE -> "🖼️ ${context.name ?: context.uri}"
        }
    }
}

