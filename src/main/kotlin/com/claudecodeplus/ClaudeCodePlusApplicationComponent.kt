package com.claudecodeplus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.claudecodeplus.toolwindow.ClaudeCodePlusToolWindowFactory
import com.intellij.openapi.diagnostic.Logger

/**
 * 应用级组件，管理插件的生命周期
 */
class ClaudeCodePlusApplicationComponent : ApplicationComponent {
    
    companion object {
        private val logger = Logger.getInstance(ClaudeCodePlusApplicationComponent::class.java)
    }
    
    override fun initComponent() {
        logger.info("Claude Code Plus plugin initialized")
    }
    
    override fun disposeComponent() {
        logger.info("Claude Code Plus plugin disposing...")
        // 停止所有服务
        ClaudeCodePlusToolWindowFactory.stopServices()
    }
    
    override fun getComponentName(): String {
        return "ClaudeCodePlusApplicationComponent"
    }
}