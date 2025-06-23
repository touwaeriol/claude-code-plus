package com.claudecodeplus.ui.redesign

import androidx.compose.ui.awt.ComposePanel
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.services.ContextProvider
import com.claudecodeplus.ui.services.ProjectService
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * 增强的对话面板 - Swing 包装器
 * 将 Compose UI 集成到 IntelliJ IDEA 的工具窗口中
 */
class EnhancedConversationPanel(
    private val projectService: ProjectService,
    private val contextProvider: ContextProvider,
    private val cliWrapper: ClaudeCliWrapper = ClaudeCliWrapper(),
    private val isDarkTheme: Boolean = true
) : JPanel(BorderLayout()) {
    
    private val composePanel = ComposePanel()
    
    init {
        add(composePanel, BorderLayout.CENTER)
        
        // 设置 Compose 内容
        updateContent()
    }
    
    /**
     * 更新内容（例如主题变化时）
     */
    fun updateContent() {
        composePanel.setContent {
            val theme = if (isDarkTheme) {
                JewelTheme.darkThemeDefinition()
            } else {
                JewelTheme.lightThemeDefinition()
            }
            
            IntUiTheme(
                theme = theme,
                styling = ComponentStyling.provide()
            ) {
                EnhancedConversationView(
                    projectService = projectService,
                    contextProvider = contextProvider,
                    cliWrapper = cliWrapper
                )
            }
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        // 清理 Compose 资源
        composePanel.removeAll()
    }
}