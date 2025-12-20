package com.asakii.plugin.compat

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

/**
 * Terminal 兼容层 - 适用于 2024.1 ~ 2025.2
 *
 * 在这些版本中，使用 TerminalToolWindowManager.createLocalShellWidget() 创建终端
 * 此方法在新版本中已弃用，但在旧版本中是唯一可用的方式
 */
object TerminalCompat {

    /**
     * 创建本地 Shell Widget
     *
     * @param project 项目
     * @param workingDirectory 工作目录
     * @param tabName 标签名称
     * @return ShellTerminalWidget 或 null（如果创建失败）
     */
    fun createShellWidget(
        project: Project,
        workingDirectory: String,
        tabName: String
    ): ShellTerminalWidget? {
        return try {
            val manager = TerminalToolWindowManager.getInstance(project)
            // 在 2024.2 中此 API 尚未弃用，无需 @Suppress
            val widget = manager.createLocalShellWidget(workingDirectory, tabName)
            widget as? ShellTerminalWidget
        } catch (e: Exception) {
            null
        }
    }
}
