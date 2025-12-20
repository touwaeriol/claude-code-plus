package com.asakii.plugin.compat

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

/**
 * Terminal 兼容层 - 适用于 2025.3+
 *
 * 使用新的 createShellWidget(String, String, boolean, boolean) API
 * 替代已弃用的 createLocalShellWidget()
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
            // 使用新 API: createShellWidget(workingDirectory, tabName, requestFocus, deferSessionStartUntilUiShown)
            val widget = manager.createShellWidget(workingDirectory, tabName, true, true)
            widget as? ShellTerminalWidget
        } catch (e: Exception) {
            null
        }
    }
}
