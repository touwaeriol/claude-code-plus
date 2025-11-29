package com.asakii.plugin.theme

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.util.ui.UIUtil
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import javax.swing.UIManager

/**
 * 完整的 IDEA 主题集成
 * 获取 IDEA 的所有主题配置：颜色、字体、大小等
 */
object IdeaThemeIntegration {
    
    private val logger = Logger.getInstance(IdeaThemeIntegration::class.java)
    
    /**
     * 获取当前 IDEA 主题配置
     */
    fun getCurrentThemeConfig(): IdeaThemeConfig {
        val colorScheme = EditorColorsManager.getInstance().globalScheme
        val isDark = com.intellij.util.ui.StartupUiUtil.isDarkTheme

        return IdeaThemeConfig(
            isDarkTheme = isDark,
            colors = getThemeColors(colorScheme, isDark),
            fonts = getThemeFonts(colorScheme),
            metrics = getThemeMetrics()
        )
    }
    
    /**
     * 获取主题颜色配置
     */
    private fun getThemeColors(scheme: EditorColorsScheme, isDark: Boolean): IdeaThemeColors {
        return IdeaThemeColors(
            // 背景色
            background = scheme.defaultBackground ?: JBColor.background(),
            panelBackground = UIManager.getColor("Panel.background") ?: JBColor.PanelBackground,
            
            // 前景色（文字）
            foreground = scheme.defaultForeground ?: JBColor.foreground(),
            
            // 编辑器颜色
            editorBackground = scheme.defaultBackground ?: Color.WHITE,
            editorForeground = scheme.defaultForeground ?: Color.BLACK,
            
            // 选择颜色
            selectionBackground = scheme.getAttributes(com.intellij.openapi.editor.DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)
                ?.backgroundColor ?: UIManager.getColor("List.selectionBackground") ?: JBColor.BLUE,
            selectionForeground = UIManager.getColor("List.selectionForeground") ?: JBColor.WHITE,
            
            // 边框颜色
            borderColor = UIManager.getColor("Borders.color") ?: JBColor.border(),
            
            // 链接颜色
            linkColor = UIManager.getColor("link.foreground") ?: JBColor.BLUE,
            
            // 按钮颜色
            buttonBackground = UIManager.getColor("Button.background") ?: JBColor.GRAY,
            buttonForeground = UIManager.getColor("Button.foreground") ?: JBColor.foreground(),
            
            // 输入框颜色
            textFieldBackground = UIManager.getColor("TextField.background") ?: Color.WHITE,
            textFieldForeground = UIManager.getColor("TextField.foreground") ?: Color.BLACK,
            
            // 提示颜色
            infoForeground = UIManager.getColor("Label.infoForeground") ?: JBColor.GRAY,
            
            // 错误和警告颜色
            errorColor = scheme.getAttributes(com.intellij.openapi.editor.colors.CodeInsightColors.ERRORS_ATTRIBUTES)
                ?.errorStripeColor ?: JBColor.RED,
            warningColor = scheme.getAttributes(com.intellij.openapi.editor.colors.CodeInsightColors.WARNINGS_ATTRIBUTES)
                ?.errorStripeColor ?: JBColor.YELLOW,
            
            // 代码高亮颜色
            keywordColor = scheme.getAttributes(com.intellij.openapi.editor.DefaultLanguageHighlighterColors.KEYWORD)
                ?.foregroundColor ?: JBColor.BLUE,
            stringColor = scheme.getAttributes(com.intellij.openapi.editor.DefaultLanguageHighlighterColors.STRING)
                ?.foregroundColor ?: JBColor.GREEN,
            commentColor = scheme.getAttributes(com.intellij.openapi.editor.DefaultLanguageHighlighterColors.LINE_COMMENT)
                ?.foregroundColor ?: JBColor.GRAY
        )
    }
    
    /**
     * 获取主题字体配置
     */
    private fun getThemeFonts(scheme: EditorColorsScheme): ThemeFonts {
        val editorFont = scheme.editorFontName
        val editorFontSize = scheme.editorFontSize
        val lineSpacing = scheme.lineSpacing
        
        // UI 字体
        val uiFont = UIManager.getFont("Label.font") ?: Font("Dialog", Font.PLAIN, 12)
        
        return ThemeFonts(
            // 编辑器字体
            editorFontFamily = editorFont,
            editorFontSize = editorFontSize,
            editorLineSpacing = lineSpacing,
            
            // UI 字体
            uiFontFamily = uiFont.family,
            uiFontSize = uiFont.size,
            
            // 控制台字体
            consoleFontFamily = scheme.consoleFontName,
            consoleFontSize = scheme.consoleFontSize,
            
            // 使用编辑器配置的字体设置
            useLigatures = scheme.isUseLigatures,
            useAntialiasing = true
        )
    }
    
    /**
     * 获取主题度量（间距、大小等）
     */
    private fun getThemeMetrics(): ThemeMetrics {
        return ThemeMetrics(
            // 基础间距
            defaultSpacing = 8,
            compactSpacing = 4,
            largeSpacing = 16,
            
            // 圆角
            borderRadius = 4,
            
            // 边框宽度
            borderWidth = 1,
            
            // 滚动条宽度
            scrollbarWidth = 12,
            
            // 工具栏高度
            toolbarHeight = UIManager.getInt("ToolBar.height") ?: 30,
            
            // 标签页高度
            tabHeight = UIManager.getInt("TabbedPane.tabHeight") ?: 36
        )
    }
    
    /**
     * 注册主题变化监听器
     */
    fun registerThemeChangeListener(onChange: (IdeaThemeConfig) -> Unit) {
        // 监听 LAF 变化
        val connection = ApplicationManager.getApplication().messageBus.connect()
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
            val newConfig = getCurrentThemeConfig()
            logger.info("IDEA 主题已变更")
            onChange(newConfig)
        })
        
        // 监听编辑器配色方案变化
        connection.subscribe(EditorColorsManager.TOPIC, object : com.intellij.openapi.editor.colors.EditorColorsListener {
            override fun globalSchemeChange(scheme: EditorColorsScheme?) {
                val newConfig = getCurrentThemeConfig()
                logger.info("编辑器配色方案已变更")
                onChange(newConfig)
            }
        })
    }
}

/**
 * IDEA 主题配置
 */
data class IdeaThemeConfig(
    val isDarkTheme: Boolean,
    val colors: IdeaThemeColors,
    val fonts: ThemeFonts,
    val metrics: ThemeMetrics
)

/**
 * 主题颜色配置
 */
data class IdeaThemeColors(
    // 背景
    val background: Color,
    val panelBackground: Color,
    val editorBackground: Color,
    
    // 前景（文字）
    val foreground: Color,
    val editorForeground: Color,
    
    // 选择
    val selectionBackground: Color,
    val selectionForeground: Color,
    
    // 边框
    val borderColor: Color,
    
    // 特殊颜色
    val linkColor: Color,
    val errorColor: Color,
    val warningColor: Color,
    val infoForeground: Color,
    
    // 按钮
    val buttonBackground: Color,
    val buttonForeground: Color,
    
    // 输入框
    val textFieldBackground: Color,
    val textFieldForeground: Color,
    
    // 代码高亮
    val keywordColor: Color,
    val stringColor: Color,
    val commentColor: Color
) {
    /**
     * 转换为 Compose 可用的颜色值
     */
    fun toComposeColors(): ComposeThemeColors {
        return ComposeThemeColors(
            background = background.hashCode(),
            panelBackground = panelBackground.hashCode(),
            foreground = foreground.hashCode(),
            selectionBackground = selectionBackground.hashCode(),
            selectionForeground = selectionForeground.hashCode(),
            borderColor = borderColor.hashCode(),
            linkColor = linkColor.hashCode(),
            errorColor = errorColor.hashCode(),
            warningColor = warningColor.hashCode(),
            infoColor = infoForeground.hashCode(),
            buttonBackground = buttonBackground.hashCode(),
            buttonForeground = buttonForeground.hashCode(),
            textFieldBackground = textFieldBackground.hashCode(),
            textFieldForeground = textFieldForeground.hashCode(),
            keywordColor = keywordColor.hashCode(),
            stringColor = stringColor.hashCode(),
            commentColor = commentColor.hashCode()
        )
    }
}

/**
 * Compose 主题颜色（Int 格式）
 */
data class ComposeThemeColors(
    val background: Int,
    val panelBackground: Int,
    val foreground: Int,
    val selectionBackground: Int,
    val selectionForeground: Int,
    val borderColor: Int,
    val linkColor: Int,
    val errorColor: Int,
    val warningColor: Int,
    val infoColor: Int,
    val buttonBackground: Int,
    val buttonForeground: Int,
    val textFieldBackground: Int,
    val textFieldForeground: Int,
    val keywordColor: Int,
    val stringColor: Int,
    val commentColor: Int
)

/**
 * 主题字体配置
 */
data class ThemeFonts(
    // 编辑器字体
    val editorFontFamily: String,
    val editorFontSize: Int,
    val editorLineSpacing: Float,
    
    // UI 字体
    val uiFontFamily: String,
    val uiFontSize: Int,
    
    // 控制台字体
    val consoleFontFamily: String,
    val consoleFontSize: Int,
    
    // 字体特性
    val useLigatures: Boolean,
    val useAntialiasing: Boolean
)

/**
 * 主题度量配置
 */
data class ThemeMetrics(
    val defaultSpacing: Int,
    val compactSpacing: Int,
    val largeSpacing: Int,
    val borderRadius: Int,
    val borderWidth: Int,
    val scrollbarWidth: Int,
    val toolbarHeight: Int,
    val tabHeight: Int
)