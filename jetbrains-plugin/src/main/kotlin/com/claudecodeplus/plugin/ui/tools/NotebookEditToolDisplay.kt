package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.NotebookEditToolCall
import com.claudecodeplus.server.tools.IdeTools
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * NotebookEdit 工具展示组件
 */
class NotebookEditToolDisplay(
    toolCall: NotebookEditToolCall,
    ideTools: IdeTools
) : BaseToolDisplay(toolCall, ideTools) {
    
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = javax.swing.BorderFactory.createLineBorder(java.awt.Color(0xE0E0E0), 1)
        
        panel.add(createToolHeader(), BorderLayout.NORTH)
        
        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.border = EmptyBorder(JBUI.insets(8))
        
        val inputs = toolCall.input.map { (k, v) -> k to (v?.toString() ?: "") }
        createInputPanel(inputs)?.let {
            panel.add(it, BorderLayout.CENTER)
        }
        
        createResultPanel()?.let {
            panel.add(it, BorderLayout.SOUTH)
        }
        
        return panel
    }
    
    override fun getToolDisplayName(): String = "Notebook Edit"
    override fun getToolIcon(): String = "📓"
}

