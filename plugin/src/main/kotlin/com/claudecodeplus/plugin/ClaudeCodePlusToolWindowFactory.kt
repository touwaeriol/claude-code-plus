package com.claudecodeplus.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.plugin.adapters.IdeaProjectServiceAdapter
import com.claudecodeplus.plugin.adapters.SimpleFileIndexService
import com.claudecodeplus.plugin.PluginComposeFactory
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.diagnostic.Logger

/**
 * IntelliJ IDEA 工具窗口工厂
 * 负责创建并配置 Claude Code Plus 工具窗口
 */
class ClaudeCodePlusToolWindowFactory : ToolWindowFactory {
    
    companion object {
        private val logger = Logger.getInstance(ClaudeCodePlusToolWindowFactory::class.java)
    }
    
    /**
     * 创建工具窗口内容
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger.info("Creating Claude Code Plus tool window for project: ${project.basePath}")
        
        val contentFactory = ContentFactory.getInstance()
        
        try {
            // 创建服务实例
            val cliWrapper = ClaudeCliWrapper()
            val sessionManager = ClaudeSessionManager()
            val projectService = IdeaProjectServiceAdapter(project)
            val fileIndexService = SimpleFileIndexService(project)
            val workingDirectory = project.basePath ?: System.getProperty("user.dir")
            
            // 使用 PluginComposeFactory 创建 Compose UI
            val compositePanel = PluginComposeFactory.createChatPanel(
                cliWrapper = cliWrapper,
                workingDirectory = workingDirectory,
                isDarkTheme = UIUtil.isUnderDarcula(),
                fileIndexService = fileIndexService,
                projectService = projectService,
                sessionManager = sessionManager
            )
            
            // 创建内容并添加到工具窗口
            val content = contentFactory.createContent(compositePanel, "Claude Chat", false)
            toolWindow.contentManager.addContent(content)
            
            logger.info("Claude Code Plus tool window created successfully")
            
        } catch (e: Exception) {
            logger.error("Failed to create Claude Code Plus tool window", e)
            
            // 显示错误面板
            val errorPanel = javax.swing.JPanel(java.awt.BorderLayout())
            val errorLabel = javax.swing.JLabel(
                "<html><center>" +
                "<h2>Claude Code Plus</h2>" +
                "<p style='color:red'>初始化失败: ${e.message}</p>" +
                "<p style='color:gray'>请检查 Claude CLI 是否已安装</p>" +
                "</center></html>",
                javax.swing.SwingConstants.CENTER
            )
            errorPanel.add(errorLabel, java.awt.BorderLayout.CENTER)
            
            val content = contentFactory.createContent(errorPanel, "Error", false)
            toolWindow.contentManager.addContent(content)
        }
    }
    
    /**
     * 配置工具窗口初始状态
     */
    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "Claude Code Plus"
    }
    
    /**
     * 是否应该可用
     */
    override fun shouldBeAvailable(project: Project): Boolean = true
}