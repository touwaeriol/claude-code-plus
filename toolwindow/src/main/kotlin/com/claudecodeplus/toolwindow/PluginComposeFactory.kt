package com.claudecodeplus.toolwindow

import androidx.compose.ui.awt.ComposePanel
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.jewel.ChatView
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.core.interfaces.ProjectService
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * 为插件模块提供 Compose 面板的工厂类
 * 解决跨模块 Compose 类加载器问题
 */
object PluginComposeFactory {
    
    /**
     * 创建带会话管理的聊天 Compose 面板
     */
    fun createComposePanel(
        cliWrapper: ClaudeCliWrapper,
        sessionManager: ClaudeSessionManager,
        workingDirectory: String,
        project: Any? = null,  // 改为 Any 类型，避免依赖 IntelliJ API
        fileIndexService: FileIndexService? = null,
        projectService: ProjectService? = null
    ): JComponent {
        return JPanel(BorderLayout()).apply {
            val composePanel = ComposePanel()
            
            // 设置 Compose 内容
            composePanel.setContent {
                // 使用系统属性判断主题
                val isDark = try {
                    // 尝试通过反射获取主题状态
                    val laf = javax.swing.UIManager.getLookAndFeel()
                    laf.name.contains("Darcula", ignoreCase = true) || 
                    laf.name.contains("Dark", ignoreCase = true)
                } catch (e: Exception) {
                    false
                }
                
                IntUiTheme(isDark = isDark) {
                    ChatView(
                        cliWrapper = cliWrapper,
                        workingDirectory = workingDirectory,
                        fileIndexService = fileIndexService,
                        projectService = projectService,
                        sessionManager = sessionManager
                    )
                }
            }
            
            // 添加到面板，确保使用全部空间
            add(composePanel, BorderLayout.CENTER)
        }
    }
}