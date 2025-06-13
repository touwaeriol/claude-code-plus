package com.claudecodeplus.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.claudecodeplus.ui.ChatWindow

class ClaudeCodeToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatWindow = ChatWindow(project)
        val content = ContentFactory.getInstance().createContent(
            chatWindow.createComponent(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
    
    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "Claude Code"
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}