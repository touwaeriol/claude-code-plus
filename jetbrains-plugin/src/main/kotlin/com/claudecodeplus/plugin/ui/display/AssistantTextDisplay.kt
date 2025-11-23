package com.claudecodeplus.plugin.ui.display

import com.claudecodeplus.plugin.types.AssistantTextItem
import com.claudecodeplus.plugin.ui.markdown.MarkdownRenderer
import com.claudecodeplus.plugin.ui.markdown.MarkdownTheme
import com.claudecodeplus.server.tools.IdeTools
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * AI 文本展示组件
 * 
 * 对应 frontend/src/components/chat/AssistantTextDisplay.vue
 */
class AssistantTextDisplay(
    private val message: AssistantTextItem,
    private val ideTools: IdeTools
) {
    
    private val markdownRenderer = MarkdownRenderer()
    
    fun create(): JComponent {
        val container = JBPanel<JBPanel<*>>(BorderLayout())
        container.isOpaque = false
        container.border = JBUI.Borders.empty(8, 0)
        
        // 消息气泡（左对齐）
        val bubble = createMessageBubble()
        
        // 左对齐布局
        val wrapper = JBPanel<JBPanel<*>>(BorderLayout())
        wrapper.isOpaque = false
        wrapper.add(bubble, BorderLayout.WEST)
        wrapper.add(Box.createHorizontalStrut(JBUI.scale(100)), BorderLayout.EAST) // 右侧留白
        
        container.add(wrapper, BorderLayout.CENTER)
        
        // Token 统计（如果是最后一个文本块）
        if (message.isLastInMessage && message.stats != null) {
            val statsPanel = createStatsPanel()
            container.add(statsPanel, BorderLayout.SOUTH)
        }
        
        return container
    }
    
    private fun createMessageBubble(): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.background = JBColor(
            Color(0xF5F5F5),  // Light theme - 浅灰色
            Color(0x2B2B2B)   // Dark theme - 深灰色
        )
        panel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(12)
        )
        
        // 使用 Markdown 渲染
        val content = markdownRenderer.render(message.content, MarkdownTheme.default())
        panel.add(content, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createStatsPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.isOpaque = false
        panel.border = JBUI.Borders.emptyTop(4)
        
        val stats = message.stats!!
        val statsText = "📊 输入: ${stats.inputTokens} tokens | 输出: ${stats.outputTokens} tokens | 耗时: ${stats.requestDuration}ms"
        
        val label = JBLabel(statsText)
        label.font = JBUI.Fonts.smallFont()
        label.foreground = JBColor.GRAY
        
        panel.add(Box.createHorizontalGlue())
        panel.add(label)
        
        return panel
    }
}

