package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.GrepToolCall
import com.claudecodeplus.server.tools.IdeTools
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class GrepToolDisplay(
    private val grepToolCall: GrepToolCall,
    ideTools: IdeTools
) : BaseToolDisplay(grepToolCall, ideTools) {
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(createToolHeader(), BorderLayout.NORTH)
        val inputs = listOf(
            "pattern" to grepToolCall.pattern,
            "path" to (grepToolCall.path ?: "")
        )
        createInputPanel(inputs)?.let { panel.add(it, BorderLayout.CENTER) }
        createResultPanel()?.let { panel.add(it, BorderLayout.SOUTH) }
        return panel
    }
    override fun getToolDisplayName() = "Grep"
}

