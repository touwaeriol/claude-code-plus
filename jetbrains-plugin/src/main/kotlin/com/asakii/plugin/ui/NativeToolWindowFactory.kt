package com.asakii.plugin.ui

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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.swing.JComponent

/**
 * ToolWindow 工厂：IDE 模式下使用 JBCefBrowser 加载 Vue 前端，
 * 通过 RSocket 与后端通信，并将会话管理迁移到标题栏。
 */
class NativeToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        private val logger = Logger.getInstance(NativeToolWindowFactory::class.java)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger.info("🚀 Creating Claude ToolWindow")
        val toolWindowEx = toolWindow as? ToolWindowEx
        val contentFactory = ContentFactory.getInstance()
        val httpService = HttpServerProjectService.getInstance(project)
        val serverUrl = httpService.serverUrl
        val serverIndicatorAction = ComponentAction(createServerPortIndicator(project))

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

        // 构建 URL 参数：ide=true + 初始主题
        val jetbrainsApi = httpService.jetbrainsApi
        val themeParam = try {
            val theme = jetbrainsApi?.theme?.get()
            if (theme != null) {
                val themeJson = Json.encodeToString(theme)
                val encoded = URLEncoder.encode(themeJson, StandardCharsets.UTF_8.toString())
                "&initialTheme=$encoded"
            } else ""
        } catch (e: Exception) {
            logger.warn("⚠️ Failed to encode initial theme: ${e.message}")
            ""
        }

        val targetUrl = if (serverUrl.contains("?")) {
            "$serverUrl&ide=true$themeParam"
        } else {
            "$serverUrl?ide=true$themeParam"
        }
        logger.info("🔗 Loading URL with initial theme: ${targetUrl.take(100)}...")
        browser.loadURL(targetUrl)

        // 将浏览器组件包装在 JBPanel 中，确保正确填充空间
        val browserPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(browser.component, BorderLayout.CENTER)
        }

        val content = contentFactory.createContent(browserPanel, "", false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
        Disposer.register(content, browser)

        // 左侧 Tab Actions：HTTP 指示器
        toolWindowEx?.setTabActions(serverIndicatorAction)

        toolWindowEx?.setTitleActions(titleActions)

        // 三个点菜单中添加 DevTools
        val gearActions = com.intellij.openapi.actionSystem.DefaultActionGroup().apply {
            add(object : AnAction(
                "Open DevTools",
                "打开浏览器开发者工具 (调试 JCEF)",
                com.intellij.icons.AllIcons.Toolwindows.ToolWindowDebugger
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    browser.openDevtools()
                }
            })
        }
        toolWindowEx?.setAdditionalGearActions(gearActions)
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

        // 使用 IDEA 主题的链接颜色
        val linkColor = JBUI.CurrentTheme.Link.Foreground.ENABLED
        val linkHoverColor = JBUI.CurrentTheme.Link.Foreground.HOVERED

        val label = JBLabel("🌐 $serverUrl")
        label.font = JBUI.Fonts.smallFont()
        label.foreground = linkColor
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
                label.foreground = linkHoverColor
            }

            override fun mouseExited(e: MouseEvent) {
                label.foreground = linkColor
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
        toolWindow.stripeTitle = "Claude Code Plus"
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
