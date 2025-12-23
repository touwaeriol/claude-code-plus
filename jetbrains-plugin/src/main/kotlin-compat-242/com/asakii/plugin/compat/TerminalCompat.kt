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
     * 使用 ShellTerminalWidget.hasRunningCommands() API
     *
     * @return true 表示有命令正在运行，false 表示没有，null 表示 API 不可用
     */
    fun hasRunningCommands(): Boolean? {
        return try {
            widget.hasRunningCommands()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to check running commands, Shell Integration may not be available" }
            null  // 返回 null 表示 API 不可用，需要使用备用方案
        }
    }

    /**
     * 等待命令执行完成 (2024.x ~ 2025.2 版本实现)
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
 * Terminal 兼容层 - 适用于 2024.1 ~ 2025.2
 *
 * 在这些版本中，使用 TerminalToolWindowManager.createLocalShellWidget() 创建终端
 * 返回的是 ShellTerminalWidget，它有 executeCommand() 和 hasRunningCommands() 方法
 */
object TerminalCompat {

    // Unix shell 检测配置（与 IDEA TerminalNewPredefinedSessionAction 保持一致）
    private val UNIX_BINARIES_DIRECTORIES = listOf("/bin", "/usr/bin", "/usr/local/bin", "/opt/homebrew/bin")
    private val UNIX_SHELL_NAMES = listOf("bash", "zsh", "fish", "pwsh")

    /**
     * 检测系统中已安装的 shell 列表
     *
     * 复制自 IDEA 242 版本 TerminalNewPredefinedSessionAction.detectShells() 实现
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

    /**
     * 检测 Unix 系统中的 shell
     * 实现逻辑与 IDEA TerminalNewPredefinedSessionAction.detectShells() 一致
     */
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

            // 如果同一 shell 在多个目录找到，添加目录后缀区分
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

    /**
     * 检测 Windows 系统中的 shell
     * 实现逻辑与 IDEA TerminalNewPredefinedSessionAction.detectShells() 一致
     */
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

        // PowerShell Core (pwsh)
        val pwshPath = "C:\\Program Files\\PowerShell\\7\\pwsh.exe"
        if (java.nio.file.Files.exists(java.nio.file.Path.of(pwshPath))) {
            shells.add(DetectedShell("PowerShell", pwshPath))
        }

        // Git Bash
        val gitBashPath = "C:\\Program Files\\Git\\bin\\bash.exe"
        if (java.nio.file.Files.exists(java.nio.file.Path.of(gitBashPath))) {
            shells.add(DetectedShell("Git Bash", gitBashPath))
        }

        // WSL (Windows Subsystem for Linux)
        val wslPath = "$systemRoot\\System32\\wsl.exe"
        if (java.nio.file.Files.exists(java.nio.file.Path.of(wslPath))) {
            shells.add(DetectedShell("WSL", wslPath))
        }

        // Cmder (通过环境变量检测)
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
