package com.claudecodeplus.ui.jewel.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 处理内联引用的输入操作
 * 支持整体删除引用等功能
 */
object InlineReferenceInputProcessor {
    
    /**
     * 处理删除操作
     * @param value 当前的文本值
     * @param isBackspace 是否是退格键（true）还是Delete键（false）
     * @return 处理后的文本值，如果没有特殊处理则返回null
     */
    fun handleDelete(
        value: TextFieldValue,
        isBackspace: Boolean
    ): TextFieldValue? {
        val text = value.text
        val cursorPos = value.selection.start
        
        // 如果有选择范围，不做特殊处理
        if (value.selection.collapsed.not()) {
            return null
        }
        
        // 查找所有引用
        val references = findAllReferences(text)
        
        if (isBackspace && cursorPos > 0) {
            // 退格键：检查光标前是否是引用的结尾
            val refToDelete = references.find { ref ->
                cursorPos == ref.endIndex
            }
            
            if (refToDelete != null) {
                // 删除整个引用
                val newText = text.substring(0, refToDelete.startIndex) + 
                              text.substring(refToDelete.endIndex)
                return TextFieldValue(
                    text = newText,
                    selection = TextRange(refToDelete.startIndex)
                )
            }
        } else if (!isBackspace && cursorPos < text.length) {
            // Delete键：检查光标后是否是引用的开始
            val refToDelete = references.find { ref ->
                cursorPos == ref.startIndex
            }
            
            if (refToDelete != null) {
                // 删除整个引用
                val newText = text.substring(0, refToDelete.startIndex) + 
                              text.substring(refToDelete.endIndex)
                return TextFieldValue(
                    text = newText,
                    selection = TextRange(refToDelete.startIndex)
                )
            }
        }
        
        return null
    }
    
    /**
     * 处理光标移动
     * @param value 当前的文本值
     * @param direction 移动方向（左或右）
     * @return 处理后的文本值，如果没有特殊处理则返回null
     */
    fun handleCursorMovement(
        value: TextFieldValue,
        direction: CursorDirection
    ): TextFieldValue? {
        val text = value.text
        val cursorPos = value.selection.start
        
        // 如果没有选择范围（光标是一个点），才处理引用跳跃
        if (!value.selection.collapsed) {
            return null // 有选择范围时不处理
        }
        
        // 查找所有引用
        val references = findAllReferences(text)
        if (references.isEmpty()) {
            return null // 没有引用时不处理
        }
        
        // 检查光标是否在引用内部
        val currentRef = references.find { ref ->
            cursorPos > ref.startIndex && cursorPos < ref.endIndex
        }
        
        if (currentRef != null) {
            // 光标在引用内部，跳到引用边界
            return when (direction) {
                CursorDirection.LEFT -> TextFieldValue(
                    text = value.text,
                    selection = TextRange(currentRef.startIndex)
                )
                CursorDirection.RIGHT -> TextFieldValue(
                    text = value.text,
                    selection = TextRange(currentRef.endIndex)
                )
            }
        }
        
        // 如果光标不在引用内部，让系统处理正常的光标移动
        return null
    }
    
    /**
     * 处理选择范围调整
     * 如果选择范围部分包含引用，扩展到整个引用
     */
    fun adjustSelection(
        value: TextFieldValue
    ): TextFieldValue? {
        val selection = value.selection
        if (selection.collapsed) {
            return null
        }
        
        val references = findAllReferences(value.text)
        var adjustedStart = selection.start
        var adjustedEnd = selection.end
        var adjusted = false
        
        references.forEach { ref ->
            // 开始位置在引用内
            if (adjustedStart > ref.startIndex && adjustedStart < ref.endIndex) {
                adjustedStart = ref.startIndex
                adjusted = true
            }
            
            // 结束位置在引用内
            if (adjustedEnd > ref.startIndex && adjustedEnd < ref.endIndex) {
                adjustedEnd = ref.endIndex
                adjusted = true
            }
        }
        
        return if (adjusted) {
            value.copy(selection = TextRange(adjustedStart, adjustedEnd))
        } else {
            null
        }
    }
    
    /**
     * 查找文本中的所有引用
     */
    private fun findAllReferences(text: String): List<Reference> {
        val references = mutableListOf<Reference>()
        
        // 使用 InlineReferenceDetector 获取所有支持的引用类型
        val schemes = InlineReferenceScheme.values()
        
        schemes.forEach { scheme ->
            val pattern = scheme.createFullPattern()
            val matches = pattern.findAll(text)
            
            matches.forEach { match ->
                references.add(
                    Reference(
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1
                    )
                )
            }
        }
        
        return references.sortedBy { it.startIndex }
    }
    
    /**
     * 光标移动方向
     */
    enum class CursorDirection {
        LEFT, RIGHT
    }
    
    /**
     * 引用位置信息
     */
    private data class Reference(
        val startIndex: Int,
        val endIndex: Int
    )
}