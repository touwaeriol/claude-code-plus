package com.asakii.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * Git Generate 独立配置页面
 *
 * 在 Settings > Tools > Claude Code Plus > Git Generate 下显示
 * 用于配置 Git commit message 自动生成功能
 */
class GitGenerateConfigurable : SearchableConfigurable {

    private var mainPanel: JPanel? = null

    // UI 组件
    private var systemPromptArea: JBTextArea? = null
    private var userPromptArea: JBTextArea? = null
    private var toolsPanel: JPanel? = null
    private var toolsList: MutableList<String> = mutableListOf()
    private var showProgressCheckbox: JCheckBox? = null
    private var saveSessionCheckbox: JCheckBox? = null

    override fun getId(): String = "claude-code-plus.git-generate"

    override fun getDisplayName(): String = "Git Generate"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout())

        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.border = JBUI.Borders.empty(8, 10, 8, 10)

        // 标题说明
        contentPanel.add(createDescription("Configure AI-powered Git commit message generation."))
        contentPanel.add(Box.createVerticalStrut(8))

        // 通知：需要启用 Git MCP
        val noticePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            val noticeLabel = JBLabel("<html><font color='#6B7280'>Note: Git Generate requires JetBrains Git MCP to be enabled in MCP settings.</font></html>")
            add(noticeLabel)
        }
        contentPanel.add(noticePanel)
        contentPanel.add(Box.createVerticalStrut(16))

        // Show Progress 复选框
        showProgressCheckbox = JCheckBox("Show detailed progress during generation").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            toolTipText = "When enabled, displays a dialog showing AI's tool calls and thinking process"
        }
        contentPanel.add(showProgressCheckbox)
        contentPanel.add(Box.createVerticalStrut(8))

        // Save Session 复选框
        saveSessionCheckbox = JCheckBox("Save session to history").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            toolTipText = "When enabled, the generation session will be saved and visible in Claude Code session history"
        }
        contentPanel.add(saveSessionCheckbox)
        contentPanel.add(Box.createVerticalStrut(16))

        // System Prompt 区域
        contentPanel.add(createSectionTitle("System Prompt"))
        contentPanel.add(createDescription("Instructions for the AI on how to generate commit messages."))
        contentPanel.add(Box.createVerticalStrut(4))

        systemPromptArea = JBTextArea(10, 70).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = true
            wrapStyleWord = true
        }
        val systemPromptScrollPane = JBScrollPane(systemPromptArea).apply {
            preferredSize = Dimension(700, 200)
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        contentPanel.add(systemPromptScrollPane)
        contentPanel.add(Box.createVerticalStrut(16))

        // User Prompt 区域
        contentPanel.add(createSectionTitle("User Prompt"))
        contentPanel.add(createDescription("Runtime prompt sent with the code changes. Customize analysis focus here."))
        contentPanel.add(Box.createVerticalStrut(4))

        userPromptArea = JBTextArea(5, 70).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = true
            wrapStyleWord = true
        }
        val userPromptScrollPane = JBScrollPane(userPromptArea).apply {
            preferredSize = Dimension(700, 100)
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        contentPanel.add(userPromptScrollPane)
        contentPanel.add(Box.createVerticalStrut(16))

        // Allowed Tools 区域
        contentPanel.add(createSectionTitle("Allowed Tools"))
        contentPanel.add(createDescription("Tools the AI can use during commit message generation."))
        contentPanel.add(Box.createVerticalStrut(4))

        // 标签容器 + 添加输入框
        val toolsContainer = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }

        // 标签流式布局面板
        toolsPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }

        // 添加工具的下拉框（带自动补全）
        val toolSuggestions = KnownTools.ALL.filter { !toolsList.contains(it) }.toTypedArray()
        val addToolCombo = ComboBox(DefaultComboBoxModel(toolSuggestions)).apply {
            isEditable = true
            preferredSize = Dimension(350, preferredSize.height)
            toolTipText = "Select a known tool or type custom tool name"
            (editor.editorComponent as? JTextField)?.let { tf ->
                tf.font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            }
        }

        val addButton = JButton("+").apply {
            preferredSize = Dimension(40, addToolCombo.preferredSize.height)
            toolTipText = "Add tool"
        }

        // 更新下拉列表（过滤已添加的工具）
        fun updateToolSuggestions() {
            val currentText = addToolCombo.editor.item?.toString() ?: ""
            val available = KnownTools.ALL.filter { !toolsList.contains(it) }
            addToolCombo.model = DefaultComboBoxModel(available.toTypedArray())
            addToolCombo.editor.item = currentText
        }

        // 添加工具的逻辑
        fun addSelectedTool() {
            val toolName = (addToolCombo.editor.item?.toString() ?: "").trim()
            if (toolName.isNotEmpty() && !toolsList.contains(toolName)) {
                addToolTag(toolName)
                addToolCombo.editor.item = ""
                updateToolSuggestions()
            }
        }

        addButton.addActionListener { addSelectedTool() }
        (addToolCombo.editor.editorComponent as? JTextField)?.addActionListener { addSelectedTool() }

        val inputRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(addToolCombo)
            add(addButton)
            add(JBLabel("<html><font color='#9CA3AF' size='-1'>Select or type, then click + or press Enter</font></html>"))
        }

        toolsContainer.add(toolsPanel!!, BorderLayout.CENTER)
        toolsContainer.add(inputRow, BorderLayout.SOUTH)

        val toolsScrollPane = JBScrollPane(toolsContainer).apply {
            preferredSize = Dimension(700, 100)
            alignmentX = JPanel.LEFT_ALIGNMENT
            border = BorderFactory.createLineBorder(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        contentPanel.add(toolsScrollPane)
        contentPanel.add(Box.createVerticalStrut(16))

        // 按钮行
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }

        val resetButton = JButton("Reset to Default").apply {
            toolTipText = "Reset all fields to their default values"
        }
        resetButton.addActionListener {
            systemPromptArea?.text = GitGenerateDefaults.SYSTEM_PROMPT
            userPromptArea?.text = GitGenerateDefaults.USER_PROMPT
            setTools(GitGenerateDefaults.TOOLS)
        }
        buttonPanel.add(resetButton)

        contentPanel.add(buttonPanel)
        contentPanel.add(Box.createVerticalGlue())

        // 包装滚动面板
        val scrollPane = JBScrollPane(contentPanel).apply {
            border = null
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        mainPanel!!.add(scrollPane, BorderLayout.CENTER)

        reset()
        return mainPanel!!
    }

    /**
     * 添加工具标签
     */
    private fun addToolTag(toolName: String) {
        if (toolsList.contains(toolName)) return
        toolsList.add(toolName)

        val tagPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBUI.CurrentTheme.ActionButton.hoverBorder(), 1, true),
                JBUI.Borders.empty(2, 6, 2, 4)
            )
            background = JBUI.CurrentTheme.ActionButton.hoverBackground()
            isOpaque = true
        }

        val label = JBLabel(toolName).apply {
            font = font.deriveFont(11f)
        }

        val removeBtn = JBLabel("×").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Remove"
        }
        removeBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                toolsList.remove(toolName)
                toolsPanel?.remove(tagPanel)
                toolsPanel?.revalidate()
                toolsPanel?.repaint()
            }
        })

        tagPanel.add(label)
        tagPanel.add(removeBtn)
        toolsPanel?.add(tagPanel)
        toolsPanel?.revalidate()
        toolsPanel?.repaint()
    }

    /**
     * 设置工具列表（清空后重新添加）
     */
    private fun setTools(tools: List<String>) {
        toolsList.clear()
        toolsPanel?.removeAll()
        tools.forEach { addToolTag(it) }
    }

    /**
     * 获取工具列表
     */
    private fun getTools(): List<String> = toolsList.toList()

    private fun createSectionTitle(text: String): JComponent {
        return JBLabel("<html><b>$text</b></html>").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            font = font.deriveFont(13f)
        }
    }

    private fun createDescription(text: String): JComponent {
        return JBLabel("<html><font color='#6B7280'>$text</font></html>").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
    }

    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()

        val effectiveSystemPrompt = settings.gitGenerateSystemPrompt.ifBlank { GitGenerateDefaults.SYSTEM_PROMPT }
        val effectiveUserPrompt = settings.gitGenerateUserPrompt.ifBlank { GitGenerateDefaults.USER_PROMPT }
        val effectiveTools = settings.getGitGenerateTools().takeIf { it.isNotEmpty() } ?: GitGenerateDefaults.TOOLS

        return systemPromptArea?.text != effectiveSystemPrompt ||
            userPromptArea?.text != effectiveUserPrompt ||
            getTools() != effectiveTools ||
            showProgressCheckbox?.isSelected != settings.gitGenerateShowProgress ||
            saveSessionCheckbox?.isSelected != settings.gitGenerateSaveSession
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()

        // 如果内容与默认值相同，存储空字符串
        val currentSystemPrompt = systemPromptArea?.text ?: ""
        val currentUserPrompt = userPromptArea?.text ?: ""

        settings.gitGenerateSystemPrompt = if (currentSystemPrompt.trim() == GitGenerateDefaults.SYSTEM_PROMPT.trim()) "" else currentSystemPrompt
        settings.gitGenerateUserPrompt = if (currentUserPrompt.trim() == GitGenerateDefaults.USER_PROMPT.trim()) "" else currentUserPrompt

        val currentTools = getTools()
        if (currentTools == GitGenerateDefaults.TOOLS) {
            settings.setGitGenerateTools(emptyList())
        } else {
            settings.setGitGenerateTools(currentTools)
        }

        // 保存 Show Progress 设置
        settings.gitGenerateShowProgress = showProgressCheckbox?.isSelected ?: true

        // 保存 Save Session 设置
        settings.gitGenerateSaveSession = saveSessionCheckbox?.isSelected ?: false

        settings.notifyChange()
    }

    override fun reset() {
        val settings = AgentSettingsService.getInstance()

        systemPromptArea?.text = settings.gitGenerateSystemPrompt.ifBlank { GitGenerateDefaults.SYSTEM_PROMPT }
        userPromptArea?.text = settings.gitGenerateUserPrompt.ifBlank { GitGenerateDefaults.USER_PROMPT }
        setTools(settings.getGitGenerateTools().takeIf { it.isNotEmpty() } ?: GitGenerateDefaults.TOOLS)
        showProgressCheckbox?.isSelected = settings.gitGenerateShowProgress
        saveSessionCheckbox?.isSelected = settings.gitGenerateSaveSession
    }

    override fun disposeUIResources() {
        systemPromptArea = null
        userPromptArea = null
        toolsPanel = null
        toolsList.clear()
        showProgressCheckbox = null
        saveSessionCheckbox = null
        mainPanel = null
    }
}
