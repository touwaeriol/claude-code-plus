package com.claudecodeplus.ui.jewel

/**
 * Jewel UI 主题风格枚举
 */
enum class JewelThemeStyle {
    /** 暗色主题 */
    DARK,
    
    /** 亮色主题 */
    LIGHT,
    
    /** 跟随系统 */
    SYSTEM,
    
    /** 高对比度暗色主题 */
    HIGH_CONTRAST_DARK,
    
    /** 高对比度亮色主题 */
    HIGH_CONTRAST_LIGHT;
    
    companion object {
        /**
         * 根据系统设置获取实际主题
         */
        fun getActualTheme(style: JewelThemeStyle, isSystemDark: Boolean): JewelThemeStyle {
            return when (style) {
                SYSTEM -> if (isSystemDark) DARK else LIGHT
                else -> style
            }
        }
    }
}