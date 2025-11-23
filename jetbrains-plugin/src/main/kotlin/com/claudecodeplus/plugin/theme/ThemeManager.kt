package com.claudecodeplus.plugin.theme

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import javax.swing.UIManager

/**
 * 主题管理器
 * 
 * 监听 IDE 主题变更并通知组件重绘
 */
class ThemeManager(private val project: Project) : Disposable {
    
    private val themeChangeListeners = mutableListOf<() -> Unit>()
    
    init {
        // 注册主题变更监听器
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
            notifyThemeChanged()
        })
    }
    
    /**
     * 注册主题变更监听器
     */
    fun addThemeChangeListener(listener: () -> Unit) {
        themeChangeListeners.add(listener)
    }
    
    /**
     * 移除主题变更监听器
     */
    fun removeThemeChangeListener(listener: () -> Unit) {
        themeChangeListeners.remove(listener)
    }
    
    /**
     * 通知所有监听器主题已变更
     */
    private fun notifyThemeChanged() {
        themeChangeListeners.forEach { it() }
    }
    
    /**
     * 检查当前是否为暗色主题
     */
    fun isDarkTheme(): Boolean {
        val lafName = LafManager.getInstance().currentLookAndFeel?.name ?: ""
        return lafName.contains("Darcula", ignoreCase = true) || 
               lafName.contains("Dark", ignoreCase = true)
    }
    
    /**
     * 获取当前主题颜色
     */
    fun getThemeColors(): ThemeColors {
        return if (isDarkTheme()) {
            ThemeColors.DARK
        } else {
            ThemeColors.LIGHT
        }
    }
    
    override fun dispose() {
        themeChangeListeners.clear()
    }
    
    /**
     * 主题颜色定义
     */
    data class ThemeColors(
        val background: String,
        val foreground: String,
        val panelBackground: String,
        val borderColor: String,
        val highlightColor: String
    ) {
        companion object {
            val DARK = ThemeColors(
                background = "#2B2B2B",
                foreground = "#BBBBBB",
                panelBackground = "#313335",
                borderColor = "#555555",
                highlightColor = "#4A88C7"
            )
            
            val LIGHT = ThemeColors(
                background = "#FFFFFF",
                foreground = "#000000",
                panelBackground = "#F5F5F5",
                borderColor = "#CCCCCC",
                highlightColor = "#4A88C7"
            )
        }
    }
    
    companion object {
        private val instances = mutableMapOf<Project, ThemeManager>()
        
        /**
         * 获取项目的主题管理器实例
         */
        fun getInstance(project: Project): ThemeManager {
            return instances.getOrPut(project) {
                ThemeManager(project)
            }
        }
    }
}


