package com.asakii.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.*
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout

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
 * Claude Code Plus 主配置页面
 */
class ClaudeCodePlusConfigurable : SearchableConfigurable, ConfigurableGroup {

    private var mainPanel: JPanel? = null

    // MCP 配置（放在最前面）
    private var enableUserInteractionMcpCheckbox: JBCheckBox? = null
    private var enableJetBrainsMcpCheckbox: JBCheckBox? = null
    private var enableContext7McpCheckbox: JBCheckBox? = null
    private var context7ApiKeyField: JBTextField? = null

    // 默认 ByPass 配置
    private var defaultBypassPermissionsCheckbox: JBCheckBox? = null

    // Claude 配置
    private var nodePathField: TextFieldWithBrowseButton? = null
    private var defaultModelCombo: ComboBox<DefaultModel>? = null
    private var defaultThinkingLevelCombo: ComboBox<ThinkingLevelConfig>? = null
    private var thinkTokensSpinner: JSpinner? = null
    private var ultraTokensSpinner: JSpinner? = null
    private var permissionModeCombo: ComboBox<String>? = null
    private var includePartialMessagesCheckbox: JBCheckBox? = null

    override fun getId(): String = "com.asakii.settings"

    override fun getDisplayName(): String = "Claude Code Plus"

    override fun getConfigurables(): Array<Configurable> = arrayOf(
        McpConfigurable()
    )

    override fun createComponent(): JComponent {
        // MCP 启用配置（放在最前面）
        enableUserInteractionMcpCheckbox = JBCheckBox("Enable user interaction MCP").apply {
            toolTipText = "Enable AskUserQuestion tool for interactive prompts"
        }

        enableJetBrainsMcpCheckbox = JBCheckBox("Enable JetBrains IDE MCP").apply {
            toolTipText = "Enable IDE indexing tools (FileIndex, CodeSearch, FindUsages, etc.)"
        }

        enableContext7McpCheckbox = JBCheckBox("Enable Context7 MCP").apply {
            toolTipText = "Enable Context7 for fetching up-to-date library documentation"
        }

        context7ApiKeyField = JBTextField().apply {
            toolTipText = "Optional: Your Context7 API key for higher rate limits"
            emptyText.text = "Optional API key"
        }

        // 默认 ByPass 配置
        defaultBypassPermissionsCheckbox = JBCheckBox("Default bypass permissions").apply {
            toolTipText = "When enabled, new sessions will automatically use bypass permissions mode"
        }

        // Node.js 路径
        nodePathField = TextFieldWithBrowseButton().apply {
            val descriptor = FileChooserDescriptor(
                true,   // chooseFiles
                false,  // chooseFolders
                false,  // chooseJars
                false,  // chooseJarsAsFiles
                false,  // chooseJarContents
                false   // chooseMultiple
            ).withTitle("Select Node.js Executable")
             .withDescription("Choose the path to node executable")
            addBrowseFolderListener("Select Node.js Executable", "Choose the path to node executable", null, descriptor)
            toolTipText = "Leave empty to auto-detect from system PATH"

            val textField = this.textField
            if (textField is com.intellij.ui.components.JBTextField) {
                val nodeInfo = AgentSettingsService.detectNodeInfo()
                if (nodeInfo != null) {
                    val hint = if (nodeInfo.version != null) {
                        "${nodeInfo.path} (${nodeInfo.version})"
                    } else {
                        nodeInfo.path
                    }
                    textField.emptyText.text = hint
                } else {
                    textField.emptyText.text = "Auto-detect from system PATH (Node.js not found)"
                }
            }
        }

        // 默认模型（下拉框）
        defaultModelCombo = ComboBox(DefaultComboBoxModel(DefaultModel.entries.toTypedArray())).apply {
            renderer = DefaultModelRenderer()
            toolTipText = "Default model for new sessions"
        }

        // 默认思考级别下拉框
        defaultThinkingLevelCombo = ComboBox<ThinkingLevelConfig>().apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
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

        // Think tokens 配置
        thinkTokensSpinner = JSpinner(SpinnerNumberModel(2048, 1, 128000, 256)).apply {
            toolTipText = "Token budget for Think level"
            preferredSize = Dimension(100, preferredSize.height)
        }

        // Ultra tokens 配置
        ultraTokensSpinner = JSpinner(SpinnerNumberModel(8096, 1, 128000, 256)).apply {
            toolTipText = "Token budget for Ultra level"
            preferredSize = Dimension(100, preferredSize.height)
        }

        // 思考配置面板
        val thinkingConfigPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)

            // Think tokens 行
            val thinkRow = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2)).apply {
                add(JBLabel("Think tokens:"))
                add(thinkTokensSpinner)
            }
            add(thinkRow)

            // Ultra tokens 行
            val ultraRow = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2)).apply {
                add(JBLabel("Ultra tokens:"))
                add(ultraTokensSpinner)
            }
            add(ultraRow)
        }

        // 当 spinner 值变化时更新下拉框
        thinkTokensSpinner?.addChangeListener { updateThinkingLevelCombo() }
        ultraTokensSpinner?.addChangeListener { updateThinkingLevelCombo() }

        // 权限模式
        permissionModeCombo = ComboBox(DefaultComboBoxModel(arrayOf(
            "default",
            "acceptEdits",
            "plan",
            "bypassPermissions",
            "dontAsk"
        ))).apply {
            toolTipText = "Default permission mode for new sessions"
        }

        // 包含部分消息（强制启用，禁止修改）
        includePartialMessagesCheckbox = JBCheckBox("Include partial messages in stream").apply {
            toolTipText = "Include partial/streaming messages in the response (always enabled)"
            isSelected = true
            isEnabled = false
        }

        mainPanel = FormBuilder.createFormBuilder()
            // MCP 设置放在最前面
            .addSeparator()
            .addComponent(JBLabel("<html><b>Integrated MCP servers</b></html>"))
            .addComponent(JBLabel("<html><font color='gray' size='-1'>MCP (Model Context Protocol) servers extend Claude's capabilities with additional tools.</font></html>"))
            .addComponent(enableUserInteractionMcpCheckbox!!)
            .addComponent(JBLabel("<html><font color='gray' size='-1'>  └ Allows Claude to ask you questions with selectable options during conversations.</font></html>"))
            .addComponent(enableJetBrainsMcpCheckbox!!)
            .addComponent(JBLabel("<html><font color='gray' size='-1'>  └ Provides fast code search, file indexing, and symbol lookup using IDE's built-in index.</font></html>"))
            .addComponent(enableContext7McpCheckbox!!)
            .addComponent(JBLabel("<html><font color='gray' size='-1'>  └ Fetches up-to-date documentation for any library (React, Vue, Ktor, etc.).</font></html>"))
            .addLabeledComponent(JBLabel("  Context7 API key:"), context7ApiKeyField!!, 1, false)
            .addComponent(JBLabel("<html><font color='gray' size='-1'>    └ Optional: Provide your API key for higher rate limits.</font></html>"))
            // 默认 ByPass 设置
            .addSeparator()
            .addComponent(JBLabel("<html><b>Default permissions</b></html>"))
            .addComponent(JBLabel("<html><font color='gray' size='-1'>Configure default permission behavior for new sessions.</font></html>"))
            .addComponent(defaultBypassPermissionsCheckbox!!)
            .addComponent(JBLabel("<html><font color='gray' size='-1'>  └ Skip confirmation dialogs for file edits and bash commands. Use with caution.</font></html>"))
            // Claude 设置
            .addSeparator()
            .addComponent(JBLabel("<html><b>Claude settings</b></html>"))
            .addComponent(JBLabel("<html><font color='gray' size='-1'>Core configuration for Claude Code integration.</font></html>"))
            .addLabeledComponent(JBLabel("Node.js path:"), nodePathField!!, 1, false)
            .addComponent(JBLabel("<html><font color='gray' size='-1'>  Path to Node.js executable. Leave empty to auto-detect from system PATH.</font></html>"))
            .addLabeledComponent(JBLabel("Default model:"), defaultModelCombo!!, 1, false)
            .addComponent(JBLabel("<html><font color='gray' size='-1'>  Opus 4.5 = Most capable | Sonnet 4 = Balanced | Haiku 3.5 = Fastest</font></html>"))
            .addLabeledComponent(JBLabel("Default thinking:"), defaultThinkingLevelCombo!!, 1, false)
            .addComponent(JBLabel("<html><font color='gray' size='-1'>  Default thinking level for new sessions</font></html>"))
            // 思考配置
            .addSeparator()
            .addComponent(JBLabel("<html><b>Thinking levels configuration</b></html>"))
            .addComponent(JBLabel("<html><font color='gray' size='-1'>Configure token budgets for each thinking level</font></html>"))
            .addComponent(thinkingConfigPanel)
            // 权限模式
            .addSeparator()
            .addLabeledComponent(JBLabel("Permission mode:"), permissionModeCombo!!, 1, false)
            .addComponent(JBLabel("<html><font color='gray' size='-1'>  default = Ask for each action | bypassPermissions = Auto-approve all actions</font></html>"))
            .addComponent(includePartialMessagesCheckbox!!)
            .addComponent(JBLabel("<html><font color='gray' size='-1'>  └ Show streaming text as Claude types. Disable for cleaner output.</font></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return mainPanel!!
    }

    /**
     * 更新思考级别下拉框的选项
     */
    private fun updateThinkingLevelCombo() {
        val currentSelection = defaultThinkingLevelCombo?.selectedItem as? ThinkingLevelConfig
        val currentId = currentSelection?.id

        val allLevels = buildAllThinkingLevels()
        val model = DefaultComboBoxModel(allLevels.toTypedArray())
        defaultThinkingLevelCombo?.model = model

        // 恢复选中项
        val toSelect = allLevels.find { it.id == currentId } ?: allLevels.find { it.id == "ultra" }
        defaultThinkingLevelCombo?.selectedItem = toSelect
    }

    /**
     * 构建所有思考级别列表
     */
    private fun buildAllThinkingLevels(): List<ThinkingLevelConfig> {
        return listOf(
            ThinkingLevelConfig("off", "Off", 0, isCustom = false),
            ThinkingLevelConfig("think", "Think", thinkTokensSpinner?.value as? Int ?: 2048, isCustom = false),
            ThinkingLevelConfig("ultra", "Ultra", ultraTokensSpinner?.value as? Int ?: 8096, isCustom = false)
        )
    }

    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()
        val selectedModel = defaultModelCombo?.selectedItem as? DefaultModel
        val selectedThinkingLevel = defaultThinkingLevelCombo?.selectedItem as? ThinkingLevelConfig

        val currentNodePath = nodePathField?.text ?: ""
        val savedNodePath = settings.nodePath
        val nodePathModified = currentNodePath != savedNodePath

        // 思考配置比较
        val thinkingLevelModified = selectedThinkingLevel?.id != settings.defaultThinkingLevelId
        val thinkTokensModified = (thinkTokensSpinner?.value as? Int ?: 2048) != settings.thinkTokens
        val ultraTokensModified = (ultraTokensSpinner?.value as? Int ?: 8096) != settings.ultraTokens

        return nodePathModified ||
                selectedModel?.name != settings.defaultModel ||
                thinkingLevelModified ||
                thinkTokensModified ||
                ultraTokensModified ||
                permissionModeCombo?.selectedItem != settings.permissionMode ||
                includePartialMessagesCheckbox?.isSelected != settings.includePartialMessages ||
                enableUserInteractionMcpCheckbox?.isSelected != settings.enableUserInteractionMcp ||
                enableJetBrainsMcpCheckbox?.isSelected != settings.enableJetBrainsMcp ||
                enableContext7McpCheckbox?.isSelected != settings.enableContext7Mcp ||
                context7ApiKeyField?.text != settings.context7ApiKey ||
                defaultBypassPermissionsCheckbox?.isSelected != settings.defaultBypassPermissions
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()
        settings.nodePath = nodePathField?.text ?: ""
        settings.defaultModel = (defaultModelCombo?.selectedItem as? DefaultModel)?.name ?: DefaultModel.OPUS_45.name
        settings.defaultThinkingLevelId = (defaultThinkingLevelCombo?.selectedItem as? ThinkingLevelConfig)?.id ?: "ultra"
        settings.thinkTokens = thinkTokensSpinner?.value as? Int ?: 2048
        settings.ultraTokens = ultraTokensSpinner?.value as? Int ?: 8096
        settings.permissionMode = permissionModeCombo?.selectedItem as? String ?: "default"
        settings.includePartialMessages = includePartialMessagesCheckbox?.isSelected ?: true
        settings.enableUserInteractionMcp = enableUserInteractionMcpCheckbox?.isSelected ?: true
        settings.enableJetBrainsMcp = enableJetBrainsMcpCheckbox?.isSelected ?: true
        settings.enableContext7Mcp = enableContext7McpCheckbox?.isSelected ?: false
        settings.context7ApiKey = context7ApiKeyField?.text ?: ""
        settings.defaultBypassPermissions = defaultBypassPermissionsCheckbox?.isSelected ?: false

        // 通知监听器设置已变更
        settings.notifyChange()
    }

    override fun reset() {
        val settings = AgentSettingsService.getInstance()
        nodePathField?.text = settings.nodePath

        // 恢复模型选择
        val modelEnum = DefaultModel.fromName(settings.defaultModel) ?: DefaultModel.OPUS_45
        defaultModelCombo?.selectedItem = modelEnum

        // 恢复思考 token 配置
        thinkTokensSpinner?.value = settings.thinkTokens
        ultraTokensSpinner?.value = settings.ultraTokens

        // 更新并恢复默认思考级别
        updateThinkingLevelCombo()
        val allLevels = buildAllThinkingLevels()
        val toSelect = allLevels.find { it.id == settings.defaultThinkingLevelId } ?: allLevels.find { it.id == "ultra" }
        defaultThinkingLevelCombo?.selectedItem = toSelect

        permissionModeCombo?.selectedItem = settings.permissionMode
        if (!settings.includePartialMessages) {
            settings.includePartialMessages = true
        }
        includePartialMessagesCheckbox?.isSelected = true
        includePartialMessagesCheckbox?.isEnabled = false
        enableUserInteractionMcpCheckbox?.isSelected = settings.enableUserInteractionMcp
        enableJetBrainsMcpCheckbox?.isSelected = settings.enableJetBrainsMcp
        enableContext7McpCheckbox?.isSelected = settings.enableContext7Mcp
        context7ApiKeyField?.text = settings.context7ApiKey
        defaultBypassPermissionsCheckbox?.isSelected = settings.defaultBypassPermissions
    }

    override fun disposeUIResources() {
        nodePathField = null
        defaultModelCombo = null
        defaultThinkingLevelCombo = null
        thinkTokensSpinner = null
        ultraTokensSpinner = null
        permissionModeCombo = null
        includePartialMessagesCheckbox = null
        enableUserInteractionMcpCheckbox = null
        enableJetBrainsMcpCheckbox = null
        enableContext7McpCheckbox = null
        context7ApiKeyField = null
        defaultBypassPermissionsCheckbox = null
        mainPanel = null
    }
}
