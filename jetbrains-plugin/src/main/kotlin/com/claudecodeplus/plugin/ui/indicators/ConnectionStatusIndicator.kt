package com.claudecodeplus.plugin.ui.indicators

import com.intellij.util.ui.JBUI
import kotlinx.coroutines.flow.StateFlow
import java.awt.Color
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * 连接状态指示器
 * 
 * 对应 frontend/src/components/ConnectionStatus.vue
 */
class ConnectionStatusIndicator(
    private val isConnectedFlow: StateFlow<Boolean>
) {
    
    private val label = JLabel()
    
    fun create(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.isOpaque = false
        panel.border = EmptyBorder(JBUI.insets(4, 8))
        
        label.font = label.font.deriveFont(Font.PLAIN, 11f)
        updateStatus()
        
        panel.add(label)
        
        return panel
    }
    
    fun updateStatus() {
        val isConnected = isConnectedFlow.value
        
        if (isConnected) {
            label.text = "🟢 已连接"
            label.foreground = Color(0x4CAF50)
        } else {
            label.text = "🔴 未连接"
            label.foreground = Color(0xF44336)
        }
    }
}


