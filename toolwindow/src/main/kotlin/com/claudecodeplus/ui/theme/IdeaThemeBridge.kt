package com.claudecodeplus.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * IDEA 主题桥接器
 * 将 IDEA 的主题配置转换为 Compose 可用的格式
 */
object IdeaThemeBridge {
    
    // 存储当前主题配置
    var currentTheme by mutableStateOf(IdeaComposeTheme())
        private set
    
    /**
     * 更新主题配置
     */
    fun updateTheme(
        isDark: Boolean,
        colors: IdeaColorScheme,
        fonts: IdeaFontScheme
    ) {
        currentTheme = IdeaComposeTheme(
            isDark = isDark,
            colors = colors,
            fonts = fonts
        )
    }
    
    /**
     * 从原始颜色值创建主题
     */
    fun updateFromRawValues(
        isDark: Boolean,
        backgroundColor: Int,
        foregroundColor: Int,
        primaryColor: Int,
        fontFamily: String,
        fontSize: Int
    ) {
        val colors = IdeaColorScheme(
            background = Color(backgroundColor),
            onBackground = Color(foregroundColor),
            surface = Color(backgroundColor),
            onSurface = Color(foregroundColor),
            primary = Color(primaryColor),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(primaryColor),
            onSecondary = Color(0xFFFFFFFF),
            error = Color(0xFFFF5252),
            onError = Color(0xFFFFFFFF),
            border = if (isDark) Color(0xFF3C3F41) else Color(0xFFD1D5DB),
            divider = if (isDark) Color(0xFF2B2B2B) else Color(0xFFE5E7EB)
        )
        
        val fonts = IdeaFontScheme(
            default = IdeaFont(fontFamily, fontSize.sp, FontWeight.Normal),
            code = IdeaFont(fontFamily, fontSize.sp, FontWeight.Normal),
            heading = IdeaFont(fontFamily, (fontSize + 2).sp, FontWeight.Bold),
            caption = IdeaFont(fontFamily, (fontSize - 1).sp, FontWeight.Normal)
        )
        
        currentTheme = IdeaComposeTheme(isDark, colors, fonts)
    }
}

/**
 * IDEA Compose 主题
 */
@Immutable
data class IdeaComposeTheme(
    val isDark: Boolean = false,
    val colors: IdeaColorScheme = IdeaColorScheme(),
    val fonts: IdeaFontScheme = IdeaFontScheme()
)

/**
 * IDEA 颜色方案
 */
@Immutable
data class IdeaColorScheme(
    val background: Color = Color(0xFFFFFFFF),
    val onBackground: Color = Color(0xFF000000),
    val surface: Color = Color(0xFFFFFFFF),
    val onSurface: Color = Color(0xFF000000),
    val primary: Color = Color(0xFF1976D2),
    val onPrimary: Color = Color(0xFFFFFFFF),
    val secondary: Color = Color(0xFF4C9AFF),
    val onSecondary: Color = Color(0xFFFFFFFF),
    val error: Color = Color(0xFFFF5252),
    val onError: Color = Color(0xFFFFFFFF),
    val border: Color = Color(0xFFD1D5DB),
    val divider: Color = Color(0xFFE5E7EB),
    
    // 特殊颜色
    val link: Color = Color(0xFF0066CC),
    val codeKeyword: Color = Color(0xFF0033CC),
    val codeString: Color = Color(0xFF008000),
    val codeComment: Color = Color(0xFF808080),
    val selection: Color = Color(0xFF3D7FC7),
    val onSelection: Color = Color(0xFFFFFFFF),
    
    // 状态颜色
    val warning: Color = Color(0xFFFFC107),
    val success: Color = Color(0xFF4CAF50),
    val info: Color = Color(0xFF2196F3)
)

/**
 * IDEA 字体方案
 */
@Immutable
data class IdeaFontScheme(
    val default: IdeaFont = IdeaFont(),
    val code: IdeaFont = IdeaFont(family = "JetBrains Mono"),
    val heading: IdeaFont = IdeaFont(size = 16.sp, weight = FontWeight.Bold),
    val caption: IdeaFont = IdeaFont(size = 11.sp)
)

/**
 * IDEA 字体配置
 */
@Immutable
data class IdeaFont(
    val family: String = "Inter",
    val size: androidx.compose.ui.unit.TextUnit = 13.sp,
    val weight: FontWeight = FontWeight.Normal,
    val letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp
)

/**
 * Compose 本地提供者
 */
val LocalIdeaTheme = compositionLocalOf { IdeaComposeTheme() }

/**
 * IDEA 主题 Composable
 */
@Composable
fun IdeaTheme(
    theme: IdeaComposeTheme = IdeaThemeBridge.currentTheme,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalIdeaTheme provides theme
    ) {
        content()
    }
}

/**
 * 获取当前 IDEA 主题
 */
@Composable
fun ideaTheme() = LocalIdeaTheme.current