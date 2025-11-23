package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.WebSearchToolCall
import com.claudecodeplus.server.tools.IdeTools
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class WebSearchToolDisplay(
    private val webSearchToolCall: WebSearchToolCall,
    ideTools: IdeTools
) : BaseToolDisplay(webSearchToolCall, ideTools) {
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(createToolHeader(), BorderLayout.NORTH)
        val inputs = listOf("query" to webSearchToolCall.query)
        createInputPanel(inputs)?.let { panel.add(it, BorderLayout.CENTER) }
        createResultPanel()?.let { panel.add(it, BorderLayout.SOUTH) }
        return panel
    }
    override fun getToolDisplayName() = "Web Search"
    override fun getToolIcon() = "🌐"
}

