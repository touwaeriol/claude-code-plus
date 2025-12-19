package com.asakii.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.swing.*
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * 自动换行的 FlowLayout
 */
class WrapLayout(align: Int = FlowLayout.LEFT, hgap: Int = 5, vgap: Int = 5) : FlowLayout(align, hgap, vgap) {
    override fun preferredLayoutSize(target: java.awt.Container): Dimension {
        return layoutSize(target, true)
    }

    override fun minimumLayoutSize(target: java.awt.Container): Dimension {
        return layoutSize(target, false)
    }

    private fun layoutSize(target: java.awt.Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            val targetWidth = target.width
            if (targetWidth == 0) {
                return if (preferred) super.preferredLayoutSize(target) else super.minimumLayoutSize(target)
            }

            val hgap = hgap
            val vgap = vgap
            val insets = target.insets
            val maxWidth = targetWidth - (insets.left + insets.right + hgap * 2)

            var rowWidth = 0
            var rowHeight = 0
            var height = insets.top + vgap

            for (i in 0 until target.componentCount) {
                val m = target.getComponent(i)
                if (m.isVisible) {
                    val d = if (preferred) m.preferredSize else m.minimumSize
                    if (rowWidth + d.width > maxWidth) {
                        height += rowHeight + vgap
                        rowWidth = 0
                        rowHeight = 0
                    }
                    rowWidth += d.width + hgap
                    rowHeight = maxOf(rowHeight, d.height)
                }
            }
            height += rowHeight + vgap + insets.bottom
            return Dimension(targetWidth, height)
        }
    }
}

/**
 * Agent 配置数据结构（用于 JSON 序列化）
 */
@Serializable
data class AgentsConfigData(
    val agents: Map<String, AgentConfigItem> = emptyMap()
)

@Serializable
data class AgentConfigItem(
    val enabled: Boolean = true,
    val description: String = "",
    val prompt: String = "",
    val tools: List<String> = emptyList(),
    val model: String = "", // 空字符串表示使用默认模型
    val selectionHint: String = "" // 主 AI 的子代理选择指引，告诉 AI 何时使用该子代理
)

/**
 * 模型下拉框渲染器
 */
class DefaultModelRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is DefaultModel) {
            text = value.displayName
        }
        return component
    }
}

/**
 * Claude Code 配置页面
 *
 * 包含两个 Tab：
 * 1. General - 通用配置（Node.js、模型、思考级别、权限）
 * 2. Agents - 子代理配置
 */
class ClaudeCodeConfigurable : SearchableConfigurable {

    private var mainPanel: JPanel? = null
    private lateinit var tabbedPane: JBTabbedPane

    // General Tab 组件
    private var defaultBypassPermissionsCheckbox: JBCheckBox? = null
    private var nodePathField: TextFieldWithBrowseButton? = null
    private var defaultModelCombo: ComboBox<DefaultModel>? = null
    private var defaultThinkingLevelCombo: ComboBox<ThinkingLevelConfig>? = null
    private var thinkTokensSpinner: JSpinner? = null
    private var ultraTokensSpinner: JSpinner? = null
    private var permissionModeCombo: ComboBox<String>? = null
    private var includePartialMessagesCheckbox: JBCheckBox? = null

    // Agents Tab 组件
    private var exploreEnabledCheckbox: JBCheckBox? = null
    private var exploreModelCombo: ComboBox<String>? = null
    private var exploreDescriptionArea: JBTextArea? = null
    private var explorePromptArea: JBTextArea? = null
    private var exploreSelectionHintArea: JBTextArea? = null  // 子代理选择指引
    private var exploreToolsPanel: JPanel? = null  // 标签容器
    private var exploreToolsList: MutableList<String> = mutableListOf()  // 工具列表
    private var exploreContentPanel: JPanel? = null
    private var exploreExpandedState: Boolean = false

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override fun getId(): String = "com.asakii.settings.claudecode"

    override fun getDisplayName(): String = "Claude Code"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout())

        // 创建选项卡
        tabbedPane = JBTabbedPane()

        // Tab 1: General
        val generalPanel = createGeneralPanel()
        tabbedPane.addTab("General", generalPanel)

        // Tab 2: Agents
        val agentsPanel = createAgentsPanel()
        tabbedPane.addTab("Agents", agentsPanel)

        mainPanel!!.add(tabbedPane, BorderLayout.CENTER)

        reset()
        return mainPanel!!
    }

    /**
     * 创建通用配置面板
     */
    private fun createGeneralPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(8, 10, 8, 10)

        // === 默认权限设置（放在最前面）===
        panel.add(createSectionTitle("Default Permissions"))
        panel.add(createDescription("Configure default permission behavior for new sessions."))

        defaultBypassPermissionsCheckbox = JBCheckBox("Default bypass permissions").apply {
            toolTipText = "When enabled, new sessions will automatically use bypass permissions mode"
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        panel.add(defaultBypassPermissionsCheckbox)
        panel.add(createDescription("  └ Skip confirmation dialogs for file edits and bash commands. Use with caution."))

        permissionModeCombo = ComboBox(DefaultComboBoxModel(arrayOf(
            "default", "acceptEdits", "plan", "bypassPermissions"
        ))).apply {
            toolTipText = "Default permission mode for new sessions"
        }
        panel.add(createLabeledRow("Mode:", permissionModeCombo!!))
        panel.add(createDescription("  default = Ask for each action | bypassPermissions = Auto-approve all actions"))

        includePartialMessagesCheckbox = JBCheckBox("Include partial messages in stream").apply {
            toolTipText = "Include partial/streaming messages in the response (always enabled)"
            isSelected = true
            isEnabled = false  // 禁止用户修改，始终保持开启
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        panel.add(includePartialMessagesCheckbox)
        panel.add(Box.createVerticalStrut(8))

        // === Runtime 设置 ===
        panel.add(createSeparator())
        panel.add(createSectionTitle("Runtime Settings"))
        panel.add(createDescription("Core configuration for Claude Code integration."))

        // Node.js 路径（使用 Kotlin UI DSL 2.0 兼容的 API）
        val nodePathPanel = panel {
            row("Node.js path:") {
                val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                    .withTitle("Select Node.js Executable")
                    .withDescription("Choose the path to node executable")
                textFieldWithBrowseButton(descriptor, null, null)
                    .applyToComponent {
                        toolTipText = "Leave empty to auto-detect from system PATH"
                        preferredSize = Dimension(450, preferredSize.height)
                        // 设置初始占位符，避免阻塞 UI
                        (textField as? JBTextField)?.let { tf ->
                            tf.emptyText.text = "Detecting Node.js..."
                            // 在后台线程检测 Node.js，避免阻塞 UI
                            com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                                val nodeInfo = AgentSettingsService.detectNodeInfo()
                                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                    tf.emptyText.text = nodeInfo?.let {
                                        if (it.version != null) "${it.path} (${it.version})" else it.path
                                    } ?: "Auto-detect from system PATH (Node.js not found)"
                                }
                            }
                        }
                        nodePathField = this
                    }
            }
        }.apply { alignmentX = JPanel.LEFT_ALIGNMENT }
        panel.add(nodePathPanel)
        panel.add(createDescription("  Path to Node.js executable. Leave empty to auto-detect from system PATH."))

        // 默认模型
        defaultModelCombo = ComboBox(DefaultComboBoxModel(DefaultModel.entries.toTypedArray())).apply {
            renderer = DefaultModelRenderer()
            toolTipText = "Default model for new sessions"
        }
        panel.add(createLabeledRow("Default model:", defaultModelCombo!!))
        panel.add(createDescription("  Opus 4.5 = Most capable | Sonnet 4.5 = Balanced | Haiku 4.5 = Fastest"))
        panel.add(Box.createVerticalStrut(8))

        // === 思考配置（合并在一起）===
        panel.add(createSeparator())
        panel.add(createSectionTitle("Thinking Configuration"))
        panel.add(createDescription("Configure thinking level and token budgets."))

        // 默认思考级别
        defaultThinkingLevelCombo = ComboBox<ThinkingLevelConfig>().apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): Component {
                    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is ThinkingLevelConfig) {
                        text = value.name
                        toolTipText = "${value.tokens} tokens"
                    }
                    return component
                }
            }
            toolTipText = "Default thinking level for new sessions"
        }
        panel.add(createLabeledRow("Default thinking:", defaultThinkingLevelCombo!!))

        // Token 配置（同一行显示）
        thinkTokensSpinner = JSpinner(SpinnerNumberModel(2048, 1, 128000, 256)).apply {
            toolTipText = "Token budget for Think level"
            preferredSize = Dimension(80, preferredSize.height)
            // 去掉数字的分组分隔符（逗号）
            editor = JSpinner.NumberEditor(this, "#")
        }
        ultraTokensSpinner = JSpinner(SpinnerNumberModel(8096, 1, 128000, 256)).apply {
            toolTipText = "Token budget for Ultra level"
            preferredSize = Dimension(80, preferredSize.height)
            // 去掉数字的分组分隔符（逗号）
            editor = JSpinner.NumberEditor(this, "#")
        }
        thinkTokensSpinner?.addChangeListener { updateThinkingLevelCombo() }
        ultraTokensSpinner?.addChangeListener { updateThinkingLevelCombo() }

        // 将 Think tokens 和 Ultra tokens 放在同一行
        val tokensRow = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("Think tokens:"))
            add(thinkTokensSpinner)
            add(Box.createHorizontalStrut(10))
            add(JBLabel("Ultra tokens:"))
            add(ultraTokensSpinner)
        }
        panel.add(tokensRow)

        panel.add(Box.createVerticalGlue())

        return JBScrollPane(panel).apply {
            border = null
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }.let { JPanel(BorderLayout()).apply { add(it, BorderLayout.CENTER) } }
    }

    /**
     * 创建 Agents 配置面板
     */
    private fun createAgentsPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(8, 10, 8, 10)

        // 说明
        panel.add(createDescription("Configure custom agents that extend Claude's capabilities."))
        panel.add(Box.createVerticalStrut(4))

        // 通知：仅对插件生效
        val noticePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            val noticeLabel = JBLabel("<html><font color='gray'>${ClaudeCodePlusBundle.message("agents.settings.notice")}</font></html>")
            add(noticeLabel)
        }
        panel.add(noticePanel)
        panel.add(Box.createVerticalStrut(6))

        // ExploreWithJetbrains Agent - 可折叠卡片
        panel.add(createCollapsibleAgentCard())
        panel.add(Box.createVerticalGlue())

        return JBScrollPane(panel).apply {
            border = null
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }.let { JPanel(BorderLayout()).apply { add(it, BorderLayout.CENTER) } }
    }

    /**
     * 创建可折叠的 Agent 配置卡片
     */
    private fun createCollapsibleAgentCard(): JPanel {
        val cardPanel = JPanel(BorderLayout())
        cardPanel.alignmentX = JPanel.LEFT_ALIGNMENT
        cardPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
            JBUI.Borders.empty(0)
        )

        // 头部区域（可点击展开/折叠）
        val headerPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(6, 8)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        // 左侧：展开/折叠箭头 + 启用复选框 + 名称
        val leftHeaderPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        val expandArrow = JBLabel("▶").apply {
            font = font.deriveFont(10f)
        }
        exploreEnabledCheckbox = JBCheckBox().apply {
            toolTipText = "Enable/disable this agent"
            isSelected = true
        }
        val agentNameLabel = JBLabel("<html><b>ExploreWithJetbrains</b></html>")
        leftHeaderPanel.add(expandArrow)
        leftHeaderPanel.add(exploreEnabledCheckbox)
        leftHeaderPanel.add(agentNameLabel)
        headerPanel.add(leftHeaderPanel, BorderLayout.WEST)

        // 右侧：模型选择
        val rightHeaderPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        rightHeaderPanel.add(JBLabel("Model:"))
        exploreModelCombo = ComboBox(DefaultComboBoxModel(arrayOf(
            "(inherit)", "opus", "sonnet", "haiku"
        ))).apply {
            toolTipText = "Model override for this agent (inherit = use default model)"
            preferredSize = Dimension(100, preferredSize.height)
        }
        rightHeaderPanel.add(exploreModelCombo)
        headerPanel.add(rightHeaderPanel, BorderLayout.EAST)

        cardPanel.add(headerPanel, BorderLayout.NORTH)

        // 内容区域（可折叠）
        exploreContentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4, 8, 8, 8)
            isVisible = false // 默认折叠
        }

        // Description
        val descLabel = JBLabel("Description:").apply { alignmentX = JPanel.LEFT_ALIGNMENT }
        exploreContentPanel!!.add(descLabel)
        exploreDescriptionArea = JBTextArea(2, 60).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true
            wrapStyleWord = true
        }
        val descScrollPane = JBScrollPane(exploreDescriptionArea).apply {
            preferredSize = Dimension(600, 45)
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        exploreContentPanel!!.add(descScrollPane)
        exploreContentPanel!!.add(Box.createVerticalStrut(6))

        // System Prompt
        val promptLabel = JBLabel("System Prompt:").apply { alignmentX = JPanel.LEFT_ALIGNMENT }
        exploreContentPanel!!.add(promptLabel)
        explorePromptArea = JBTextArea(8, 60).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true
            wrapStyleWord = true
        }
        val promptScrollPane = JBScrollPane(explorePromptArea).apply {
            preferredSize = Dimension(600, 150)
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        exploreContentPanel!!.add(promptScrollPane)
        exploreContentPanel!!.add(Box.createVerticalStrut(6))

        // Appended System Prompt (追加到主 AI 的系统提示词)
        val selectionHintLabel = JBLabel("Appended System Prompt:").apply { alignmentX = JPanel.LEFT_ALIGNMENT }
        exploreContentPanel!!.add(selectionHintLabel)
        exploreContentPanel!!.add(createDescription("  Appended to CLI's system prompt. Tells AI when/how to use this agent. Leave empty to skip."))
        exploreSelectionHintArea = JBTextArea(4, 60).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true
            wrapStyleWord = true
        }
        val selectionHintScrollPane = JBScrollPane(exploreSelectionHintArea).apply {
            preferredSize = Dimension(600, 80)
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        exploreContentPanel!!.add(selectionHintScrollPane)
        exploreContentPanel!!.add(Box.createVerticalStrut(6))

        // Allowed Tools - 标签样式
        val toolsLabel = JBLabel("Allowed Tools:").apply { alignmentX = JPanel.LEFT_ALIGNMENT }
        exploreContentPanel!!.add(toolsLabel)

        // 标签容器 + 添加输入框
        val toolsContainer = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }

        // 标签流式布局面板
        exploreToolsPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }

        // 添加工具的下拉框（带自动补全）
        val toolSuggestions = KnownTools.ALL.filter { !exploreToolsList.contains(it) }.toTypedArray()
        val addToolCombo = ComboBox(DefaultComboBoxModel(toolSuggestions)).apply {
            isEditable = true
            preferredSize = Dimension(250, preferredSize.height)
            toolTipText = "Select a known tool or type custom tool name"
            // 设置编辑器字体
            (editor.editorComponent as? JTextField)?.let { tf ->
                tf.font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            }
        }

        // 添加按钮
        val addButton = JButton("+").apply {
            preferredSize = Dimension(40, addToolCombo.preferredSize.height)
            toolTipText = "Add tool"
        }

        // 更新下拉列表（过滤已添加的工具）
        fun updateToolSuggestions() {
            val currentText = addToolCombo.editor.item?.toString() ?: ""
            val available = KnownTools.ALL.filter { !exploreToolsList.contains(it) }
            addToolCombo.model = DefaultComboBoxModel(available.toTypedArray())
            addToolCombo.editor.item = currentText
        }

        // 添加工具的逻辑
        fun addSelectedTool() {
            val toolName = (addToolCombo.editor.item?.toString() ?: "").trim()
            if (toolName.isNotEmpty() && !exploreToolsList.contains(toolName)) {
                addToolTag(toolName)
                addToolCombo.editor.item = ""
                updateToolSuggestions()
            }
        }

        addButton.addActionListener { addSelectedTool() }
        // Enter 键添加
        (addToolCombo.editor.editorComponent as? JTextField)?.addActionListener { addSelectedTool() }

        val inputRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(addToolCombo)
            add(addButton)
            add(JBLabel("<html><font color='gray' size='-1'>Select or type, then click + or press Enter</font></html>"))
        }

        toolsContainer.add(exploreToolsPanel!!, BorderLayout.CENTER)
        toolsContainer.add(inputRow, BorderLayout.SOUTH)

        val toolsScrollPane = JBScrollPane(toolsContainer).apply {
            preferredSize = Dimension(600, 90)
            alignmentX = JPanel.LEFT_ALIGNMENT
            border = BorderFactory.createLineBorder(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
            // 禁用水平滚动，强制工具标签换行
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        exploreContentPanel!!.add(toolsScrollPane)
        exploreContentPanel!!.add(Box.createVerticalStrut(6))

        // Reset 按钮
        val resetButton = JButton("Reset to Default").apply {
            preferredSize = Dimension(120, preferredSize.height)
        }
        resetButton.addActionListener {
            exploreDescriptionArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.description
            explorePromptArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt
            exploreSelectionHintArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint
            setTools(AgentDefaults.EXPLORE_WITH_JETBRAINS.tools)
            exploreModelCombo?.selectedItem = "(inherit)"
        }
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        buttonPanel.add(resetButton)
        exploreContentPanel!!.add(buttonPanel)

        cardPanel.add(exploreContentPanel!!, BorderLayout.CENTER)

        // 点击头部展开/折叠
        headerPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                // 如果点击的是复选框或下拉框区域，不处理折叠
                val clickedComponent = SwingUtilities.getDeepestComponentAt(headerPanel, e.x, e.y)
                if (clickedComponent is JBCheckBox || clickedComponent is ComboBox<*> ||
                    clickedComponent?.parent is ComboBox<*>) {
                    return
                }
                toggleAgentExpanded(expandArrow)
            }
        })

        return cardPanel
    }

    /**
     * 切换 Agent 卡片的展开/折叠状态
     */
    private fun toggleAgentExpanded(arrowLabel: JBLabel) {
        exploreExpandedState = !exploreExpandedState
        exploreContentPanel?.isVisible = exploreExpandedState
        arrowLabel.text = if (exploreExpandedState) "▼" else "▶"
        exploreContentPanel?.parent?.revalidate()
        exploreContentPanel?.parent?.repaint()
    }

    /**
     * 添加工具标签
     */
    private fun addToolTag(toolName: String) {
        if (exploreToolsList.contains(toolName)) return
        exploreToolsList.add(toolName)

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
                exploreToolsList.remove(toolName)
                exploreToolsPanel?.remove(tagPanel)
                exploreToolsPanel?.revalidate()
                exploreToolsPanel?.repaint()
            }
        })

        tagPanel.add(label)
        tagPanel.add(removeBtn)
        exploreToolsPanel?.add(tagPanel)
        exploreToolsPanel?.revalidate()
        exploreToolsPanel?.repaint()
    }

    /**
     * 设置工具列表（清空后重新添加）
     */
    private fun setTools(tools: List<String>) {
        exploreToolsList.clear()
        exploreToolsPanel?.removeAll()
        tools.forEach { addToolTag(it) }
    }

    /**
     * 获取当前工具列表
     */
    private fun getTools(): List<String> = exploreToolsList.toList()

    // UI 辅助方法
    private fun createSectionTitle(text: String): JComponent {
        return JBLabel("<html><b>$text</b></html>").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            border = JBUI.Borders.emptyTop(5)
        }
    }

    private fun createDescription(text: String): JComponent {
        return JBLabel("<html><font color='gray' size='-1'>$text</font></html>").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
    }

    private fun createSeparator(): JComponent {
        return JSeparator().apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 1)
        }
    }

    private fun createLabeledRow(label: String, component: JComponent): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 5, 2)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel(label))
            add(component)
        }
    }

    private fun updateThinkingLevelCombo() {
        val currentSelection = defaultThinkingLevelCombo?.selectedItem as? ThinkingLevelConfig
        val currentId = currentSelection?.id
        val allLevels = buildAllThinkingLevels()
        defaultThinkingLevelCombo?.model = DefaultComboBoxModel(allLevels.toTypedArray())
        defaultThinkingLevelCombo?.selectedItem = allLevels.find { it.id == currentId } ?: allLevels.find { it.id == "ultra" }
    }

    private fun buildAllThinkingLevels(): List<ThinkingLevelConfig> = listOf(
        ThinkingLevelConfig("off", "Off", 0, isCustom = false),
        ThinkingLevelConfig("think", "Think", thinkTokensSpinner?.value as? Int ?: 2048, isCustom = false),
        ThinkingLevelConfig("ultra", "Ultra", ultraTokensSpinner?.value as? Int ?: 8096, isCustom = false)
    )

    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()

        // General Tab
        val generalModified =
            nodePathField?.text != settings.nodePath ||
            (defaultModelCombo?.selectedItem as? DefaultModel)?.name != settings.defaultModel ||
            (defaultThinkingLevelCombo?.selectedItem as? ThinkingLevelConfig)?.id != settings.defaultThinkingLevelId ||
            (thinkTokensSpinner?.value as? Int ?: 2048) != settings.thinkTokens ||
            (ultraTokensSpinner?.value as? Int ?: 8096) != settings.ultraTokens ||
            permissionModeCombo?.selectedItem != settings.permissionMode ||
            defaultBypassPermissionsCheckbox?.isSelected != settings.defaultBypassPermissions

        // Agents Tab
        val currentConfig = parseAgentsConfig(settings.customAgents)
        val exploreConfig = currentConfig.agents["ExploreWithJetbrains"]
        val effectiveEnabled = exploreConfig?.enabled ?: true
        val effectiveModel = exploreConfig?.model?.ifBlank { "(inherit)" } ?: "(inherit)"
        val effectiveDescription = exploreConfig?.description?.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.description }
            ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.description
        val effectivePrompt = exploreConfig?.prompt?.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt }
            ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt
        val effectiveSelectionHint = exploreConfig?.selectionHint?.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint }
            ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint
        val effectiveTools = exploreConfig?.tools?.takeIf { it.isNotEmpty() }
            ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.tools

        val agentsModified =
            exploreEnabledCheckbox?.isSelected != effectiveEnabled ||
            exploreModelCombo?.selectedItem != effectiveModel ||
            exploreDescriptionArea?.text != effectiveDescription ||
            explorePromptArea?.text != effectivePrompt ||
            exploreSelectionHintArea?.text != effectiveSelectionHint ||
            getTools() != effectiveTools

        return generalModified || agentsModified
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()

        // General Tab
        settings.nodePath = nodePathField?.text ?: ""
        settings.defaultModel = (defaultModelCombo?.selectedItem as? DefaultModel)?.name ?: DefaultModel.OPUS_45.name
        settings.defaultThinkingLevelId = (defaultThinkingLevelCombo?.selectedItem as? ThinkingLevelConfig)?.id ?: "ultra"
        settings.thinkTokens = thinkTokensSpinner?.value as? Int ?: 2048
        settings.ultraTokens = ultraTokensSpinner?.value as? Int ?: 8096
        settings.permissionMode = permissionModeCombo?.selectedItem as? String ?: "default"
        settings.includePartialMessages = true
        settings.defaultBypassPermissions = defaultBypassPermissionsCheckbox?.isSelected ?: false

        // Agents Tab
        val selectedModel = exploreModelCombo?.selectedItem as? String ?: "(inherit)"
        val exploreConfig = AgentConfigItem(
            enabled = exploreEnabledCheckbox?.isSelected ?: true,
            description = exploreDescriptionArea?.text ?: "",
            prompt = explorePromptArea?.text ?: "",
            tools = getTools(),
            model = if (selectedModel == "(inherit)") "" else selectedModel,
            selectionHint = exploreSelectionHintArea?.text ?: ""
        )
        settings.customAgents = json.encodeToString(AgentsConfigData(agents = mapOf("ExploreWithJetbrains" to exploreConfig)))

        settings.notifyChange()
    }

    override fun reset() {
        val settings = AgentSettingsService.getInstance()

        // General Tab
        nodePathField?.text = settings.nodePath
        defaultModelCombo?.selectedItem = DefaultModel.fromName(settings.defaultModel) ?: DefaultModel.OPUS_45
        thinkTokensSpinner?.value = settings.thinkTokens
        ultraTokensSpinner?.value = settings.ultraTokens
        updateThinkingLevelCombo()
        defaultThinkingLevelCombo?.selectedItem = buildAllThinkingLevels().find { it.id == settings.defaultThinkingLevelId }
            ?: buildAllThinkingLevels().find { it.id == "ultra" }
        permissionModeCombo?.selectedItem = settings.permissionMode
        includePartialMessagesCheckbox?.isSelected = true
        defaultBypassPermissionsCheckbox?.isSelected = settings.defaultBypassPermissions

        // Agents Tab
        val currentConfig = parseAgentsConfig(settings.customAgents)
        val exploreConfig = currentConfig.agents["ExploreWithJetbrains"]
        if (exploreConfig != null && (exploreConfig.description.isNotBlank() || exploreConfig.prompt.isNotBlank())) {
            exploreEnabledCheckbox?.isSelected = exploreConfig.enabled
            exploreModelCombo?.selectedItem = exploreConfig.model.ifBlank { "(inherit)" }
            exploreDescriptionArea?.text = exploreConfig.description.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.description }
            explorePromptArea?.text = exploreConfig.prompt.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt }
            exploreSelectionHintArea?.text = exploreConfig.selectionHint.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint }
            setTools(exploreConfig.tools.takeIf { it.isNotEmpty() } ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.tools)
        } else {
            exploreEnabledCheckbox?.isSelected = true
            exploreModelCombo?.selectedItem = "(inherit)"
            exploreDescriptionArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.description
            explorePromptArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt
            exploreSelectionHintArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint
            setTools(AgentDefaults.EXPLORE_WITH_JETBRAINS.tools)
        }
    }

    private fun parseAgentsConfig(jsonStr: String): AgentsConfigData {
        return try {
            if (jsonStr.isBlank() || jsonStr == "{}") AgentsConfigData()
            else json.decodeFromString<AgentsConfigData>(jsonStr)
        } catch (e: Exception) {
            AgentsConfigData()
        }
    }

    override fun disposeUIResources() {
        nodePathField = null
        defaultModelCombo = null
        defaultThinkingLevelCombo = null
        thinkTokensSpinner = null
        ultraTokensSpinner = null
        permissionModeCombo = null
        includePartialMessagesCheckbox = null
        defaultBypassPermissionsCheckbox = null
        exploreEnabledCheckbox = null
        exploreModelCombo = null
        exploreDescriptionArea = null
        explorePromptArea = null
        exploreSelectionHintArea = null
        exploreToolsPanel = null
        exploreToolsList.clear()
        exploreContentPanel = null
        mainPanel = null
    }
}
