package com.claudecodeplus.core.interfaces

/**
 * 编辑器服务接口
 */
interface EditorService {
    /**
     * 创建编辑器组件
     */
    fun createEditor(content: String = ""): EditorComponent
    
    /**
     * 更新编辑器内容
     */
    fun updateContent(editor: EditorComponent, content: String)
    
    /**
     * 滚动到底部
     */
    fun scrollToBottom(editor: EditorComponent)
}

/**
 * 编辑器组件接口
 */
interface EditorComponent {
    fun getComponent(): javax.swing.JComponent
    fun getText(): String
    fun setText(text: String)
}