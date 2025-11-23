package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.BashToolCall
import com.claudecodeplus.server.tools.IdeTools
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class BashToolDisplay(
    private val bashToolCall: BashToolCall,
    ideTools: IdeTools
) : BaseToolDisplay(bashToolCall, ideTools) {
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(createToolHeader(), BorderLayout.NORTH)
        val inputs = listOf(
            "command" to bashToolCall.command,
            "cwd" to (bashToolCall.cwd ?: "")
        )
        createInputPanel(inputs)?.let { panel.add(it, BorderLayout.CENTER) }
        createResultPanel()?.let { panel.add(it, BorderLayout.SOUTH) }
        return panel
    }
    override fun getToolDisplayName() = "Bash"
    override fun getToolIcon() = "⚡"
}

