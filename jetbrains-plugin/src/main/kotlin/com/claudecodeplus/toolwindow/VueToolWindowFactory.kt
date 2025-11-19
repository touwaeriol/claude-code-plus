package com.claudecodeplus.toolwindow


import com.claudecodeplus.server.HttpServerProjectService
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.logging.Logger
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Vue 工具窗口工厂（基于 Ktor + SSE）
 * 创建基于 JCEF + Vue 的工具窗口，使用统一的 HTTP API 架构
 */
class VueToolWindowFactory : ToolWindowFactory, DumbAware {
    private val logger = Logger.getLogger(javaClass.name)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger.info("🚀 Creating Vue tool window...")

        // 检查 JCEF 是否可用
        if (!JBCefApp.isSupported()) {
            logger.severe("❌ JCEF is not supported")
            val panel = JPanel(BorderLayout())
            panel.add(JLabel("JCEF is not supported in this environment"), BorderLayout.CENTER)
            val content = ContentFactory.getInstance().createContent(panel, "", false)
            toolWindow.contentManager.addContent(content)
            return
        }

        try {
            // 获取已启动的 HTTP 服务器
            val httpServerService = HttpServerProjectService.getInstance(project)
            val serverUrl = httpServerService.serverUrl

            if (serverUrl == null) {
                logger.severe("❌ HTTP Server not started")
                val errorPanel = JPanel(BorderLayout())
                errorPanel.add(
                    JLabel("<html>HTTP Server failed to start.<br>Please check the logs for details.</html>"),
                    BorderLayout.CENTER
                )
                val content = ContentFactory.getInstance().createContent(errorPanel, "", false)
                toolWindow.contentManager.addContent(content)
                return
            }

            logger.info("✅ Using HTTP Server at: $serverUrl")

            // 创建 JCEF 浏览器
            val browser = JBCefBrowser()
            logger.info("✅ JCEF browser created")

            // 🔧 启用开发者工具
            browser.jbCefClient.setProperty("dev.tools", true)
            logger.info("🔧 JCEF developer tools enabled")
            
            // 📝 添加控制台消息处理器（捕获 JavaScript 日志）
            browser.jbCefClient.addDisplayHandler(object : org.cef.handler.CefDisplayHandlerAdapter() {
                override fun onConsoleMessage(
                    browser: org.cef.browser.CefBrowser?,
                    level: org.cef.CefSettings.LogSeverity?,
                    message: String?,
                    source: String?,
                    line: Int
                ): Boolean {
                    val levelStr = when (level) {
                        org.cef.CefSettings.LogSeverity.LOGSEVERITY_ERROR -> "❌ ERROR"
                        org.cef.CefSettings.LogSeverity.LOGSEVERITY_WARNING -> "⚠️ WARN"
                        org.cef.CefSettings.LogSeverity.LOGSEVERITY_INFO -> "ℹ️ INFO"
                        else -> "🔍 LOG"
                    }
                    logger.info("$levelStr [JS Console] $message (${source}:${line})")
                    return false // 让 CEF 也处理
                }
            }, browser.cefBrowser)

            // 添加页面加载监听器
            browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain == true) {
                        logger.info("✅ Page loaded with status: $httpStatusCode")
                        logger.info("✅ JCEF communication is now handled by the HTTP/WebSocket server.")
                        logger.info("✅ JCEF Bridge injected")

                        // 注入调试脚本（环境变量已在 HTML 中注入）
                        val debugScript = """
                            (function() {
                                console.log('🔧 Debug script injected');
                                console.log('🌐 Server URL:', window.__serverUrl || 'Not injected');
                                console.log('🔌 Running in IDEA Plugin Mode');

                                // 捕获所有未处理的错误
                                window.addEventListener('error', function(e) {
                                    console.error('❌ Global error:', e.message, e.filename, e.lineno, e.colno);
                                });

                                // 捕获 Promise 错误
                                window.addEventListener('unhandledrejection', function(e) {
                                    console.error('❌ Unhandled promise rejection:', e.reason);
                                });

                                // 检查 #app 元素
                                setTimeout(function() {
                                    const app = document.getElementById('app');
                                    if (app) {
                                        console.log('✅ #app found');
                                        if (app.innerHTML.length === 0) {
                                            console.warn('⚠️ #app is empty! Vue may not have mounted.');
                                        } else {
                                            console.log('✅ Vue app mounted successfully');
                                        }
                                    } else {
                                        console.error('❌ #app element not found!');
                                    }
                                }, 1000);
                            })();
                        """.trimIndent()

                        cefBrowser?.executeJavaScript(debugScript, cefBrowser.url, 0)
                    }
                }
            }, browser.cefBrowser)

            // 🔧 使用 CefContextMenuHandler 禁用默认右键菜单并显示自定义菜单
            browser.jbCefClient.addContextMenuHandler(object : org.cef.handler.CefContextMenuHandlerAdapter() {
                override fun onBeforeContextMenu(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    params: org.cef.callback.CefContextMenuParams?,
                    model: org.cef.callback.CefMenuModel?
                ) {
                    // 清空默认菜单项
                    model?.clear()
                    logger.info("🔧 Default context menu cleared")
                }

                override fun onContextMenuCommand(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    params: org.cef.callback.CefContextMenuParams?,
                    commandId: Int,
                    eventFlags: Int
                ): Boolean {
                    // 返回 true 表示已处理，不显示默认菜单
                    return true
                }

                override fun onContextMenuDismissed(cefBrowser: CefBrowser?, frame: CefFrame?) {
                    // 菜单关闭时的回调
                }
            }, browser.cefBrowser)

            // 添加 Swing 层面的右键菜单监听器
            val component = browser.component
            component.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mousePressed(e: java.awt.event.MouseEvent) {
                    if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                        showCustomContextMenu(e)
                    }
                }

                override fun mouseReleased(e: java.awt.event.MouseEvent) {
                    if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                        showCustomContextMenu(e)
                    }
                }

                private fun showCustomContextMenu(e: java.awt.event.MouseEvent) {
                    val popup = javax.swing.JPopupMenu()

                    // 打开 Console 窗口
                    popup.add(javax.swing.JMenuItem("打开 Console").apply {
                        addActionListener {
                            try {
                                // 获取 Console Tool Window
                                val toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                                val consoleWindow = toolWindowManager.getToolWindow("Claude Console")

                                if (consoleWindow == null) {
                                    logger.warning("⚠️ Console window not found")
                                    return@addActionListener
                                }

                                // 清空旧内容（关键：确保每次都是新的 DevTools）
                                consoleWindow.contentManager.removeAllContents(true)
                                logger.info("✅ Old console content removed")

                                // 获取 DevTools 的 CefBrowser
                                val devToolsCefBrowser = browser.cefBrowser.getDevTools()
                                logger.info("✅ DevTools CefBrowser obtained")

                                // 创建 JBCefBrowser 包装 DevTools
                                val devToolsBrowser = com.intellij.ui.jcef.JBCefBrowser.createBuilder()
                                    .setClient(browser.jbCefClient)
                                    .setCefBrowser(devToolsCefBrowser)
                                    .setUrl("about:blank")
                                    .build()
                                logger.info("✅ DevTools browser created")

                                // 创建面板并添加 DevTools 组件
                                val panel = javax.swing.JPanel(java.awt.BorderLayout())
                                panel.add(devToolsBrowser.component, java.awt.BorderLayout.CENTER)

                                // 创建内容
                                val content = com.intellij.ui.content.ContentFactory.getInstance()
                                    .createContent(panel, "", false)

                                // 注册资源清理
                                com.intellij.openapi.util.Disposer.register(content, devToolsBrowser)
                                logger.info("✅ Disposer registered")

                                // 添加内容到窗口
                                consoleWindow.contentManager.addContent(content)

                                // 显示窗口
                                consoleWindow.show()
                                logger.info("✅ Console window opened with fresh DevTools")

                            } catch (ex: Exception) {
                                logger.severe("❌ Failed to open console: ${ex.message}")
                                ex.printStackTrace()
                            }
                        }
                    })

                    // 刷新页面
                    popup.add(javax.swing.JMenuItem("刷新页面").apply {
                        addActionListener {
                            browser.cefBrowser.reload()
                            logger.info("🔄 Page reloaded")
                        }
                    })

                    popup.show(component, e.x, e.y)
                }
            })
            
            // 创建包含地址栏和浏览器的面板
            val mainPanel = JPanel(BorderLayout()).apply {
                // 创建顶部地址栏面板
                val urlBarPanel = JPanel(BorderLayout()).apply {
                    border = BorderFactory.createEmptyBorder(5, 8, 5, 8)

                    // URL 标签
                    val urlLabel = JLabel("URL: ").apply {
                        border = BorderFactory.createEmptyBorder(0, 0, 0, 5)
                    }

                    // URL 文本框
                    val urlTextField = JTextField(serverUrl).apply {
                        isEditable = false
                        preferredSize = Dimension(preferredSize.width, 28)
                        border = BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(java.awt.Color(0x80, 0x80, 0x80)),
                            BorderFactory.createEmptyBorder(4, 8, 4, 8)
                        )
                    }

                    add(urlLabel, BorderLayout.WEST)
                    add(urlTextField, BorderLayout.CENTER)
                }

                // 添加组件
                add(urlBarPanel, BorderLayout.NORTH)
                add(browser.component, BorderLayout.CENTER)
            }

            // 添加到工具窗口
            val content = ContentFactory.getInstance()
                .createContent(mainPanel, "", false)
            toolWindow.contentManager.addContent(content)

            // 注册清理逻辑
            Disposer.register(content) {
                logger.info("🛑 Cleaning up tool window resources")
                // 不需要停止服务器，它会在项目关闭时自动停止
                browser.dispose()
            }

            // 加载前端页面（带上 ide=true 参数，告诉后端这是 IDEA 插件环境）
            val ideUrl = "$serverUrl?ide=true"
            logger.info("📄 Loading frontend from: $ideUrl")
            browser.loadURL(ideUrl)

            logger.info("✅ Vue tool window created successfully")
            logger.info("🔍 Users can also access at: $serverUrl")

        } catch (e: Exception) {
            logger.severe("❌ Failed to create Vue tool window: ${e.message}")
            e.printStackTrace()

            // 显示错误信息
            val errorPanel = JPanel(BorderLayout())
            errorPanel.add(
                JLabel("<html>Failed to create tool window:<br>${e.message}</html>"),
                BorderLayout.CENTER
            )
            val content = ContentFactory.getInstance().createContent(errorPanel, "", false)
            toolWindow.contentManager.addContent(content)
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }
}
