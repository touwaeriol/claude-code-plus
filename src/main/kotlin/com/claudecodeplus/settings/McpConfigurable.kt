package com.claudecodeplus.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * MCP 服务配置页面
 */
class McpConfigurable(private val project: Project? = null) : Configurable {
    
    private var userConfigTextArea: JBTextArea? = null
    private var localConfigTextArea: JBTextArea? = null
    private var projectConfigTextArea: JBTextArea? = null
    private lateinit var tabbedPane: JBTabbedPane
    
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    
    override fun getDisplayName(): String = "MCP"
    
    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // 顶部说明
        val descriptionPanel = JPanel(BorderLayout())
        descriptionPanel.border = JBUI.Borders.empty(10)
        val descriptionLabel = JBLabel("""
            <html>
            <body>
            配置 MCP (Model Context Protocol) 服务。您可以在不同级别配置服务：<br>
            <b>User</b>: 应用于所有项目<br>
            <b>Local</b>: 应用于当前机器<br>
            <b>Project</b>: 仅应用于当前项目<br><br>
            支持两种 JSON 格式：<br>
            1. 单个服务配置：直接定义服务对象<br>
            2. 多个服务配置：使用 "mcpServers" 包装多个服务
            </body>
            </html>
        """.trimIndent())
        descriptionPanel.add(descriptionLabel, BorderLayout.CENTER)
        
        // 创建选项卡
        tabbedPane = JBTabbedPane()
        
        // User 级别配置
        val userPanel = createConfigPanel("User", getUserConfigExample())
        userConfigTextArea = userPanel.getClientProperty("textArea") as JBTextArea
        tabbedPane.addTab("User", userPanel)
        
        // Local 级别配置
        val localPanel = createConfigPanel("Local", getLocalConfigExample())
        localConfigTextArea = localPanel.getClientProperty("textArea") as JBTextArea
        tabbedPane.addTab("Local", localPanel)
        
        // Project 级别配置
        val projectPanel = createConfigPanel("Project", getProjectConfigExample())
        projectConfigTextArea = projectPanel.getClientProperty("textArea") as JBTextArea
        tabbedPane.addTab("Project", projectPanel)
        
        panel.add(descriptionPanel, BorderLayout.NORTH)
        panel.add(tabbedPane, BorderLayout.CENTER)
        
        // 加载现有配置
        loadConfigurations()
        
        return panel
    }
    
    private fun createConfigPanel(level: String, example: String): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)
        
        // 配置输入区域
        val textArea = JBTextArea()
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        textArea.text = example
        
        val scrollPane = JBScrollPane(textArea)
        scrollPane.preferredSize = JBUI.size(600, 400)
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 存储引用
        panel.putClientProperty("textArea", textArea)
        
        return panel
    }
    
    private fun getUserConfigExample(): String = """
{
  "mcpServers": {
    "playwright": {
      "command": "docker",
      "args": ["run", "-i", "--rm", "--init", "--label", "com.docker.compose.project=mcps", "--pull=always", "mcr.microsoft.com/playwright/mcp"]
    }
  }
}
    """.trimIndent()
    
    private fun getLocalConfigExample(): String = """
{
  "mcpServers": {
    "word-mcp": {
      "command": "/opt/homebrew/bin/uvx",
      "args": ["--from", "office-word-mcp-server", "word_mcp_server"]
    }
  }
}
    """.trimIndent()
    
    private fun getProjectConfigExample(): String = """
{
  "mcpServers": {
    "postgres@host.docker.internal": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm", "--label", "com.docker.compose.project=mcps",
        "-e", "DATABASE_URI",
        "crystaldba/postgres-mcp:latest",
        "--access-mode=unrestricted"
      ],
      "env": {
        "DATABASE_URI": "postgresql://username:password@host.docker.internal:5432/database"
      }
    }
  }
}
    """.trimIndent()
    
    private fun loadConfigurations() {
        val settingsService = service<McpSettingsService>()
        
        userConfigTextArea?.text = settingsService.getUserConfig()
        localConfigTextArea?.text = settingsService.getLocalConfig()
        projectConfigTextArea?.text = settingsService.getProjectConfig(project)
    }
    
    override fun isModified(): Boolean {
        val settingsService = service<McpSettingsService>()
        
        return userConfigTextArea?.text != settingsService.getUserConfig() ||
               localConfigTextArea?.text != settingsService.getLocalConfig() ||
               projectConfigTextArea?.text != settingsService.getProjectConfig(project)
    }
    
    override fun apply() {
        val settingsService = service<McpSettingsService>()
        
        // 验证并保存配置
        userConfigTextArea?.text?.let { config ->
            if (validateJsonConfig(config)) {
                settingsService.setUserConfig(config)
            } else {
                throw ConfigurationException("User 级别的 JSON 配置格式不正确")
            }
        }
        
        localConfigTextArea?.text?.let { config ->
            if (validateJsonConfig(config)) {
                settingsService.setLocalConfig(config)
            } else {
                throw ConfigurationException("Local 级别的 JSON 配置格式不正确")
            }
        }
        
        projectConfigTextArea?.text?.let { config ->
            if (validateJsonConfig(config)) {
                settingsService.setProjectConfig(project, config)
            } else {
                throw ConfigurationException("Project 级别的 JSON 配置格式不正确")
            }
        }
    }
    
    private fun validateJsonConfig(jsonStr: String): Boolean {
        if (jsonStr.isBlank()) return true
        
        return try {
            objectMapper.readTree(jsonStr)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun reset() {
        loadConfigurations()
    }
}