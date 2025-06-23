package com.claudecodeplus.ui.jewel

import androidx.compose.ui.awt.ComposePanel
import com.claudecodeplus.sdk.ClaudeCliWrapper
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * Jewel 聊天面板 - Swing 包装器
 * 将 Compose UI 集成到 Swing 应用中
 * 基于 ClaudeCliWrapper 实现 AI 对话
 */
class JewelChatPanel(
    private val cliWrapper: ClaudeCliWrapper = ClaudeCliWrapper(),
    private val workingDirectory: String = System.getProperty("user.dir"),
    themeStyle: JewelThemeStyle = JewelThemeStyle.SYSTEM,
    isSystemDark: Boolean = true,
    themeConfig: JewelThemeConfig = JewelThemeConfig.DEFAULT
) : JPanel(BorderLayout()) {
    
    var themeStyle: JewelThemeStyle = themeStyle
        set(value) {
            if (field != value) {
                field = value
                updateContent()
            }
        }
    
    private var isSystemDark: Boolean = isSystemDark
        set(value) {
            if (field != value && themeStyle == JewelThemeStyle.SYSTEM) {
                field = value
                updateContent()
            }
        }
    
    var themeConfig: JewelThemeConfig = themeConfig
        set(value) {
            if (field != value) {
                field = value
                updateContent()
            }
        }
    
    private val composePanel = ComposePanel()
    
    init {
        add(composePanel, BorderLayout.CENTER)
        updateContent()
    }
    
    /**
     * 更新内容
     */
    fun updateContent() {
        composePanel.setContent {
            val actualTheme = JewelThemeStyle.getActualTheme(themeStyle, isSystemDark)
            
            val theme = when (actualTheme) {
                JewelThemeStyle.DARK, JewelThemeStyle.HIGH_CONTRAST_DARK -> {
                    JewelTheme.darkThemeDefinition()
                }
                JewelThemeStyle.LIGHT, JewelThemeStyle.HIGH_CONTRAST_LIGHT -> {
                    JewelTheme.lightThemeDefinition()
                }
                else -> JewelTheme.darkThemeDefinition() // 默认暗色
            }
            
            IntUiTheme(
                theme = theme,
                styling = ComponentStyling.provide()
            ) {
                JewelConversationView(
                    cliWrapper = cliWrapper,
                    workingDirectory = workingDirectory,
                    fileIndexService = null // TODO: 在插件中提供 IDEA 的实现
                )
            }
        }
    }
    
    /**
     * 获取 CLI Wrapper
     */
    fun getCliWrapper(): ClaudeCliWrapper = cliWrapper
    
    
    /**
     * 设置系统主题（仅在 themeStyle 为 SYSTEM 时生效）
     */
    fun setSystemTheme(isDark: Boolean) {
        isSystemDark = isDark
    }
    
    
    /**
     * 获取实际使用的主题
     */
    fun getActualTheme(): JewelThemeStyle = JewelThemeStyle.getActualTheme(themeStyle, isSystemDark)
    
    
}