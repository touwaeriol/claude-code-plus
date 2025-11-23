package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.ReadToolCall
import com.claudecodeplus.plugin.types.ToolResult
import com.claudecodeplus.server.tools.IdeTools
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Read 工具展示组件
 * 
 * 对应 frontend/src/components/tools/ReadToolDisplay.vue
 */
class ReadToolDisplay(
    toolCall: ReadToolCall,
    ideTools: IdeTools
) : BaseToolDisplay(toolCall, ideTools) {
    
    private val readToolCall = toolCall
    
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createLineBorder(java.awt.Color(0xE0E0E0), 1)
        panel.background = java.awt.Color.WHITE
        
        // 头部
        panel.add(createToolHeader(), BorderLayout.NORTH)
        
        // 文件信息
        val infoPanel = createFileInfoPanel()
        panel.add(infoPanel, BorderLayout.CENTER)
        
        // 结果展示
        if (readToolCall.result != null) {
            createResultPanel()?.let {
                panel.add(it, BorderLayout.SOUTH)
            }
        }
        
        return panel
    }
    
    private fun createFileInfoPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = EmptyBorder(JBUI.insets(8))
        panel.isOpaque = false
        
        // 文件路径（可点击）
        val filePath = readToolCall.filePath ?: "未知文件"
        val filePathLabel = JLabel("<html><u>📄 $filePath</u></html>")
        filePathLabel.foreground = java.awt.Color(0x2196F3)
        filePathLabel.cursor = Cursor(Cursor.HAND_CURSOR)
        filePathLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                openFile()
            }
        })
        panel.add(filePathLabel)
        
        // 行号范围
        val lineRange = getLineRange()
        if (lineRange.isNotEmpty()) {
            panel.add(Box.createVerticalStrut(4))
            val rangeLabel = JLabel("📍 $lineRange")
            rangeLabel.font = rangeLabel.font.deriveFont(11f)
            rangeLabel.foreground = java.awt.Color(0x666666)
            panel.add(rangeLabel)
        }
        
        return panel
    }
    
    private fun getLineRange(): String {
        val viewRange = readToolCall.viewRange
        if (viewRange != null) {
            return "L${viewRange.first}-${viewRange.second}"
        }
        
        val offset = readToolCall.offset
        val limit = readToolCall.limit
        if (offset != null) {
            val end = if (limit != null) offset + limit - 1 else "∞"
            return "L$offset-$end"
        }
        
        return ""
    }
    
    private fun openFile() {
        val filePath = readToolCall.filePath ?: return
        val viewRange = readToolCall.viewRange
        
        if (viewRange != null) {
            ideTools.openFile(filePath, viewRange.first, viewRange.second)
        } else {
            ideTools.openFile(filePath)
        }
    }
    
    override fun getToolDisplayName(): String = "Read File"
}


