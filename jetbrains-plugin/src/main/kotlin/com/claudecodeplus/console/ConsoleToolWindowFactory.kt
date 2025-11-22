package com.claudecodeplus.console

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.util.logging.Logger
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Console 工具窗口工厂
 *
 * 注意：此工厂只负责初始化窗口框架
 * 实际的 DevTools 内容由 VueToolWindowFactory 的右键菜单动态创建
 * 这样可以确保每次打开都使用最新的浏览器引用
 */
class ConsoleToolWindowFactory : ToolWindowFactory, DumbAware {
    private val logger = Logger.getLogger(javaClass.name)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger.info("🔧 Initializing Console tool window...")

        // 显示提示信息
        // 实际内容将由右键菜单创建
        showInfoPanel(toolWindow, "请在 Claude Code Plus 主窗口右键选择 '打开 Console'")

        logger.info("✅ Console tool window initialized")
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }

    /**
     * 显示提示信息面板
     */
    private fun showInfoPanel(toolWindow: ToolWindow, message: String) {
        val panel = JPanel(BorderLayout())
        panel.add(
            JLabel("<html><center><h3>ℹ️ 提示</h3><p>$message</p></center></html>"),
            BorderLayout.CENTER
        )
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
