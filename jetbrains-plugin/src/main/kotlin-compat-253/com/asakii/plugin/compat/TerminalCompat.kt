package com.asakii.plugin.compat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalView
import mu.KotlinLogging

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
 * Terminal Widget 包装器 - 适用于 2025.3+
 *
 * 使用新的 TerminalView API 提供统一接口
 */
class TerminalWidgetWrapper(
    private val view: TerminalView,
    private val tab: TerminalToolWindowTab
) {

    /** Widget 类名（用于日志） */
    val widgetClassName: String get() = view.javaClass.name

    /**
     * 执行命令
     * 使用 TerminalView.sendText() API，并追加换行符执行
     */
    fun executeCommand(command: String) {
        try {
            // sendText 不会自动执行，需要追加换行符
            view.sendText(command + "\n")
            logger.debug { "Command sent: $command" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to execute command: $command" }
        }
    }

    /**
     * 检查是否有正在运行的命令
     *
     * 注意：2025.3 的 TerminalView API 中，命令状态检测需要通过 shellIntegrationDeferred
     * 这是一个异步 API，在同步上下文中无法直接使用
     *
     * @return null 表示 API 在同步上下文中不可用
     */
    fun hasRunningCommands(): Boolean? {
        // TerminalView 的 shellIntegrationDeferred 是异步的
        // 在同步上下文中无法直接检测命令状态
        // 返回 null 让调用者使用备用方案
        return null
    }

    /**
     * 等待命令执行完成 (2025.3+ 实现)
     *
     * 由于 TerminalView API 的命令状态检测是异步的，
     * 在同步上下文中返回 ApiUnavailable，建议使用后台执行模式
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
        // TerminalView 的命令状态检测需要通过 shellIntegrationDeferred（协程 API）
        // 在同步上下文中无法直接使用，返回 ApiUnavailable
        logger.warn { "waitForCommandCompletion: TerminalView API requires coroutine context, returning ApiUnavailable" }
        return CommandWaitResult.ApiUnavailable
    }

    /**
     * 发送 Ctrl+C 中断信号
     */
    fun sendInterrupt() {
        try {
            // 发送 Ctrl+C 字符
            view.sendText("\u0003")
            logger.debug { "Interrupt signal sent" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to send interrupt" }
        }
    }

    /**
     * 获取终端输出文本
     * 使用 TerminalView.outputModels API
     */
    fun getText(): String {
        return try {
            val outputModels = view.outputModels
            val regularModel = outputModels.regular
            // 获取从 startOffset 到 endOffset 的文本
            val startOffset = regularModel.startOffset
            val endOffset = regularModel.endOffset
            regularModel.getText(startOffset, endOffset).toString()
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
 * 使用新的 TerminalToolWindowTabsManager 公开 API (@Experimental)
 */
object TerminalCompat {

    // Unix shell 检测配置
    private val UNIX_BINARIES_DIRECTORIES = listOf("/bin", "/usr/bin", "/usr/local/bin", "/opt/homebrew/bin")
    private val UNIX_SHELL_NAMES = listOf("bash", "zsh", "fish", "pwsh")

    /**
     * 检测系统中已安装的 shell 列表
     *
     * 使用自定义实现，避免依赖内部 API
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
     * 使用 TerminalToolWindowTabsManager 公开 API (2025.3+)
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
        logger.info { "=== [253 TerminalCompat] createShellWidget ===" }
        logger.info { "  project: ${project.name}" }
        logger.info { "  workingDirectory: $workingDirectory" }
        logger.info { "  tabName: $tabName" }
        logger.info { "  shellCommand: $shellCommand" }

        @Suppress("removal")
        return try {
            var result: TerminalWidgetWrapper? = null

            // TerminalToolWindowTabsManager 需要在 EDT 上调用
            ApplicationManager.getApplication().invokeAndWait {
                val tabsManager = TerminalToolWindowTabsManager.getInstance(project)
                logger.info { "  TerminalToolWindowTabsManager class: ${tabsManager.javaClass.name}" }

                // 使用新的公开 API 创建终端
                val tab = tabsManager.createTabBuilder()
                    .workingDirectory(workingDirectory)
                    .shellCommand(shellCommand)
                    .tabName(tabName)
                    .requestFocus(true)
                    .deferSessionStartUntilUiShown(true)
                    .createTab()

                logger.info { "  Created terminal tab: ${tab.javaClass.name}" }

                // 激活终端工具窗口（253 新 API 不会自动激活）
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Terminal")
                if (toolWindow != null) {
                    toolWindow.show()
                    logger.info { "  Terminal ToolWindow activated" }
                } else {
                    logger.warn { "  Terminal ToolWindow not found" }
                }

                val view = tab.view
                logger.info { "  TerminalView class: ${view.javaClass.name}" }

                result = TerminalWidgetWrapper(view, tab)
            }

            result
        } catch (e: ProcessCanceledException) {
            // 必须重新抛出 ProcessCanceledException
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to create terminal widget" }
            null
        }
    }
}
