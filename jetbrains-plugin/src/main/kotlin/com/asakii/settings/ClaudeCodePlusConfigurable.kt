package com.asakii.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.*

/**
 * Claude Code Plus 主配置页
 *
 * 作为父配置页，包含两个子页面（在 plugin.xml 中注册）：
 * - MCP: MCP 服务器配置（内置 + 自定义）
 * - Claude Code: 通用配置（Node.js、模型、思考级别、权限、Agents）
 */
class ClaudeCodePlusConfigurable : SearchableConfigurable {

    private var mainPanel: JPanel? = null

    override fun getId(): String = "com.asakii.settings"

    override fun getDisplayName(): String = "Claude Code Plus"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout())
        mainPanel!!.border = JBUI.Borders.empty(20)

        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)

        // 标题
        val titleLabel = JBLabel("<html><h2>Claude Code Plus</h2></html>")
        titleLabel.alignmentX = JPanel.LEFT_ALIGNMENT
        contentPanel.add(titleLabel)
        contentPanel.add(Box.createVerticalStrut(20))

        // 说明
        val descriptionLabel = JBLabel("""
            <html>
            <body style='width: 500px'>
            <p>Welcome to Claude Code Plus settings!</p>
            <br>
            <p>Configure the plugin using the sub-pages:</p>
            <ul>
                <li><b>MCP</b> - MCP server configuration (built-in and custom servers)</li>
                <li><b>Claude Code</b> - Runtime settings, agents, and permissions</li>
            </ul>
            <br>
            <p>Select a sub-page from the left panel to configure specific settings.</p>
            </body>
            </html>
        """.trimIndent())
        descriptionLabel.alignmentX = JPanel.LEFT_ALIGNMENT
        contentPanel.add(descriptionLabel)

        contentPanel.add(Box.createVerticalGlue())

        mainPanel!!.add(contentPanel, BorderLayout.NORTH)

        return mainPanel!!
    }

    override fun isModified(): Boolean = false

    override fun apply() {
        // 主页面不需要保存任何配置
    }

    override fun reset() {
        // 主页面不需要重置任何配置
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}
