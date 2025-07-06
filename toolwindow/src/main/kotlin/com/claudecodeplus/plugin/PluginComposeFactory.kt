package com.claudecodeplus.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.jewel.ChatView
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.ProjectService
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * 为插件集成提供的 Compose UI 工厂
 * 这个类专门处理 IntelliJ 插件环境中的 Compose 集成
 */
object PluginComposeFactory {
    
    /**
     * 创建聊天面板的 Compose UI
     * 这个方法被设计为从插件模块调用，避免了类加载器问题
     */
    @JvmStatic
    fun createChatPanel(
        cliWrapper: ClaudeCliWrapper,
        workingDirectory: String,
        isDarkTheme: Boolean,
        fileIndexService: FileIndexService? = null,
        projectService: ProjectService? = null,
        sessionManager: ClaudeSessionManager = ClaudeSessionManager()
    ): JComponent {
        val composePanel = ComposePanel()
        
        // 确保在 EDT 中设置内容
        if (SwingUtilities.isEventDispatchThread()) {
            setComposeContent(composePanel, cliWrapper, workingDirectory, isDarkTheme, 
                            fileIndexService, projectService, sessionManager)
        } else {
            SwingUtilities.invokeLater {
                setComposeContent(composePanel, cliWrapper, workingDirectory, isDarkTheme,
                                fileIndexService, projectService, sessionManager)
            }
        }
        
        return composePanel
    }
    
    private fun setComposeContent(
        panel: ComposePanel,
        cliWrapper: ClaudeCliWrapper,
        workingDirectory: String,
        isDarkTheme: Boolean,
        fileIndexService: FileIndexService?,
        projectService: ProjectService?,
        sessionManager: ClaudeSessionManager
    ) {
        try {
            // 使用标准的 setContent 方法
            panel.setContent {
                IntUiTheme(isDark = isDarkTheme) {
                    ChatView(
                        cliWrapper = cliWrapper,
                        workingDirectory = workingDirectory,
                        fileIndexService = fileIndexService,
                        projectService = projectService,
                        sessionManager = sessionManager
                    )
                }
            }
        } catch (e: NoSuchMethodError) {
            // 如果标准方法不存在，尝试反射
            trySetContentViaReflection(panel) {
                IntUiTheme(isDark = isDarkTheme) {
                    ChatView(
                        cliWrapper = cliWrapper,
                        workingDirectory = workingDirectory,
                        fileIndexService = fileIndexService,
                        projectService = projectService,
                        sessionManager = sessionManager
                    )
                }
            }
        }
    }
    
    private fun trySetContentViaReflection(panel: ComposePanel, content: @Composable () -> Unit) {
        val methods = panel.javaClass.methods.filter { it.name == "setContent" }
        
        for (method in methods) {
            try {
                when (method.parameterCount) {
                    1 -> {
                        method.invoke(panel, content)
                        return
                    }
                    2 -> {
                        method.invoke(panel, null, content)
                        return
                    }
                }
            } catch (e: Exception) {
                // 继续尝试下一个方法
            }
        }
        
        throw RuntimeException("无法找到合适的 setContent 方法")
    }
}