package com.asakii.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.Component
import javax.swing.JList
import javax.swing.DefaultListCellRenderer

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

    // 默认 ByPass 配置
    private var defaultBypassPermissionsCheckbox: JBCheckBox? = null

    // Claude 配置
    private var nodePathField: TextFieldWithBrowseButton? = null
    private var defaultModelCombo: ComboBox<DefaultModel>? = null
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

        // 默认 ByPass 配置
        defaultBypassPermissionsCheckbox = JBCheckBox("Default bypass permissions").apply {
            toolTipText = "When enabled, new sessions will automatically use bypass permissions mode"
        }

        // Node.js 路径
        nodePathField = TextFieldWithBrowseButton().apply {
            // 使用新的 API：FileChooserDescriptor(chooseFiles, chooseFolders, chooseJars, ...)
            val descriptor = FileChooserDescriptor(
                true,   // chooseFiles
                false,  // chooseFolders
                false,  // chooseJars
                false,  // chooseJarsAsFiles
                false,  // chooseJarContents
                false   // chooseMultiple
            ).withTitle("Select Node.js Executable")
             .withDescription("Choose the path to node executable")
            addBrowseFolderListener(null, descriptor)
            toolTipText = "Leave empty to auto-detect from system PATH"

            // 设置 placeholder 文本，显示自动检测的路径和版本
            // textField 需要转换为 JBTextField 才能访问 emptyText
            val textField = this.textField
            if (textField is com.intellij.ui.components.JBTextField) {
                val nodeInfo = AgentSettingsService.detectNodeInfo()
                if (nodeInfo != null) {
                    // 显示格式：路径 (版本号)
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
            isEnabled = false  // 禁止修改
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
            .addLabeledComponent(JBLabel("Permission mode:"), permissionModeCombo!!, 1, false)
            .addComponent(JBLabel("<html><font color='gray' size='-1'>  default = Ask for each action | bypassPermissions = Auto-approve all actions</font></html>"))
            .addComponent(includePartialMessagesCheckbox!!)
            .addComponent(JBLabel("<html><font color='gray' size='-1'>  └ Show streaming text as Claude types. Disable for cleaner output.</font></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()
        val selectedModel = defaultModelCombo?.selectedItem as? DefaultModel
        // Node.js 路径比较：只比较用户输入的值
        val currentNodePath = nodePathField?.text ?: ""
        val savedNodePath = settings.nodePath
        val nodePathModified = currentNodePath != savedNodePath

        return nodePathModified ||
                selectedModel?.name != settings.defaultModel ||
                permissionModeCombo?.selectedItem != settings.permissionMode ||
                includePartialMessagesCheckbox?.isSelected != settings.includePartialMessages ||
                enableUserInteractionMcpCheckbox?.isSelected != settings.enableUserInteractionMcp ||
                enableJetBrainsMcpCheckbox?.isSelected != settings.enableJetBrainsMcp ||
                defaultBypassPermissionsCheckbox?.isSelected != settings.defaultBypassPermissions
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()
        settings.nodePath = nodePathField?.text ?: ""
        settings.defaultModel = (defaultModelCombo?.selectedItem as? DefaultModel)?.name ?: DefaultModel.OPUS_45.name
        settings.permissionMode = permissionModeCombo?.selectedItem as? String ?: "default"
        settings.includePartialMessages = includePartialMessagesCheckbox?.isSelected ?: true
        settings.enableUserInteractionMcp = enableUserInteractionMcpCheckbox?.isSelected ?: true
        settings.enableJetBrainsMcp = enableJetBrainsMcpCheckbox?.isSelected ?: true
        settings.defaultBypassPermissions = defaultBypassPermissionsCheckbox?.isSelected ?: false

        // 通知监听器设置已变更
        settings.notifyChange()
    }

    override fun reset() {
        val settings = AgentSettingsService.getInstance()
        // 只恢复用户保存的路径，不自动填充检测的路径
        // 检测的路径显示在 placeholder 中
        nodePathField?.text = settings.nodePath

        // 从枚举名称恢复选中项
        val modelEnum = DefaultModel.fromName(settings.defaultModel) ?: DefaultModel.OPUS_45
        defaultModelCombo?.selectedItem = modelEnum
        permissionModeCombo?.selectedItem = settings.permissionMode
        // includePartialMessages 强制为 true 并禁用修改
        // 如果之前保存的是 false，也强制修正为 true
        if (!settings.includePartialMessages) {
            settings.includePartialMessages = true
        }
        includePartialMessagesCheckbox?.isSelected = true
        includePartialMessagesCheckbox?.isEnabled = false
        enableUserInteractionMcpCheckbox?.isSelected = settings.enableUserInteractionMcp
        enableJetBrainsMcpCheckbox?.isSelected = settings.enableJetBrainsMcp
        defaultBypassPermissionsCheckbox?.isSelected = settings.defaultBypassPermissions
    }

    override fun disposeUIResources() {
        nodePathField = null
        defaultModelCombo = null
        permissionModeCombo = null
        includePartialMessagesCheckbox = null
        enableUserInteractionMcpCheckbox = null
        enableJetBrainsMcpCheckbox = null
        defaultBypassPermissionsCheckbox = null
        mainPanel = null
    }
}
