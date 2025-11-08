package com.claudecodeplus.toolwindow

import com.claudecodeplus.bridge.FrontendBridge
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

            // 创建协程作用域
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            // 创建 FrontendBridge 用于 JCEF 通信
            val frontendBridge = FrontendBridge(project, browser, scope)
            logger.info("✅ FrontendBridge created")

            // 添加页面加载监听器
            browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain == true) {
                        logger.info("✅ Page loaded with status: $httpStatusCode")
                        logger.info("📄 Page URL: ${frame.url}")

                        // 注入 JCEF Bridge（页面加载完成后立即注入）
                        frontendBridge.injectBridgeScript()
                        logger.info("✅ JCEF Bridge injected")

                        // 注入调试脚本
                        val debugScript = """
                            (function() {
                                console.log('🔧 Debug script injected');
                                console.log('🌐 Server URL: $serverUrl');
                                console.log('🔌 Bridge Mode: JCEF (via FrontendBridge)');

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

            // 添加右键菜单：打开开发者工具
            val component = browser.component
            component.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                        val popup = javax.swing.JPopupMenu()
                        popup.add(javax.swing.JMenuItem("Open DevTools").apply {
                            addActionListener {
                                browser.openDevtools()
                                logger.info("🔧 DevTools opened")
                            }
                        })
                        popup.add(javax.swing.JMenuItem("Reload Page").apply {
                            addActionListener {
                                browser.cefBrowser.reload()
                                logger.info("🔄 Page reloaded")
                            }
                        })
                        popup.show(component, e.x, e.y)
                    }
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

            // 🚀 在加载页面前注入早期 JCEF 标志
            val earlyScript = """
                window.__jcefMode = true;
                window.__bridgeReady = false;
                console.log('✅ Early JCEF mode flag set');
            """.trimIndent()
            browser.cefBrowser.executeJavaScript(earlyScript, "about:blank", 0)
            logger.info("✅ Early JCEF flag injected")

            // 加载前端页面
            logger.info("📄 Loading frontend from: $serverUrl")
            browser.loadURL(serverUrl)

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
