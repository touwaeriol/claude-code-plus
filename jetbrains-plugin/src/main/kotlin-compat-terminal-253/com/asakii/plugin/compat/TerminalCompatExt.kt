package com.asakii.plugin.compat

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

private val logger = Logger.getInstance("com.asakii.plugin.compat.TerminalCompatExt")

/**
 * Terminal 兼容层扩展 - 253+ 版本
 *
 * 使用 createNewSession API 直接传入 shellCommand 参数，
 * 支持自定义 shell。
 */
fun createShellWidget(
    project: Project,
    workingDirectory: String,
    tabName: String,
    shellCommand: List<String>? = null
): TerminalWidgetWrapper? {
    logger.info("=== [TerminalCompat-253] createShellWidget ===")
    logger.info("  project: ${project.name}")
    logger.info("  workingDirectory: $workingDirectory")
    logger.info("  tabName: $tabName")
    logger.info("  shellCommand: $shellCommand")

    return try {
        val manager = TerminalToolWindowManager.getInstance(project)

        // 253+ 版本使用 createNewSession，支持自定义 shell
        @Suppress("UnstableApiUsage")
        val terminalWidget = manager.createNewSession(
            workingDirectory,
            tabName,
            shellCommand,
            true,  // requestFocus
            false  // deferSessionStartUntilUiShown
        )

        // 转换为 ShellTerminalWidget
        val shellWidget = ShellTerminalWidget.toShellJediTermWidgetOrThrow(terminalWidget)
        logger.info("  Created terminal widget: ${shellWidget.javaClass.name}")
        TerminalWidgetWrapper(shellWidget)
    } catch (e: Exception) {
        logger.error("Failed to create terminal widget", e)
        null
    }
}
