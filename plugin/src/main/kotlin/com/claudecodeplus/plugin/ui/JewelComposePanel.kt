package com.claudecodeplus.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.diagnostic.Logger
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JLabel
import java.awt.BorderLayout

/**
 * 创建一个兼容 IntelliJ 的 Compose 面板
 * 处理不同版本的 ComposePanel.setContent 方法签名
 */
fun JewelComposePanel(
    config: ComposePanel.() -> Unit = {},
    content: @Composable () -> Unit
): JComponent {
    val logger = Logger.getInstance("JewelComposePanel")
    
    return try {
        val composePanel = ComposePanel()
        
        // 应用配置
        composePanel.config()
        
        // 使用 ComposeContentSetter 设置内容
        val success = ComposeContentSetter.setContent(composePanel, content)
        
        if (success) {
            logger.info("Successfully created JewelComposePanel")
            composePanel
        } else {
            throw RuntimeException("Failed to set content on ComposePanel")
        }
    } catch (e: Exception) {
        logger.error("Failed to create JewelComposePanel", e)
        
        // 创建错误面板
        val errorPanel = JPanel(BorderLayout())
        val errorLabel = JLabel(
            "<html><center>" +
            "<h3>Compose UI 初始化失败</h3>" +
            "<p>错误: ${e.message}</p>" +
            "</center></html>"
        )
        errorPanel.add(errorLabel, BorderLayout.CENTER)
        errorPanel
    }
}

/**
 * 创建一个专门用于工具窗口的 Compose 面板
 */
fun JewelToolWindowComposePanel(
    config: ComposePanel.() -> Unit = {},
    content: @Composable () -> Unit
): JComponent = JewelComposePanel(
    config = {
        config()
        // 工具窗口特定的配置
        isFocusable = true
        isOpaque = true
    },
    content = content
)