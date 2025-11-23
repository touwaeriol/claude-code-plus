package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.MultiEditToolCall
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
 * MultiEdit 工具展示组件
 */
class MultiEditToolDisplay(
    toolCall: MultiEditToolCall,
    ideTools: IdeTools
) : BaseToolDisplay(toolCall, ideTools) {
    
    private val multiEditToolCall = toolCall
    
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createLineBorder(java.awt.Color(0xE0E0E0), 1)
        
        panel.add(createToolHeader(), BorderLayout.NORTH)
        
        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.border = EmptyBorder(JBUI.insets(8))
        
        // 文件路径
        val filePathLabel = JLabel("<html><u>📄 ${multiEditToolCall.filePath}</u></html>")
        filePathLabel.foreground = java.awt.Color(0x2196F3)
        filePathLabel.cursor = Cursor(Cursor.HAND_CURSOR)
        filePathLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                showDiff()
            }
        })
        infoPanel.add(filePathLabel)
        
        // 编辑数量
        infoPanel.add(Box.createVerticalStrut(4))
        val editCountLabel = JLabel("📝 ${multiEditToolCall.edits.size} 处修改")
        editCountLabel.font = editCountLabel.font.deriveFont(11f)
        editCountLabel.foreground = java.awt.Color(0x666666)
        infoPanel.add(editCountLabel)
        
        panel.add(infoPanel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun showDiff() {
        val edits = multiEditToolCall.edits.map {
            EditOperation(
                oldString = it.oldString,
                newString = it.newString,
                replaceAll = it.replaceAll
            )
        }
        
        ideTools.showDiff(
            DiffRequest(
                filePath = multiEditToolCall.filePath,
                oldContent = "",
                newContent = "",
                rebuildFromFile = true,
                edits = edits
            )
        )
    }
    
    override fun getToolDisplayName(): String = "Multi-Edit"
}


