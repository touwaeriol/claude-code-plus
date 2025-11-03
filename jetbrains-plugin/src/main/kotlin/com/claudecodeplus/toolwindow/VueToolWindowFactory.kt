package com.claudecodeplus.toolwindow

import com.claudecodeplus.bridge.FrontendBridge
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.awt.BorderLayout
import java.util.logging.Logger
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Vue 工具窗口工厂
 * 创建基于 JCEF + Vue 的工具窗口
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
            // 创建 JCEF 浏览器
            val browser = JBCefBrowser()
            logger.info("✅ JCEF browser created")

            // 创建协程作用域
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            // 创建通信桥接
            val bridge = FrontendBridge(project, browser, scope)
            logger.info("✅ Frontend bridge created")

            // 加载前端页面
            loadFrontend(browser)

            // 添加到工具窗口
            val content = ContentFactory.getInstance()
                .createContent(browser.component, "", false)
            toolWindow.contentManager.addContent(content)

            logger.info("✅ Vue tool window created successfully")
        } catch (e: Exception) {
            logger.severe("❌ Failed to create Vue tool window: ${e.message}")
            e.printStackTrace()

            // 显示错误信息
            val errorPanel = JPanel(BorderLayout())
            errorPanel.add(JLabel("Failed to create tool window: ${e.message}"), BorderLayout.CENTER)
            val content = ContentFactory.getInstance().createContent(errorPanel, "", false)
            toolWindow.contentManager.addContent(content)
        }
    }

    /**
     * 加载前端页面
     */
    private fun loadFrontend(browser: JBCefBrowser) {
        val devServerUrl = "http://localhost:5173"

        // 直接尝试加载 dev server，让浏览器自己处理连接失败
        // 这样可以避免初始化时的检查延迟
        logger.info("🔧 Attempting to load from dev server: $devServerUrl")

        try {
            browser.loadURL(devServerUrl)
            logger.info("✅ Loading URL: $devServerUrl")
        } catch (e: Exception) {
            logger.warning("⚠️ Failed to load dev server, showing fallback page: ${e.message}")

            browser.loadHTML("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Claude Code Plus</title>
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                            padding: 20px;
                            background: #f5f5f5;
                        }
                        .container {
                            max-width: 600px;
                            margin: 0 auto;
                            background: white;
                            padding: 30px;
                            border-radius: 8px;
                            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                        }
                        h1 { color: #2c3e50; }
                        .status {
                            padding: 15px;
                            background: #fff3cd;
                            border: 1px solid #ffc107;
                            border-radius: 4px;
                            margin: 20px 0;
                        }
                        .command {
                            background: #282c34;
                            color: #abb2bf;
                            padding: 15px;
                            border-radius: 4px;
                            font-family: monospace;
                            margin: 10px 0;
                        }
                        .button {
                            display: inline-block;
                            padding: 10px 20px;
                            background: #42b983;
                            color: white;
                            text-decoration: none;
                            border-radius: 4px;
                            margin-top: 20px;
                            cursor: pointer;
                            border: none;
                            font-size: 14px;
                        }
                        .button:hover {
                            background: #3aa876;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>🚧 开发模式</h1>
                        <div class="status">
                            <p><strong>Dev server 未检测到</strong></p>
                            <p>请确保 Vite dev server 正在运行:</p>
                        </div>
                        <div class="command">
                            cd frontend<br>
                            npm install<br>
                            npm run dev
                        </div>
                        <p>启动后点击下方按钮加载前端:</p>
                        <button onclick="window.location.href='http://localhost:5173'" class="button">
                            🔄 加载开发服务器
                        </button>
                        <p style="margin-top: 20px; color: #666; font-size: 12px;">
                            或者构建生产版本: <code>cd frontend && npm run build</code>
                        </p>
                    </div>
                </body>
                </html>
            """.trimIndent())
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }
}
