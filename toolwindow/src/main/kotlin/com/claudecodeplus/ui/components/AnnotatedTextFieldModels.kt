/*
 * AnnotatedTextFieldModels.kt
 * 
 * 带注解的文本输入框数据模型
 * 支持文件引用注解和超链接样式显示
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.ui.text.TextRange
import com.claudecodeplus.ui.services.IndexedFileInfo

/**
 * 文件引用注解数据
 * 
 * 描述文本中的一个文件引用，包含位置信息和文件数据
 */
data class FileReferenceAnnotation(
    /** 注解在文本中的起始位置 */
    val startIndex: Int,
    /** 注解在文本中的结束位置 */
    val endIndex: Int,
    /** 引用的文件信息 */
    val file: IndexedFileInfo,
    /** 在UI中显示的文本 */
    val displayText: String
) {
    /** 注解的文本长度 */
    val length: Int get() = endIndex - startIndex
    
    /** 检查指定位置是否在注解范围内 */
    fun contains(position: Int): Boolean = position in startIndex until endIndex
    
    /** 检查指定范围是否与注解重叠 */
    fun overlaps(start: Int, end: Int): Boolean {
        return !(end <= startIndex || start >= endIndex)
    }
}

/**
 * 增强的文本字段值
 * 
 * 包含文本内容、光标选择和文件引用注解信息
 */
data class AnnotatedTextFieldValue(
    /** 文本内容 */
    val text: String,
    /** 光标选择范围 */
    val selection: TextRange,
    /** 文件引用注解列表 */
    val annotations: List<FileReferenceAnnotation> = emptyList()
) {
    /** 获取指定位置的注解 */
    fun getAnnotationAt(position: Int): FileReferenceAnnotation? {
        return annotations.find { it.contains(position) }
    }
    
    /** 获取与指定范围重叠的所有注解 */
    fun getAnnotationsInRange(start: Int, end: Int): List<FileReferenceAnnotation> {
        return annotations.filter { it.overlaps(start, end) }
    }
    
    /** 检查指定位置是否在任何注解内 */
    fun isInAnnotation(position: Int): Boolean {
        return getAnnotationAt(position) != null
    }
    
    /** 获取排序后的注解列表（按起始位置排序） */
    val sortedAnnotations: List<FileReferenceAnnotation>
        get() = annotations.sortedBy { it.startIndex }
}

/**
 * 注解更新结果
 */
sealed class AnnotationUpdateResult {
    /** 成功更新 */
    data class Success(val updatedValue: AnnotatedTextFieldValue) : AnnotationUpdateResult()
    
    /** 更新失败，注解被破坏 */
    data class AnnotationDestroyed(
        val updatedValue: AnnotatedTextFieldValue,
        val destroyedAnnotations: List<FileReferenceAnnotation>
    ) : AnnotationUpdateResult()
}

/**
 * 文本变化信息
 */
data class TextChange(
    /** 变化的起始位置 */
    val start: Int,
    /** 变化的结束位置（原文本中的位置） */
    val end: Int,
    /** 插入的新文本 */
    val newText: String,
    /** 文本长度的变化量 */
    val delta: Int = newText.length - (end - start)
)

/**
 * 注解主题样式配置
 */
data class AnnotationTheme(
    /** 正常状态的背景色透明度 */
    val normalBackgroundAlpha: Float = 0.1f,
    /** 悬停状态的背景色透明度 */
    val hoverBackgroundAlpha: Float = 0.2f,
    /** 选中状态的背景色透明度 */
    val selectedBackgroundAlpha: Float = 0.3f,
    /** 是否显示边框 */
    val showBorder: Boolean = true,
    /** 边框圆角半径（dp） */
    val cornerRadius: Float = 4f,
    /** 内边距（dp） */
    val padding: Float = 2f
)