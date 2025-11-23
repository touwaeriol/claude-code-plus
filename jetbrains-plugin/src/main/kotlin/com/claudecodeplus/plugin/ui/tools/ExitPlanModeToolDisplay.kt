package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.ExitPlanModeToolCall
import com.claudecodeplus.server.tools.IdeTools
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ExitPlanModeToolDisplay(toolCall: ExitPlanModeToolCall, ideTools: IdeTools) : BaseToolDisplay(toolCall, ideTools) {
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(createToolHeader(), BorderLayout.NORTH)
        return panel
    }
    override fun getToolDisplayName() = "Exit Plan Mode"
}


