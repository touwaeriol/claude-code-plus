package com.asakii.plugin.ui

import com.asakii.plugin.bridge.IdeSessionBridge
import com.asakii.plugin.ui.title.HistorySessionAction
import com.asakii.plugin.ui.title.NewSessionAction
import com.asakii.plugin.ui.title.SessionTabsAction
import com.asakii.server.HttpServerProjectService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.IOException
import javax.swing.JComponent

/**
 * ToolWindow 工厂：IDE 模式下加载 Vue (JCEF)，并将会话管理迁移到标题栏。
 */
class NativeToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        private val logger = Logger.getInstance(NativeToolWindowFactory::class.java)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger.info("🚀 Creating Claude ToolWindow (JCEF)")
        val toolWindowEx = toolWindow as? ToolWindowEx
        val contentFactory = ContentFactory.getInstance()
        val httpService = HttpServerProjectService.getInstance(project)
        val serverUrl = httpService.serverUrl
        val serverIndicatorAction = ComponentAction(createServerPortIndicator(project))

        // 将 HTTP URL 指示器放在标题最左侧（紧挨 ToolWindow 标题）
        toolWindowEx?.setTabActions(serverIndicatorAction)

        // 标题栏动作（会话控件按顺序置于右侧）
        val titleActions = mutableListOf<AnAction>()

        if (serverUrl.isNullOrBlank()) {
            logger.warn("⚠️ HTTP Server is not ready, showing placeholder panel")
            val placeholder = createPlaceholderComponent()
            val content = contentFactory.createContent(placeholder, "", false)
            toolWindow.contentManager.addContent(content)
            toolWindowEx?.setTitleActions(titleActions)
            return
        }

        val browser = JBCefBrowser()
        val sessionBridge = IdeSessionBridge(browser, project)
        val targetUrl = if (serverUrl.contains("?")) {
            "$serverUrl&ide=true"
        } else {
            "$serverUrl?ide=true"
        }
        browser.loadURL(targetUrl)

        val content = contentFactory.createContent(browser.component, "", false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
        Disposer.register(content, browser)
        Disposer.register(content, sessionBridge)

        // 会话标签动作（下拉选择器）
        titleActions.add(SessionTabsAction(sessionBridge))

        // 历史会话入口
        titleActions.add(HistorySessionAction(sessionBridge))

        // 新建会话入口
        titleActions.add(NewSessionAction(sessionBridge))

        toolWindowEx?.setTitleActions(titleActions)
    }

    /**
     * 将 Swing 组件包装为 ToolWindow 标题栏可用的 Action。
     */
    private class ComponentAction(
        private val component: JComponent
    ) : AnAction(), CustomComponentAction {
        override fun actionPerformed(e: AnActionEvent) = Unit

        override fun createCustomComponent(
            presentation: com.intellij.openapi.actionSystem.Presentation,
            place: String
        ): JComponent = component
    }

    private fun createPlaceholderComponent(): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.border = JBUI.Borders.empty(32)
        val label = JBLabel("Claude HTTP 服务启动中，请稍候...").apply {
            foreground = JBColor(0x6B7280, 0x9CA3AF)
        }
        panel.add(label, BorderLayout.CENTER)
        return panel
    }

    /**
     * 创建服务器端口指示器（单击复制并气泡提示，双击打开浏览器）。
     */
    private fun createServerPortIndicator(project: Project): JBLabel {
        val httpService = HttpServerProjectService.getInstance(project)
        val serverUrl = httpService.serverUrl ?: "未启动"

        val label = JBLabel("🌐 $serverUrl")
        label.font = JBUI.Fonts.smallFont()
        label.foreground = JBColor(Color(0x2196F3), Color(0x42A5F5))
        label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        label.toolTipText = "<html>HTTP 服务地址<br>单击：复制地址<br>双击：在浏览器中打开</html>"

        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    CopyPasteManager.getInstance().setContents(StringSelection(serverUrl))
                    JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder("已复制：$serverUrl", MessageType.INFO, null)
                        .setFadeoutTime(2000)
                        .createBalloon()
                        .show(RelativePoint.getCenterOf(label), Balloon.Position.below)
                } else if (e.clickCount == 2) {
                    openInBrowser(project, serverUrl)
                }
            }

            override fun mouseEntered(e: MouseEvent) {
                label.foreground = JBColor(Color(0x1976D2), Color(0x64B5F6))
            }

            override fun mouseExited(e: MouseEvent) {
                label.foreground = JBColor(Color(0x2196F3), Color(0x42A5F5))
            }
        })

        return label
    }

    /**
     * 在浏览器中打开URL。
     */
    private fun openInBrowser(project: Project, url: String) {
        try {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(java.net.URI(url))
            } else {
                logger.warn("Browser not supported to open: $url")
            }
        } catch (e: IOException) {
            logger.warn("Failed to open browser: ${e.message}", e)
        }
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "Claude AI"
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
