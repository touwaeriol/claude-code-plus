package com.claudecodeplus.plugin.ui

import com.intellij.openapi.diagnostic.Logger
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * 简单的 Compose 包装器
 * 避免直接调用 ComposePanel，而是通过 toolwindow 模块的工厂方法创建
 */
object SimpleComposeWrapper {
    private val logger = Logger.getInstance(SimpleComposeWrapper::class.java)
    
    /**
     * 创建 Compose UI 面板
     * 通过反射调用 toolwindow 模块中的工厂方法
     */
    fun createComposePanel(
        cliWrapper: Any,
        workingDirectory: String,
        fileIndexService: Any?,
        projectService: Any?,
        sessionManager: Any
    ): JComponent {
        return try {
            // 尝试通过反射调用 toolwindow 模块中的独立启动方法
            val mainClass = Class.forName("com.claudecodeplus.MainKt")
            val createUIMethod = mainClass.getDeclaredMethod(
                "createStandaloneUI",
                Any::class.java,  // cliWrapper
                String::class.java,  // workingDirectory  
                Any::class.java,  // fileIndexService
                Any::class.java,  // projectService
                Any::class.java   // sessionManager
            )
            
            logger.info("Found createStandaloneUI method, invoking...")
            createUIMethod.invoke(null, cliWrapper, workingDirectory, fileIndexService, projectService, sessionManager) as JComponent
            
        } catch (e: Exception) {
            logger.warn("Failed to create UI via reflection: ${e.message}")
            
            // 如果反射失败，创建一个简单的面板
            val panel = JPanel(BorderLayout())
            panel.add(javax.swing.JLabel(
                "<html><center>" +
                "<h2>Claude Code Plus</h2>" +
                "<p>UI 初始化失败</p>" +
                "<p style='color:gray'>错误: ${e.message}</p>" +
                "</center></html>"
            ), BorderLayout.CENTER)
            panel
        }
    }
}