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
import com.claudecodeplus.ui.services.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.intellij.openapi.diagnostic.Logger
import androidx.compose.runtime.mutableStateOf
import com.claudecodeplus.plugin.services.ClaudeCodePlusBackgroundService
import com.claudecodeplus.plugin.services.SessionStateSyncImpl
import com.intellij.openapi.components.service

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
            
            // 获取后台服务实例
            val backgroundService = service<ClaudeCodePlusBackgroundService>()
            val sessionStateSync = SessionStateSyncImpl()
            logger.info("🔗 已连接到后台服务，统计信息: ${backgroundService.getServiceStats()}")
            
            // 创建 IntelliJ 平台服务适配器
            val projectService = IdeaProjectServiceAdapter(project)
            val fileIndexService = SimpleFileIndexService(project)
            
            // 创建会话管理器实例（用于从 Claude 文件恢复会话）
            val sessionManagerForRestore = SessionManager()
            
            // 创建主题状态holder
            val currentTheme = IdeaThemeAdapter.isDarkTheme()
            val themeStateHolder = mutableStateOf(currentTheme)
            logger.info("当前 IDE 主题: ${IdeaThemeAdapter.getCurrentThemeName()}, 是否为暗色: $currentTheme")
            
            // 使用 toolwindow 提供的 Compose 面板，传入主题状态和后台服务
            val composePanel = PluginComposeFactory.createComposePanel(
                unifiedSessionService = unifiedSessionService,
                sessionManager = sessionManager,
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
            
            // 延迟恢复会话状态，给 UI 初始化留出时间
            scope.launch {
                delay(1000) // 延迟1秒等待 UI 完全初始化
                
                try {
                    logger.info("开始尝试从 Claude 会话文件恢复状态")
                    
                    // 尝试恢复主标签页的会话
                    val projectPath = project.basePath
                    if (projectPath != null) {
                        // 创建项目模型（简化版本）
                        val projectModel = com.claudecodeplus.ui.models.Project(
                            id = com.claudecodeplus.ui.utils.ClaudePathConverter.pathToClaudeProjectName(projectPath),
                            path = projectPath,
                            name = project.name,
                            lastAccessedAt = null
                        )
                        
                        // 尝试恢复默认标签页 "main" 的会话
                        val restored = sessionManagerForRestore.restoreSessionFromClaudeFile(
                            projectPath = projectPath,
                            tabId = "main",  // 默认标签页 ID
                            project = projectModel,
                            coroutineScope = scope
                        )
                        
                        if (restored) {
                            logger.info("成功启动会话恢复流程")
                        } else {
                            logger.info("项目没有可恢复的会话，将创建新会话")
                        }
                        
                        // 记录统计信息
                        val stats = sessionManagerForRestore.getSessionRestoreStats(projectPath)
                        logger.info("会话恢复统计: 已注册=${stats.registeredSessionCount}, 可用文件=${stats.availableFileCount}, 总大小=${stats.totalFileSizeMB}MB")
                        
                    } else {
                        logger.warn("项目路径为空，无法恢复会话")
                    }
                } catch (e: Exception) {
                    logger.error("恢复会话状态失败", e)
                }
            }
            
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