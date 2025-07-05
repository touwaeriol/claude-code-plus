package com.claudecodeplus.plugin

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
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.plugin.ui.ChatAppWithSessionsPanel
import com.claudecodeplus.idea.IdeaProjectService
import com.claudecodeplus.idea.IdeaFileSearchService
import com.claudecodeplus.idea.IdeaContextProvider
import com.claudecodeplus.plugin.adapters.IdeaProjectServiceAdapter
import com.claudecodeplus.plugin.adapters.IdeaFileIndexServiceAdapter
import kotlinx.coroutines.*

/**
 * Claude Code Plus 工具窗口工厂实现
 */
class ClaudeCodePlusToolWindowFactory : ToolWindowFactory {
    
    companion object {
        private val logger = com.intellij.openapi.diagnostic.Logger.getInstance(ClaudeCodePlusToolWindowFactory::class.java)
        private val cliWrapper = ClaudeCliWrapper()
        private val sessionManager = OptimizedSessionManager()
        private val claudeSessionManager = ClaudeSessionManager()
    }
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger.info("Creating tool window content for project: ${project.name}")
        
        val contentFactory = ContentFactory.getInstance()
        
        try {
            // 创建服务
            logger.info("Creating project services...")
            val projectService = IdeaProjectServiceAdapter(IdeaProjectService(project))
            val contextProvider = IdeaContextProvider(project)
            val ideaFileSearchService = IdeaFileSearchService(project)
            val fileIndexService = IdeaFileIndexServiceAdapter(ideaFileSearchService)
            val workingDirectory = project.basePath ?: System.getProperty("user.dir")
        
            // 创建带会话管理的聊天面板（使用改进的反射方法）
            logger.info("Creating chat panel with Compose UI...")
            val chatPanel = ChatAppWithSessionsPanel(
                cliWrapper = cliWrapper,
                workingDirectory = workingDirectory,
                fileIndexService = fileIndexService,
                projectService = projectService,
                sessionManager = claudeSessionManager
            )
            
            val content = contentFactory.createContent(chatPanel, "Claude Chat", false)
            toolWindow.contentManager.addContent(content)
            
            // 添加清理逻辑
            content.setDisposer {
                sessionManager.cleanup()
            }
            
            logger.info("Tool window content created successfully")
        } catch (e: Exception) {
            logger.error("Failed to create tool window content", e)
            
            // 创建错误面板作为后备
            val errorPanel = javax.swing.JPanel(java.awt.BorderLayout())
            val errorLabel = javax.swing.JLabel(
                "<html><center><h2>Claude Code Plus</h2><p style='color:red'>初始化失败: ${e.message}</p></center></html>",
                javax.swing.SwingConstants.CENTER
            )
            errorPanel.add(errorLabel, java.awt.BorderLayout.CENTER)
            
            val content = contentFactory.createContent(errorPanel, "Error", false)
            toolWindow.contentManager.addContent(content)
        }
        
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