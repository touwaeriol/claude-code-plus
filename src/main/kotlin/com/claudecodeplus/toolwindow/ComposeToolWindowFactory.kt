package com.claudecodeplus.toolwindow

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.awt.ComposePanel
import com.claudecodeplus.service.ClaudeCodeService
import com.claudecodeplus.ui.compose.ComposeChatWindow
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JComponent

/**
 * 使用 Compose UI 的工具窗口工厂
 */
class ComposeToolWindowFactory : ToolWindowFactory, DumbAware {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val service = ClaudeCodeService.getInstance()
        
        // 创建 Compose 面板
        val composePanel = createComposePanel(project, service)
        
        // 创建内容并添加到工具窗口
        val content = contentFactory.createContent(
            composePanel,
            "Claude Chat (Compose)",
            false
        )
        
        toolWindow.contentManager.addContent(content)
    }
    
    private fun createComposePanel(project: Project, service: ClaudeCodeService): JComponent {
        return ComposePanel().apply {
            setContent {
                MaterialTheme {
                    ComposeChatWindow(
                        project = project,
                        service = service
                    )
                }
            }
        }
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}