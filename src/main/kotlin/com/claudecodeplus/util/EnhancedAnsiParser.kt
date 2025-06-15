package com.claudecodeplus.util

import java.awt.Color
import javax.swing.text.*

/**
 * 增强的 ANSI 转义序列解析器
 * 支持更多 ANSI 特性和更好的性能
 */
object EnhancedAnsiParser {
    
    // 更完整的 ANSI 模式，包括更多控制序列
    private val ANSI_PATTERN = """\u001B\[[0-9;]*[mGKHfABCDsu]""".toRegex()
    
    // 扩展的颜色映射，包括 256 色支持
    private val BASIC_COLORS = mapOf(
        30 to Color(0, 0, 0),        // Black
        31 to Color(205, 49, 49),    // Red
        32 to Color(13, 188, 121),   // Green
        33 to Color(229, 229, 16),   // Yellow
        34 to Color(36, 114, 200),   // Blue
        35 to Color(188, 63, 188),   // Magenta
        36 to Color(17, 168, 205),   // Cyan
        37 to Color(229, 229, 229),  // White
        
        // Bright colors
        90 to Color(102, 102, 102),  // Bright Black
        91 to Color(241, 76, 76),    // Bright Red
        92 to Color(35, 209, 139),   // Bright Green
        93 to Color(245, 245, 67),   // Bright Yellow
        94 to Color(59, 142, 234),   // Bright Blue
        95 to Color(214, 112, 214),  // Bright Magenta
        96 to Color(41, 184, 219),   // Bright Cyan
        97 to Color(255, 255, 255)   // Bright White
    )
    
    /**
     * 解析 ANSI 文本并返回 StyledDocument
     */
    fun parseToStyledDocument(text: String, doc: StyledDocument? = null): StyledDocument {
        val document = doc ?: DefaultStyledDocument()
        val segments = parse(text)
        
        segments.forEach { segment ->
            document.insertString(document.length, segment.text, segment.attributes)
        }
        
        return document
    }
    
    /**
     * 解析包含 ANSI 转义序列的文本
     */
    fun parse(text: String): List<StyledSegment> {
        val segments = mutableListOf<StyledSegment>()
        var currentStyle = createDefaultStyle()
        var lastEnd = 0
        
        ANSI_PATTERN.findAll(text).forEach { match ->
            // 添加转义序列之前的文本
            if (match.range.first > lastEnd) {
                val content = text.substring(lastEnd, match.range.first)
                if (content.isNotEmpty()) {
                    segments.add(StyledSegment(content, SimpleAttributeSet(currentStyle)))
                }
            }
            
            // 解析转义序列
            val sequence = match.value
            when (sequence.last()) {
                'm' -> {
                    // 样式和颜色代码
                    val codes = sequence
                        .removeSurrounding("\u001B[", "m")
                        .split(";")
                        .mapNotNull { it.toIntOrNull() }
                    currentStyle = updateStyle(currentStyle, codes)
                }
                'K' -> {
                    // 清除行（通常忽略）
                }
                'G', 'H', 'f' -> {
                    // 光标移动（在文本显示中通常忽略）
                }
                'A', 'B', 'C', 'D' -> {
                    // 光标移动（上下左右）
                }
                's', 'u' -> {
                    // 保存/恢复光标位置
                }
            }
            
            lastEnd = match.range.last + 1
        }
        
        // 添加剩余的文本
        if (lastEnd < text.length) {
            val remainingText = text.substring(lastEnd)
            if (remainingText.isNotEmpty()) {
                segments.add(StyledSegment(remainingText, SimpleAttributeSet(currentStyle)))
            }
        }
        
        return segments
    }
    
    /**
     * 创建默认样式
     */
    private fun createDefaultStyle(): SimpleAttributeSet {
        return SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, Color.BLACK)
            StyleConstants.setBackground(this, Color.WHITE)
            StyleConstants.setFontFamily(this, "Monospaced")
            StyleConstants.setFontSize(this, 14)
        }
    }
    
    /**
     * 根据 ANSI 代码更新样式
     */
    private fun updateStyle(currentStyle: SimpleAttributeSet, codes: List<Int>): SimpleAttributeSet {
        val newStyle = SimpleAttributeSet(currentStyle)
        
        var i = 0
        while (i < codes.size) {
            when (val code = codes[i]) {
                0 -> {
                    // 重置所有属性
                    newStyle.removeAttributes(newStyle)
                    val defaultStyle = createDefaultStyle()
                    newStyle.addAttributes(defaultStyle)
                }
                1 -> StyleConstants.setBold(newStyle, true)
                2 -> { /* 暗淡（可以通过调整颜色实现） */ }
                3 -> StyleConstants.setItalic(newStyle, true)
                4 -> StyleConstants.setUnderline(newStyle, true)
                5 -> { /* 闪烁（通常忽略） */ }
                7 -> { /* 反色 */ 
                    val fg = StyleConstants.getForeground(newStyle)
                    val bg = StyleConstants.getBackground(newStyle)
                    StyleConstants.setForeground(newStyle, bg)
                    StyleConstants.setBackground(newStyle, fg)
                }
                8 -> { /* 隐藏（设置前景色与背景色相同） */
                    val bg = StyleConstants.getBackground(newStyle)
                    StyleConstants.setForeground(newStyle, bg)
                }
                9 -> StyleConstants.setStrikeThrough(newStyle, true)
                
                // 基本前景色
                in 30..37, in 90..97 -> {
                    BASIC_COLORS[code]?.let { color ->
                        StyleConstants.setForeground(newStyle, color)
                    }
                }
                
                // 基本背景色
                in 40..47, in 100..107 -> {
                    val colorCode = if (code >= 100) code - 60 else code - 10
                    BASIC_COLORS[colorCode]?.let { color ->
                        StyleConstants.setBackground(newStyle, color)
                    }
                }
                
                // 256 色支持
                38 -> {
                    if (i + 2 < codes.size && codes[i + 1] == 5) {
                        val colorIndex = codes[i + 2]
                        StyleConstants.setForeground(newStyle, get256Color(colorIndex))
                        i += 2
                    } else if (i + 4 < codes.size && codes[i + 1] == 2) {
                        // RGB 颜色
                        val r = codes[i + 2].coerceIn(0, 255)
                        val g = codes[i + 3].coerceIn(0, 255)
                        val b = codes[i + 4].coerceIn(0, 255)
                        StyleConstants.setForeground(newStyle, Color(r, g, b))
                        i += 4
                    }
                }
                
                48 -> {
                    if (i + 2 < codes.size && codes[i + 1] == 5) {
                        val colorIndex = codes[i + 2]
                        StyleConstants.setBackground(newStyle, get256Color(colorIndex))
                        i += 2
                    } else if (i + 4 < codes.size && codes[i + 1] == 2) {
                        // RGB 颜色
                        val r = codes[i + 2].coerceIn(0, 255)
                        val g = codes[i + 3].coerceIn(0, 255)
                        val b = codes[i + 4].coerceIn(0, 255)
                        StyleConstants.setBackground(newStyle, Color(r, g, b))
                        i += 4
                    }
                }
                
                // 重置特定属性
                21 -> StyleConstants.setBold(newStyle, false)
                22 -> { /* 重置暗淡/粗体 */ 
                    StyleConstants.setBold(newStyle, false)
                }
                23 -> StyleConstants.setItalic(newStyle, false)
                24 -> StyleConstants.setUnderline(newStyle, false)
                25 -> { /* 重置闪烁 */ }
                27 -> { /* 重置反色 */ }
                28 -> { /* 重置隐藏 */ }
                29 -> StyleConstants.setStrikeThrough(newStyle, false)
                
                39 -> {
                    // 默认前景色
                    StyleConstants.setForeground(newStyle, Color.BLACK)
                }
                49 -> {
                    // 默认背景色
                    StyleConstants.setBackground(newStyle, Color.WHITE)
                }
            }
            i++
        }
        
        return newStyle
    }
    
    /**
     * 获取 256 色调色板中的颜色
     */
    private fun get256Color(index: Int): Color {
        return when (index) {
            in 0..15 -> {
                // 标准 16 色
                BASIC_COLORS[if (index < 8) index + 30 else index + 82] ?: Color.BLACK
            }
            in 16..231 -> {
                // 216 色立方体
                val i = index - 16
                val r = (i / 36) * 51
                val g = ((i % 36) / 6) * 51
                val b = (i % 6) * 51
                Color(r, g, b)
            }
            in 232..255 -> {
                // 灰度
                val gray = (index - 232) * 10 + 8
                Color(gray, gray, gray)
            }
            else -> Color.BLACK
        }
    }
    
    /**
     * 移除文本中的所有 ANSI 转义序列
     */
    fun stripAnsi(text: String): String {
        return text.replace(ANSI_PATTERN, "")
    }
    
    /**
     * 带样式的文本段
     */
    data class StyledSegment(
        val text: String,
        val attributes: AttributeSet
    )
}