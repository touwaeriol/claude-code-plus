package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.server.tools.IdeTools
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Diff 对比查看器组件
 * 
 * 对应 frontend/src/components/tools/DiffViewer.vue
 */
class DiffViewerPanel(
    private val filePath: String,
    private val oldContent: String,
    private val newContent: String,
    private val ideTools: IdeTools
) {
    
    fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createLineBorder(Color(0xE0E0E0), 1)
        
        // 头部：文件名
        val header = JPanel(BorderLayout())
        header.background = Color(0xF5F5F5)
        header.border = EmptyBorder(JBUI.insets(8))
        
        val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')
        val fileLabel = JLabel("📄 $fileName")
        fileLabel.font = fileLabel.font.deriveFont(Font.BOLD)
        header.add(fileLabel, BorderLayout.WEST)
        
        // 查看 Diff 按钮
        val viewDiffButton = JButton("查看完整 Diff")
        viewDiffButton.addActionListener {
            showFullDiff()
        }
        header.add(viewDiffButton, BorderLayout.EAST)
        
        panel.add(header, BorderLayout.NORTH)
        
        // 简单的对比显示
        val diffText = createSimpleDiff()
        val textArea = JTextArea(diffText)
        textArea.isEditable = false
        textArea.font = Font("JetBrains Mono", Font.PLAIN, 11)
        textArea.lineWrap = false
        
        val scrollPane = JScrollPane(textArea)
        scrollPane.preferredSize = java.awt.Dimension(600, 150)
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createSimpleDiff(): String {
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        
        val sb = StringBuilder()
        sb.appendLine("--- 原内容")
        oldLines.take(5).forEach { sb.appendLine("- $it") }
        if (oldLines.size > 5) sb.appendLine("... (${oldLines.size - 5} 行)")
        
        sb.appendLine("")
        sb.appendLine("+++ 新内容")
        newLines.take(5).forEach { sb.appendLine("+ $it") }
        if (newLines.size > 5) sb.appendLine("... (${newLines.size - 5} 行)")
        
        return sb.toString()
    }
    
    private fun showFullDiff() {
        ideTools.showDiff(
            com.claudecodeplus.server.tools.DiffRequest(
                filePath = filePath,
                oldContent = oldContent,
                newContent = newContent,
                rebuildFromFile = false
            )
        )
    }
}

