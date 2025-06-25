package com.claudecodeplus.ui.jewel

import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition

/**
 * Jewel 主题提供者接口
 * 用于统一管理主题配置，便于插件集成
 */
interface JewelThemeProvider {
    /**
     * 获取当前主题样式
     */
    fun getCurrentThemeStyle(): JewelThemeStyle
    
    /**
     * 获取系统是否为暗色主题
     */
    fun isSystemDark(): Boolean
    
    /**
     * 获取主题配置
     */
    fun getThemeConfig(): JewelThemeConfig
    
    /**
     * 主题变化监听器
     */
    fun addThemeChangeListener(listener: (JewelThemeStyle) -> Unit)
    
    /**
     * 移除主题变化监听器
     */
    fun removeThemeChangeListener(listener: (JewelThemeStyle) -> Unit)
}

/**
 * 默认的主题提供者实现
 */
class DefaultJewelThemeProvider(
    private var themeStyle: JewelThemeStyle = JewelThemeStyle.LIGHT,
    private var isSystemDark: Boolean = false,
    private var themeConfig: JewelThemeConfig = JewelThemeConfig.DEFAULT
) : JewelThemeProvider {
    
    private val listeners = mutableListOf<(JewelThemeStyle) -> Unit>()
    
    override fun getCurrentThemeStyle(): JewelThemeStyle = themeStyle
    
    override fun isSystemDark(): Boolean = isSystemDark
    
    override fun getThemeConfig(): JewelThemeConfig = themeConfig
    
    override fun addThemeChangeListener(listener: (JewelThemeStyle) -> Unit) {
        listeners.add(listener)
    }
    
    override fun removeThemeChangeListener(listener: (JewelThemeStyle) -> Unit) {
        listeners.remove(listener)
    }
    
    /**
     * 更新主题样式
     */
    fun updateThemeStyle(newThemeStyle: JewelThemeStyle) {
        if (themeStyle != newThemeStyle) {
            themeStyle = newThemeStyle
            notifyListeners()
        }
    }
    
    /**
     * 更新系统暗色主题状态
     */
    fun updateSystemDark(isDark: Boolean) {
        if (isSystemDark != isDark) {
            isSystemDark = isDark
            if (themeStyle == JewelThemeStyle.SYSTEM) {
                notifyListeners()
            }
        }
    }
    
    /**
     * 更新主题配置
     */
    fun updateThemeConfig(newConfig: JewelThemeConfig) {
        if (themeConfig != newConfig) {
            themeConfig = newConfig
            notifyListeners()
        }
    }
    
    private fun notifyListeners() {
        listeners.forEach { it(themeStyle) }
    }
}

/**
 * 插件主题提供者 - 用于与 IntelliJ 插件系统集成
 */
class PluginJewelThemeProvider(
    private val getPluginTheme: () -> JewelThemeStyle = { JewelThemeStyle.SYSTEM },
    private val isPluginDarkTheme: () -> Boolean = { false }
) : JewelThemeProvider {
    
    private val listeners = mutableListOf<(JewelThemeStyle) -> Unit>()
    
    override fun getCurrentThemeStyle(): JewelThemeStyle = getPluginTheme()
    
    override fun isSystemDark(): Boolean = isPluginDarkTheme()
    
    override fun getThemeConfig(): JewelThemeConfig = JewelThemeConfig.DEFAULT
    
    override fun addThemeChangeListener(listener: (JewelThemeStyle) -> Unit) {
        listeners.add(listener)
    }
    
    override fun removeThemeChangeListener(listener: (JewelThemeStyle) -> Unit) {
        listeners.remove(listener)
    }
    
    /**
     * 通知主题变化（由插件系统调用）
     */
    fun notifyThemeChanged() {
        listeners.forEach { it(getCurrentThemeStyle()) }
    }
}

/**
 * 主题工具类
 */
object JewelThemeUtils {
    /**
     * 根据主题提供者获取实际的 Jewel 主题定义
     */
    fun getThemeDefinition(provider: JewelThemeProvider) = run {
        val actualTheme = JewelThemeStyle.getActualTheme(
            provider.getCurrentThemeStyle(),
            provider.isSystemDark()
        )
        
        when (actualTheme) {
            JewelThemeStyle.DARK, JewelThemeStyle.HIGH_CONTRAST_DARK -> {
                JewelTheme.darkThemeDefinition()
            }
            JewelThemeStyle.LIGHT, JewelThemeStyle.HIGH_CONTRAST_LIGHT -> {
                JewelTheme.lightThemeDefinition()
            }
            else -> JewelTheme.lightThemeDefinition() // 默认亮色
        }
    }
    
    /**
     * 检测系统主题
     */
    fun detectSystemTheme(): Boolean {
        // 这里可以添加系统主题检测逻辑
        // 对于 macOS 可以检查系统外观设置
        // 对于其他系统可以有相应的检测方法
        return try {
            System.getProperty("apple.awt.application.appearance")?.contains("Dark") == true
        } catch (e: Exception) {
            false // 默认为亮色主题
        }
    }
} 