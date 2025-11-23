package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.AskUserQuestionToolCall
import com.claudecodeplus.server.tools.IdeTools
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class AskUserQuestionToolDisplay(toolCall: AskUserQuestionToolCall, ideTools: IdeTools) : BaseToolDisplay(toolCall, ideTools) {
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(createToolHeader(), BorderLayout.NORTH)
        createResultPanel()?.let { panel.add(it, BorderLayout.SOUTH) }
        return panel
    }
    override fun getToolDisplayName() = "Ask User Question"
}


