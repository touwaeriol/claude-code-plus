package com.claudecodeplus.plugin.ui.settings

import com.claudecodeplus.plugin.services.ClaudeSettingsService
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Claude 设置面板
 * 
 * 配置 API Key、模型、主题等设置
 */
class ClaudeSettingsPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val settingsService = ClaudeSettingsService.getInstance(project)
    
    private val apiKeyField = JBPasswordField()
    private val modelComboBox = JComboBox(arrayOf(
        "claude-sonnet-4-5-20250929",
        "claude-opus-4-20250514",
        "claude-3-5-sonnet-20241022",
        "claude-3-5-haiku-20241022"
    ))
    private val maxTokensField = JBTextField()
    private val temperatureField = JBTextField()
    private val maxTurnsField = JBTextField()
    private val autoApproveCheckBox = JCheckBox("自动批准工具调用")
    private val darkModeCheckBox = JCheckBox("使用暗色主题")
    
    init {
        createUI()
        loadSettings()
    }
    
    private fun createUI() {
        border = EmptyBorder(JBUI.insets(16))
        
        // 标题
        val titleLabel = JLabel("Claude Code Plus 设置")
        titleLabel.font = titleLabel.font.deriveFont(16f).deriveFont(java.awt.Font.BOLD)
        titleLabel.border = EmptyBorder(JBUI.insets(0, 0, 16, 0))
        add(titleLabel, BorderLayout.NORTH)
        
        // 设置表单
        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(8)
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        
        // API Key
        formPanel.add(JLabel("API Key:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(apiKeyField, gbc)
        
        // 模型选择
        gbc.gridx = 0
        gbc.gridy++
        gbc.weightx = 0.0
        formPanel.add(JLabel("模型:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(modelComboBox, gbc)
        
        // Max Tokens
        gbc.gridx = 0
        gbc.gridy++
        gbc.weightx = 0.0
        formPanel.add(JLabel("最大 Token:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        maxTokensField.text = "4096"
        formPanel.add(maxTokensField, gbc)
        
        // Temperature
        gbc.gridx = 0
        gbc.gridy++
        gbc.weightx = 0.0
        formPanel.add(JLabel("Temperature (0-1):"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        temperatureField.text = "0.7"
        formPanel.add(temperatureField, gbc)
        
        // Max Turns
        gbc.gridx = 0
        gbc.gridy++
        gbc.weightx = 0.0
        formPanel.add(JLabel("最大轮次:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        maxTurnsField.text = "10"
        formPanel.add(maxTurnsField, gbc)
        
        // 自动批准
        gbc.gridx = 0
        gbc.gridy++
        gbc.gridwidth = 2
        formPanel.add(autoApproveCheckBox, gbc)
        
        // 暗色主题
        gbc.gridy++
        formPanel.add(darkModeCheckBox, gbc)
        
        add(formPanel, BorderLayout.CENTER)
        
        // 按钮面板
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.border = EmptyBorder(JBUI.insets(16, 0, 0, 0))
        
        val saveButton = JButton("保存")
        saveButton.addActionListener { saveSettings() }
        
        val cancelButton = JButton("取消")
        cancelButton.addActionListener {
            loadSettings() // 重新加载以取消更改
        }
        
        val resetButton = JButton("重置为默认值")
        resetButton.addActionListener { resetToDefaults() }
        
        buttonPanel.add(saveButton)
        buttonPanel.add(Box.createRigidArea(JBUI.size(8, 0)))
        buttonPanel.add(cancelButton)
        buttonPanel.add(Box.createHorizontalGlue())
        buttonPanel.add(resetButton)
        
        add(buttonPanel, BorderLayout.SOUTH)
    }
    
    /**
     * 加载设置
     */
    private fun loadSettings() {
        val settings = settingsService.getSettings()
        apiKeyField.text = settings.apiKey ?: ""
        modelComboBox.selectedItem = settings.model
        maxTokensField.text = settings.maxTokens.toString()
        temperatureField.text = settings.temperature.toString()
        maxTurnsField.text = settings.maxTurns.toString()
        autoApproveCheckBox.isSelected = settings.autoApproveTools
        darkModeCheckBox.isSelected = settings.useDarkTheme
    }
    
    /**
     * 保存设置
     */
    private fun saveSettings() {
        try {
            val settings = ClaudeSettingsService.Settings(
                apiKey = String(apiKeyField.password).takeIf { it.isNotBlank() },
                model = modelComboBox.selectedItem as String,
                maxTokens = maxTokensField.text.toIntOrNull() ?: 4096,
                temperature = temperatureField.text.toDoubleOrNull() ?: 0.7,
                maxTurns = maxTurnsField.text.toIntOrNull() ?: 10,
                autoApproveTools = autoApproveCheckBox.isSelected,
                useDarkTheme = darkModeCheckBox.isSelected
            )
            
            settingsService.updateSettings(settings)
            
            JOptionPane.showMessageDialog(
                this,
                "设置已保存！",
                "成功",
                JOptionPane.INFORMATION_MESSAGE
            )
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "保存设置失败: ${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    /**
     * 重置为默认值
     */
    private fun resetToDefaults() {
        val result = JOptionPane.showConfirmDialog(
            this,
            "确定要重置为默认设置吗？",
            "确认",
            JOptionPane.YES_NO_OPTION
        )
        
        if (result == JOptionPane.YES_OPTION) {
            settingsService.resetToDefaults()
            loadSettings()
        }
    }
}


