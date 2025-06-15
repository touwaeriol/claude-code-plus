package com.claudecodeplus.util

import java.awt.Color
import javax.swing.text.AttributeSet
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * ANSI 转义序列解析器
 */
object AnsiParser {
    
    private val ANSI_PATTERN = "\u001B\\[[0-9;]*m".toRegex()
    
    // ANSI 颜色映射
    private val COLORS = mapOf(
        30 to Color.BLACK,
        31 to Color.RED,
        32 to Color.GREEN,
        33 to Color.YELLOW,
        34 to Color.BLUE,
        35 to Color.MAGENTA,
        36 to Color.CYAN,
        37 to Color.LIGHT_GRAY,
        90 to Color.DARK_GRAY,
        91 to Color(255, 128, 128), // Bright Red
        92 to Color(128, 255, 128), // Bright Green
        93 to Color(255, 255, 128), // Bright Yellow
        94 to Color(128, 128, 255), // Bright Blue
        95 to Color(255, 128, 255), // Bright Magenta
        96 to Color(128, 255, 255), // Bright Cyan
        97 to Color.WHITE
    )
    
    /**
     * 解析包含 ANSI 转义序列的文本，返回带样式的文本段
     */
    fun parse(text: String): List<StyledSegment> {
        val segments = mutableListOf<StyledSegment>()
        var currentAttributes = SimpleAttributeSet()
        var lastEnd = 0
        
        ANSI_PATTERN.findAll(text).forEach { match ->
            // 添加转义序列之前的文本
            if (match.range.first > lastEnd) {
                val content = text.substring(lastEnd, match.range.first)
                if (content.isNotEmpty()) {
                    segments.add(StyledSegment(content, SimpleAttributeSet(currentAttributes)))
                }
            }
            
            // 解析转义序列
            val codes = match.value
                .removeSurrounding("\u001B[", "m")
                .split(";")
                .mapNotNull { it.toIntOrNull() }
            
            // 更新属性
            currentAttributes = updateAttributes(currentAttributes, codes)
            lastEnd = match.range.last + 1
        }
        
        // 添加剩余的文本
        if (lastEnd < text.length) {
            segments.add(StyledSegment(text.substring(lastEnd), SimpleAttributeSet(currentAttributes)))
        }
        
        return segments
    }
    
    /**
     * 移除文本中的所有 ANSI 转义序列
     */
    fun stripAnsi(text: String): String {
        return text.replace(ANSI_PATTERN, "")
    }
    
    private fun updateAttributes(attributes: SimpleAttributeSet, codes: List<Int>): SimpleAttributeSet {
        val newAttributes = SimpleAttributeSet(attributes)
        
        for (code in codes) {
            when (code) {
                0 -> {
                    // 重置所有属性
                    newAttributes.removeAttributes(newAttributes)
                }
                1 -> StyleConstants.setBold(newAttributes, true)
                2 -> StyleConstants.setItalic(newAttributes, true)
                4 -> StyleConstants.setUnderline(newAttributes, true)
                in 30..37, in 90..97 -> {
                    // 前景色
                    COLORS[code]?.let { color ->
                        StyleConstants.setForeground(newAttributes, color)
                    }
                }
                in 40..47, in 100..107 -> {
                    // 背景色
                    val colorCode = if (code >= 100) code - 60 else code - 10
                    COLORS[colorCode]?.let { color ->
                        StyleConstants.setBackground(newAttributes, color)
                    }
                }
            }
        }
        
        return newAttributes
    }
    
    /**
     * 带样式的文本段
     */
    data class StyledSegment(
        val text: String,
        val attributes: AttributeSet
    )
}