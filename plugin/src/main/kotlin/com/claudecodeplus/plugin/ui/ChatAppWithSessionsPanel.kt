package com.claudecodeplus.plugin.ui

import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.ProjectService
import com.claudecodeplus.plugin.PluginComposeFactory
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JLabel
import javax.swing.SwingConstants
import java.awt.BorderLayout
import com.intellij.openapi.diagnostic.Logger

/**
 * 带会话管理的聊天面板包装器
 * 使用 toolwindow 模块提供的工厂方法创建 Compose UI
 */
fun ChatAppWithSessionsPanel(
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String,
    fileIndexService: FileIndexService? = null,
    projectService: ProjectService? = null,
    sessionManager: ClaudeSessionManager = ClaudeSessionManager()
): JComponent {
    val logger = Logger.getInstance("ChatAppWithSessionsPanel")
    
    logger.info("Creating ChatAppWithSessionsPanel using PluginComposeFactory for directory: $workingDirectory")
    
    return try {
        // 使用 toolwindow 模块的工厂方法创建 Compose UI
        // 这避免了插件模块中的类加载器问题
        PluginComposeFactory.createChatPanel(
            cliWrapper = cliWrapper,
            workingDirectory = workingDirectory,
            isDarkTheme = UIUtil.isUnderDarcula(),
            fileIndexService = fileIndexService,
            projectService = projectService,
            sessionManager = sessionManager
        )
    } catch (e: Exception) {
        logger.error("Failed to create ChatAppWithSessionsPanel", e)
        
        // 创建错误面板作为后备方案
        val errorPanel = JPanel(BorderLayout())
        val errorLabel = JLabel(
            "<html><center>" +
            "<h2>Claude Code Plus</h2>" +
            "<p style='color:red'>组件初始化失败: ${e.message}</p>" +
            "<p style='color:gray'>请检查依赖配置</p>" +
            "</center></html>",
            SwingConstants.CENTER
        )
        errorPanel.add(errorLabel, BorderLayout.CENTER)
        errorPanel
    }
}