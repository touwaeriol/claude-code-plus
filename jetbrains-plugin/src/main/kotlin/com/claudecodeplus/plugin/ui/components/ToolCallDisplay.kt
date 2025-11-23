package com.claudecodeplus.plugin.ui.components

import com.claudecodeplus.server.tools.IdeTools
import com.claudecodeplus.server.tools.DiffRequest
import com.claudecodeplus.server.tools.EditOperation
import com.claudecodeplus.sdk.types.*
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 工具调用显示组件
 * 
 * 用于显示工具调用的信息，包括工具类型、参数、状态等
 * 支持折叠/展开状态和点击交互（类似 Vue UI 的 CompactToolCard）
 */
class ToolCallDisplay(
    private val toolUse: SpecificToolUse,
    private val ideTools: IdeTools,
    private val status: ToolCallStatus = ToolCallStatus.RUNNING,
    private val result: String? = null,
    private val onFileClick: ((String) -> Unit)? = null
) {
    
    private var isExpanded = false
    private var mainPanel: JPanel? = null
    private var detailsPanel: JPanel? = null
    
    /**
     * 创建工具调用显示组件
     */
    fun createComponent(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(getStatusColor(status), 2),
            EmptyBorder(JBUI.insets(8))
        )
        panel.background = getBackgroundColor(status)
        panel.alignmentX = 0f
        panel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        
        mainPanel = panel
        
        // 工具头部（始终显示）
        panel.add(createToolHeader())
        
        // 详细信息面板（可折叠）
        detailsPanel = createDetailsPanel()
        detailsPanel?.isVisible = isExpanded
        panel.add(detailsPanel!!)
        
        // 添加整体点击监听器
        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                handleCardClick()
            }
            
            override fun mouseEntered(e: MouseEvent) {
                panel.background = getBackgroundColor(status).brighter()
            }
            
            override fun mouseExited(e: MouseEvent) {
                panel.background = getBackgroundColor(status)
            }
        })
        
        return panel
    }
    
    /**
     * 创建详细信息面板
     */
    private fun createDetailsPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = getBackgroundColor(status)
        panel.alignmentX = 0f
        
        panel.add(Box.createVerticalStrut(8))
        
        // 工具参数
        panel.add(createToolParameters())
        
        // 工具结果（如果有）
        if (result != null) {
            panel.add(Box.createVerticalStrut(8))
            panel.add(createToolResult())
        }
        
        return panel
    }
    
    /**
     * 处理卡片点击事件
     * 根据工具类型执行不同的操作，类似 Vue UI 的行为
     */
    private fun handleCardClick() {
        // 只有成功状态的工具才能执行IDE操作
        if (status == ToolCallStatus.SUCCESS) {
            when (toolUse) {
                is ReadToolUse -> {
                    // ReadToolUse: 点击打开文件
                    ideTools.openFile(toolUse.filePath).fold(
                        onSuccess = { },
                        onFailure = { error ->
                            JOptionPane.showMessageDialog(
                                mainPanel,
                                "Failed to open file: ${error.message}",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    )
                }
                is EditToolUse -> {
                    // EditToolUse: 点击显示 diff（使用 rebuildFromFile=true）
                    ideTools.showDiff(
                        DiffRequest(
                            filePath = toolUse.filePath,
                            oldContent = toolUse.oldString,
                            newContent = toolUse.newString,
                            rebuildFromFile = true,
                            edits = listOf(
                                EditOperation(
                                    oldString = toolUse.oldString,
                                    newString = toolUse.newString,
                                    replaceAll = false
                                )
                            )
                        )
                    ).fold(
                        onSuccess = { },
                        onFailure = { error ->
                            JOptionPane.showMessageDialog(
                                mainPanel,
                                "Failed to show diff: ${error.message}",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    )
                }
                is MultiEditToolUse -> {
                    // MultiEditToolUse: 点击显示 diff（使用 rebuildFromFile=true）
                    val edits = toolUse.edits.map { edit ->
                        EditOperation(
                            oldString = edit.oldString,
                            newString = edit.newString,
                            replaceAll = edit.replaceAll ?: false
                        )
                    }
                    
                    ideTools.showDiff(
                        DiffRequest(
                            filePath = toolUse.filePath,
                            oldContent = "", // 从文件重建
                            newContent = "", // 从文件重建
                            rebuildFromFile = true,
                            edits = edits
                        )
                    ).fold(
                        onSuccess = { },
                        onFailure = { error ->
                            JOptionPane.showMessageDialog(
                                mainPanel,
                                "Failed to show diff: ${error.message}",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    )
                }
                is WriteToolUse -> {
                    // WriteToolUse: 点击打开文件
                    ideTools.openFile(toolUse.filePath).fold(
                        onSuccess = { },
                        onFailure = { error ->
                            JOptionPane.showMessageDialog(
                                mainPanel,
                                "Failed to open file: ${error.message}",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    )
                }
                else -> {
                    // 其他工具：切换展开/折叠状态
                    toggleExpand()
                }
            }
        } else {
            // 非成功状态：仅切换展开/折叠
            toggleExpand()
        }
    }
    
    /**
     * 切换展开/折叠状态
     */
    private fun toggleExpand() {
        isExpanded = !isExpanded
        detailsPanel?.isVisible = isExpanded
        mainPanel?.revalidate()
        mainPanel?.repaint()
    }
    
    /**
     * 创建工具头部
     */
    private fun createToolHeader(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.background = getBackgroundColor(status)
        
        // 左侧：图标、工具名称、文件名（如果有）
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        leftPanel.background = getBackgroundColor(status)
        
        val iconLabel = JLabel(getToolIcon(toolUse.toolType))
        iconLabel.font = iconLabel.font.deriveFont(16f)
        leftPanel.add(iconLabel)
        
        val toolName = getToolDisplayName(toolUse)
        val fileName = getToolFileName(toolUse)
        
        val displayText = if (fileName != null) {
            "$toolName: $fileName"
        } else {
            toolName
        }
        
        val nameLabel = JLabel(displayText)
        nameLabel.font = nameLabel.font.deriveFont(Font.BOLD)
        nameLabel.foreground = getTextColor(status)
        leftPanel.add(nameLabel)
        
        // 右侧：状态徽章
        val statusBadge = createStatusBadge()
        
        panel.add(leftPanel, BorderLayout.WEST)
        panel.add(statusBadge, BorderLayout.EAST)
        
        return panel
    }
    
    /**
     * 获取工具的文件名（如果工具涉及文件操作）
     */
    private fun getToolFileName(toolUse: SpecificToolUse): String? {
        return when (toolUse) {
            is ReadToolUse -> java.io.File(toolUse.filePath).name
            is WriteToolUse -> java.io.File(toolUse.filePath).name
            is EditToolUse -> java.io.File(toolUse.filePath).name
            is MultiEditToolUse -> java.io.File(toolUse.filePath).name
            is GrepToolUse -> toolUse.path?.let { java.io.File(it).name }
            is GlobToolUse -> toolUse.path?.let { java.io.File(it).name }
            else -> null
        }
    }
    
    /**
     * 创建工具参数显示
     */
    private fun createToolParameters(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = getBackgroundColor(status)
        panel.alignmentX = 0f
        
        // 根据工具类型显示不同的参数
        when (toolUse) {
            is ReadToolUse -> {
                panel.add(createFileParameter("File", toolUse.filePath, toolUse.filePath))
            }
            is WriteToolUse -> {
                panel.add(createFileParameter("File", toolUse.filePath, toolUse.filePath))
                panel.add(Box.createVerticalStrut(4))
                panel.add(createTextParameter("Content", toolUse.content, maxLines = 5))
            }
            is EditToolUse -> {
                panel.add(createFileParameter("File", toolUse.filePath, toolUse.filePath))
                panel.add(Box.createVerticalStrut(4))
                panel.add(createTextParameter("Old String", toolUse.oldString, maxLines = 3))
                panel.add(Box.createVerticalStrut(4))
                panel.add(createTextParameter("New String", toolUse.newString, maxLines = 3))
            }
            is MultiEditToolUse -> {
                panel.add(createFileParameter("File", toolUse.filePath, toolUse.filePath))
                panel.add(Box.createVerticalStrut(4))
                panel.add(createLabel("Edits: ${toolUse.edits.size} operations"))
            }
            is BashToolUse -> {
                panel.add(createTextParameter("Command", toolUse.command))
            }
            is GrepToolUse -> {
                panel.add(createTextParameter("Pattern", toolUse.pattern))
                toolUse.path?.let {
                    panel.add(Box.createVerticalStrut(4))
                    panel.add(createFileParameter("Path", it, it))
                }
            }
            is GlobToolUse -> {
                panel.add(createTextParameter("Pattern", toolUse.pattern))
                toolUse.path?.let {
                    panel.add(Box.createVerticalStrut(4))
                    panel.add(createFileParameter("Path", it, it))
                }
            }
            is TodoWriteToolUse -> {
                panel.add(createLabel("Todos: ${toolUse.todos.size} items"))
            }
            else -> {
                // 通用参数显示
                val params = toolUse.getTypedParameters()
                if (params.isNotEmpty()) {
                    params.forEach { (key, value) ->
                        panel.add(createLabel("$key: ${value.toString().take(100)}"))
                        panel.add(Box.createVerticalStrut(2))
                    }
                }
            }
        }
        
        return panel
    }
    
    /**
     * 创建文件参数（可点击）
     */
    private fun createFileParameter(label: String, value: String, filePath: String): JComponent {
        val panel = JPanel(BorderLayout())
        panel.background = getBackgroundColor(status)
        
        val labelComponent = JLabel("$label:")
        labelComponent.font = labelComponent.font.deriveFont(Font.BOLD, labelComponent.font.size - 1f)
        labelComponent.foreground = getTextColor(status).darker()
        
        val valueLabel = JLabel("<html><a href='#'>$value</a></html>")
        valueLabel.foreground = Color(0x0066CC)
        valueLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        valueLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onFileClick?.invoke(filePath) ?: run {
                    // 默认行为：打开文件
                    ideTools.openFile(filePath).fold(
                        onSuccess = { },
                        onFailure = { error ->
                            JOptionPane.showMessageDialog(
                                null,
                                "Failed to open file: ${error.message}",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    )
                }
            }
        })
        
        val contentPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        contentPanel.background = getBackgroundColor(status)
        contentPanel.add(labelComponent)
        contentPanel.add(valueLabel)
        
        panel.add(contentPanel, BorderLayout.WEST)
        
        return panel
    }
    
    /**
     * 创建文本参数
     */
    private fun createTextParameter(label: String, value: String, maxLines: Int = 10): JComponent {
        val panel = JPanel(BorderLayout())
        panel.background = getBackgroundColor(status)
        
        val labelComponent = JLabel("$label:")
        labelComponent.font = labelComponent.font.deriveFont(Font.BOLD, labelComponent.font.size - 1f)
        labelComponent.foreground = getTextColor(status).darker()
        labelComponent.border = EmptyBorder(JBUI.insets(0, 0, 4, 0))
        
        val textArea = JTextArea(value)
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, labelComponent.font.size - 1)
        textArea.foreground = getTextColor(status)
        textArea.background = getBackgroundColor(status).darker()
        textArea.isEditable = false
        textArea.isOpaque = true
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.rows = minOf(value.lines().size, maxLines)
        textArea.border = EmptyBorder(JBUI.insets(4))
        
        val scrollPane = JScrollPane(textArea)
        scrollPane.border = BorderFactory.createLineBorder(getBorderColor(status))
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.maximumSize = Dimension(Int.MAX_VALUE, 150)
        
        panel.add(labelComponent, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 创建标签
     */
    private fun createLabel(text: String): JComponent {
        val label = JLabel(text)
        label.font = label.font.deriveFont(label.font.size - 1f)
        label.foreground = getTextColor(status)
        label.alignmentX = 0f
        return label
    }
    
    /**
     * 创建工具结果显示
     */
    private fun createToolResult(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.background = getBackgroundColor(status)
        panel.border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(getBorderColor(status)),
            "Result"
        )
        
        val textArea = JTextArea(result ?: "")
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, textArea.font.size - 1)
        textArea.foreground = getTextColor(status)
        textArea.background = getBackgroundColor(status).darker()
        textArea.isEditable = false
        textArea.isOpaque = true
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.rows = minOf(result?.lines()?.size ?: 0, 10)
        textArea.border = EmptyBorder(JBUI.insets(4))
        
        val scrollPane = JScrollPane(textArea)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scrollPane.maximumSize = Dimension(Int.MAX_VALUE, 200)
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 创建状态徽章
     */
    private fun createStatusBadge(): JComponent {
        val label = JLabel(getStatusLabel(status))
        label.font = label.font.deriveFont(Font.BOLD, label.font.size - 2f)
        label.foreground = Color.WHITE
        label.background = getStatusColor(status)
        label.isOpaque = true
        label.border = EmptyBorder(JBUI.insets(2, 8, 2, 8))
        
        return label
    }
    
    /**
     * 获取工具图标
     */
    private fun getToolIcon(toolType: ToolType): String {
        return when (toolType) {
            ToolType.READ -> "📖"
            ToolType.WRITE -> "✏️"
            ToolType.EDIT -> "✂️"
            ToolType.MULTI_EDIT -> "🔧"
            ToolType.BASH -> "💻"
            ToolType.GREP -> "🔍"
            ToolType.GLOB -> "📁"
            ToolType.TODO_WRITE -> "✅"
            ToolType.WEB_SEARCH -> "🌐"
            ToolType.WEB_FETCH -> "📥"
            ToolType.MCP_TOOL -> "🔌"
            else -> "🔧"
        }
    }
    
    /**
     * 获取工具显示名称
     */
    private fun getToolDisplayName(toolUse: SpecificToolUse): String {
        return when (toolUse) {
            is ReadToolUse -> "Read File"
            is WriteToolUse -> "Write File"
            is EditToolUse -> "Edit File"
            is MultiEditToolUse -> "Multi Edit"
            is BashToolUse -> "Bash"
            is GrepToolUse -> "Grep"
            is GlobToolUse -> "Glob"
            is TodoWriteToolUse -> "Todo Write"
            is WebSearchToolUse -> "Web Search"
            is WebFetchToolUse -> "Web Fetch"
            is McpToolUse -> "MCP: ${toolUse.serverName}.${toolUse.functionName}"
            else -> toolUse.name
        }
    }
    
    /**
     * 获取状态颜色
     */
    private fun getStatusColor(status: ToolCallStatus): Color {
        return when (status) {
            ToolCallStatus.RUNNING -> Color(0xFFA500) // Orange
            ToolCallStatus.SUCCESS -> Color(0x4CAF50) // Green
            ToolCallStatus.FAILED -> Color(0xF44336)  // Red
        }
    }
    
    /**
     * 获取背景颜色
     */
    private fun getBackgroundColor(status: ToolCallStatus): Color {
        val isDark = com.intellij.util.ui.UIUtil.isUnderDarcula()
        val baseColor = if (isDark) Color(0x3C3C3C) else Color(0xF5F5F5)
        
        return when (status) {
            ToolCallStatus.RUNNING -> baseColor
            ToolCallStatus.SUCCESS -> if (isDark) Color(0x2D4A2D) else Color(0xE8F5E9)
            ToolCallStatus.FAILED -> if (isDark) Color(0x4A2D2D) else Color(0xFFEBEE)
        }
    }
    
    /**
     * 获取文本颜色
     */
    private fun getTextColor(status: ToolCallStatus): Color {
        val isDark = com.intellij.util.ui.UIUtil.isUnderDarcula()
        return if (isDark) Color(0xCCCCCC) else Color(0x000000)
    }
    
    /**
     * 获取边框颜色
     */
    private fun getBorderColor(status: ToolCallStatus): Color {
        return getStatusColor(status).darker()
    }
    
    /**
     * 获取状态标签
     */
    private fun getStatusLabel(status: ToolCallStatus): String {
        return when (status) {
            ToolCallStatus.RUNNING -> "Running..."
            ToolCallStatus.SUCCESS -> "Success"
            ToolCallStatus.FAILED -> "Failed"
        }
    }
}

/**
 * 工具调用状态
 */
enum class ToolCallStatus {
    RUNNING,
    SUCCESS,
    FAILED
}

