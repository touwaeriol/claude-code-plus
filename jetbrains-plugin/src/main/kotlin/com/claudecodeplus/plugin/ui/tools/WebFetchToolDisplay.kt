package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.WebFetchToolCall
import com.claudecodeplus.server.tools.IdeTools
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class WebFetchToolDisplay(
    private val webFetchToolCall: WebFetchToolCall,
    ideTools: IdeTools
) : BaseToolDisplay(webFetchToolCall, ideTools) {
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(createToolHeader(), BorderLayout.NORTH)
        val inputs = listOf(
            "url" to webFetchToolCall.url,
            "prompt" to webFetchToolCall.prompt
        )
        createInputPanel(inputs)?.let { panel.add(it, BorderLayout.CENTER) }
        createResultPanel()?.let { panel.add(it, BorderLayout.SOUTH) }
        return panel
    }
    override fun getToolDisplayName() = "Web Fetch"
    override fun getToolIcon() = "🌍"
}

