package com.claudecodeplus.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.claudecodeplus.ui.services.UnifiedSessionService
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.toolwindow.PluginComposeFactory
import com.claudecodeplus.plugin.adapters.IdeaProjectServiceAdapter
import com.claudecodeplus.plugin.adapters.SimpleFileIndexService
import com.claudecodeplus.plugin.theme.IdeaThemeAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.intellij.openapi.diagnostic.Logger
import androidx.compose.runtime.mutableStateOf

/**
 * IntelliJ IDEA 工具窗口工厂
 * 创建简化的聊天界面
 */
class ClaudeCodePlusToolWindowFactory : ToolWindowFactory {
    
    companion object {
        private val logger = Logger.getInstance(ClaudeCodePlusToolWindowFactory::class.java)
    }
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger.info("Creating Claude Code Plus tool window for project: ${project.basePath}")
        
        val contentFactory = ContentFactory.getInstance()
        
        try {
            // 创建服务实例
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val workingDirectory = project.basePath ?: System.getProperty("user.dir")
            val unifiedSessionService = UnifiedSessionService(scope)
            val sessionManager = ClaudeSessionManager()
            
            // 创建 IntelliJ 平台服务适配器
            val projectService = IdeaProjectServiceAdapter(project)
            val fileIndexService = SimpleFileIndexService(project)
            
            // 创建主题状态holder
            val currentTheme = IdeaThemeAdapter.isDarkTheme()
            val themeStateHolder = mutableStateOf(currentTheme)
            logger.info("当前 IDE 主题: ${IdeaThemeAdapter.getCurrentThemeName()}, 是否为暗色: $currentTheme")
            
            // 使用 toolwindow 提供的 Compose 面板，传入主题状态
            val composePanel = PluginComposeFactory.createComposePanel(
                unifiedSessionService = unifiedSessionService,
                sessionManager = sessionManager,
                workingDirectory = workingDirectory,
                project = project,
                fileIndexService = fileIndexService,
                projectService = projectService,
                themeStateHolder = themeStateHolder  // 传入主题状态
            )
            
            // 注册主题变化监听器，更新主题状态
            IdeaThemeAdapter.registerThemeChangeListener { isDark ->
                logger.info("IDE 主题已变更为: ${if (isDark) "暗色" else "亮色"}")
                // 更新主题状态，触发 Compose 重新渲染
                themeStateHolder.value = isDark
            }
            
            // 创建内容并添加到工具窗口
            val content = contentFactory.createContent(composePanel, "", false)
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
    
    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "Claude AI"
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}