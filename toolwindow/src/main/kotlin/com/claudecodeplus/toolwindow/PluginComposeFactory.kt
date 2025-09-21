package com.claudecodeplus.toolwindow

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposePanel
import com.claudecodeplus.ui.services.UnifiedSessionService
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.components.StandaloneChatView
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.core.services.ProjectService
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import androidx.compose.runtime.CompositionLocalProvider
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
    @OptIn(ExperimentalJewelApi::class)
    fun createComposePanel(
        unifiedSessionService: UnifiedSessionService,
        sessionManager: ClaudeSessionManager,
        workingDirectory: String,
        project: Any? = null,  // 改为 Any 类型，避免依赖 IntelliJ API
        fileIndexService: FileIndexService? = null,
        projectService: ProjectService? = null,
        ideIntegration: com.claudecodeplus.ui.services.IdeIntegration? = null,  // 新增：IDE 集成
        themeStateHolder: MutableState<Boolean>? = null,  // 新增：外部传入的主题状态
        backgroundService: Any? = null,  // 新增：后台服务
        sessionStateSync: Any? = null,   // 新增：状态同步器
        onNewSessionRequest: (() -> Unit)? = null  // 新增：新会话请求回调
    ): JComponent {
        return JPanel(BorderLayout()).apply {
            val composePanel = ComposePanel()
            
            // 设置 Compose 内容
            composePanel.setContent {
                // 手动提供 GlobalColors 上下文，解决主题错误
                SwingBridgeTheme {
                    StandaloneChatView(
                        unifiedSessionService = unifiedSessionService,
                        workingDirectory = workingDirectory,
                        fileIndexService = fileIndexService,
                        projectService = projectService,
                        sessionManager = sessionManager,
                        ideIntegration = ideIntegration,
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