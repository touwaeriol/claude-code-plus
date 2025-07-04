/*
 * MarkdownAnnotationParser.kt
 * 
 * 解析 Markdown 格式的上下文引用为 AnnotatedString
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * 上下文引用的注解标签
 */
const val CONTEXT_ANNOTATION_TAG = "context_uri"

/**
 * Markdown 上下文引用的正则表达式
 * 格式：[@显示名称](uri)
 */
private val CONTEXT_LINK_REGEX = Regex("""\[@([^\]]+)\]\(([^)]+)\)""")

/**
 * 解析包含 Markdown 格式上下文引用的文本为 AnnotatedString
 * 
 * @param markdown 包含 [@显示名称](uri) 格式的 Markdown 文本
 * @param linkColor 链接颜色，默认使用 Jewel 主题的 info 颜色
 * @param linkBackgroundAlpha 链接背景透明度
 * @return 带有注解和样式的 AnnotatedString
 */
fun parseMarkdownToAnnotatedString(
    markdown: String,
    linkColor: Color? = null,
    linkBackgroundAlpha: Float = 0.1f
): AnnotatedString {
    return buildAnnotatedString {
        var lastIndex = 0
        
        CONTEXT_LINK_REGEX.findAll(markdown).forEach { matchResult ->
            // 添加匹配前的普通文本
            if (matchResult.range.first > lastIndex) {
                append(markdown.substring(lastIndex, matchResult.range.first))
            }
            
            val displayName = matchResult.groupValues[1]
            val uriString = matchResult.groupValues[2]
            
            // 解析 URI 以确定类型
            val contextUri = parseContextUri(uriString)
            val (icon, color) = getContextStyle(contextUri, linkColor)
            
            // 添加带注解和样式的引用
            pushStringAnnotation(
                tag = CONTEXT_ANNOTATION_TAG,
                annotation = uriString
            )
            
            withStyle(
                SpanStyle(
                    color = color,
                    background = color.copy(alpha = linkBackgroundAlpha),
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append("$icon $displayName")
            }
            
            pop() // 弹出注解
            
            lastIndex = matchResult.range.last + 1
        }
        
        // 添加剩余的文本
        if (lastIndex < markdown.length) {
            append(markdown.substring(lastIndex))
        }
    }
}

/**
 * 根据上下文类型获取图标和颜色
 */
private fun getContextStyle(
    contextUri: ContextUri?,
    defaultColor: Color?
): Pair<String, Color> {
    val defaultLinkColor = defaultColor ?: Color(0xFF0969DA) // GitHub 蓝色
    
    return when (contextUri) {
        is ContextUri.FileUri -> "📄" to Color(0xFF0969DA) // 蓝色
        is ContextUri.WebUri -> "🌐" to Color(0xFF1A73E8) // Google 蓝色
        is ContextUri.FolderUri -> "📁" to Color(0xFFE37400) // 橙色
        is ContextUri.SymbolUri -> "🔤" to Color(0xFF9333EA) // 紫色
        is ContextUri.ImageUri -> "🖼️" to Color(0xFF10B981) // 绿色
        is ContextUri.TerminalUri -> "💻" to Color(0xFF6B7280) // 灰色
        is ContextUri.ProblemsUri -> "⚠️" to Color(0xFFEF4444) // 红色
        is ContextUri.GitUri -> "🔀" to Color(0xFFF97316) // Git 橙色
        is ContextUri.SelectionUri -> "✂️" to Color(0xFF8B5CF6) // 紫色
        is ContextUri.WorkspaceUri -> "🏢" to Color(0xFF3B82F6) // 蓝色
        null -> "@" to defaultLinkColor // 未知类型使用默认
    }
}

/**
 * 从 AnnotatedString 中提取指定位置的上下文 URI
 * 
 * @param annotatedString 带注解的字符串
 * @param offset 点击位置
 * @return 如果该位置有上下文注解，返回 URI，否则返回 null
 */
fun getContextUriAtOffset(
    annotatedString: AnnotatedString,
    offset: Int
): String? {
    return annotatedString
        .getStringAnnotations(
            tag = CONTEXT_ANNOTATION_TAG,
            start = offset,
            end = offset
        )
        .firstOrNull()
        ?.item
}

/**
 * 将普通文本转换为 Markdown 格式的上下文引用
 * 
 * @param displayName 显示名称
 * @param uri 上下文 URI
 * @return Markdown 格式的引用字符串
 */
fun createMarkdownContextLink(
    displayName: String,
    uri: String
): String {
    return "[@$displayName]($uri)"
}

/**
 * 验证 URI 是否为有效的上下文引用
 */
fun isValidContextUri(uri: String): Boolean {
    return when {
        uri.startsWith("file://") -> true
        uri.startsWith("https://") -> true
        uri.startsWith("http://") -> true
        uri.startsWith("claude-context://") -> true
        else -> false
    }
}