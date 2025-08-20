package com.claudecodeplus.plugin.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import javax.swing.Timer

/**
 * 浮动工具栏 - 在编辑器中选中代码时显示
 * 提供快速访问 Claude 功能
 */
class FloatingToolbar(
    private val project: Project,
    private val editor: Editor
) {
    private var currentPopup: ListPopup? = null
    private val hideTimer = Timer(500) { hidePopup() }
    
    init {
        // 监听选择变化
        editor.selectionModel.addSelectionListener(object : SelectionListener {
            override fun selectionChanged(e: SelectionEvent) {
                handleSelectionChange()
            }
        })
    }
    
    private fun handleSelectionChange() {
        val selectedText = editor.selectionModel.selectedText
        
        if (selectedText != null && selectedText.length > 10) {
            // 有足够的选中文本，显示工具栏
            showFloatingToolbar()
        } else {
            // 没有选中或选中太少，隐藏工具栏
            scheduleHide()
        }
    }
    
    private fun showFloatingToolbar() {
        hideTimer.stop()
        
        // 如果已有弹窗，先关闭
        currentPopup?.cancel()
        
        // 创建动作组
        val actionGroup = createActionGroup()
        
        // 创建弹出菜单
        currentPopup = JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Claude 操作",
                actionGroup,
                com.intellij.ide.DataManager.getInstance().getDataContext(editor.component),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )
        
        // 计算显示位置（在选中文本的末尾）
        val selectionEnd = editor.selectionModel.selectionEnd
        val point = editor.visualPositionToXY(
            editor.offsetToVisualPosition(selectionEnd)
        )
        
        // 显示弹窗
        currentPopup?.show(RelativePoint(editor.contentComponent, point))
    }
    
    private fun createActionGroup(): ActionGroup {
        return object : ActionGroup("Claude Actions", true) {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                return arrayOf(
                    QuickClaudeAction("解释代码", "请解释这段代码的功能"),
                    QuickClaudeAction("优化性能", "请优化这段代码的性能"),
                    QuickClaudeAction("生成测试", "请为这段代码生成单元测试"),
                    QuickClaudeAction("修复问题", "请检查并修复这段代码中的问题"),
                    QuickClaudeAction("添加注释", "请为这段代码添加详细注释"),
                    Separator.getInstance(),
                    NewSessionWithContextAction(),
                    AddToCurrentSessionAction()
                )
            }
        }
    }
    
    private fun scheduleHide() {
        hideTimer.restart()
    }
    
    private fun hidePopup() {
        currentPopup?.cancel()
        currentPopup = null
    }
    
    /**
     * 快速 Claude 操作
     */
    private inner class QuickClaudeAction(
        private val actionName: String,
        private val prompt: String
    ) : AnAction(actionName) {
        
        override fun actionPerformed(e: AnActionEvent) {
            val selectedText = editor.selectionModel.selectedText ?: return
            
            // 打开快速对话框
            val dialog = QuickChatDialog(project, selectedText, editor.virtualFile?.name)
            dialog.show()
            
            // TODO: 自动填充 prompt
        }
    }
    
    /**
     * 使用选中代码创建新会话
     */
    private inner class NewSessionWithContextAction : AnAction("新建会话...") {
        override fun actionPerformed(e: AnActionEvent) {
            val selectedText = editor.selectionModel.selectedText ?: return
            
            // 创建新会话并添加选中代码作为上下文
            // TODO: 调用工具窗口服务创建会话
        }
    }
    
    /**
     * 添加选中代码到当前会话
     */
    private inner class AddToCurrentSessionAction : AnAction("添加到当前会话") {
        override fun actionPerformed(e: AnActionEvent) {
            val selectedText = editor.selectionModel.selectedText ?: return
            
            // 添加到当前活动会话
            // TODO: 调用工具窗口服务添加上下文
        }
    }
}