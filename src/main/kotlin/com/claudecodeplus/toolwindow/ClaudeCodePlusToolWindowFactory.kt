package com.claudecodeplus.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.OptimizedSessionManager
import com.claudecodeplus.core.ui.ConversationPanel
import com.claudecodeplus.idea.IdeaProjectService
import com.claudecodeplus.idea.IdeaFileSearchService

/**
 * Claude Code Plus 工具窗口工厂实现
 */
class ClaudeCodePlusToolWindowFactory : ToolWindowFactory {
    
    companion object {
        private val logger = com.intellij.openapi.diagnostic.Logger.getInstance(ClaudeCodePlusToolWindowFactory::class.java)
        private val cliWrapper = ClaudeCliWrapper()
        private val sessionManager = OptimizedSessionManager()
    }
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        
        // 创建服务实例
        val projectService = IdeaProjectService(project)
        val fileSearchService = IdeaFileSearchService(project)
        
        // 创建共享的对话面板
        val conversationPanel = ConversationPanel(projectService, fileSearchService, cliWrapper)
        
        val content = contentFactory.createContent(conversationPanel, "", false)
        toolWindow.contentManager.addContent(content)
        
        // 添加工具窗口关闭时的清理逻辑
        toolWindow.addContentManagerListener(object : com.intellij.ui.content.ContentManagerListener {
            override fun contentRemoved(event: com.intellij.ui.content.ContentManagerEvent) {
                // 清理资源
                sessionManager.cleanup()
            }
        })
        
        // 添加齿轮菜单项（设置）
        val gearActions = DefaultActionGroup()
        gearActions.add(object : AnAction("Settings", "打开 Claude Code Plus 设置", null) {
            override fun actionPerformed(e: AnActionEvent) {
                // 打开到 Tools > Claude Code Plus 设置组
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "com.claudecodeplus.settings")
            }
        })
        toolWindow.setAdditionalGearActions(gearActions)
    }
}