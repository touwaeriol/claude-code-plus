package com.asakii.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.asakii.plugin.compat.BrowseButtonCompat
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * 自动换行的 FlowLayout (保留供工具标签使用)
 */
class WrapLayout(align: Int = FlowLayout.LEFT, hgap: Int = 5, vgap: Int = 5) : FlowLayout(align, hgap, vgap) {
    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, true)
    override fun minimumLayoutSize(target: Container): Dimension = layoutSize(target, false)

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            val targetWidth = target.width
            if (targetWidth == 0) {
                return if (preferred) super.preferredLayoutSize(target) else super.minimumLayoutSize(target)
            }
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

@Serializable
data class AgentsConfigData(val agents: Map<String, AgentConfigItem> = emptyMap())

@Serializable
data class AgentConfigItem(
    val enabled: Boolean = true,
    val description: String = "",
    val prompt: String = "",
    val tools: List<String> = emptyList(),
    val model: String = "",
    val selectionHint: String = ""
)

class ModelInfoRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
    ): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is ModelInfo) {
            text = if (value.isBuiltIn) value.displayName else "${value.displayName} (custom)"
            toolTipText = value.modelId
        }
        return component
    }
}

/**
 * Claude Code 配置页面 - 使用 Kotlin UI DSL
 */
class ClaudeCodeConfigurable : SearchableConfigurable {

    private var mainPanel: JComponent? = null

    // General Tab 组件
    private var defaultBypassPermissionsCheckbox: JBCheckBox? = null
    private var defaultAutoCleanupContextsCheckbox: JBCheckBox? = null
    private var claudePathField: TextFieldWithBrowseButton? = null
    private var defaultModelCombo: ComboBox<ModelInfo>? = null
    private var defaultThinkingLevelCombo: ComboBox<ThinkingLevelConfig>? = null
    private var thinkTokensSpinner: JSpinner? = null
    private var ultraTokensSpinner: JSpinner? = null
    private var permissionModeCombo: ComboBox<String>? = null
    private var includePartialMessagesCheckbox: JBCheckBox? = null
    private var agentTeamsModeCombo: ComboBox<String>? = null

    // Custom Models 组件
    private var customModelsTable: JBTable? = null
    private var customModelsTableModel: DefaultTableModel? = null

    // Agents Tab 组件 - ExploreWithJetbrains
    private var exploreEnabledCheckbox: JBCheckBox? = null
    private var exploreModelCombo: ComboBox<String>? = null
    private var exploreDescriptionArea: JBTextArea? = null
    private var explorePromptArea: JBTextArea? = null
    private var exploreSelectionHintArea: JBTextArea? = null
    private var exploreToolsPanel: JPanel? = null
    private var exploreToolsList: MutableList<String> = mutableListOf()

    // Agents Tab 组件 - CodeWithJetbrains
    private var codeEnabledCheckbox: JBCheckBox? = null
    private var codeModelCombo: ComboBox<String>? = null
    private var codeDescriptionArea: JBTextArea? = null
    private var codePromptArea: JBTextArea? = null
    private var codeSelectionHintArea: JBTextArea? = null
    private var codeToolsPanel: JPanel? = null
    private var codeToolsList: MutableList<String> = mutableListOf()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun getId(): String = "com.asakii.settings.claudecode"
    override fun getDisplayName(): String = "Claude Code"

    override fun createComponent(): JComponent {
        val tabbedPane = JBTabbedPane()
        tabbedPane.addTab("General", createGeneralPanel())
        tabbedPane.addTab("Agents", createAgentsPanel())
        mainPanel = tabbedPane
        reset()
        return mainPanel!!
    }

    private fun createGeneralPanel(): JComponent {
        // 初始化组件
        defaultBypassPermissionsCheckbox = JBCheckBox("Default bypass permissions")
        defaultAutoCleanupContextsCheckbox = JBCheckBox("Default auto cleanup contexts")
        includePartialMessagesCheckbox = JBCheckBox("Include partial messages in stream").apply {
            isSelected = true
            isEnabled = false
        }
        permissionModeCombo = ComboBox(arrayOf("default", "acceptEdits", "plan", "bypassPermissions"))
        agentTeamsModeCombo = ComboBox(arrayOf("default", "enable", "disable"))

        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        claudePathField = TextFieldWithBrowseButton().apply {
            BrowseButtonCompat.addBrowseFolderListener(this, "Select Claude CLI Executable",
                "Choose the path to claude executable", null, descriptor)
            (textField as? JBTextField)?.let { tf ->
                tf.emptyText.text = "Detecting Claude CLI..."
                com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                    val claudeInfo = AgentSettingsService.detectClaudeInfo()
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        tf.emptyText.text = claudeInfo?.let {
                            if (it.version != null) "${it.path} (${it.version})" else it.path
                        } ?: "Auto-detect from system PATH"
                    }
                }
            }
        }
        defaultModelCombo = ComboBox<ModelInfo>().apply { renderer = ModelInfoRenderer() }
        refreshModelCombo()

        defaultThinkingLevelCombo = ComboBox<ThinkingLevelConfig>().apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): Component {
                    val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is ThinkingLevelConfig) {
                        text = value.name
                        toolTipText = "${value.tokens} tokens"
                    }
                    return c
                }
            }
        }

        thinkTokensSpinner = JSpinner(SpinnerNumberModel(2048, 1, 128000, 256)).apply {
            editor = JSpinner.NumberEditor(this, "#")
            addChangeListener { updateThinkingLevelCombo() }
        }
        ultraTokensSpinner = JSpinner(SpinnerNumberModel(8096, 1, 128000, 256)).apply {
            editor = JSpinner.NumberEditor(this, "#")
            addChangeListener { updateThinkingLevelCombo() }
        }

        // 自定义模型表格
        customModelsTableModel = object : DefaultTableModel(arrayOf("Display Name", "Model ID"), 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        customModelsTable = JBTable(customModelsTableModel).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            tableHeader.reorderingAllowed = false
        }

        return panel {
            group("Default Permissions") {
                row { cell(defaultBypassPermissionsCheckbox!!) }
                row { comment("Skip confirmation dialogs for file edits and bash commands.") }
                row { cell(defaultAutoCleanupContextsCheckbox!!) }
                row { comment("Enabled contexts are cleared after send; disabled contexts stay.") }
                row("Permission Mode:") { cell(permissionModeCombo!!).columns(COLUMNS_MEDIUM) }
                row { comment("default = Ask for each action | bypassPermissions = Auto-approve all") }
                row { cell(includePartialMessagesCheckbox!!) }
            }

            group("Runtime Settings") {
                row("Claude CLI path:") {
                    cell(claudePathField!!).align(AlignX.FILL).resizableColumn()
                }
                row { comment("Path to Claude CLI executable. Leave empty to auto-detect from system PATH.") }
                row("Default model:") { cell(defaultModelCombo!!).columns(COLUMNS_MEDIUM) }
                row { comment("Opus 4.5 = Most capable | Sonnet 4.5 = Balanced | Haiku 4.5 = Fastest") }
                row("Agent Teams:") { cell(agentTeamsModeCombo!!).columns(COLUMNS_MEDIUM) }
                row { comment("default = No env var | enable = CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1 | disable = 0") }
            }

            collapsibleGroup("Custom Models") {
                row {
                    cell(JBScrollPane(customModelsTable).apply {
                        preferredSize = Dimension(500, 100)
                    }).align(Align.FILL).resizableColumn()
                }.resizableRow()
                row {
                    button("Add") { showCustomModelDialog(null, null) { d, m ->
                        customModelsTableModel?.addRow(arrayOf(d, m))
                        refreshModelCombo()
                    }}
                    val editButton = button("Edit") {
                        val row = customModelsTable!!.selectedRow
                        if (row >= 0) {
                            val name = customModelsTableModel?.getValueAt(row, 0) as? String ?: ""
                            val id = customModelsTableModel?.getValueAt(row, 1) as? String ?: ""
                            showCustomModelDialog(name, id) { d, m ->
                                customModelsTableModel?.setValueAt(d, row, 0)
                                customModelsTableModel?.setValueAt(m, row, 1)
                                refreshModelCombo()
                            }
                        }
                    }
                    val removeButton = button("Remove") {
                        val row = customModelsTable!!.selectedRow
                        if (row >= 0) {
                            customModelsTableModel?.removeRow(row)
                            refreshModelCombo()
                        }
                    }
                    // 初始禁用按钮，通过选择监听器更新
                    editButton.component.isEnabled = false
                    removeButton.component.isEnabled = false
                    customModelsTable!!.selectionModel.addListSelectionListener {
                        val hasSelection = customModelsTable!!.selectedRow >= 0
                        editButton.component.isEnabled = hasSelection
                        removeButton.component.isEnabled = hasSelection
                    }
                }
            }

            group("Thinking Configuration") {
                row("Default thinking:") { cell(defaultThinkingLevelCombo!!).columns(COLUMNS_MEDIUM) }
                row {
                    label("Think tokens:")
                    cell(thinkTokensSpinner!!)
                    label("Ultra tokens:").gap(RightGap.SMALL)
                    cell(ultraTokensSpinner!!)
                }
            }
        }
    }

    private fun createAgentsPanel(): JComponent {
        // 初始化 ExploreWithJetbrains 组件
        exploreEnabledCheckbox = JBCheckBox("Enable")
        exploreModelCombo = ComboBox(arrayOf("(inherit)", "opus", "sonnet", "haiku"))
        exploreDescriptionArea = JBTextArea(2, 60).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true; wrapStyleWord = true
        }
        explorePromptArea = JBTextArea(6, 60).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true; wrapStyleWord = true
        }
        exploreSelectionHintArea = JBTextArea(3, 60).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true; wrapStyleWord = true
        }
        exploreToolsPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4))

        // 初始化 CodeWithJetbrains 组件
        codeEnabledCheckbox = JBCheckBox("Enable")
        codeModelCombo = ComboBox(arrayOf("(inherit)", "opus", "sonnet", "haiku"))
        codeDescriptionArea = JBTextArea(2, 60).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true; wrapStyleWord = true
        }
        codePromptArea = JBTextArea(6, 60).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true; wrapStyleWord = true
        }
        codeSelectionHintArea = JBTextArea(3, 60).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true; wrapStyleWord = true
        }
        codeToolsPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4))

        // 更新 ExploreWithJetbrains 依赖状态
        fun updateExploreDependency() {
            val mcpEnabled = AgentSettingsService.getInstance().enableJetBrainsMcp
            exploreEnabledCheckbox?.isEnabled = mcpEnabled
            exploreModelCombo?.isEnabled = mcpEnabled && (exploreEnabledCheckbox?.isSelected == true)
        }
        updateExploreDependency()
        exploreEnabledCheckbox!!.addActionListener { updateExploreDependency() }

        // 更新 CodeWithJetbrains 依赖状态
        fun updateCodeDependency() {
            val mcpEnabled = AgentSettingsService.getInstance().enableJetBrainsMcp
            codeEnabledCheckbox?.isEnabled = mcpEnabled
            codeModelCombo?.isEnabled = mcpEnabled && (codeEnabledCheckbox?.isSelected == true)
        }
        updateCodeDependency()
        codeEnabledCheckbox!!.addActionListener { updateCodeDependency() }

        // ExploreWithJetbrains 工具输入
        val exploreToolCombo = ComboBox(DefaultComboBoxModel(KnownTools.ALL.toTypedArray())).apply {
            isEditable = true
        }

        fun addExploreTool() {
            val name = (exploreToolCombo.editor.item?.toString() ?: "").trim()
            if (name.isNotEmpty() && !exploreToolsList.contains(name)) {
                addToolTag(name, exploreToolsList, exploreToolsPanel!!)
                exploreToolCombo.editor.item = ""
            }
        }

        // CodeWithJetbrains 工具输入
        val codeToolCombo = ComboBox(DefaultComboBoxModel(KnownTools.ALL.toTypedArray())).apply {
            isEditable = true
        }

        fun addCodeTool() {
            val name = (codeToolCombo.editor.item?.toString() ?: "").trim()
            if (name.isNotEmpty() && !codeToolsList.contains(name)) {
                addToolTag(name, codeToolsList, codeToolsPanel!!)
                codeToolCombo.editor.item = ""
            }
        }

        return panel {
            row { comment("Configure custom agents that extend Claude's capabilities.") }
            row {
                comment(ClaudeCodePlusBundle.message("agents.settings.notice")).applyToComponent {
                    foreground = JBColor.GRAY
                }
            }

            collapsibleGroup("ExploreWithJetbrains") {
                row {
                    cell(exploreEnabledCheckbox!!)
                    label("Model:").gap(RightGap.SMALL)
                    cell(exploreModelCombo!!).columns(COLUMNS_SHORT)
                }
                row {
                    val mcpEnabled = AgentSettingsService.getInstance().enableJetBrainsMcp
                    if (!mcpEnabled) {
                        comment("Requires JetBrains MCP to be enabled").applyToComponent {
                            foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                        }
                    }
                }

                separator().topGap(TopGap.SMALL)

                row("Description:") {}
                row {
                    scrollCell(exploreDescriptionArea!!).align(Align.FILL).resizableColumn()
                }

                row("System Prompt:") {}
                row {
                    scrollCell(explorePromptArea!!).align(Align.FILL).resizableColumn()
                }.resizableRow()

                row("Appended System Prompt:") {}
                row { comment("Appended to CLI's system prompt. Tells AI when/how to use this agent.") }
                row {
                    scrollCell(exploreSelectionHintArea!!).align(Align.FILL).resizableColumn()
                }

                separator().topGap(TopGap.SMALL)

                row("Allowed Tools:") {}
                row {
                    cell(exploreToolCombo).columns(COLUMNS_MEDIUM)
                    button("+") { addExploreTool() }
                    comment("Select or type, then click +")
                }
                row {
                    cell(JBScrollPane(exploreToolsPanel).apply {
                        preferredSize = Dimension(600, 80)
                        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                    }).align(AlignX.FILL)
                }

                separator().topGap(TopGap.SMALL)

                row {
                    button("Reset to Default") {
                        exploreDescriptionArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.description
                        explorePromptArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt
                        exploreSelectionHintArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint
                        setTools(AgentDefaults.EXPLORE_WITH_JETBRAINS.tools, exploreToolsList, exploreToolsPanel!!)
                        exploreModelCombo?.selectedItem = "(inherit)"
                    }
                }
            }.apply { expanded = true }

            collapsibleGroup("CodeWithJetbrains") {
                row {
                    cell(codeEnabledCheckbox!!)
                    label("Model:").gap(RightGap.SMALL)
                    cell(codeModelCombo!!).columns(COLUMNS_SHORT)
                }
                row {
                    val mcpEnabled = AgentSettingsService.getInstance().enableJetBrainsMcp
                    if (!mcpEnabled) {
                        comment("Requires JetBrains MCP to be enabled").applyToComponent {
                            foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                        }
                    }
                }

                separator().topGap(TopGap.SMALL)

                row("Description:") {}
                row {
                    scrollCell(codeDescriptionArea!!).align(Align.FILL).resizableColumn()
                }

                row("System Prompt:") {}
                row {
                    scrollCell(codePromptArea!!).align(Align.FILL).resizableColumn()
                }.resizableRow()

                row("Appended System Prompt:") {}
                row { comment("Appended to CLI's system prompt. Tells AI when/how to use this agent.") }
                row {
                    scrollCell(codeSelectionHintArea!!).align(Align.FILL).resizableColumn()
                }

                separator().topGap(TopGap.SMALL)

                row("Allowed Tools:") {}
                row {
                    cell(codeToolCombo).columns(COLUMNS_MEDIUM)
                    button("+") { addCodeTool() }
                    comment("Select or type, then click +")
                }
                row {
                    cell(JBScrollPane(codeToolsPanel).apply {
                        preferredSize = Dimension(600, 80)
                        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                    }).align(AlignX.FILL)
                }

                separator().topGap(TopGap.SMALL)

                row {
                    button("Reset to Default") {
                        codeDescriptionArea?.text = AgentDefaults.CODE_WITH_JETBRAINS.description
                        codePromptArea?.text = AgentDefaults.CODE_WITH_JETBRAINS.prompt
                        codeSelectionHintArea?.text = AgentDefaults.CODE_WITH_JETBRAINS.selectionHint
                        setTools(AgentDefaults.CODE_WITH_JETBRAINS.tools, codeToolsList, codeToolsPanel!!)
                        codeModelCombo?.selectedItem = "(inherit)"
                    }
                }
            }.apply { expanded = false }
        }
    }

    private fun addToolTag(toolName: String, toolsList: MutableList<String>, toolsPanel: JPanel) {
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
        val label = JBLabel(toolName).apply { font = font.deriveFont(11f) }
        val removeBtn = JBLabel("×").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        removeBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                toolsList.remove(toolName)
                toolsPanel.remove(tagPanel)
                toolsPanel.revalidate()
                toolsPanel.repaint()
            }
        })
        tagPanel.add(label)
        tagPanel.add(removeBtn)
        toolsPanel.add(tagPanel)
        toolsPanel.revalidate()
        toolsPanel.repaint()
    }

    private fun setTools(tools: List<String>, toolsList: MutableList<String>, toolsPanel: JPanel) {
        toolsList.clear()
        toolsPanel.removeAll()
        tools.forEach { addToolTag(it, toolsList, toolsPanel) }
    }

    private fun getExploreTools(): List<String> = exploreToolsList.toList()
    private fun getCodeTools(): List<String> = codeToolsList.toList()

    private fun refreshModelCombo() {
        val current = defaultModelCombo?.selectedItem as? ModelInfo
        val settings = AgentSettingsService.getInstance()
        val builtIn = settings.getAllAvailableModels().filter { it.isBuiltIn }
        val custom = getCustomModelsFromTable()
        val all = builtIn + custom
        defaultModelCombo?.model = DefaultComboBoxModel(all.toTypedArray())
        current?.let { c -> all.find { it.modelId == c.modelId }?.let { defaultModelCombo?.selectedItem = it } }
    }

    private fun getCustomModelsFromTable(): List<ModelInfo> {
        val model = customModelsTableModel ?: return emptyList()
        return (0 until model.rowCount).mapNotNull { i ->
            val name = model.getValueAt(i, 0) as? String ?: return@mapNotNull null
            val id = model.getValueAt(i, 1) as? String ?: return@mapNotNull null
            ModelInfo(name, id, false)
        }
    }

    private fun showCustomModelDialog(currentName: String?, currentId: String?, onSave: (String, String) -> Unit) {
        val nameField = JBTextField(currentName ?: "", 20)
        val idField = JBTextField(currentId ?: "", 30)

        val dialogPanel = panel {
            row("Display Name:") { cell(nameField).align(AlignX.FILL) }
            row("Model ID:") { cell(idField).align(AlignX.FILL) }
            row { comment("Model ID examples: claude-sonnet-4-6") }
        }

        val result = JOptionPane.showConfirmDialog(mainPanel, dialogPanel,
            if (currentName != null) "Edit Custom Model" else "Add Custom Model",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)

        if (result == JOptionPane.OK_OPTION) {
            val name = nameField.text.trim()
            val id = idField.text.trim()
            if (name.isNotEmpty() && id.isNotEmpty()) {
                onSave(name, id)
            }
        }
    }

    private fun updateThinkingLevelCombo() {
        val current = (defaultThinkingLevelCombo?.selectedItem as? ThinkingLevelConfig)?.id
        val levels = buildAllThinkingLevels()
        defaultThinkingLevelCombo?.model = DefaultComboBoxModel(levels.toTypedArray())
        defaultThinkingLevelCombo?.selectedItem = levels.find { it.id == current } ?: levels.find { it.id == "ultra" }
    }

    private fun buildAllThinkingLevels() = listOf(
        ThinkingLevelConfig("off", "Off", 0, false),
        ThinkingLevelConfig("think", "Think", thinkTokensSpinner?.value as? Int ?: 2048, false),
        ThinkingLevelConfig("ultra", "Ultra", ultraTokensSpinner?.value as? Int ?: 8096, false)
    )

    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()
        val selectedModel = defaultModelCombo?.selectedItem as? ModelInfo
        val tableModels = getCustomModelsFromTable().map { it.displayName to it.modelId }
        val savedModels = settings.getCustomModels().map { it.displayName to it.modelId }

        val generalModified = claudePathField?.text != settings.claudePath ||
            selectedModel?.modelId != settings.defaultModel ||
            tableModels != savedModels ||
            (defaultThinkingLevelCombo?.selectedItem as? ThinkingLevelConfig)?.id != settings.defaultThinkingLevelId ||
            (thinkTokensSpinner?.value as? Int ?: 2048) != settings.thinkTokens ||
            (ultraTokensSpinner?.value as? Int ?: 8096) != settings.ultraTokens ||
            permissionModeCombo?.selectedItem != settings.permissionMode ||
            agentTeamsModeCombo?.selectedItem != settings.agentTeamsMode ||
            defaultBypassPermissionsCheckbox?.isSelected != settings.defaultBypassPermissions ||
            defaultAutoCleanupContextsCheckbox?.isSelected != settings.claudeDefaultAutoCleanupContexts

        val config = parseAgentsConfig(settings.customAgents)

        // Check ExploreWithJetbrains modifications
        val explore = config.agents["ExploreWithJetbrains"]
        val exploreEffectiveEnabled = explore?.enabled ?: true
        val exploreEffectiveModel = explore?.model?.ifBlank { "(inherit)" } ?: "(inherit)"
        val exploreEffectiveDesc = explore?.description?.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.description }
            ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.description
        val exploreEffectivePrompt = explore?.prompt?.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt }
            ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt
        val exploreEffectiveHint = explore?.selectionHint?.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint }
            ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint
        val exploreEffectiveTools = explore?.tools?.takeIf { it.isNotEmpty() } ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.tools

        val exploreModified = exploreEnabledCheckbox?.isSelected != exploreEffectiveEnabled ||
            exploreModelCombo?.selectedItem != exploreEffectiveModel ||
            exploreDescriptionArea?.text != exploreEffectiveDesc ||
            explorePromptArea?.text != exploreEffectivePrompt ||
            exploreSelectionHintArea?.text != exploreEffectiveHint ||
            getExploreTools() != exploreEffectiveTools

        // Check CodeWithJetbrains modifications
        val code = config.agents["CodeWithJetbrains"]
        val codeEffectiveEnabled = code?.enabled ?: true
        val codeEffectiveModel = code?.model?.ifBlank { "(inherit)" } ?: "(inherit)"
        val codeEffectiveDesc = code?.description?.ifBlank { AgentDefaults.CODE_WITH_JETBRAINS.description }
            ?: AgentDefaults.CODE_WITH_JETBRAINS.description
        val codeEffectivePrompt = code?.prompt?.ifBlank { AgentDefaults.CODE_WITH_JETBRAINS.prompt }
            ?: AgentDefaults.CODE_WITH_JETBRAINS.prompt
        val codeEffectiveHint = code?.selectionHint?.ifBlank { AgentDefaults.CODE_WITH_JETBRAINS.selectionHint }
            ?: AgentDefaults.CODE_WITH_JETBRAINS.selectionHint
        val codeEffectiveTools = code?.tools?.takeIf { it.isNotEmpty() } ?: AgentDefaults.CODE_WITH_JETBRAINS.tools

        val codeModified = codeEnabledCheckbox?.isSelected != codeEffectiveEnabled ||
            codeModelCombo?.selectedItem != codeEffectiveModel ||
            codeDescriptionArea?.text != codeEffectiveDesc ||
            codePromptArea?.text != codeEffectivePrompt ||
            codeSelectionHintArea?.text != codeEffectiveHint ||
            getCodeTools() != codeEffectiveTools

        return generalModified || exploreModified || codeModified
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()
        settings.claudePath = claudePathField?.text ?: ""

        val selectedModel = defaultModelCombo?.selectedItem as? ModelInfo
        settings.defaultModel = selectedModel?.modelId ?: settings.getAllAvailableModels().firstOrNull { it.isBuiltIn }?.modelId
            ?: "claude-opus-4-6"

        val existing = settings.getCustomModels().associateBy { it.modelId }
        val custom = getCustomModelsFromTable().map { m ->
            val id = existing[m.modelId]?.id ?: "custom_${System.currentTimeMillis()}_${m.modelId.hashCode().toUInt()}"
            CustomModelConfig(id, m.displayName, m.modelId)
        }
        settings.setCustomModels(custom)

        settings.defaultThinkingLevelId = (defaultThinkingLevelCombo?.selectedItem as? ThinkingLevelConfig)?.id ?: "ultra"
        settings.thinkTokens = thinkTokensSpinner?.value as? Int ?: 2048
        settings.ultraTokens = ultraTokensSpinner?.value as? Int ?: 8096
        settings.permissionMode = permissionModeCombo?.selectedItem as? String ?: "default"
        settings.agentTeamsMode = agentTeamsModeCombo?.selectedItem as? String ?: "default"
        settings.includePartialMessages = true
        settings.defaultBypassPermissions = defaultBypassPermissionsCheckbox?.isSelected ?: false
        settings.claudeDefaultAutoCleanupContexts = defaultAutoCleanupContextsCheckbox?.isSelected ?: false

        // Save ExploreWithJetbrains config
        val exploreAgentModel = exploreModelCombo?.selectedItem as? String ?: "(inherit)"
        val exploreConfig = AgentConfigItem(
            enabled = exploreEnabledCheckbox?.isSelected ?: true,
            description = exploreDescriptionArea?.text ?: "",
            prompt = explorePromptArea?.text ?: "",
            tools = getExploreTools(),
            model = if (exploreAgentModel == "(inherit)") "" else exploreAgentModel,
            selectionHint = exploreSelectionHintArea?.text ?: ""
        )

        // Save CodeWithJetbrains config
        val codeAgentModel = codeModelCombo?.selectedItem as? String ?: "(inherit)"
        val codeConfig = AgentConfigItem(
            enabled = codeEnabledCheckbox?.isSelected ?: true,
            description = codeDescriptionArea?.text ?: "",
            prompt = codePromptArea?.text ?: "",
            tools = getCodeTools(),
            model = if (codeAgentModel == "(inherit)") "" else codeAgentModel,
            selectionHint = codeSelectionHintArea?.text ?: ""
        )

        settings.customAgents = json.encodeToString(AgentsConfigData(mapOf(
            "ExploreWithJetbrains" to exploreConfig,
            "CodeWithJetbrains" to codeConfig
        )))
        settings.notifyChange()
    }

    override fun reset() {
        val settings = AgentSettingsService.getInstance()
        claudePathField?.text = settings.claudePath

        customModelsTableModel?.rowCount = 0
        settings.getCustomModels().forEach { customModelsTableModel?.addRow(arrayOf(it.displayName, it.modelId)) }

        refreshModelCombo()
        val saved = settings.defaultModel
        val all = settings.getAllAvailableModels()
        defaultModelCombo?.selectedItem = all.find { it.modelId == saved } ?: all.firstOrNull { it.isBuiltIn }

        thinkTokensSpinner?.value = settings.thinkTokens
        ultraTokensSpinner?.value = settings.ultraTokens
        updateThinkingLevelCombo()
        defaultThinkingLevelCombo?.selectedItem = buildAllThinkingLevels().find { it.id == settings.defaultThinkingLevelId }
            ?: buildAllThinkingLevels().find { it.id == "ultra" }
        permissionModeCombo?.selectedItem = settings.permissionMode
        agentTeamsModeCombo?.selectedItem = settings.agentTeamsMode
        includePartialMessagesCheckbox?.isSelected = true
        defaultBypassPermissionsCheckbox?.isSelected = settings.defaultBypassPermissions
        defaultAutoCleanupContextsCheckbox?.isSelected = settings.claudeDefaultAutoCleanupContexts

        val config = parseAgentsConfig(settings.customAgents)

        // Reset ExploreWithJetbrains
        val explore = config.agents["ExploreWithJetbrains"]
        if (explore != null && (explore.description.isNotBlank() || explore.prompt.isNotBlank())) {
            exploreEnabledCheckbox?.isSelected = explore.enabled
            exploreModelCombo?.selectedItem = explore.model.ifBlank { "(inherit)" }
            exploreDescriptionArea?.text = explore.description.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.description }
            explorePromptArea?.text = explore.prompt.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt }
            exploreSelectionHintArea?.text = explore.selectionHint.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint }
            setTools(explore.tools.takeIf { it.isNotEmpty() } ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.tools, exploreToolsList, exploreToolsPanel!!)
        } else {
            exploreEnabledCheckbox?.isSelected = true
            exploreModelCombo?.selectedItem = "(inherit)"
            exploreDescriptionArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.description
            explorePromptArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt
            exploreSelectionHintArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint
            setTools(AgentDefaults.EXPLORE_WITH_JETBRAINS.tools, exploreToolsList, exploreToolsPanel!!)
        }

        // Reset CodeWithJetbrains
        val code = config.agents["CodeWithJetbrains"]
        if (code != null && (code.description.isNotBlank() || code.prompt.isNotBlank())) {
            codeEnabledCheckbox?.isSelected = code.enabled
            codeModelCombo?.selectedItem = code.model.ifBlank { "(inherit)" }
            codeDescriptionArea?.text = code.description.ifBlank { AgentDefaults.CODE_WITH_JETBRAINS.description }
            codePromptArea?.text = code.prompt.ifBlank { AgentDefaults.CODE_WITH_JETBRAINS.prompt }
            codeSelectionHintArea?.text = code.selectionHint.ifBlank { AgentDefaults.CODE_WITH_JETBRAINS.selectionHint }
            setTools(code.tools.takeIf { it.isNotEmpty() } ?: AgentDefaults.CODE_WITH_JETBRAINS.tools, codeToolsList, codeToolsPanel!!)
        } else {
            codeEnabledCheckbox?.isSelected = true
            codeModelCombo?.selectedItem = "(inherit)"
            codeDescriptionArea?.text = AgentDefaults.CODE_WITH_JETBRAINS.description
            codePromptArea?.text = AgentDefaults.CODE_WITH_JETBRAINS.prompt
            codeSelectionHintArea?.text = AgentDefaults.CODE_WITH_JETBRAINS.selectionHint
            setTools(AgentDefaults.CODE_WITH_JETBRAINS.tools, codeToolsList, codeToolsPanel!!)
        }
    }

    private fun parseAgentsConfig(jsonStr: String): AgentsConfigData {
        return try {
            if (jsonStr.isBlank() || jsonStr == "{}") AgentsConfigData()
            else json.decodeFromString<AgentsConfigData>(jsonStr)
        } catch (e: Exception) { AgentsConfigData() }
    }

    override fun disposeUIResources() {
        claudePathField = null
        defaultModelCombo = null
        defaultThinkingLevelCombo = null
        thinkTokensSpinner = null
        ultraTokensSpinner = null
        permissionModeCombo = null
        includePartialMessagesCheckbox = null
        defaultBypassPermissionsCheckbox = null
        defaultAutoCleanupContextsCheckbox = null
        customModelsTable = null
        customModelsTableModel = null
        // ExploreWithJetbrains
        exploreEnabledCheckbox = null
        exploreModelCombo = null
        exploreDescriptionArea = null
        explorePromptArea = null
        exploreSelectionHintArea = null
        exploreToolsPanel = null
        exploreToolsList.clear()
        // CodeWithJetbrains
        codeEnabledCheckbox = null
        codeModelCombo = null
        codeDescriptionArea = null
        codePromptArea = null
        codeSelectionHintArea = null
        codeToolsPanel = null
        codeToolsList.clear()
        mainPanel = null
    }
}
