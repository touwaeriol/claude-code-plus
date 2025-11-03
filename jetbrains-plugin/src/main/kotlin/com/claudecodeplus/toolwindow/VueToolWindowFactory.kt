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
     * 读取资源文件内容
     */
    private fun readResource(path: String): String? {
        return try {
            javaClass.getResourceAsStream(path)?.bufferedReader()?.readText()
        } catch (e: Exception) {
            logger.warning("⚠️ Failed to read resource: $path - ${e.message}")
            null
        }
    }

    /**
     * 检查 dev server 是否可用
     */
    private fun isDevServerAvailable(): Boolean {
        return try {
            val url = java.net.URL("http://localhost:5173")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            connection.connect()
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 加载前端页面
     */
    private fun loadFrontend(browser: JBCefBrowser) {
        // 优先尝试从 dev server 加载（开发模式）
        if (isDevServerAvailable()) {
            val devServerUrl = "http://localhost:5173"
            logger.info("🔧 Development mode: loading from $devServerUrl")
            browser.loadURL(devServerUrl)
            logger.info("✅ Frontend loaded from dev server")
        } else {
            // 开发模式: 从 Vite dev server 加载
            val devServerUrl = "http://localhost:5173"
            logger.info("🔧 Development mode: loading from $devServerUrl")
            logger.info("⚠️ Make sure to run 'npm run dev' in the frontend directory")

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
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>🚧 开发模式</h1>
                        <div class="status">
                            <p><strong>前端资源未找到</strong></p>
                            <p>请在开发时运行 Vite dev server:</p>
                        </div>
                        <div class="command">
                            cd frontend<br>
                            npm install<br>
                            npm run dev
                        </div>
                        <p>然后刷新此窗口,或者先构建前端:</p>
                        <div class="command">
                            cd frontend<br>
                            npm run build
                        </div>
                        <a href="#" onclick="location.reload(); return false;" class="button">
                            🔄 刷新
                        </a>
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
