package com.claudecodeplus.plugin.ui.components

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.ui.JBUI
import java.awt.datatransfer.StringSelection
import java.awt.FlowLayout
import javax.swing.*

/**
 * 消息操作面板
 * 
 * 提供消息的编辑、删除、复制、重新生成等操作
 */
class MessageActionPanel(
    private val messageContent: String,
    private val messageIndex: Int,
    private val isUserMessage: Boolean,
    private val onEdit: ((Int) -> Unit)? = null,
    private val onDelete: ((Int) -> Unit)? = null,
    private val onCopy: (() -> Unit)? = null,
    private val onRegenerate: ((Int) -> Unit)? = null
) : JPanel() {
    
    init {
        layout = FlowLayout(FlowLayout.RIGHT, 4, 2)
        border = JBUI.Borders.empty(0, 4)
        isOpaque = false
        createButtons()
    }
    
    private fun createButtons() {
        // 复制按钮
        if (onCopy != null) {
            val copyButton = createActionButton("复制", "将消息复制到剪贴板") {
                copyToClipboard()
                onCopy.invoke()
            }
            add(copyButton)
        }
        
        // 编辑按钮（仅用户消息）
        if (isUserMessage && onEdit != null) {
            val editButton = createActionButton("编辑", "编辑此消息") {
                onEdit.invoke(messageIndex)
            }
            add(editButton)
        }
        
        // 重新生成按钮（仅助手消息）
        if (!isUserMessage && onRegenerate != null) {
            val regenerateButton = createActionButton("重新生成", "重新生成此响应") {
                onRegenerate.invoke(messageIndex)
            }
            add(regenerateButton)
        }
        
        // 删除按钮
        if (onDelete != null) {
            val deleteButton = createActionButton("删除", "删除此消息") {
                val result = JOptionPane.showConfirmDialog(
                    this,
                    "确定要删除这条消息吗？",
                    "确认删除",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                )
                if (result == JOptionPane.YES_OPTION) {
                    onDelete.invoke(messageIndex)
                }
            }
            add(deleteButton)
        }
    }
    
    private fun createActionButton(text: String, tooltip: String, action: () -> Unit): JButton {
        val button = JButton(text)
        button.toolTipText = tooltip
        button.isBorderPainted = false
        button.isContentAreaFilled = false
        button.isFocusPainted = false
        button.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        button.font = button.font.deriveFont(11f)
        button.addActionListener { action() }
        
        // 鼠标悬停效果
        button.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(e: java.awt.event.MouseEvent) {
                button.isContentAreaFilled = true
            }
            
            override fun mouseExited(e: java.awt.event.MouseEvent) {
                button.isContentAreaFilled = false
            }
        })
        
        return button
    }
    
    private fun copyToClipboard() {
        val selection = StringSelection(messageContent)
        CopyPasteManager.getInstance().setContents(selection)
    }
    
    /**
     * 创建右键菜单
     */
    fun createContextMenu(): JPopupMenu {
        val menu = JPopupMenu()
        
        // 复制
        val copyItem = JMenuItem("复制")
        copyItem.addActionListener {
            copyToClipboard()
            onCopy?.invoke()
        }
        menu.add(copyItem)
        
        if (isUserMessage) {
            // 编辑
            val editItem = JMenuItem("编辑")
            editItem.addActionListener {
                onEdit?.invoke(messageIndex)
            }
            menu.add(editItem)
        } else {
            // 重新生成
            val regenerateItem = JMenuItem("重新生成")
            regenerateItem.addActionListener {
                onRegenerate?.invoke(messageIndex)
            }
            menu.add(regenerateItem)
        }
        
        menu.addSeparator()
        
        // 删除
        val deleteItem = JMenuItem("删除")
        deleteItem.addActionListener {
            val result = JOptionPane.showConfirmDialog(
                this,
                "确定要删除这条消息吗？",
                "确认删除",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )
            if (result == JOptionPane.YES_OPTION) {
                onDelete?.invoke(messageIndex)
            }
        }
        menu.add(deleteItem)
        
        return menu
    }
}


