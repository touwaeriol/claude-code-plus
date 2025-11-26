package com.asakii.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBPanel
import javax.swing.JComponent

/**
 * Claude Code Plus 主配置页面
 */
class ClaudeCodePlusConfigurable : SearchableConfigurable, ConfigurableGroup {
    
    override fun getId(): String = "com.asakii.settings"
    
    override fun getDisplayName(): String = "Claude Code Plus"
    
    override fun getConfigurables(): Array<Configurable> = arrayOf(
        McpConfigurable()
    )
    
    override fun createComponent(): JComponent {
        // 主配置页面可以显示一些概述信息
        return JBPanel<JBPanel<*>>()
    }
    
    override fun isModified(): Boolean = false
    
    override fun apply() {
        // 主配置页面不需要应用设置
    }
}