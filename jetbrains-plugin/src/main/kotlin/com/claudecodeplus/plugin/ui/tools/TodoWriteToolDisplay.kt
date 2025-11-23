package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.TodoWriteToolCall
import com.claudecodeplus.server.tools.IdeTools
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class TodoWriteToolDisplay(
    private val todoWriteToolCall: TodoWriteToolCall,
    ideTools: IdeTools
) : BaseToolDisplay(todoWriteToolCall, ideTools) {
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(createToolHeader(), BorderLayout.NORTH)
        val inputs = listOf("todos" to "${todoWriteToolCall.todos.size} 个待办事项")
        createInputPanel(inputs)?.let { panel.add(it, BorderLayout.CENTER) }
        createResultPanel()?.let { panel.add(it, BorderLayout.SOUTH) }
        return panel
    }
    override fun getToolDisplayName() = "Todo Write"
    override fun getToolIcon() = "✅"
}

