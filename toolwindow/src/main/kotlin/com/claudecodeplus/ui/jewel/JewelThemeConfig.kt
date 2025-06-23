package com.claudecodeplus.ui.jewel

/**
 * Jewel UI 主题配置
 */
data class JewelThemeConfig(
    /** 主题风格 */
    val themeStyle: JewelThemeStyle = JewelThemeStyle.SYSTEM,
    
    /** 字体大小缩放比例 */
    val fontScale: Float = 1.0f,
    
    /** 是否使用等宽字体显示代码 */
    val useMonospaceForCode: Boolean = true,
    
    /** 消息间距（dp） */
    val messageSpacing: Int = 12,
    
    /** 是否显示时间戳 */
    val showTimestamp: Boolean = true,
    
    /** 是否启用动画效果 */
    val enableAnimations: Boolean = true
) {
    companion object {
        /** 默认配置 */
        val DEFAULT = JewelThemeConfig()
        
        /** 紧凑模式配置 */
        val COMPACT = JewelThemeConfig(
            fontScale = 0.9f,
            messageSpacing = 8,
            showTimestamp = false
        )
        
        /** 舒适模式配置 */
        val COMFORTABLE = JewelThemeConfig(
            fontScale = 1.1f,
            messageSpacing = 16
        )
    }
}