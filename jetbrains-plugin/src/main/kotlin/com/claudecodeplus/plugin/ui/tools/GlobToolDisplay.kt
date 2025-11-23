package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.GlobToolCall
import com.claudecodeplus.server.tools.IdeTools
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class GlobToolDisplay(
    private val globToolCall: GlobToolCall,
    ideTools: IdeTools
) : BaseToolDisplay(globToolCall, ideTools) {
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(createToolHeader(), BorderLayout.NORTH)
        val inputs = listOf(
            "pattern" to globToolCall.pattern,
            "path" to (globToolCall.path ?: "")
        )
        createInputPanel(inputs)?.let { panel.add(it, BorderLayout.CENTER) }
        createResultPanel()?.let { panel.add(it, BorderLayout.SOUTH) }
        return panel
    }
    override fun getToolDisplayName() = "Glob"
    override fun getToolIcon() = "📁"
}

