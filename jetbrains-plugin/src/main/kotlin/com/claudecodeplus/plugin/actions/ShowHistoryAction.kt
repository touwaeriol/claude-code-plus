package com.claudecodeplus.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

/**
 * 显示会话历史 Action
 */
class ShowHistoryAction : AnAction("会话历史", "显示会话历史", null) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // TODO: 实现会话历史功能
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "会话历史功能开发中...",
            "提示",
            javax.swing.JOptionPane.INFORMATION_MESSAGE
        )
    }
}

