/*
 * AnnotatedStringBuilder.kt
 * 
 * 构建带文件引用注解的 AnnotatedString
 * 支持主题适配和交互样式
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * 文件引用注解标签
 */
object FileReferenceAnnotationTags {
    const val FILE_REFERENCE = "FileReference"
    const val FILE_PATH = "FilePath"
    const val FILE_ID = "FileId"
}

/**
 * 文件引用主题配置
 */
object FileReferenceTheme {
    
    /**
     * 正常状态样式
     */
    @Composable
    fun normalStyle(): SpanStyle {
        return SpanStyle(
            background = JewelTheme.globalColors.borders.focused.copy(alpha = 0.1f),
            color = JewelTheme.globalColors.text.info,
            fontWeight = FontWeight.Medium
        )
    }
    
    /**
     * 悬停状态样式
     */
    @Composable 
    fun hoverStyle(): SpanStyle {
        return SpanStyle(
            background = JewelTheme.globalColors.borders.focused.copy(alpha = 0.2f),
            color = JewelTheme.globalColors.text.info,
            fontWeight = FontWeight.Medium
        )
    }
    
    /**
     * 选中状态样式
     */
    @Composable
    fun selectedStyle(): SpanStyle {
        return SpanStyle(
            background = JewelTheme.globalColors.borders.focused.copy(alpha = 0.3f),
            color = JewelTheme.globalColors.text.info,
            fontWeight = FontWeight.Bold
        )
    }
    
    /**
     * 错误状态样式（文件不存在等）
     */
    @Composable
    fun errorStyle(): SpanStyle {
        return SpanStyle(
            background = JewelTheme.globalColors.borders.normal.copy(alpha = 0.1f),
            color = JewelTheme.globalColors.text.normal,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 构建文件引用 AnnotatedString
 * 
 * @param text 原始文本
 * @param annotations 文件引用注解列表
 * @param selectedAnnotation 当前选中的注解（可选）
 * @param hoveredAnnotation 当前悬停的注解（可选）
 * @return 带样式的 AnnotatedString
 */
@Composable
fun buildFileReferenceAnnotatedString(
    text: String,
    annotations: List<FileReferenceAnnotation>,
    selectedAnnotation: FileReferenceAnnotation? = null,
    hoveredAnnotation: FileReferenceAnnotation? = null
): AnnotatedString {
    return buildAnnotatedString {
        var lastIndex = 0
        
        // 按起始位置排序注解
        val sortedAnnotations = annotations.sortedBy { it.startIndex }
        
        sortedAnnotations.forEach { annotation ->
            // 添加注解前的普通文本
            if (annotation.startIndex > lastIndex) {
                append(text.substring(lastIndex, annotation.startIndex))
            }
            
            // 确定注解样式
            val style = when {
                annotation == selectedAnnotation -> FileReferenceTheme.selectedStyle()
                annotation == hoveredAnnotation -> FileReferenceTheme.hoverStyle()
                else -> FileReferenceTheme.normalStyle()
            }
            
            // 添加带样式的文件引用
            val annotationStart = length
            withStyle(style = style) {
                append(annotation.displayText)
            }
            val annotationEnd = length
            
            // 添加字符串注解用于点击检测和交互
            addStringAnnotation(
                tag = FileReferenceAnnotationTags.FILE_REFERENCE,
                annotation = "${annotation.file.absolutePath}|${annotation.startIndex}|${annotation.endIndex}",
                start = annotationStart,
                end = annotationEnd
            )
            
            // 添加文件路径注解
            addStringAnnotation(
                tag = FileReferenceAnnotationTags.FILE_PATH,
                annotation = annotation.file.relativePath,
                start = annotationStart,
                end = annotationEnd
            )
            
            // 添加文件ID注解（用于快速查找）
            addStringAnnotation(
                tag = FileReferenceAnnotationTags.FILE_ID,
                annotation = annotation.file.absolutePath.hashCode().toString(),
                start = annotationStart,
                end = annotationEnd
            )
            
            lastIndex = annotation.endIndex
        }
        
        // 添加最后剩余的普通文本
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

/**
 * 构建简单的文件引用 AnnotatedString（无特殊状态）
 */
@Composable
fun buildSimpleFileReferenceAnnotatedString(
    text: String,
    annotations: List<FileReferenceAnnotation>
): AnnotatedString {
    return buildFileReferenceAnnotatedString(
        text = text,
        annotations = annotations,
        selectedAnnotation = null,
        hoveredAnnotation = null
    )
}

/**
 * 解析字符串注解数据
 * 
 * @param annotationData 注解数据字符串，格式："absolutePath|startIndex|endIndex"
 * @return 解析后的数据或null
 */
fun parseFileReferenceAnnotation(annotationData: String): Triple<String, Int, Int>? {
    return try {
        val parts = annotationData.split("|")
        if (parts.size >= 3) {
            Triple(parts[0], parts[1].toInt(), parts[2].toInt())
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * 获取注解在 AnnotatedString 中的实际范围
 * 
 * @param annotatedString 注解字符串
 * @param originalAnnotation 原始注解
 * @return 在 AnnotatedString 中的实际范围，如果未找到返回 null
 */
fun getAnnotationRangeInAnnotatedString(
    annotatedString: AnnotatedString,
    originalAnnotation: FileReferenceAnnotation
): IntRange? {
    val annotations = annotatedString.getStringAnnotations(
        tag = FileReferenceAnnotationTags.FILE_REFERENCE,
        start = 0,
        end = annotatedString.length
    )
    
    return annotations.find { annotation ->
        val parsed = parseFileReferenceAnnotation(annotation.item)
        parsed?.let { (path, start, end) ->
            path == originalAnnotation.file.absolutePath &&
            start == originalAnnotation.startIndex &&
            end == originalAnnotation.endIndex
        } ?: false
    }?.let { it.start..it.end }
}

/**
 * 检查指定位置是否在文件引用注解内
 * 
 * @param annotatedString 注解字符串
 * @param offset 位置偏移
 * @return 如果在注解内，返回注解信息，否则返回 null
 */
fun getFileReferenceAt(
    annotatedString: AnnotatedString,
    offset: Int
): Pair<String, IntRange>? {
    val annotations = annotatedString.getStringAnnotations(
        tag = FileReferenceAnnotationTags.FILE_REFERENCE,
        start = offset,
        end = offset
    )
    
    return annotations.firstOrNull()?.let { annotation ->
        Pair(annotation.item, annotation.start..annotation.end)
    }
}