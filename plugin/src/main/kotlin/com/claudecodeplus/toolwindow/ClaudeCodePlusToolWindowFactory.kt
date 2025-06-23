package com.claudecodeplus.toolwindow

import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.jewel.JewelChatPanel
import com.claudecodeplus.ui.jewel.JewelThemeStyle
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import javax.swing.UIManager

/**
 * Claude Code Plus 工具窗口工厂
 * 基于 Jewel UI 和 ClaudeCliWrapper 实现
 */
class ClaudeCodePlusToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 创建 CLI wrapper
        val cliWrapper = ClaudeCliWrapper()
        
        // 使用 IDEA 接口获取当前打开的项目路径作为工作目录
        val workingDirectory = project.basePath ?: System.getProperty("user.dir")
        println("[ClaudeCodePlus] Using working directory: $workingDirectory")
        
        // 检测 IDE 当前主题
        val isSystemDark = isDarkTheme()
        
        // 创建基于 Jewel 的聊天面板
        val chatPanel = JewelChatPanel(
            cliWrapper = cliWrapper,
            workingDirectory = workingDirectory,
            themeStyle = JewelThemeStyle.SYSTEM,  // 默认跟随系统
            isSystemDark = isSystemDark
        )
        
        // 创建内容
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(
            chatPanel,
            "Claude Assistant",
            false
        )
        
        toolWindow.contentManager.addContent(content)
        
        // 监听主题变化
        UIManager.addPropertyChangeListener { evt ->
            if (evt.propertyName == "lookAndFeel") {
                chatPanel.setSystemTheme(isDarkTheme())
            }
        }
    }
    
    /**
     * 检测是否为暗色主题
     */
    private fun isDarkTheme(): Boolean {
        return UIUtil.isUnderDarcula()
    }
}