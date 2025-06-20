package com.claudecodeplus.toolwindow

import com.claudecodeplus.sdk.SimpleNodeServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.*

/**
 * 测试 Node 服务的工具窗口
 */
class TestNodeServiceToolWindow : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TestNodeServicePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "Node Service Test", false)
        toolWindow.contentManager.addContent(content)
    }
    
    private class TestNodeServicePanel(private val project: Project) : JPanel(BorderLayout()) {
        private val logArea = JTextArea()
        private val serviceManager = SimpleNodeServiceManager()
        private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS")
        
        init {
            createUI()
        }
        
        private fun createUI() {
            // 顶部按钮面板
            val buttonPanel = JPanel().apply {
                add(JButton("Start Service").apply {
                    addActionListener { startService() }
                })
                add(JButton("Stop Service").apply {
                    addActionListener { stopService() }
                })
                add(JButton("Check Status").apply {
                    addActionListener { checkStatus() }
                })
                add(JButton("Clear Log").apply {
                    addActionListener { logArea.text = "" }
                })
            }
            
            // 日志区域
            logArea.isEditable = false
            logArea.font = logArea.font.deriveFont(12f)
            
            add(buttonPanel, BorderLayout.NORTH)
            add(JBScrollPane(logArea), BorderLayout.CENTER)
            
            log("Test Node Service Panel initialized")
        }
        
        private fun startService() {
            log("Starting Node service...")
            
            SwingUtilities.invokeLater {
                serviceManager.startService(project).thenAccept { socketPath ->
                    SwingUtilities.invokeLater {
                        if (socketPath != null) {
                            log("✅ Service started successfully!")
                            log("Socket path: $socketPath")
                        } else {
                            log("❌ Failed to start service")
                        }
                    }
                }.exceptionally { e ->
                    SwingUtilities.invokeLater {
                        log("❌ Error: ${e.message}")
                        e.printStackTrace()
                    }
                    null
                }
            }
        }
        
        private fun stopService() {
            log("Stopping Node service...")
            serviceManager.stopService()
            log("Service stopped")
        }
        
        private fun checkStatus() {
            val isRunning = serviceManager.isServiceRunning()
            val socketPath = serviceManager.getSocketPath()
            
            log("=== Service Status ===")
            log("Running: $isRunning")
            log("Socket path: ${socketPath ?: "N/A"}")
        }
        
        private fun log(message: String) {
            val timestamp = dateFormat.format(Date())
            logArea.append("[$timestamp] $message\n")
            logArea.caretPosition = logArea.document.length
        }
    }
}