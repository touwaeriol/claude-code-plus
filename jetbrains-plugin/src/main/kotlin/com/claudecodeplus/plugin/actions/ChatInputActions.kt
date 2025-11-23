package com.claudecodeplus.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.claudecodeplus.plugin.types.SessionObject

// 注意：这些 Action 类目前不可用，因为 SessionObject 不包含 UI 相关的字段
// 如果需要使用，需要重新设计为 Swing UI 实现

/**
 * 聊天输入框快捷键动作集合
 * 实现插件专用的文本编辑快捷键，仅在聊天输入框中生效
 */

/**
 * Ctrl+U: 删除光标位置到行首的文本
 * 标准Unix/Linux终端行为
 * 
 * 注意：此实现需要 Swing UI 支持，当前暂时禁用
 */
class DeleteToLineStartAction(
    private val getSessionObject: () -> SessionObject?
) : AnAction("Delete to Line Start") {
    
    companion object {
        private val logger = Logger.getInstance(DeleteToLineStartAction::class.java)
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        // TODO: 实现 Swing UI 版本
        logger.debug("DeleteToLineStartAction: 需要 Swing UI 实现")
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = false  // 暂时禁用
    }
}

/**
 * Shift+Enter: 插入换行符
 * 与现有逻辑保持一致的备用实现
 * 
 * 注意：此实现需要 Swing UI 支持，当前暂时禁用
 */
class InsertNewLineAction(
    private val getSessionObject: () -> SessionObject?
) : AnAction("Insert New Line") {
    
    companion object {
        private val logger = Logger.getInstance(InsertNewLineAction::class.java)
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        // TODO: 实现 Swing UI 版本
        logger.debug("InsertNewLineAction: 需要 Swing UI 实现")
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = false  // 暂时禁用
    }
}

/**
 * Ctrl+J: 插入换行符（Unix风格快捷键）
 * 别名为 Shift+Enter
 */
class InsertNewLineAltAction(
    private val getSessionObject: () -> SessionObject?
) : AnAction("Insert New Line (Alt)") {
    
    private val insertNewLineAction = InsertNewLineAction(getSessionObject)
    
    override fun actionPerformed(e: AnActionEvent) {
        insertNewLineAction.actionPerformed(e)
    }
    
    override fun update(e: AnActionEvent) {
        insertNewLineAction.update(e)
    }
}