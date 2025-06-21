package com.claudecodeplus.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.SearchableConfigurable
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Claude Code Plus 主配置页面
 */
class ClaudeCodePlusConfigurable : SearchableConfigurable, ConfigurableGroup {
    
    override fun getId(): String = "com.claudecodeplus.settings"
    
    override fun getDisplayName(): String = "Claude Code Plus"
    
    override fun getConfigurables(): Array<Configurable> = arrayOf(
        McpConfigurable()
    )
    
    override fun createComponent(): JComponent {
        // 主配置页面可以显示一些概述信息
        return JPanel()
    }
    
    override fun isModified(): Boolean = false
    
    override fun apply() {
        // 主配置页面不需要应用设置
    }
}