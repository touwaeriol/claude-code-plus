/*
 * InlineReferenceRenderer.kt
 * 
 * 内联引用渲染器 - 处理@file://和@https://格式的超链接显示
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * 内联引用信息
 */
data class InlineReference(
    val displayText: String,    // 显示的文本（如文件名）
    val fullPath: String,       // 完整路径
    val type: InlineReferenceType,
    val startIndex: Int,        // 在原始文本中的起始位置
    val endIndex: Int          // 在原始文本中的结束位置
)

enum class InlineReferenceType {
    FILE,
    WEB
}

/**
 * 解析文本中的内联引用，仅用于UI显示
 * 使用新的可扩展检测系统
 */
fun parseInlineReferences(text: String): Pair<String, List<InlineReference>> {
    val extractedRefs = InlineReferenceDetector.extractReferences(text)
    val references = mutableListOf<InlineReference>()
    var processedText = text
    var offset = 0
    
    // 从后往前处理，避免索引偏移问题
    for (extractedRef in extractedRefs.reversed()) {
        val displayText = extractedRef.getDisplayText()
        
        val type = when (extractedRef.scheme) {
            InlineReferenceScheme.FILE -> InlineReferenceType.FILE
            InlineReferenceScheme.HTTP, InlineReferenceScheme.HTTPS -> InlineReferenceType.WEB
            else -> InlineReferenceType.FILE // 默认类型
        }
        
        val reference = InlineReference(
            displayText = displayText,
            fullPath = extractedRef.fullText, // 保存完整的 @scheme://path 格式
            type = type,
            startIndex = extractedRef.startIndex - offset,
            endIndex = extractedRef.startIndex - offset + displayText.length
        )
        
        references.add(0, reference) // 添加到开头以保持顺序
        
        // 替换原始文本中的引用为显示文本（仅用于显示）
        processedText = processedText.replaceRange(
            extractedRef.startIndex - offset,
            extractedRef.endIndex - offset,
            displayText
        )
        
        // 更新偏移量
        offset += extractedRef.fullText.length - displayText.length
    }
    
    return processedText to references
}

/**
 * 创建带有内联引用的富文本
 */
fun createAnnotatedStringWithReferences(
    text: String,
    onReferenceClick: (InlineReference) -> Unit = {}
): AnnotatedString {
    val (displayText, references) = parseInlineReferences(text)
    
    return buildAnnotatedString {
        append(displayText)
        
        // 为每个引用添加样式和点击注解
        references.forEach { reference ->
            // 添加超链接样式
            addStyle(
                style = SpanStyle(
                    color = Color(0xFF007ACC), // 蓝色链接颜色
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline
                ),
                start = reference.startIndex,
                end = reference.endIndex
            )
            
            // 添加点击注解
            addStringAnnotation(
                tag = "REFERENCE",
                annotation = "${reference.type.name}:${reference.fullPath}",
                start = reference.startIndex,
                end = reference.endIndex
            )
        }
    }
}

/**
 * 可点击的内联引用文本组件
 */
@Composable
fun ClickableInlineText(
    text: String,
    onReferenceClick: (InlineReference) -> Unit = {},
    style: TextStyle = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp),
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val (displayText, references) = remember(text) { parseInlineReferences(text) }
    val annotatedText = remember(displayText, references) {
        createAnnotatedStringWithReferences(text, onReferenceClick)
    }
    
    ClickableText(
        text = annotatedText,
        style = style.copy(color = JewelTheme.globalColors.text.normal),
        onClick = { offset ->
            // 查找点击位置对应的引用
            references.forEach { reference ->
                if (offset >= reference.startIndex && offset < reference.endIndex) {
                    onReferenceClick(reference)
                }
            }
        },
        modifier = modifier
    )
}

/**
 * 生成内联引用文本
 */
fun generateInlineReference(context: com.claudecodeplus.ui.models.ContextReference): String {
    return when (context) {
        is com.claudecodeplus.ui.models.ContextReference.FileReference -> {
            "@file://${context.path}"
        }
        is com.claudecodeplus.ui.models.ContextReference.WebReference -> {
            "@${context.url}"
        }
        else -> "@${context.toString()}"
    }
}

/**
 * 发送给AI时不做任何转换，保持原始的@file://格式
 * Claude可以理解这种格式的文件引用
 */
fun expandInlineReferencesForAI(text: String, projectPath: String = ""): String {
    // 直接返回原始文本，不做任何转换
    // AI会收到类似 "@file://src/main.kt" 这样的格式
    return text
}

/**
 * 从Claude的回复中提取文件引用并转换为可点击链接
 */
fun parseClaudeResponseLinks(text: String): String {
    // 匹配Claude回复中的文件链接格式: [filename](file://path)
    val linkPattern = Regex("\\[([^\\]]+)\\]\\(file://([^)]+)\\)")
    
    return linkPattern.replace(text) { matchResult ->
        val fileName = matchResult.groupValues[1]
        val filePath = matchResult.groupValues[2]
        
        // 转换为我们的内联引用格式以便在UI中正确显示
        "@file://${filePath.substringAfterLast('/')}"
    }
}