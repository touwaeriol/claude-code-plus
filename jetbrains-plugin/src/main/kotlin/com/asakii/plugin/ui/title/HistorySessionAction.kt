package com.asakii.plugin.ui.title

import com.asakii.plugin.bridge.IdeSessionBridge
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * 历史会话按钮 - 显示在 ToolWindow 标题栏右侧
 *
 * 点击后触发前端显示历史会话浮层
 */
class HistorySessionAction(
    private val sessionBridge: IdeSessionBridge
) : AnAction("历史会话", "查看历史会话", AllIcons.Actions.Search) {

    override fun actionPerformed(e: AnActionEvent) {
        sessionBridge.toggleHistoryOverlay()
    }

    override fun update(e: AnActionEvent) {
        // 始终可用
        e.presentation.isEnabled = true
    }
}
