package com.claudecodeplus.plugin.actions

import com.claudecodeplus.plugin.ui.SessionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Claude 快捷键操作
 * 
 * 定义各种快捷键操作
 */

/**
 * 聚焦到 Claude 输入框
 * 快捷键: Ctrl+K (Mac: Cmd+K)
 */
class FocusClaudeInputAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Claude Code Plus")
        toolWindow?.show()
        
        // TODO: 实际聚焦到输入框
        // 需要从工具窗口获取 ChatPanel 引用
    }
}

/**
 * 新建会话
 * 快捷键: Ctrl+N (Mac: Cmd+N)
 */
class NewClaudeSessionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val sessionManager = SessionManager.getInstance(project)
        sessionManager.createSession()
        
        // 显示工具窗口
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Claude Code Plus")
        toolWindow?.show()
    }
}

/**
 * 切换搜索
 * 快捷键: Ctrl+/ (Mac: Cmd+/)
 */
class ToggleClaudeSearchAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Claude Code Plus")
        toolWindow?.show()
        
        // TODO: 切换搜索面板显示状态
    }
}

/**
 * 显示 Claude 设置
 * 快捷键: Ctrl+, (Mac: Cmd+,)
 */
class ShowClaudeSettingsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 打开设置对话框
        com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            "Claude Code Plus"
        )
    }
}

/**
 * 显示 Token 统计
 */
class ShowTokenStatsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        com.claudecodeplus.plugin.ui.dialogs.TokenStatsDialog.show(project)
    }
}

/**
 * 显示上下文管理器
 */
class ShowContextManagerAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        com.claudecodeplus.plugin.ui.dialogs.ContextManagerDialog.show(project)
    }
}


