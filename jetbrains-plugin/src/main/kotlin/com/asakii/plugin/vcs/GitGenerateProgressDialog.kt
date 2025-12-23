package com.asakii.plugin.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.text.DefaultCaret

/**
 * Git Generate 进度对话框
 *
 * 显示 AI 生成 commit message 的完整过程，包括：
 * - 思考过程
 * - 工具调用（名称、参数、结果）
 * - 最终生成的 commit message
 */
class GitGenerateProgressDialog(
    private val project: Project
) : DialogWrapper(project, false) {

    private val logArea: JTextArea = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(8)
        // 自动滚动到底部
        (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.ALWAYS_UPDATE
    }

    private val statusLabel: JLabel = JLabel("Initializing...").apply {
        border = JBUI.Borders.empty(4, 8)
    }

    private var isComplete = false

    init {
        title = "Generating Commit Message"
        setOKButtonText("Close")
        setCancelButtonText("Cancel")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(700, 500)

        // 日志区域
        val scrollPane = JBScrollPane(logArea).apply {
            border = BorderFactory.createTitledBorder("Progress")
        }
        panel.add(scrollPane, BorderLayout.CENTER)

        // 状态栏
        panel.add(statusLabel, BorderLayout.SOUTH)

        return panel
    }

    /**
     * 添加日志行
     */
    fun appendLog(message: String) {
        SwingUtilities.invokeLater {
            logArea.append(message)
            logArea.append("\n")
        }
    }

    /**
     * 添加思考内容
     */
    fun appendThinking(thinking: String) {
        val truncated = if (thinking.length > 200) thinking.take(200) + "..." else thinking
        appendLog("💭 Thinking: $truncated")
    }

    /**
     * 添加工具调用开始
     */
    fun appendToolStart(toolName: String, params: String? = null) {
        val shortName = toolName
            .replace("mcp__jetbrains_git__", "Git.")
            .replace("mcp__jetbrains__", "IDE.")
        appendLog("")
        appendLog("🔧 Calling: $shortName")
        if (!params.isNullOrBlank()) {
            val truncatedParams = if (params.length > 300) params.take(300) + "..." else params
            appendLog("   Parameters: $truncatedParams")
        }
    }

    /**
     * 添加工具调用完成
     */
    fun appendToolComplete(toolName: String, success: Boolean, result: String? = null) {
        val shortName = toolName
            .replace("mcp__jetbrains_git__", "Git.")
            .replace("mcp__jetbrains__", "IDE.")
        val icon = if (success) "✅" else "❌"
        appendLog("$icon $shortName completed")
        if (!result.isNullOrBlank() && result.length < 500) {
            appendLog("   Result: $result")
        }
    }

    /**
     * 添加错误信息
     */
    fun appendError(error: String) {
        appendLog("")
        appendLog("❌ Error: $error")
    }

    /**
     * 添加最终结果
     */
    fun appendResult(commitMessage: String) {
        appendLog("")
        appendLog("═".repeat(50))
        appendLog("📝 Generated Commit Message:")
        appendLog("")
        appendLog(commitMessage)
        appendLog("═".repeat(50))
    }

    /**
     * 更新状态
     */
    fun updateStatus(status: String) {
        SwingUtilities.invokeLater {
            statusLabel.text = status
        }
    }

    /**
     * 标记完成
     */
    fun markComplete(success: Boolean) {
        isComplete = true
        SwingUtilities.invokeLater {
            statusLabel.text = if (success) "✅ Completed successfully" else "⚠️ Completed with issues"
            setCancelButtonText("Close")
            // 禁用取消按钮，只保留关闭
            cancelAction.isEnabled = false
        }
    }

    /**
     * 检查是否已取消
     */
    fun isCancelled(): Boolean {
        return !isComplete && !isShowing
    }

    override fun doCancelAction() {
        if (isComplete) {
            super.doCancelAction()
        } else {
            // 确认取消
            val result = JOptionPane.showConfirmDialog(
                contentPanel,
                "Cancel the commit message generation?",
                "Confirm Cancel",
                JOptionPane.YES_NO_OPTION
            )
            if (result == JOptionPane.YES_OPTION) {
                super.doCancelAction()
            }
        }
    }
}
