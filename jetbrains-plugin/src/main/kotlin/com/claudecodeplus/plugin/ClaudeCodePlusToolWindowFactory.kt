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
import com.claudecodeplus.plugin.services.ClaudeCodePlusBackgroundService
import com.claudecodeplus.plugin.services.SessionStateSyncImpl
import com.intellij.openapi.components.service
import com.claudecodeplus.plugin.listeners.ClaudeToolWindowListener
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx

/**
 * IntelliJ IDEA 工具窗口工厂
 * 创建简化的聊天界面
 */
class ClaudeCodePlusToolWindowFactory : ToolWindowFactory {
    
    companion object {
        private val logger = Logger.getInstance(ClaudeCodePlusToolWindowFactory::class.java)
        
        // 存储当前会话对象的引用，用于New Chat功能
        @Volatile
        private var currentSessionObject: Any? = null
        
        /**
         * 设置当前会话对象
         */
        fun setCurrentSessionObject(sessionObject: Any?) {
            currentSessionObject = sessionObject
            logger.info("设置当前会话对象: $sessionObject")
        }
        
        /**
         * 清空当前会话
         */
        fun clearCurrentSession() {
            try {
                currentSessionObject?.let { session ->
                    // 通过反射调用clearSession方法
                    val clearMethod = session.javaClass.getMethod("clearSession")
                    clearMethod.invoke(session)
                    logger.info("✅ 会话已清空")
                }
            } catch (e: Exception) {
                logger.error("清空会话失败", e)
            }
        }
    }
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger.info("Creating Claude Code Plus tool window for project: ${project.basePath}")
        
        val contentFactory = ContentFactory.getInstance()
        
        try {
            // 创建服务实例
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val workingDirectory = project.basePath ?: System.getProperty("user.dir")
            val unifiedSessionService = UnifiedSessionService(scope)
            val cliSessionManager = ClaudeSessionManager()
            
            // 获取后台服务实例
            val backgroundService = service<ClaudeCodePlusBackgroundService>()
            val sessionStateSync = SessionStateSyncImpl()
            logger.info("🔗 已连接到后台服务，统计信息: ${backgroundService.getServiceStats()}")
            
            // 创建 IntelliJ 平台服务适配器
            val projectService = IdeaProjectServiceAdapter(project)
            val fileIndexService = SimpleFileIndexService(project)
            
            // 创建主题状态holder
            val currentTheme = IdeaThemeAdapter.isDarkTheme()
            val themeStateHolder = mutableStateOf(currentTheme)
            logger.info("当前 IDE 主题: ${IdeaThemeAdapter.getCurrentThemeName()}, 是否为暗色: $currentTheme")
            
            // 使用 toolwindow 提供的 Compose 面板，传入主题状态和后台服务
            val composePanel = PluginComposeFactory.createComposePanel(
                unifiedSessionService = unifiedSessionService,
                sessionManager = cliSessionManager,
                workingDirectory = workingDirectory,
                project = project,
                fileIndexService = fileIndexService,
                projectService = projectService,
                themeStateHolder = themeStateHolder,  // 传入主题状态
                backgroundService = backgroundService,  // 传入后台服务
                sessionStateSync = sessionStateSync     // 传入状态同步器
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
            
            // 注册工具窗口监听器
            val toolWindowListener = ClaudeToolWindowListener(project)
            val toolWindowManager = ToolWindowManager.getInstance(project)
            if (toolWindowManager is ToolWindowManagerEx) {
                // 使用新的API注册监听器，连接到项目的生命周期
                val connection = project.messageBus.connect(project)
                connection.subscribe(
                    com.intellij.openapi.wm.ex.ToolWindowManagerListener.TOPIC,
                    toolWindowListener
                )
                logger.info("✅ 已注册工具窗口监听器，连接绑定到项目生命周期")
            }
            
            logger.info("Claude Code Plus tool window created successfully - 默认创建新会话")
            
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
        
        // 添加标题栏按钮
        setupTitleActions(toolWindow)
    }
    
    private fun setupTitleActions(toolWindow: ToolWindow) {
        // 创建新会话按钮Action
        val newChatAction = object : com.intellij.openapi.actionSystem.AnAction(
            "New Chat",
            "Start a new conversation",
            com.intellij.icons.AllIcons.General.Add
        ) {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                logger.info("New Chat button clicked")
                
                // 直接清空当前会话
                clearCurrentSession()
            }
        }
        
        // 设置标题栏动作
        if (toolWindow is com.intellij.openapi.wm.ex.ToolWindowEx) {
            toolWindow.setTitleActions(listOf(newChatAction))
        }
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}