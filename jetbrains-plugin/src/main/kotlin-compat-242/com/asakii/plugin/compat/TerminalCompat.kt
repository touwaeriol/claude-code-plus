package com.asakii.plugin.compat

import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalWidget
import mu.KotlinLogging
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

private val logger = KotlinLogging.logger {}

/**
 * Terminal Widget 包装器接口
 * 提供跨版本的统一 API
 */
class TerminalWidgetWrapper(private val widget: ShellTerminalWidget) {

    /** 获取底层 JBTerminalWidget */
    val jbWidget: JBTerminalWidget get() = widget

    /** Widget 类名（用于日志） */
    val widgetClassName: String get() = widget.javaClass.name

    /**
     * 执行命令
     * 在 2024.x ~ 2025.2 中使用 ShellTerminalWidget.executeCommand()
     */
    fun executeCommand(command: String) {
        widget.executeCommand(command)
    }

    /**
     * 检查是否有正在运行的命令
     */
    fun hasRunningCommands(): Boolean {
        return try {
            widget.hasRunningCommands()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to check running commands" }
            false
        }
    }

    /**
     * 发送 Ctrl+C 中断信号
     */
    fun sendInterrupt() {
        widget.terminalStarter?.sendBytes("\u0003".toByteArray(), false)
    }

    /**
     * 获取终端文本缓冲区
     */
    val terminalTextBuffer get() = widget.terminalTextBuffer

    /**
     * 获取 terminalStarter
     */
    val terminalStarter get() = widget.terminalStarter

    /**
     * 获取终端输出内容（统一 API）
     * @param maxLines 最大行数
     * @return 终端输出文本
     */
    fun getOutput(maxLines: Int = 1000): String {
        return try {
            val buffer = terminalTextBuffer
            val screenLines = buffer.screenLinesCount
            val historyLines = buffer.historyLinesCount
            val totalLines = screenLines + historyLines

            val startLine = if (totalLines > maxLines) {
                totalLines - maxLines
            } else {
                0
            }

            val sb = StringBuilder()
            for (i in startLine until totalLines) {
                val line = if (i < historyLines) {
                    buffer.getLine(i - historyLines)
                } else {
                    buffer.getLine(i - historyLines)
                }
                sb.append(line.text.trimEnd())
                if (i < totalLines - 1) {
                    sb.append("\n")
                }
            }
            sb.toString()
        } catch (e: Exception) {
            logger.error(e) { "Failed to get output" }
            ""
        }
    }
}

/**
 * Terminal 兼容层 - 适用于 2024.1 ~ 2025.2
 *
 * 在这些版本中，使用 TerminalToolWindowManager.createLocalShellWidget() 创建终端
 * 返回的是 ShellTerminalWidget，它有 executeCommand() 和 hasRunningCommands() 方法
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
        logger.warn { "createShellWidget called: project=${project.name}, workingDirectory=$workingDirectory, tabName=$tabName" }
        return try {
            val manager = TerminalToolWindowManager.getInstance(project)
            logger.warn { "TerminalToolWindowManager instance: ${manager.javaClass.name}" }

            val widget = manager.createLocalShellWidget(workingDirectory, tabName)
            logger.warn { "createLocalShellWidget returned: ${widget?.javaClass?.name ?: "null"}" }

            if (widget != null) {
                logger.warn { "Successfully created TerminalWidgetWrapper for: ${widget.javaClass.name}" }
                TerminalWidgetWrapper(widget)
            } else {
                logger.error { "createLocalShellWidget returned null" }
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create terminal widget in TerminalCompat (242)" }
            null
        }
    }
}
