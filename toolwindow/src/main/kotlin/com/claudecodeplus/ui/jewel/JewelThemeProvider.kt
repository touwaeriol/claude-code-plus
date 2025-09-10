package com.claudecodeplus.ui.jewel

import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Jewel 主题提供者接口
 * 在IDE平台环境中，主题由IDE自动管理，这个接口用于兼容独立应用模式
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
 * IDE插件环境的主题提供者实现
 * 在IDE环境中，主题由平台自动管理，此类主要用于兼容性
 */
class IdeJewelThemeProvider(
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
     * 更新主题状态（内部使用）
     */
    internal fun updateThemeStyle(newStyle: JewelThemeStyle, systemDark: Boolean) {
        if (themeStyle != newStyle || isSystemDark != systemDark) {
            themeStyle = newStyle
            isSystemDark = systemDark
            listeners.forEach { it(newStyle) }
        }
    }
}


/**
 * IDE平台主题工具类
 * 在IDE环境中，主题由平台自动管理
 */
object IdePlatformTheme {
    
    /**
     * 创建默认的主题提供者
     * 在IDE环境中返回简化的实现
     */
    fun createProvider(): JewelThemeProvider {
        return IdeJewelThemeProvider()
    }
    
    /**
     * 检测系统主题
     * 在IDE环境中，这个信息由IDE平台提供
     */
    fun detectSystemTheme(): Boolean {
        return try {
            // 在IDE环境中，主题由平台管理，这里提供默认值
            false
        } catch (e: Exception) {
            false
        }
    }
}