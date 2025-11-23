package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.EditToolCall
import com.claudecodeplus.server.tools.DiffRequest
import com.claudecodeplus.server.tools.EditOperation
import com.claudecodeplus.server.tools.IdeTools
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Edit 工具展示组件
 */
class EditToolDisplay(
    toolCall: EditToolCall,
    ideTools: IdeTools
) : BaseToolDisplay(toolCall, ideTools) {
    
    private val editToolCall = toolCall
    
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createLineBorder(java.awt.Color(0xE0E0E0), 1)
        
        panel.add(createToolHeader(), BorderLayout.NORTH)
        
        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.border = EmptyBorder(JBUI.insets(8))
        
        // 文件路径
        val filePathLabel = JLabel("<html><u>📄 ${editToolCall.filePath}</u></html>")
        filePathLabel.foreground = java.awt.Color(0x2196F3)
        filePathLabel.cursor = Cursor(Cursor.HAND_CURSOR)
        filePathLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                showDiff()
            }
        })
        infoPanel.add(filePathLabel)
        
        // 编辑信息
        infoPanel.add(Box.createVerticalStrut(4))
        val editInfo = JLabel("✏️ 编辑：${editToolCall.oldString.take(30)}... → ${editToolCall.newString.take(30)}...")
        editInfo.font = editInfo.font.deriveFont(11f)
        editInfo.foreground = java.awt.Color(0x666666)
        infoPanel.add(editInfo)
        
        panel.add(infoPanel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun showDiff() {
        ideTools.showDiff(
            DiffRequest(
                filePath = editToolCall.filePath,
                oldContent = editToolCall.oldString,
                newContent = editToolCall.newString,
                rebuildFromFile = true,
                edits = listOf(
                    EditOperation(
                        oldString = editToolCall.oldString,
                        newString = editToolCall.newString,
                        replaceAll = editToolCall.replaceAll
                    )
                )
            )
        )
    }
    
    override fun getToolDisplayName(): String = "Edit File"
}


