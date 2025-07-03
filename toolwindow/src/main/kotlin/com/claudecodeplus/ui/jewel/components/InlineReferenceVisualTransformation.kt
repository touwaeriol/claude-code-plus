package com.claudecodeplus.ui.jewel.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * 内联引用的视觉转换器
 * 将 @file://path/to/file.kt 格式转换为蓝色超链接显示的 @file.kt
 */
class InlineReferenceVisualTransformation : VisualTransformation {
    
    override fun filter(text: AnnotatedString): TransformedText {
        // 解析所有的内联引用
        val references = parseInlineReferences(text.text)
        
        if (references.isEmpty()) {
            // 没有引用，直接返回原文本
            return TransformedText(text, OffsetMapping.Identity)
        }
        
        // 构建转换后的文本
        val transformedText = buildAnnotatedString {
            var lastIndex = 0
            
            references.forEach { ref ->
                // 添加引用前的普通文本
                if (ref.startIndex > lastIndex) {
                    append(text.substring(lastIndex, ref.startIndex))
                }
                
                // 添加转换后的引用（带样式）
                withStyle(
                    SpanStyle(
                        color = Color(0xFF007ACC), // 蓝色
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("@${ref.fileName}")
                }
                
                lastIndex = ref.endIndex
            }
            
            // 添加最后一个引用后的文本
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
        
        // 创建偏移映射
        val offsetMapping = createOffsetMapping(text.text, transformedText.text, references)
        
        return TransformedText(transformedText, offsetMapping)
    }
    
    /**
     * 解析文本中的内联引用
     */
    private fun parseInlineReferences(text: String): List<ParsedReference> {
        val references = mutableListOf<ParsedReference>()
        
        // 使用 InlineReferenceDetector 获取所有支持的引用类型
        val schemes = InlineReferenceScheme.values()
        
        schemes.forEach { scheme ->
            val pattern = scheme.createFullPattern()
            val matches = pattern.findAll(text)
            
            matches.forEach { match ->
                val fullText = match.value
                val path = match.groupValues.getOrNull(1) ?: ""
                val fileName = extractFileName(path, scheme)
                
                references.add(
                    ParsedReference(
                        fullText = fullText,
                        scheme = scheme,
                        path = path,
                        fileName = fileName,
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1
                    )
                )
            }
        }
        
        // 按开始位置排序
        return references.sortedBy { it.startIndex }
    }
    
    /**
     * 提取文件名或显示名称
     */
    private fun extractFileName(path: String, scheme: InlineReferenceScheme): String {
        return when (scheme) {
            InlineReferenceScheme.FILE -> path.substringAfterLast('/')
            InlineReferenceScheme.HTTP, 
            InlineReferenceScheme.HTTPS -> {
                // 提取域名
                path.substringAfter("://")
                    .substringBefore('/')
                    .substringAfterLast('.')
                    .let { if (it.isEmpty()) path else it }
            }
            InlineReferenceScheme.GIT -> {
                // 提取仓库名
                path.substringAfterLast('/')
                    .substringBefore(".git")
            }
            InlineReferenceScheme.SYMBOL -> {
                // 提取符号名
                path.substringAfterLast('.')
            }
            else -> scheme.displayName
        }
    }
    
    /**
     * 创建偏移映射
     */
    private fun createOffsetMapping(
        originalText: String, 
        transformedText: String,
        references: List<ParsedReference>
    ): OffsetMapping {
        return object : OffsetMapping {
            /**
             * 原始偏移 -> 转换后偏移
             */
            override fun originalToTransformed(offset: Int): Int {
                var transformedOffset = offset
                var adjustment = 0
                
                for (ref in references) {
                    when {
                        // 偏移在引用之前
                        offset <= ref.startIndex -> {
                            return transformedOffset + adjustment
                        }
                        // 偏移在引用内部
                        offset < ref.endIndex -> {
                            // 映射到转换后的引用开始位置
                            val refStart = ref.startIndex + adjustment
                            val progressInRef = offset - ref.startIndex
                            val transformedRefLength = "@${ref.fileName}".length
                            
                            // 按比例映射到转换后的位置
                            val ratio = progressInRef.toFloat() / ref.fullText.length
                            return refStart + (ratio * transformedRefLength).toInt()
                        }
                        // 偏移在引用之后
                        else -> {
                            val originalLength = ref.fullText.length
                            val transformedLength = "@${ref.fileName}".length
                            adjustment += transformedLength - originalLength
                        }
                    }
                }
                
                return transformedOffset + adjustment
            }
            
            /**
             * 转换后偏移 -> 原始偏移
             */
            override fun transformedToOriginal(offset: Int): Int {
                var originalOffset = 0
                var currentTransformedPos = 0
                var refIndex = 0
                
                while (currentTransformedPos < offset && refIndex < references.size) {
                    val ref = references[refIndex]
                    val transformedRefLength = "@${ref.fileName}".length
                    val transformedRefStart = originalToTransformed(ref.startIndex)
                    val transformedRefEnd = transformedRefStart + transformedRefLength
                    
                    when {
                        // 还没到引用位置
                        currentTransformedPos < transformedRefStart -> {
                            val distance = minOf(offset - currentTransformedPos, transformedRefStart - currentTransformedPos)
                            originalOffset += distance
                            currentTransformedPos += distance
                        }
                        // 在引用内部
                        currentTransformedPos < transformedRefEnd -> {
                            if (offset <= transformedRefEnd) {
                                // 偏移在当前引用内，映射到原始引用的开始
                                return ref.startIndex
                            } else {
                                // 跳过整个引用
                                originalOffset = ref.endIndex
                                currentTransformedPos = transformedRefEnd
                                refIndex++
                            }
                        }
                        else -> refIndex++
                    }
                }
                
                // 处理最后一段文本
                if (currentTransformedPos < offset) {
                    originalOffset += offset - currentTransformedPos
                }
                
                return originalOffset
            }
        }
    }
}

/**
 * 内联引用数据类（内部使用）
 */
private data class ParsedReference(
    val fullText: String,        // 完整的引用文本，如 @file://src/main.kt
    val scheme: InlineReferenceScheme, // 引用类型
    val path: String,            // 路径部分，如 src/main.kt
    val fileName: String,        // 显示的文件名，如 main.kt
    val startIndex: Int,         // 在原文中的开始位置
    val endIndex: Int           // 在原文中的结束位置
)