package com.claudecodeplus.toolwindow

import com.claudecodeplus.idea.IdeaContextProvider
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.redesign.EnhancedConversationPanel
import com.claudecodeplus.ui.services.ProjectService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.nio.file.Paths

/**
 * Claude Code Plus 工具窗口工厂
 */
class ClaudeCodePlusToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 创建项目服务适配器
        val projectService = createProjectService(project)
        
        // 创建上下文提供者
        val contextProvider = IdeaContextProvider(project)
        
        // 创建 CLI wrapper
        val cliWrapper = ClaudeCliWrapper()
        
        // 创建增强的对话面板
        val conversationPanel = EnhancedConversationPanel(
            projectService = projectService,
            contextProvider = contextProvider,
            cliWrapper = cliWrapper
        )
        
        // 创建内容
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(
            conversationPanel,
            "Claude Assistant",
            false
        )
        
        toolWindow.contentManager.addContent(content)
    }
    
    /**
     * 创建项目服务适配器
     */
    private fun createProjectService(project: Project): ProjectService {
        return object : ProjectService {
            override fun getProjectPath(): String = project.basePath ?: ""
            
            override fun getProjectName(): String = project.name
            
            override fun openFile(filePath: String, lineNumber: Int?) {
                // TODO: 实现文件打开功能
                println("Opening file: $filePath at line $lineNumber")
            }
            
            override fun showSettings(settingsId: String?) {
                // TODO: 实现设置打开功能
                println("Showing settings: $settingsId")
            }
        }
    }
}