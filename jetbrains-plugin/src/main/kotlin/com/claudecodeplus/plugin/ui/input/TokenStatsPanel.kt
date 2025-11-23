package com.claudecodeplus.plugin.ui.input

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
 * Token 统计面板
 * 
 * 对应 frontend/src/components/chat/ContextUsageIndicator.vue
 */
class TokenStatsPanel(
    private val inputTokensFlow: StateFlow<Int>,
    private val outputTokensFlow: StateFlow<Int>
) {
    
    private val statsLabel = JLabel()
    
    init {
        updateStats()
    }
    
    fun create(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.isOpaque = false
        panel.border = EmptyBorder(JBUI.insets(0, 4))
        
        statsLabel.font = statsLabel.font.deriveFont(Font.PLAIN, 11f)
        statsLabel.foreground = Color(0x666666)
        
        panel.add(statsLabel)
        
        return panel
    }
    
    fun updateStats() {
        val inputTokens = inputTokensFlow.value
        val outputTokens = outputTokensFlow.value
        val totalTokens = inputTokens + outputTokens
        
        statsLabel.text = "📊 In: $inputTokens | Out: $outputTokens | Total: $totalTokens"
    }
}


