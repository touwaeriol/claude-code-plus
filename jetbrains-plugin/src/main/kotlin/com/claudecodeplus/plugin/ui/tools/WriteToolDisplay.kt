package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.WriteToolCall
import com.claudecodeplus.server.tools.IdeTools
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Write 工具展示组件
 */
class WriteToolDisplay(
    toolCall: WriteToolCall,
    ideTools: IdeTools
) : BaseToolDisplay(toolCall, ideTools) {
    
    private val writeToolCall = toolCall
    
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createLineBorder(java.awt.Color(0xE0E0E0), 1)
        
        panel.add(createToolHeader(), BorderLayout.NORTH)
        
        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.border = EmptyBorder(JBUI.insets(8))
        
        // 文件路径
        val filePath = writeToolCall.filePath ?: "未知文件"
        val filePathLabel = JLabel("<html><u>📄 $filePath</u></html>")
        filePathLabel.foreground = java.awt.Color(0x2196F3)
        filePathLabel.cursor = Cursor(Cursor.HAND_CURSOR)
        filePathLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                writeToolCall.filePath?.let { ideTools.openFile(it) }
            }
        })
        infoPanel.add(filePathLabel)
        
        panel.add(infoPanel, BorderLayout.CENTER)
        createResultPanel()?.let { panel.add(it, BorderLayout.SOUTH) }
        
        return panel
    }
    
    override fun getToolDisplayName(): String = "Write File"
}


