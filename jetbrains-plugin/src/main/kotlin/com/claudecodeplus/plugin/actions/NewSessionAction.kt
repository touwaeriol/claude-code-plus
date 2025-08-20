package com.claudecodeplus.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
// import com.claudecodeplus.plugin.services.ClaudeToolWindowService
// import com.intellij.openapi.components.service

/**
 * Action 用于创建新的 Claude 会话
 * 可通过主菜单、工具栏或快捷键触发
 */
class NewSessionAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 获取当前编辑器上下文
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val selectedText = editor?.selectionModel?.selectedText
        
        // TODO: 获取工具窗口服务
        // val toolWindowService = project.service<ClaudeToolWindowService>()
        
        // TODO: 创建新会话，如果有选中文本则自动添加为上下文
        // toolWindowService.createNewSession(
        //     initialContext = if (selectedText != null && psiFile != null) {
        //         mapOf(
        //             "selectedText" to selectedText,
        //             "fileName" to psiFile.name,
        //             "filePath" to psiFile.virtualFile.path
        //         )
        //     } else null
        // )
        
        // 打开并聚焦工具窗口
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("ClaudeCodePlus")
        toolWindow?.show()
    }
    
    override fun update(e: AnActionEvent) {
        // 只在有项目打开时启用
        e.presentation.isEnabled = e.project != null
    }
}