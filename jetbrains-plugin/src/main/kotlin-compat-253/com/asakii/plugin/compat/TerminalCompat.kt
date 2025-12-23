package com.asakii.plugin.compat

import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import mu.KotlinLogging
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

private val logger = KotlinLogging.logger {}

/**
 * 命令完成等待结果
 */
sealed class CommandWaitResult {
    /** 命令完成 */
    object Completed : CommandWaitResult()
    /** 超时 */
    object Timeout : CommandWaitResult()
    /** 被中断 */
    object Interrupted : CommandWaitResult()
    /** API 不可用，无法检测命令状态 */
    object ApiUnavailable : CommandWaitResult()
}

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
     *
     * @return true 表示有命令正在运行，false 表示没有，null 表示 API 不可用
     */
    fun hasRunningCommands(): Boolean? {
        return try {
            widget.isCommandRunning()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to check isCommandRunning, API may not be available" }
            null  // 返回 null 表示 API 不可用，需要使用备用方案
        }
    }

    /**
     * 等待命令执行完成 (2025.3+ 实现)
     *
     * 使用 isCommandRunning() API (依赖 Shell Integration)
     * 如果 API 不可用，返回 ApiUnavailable，建议使用后台执行模式
     *
     * @param timeoutMs 超时时间（毫秒）
     * @param initialDelayMs 初始等待时间，让命令开始执行
     * @param pollIntervalMs 轮询间隔
     * @return 等待结果
     */
    fun waitForCommandCompletion(
        timeoutMs: Long = 300_000,
        initialDelayMs: Long = 300,
        pollIntervalMs: Long = 100
    ): CommandWaitResult {
        val startTime = System.currentTimeMillis()

        // 初始等待，让命令开始执行
        Thread.sleep(initialDelayMs)

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (Thread.currentThread().isInterrupted) {
                return CommandWaitResult.Interrupted
            }

            val isRunning = hasRunningCommands()
            when (isRunning) {
                false -> {
                    // API 报告命令已完成，再确认一次
                    Thread.sleep(pollIntervalMs)
                    if (hasRunningCommands() == false) {
                        logger.debug { "Command completed (detected by isCommandRunning API)" }
                        return CommandWaitResult.Completed
                    }
                }
                null -> {
                    // API 不可用，无法检测命令状态
                    logger.warn { "isCommandRunning API not available (Shell Integration may be disabled)" }
                    return CommandWaitResult.ApiUnavailable
                }
                true -> {
                    // 命令仍在运行，继续等待
                }
            }

            Thread.sleep(pollIntervalMs)
        }

        logger.warn { "Command wait timeout after ${timeoutMs}ms" }
        return CommandWaitResult.Timeout
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
 * Shell 检测结果
 */
data class DetectedShell(
    val name: String,
    val path: String
)

/**
 * Terminal 兼容层 - 适用于 2025.3+
 *
 * 完全使用 TerminalWidget 接口的新 API，无类型转换
 */
object TerminalCompat {

    /**
     * 检测系统中已安装的 shell 列表
     *
     * 使用 TerminalShellsDetector.detectShells() API (2025.3+)
     *
     * @return 检测到的 shell 列表
     */
    fun detectInstalledShells(): List<DetectedShell> {
        return org.jetbrains.plugins.terminal.TerminalShellsDetector.detectShells()
            .map { DetectedShell(it.name, it.path) }
    }

    /**
     * 创建本地 Shell Widget
     *
     * @param project 项目
     * @param workingDirectory 工作目录
     * @param tabName 标签名称
     * @param shellCommand Shell 命令（如 listOf("C:\\Program Files\\Git\\bin\\bash.exe")）
     * @return TerminalWidgetWrapper 或 null（如果创建失败）
     */
    fun createShellWidget(
        project: Project,
        workingDirectory: String,
        tabName: String,
        shellCommand: List<String>? = null
    ): TerminalWidgetWrapper? {
        logger.info { "createShellWidget: project=${project.name}, workingDirectory=$workingDirectory, tabName=$tabName, shellCommand=$shellCommand" }
        return try {
            val manager = TerminalToolWindowManager.getInstance(project)
            logger.debug { "TerminalToolWindowManager: ${manager.javaClass.name}" }

            // 使用 createNewSession 以支持指定 shellCommand
            val widget = manager.createNewSession(workingDirectory, tabName, shellCommand, true, true)
            logger.info { "Created terminal widget: ${widget.javaClass.name}" }

            TerminalWidgetWrapper(widget)
        } catch (e: Exception) {
            logger.error(e) { "Failed to create terminal widget" }
            null
        }
    }
}
