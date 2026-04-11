package com.asakii.settings

import com.asakii.plugin.compat.BrowseButtonCompat
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets

class CodexConfigurable : SearchableConfigurable {

    private var mainPanel: JPanel? = null
    private var codexPathField: TextFieldWithBrowseButton? = null
    private var webSearchCheckBox: JBCheckBox? = null
    private var defaultBypassPermissionsCheckbox: JBCheckBox? = null
    private var defaultAutoCleanupContextsCheckbox: JBCheckBox? = null
    private var defaultModelCombo: ComboBox<ModelInfo>? = null
    private var defaultReasoningEffortCombo: ComboBox<String>? = null
    private var defaultReasoningSummaryCombo: ComboBox<String>? = null
    private var defaultSandboxModeCombo: ComboBox<SandboxOption>? = null
    private var customModelsTable: JBTable? = null
    private var customModelsTableModel: DefaultTableModel? = null
    private var addModelButton: JButton? = null
    private var editModelButton: JButton? = null
    private var removeModelButton: JButton? = null

    override fun getId(): String = "com.asakii.settings.codex"

    override fun getDisplayName(): String = "Codex"

    override fun createComponent(): JComponent {
        initComponents()
        
        mainPanel = panel {
            group("Default Permissions") {
                row {
                    cell(defaultBypassPermissionsCheckbox!!)
                }
                row {
                    comment("Skip confirmation dialogs for file edits and bash commands. Use with caution.")
                }
                row {
                    cell(defaultAutoCleanupContextsCheckbox!!)
                }
                row {
                    comment("Enabled contexts are cleared after send; disabled contexts stay.")
                }
            }

            group("Runtime Settings") {
                row("Codex path:") {
                    cell(codexPathField!!)
                        .align(AlignX.FILL)
                        .resizableColumn()
                        .comment("Path to Codex executable. Leave empty to auto-detect from system PATH.")
                }
                row {
                    cell(webSearchCheckBox!!)
                        .comment("Allow Codex to request web searches (features.web_search_request).")
                }
            }

            group("Model Settings") {
                row("Default model:") {
                    cell(defaultModelCombo!!)
                        .comment("gpt-5.4 = Latest | gpt-5.3-codex = Codex optimized")
                }
            }

            collapsibleGroup("Custom Models") {
                row {
                    cell(JBScrollPane(customModelsTable))
                        .align(Align.FILL)
                }.resizableRow()
                row {
                    cell(addModelButton!!)
                    cell(editModelButton!!)
                    cell(removeModelButton!!)
                }
            }

            group("Session Defaults") {
                row("Reasoning effort:") {
                    cell(defaultReasoningEffortCombo!!)
                        .comment("Controls reasoning depth for Codex responses.")
                }
                row("Reasoning summary:") {
                    cell(defaultReasoningSummaryCombo!!)
                        .comment("Summary style for reasoning output when supported.")
                }
                row("Sandbox mode:") {
                    cell(defaultSandboxModeCombo!!)
                        .comment("Controls file system and network access permissions.")
                }
            }
        }

        reset()
        return mainPanel!!
    }

    private fun initComponents() {
        // Default Permissions
        defaultBypassPermissionsCheckbox = JBCheckBox("Default bypass permissions").apply {
            toolTipText = "When enabled, new sessions will automatically use bypass permissions mode"
        }
        defaultAutoCleanupContextsCheckbox = JBCheckBox("Default auto cleanup contexts").apply {
            toolTipText = "When enabled, new sessions will auto clear enabled contexts after sending"
        }

        // Runtime Settings
        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        codexPathField = TextFieldWithBrowseButton().apply {
            BrowseButtonCompat.addBrowseFolderListener(
                this,
                "Select Codex Executable",
                "Choose the path to codex executable",
                null,
                descriptor
            )
            toolTipText = "Leave empty to auto-detect from system PATH"
            (textField as? JBTextField)?.let { tf ->
                tf.emptyText.text = "Detecting Codex..."
                com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                    val codexInfo = AgentSettingsService.detectCodexInfo()
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        tf.emptyText.text = codexInfo?.let {
                            if (it.version != null) "${it.path} (${it.version})" else it.path
                        } ?: "Auto-detect from system PATH (Codex not found)"
                    }
                }
            }
        }

        webSearchCheckBox = JBCheckBox("Enable web search")

        // Model Settings
        defaultModelCombo = ComboBox<ModelInfo>().apply {
            renderer = ModelInfoRenderer()
            toolTipText = "Default model for new Codex sessions"
        }

        // Custom Models Table
        customModelsTableModel = object : DefaultTableModel(arrayOf("Display Name", "Model ID"), 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        customModelsTable = JBTable(customModelsTableModel).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            tableHeader.reorderingAllowed = false
        }

        addModelButton = JButton("Add").apply {
            toolTipText = "Add a new custom model"
        }
        editModelButton = JButton("Edit").apply {
            toolTipText = "Edit selected custom model"
            isEnabled = false
        }
        removeModelButton = JButton("Remove").apply {
            toolTipText = "Remove selected custom model"
            isEnabled = false
        }

        customModelsTable!!.selectionModel.addListSelectionListener {
            val hasSelection = customModelsTable!!.selectedRow >= 0
            editModelButton?.isEnabled = hasSelection
            removeModelButton?.isEnabled = hasSelection
        }

        addModelButton!!.addActionListener {
            showCustomModelDialog(null, null) { displayName, modelId ->
                customModelsTableModel?.addRow(arrayOf(displayName, modelId))
                refreshModelCombo()
            }
        }
        editModelButton!!.addActionListener {
            val selectedRow = customModelsTable!!.selectedRow
            if (selectedRow >= 0) {
                val currentName = customModelsTableModel?.getValueAt(selectedRow, 0) as? String ?: ""
                val currentModelId = customModelsTableModel?.getValueAt(selectedRow, 1) as? String ?: ""
                showCustomModelDialog(currentName, currentModelId) { displayName, modelId ->
                    customModelsTableModel?.setValueAt(displayName, selectedRow, 0)
                    customModelsTableModel?.setValueAt(modelId, selectedRow, 1)
                    refreshModelCombo()
                }
            }
        }
        removeModelButton!!.addActionListener {
            val selectedRow = customModelsTable!!.selectedRow
            if (selectedRow >= 0) {
                customModelsTableModel?.removeRow(selectedRow)
                refreshModelCombo()
            }
        }

        // Session Defaults
        defaultReasoningEffortCombo = ComboBox(
            DefaultComboBoxModel(arrayOf("minimal", "low", "medium", "high", "xhigh"))
        ).apply {
            toolTipText = "Default reasoning effort for new Codex sessions"
        }

        defaultReasoningSummaryCombo = ComboBox(
            DefaultComboBoxModel(arrayOf("auto", "concise", "detailed", "none"))
        ).apply {
            toolTipText = "Default reasoning summary behavior for Codex sessions"
        }

        defaultSandboxModeCombo = ComboBox(
            DefaultComboBoxModel(
                arrayOf(
                    SandboxOption("read-only", "Chat"),
                    SandboxOption("workspace-write", "Agent"),
                    SandboxOption("danger-full-access", "Agent (full access)")
                )
            )
        ).apply {
            renderer = SandboxOptionRenderer()
            toolTipText = "Default sandbox mode for new Codex sessions"
        }

        refreshModelCombo()
    }

    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()
        return codexPathField?.text?.trim() != settings.codexPath ||
            (webSearchCheckBox?.isSelected ?: false) != settings.codexWebSearchEnabled ||
            (defaultBypassPermissionsCheckbox?.isSelected ?: false) != settings.defaultBypassPermissions ||
            (defaultAutoCleanupContextsCheckbox?.isSelected ?: false) != settings.codexDefaultAutoCleanupContexts ||
            (defaultModelCombo?.selectedItem as? ModelInfo)?.modelId != settings.codexDefaultModelId ||
            defaultReasoningEffortCombo?.selectedItem != settings.codexDefaultReasoningEffort ||
            defaultReasoningSummaryCombo?.selectedItem != settings.codexDefaultReasoningSummary ||
            (defaultSandboxModeCombo?.selectedItem as? SandboxOption)?.id != settings.codexDefaultSandboxMode ||
            getCustomModelsFromTable().map { it.displayName to it.modelId } != settings.getCodexCustomModels().map { it.displayName to it.modelId }
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()
        settings.codexPath = codexPathField?.text?.trim() ?: ""
        settings.codexWebSearchEnabled = webSearchCheckBox?.isSelected ?: false
        settings.defaultBypassPermissions = defaultBypassPermissionsCheckbox?.isSelected ?: false
        settings.codexDefaultAutoCleanupContexts = defaultAutoCleanupContextsCheckbox?.isSelected ?: false
        val selectedModel = defaultModelCombo?.selectedItem as? ModelInfo
        settings.codexDefaultModelId = selectedModel?.modelId ?: settings.getCodexBuiltInModels().first().modelId
        settings.codexDefaultReasoningEffort = defaultReasoningEffortCombo?.selectedItem as? String ?: "medium"
        settings.codexDefaultReasoningSummary = defaultReasoningSummaryCombo?.selectedItem as? String ?: "auto"
        settings.codexDefaultSandboxMode =
            (defaultSandboxModeCombo?.selectedItem as? SandboxOption)?.id ?: "workspace-write"
        val existingModels = settings.getCodexCustomModels().associateBy { it.modelId }
        val customModels = getCustomModelsFromTable().map { model ->
            val existing = existingModels[model.modelId]
            val id = existing?.id ?: "custom_${System.currentTimeMillis()}_${model.modelId.hashCode().toUInt()}"
            CustomModelConfig(id, model.displayName, model.modelId)
        }
        settings.setCodexCustomModels(customModels)
        settings.notifyChange()
    }

    override fun reset() {
        val settings = AgentSettingsService.getInstance()
        codexPathField?.text = settings.codexPath
        webSearchCheckBox?.isSelected = settings.codexWebSearchEnabled
        defaultBypassPermissionsCheckbox?.isSelected = settings.defaultBypassPermissions
        defaultAutoCleanupContextsCheckbox?.isSelected = settings.codexDefaultAutoCleanupContexts
        customModelsTableModel?.rowCount = 0
        settings.getCodexCustomModels().forEach { model ->
            customModelsTableModel?.addRow(arrayOf(model.displayName, model.modelId))
        }
        refreshModelCombo()
        val savedDefaultModelId = settings.codexDefaultModelId
        val allModels = settings.getAllCodexModels()
        val matchingModel = allModels.find { it.modelId == savedDefaultModelId }
        if (matchingModel != null) {
            defaultModelCombo?.selectedItem = matchingModel
        } else {
            defaultModelCombo?.selectedItem = allModels.firstOrNull { it.isBuiltIn }
        }
        defaultReasoningEffortCombo?.selectedItem = settings.codexDefaultReasoningEffort
        defaultReasoningSummaryCombo?.selectedItem = settings.codexDefaultReasoningSummary
        val sandboxMode = settings.codexDefaultSandboxMode
        val sandboxModel = defaultSandboxModeCombo?.model as? DefaultComboBoxModel<*>
        val sandboxOptions = sandboxModel?.let { model ->
            (0 until model.size).mapNotNull { idx ->
                model.getElementAt(idx) as? SandboxOption
            }
        } ?: emptyList()
        val sandboxSelection = sandboxOptions.find { it.id == sandboxMode } ?: sandboxOptions.firstOrNull()
        if (sandboxSelection != null) {
            defaultSandboxModeCombo?.selectedItem = sandboxSelection
        }
    }

    override fun disposeUIResources() {
        codexPathField = null
        webSearchCheckBox = null
        defaultBypassPermissionsCheckbox = null
        defaultAutoCleanupContextsCheckbox = null
        defaultModelCombo = null
        defaultReasoningEffortCombo = null
        defaultReasoningSummaryCombo = null
        defaultSandboxModeCombo = null
        customModelsTable = null
        customModelsTableModel = null
        addModelButton = null
        editModelButton = null
        removeModelButton = null
        mainPanel = null
    }

    private data class SandboxOption(val id: String, val label: String) {
        override fun toString(): String = label
    }

    private class SandboxOptionRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is SandboxOption) {
                text = value.label
            }
            return component
        }
    }

    private fun refreshModelCombo() {
        val currentSelection = defaultModelCombo?.selectedItem as? ModelInfo
        val settings = AgentSettingsService.getInstance()
        val allModels = settings.getCodexBuiltInModels() + getCustomModelsFromTable()
        defaultModelCombo?.model = DefaultComboBoxModel(allModels.toTypedArray())

        if (currentSelection != null) {
            val matchingModel = allModels.find { it.modelId == currentSelection.modelId }
            if (matchingModel != null) {
                defaultModelCombo?.selectedItem = matchingModel
            }
        }
    }

    private fun getCustomModelsFromTable(): List<ModelInfo> {
        val tableModel = customModelsTableModel ?: return emptyList()
        val result = mutableListOf<ModelInfo>()
        for (i in 0 until tableModel.rowCount) {
            val displayName = tableModel.getValueAt(i, 0) as? String ?: continue
            val modelId = tableModel.getValueAt(i, 1) as? String ?: continue
            if (displayName.isBlank() || modelId.isBlank()) continue
            result.add(ModelInfo(modelId = modelId, displayName = displayName, isBuiltIn = false))
        }
        return result
    }

    private fun showCustomModelDialog(
        currentDisplayName: String?,
        currentModelId: String?,
        onSave: (displayName: String, modelId: String) -> Unit
    ) {
        val isEdit = currentDisplayName != null

        val dialogPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
        }

        gbc.gridx = 0
        gbc.gridy = 0
        dialogPanel.add(JBLabel("Display Name:"), gbc)

        val displayNameField = JBTextField(20).apply {
            text = currentDisplayName ?: ""
            toolTipText = "Name shown in the model selector (e.g., 'My Codex Model')"
        }
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        dialogPanel.add(displayNameField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        dialogPanel.add(JBLabel("Model ID:"), gbc)

        val modelIdField = JBTextField(30).apply {
            text = currentModelId ?: ""
            toolTipText = "Codex model ID (e.g., 'gpt-5.4')"
        }
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        dialogPanel.add(modelIdField, gbc)

        gbc.gridx = 0
        gbc.gridy = 2
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        dialogPanel.add(JBLabel("<html><font color='gray' size='-1'>Model ID examples: gpt-5.4, gpt-5.2</font></html>"), gbc)

        val result = JOptionPane.showConfirmDialog(
            mainPanel,
            dialogPanel,
            if (isEdit) "Edit Custom Model" else "Add Custom Model",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            val displayName = displayNameField.text.trim()
            val modelId = modelIdField.text.trim()

            if (displayName.isNotEmpty() && modelId.isNotEmpty()) {
                onSave(displayName, modelId)
            } else {
                JOptionPane.showMessageDialog(
                    mainPanel,
                    "Both Display Name and Model ID are required.",
                    "Validation Error",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        }
    }
}
