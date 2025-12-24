package com.asakii.plugin.compat

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

private val logger = Logger.getInstance("com.asakii.plugin.compat.TerminalCompatExt")

/**
 * Terminal 兼容层扩展 - 242-250 版本
 *
 * 使用 createNewSession(runner, tabState) + TerminalTabState.myShellCommand 来创建终端。
 * 这是 242 版本中支持自定义 shell 的唯一可靠方法。
 *
 * 注意：242 版本的 createLocalShellWidget 不支持直接传入 shell 命令，
 * LocalTerminalCustomizer 扩展点在新终端引擎中也不再被调用。
 * 因此必须通过 TerminalTabState.myShellCommand 来传递自定义 shell 命令。
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
        val runner = manager.terminalRunner
        logger.info("  terminalRunner: ${runner.javaClass.name}")

        // 创建 TerminalTabState 并设置参数
        val tabState = TerminalTabState().apply {
            myTabName = tabName
            myWorkingDirectory = workingDirectory
            // 关键：设置自定义 shell 命令
            if (shellCommand != null && shellCommand.isNotEmpty()) {
                myShellCommand = shellCommand
                logger.info("  Set TerminalTabState.myShellCommand: $shellCommand")
            }
        }

        // 记录调用前的 widgets
        val widgetsBefore = manager.terminalWidgets.toSet()
        logger.info("  Widgets before: ${widgetsBefore.size}")

        // 使用 createNewSession(runner, tabState) 创建终端
        // 这个方法会使用 tabState.myShellCommand 作为 shell 命令
        logger.info("  Calling createNewSession(runner, tabState)...")
        manager.createNewSession(runner, tabState)

        // 获取新创建的 widget
        val widgetsAfter = manager.terminalWidgets.toSet()
        logger.info("  Widgets after: ${widgetsAfter.size}")

        val newWidgets = widgetsAfter - widgetsBefore
        logger.info("  New widgets: ${newWidgets.size}")

        if (newWidgets.isNotEmpty()) {
            val terminalWidget = newWidgets.first()
            logger.info("  New terminal widget: ${terminalWidget.javaClass.name}")

            // 转换为 ShellTerminalWidget
            val shellWidget = ShellTerminalWidget.toShellJediTermWidgetOrThrow(terminalWidget)
            logger.info("  Created shell widget: ${shellWidget.javaClass.name}")
            TerminalWidgetWrapper(shellWidget)
        } else {
            logger.warn("  No new widget created!")
            null
        }
    } catch (e: Exception) {
        logger.error("Failed to create terminal widget", e)
        null
    }
}
