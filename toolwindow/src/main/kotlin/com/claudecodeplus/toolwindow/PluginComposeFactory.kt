package com.claudecodeplus.toolwindow

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposePanel
import com.claudecodeplus.ui.services.UnifiedSessionService
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.jewel.StandaloneChatView
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.core.interfaces.ProjectService
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import javax.swing.UIManager
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
        unifiedSessionService: UnifiedSessionService,
        sessionManager: ClaudeSessionManager,
        workingDirectory: String,
        project: Any? = null,  // 改为 Any 类型，避免依赖 IntelliJ API
        fileIndexService: FileIndexService? = null,
        projectService: ProjectService? = null,
        themeStateHolder: MutableState<Boolean>? = null,  // 新增：外部传入的主题状态
        backgroundService: Any? = null,  // 新增：后台服务
        sessionStateSync: Any? = null,   // 新增：状态同步器
        onNewSessionRequest: (() -> Unit)? = null  // 新增：新会话请求回调
    ): JComponent {
        return JPanel(BorderLayout()).apply {
            val composePanel = ComposePanel()
            
            // 设置 Compose 内容
            composePanel.setContent {
                // 使用外部传入的主题状态，如果没有则使用本地检测
                val isDark = if (themeStateHolder != null) {
                    themeStateHolder.value
                } else {
                    // 备用方案：使用 UIManager 检测主题
                    remember { 
                        try {
                            val laf = UIManager.getLookAndFeel()
                            laf.name.contains("Darcula", ignoreCase = true) || 
                            laf.name.contains("Dark", ignoreCase = true) ||
                            laf.javaClass.name.contains("Darcula", ignoreCase = true)
                        } catch (e: Exception) {
                            false
                        }
                    }
                }
                
                // 根据主题状态选择主题
                IntUiTheme(isDark = isDark) {
                    StandaloneChatView(
                        unifiedSessionService = unifiedSessionService,
                        workingDirectory = workingDirectory,
                        fileIndexService = fileIndexService,
                        projectService = projectService,
                        sessionManager = sessionManager,
                        backgroundService = backgroundService,
                        sessionStateSync = sessionStateSync,
                        onNewSessionRequest = onNewSessionRequest
                    )
                }
            }
            
            // 添加到面板，确保使用全部空间
            add(composePanel, BorderLayout.CENTER)
        }
    }
}