package com.claudecodeplus.plugin.ui.display

import com.claudecodeplus.plugin.types.SystemMessageItem
import com.claudecodeplus.plugin.types.SystemMessageLevel
import com.claudecodeplus.server.tools.IdeTools
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 系统消息展示组件
 * 
 * 对应 frontend/src/components/chat/SystemMessageDisplay.vue
 */
class SystemMessageDisplay(
    private val message: SystemMessageItem,
    private val ideTools: IdeTools
) {
    
    fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = EmptyBorder(JBUI.insets(8, 0))
        panel.isOpaque = false
        
        val label = JLabel(escapeHtml(message.content))
        label.horizontalAlignment = SwingConstants.CENTER
        label.font = label.font.deriveFont(Font.ITALIC, 12f)
        label.foreground = when (message.level) {
            SystemMessageLevel.ERROR -> Color(0xD32F2F)
            SystemMessageLevel.WARNING -> Color(0xF57C00)
            SystemMessageLevel.INFO -> Color(0x666666)
        }
        
        panel.add(label, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun escapeHtml(text: String): String {
        return "<html><div style='text-align: center;'>$text</div></html>"
    }
}


