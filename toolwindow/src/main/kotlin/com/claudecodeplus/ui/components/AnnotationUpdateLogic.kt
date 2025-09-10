/*
 * AnnotationUpdateLogic.kt
 * 
 * 处理注解在文本变化时的位置更新逻辑
 * 确保注解完整性和正确的位置调整
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 文本变化分析器
 */
object TextChangeAnalyzer {
    
    /**
     * 分析文本变化
     * 
     * @param oldText 旧文本
     * @param newText 新文本
     * @param oldSelection 旧光标选择
     * @param newSelection 新光标选择
     * @return 文本变化信息
     */
    fun analyzeTextChange(
        oldText: String,
        newText: String,
        oldSelection: TextRange,
        newSelection: TextRange
    ): TextChange {
        // 找到变化的起始位置
        val changeStart = findChangeStart(oldText, newText)
        
        // 找到变化的结束位置
        val changeEnd = findChangeEnd(oldText, newText, changeStart)
        
        // 计算插入的新文本
        val insertedText = if (changeStart < newText.length && changeEnd <= oldText.length) {
            val insertEnd = changeStart + (newText.length - oldText.length + changeEnd - changeStart)
            newText.substring(changeStart, insertEnd.coerceAtMost(newText.length))
        } else {
            ""
        }
        
        return TextChange(
            start = changeStart,
            end = changeEnd,
            newText = insertedText
        )
    }
    
    /**
     * 找到文本变化的起始位置
     */
    private fun findChangeStart(oldText: String, newText: String): Int {
        val minLength = minOf(oldText.length, newText.length)
        for (i in 0 until minLength) {
            if (oldText[i] != newText[i]) {
                return i
            }
        }
        return minLength
    }
    
    /**
     * 找到文本变化的结束位置
     */
    private fun findChangeEnd(oldText: String, newText: String, changeStart: Int): Int {
        val oldEnd = oldText.length
        val newEnd = newText.length
        val maxBacktrack = minOf(oldEnd - changeStart, newEnd - changeStart)
        
        for (i in 0 until maxBacktrack) {
            if (oldText[oldEnd - 1 - i] != newText[newEnd - 1 - i]) {
                return oldEnd - i
            }
        }
        return oldEnd - maxBacktrack
    }
}

/**
 * 注解位置更新器
 */
object AnnotationUpdater {
    
    /**
     * 更新注解在文本变化后的位置
     * 
     * @param oldValue 旧的文本字段值
     * @param newTextFieldValue 新的文本字段值
     * @return 更新结果
     */
    fun updateAnnotationsOnTextChange(
        oldValue: AnnotatedTextFieldValue,
        newTextFieldValue: TextFieldValue
    ): AnnotationUpdateResult {
        val textChange = TextChangeAnalyzer.analyzeTextChange(
            oldText = oldValue.text,
            newText = newTextFieldValue.text,
            oldSelection = oldValue.selection,
            newSelection = newTextFieldValue.selection
        )
        
        val (updatedAnnotations, destroyedAnnotations) = updateAnnotationPositions(
            annotations = oldValue.annotations,
            textChange = textChange
        )
        
        val newValue = AnnotatedTextFieldValue(
            text = newTextFieldValue.text,
            selection = newTextFieldValue.selection,
            annotations = updatedAnnotations
        )
        
        return if (destroyedAnnotations.isNotEmpty()) {
            AnnotationUpdateResult.AnnotationDestroyed(newValue, destroyedAnnotations)
        } else {
            AnnotationUpdateResult.Success(newValue)
        }
    }
    
    /**
     * 更新注解位置
     */
    private fun updateAnnotationPositions(
        annotations: List<FileReferenceAnnotation>,
        textChange: TextChange
    ): Pair<List<FileReferenceAnnotation>, List<FileReferenceAnnotation>> {
        val updatedAnnotations = mutableListOf<FileReferenceAnnotation>()
        val destroyedAnnotations = mutableListOf<FileReferenceAnnotation>()
        
        annotations.forEach { annotation ->
            val updateResult = updateSingleAnnotation(annotation, textChange)
            when (updateResult) {
                is AnnotationUpdateResult.Success -> {
                    updatedAnnotations.addAll(updateResult.updatedValue.annotations)
                }
                is AnnotationUpdateResult.AnnotationDestroyed -> {
                    destroyedAnnotations.addAll(updateResult.destroyedAnnotations)
                }
            }
        }
        
        return Pair(updatedAnnotations, destroyedAnnotations)
    }
    
    /**
     * 更新单个注解的位置
     */
    private fun updateSingleAnnotation(
        annotation: FileReferenceAnnotation,
        textChange: TextChange
    ): AnnotationUpdateResult {
        val (changeStart, changeEnd, delta) = Triple(textChange.start, textChange.end, textChange.delta)
        val (annotationStart, annotationEnd) = Pair(annotation.startIndex, annotation.endIndex)
        
        when {
            // 注解在变化范围之前，不受影响
            annotationEnd <= changeStart -> {
                return AnnotationUpdateResult.Success(
                    AnnotatedTextFieldValue(
                        text = "",
                        selection = TextRange.Zero,
                        annotations = listOf(annotation)
                    )
                )
            }
            
            // 注解在变化范围之后，移动位置
            annotationStart >= changeEnd -> {
                val updatedAnnotation = annotation.copy(
                    startIndex = annotationStart + delta,
                    endIndex = annotationEnd + delta
                )
                return AnnotationUpdateResult.Success(
                    AnnotatedTextFieldValue(
                        text = "",
                        selection = TextRange.Zero,
                        annotations = listOf(updatedAnnotation)
                    )
                )
            }
            
            // 注解与变化范围重叠
            else -> {
                // 检查注解是否完整
                if (isAnnotationIntact(annotation, textChange)) {
                    // 如果注解内容完整，尝试调整位置
                    val updatedAnnotation = adjustAnnotationPosition(annotation, textChange)
                    return if (updatedAnnotation != null) {
                        AnnotationUpdateResult.Success(
                            AnnotatedTextFieldValue(
                                text = "",
                                selection = TextRange.Zero,
                                annotations = listOf(updatedAnnotation)
                            )
                        )
                    } else {
                        AnnotationUpdateResult.AnnotationDestroyed(
                            AnnotatedTextFieldValue("", TextRange.Zero, emptyList()),
                            listOf(annotation)
                        )
                    }
                } else {
                    // 注解被破坏，标记为删除
                    return AnnotationUpdateResult.AnnotationDestroyed(
                        AnnotatedTextFieldValue("", TextRange.Zero, emptyList()),
                        listOf(annotation)
                    )
                }
            }
        }
    }
    
    /**
     * 检查注解是否完整
     */
    private fun isAnnotationIntact(
        annotation: FileReferenceAnnotation,
        textChange: TextChange
    ): Boolean {
        // 如果变化范围完全在注解内部，且没有删除注解的关键部分，则认为注解完整
        if (textChange.start >= annotation.startIndex && textChange.end <= annotation.endIndex) {
            // 内部变化，检查是否破坏了显示文本
            return true // 简化实现，后续可以加强检查逻辑
        }
        
        // 如果变化只是在注解边界添加内容，也认为注解完整
        if (textChange.start == annotation.endIndex && textChange.end == annotation.endIndex) {
            return true // 在注解后添加内容
        }
        
        if (textChange.start == annotation.startIndex && textChange.end == annotation.startIndex) {
            return true // 在注解前添加内容
        }
        
        return false
    }
    
    /**
     * 调整注解位置
     */
    private fun adjustAnnotationPosition(
        annotation: FileReferenceAnnotation,
        textChange: TextChange
    ): FileReferenceAnnotation? {
        // 简化实现：如果变化在注解内部，保持注解范围，调整结束位置
        if (textChange.start >= annotation.startIndex && textChange.end <= annotation.endIndex) {
            return annotation.copy(
                endIndex = annotation.endIndex + textChange.delta
            )
        }
        
        return null
    }
}

/**
 * 注解验证器
 */
object AnnotationValidator {
    
    /**
     * 验证注解是否有效
     * 
     * @param text 文本内容
     * @param annotations 注解列表
     * @return 有效的注解列表
     */
    fun validateAnnotations(
        text: String,
        annotations: List<FileReferenceAnnotation>
    ): List<FileReferenceAnnotation> {
        return annotations.filter { annotation ->
            // 检查注解范围是否在文本范围内
            annotation.startIndex >= 0 &&
            annotation.endIndex <= text.length &&
            annotation.startIndex < annotation.endIndex &&
            // 检查注解内容是否匹配
            text.substring(annotation.startIndex, annotation.endIndex) == annotation.displayText
        }
    }
    
    /**
     * 修复重叠的注解
     * 
     * @param annotations 注解列表
     * @return 修复后的注解列表
     */
    fun fixOverlappingAnnotations(
        annotations: List<FileReferenceAnnotation>
    ): List<FileReferenceAnnotation> {
        val sortedAnnotations = annotations.sortedBy { it.startIndex }
        val result = mutableListOf<FileReferenceAnnotation>()
        
        sortedAnnotations.forEach { annotation ->
            val lastAnnotation = result.lastOrNull()
            if (lastAnnotation == null || lastAnnotation.endIndex <= annotation.startIndex) {
                // 没有重叠，直接添加
                result.add(annotation)
            } else {
                // 有重叠，保留较长的注解或较早的注解
                if (annotation.length > lastAnnotation.length) {
                    result[result.size - 1] = annotation
                }
                // 否则忽略当前注解
            }
        }
        
        return result
    }
}