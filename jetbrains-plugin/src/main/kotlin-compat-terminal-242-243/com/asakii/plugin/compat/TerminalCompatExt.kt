package com.asakii.plugin.compat

import com.asakii.plugin.mcp.tools.terminal.ShellCommandOverride
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

private val logger = Logger.getInstance("com.asakii.plugin.compat.TerminalCompatExt")

/**
 * Terminal 兼容层扩展 - 242-250 版本
 *
 * 使用 createLocalShellWidget + LocalTerminalCustomizer 来创建终端
 * 通过 ShellCommandOverride 传递 shell 命令给 LocalTerminalCustomizer
 */
fun createShellWidget(
    project: Project,
    workingDirectory: String,
    tabName: String,
    shellCommand: List<String>? = null
): TerminalWidgetWrapper? {
    logger.info("=== [TerminalCompat-242] createShellWidget ===")
    logger.info("  project: ${project.name}")
    logger.info("  workingDirectory: $workingDirectory")
    logger.info("  tabName: $tabName")
    logger.info("  shellCommand: $shellCommand")

    return try {
        val manager = TerminalToolWindowManager.getInstance(project)

        // 使用 ShellCommandOverride + LocalTerminalCustomizer 来指定 shell
        if (shellCommand != null && shellCommand.isNotEmpty()) {
            logger.info("  Setting ShellCommandOverride for LocalTerminalCustomizer: $shellCommand")
            ShellCommandOverride.set(shellCommand)
        }

        @Suppress("DEPRECATION")
        val widget = manager.createLocalShellWidget(workingDirectory, tabName)

        if (widget != null) {
            logger.info("  Created terminal widget via createLocalShellWidget: ${widget.javaClass.name}")
            TerminalWidgetWrapper(widget)
        } else {
            logger.warn("  createLocalShellWidget returned null")
            ShellCommandOverride.clear()
            null
        }
    } catch (e: Exception) {
        logger.error("Failed to create terminal widget", e)
        ShellCommandOverride.clear()
        null
    }
}
