package com.claudecodeplus.plugin.ui.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * 错误详情对话框
 * 
 * 显示详细错误信息，包括堆栈跟踪和重试按钮
 */
class ErrorDetailDialog(
    private val project: Project?,
    private val errorTitle: String,
    private val errorMessage: String,
    private val stackTrace: String? = null,
    private val onRetry: (() -> Unit)? = null
) : DialogWrapper(project) {
    
    init {
        title = errorTitle
        setOKButtonText("关闭")
        if (onRetry != null) {
            // 添加重试按钮
            setCancelButtonText("重试")
        }
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(600, 400)
        panel.border = JBUI.Borders.empty(10)
        
        // 错误消息标签
        val messageLabel = JLabel("<html><b>错误信息:</b></html>")
        messageLabel.border = JBUI.Borders.empty(0, 0, 5, 0)
        panel.add(messageLabel, BorderLayout.NORTH)
        
        // 错误详情文本区域
        val detailsArea = JBTextArea()
        detailsArea.isEditable = false
        detailsArea.lineWrap = true
        detailsArea.wrapStyleWord = true
        
        val detailsText = buildString {
            appendLine(errorMessage)
            
            if (stackTrace != null) {
                appendLine()
                appendLine("堆栈跟踪:")
                appendLine("─".repeat(50))
                appendLine(stackTrace)
            }
        }
        
        detailsArea.text = detailsText
        detailsArea.caretPosition = 0 // 滚动到顶部
        
        val scrollPane = JBScrollPane(detailsArea)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 提示标签
        val hintLabel = JLabel("<html><small>您可以复制此错误信息并报告给开发团队</small></html>")
        hintLabel.border = JBUI.Borders.empty(5, 0, 0, 0)
        panel.add(hintLabel, BorderLayout.SOUTH)
        
        return panel
    }
    
    override fun doCancelAction() {
        // 取消按钮用作重试
        if (onRetry != null) {
            close(CANCEL_EXIT_CODE)
            onRetry.invoke()
        } else {
            super.doCancelAction()
        }
    }
    
    companion object {
        /**
         * 显示错误对话框
         */
        fun show(
            project: Project?,
            title: String,
            message: String,
            exception: Exception? = null,
            onRetry: (() -> Unit)? = null
        ) {
            val stackTrace = exception?.stackTraceToString()
            val dialog = ErrorDetailDialog(project, title, message, stackTrace, onRetry)
            dialog.show()
        }
        
        /**
         * 显示连接错误对话框
         */
        fun showConnectionError(
            project: Project?,
            onRetry: (() -> Unit)? = null
        ) {
            show(
                project,
                "连接错误",
                "无法连接到 Claude 服务器。请检查您的网络连接和 API 配置。",
                null,
                onRetry
            )
        }
        
        /**
         * 显示 API 速率限制错误
         */
        fun showRateLimitError(
            project: Project?,
            retryAfterSeconds: Int = 60
        ) {
            show(
                project,
                "API 速率限制",
                "已达到 API 速率限制。请在 $retryAfterSeconds 秒后重试。",
                null,
                null
            )
        }
    }
}


