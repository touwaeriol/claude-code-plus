package com.asakii.plugin.compat

import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalWidget
import mu.KotlinLogging
import org.jetbrains.plugins.terminal.ShellTerminalWidget
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
 * Terminal Widget 包装器 - 适用于 2024.2 ~ 2025.2
 *
 * 封装 ShellTerminalWidget，提供统一的跨版本 API
 */
class TerminalWidgetWrapper(private val widget: ShellTerminalWidget) {

    /** 获取底层 JBTerminalWidget */
    val jbWidget: JBTerminalWidget get() = widget

    /** Widget 类名（用于日志） */
    val widgetClassName: String get() = widget.javaClass.name

    /**
     * 执行命令
     * 使用 ShellTerminalWidget.executeCommand()
     */
    fun executeCommand(command: String) {
        widget.executeCommand(command)
    }

    /**
     * 检查是否有正在运行的命令
     * 使用 ShellTerminalWidget.hasRunningCommands() API
     *
     * @return true 表示有命令正在运行，false 表示没有，null 表示 API 不可用
     */
    fun hasRunningCommands(): Boolean? {
        return try {
            widget.hasRunningCommands()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to check running commands, Shell Integration may not be available" }
            null
        }
    }

    /**
     * 等待命令执行完成
     *
     * 使用 hasRunningCommands() API (依赖 Shell Integration)
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
                        logger.debug { "Command completed (detected by hasRunningCommands API)" }
                        return CommandWaitResult.Completed
                    }
                }
                null -> {
                    // API 不可用，无法检测命令状态
                    logger.warn { "hasRunningCommands API not available (Shell Integration may be disabled)" }
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
     * 获取终端输出内容
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
                val line = buffer.getLine(i - historyLines)
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

    /**
     * 获取终端输出文本（按行分割）
     */
    fun getOutputLines(maxLines: Int = 1000): List<String> {
        val text = getOutput(maxLines)
        return text.split("\n")
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
 * Terminal 兼容层 - 适用于 2024.2 ~ 2025.2
 *
 * 使用 TerminalToolWindowManager.createLocalShellWidget() 创建终端
 * 返回的是 ShellTerminalWidget，有 executeCommand() 和 hasRunningCommands() 方法
 *
 * 注意：此 API 在 2025.3 中被标记为 deprecated，2025.3+ 应使用 kotlin-compat-253
 */
object TerminalCompat {

    // Unix shell 检测配置
    private val UNIX_BINARIES_DIRECTORIES = listOf("/bin", "/usr/bin", "/usr/local/bin", "/opt/homebrew/bin")
    private val UNIX_SHELL_NAMES = listOf("bash", "zsh", "fish", "pwsh")

    /**
     * 检测系统中已安装的 shell 列表
     *
     * @return 检测到的 shell 列表
     */
    fun detectInstalledShells(): List<DetectedShell> {
        return if (com.intellij.openapi.util.SystemInfo.isUnix) {
            detectUnixShells()
        } else {
            detectWindowsShells()
        }
    }

    private fun detectUnixShells(): List<DetectedShell> {
        val shells = mutableListOf<DetectedShell>()

        for (shellName in UNIX_SHELL_NAMES) {
            val foundPaths = mutableListOf<String>()

            for (dir in UNIX_BINARIES_DIRECTORIES) {
                val shellPath = "$dir/$shellName"
                if (java.nio.file.Files.exists(java.nio.file.Path.of(shellPath))) {
                    foundPaths.add(shellPath)
                }
            }

            if (foundPaths.size > 1) {
                for (path in foundPaths) {
                    val dir = java.io.File(path).parent
                    shells.add(DetectedShell("$shellName ($dir)", path))
                }
            } else if (foundPaths.size == 1) {
                shells.add(DetectedShell(shellName, foundPaths[0]))
            }
        }

        return shells
    }

    private fun detectWindowsShells(): List<DetectedShell> {
        val shells = mutableListOf<DetectedShell>()
        val systemRoot = System.getenv("SystemRoot") ?: "C:\\Windows"

        // Windows PowerShell
        val windowsPowerShellPath = "$systemRoot\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"
        if (java.nio.file.Files.exists(java.nio.file.Path.of(windowsPowerShellPath))) {
            shells.add(DetectedShell("Windows PowerShell", windowsPowerShellPath))
        }

        // Command Prompt
        val cmdPath = "$systemRoot\\System32\\cmd.exe"
        if (java.nio.file.Files.exists(java.nio.file.Path.of(cmdPath))) {
            shells.add(DetectedShell("Command Prompt", cmdPath))
        }

        // PowerShell Core
        val pwshPath = "C:\\Program Files\\PowerShell\\7\\pwsh.exe"
        if (java.nio.file.Files.exists(java.nio.file.Path.of(pwshPath))) {
            shells.add(DetectedShell("PowerShell", pwshPath))
        }

        // Git Bash
        val gitBashPath = "C:\\Program Files\\Git\\bin\\bash.exe"
        if (java.nio.file.Files.exists(java.nio.file.Path.of(gitBashPath))) {
            shells.add(DetectedShell("Git Bash", gitBashPath))
        }

        // WSL
        val wslPath = "$systemRoot\\System32\\wsl.exe"
        if (java.nio.file.Files.exists(java.nio.file.Path.of(wslPath))) {
            shells.add(DetectedShell("WSL", wslPath))
        }

        // Cmder
        val cmderRoot = System.getenv("CMDER_ROOT")
        if (!cmderRoot.isNullOrBlank()) {
            val cmderInitPath = "$cmderRoot\\vendor\\init.bat"
            if (java.nio.file.Files.exists(java.nio.file.Path.of(cmderInitPath))) {
                shells.add(DetectedShell("Cmder", cmderInitPath))
            }
        }

        return shells
    }

    /**
     * 创建本地 Shell Widget
     *
     * 使用 createLocalShellWidget API (2024.2 ~ 2025.2)
     * 注意：此 API 不支持自定义 shell 命令，将使用系统默认 shell
     *
     * @param project 项目
     * @param workingDirectory 工作目录
     * @param tabName 标签名称
     * @param shellCommand Shell 命令（在此版本中不支持，将被忽略）
     * @return TerminalWidgetWrapper 或 null
     */
    fun createShellWidget(
        project: Project,
        workingDirectory: String,
        tabName: String,
        shellCommand: List<String>? = null
    ): TerminalWidgetWrapper? {
        logger.info { "createShellWidget: project=${project.name}, workingDirectory=$workingDirectory, tabName=$tabName" }

        if (shellCommand != null) {
            logger.warn { "Custom shell command not supported in 2024.2 ~ 2025.2, using default shell" }
        }

        return try {
            val manager = TerminalToolWindowManager.getInstance(project)
            @Suppress("DEPRECATION")
            val widget = manager.createLocalShellWidget(workingDirectory, tabName)

            if (widget != null) {
                logger.info { "Created terminal widget: ${widget.javaClass.name}" }
                TerminalWidgetWrapper(widget)
            } else {
                logger.error { "createLocalShellWidget returned null" }
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create terminal widget" }
            null
        }
    }
}
