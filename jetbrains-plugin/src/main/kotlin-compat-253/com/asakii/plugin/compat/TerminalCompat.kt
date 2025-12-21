package com.asakii.plugin.compat

import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import mu.KotlinLogging
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

private val logger = KotlinLogging.logger {}

/**
 * Terminal Widget 包装器
 * 提供跨版本的统一 API
 *
 * 在 2025.3+ 中，完全使用 TerminalWidget 接口的新 API
 */
class TerminalWidgetWrapper(private val widget: TerminalWidget) {

    /** Widget 类名（用于日志） */
    val widgetClassName: String get() = widget.javaClass.name

    /**
     * 执行命令
     * 使用 TerminalWidget.sendCommandToExecute() 新 API
     */
    fun executeCommand(command: String) {
        try {
            widget.sendCommandToExecute(command)
            logger.debug { "Command sent: $command" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to execute command: $command" }
        }
    }

    /**
     * 检查是否有正在运行的命令
     * 使用 TerminalWidget.isCommandRunning 新 API
     */
    fun hasRunningCommands(): Boolean {
        return try {
            widget.isCommandRunning()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to check isCommandRunning" }
            false
        }
    }

    /**
     * 发送 Ctrl+C 中断信号
     * 使用 TerminalWidget.ttyConnector 发送中断字节
     */
    fun sendInterrupt() {
        try {
            val connector = widget.ttyConnector
            if (connector != null) {
                connector.write("\u0003".toByteArray())
                logger.debug { "Interrupt signal sent" }
            } else {
                logger.warn { "Cannot send interrupt: ttyConnector is null" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to send interrupt" }
        }
    }

    /**
     * 获取终端输出文本
     * 使用 TerminalWidget.getText() (实验性 API)
     */
    fun getText(): String {
        return try {
            widget.getText().toString()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get terminal text" }
            ""
        }
    }

    /**
     * 获取终端输出内容（统一 API）
     * @param maxLines 最大行数
     * @return 终端输出文本
     */
    fun getOutput(maxLines: Int = 1000): String {
        val text = getText()
        val lines = text.split("\n")
        return if (lines.size > maxLines) {
            lines.takeLast(maxLines).joinToString("\n")
        } else {
            text
        }
    }

    /**
     * 获取终端输出文本（按行分割）
     */
    fun getOutputLines(maxLines: Int = 1000): List<String> {
        val text = getText()
        val lines = text.split("\n")
        return if (lines.size > maxLines) {
            lines.takeLast(maxLines)
        } else {
            lines
        }
    }
}

/**
 * Terminal 兼容层 - 适用于 2025.3+
 *
 * 完全使用 TerminalWidget 接口的新 API，无类型转换
 */
object TerminalCompat {

    /**
     * 创建本地 Shell Widget
     *
     * @param project 项目
     * @param workingDirectory 工作目录
     * @param tabName 标签名称
     * @return TerminalWidgetWrapper 或 null（如果创建失败）
     */
    fun createShellWidget(
        project: Project,
        workingDirectory: String,
        tabName: String
    ): TerminalWidgetWrapper? {
        logger.info { "createShellWidget: project=${project.name}, workingDirectory=$workingDirectory, tabName=$tabName" }
        return try {
            val manager = TerminalToolWindowManager.getInstance(project)
            logger.debug { "TerminalToolWindowManager: ${manager.javaClass.name}" }

            val widget = manager.createShellWidget(workingDirectory, tabName, true, true)
            logger.info { "Created terminal widget: ${widget.javaClass.name}" }

            TerminalWidgetWrapper(widget)
        } catch (e: Exception) {
            logger.error(e) { "Failed to create terminal widget" }
            null
        }
    }
}
