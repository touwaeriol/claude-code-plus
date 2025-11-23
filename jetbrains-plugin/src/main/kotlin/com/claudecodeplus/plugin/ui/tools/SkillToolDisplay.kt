package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.SkillToolCall
import com.claudecodeplus.server.tools.IdeTools
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class SkillToolDisplay(toolCall: SkillToolCall, ideTools: IdeTools) : BaseToolDisplay(toolCall, ideTools) {
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(createToolHeader(), BorderLayout.NORTH)
        createResultPanel()?.let { panel.add(it, BorderLayout.SOUTH) }
        return panel
    }
    override fun getToolDisplayName() = "Skill"
}


