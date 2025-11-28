package com.asakii.plugin.ui.title

import com.asakii.plugin.bridge.IdeSessionBridge
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * 新建会话按钮 - 显示在 ToolWindow 标题栏右侧
 *
 * 点击后触发前端创建新会话
 */
class NewSessionAction(
    private val sessionBridge: IdeSessionBridge
) : AnAction("新建会话", "创建新会话", AllIcons.General.Add) {

    override fun actionPerformed(e: AnActionEvent) {
        sessionBridge.requestNewSession()
    }

    override fun update(e: AnActionEvent) {
        // 始终可用
        e.presentation.isEnabled = true
    }
}
