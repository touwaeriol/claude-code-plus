package com.claudecodeplus.ui.jewel.components.tools.output

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * 简单的 ANSI 文本解析器
 * 支持基本的 ANSI 颜色代码
 */
class SimpleAnsiParser {
    
    /**
     * 解析 ANSI 文本并返回 AnnotatedString
     */
    fun parseAnsiText(text: String): AnnotatedString {
        return buildAnnotatedString {
            var currentStyle = SpanStyle()
            val ansiPattern = Regex("\u001B\\[(\\d+(?:;\\d+)*)m")
            var lastEnd = 0
            
            ansiPattern.findAll(text).forEach { match ->
                // 添加之前的文本
                if (match.range.first > lastEnd) {
                    withStyle(currentStyle) {
                        append(text.substring(lastEnd, match.range.first))
                    }
                }
                
                // 解析 ANSI 代码
                val codes = match.groupValues[1]
                    .split(";")
                    .mapNotNull { it.toIntOrNull() }
                
                codes.forEach { code ->
                    currentStyle = updateStyle(currentStyle, code)
                }
                
                lastEnd = match.range.last + 1
            }
            
            // 添加剩余文本
            if (lastEnd < text.length) {
                withStyle(currentStyle) {
                    append(text.substring(lastEnd))
                }
            }
        }
    }
    
    /**
     * 根据 ANSI 代码更新样式
     */
    private fun updateStyle(currentStyle: SpanStyle, code: Int): SpanStyle {
        return when (code) {
            0 -> SpanStyle() // Reset
            1 -> currentStyle.copy(fontWeight = FontWeight.Bold)
            3 -> currentStyle.copy(fontStyle = FontStyle.Italic)
            4 -> currentStyle.copy(textDecoration = TextDecoration.Underline)
            9 -> currentStyle.copy(textDecoration = TextDecoration.LineThrough)
            
            // 前景色
            30 -> currentStyle.copy(color = AnsiColors.BLACK)
            31 -> currentStyle.copy(color = AnsiColors.RED)
            32 -> currentStyle.copy(color = AnsiColors.GREEN)
            33 -> currentStyle.copy(color = AnsiColors.YELLOW)
            34 -> currentStyle.copy(color = AnsiColors.BLUE)
            35 -> currentStyle.copy(color = AnsiColors.MAGENTA)
            36 -> currentStyle.copy(color = AnsiColors.CYAN)
            37 -> currentStyle.copy(color = AnsiColors.WHITE)
            
            // 亮色前景色
            90 -> currentStyle.copy(color = AnsiColors.BRIGHT_BLACK)
            91 -> currentStyle.copy(color = AnsiColors.BRIGHT_RED)
            92 -> currentStyle.copy(color = AnsiColors.BRIGHT_GREEN)
            93 -> currentStyle.copy(color = AnsiColors.BRIGHT_YELLOW)
            94 -> currentStyle.copy(color = AnsiColors.BRIGHT_BLUE)
            95 -> currentStyle.copy(color = AnsiColors.BRIGHT_MAGENTA)
            96 -> currentStyle.copy(color = AnsiColors.BRIGHT_CYAN)
            97 -> currentStyle.copy(color = AnsiColors.BRIGHT_WHITE)
            
            // 背景色
            40 -> currentStyle.copy(background = AnsiColors.BLACK)
            41 -> currentStyle.copy(background = AnsiColors.RED)
            42 -> currentStyle.copy(background = AnsiColors.GREEN)
            43 -> currentStyle.copy(background = AnsiColors.YELLOW)
            44 -> currentStyle.copy(background = AnsiColors.BLUE)
            45 -> currentStyle.copy(background = AnsiColors.MAGENTA)
            46 -> currentStyle.copy(background = AnsiColors.CYAN)
            47 -> currentStyle.copy(background = AnsiColors.WHITE)
            
            // 亮色背景色
            100 -> currentStyle.copy(background = AnsiColors.BRIGHT_BLACK)
            101 -> currentStyle.copy(background = AnsiColors.BRIGHT_RED)
            102 -> currentStyle.copy(background = AnsiColors.BRIGHT_GREEN)
            103 -> currentStyle.copy(background = AnsiColors.BRIGHT_YELLOW)
            104 -> currentStyle.copy(background = AnsiColors.BRIGHT_BLUE)
            105 -> currentStyle.copy(background = AnsiColors.BRIGHT_MAGENTA)
            106 -> currentStyle.copy(background = AnsiColors.BRIGHT_CYAN)
            107 -> currentStyle.copy(background = AnsiColors.BRIGHT_WHITE)
            
            else -> currentStyle
        }
    }
}

/**
 * ANSI 颜色常量
 */
object AnsiColors {
    val BLACK = Color(0, 0, 0)
    val RED = Color(170, 0, 0)
    val GREEN = Color(0, 170, 0)
    val YELLOW = Color(170, 85, 0)
    val BLUE = Color(0, 0, 170)
    val MAGENTA = Color(170, 0, 170)
    val CYAN = Color(0, 170, 170)
    val WHITE = Color(170, 170, 170)
    
    val BRIGHT_BLACK = Color(85, 85, 85)
    val BRIGHT_RED = Color(255, 85, 85)
    val BRIGHT_GREEN = Color(85, 255, 85)
    val BRIGHT_YELLOW = Color(255, 255, 85)
    val BRIGHT_BLUE = Color(85, 85, 255)
    val BRIGHT_MAGENTA = Color(255, 85, 255)
    val BRIGHT_CYAN = Color(85, 255, 255)
    val BRIGHT_WHITE = Color(255, 255, 255)
    
    // 使用主题相对应的终端背景色
    // 深色主题使用深色背景，浅色主题使用浅色背景
    // 注意：这些将在Composable中动态获取主题色
    val DEFAULT_BACKGROUND = Color.Transparent  // 占位符，实际使用时会被主题色替换
    val DEFAULT_FOREGROUND = Color.Transparent  // 占位符，实际使用时会被主题色替换
}