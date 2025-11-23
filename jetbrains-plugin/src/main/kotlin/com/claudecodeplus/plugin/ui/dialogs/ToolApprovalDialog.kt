package com.claudecodeplus.plugin.ui.dialogs

import com.claudecodeplus.sdk.types.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

/**
 * 工具调用批准对话框
 * 
 * 在工具调用前请求用户确认
 */
class ToolApprovalDialog(
    project: Project,
    private val toolUse: SpecificToolUse
) : DialogWrapper(project) {
    
    private var approved = false
    private val detailsArea = JTextArea()
    
    init {
        title = "工具调用确认"
        setOKButtonText("批准")
        setCancelButtonText("拒绝")
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(500, 350)
        panel.border = JBUI.Borders.empty(10)
        
        // 工具信息
        val infoPanel = createInfoPanel()
        panel.add(infoPanel, BorderLayout.NORTH)
        
        // 详细参数
        val detailsPanel = createDetailsPanel()
        panel.add(detailsPanel, BorderLayout.CENTER)
        
        // 警告信息
        if (isDestructiveTool()) {
            val warningLabel = JLabel("⚠️ 此操作可能会修改文件，请仔细检查！")
            warningLabel.foreground = java.awt.Color.RED
            warningLabel.border = JBUI.Borders.empty(10, 0, 0, 0)
            panel.add(warningLabel, BorderLayout.SOUTH)
        }
        
        return panel
    }
    
    private fun createInfoPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(0, 0, 10, 0)
        
        val titleLabel = JLabel(getToolDisplayName())
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        panel.add(titleLabel)
        
        val typeLabel = JLabel(getToolDescription())
        typeLabel.foreground = java.awt.Color.GRAY
        panel.add(typeLabel)
        
        return panel
    }
    
    private fun createDetailsPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        val label = JLabel("参数详情:")
        label.border = JBUI.Borders.empty(0, 0, 5, 0)
        panel.add(label, BorderLayout.NORTH)
        
        detailsArea.text = getToolDetails()
        detailsArea.isEditable = false
        detailsArea.lineWrap = true
        detailsArea.wrapStyleWord = true
        detailsArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        
        val scrollPane = JBScrollPane(detailsArea)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun getToolDisplayName(): String {
        return when (toolUse) {
            is ReadToolUse -> "读取文件"
            is WriteToolUse -> "写入文件"
            is EditToolUse -> "编辑文件"
            is MultiEditToolUse -> "批量编辑文件"
            is BashToolUse -> "执行命令"
            is GrepToolUse -> "搜索文件"
            is GlobToolUse -> "查找文件"
            is TodoWriteToolUse -> "更新待办事项"
            else -> "工具调用: ${toolUse.name}"
        }
    }
    
    private fun getToolDescription(): String {
        return when (toolUse) {
            is ReadToolUse -> "将读取文件的内容"
            is WriteToolUse -> "将写入或覆盖文件"
            is EditToolUse -> "将修改文件的部分内容"
            is MultiEditToolUse -> "将对文件进行多处修改"
            is BashToolUse -> "将在系统中执行命令"
            is GrepToolUse -> "将搜索文件内容"
            is GlobToolUse -> "将查找匹配的文件"
            is TodoWriteToolUse -> "将更新待办事项列表"
            else -> "将执行 ${toolUse.name} 操作"
        }
    }
    
    private fun getToolDetails(): String {
        return when (toolUse) {
            is ReadToolUse -> "文件路径: ${toolUse.filePath}"
            is WriteToolUse -> buildString {
                appendLine("文件路径: ${toolUse.filePath}")
                appendLine()
                appendLine("将写入以下内容:")
                appendLine("─".repeat(50))
                appendLine(toolUse.content)
            }
            is EditToolUse -> buildString {
                appendLine("文件路径: ${toolUse.filePath}")
                appendLine()
                appendLine("旧内容:")
                appendLine("─".repeat(50))
                appendLine(toolUse.oldString)
                appendLine()
                appendLine("新内容:")
                appendLine("─".repeat(50))
                appendLine(toolUse.newString)
            }
            is MultiEditToolUse -> buildString {
                appendLine("文件路径: ${toolUse.filePath}")
                appendLine("修改次数: ${toolUse.edits.size}")
                appendLine()
                toolUse.edits.forEachIndexed { index, edit ->
                    appendLine("修改 ${index + 1}:")
                    appendLine("  旧: ${edit.oldString.take(50)}${if (edit.oldString.length > 50) "..." else ""}")
                    appendLine("  新: ${edit.newString.take(50)}${if (edit.newString.length > 50) "..." else ""}")
                    appendLine()
                }
            }
            is BashToolUse -> buildString {
                appendLine("命令: ${toolUse.command}")
                if (toolUse.description != null) {
                    appendLine("描述: ${toolUse.description}")
                }
                if (toolUse.runInBackground) {
                    appendLine("后台运行: 是")
                }
            }
            is GrepToolUse -> buildString {
                appendLine("搜索模式: ${toolUse.pattern}")
                if (toolUse.path != null) {
                    appendLine("搜索路径: ${toolUse.path}")
                }
            }
            is GlobToolUse -> buildString {
                appendLine("匹配模式: ${toolUse.pattern}")
                if (toolUse.path != null) {
                    appendLine("搜索路径: ${toolUse.path}")
                }
            }
            is TodoWriteToolUse -> buildString {
                appendLine("待办事项数量: ${toolUse.todos.size}")
                appendLine()
                toolUse.todos.forEach { todo ->
                    appendLine("- [${todo.status}] ${todo.content}")
                }
            }
            else -> "参数: ${toolUse.getTypedParameters()}"
        }
    }
    
    private fun isDestructiveTool(): Boolean {
        return toolUse is WriteToolUse || 
               toolUse is EditToolUse || 
               toolUse is MultiEditToolUse || 
               toolUse is BashToolUse
    }
    
    override fun doOKAction() {
        approved = true
        super.doOKAction()
    }
    
    fun isApproved(): Boolean = approved
}


