package com.claudecodeplus.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import com.claudecodeplus.ui.models.SessionObject

/**
 * 聊天输入框快捷键动作集合
 * 实现插件专用的文本编辑快捷键，仅在聊天输入框中生效
 */

/**
 * Ctrl+U: 删除光标位置到行首的文本
 * 标准Unix/Linux终端行为
 */
class DeleteToLineStartAction(
    private val getSessionObject: () -> SessionObject?
) : AnAction("Delete to Line Start") {
    
    companion object {
        private val logger = Logger.getInstance(DeleteToLineStartAction::class.java)
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val sessionObject = getSessionObject()
        if (sessionObject == null) {
            logger.debug("SessionObject is null, ignoring Ctrl+U action")
            return
        }
        
        val textFieldValue = sessionObject.inputTextFieldValue
        val currentText = textFieldValue.text
        val cursorPos = textFieldValue.selection.start
        
        logger.debug("Executing Ctrl+U: cursor at $cursorPos, text length ${currentText.length}")
        
        // 找到当前行的开始位置
        val lineStart = if (cursorPos == 0) {
            0
        } else {
            val lineBreakPos = currentText.lastIndexOf('\n', cursorPos - 1)
            if (lineBreakPos == -1) 0 else lineBreakPos + 1
        }
        
        // 删除从行首到光标位置的文本
        val newText = currentText.substring(0, lineStart) + 
                      currentText.substring(cursorPos)
        
        logger.debug("Deleting from $lineStart to $cursorPos, new text length: ${newText.length}")
        
        // 更新文本并将光标移动到行首
        sessionObject.updateInputText(
            TextFieldValue(
                text = newText,
                selection = TextRange(lineStart)
            )
        )
    }
    
    override fun update(e: AnActionEvent) {
        // 只有当有会话对象时才启用
        e.presentation.isEnabled = getSessionObject() != null
    }
}

/**
 * Shift+Enter: 插入换行符
 * 与现有逻辑保持一致的备用实现
 */
class InsertNewLineAction(
    private val getSessionObject: () -> SessionObject?
) : AnAction("Insert New Line") {
    
    companion object {
        private val logger = Logger.getInstance(InsertNewLineAction::class.java)
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val sessionObject = getSessionObject()
        if (sessionObject == null) {
            logger.debug("SessionObject is null, ignoring Shift+Enter action")
            return
        }
        
        val textFieldValue = sessionObject.inputTextFieldValue
        val currentPos = textFieldValue.selection.start
        val newText = textFieldValue.text.substring(0, currentPos) + "\n" + 
                      textFieldValue.text.substring(currentPos)
        val newPosition = currentPos + 1
        
        logger.debug("Inserting newline at position $currentPos")
        
        sessionObject.updateInputText(
            TextFieldValue(
                text = newText,
                selection = TextRange(newPosition)
            )
        )
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = getSessionObject() != null
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