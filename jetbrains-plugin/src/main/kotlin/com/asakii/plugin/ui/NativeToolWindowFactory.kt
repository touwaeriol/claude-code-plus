package com.asakii.plugin.ui

import com.asakii.server.HttpServerProjectService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.ShowSettingsUtil
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
import com.intellij.ui.jcef.JBCefApp
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
        val serverIndicatorLabel = createServerPortIndicator(project)
        val serverIndicatorAction = ComponentAction(serverIndicatorLabel)

        // 标题栏动作（会话控件按顺序置于右侧）
        val titleActions = mutableListOf<AnAction>(
            // Open in External Browser button
            object : AnAction(
                "Open in External Browser",
                "在外部浏览器中打开并使用 Chrome DevTools 调试",
                AllIcons.Xml.Browsers.Chrome
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    val url = httpService.serverUrl
                    if (url != null) {
                        openInBrowser(project, url)
                    }
                }
            },
            // Settings button
            object : AnAction(
                "Settings",
                "Open Claude Code Settings",
                AllIcons.General.Settings
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    // 直接打开 Claude Code 配置页（跳过父级欢迎页）
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, "com.asakii.settings.claudecode")
                }
            }
        )

        if (serverUrl.isNullOrBlank()) {
            logger.warn("⚠️ HTTP Server is not ready, showing placeholder panel")
            val placeholder = createPlaceholderComponent()
            val content = contentFactory.createContent(placeholder, "", false)
            toolWindow.contentManager.addContent(content)
            toolWindowEx?.setTitleActions(titleActions)
            return
        }

        // 使用 Builder 模式显式禁用 OSR，避免 IDEA 2025.x 中上下文菜单和 DevTools 被禁用
        val browser = JBCefBrowser.createBuilder()
            .setOffScreenRendering(false)
            .setEnableOpenDevToolsMenuItem(true)
            .build()

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
            "$serverUrl&ide=true&scrollMultiplier=2.5$themeParam"
        } else {
            "$serverUrl?ide=true&scrollMultiplier=2.5$themeParam"
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

        // 在标题栏最左边添加刷新按钮
        val refreshAction = object : AnAction(
            "Refresh",
            "Restart backend server and reload frontend",
            AllIcons.Actions.Refresh
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                logger.info("🔄 Restarting server and refreshing frontend...")

                // 重启服务器以重新加载前端资源
                val newUrl = httpService.restart()

                if (newUrl != null) {
                    // 更新 URL 指示器
                    serverIndicatorLabel.text = "🌐 $newUrl"

                    // 构建新的 URL 参数
                    val newThemeParam = try {
                        val theme = httpService.jetbrainsApi?.theme?.get()
                        if (theme != null) {
                            val themeJson = Json.encodeToString(theme)
                            val encoded = URLEncoder.encode(themeJson, StandardCharsets.UTF_8.toString())
                            "&initialTheme=$encoded"
                        } else ""
                    } catch (ex: Exception) {
                        logger.warn("⚠️ Failed to encode initial theme: ${ex.message}")
                        ""
                    }

                    val newTargetUrl = if (newUrl.contains("?")) {
                        "$newUrl&ide=true&scrollMultiplier=2.5$newThemeParam"
                    } else {
                        "$newUrl?ide=true&scrollMultiplier=2.5$newThemeParam"
                    }

                    logger.info("🔗 Loading new URL: ${newTargetUrl.take(100)}...")
                    browser.loadURL(newTargetUrl)
                } else {
                    // 如果重启失败，仅刷新页面
                    logger.warn("⚠️ Server restart failed, just reloading page")
                    browser.cefBrowser.reloadIgnoreCache()
                }
            }
        }
        titleActions.add(0, refreshAction)

        toolWindowEx?.setTitleActions(titleActions)

        // 三个点菜单中保留 DevTools 选项
        val gearActions = com.intellij.openapi.actionSystem.DefaultActionGroup().apply {
            add(object : AnAction(
                "Open DevTools",
                "打开浏览器开发者工具 (调试 JCEF)",
                com.intellij.icons.AllIcons.Toolwindows.ToolWindowDebugger
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    openDevToolsInDialog(project, browser)
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
        val initialUrl = httpService.serverUrl ?: "未启动"

        // 使用 IDEA 主题的链接颜色
        val linkColor = JBUI.CurrentTheme.Link.Foreground.ENABLED
        val linkHoverColor = JBUI.CurrentTheme.Link.Foreground.HOVERED

        val label = JBLabel("🌐 $initialUrl")
        label.font = JBUI.Fonts.smallFont()
        label.foreground = linkColor
        label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        label.toolTipText = "<html>HTTP 服务地址<br>单击：复制地址<br>双击：在浏览器中打开</html>"

        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                // 动态获取最新的 serverUrl
                val currentUrl = httpService.serverUrl ?: "未启动"
                if (e.clickCount == 1) {
                    CopyPasteManager.getInstance().setContents(StringSelection(currentUrl))
                    JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder("已复制：$currentUrl", MessageType.INFO, null)
                        .setFadeoutTime(2000)
                        .createBalloon()
                        .show(RelativePoint.getCenterOf(label), Balloon.Position.below)
                } else if (e.clickCount == 2) {
                    openInBrowser(project, currentUrl)
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

    /**
     * 打开 DevTools 窗口
     * 在 Windows 上 JCEF out-of-process 模式可能导致 DevTools 无法打开
     * 参考: https://platform.jetbrains.com/t/tests-using-jcef-on-windows-failed-with-2025-1/1493
     */
    private fun openDevToolsInDialog(project: Project, browser: JBCefBrowser) {
        try {
            // 使用 JBCefBrowser 封装的 openDevtools 方法（跨版本兼容）
            browser.openDevtools()
            logger.info("✅ DevTools window opened via JBCefBrowser.openDevtools()")
        } catch (e: Exception) {
            logger.warn("⚠️ JBCefBrowser.openDevtools() failed: ${e.message}")
            // 提示用户在外部浏览器中打开
            val serverUrl = HttpServerProjectService.getInstance(project).serverUrl
            if (serverUrl != null) {
                com.intellij.openapi.ui.Messages.showInfoMessage(
                    project,
                    "DevTools 无法在 IDE 内打开 (Windows JCEF 兼容性问题)。\n\n" +
                    "请在外部浏览器中打开以下地址，使用浏览器的 DevTools (F12)：\n$serverUrl",
                    "DevTools"
                )
            }
        }
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "Claude Code Plus"
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
