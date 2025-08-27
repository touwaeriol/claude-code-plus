/*
 * AnnotationKeyboardHandler.kt
 * 
 * 处理注解文本输入框的键盘事件
 * 支持注解区域的特殊交互逻辑
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange

/**
 * 注解键盘事件处理器
 */
object AnnotationKeyboardHandler {
    
    /**
     * 处理键盘事件
     * 
     * @param keyEvent 键盘事件
     * @param value 当前文本字段值
     * @param onValueChange 值变化回调
     * @param onAnnotationClick 注解点击回调
     * @return 是否处理了该事件
     */
    fun handleKeyEvent(
        keyEvent: KeyEvent,
        value: AnnotatedTextFieldValue,
        onValueChange: (AnnotatedTextFieldValue) -> Unit,
        onAnnotationClick: ((FileReferenceAnnotation) -> Unit)? = null
    ): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        
        val cursorPos = value.selection.start
        val currentAnnotation = value.getAnnotationAt(cursorPos)
        
        return when (keyEvent.key) {
            Key.Backspace -> {
                handleBackspaceKey(value, onValueChange, cursorPos, currentAnnotation)
            }
            
            Key.Delete -> {
                handleDeleteKey(value, onValueChange, cursorPos, currentAnnotation)
            }
            
            Key.DirectionLeft -> {
                handleLeftArrowKey(value, onValueChange, cursorPos, currentAnnotation, keyEvent)
            }
            
            Key.DirectionRight -> {
                handleRightArrowKey(value, onValueChange, cursorPos, currentAnnotation, keyEvent)
            }
            
            Key.Enter -> {
                if (currentAnnotation != null && onAnnotationClick != null) {
                    // 在注解内按回车，触发注解点击事件
                    onAnnotationClick(currentAnnotation)
                    true
                } else {
                    false // 让普通的回车处理逻辑处理
                }
            }
            
            Key.Home -> {
                handleHomeKey(value, onValueChange, keyEvent)
            }
            
            Key.MoveEnd -> {
                handleEndKey(value, onValueChange, keyEvent)
            }
            
            else -> false
        }
    }
    
    /**
     * 处理 Backspace 键
     */
    private fun handleBackspaceKey(
        value: AnnotatedTextFieldValue,
        onValueChange: (AnnotatedTextFieldValue) -> Unit,
        cursorPos: Int,
        currentAnnotation: FileReferenceAnnotation?
    ): Boolean {
        return when {
            // 光标在注解内部任何位置，删除整个注解
            currentAnnotation != null -> {
                deleteAnnotation(value, onValueChange, currentAnnotation)
                true
            }
            
            // 光标在注解边界后，检查前一个字符是否是注解的结束
            cursorPos > 0 -> {
                val prevAnnotation = value.getAnnotationAt(cursorPos - 1)
                if (prevAnnotation != null) {
                    // 前一个字符在注解内，删除整个注解
                    deleteAnnotation(value, onValueChange, prevAnnotation)
                    true
                } else {
                    false // 让普通删除逻辑处理
                }
            }
            
            else -> false
        }
    }
    
    /**
     * 处理 Delete 键
     */
    private fun handleDeleteKey(
        value: AnnotatedTextFieldValue,
        onValueChange: (AnnotatedTextFieldValue) -> Unit,
        cursorPos: Int,
        currentAnnotation: FileReferenceAnnotation?
    ): Boolean {
        return when {
            // 光标在注解内部任何位置，删除整个注解
            currentAnnotation != null -> {
                deleteAnnotation(value, onValueChange, currentAnnotation)
                true
            }
            
            // 光标在注解边界前，检查下一个字符是否是注解的开始
            cursorPos < value.text.length -> {
                val nextAnnotation = value.getAnnotationAt(cursorPos)
                if (nextAnnotation != null) {
                    // 下一个字符在注解内，删除整个注解
                    deleteAnnotation(value, onValueChange, nextAnnotation)
                    true
                } else {
                    false // 让普通删除逻辑处理
                }
            }
            
            else -> false
        }
    }
    
    /**
     * 处理左方向键
     */
    private fun handleLeftArrowKey(
        value: AnnotatedTextFieldValue,
        onValueChange: (AnnotatedTextFieldValue) -> Unit,
        cursorPos: Int,
        currentAnnotation: FileReferenceAnnotation?,
        keyEvent: KeyEvent
    ): Boolean {
        return when {
            // 在注解内部，跳到注解开始
            currentAnnotation != null && cursorPos > currentAnnotation.startIndex -> {
                val newPos = if (keyEvent.isShiftPressed) {
                    // Shift+左键：扩展选择到注解开始
                    TextRange(currentAnnotation.startIndex, value.selection.end)
                } else {
                    // 普通左键：移动光标到注解开始
                    TextRange(currentAnnotation.startIndex)
                }
                onValueChange(value.copy(selection = newPos))
                true
            }
            
            // 在注解开始位置，跳过整个注解到前一个位置
            currentAnnotation != null && cursorPos == currentAnnotation.startIndex -> {
                val newPos = if (keyEvent.isShiftPressed) {
                    TextRange(maxOf(0, currentAnnotation.startIndex - 1), value.selection.end)
                } else {
                    TextRange(maxOf(0, currentAnnotation.startIndex - 1))
                }
                onValueChange(value.copy(selection = newPos))
                true
            }
            
            else -> false
        }
    }
    
    /**
     * 处理右方向键
     */
    private fun handleRightArrowKey(
        value: AnnotatedTextFieldValue,
        onValueChange: (AnnotatedTextFieldValue) -> Unit,
        cursorPos: Int,
        currentAnnotation: FileReferenceAnnotation?,
        keyEvent: KeyEvent
    ): Boolean {
        return when {
            // 在注解内部，跳到注解结束
            currentAnnotation != null && cursorPos < currentAnnotation.endIndex -> {
                val newPos = if (keyEvent.isShiftPressed) {
                    // Shift+右键：扩展选择到注解结束
                    TextRange(value.selection.start, currentAnnotation.endIndex)
                } else {
                    // 普通右键：移动光标到注解结束
                    TextRange(currentAnnotation.endIndex)
                }
                onValueChange(value.copy(selection = newPos))
                true
            }
            
            // 在注解结束位置，跳过到下一个位置
            currentAnnotation != null && cursorPos == currentAnnotation.endIndex -> {
                val newPos = if (keyEvent.isShiftPressed) {
                    TextRange(value.selection.start, minOf(value.text.length, currentAnnotation.endIndex + 1))
                } else {
                    TextRange(minOf(value.text.length, currentAnnotation.endIndex + 1))
                }
                onValueChange(value.copy(selection = newPos))
                true
            }
            
            else -> false
        }
    }
    
    /**
     * 处理 Home 键
     */
    private fun handleHomeKey(
        value: AnnotatedTextFieldValue,
        onValueChange: (AnnotatedTextFieldValue) -> Unit,
        keyEvent: KeyEvent
    ): Boolean {
        // 在注解模式下，Home 键行为保持标准
        return false
    }
    
    /**
     * 处理 End 键
     */
    private fun handleEndKey(
        value: AnnotatedTextFieldValue,
        onValueChange: (AnnotatedTextFieldValue) -> Unit,
        keyEvent: KeyEvent
    ): Boolean {
        // 在注解模式下，End 键行为保持标准
        return false
    }
    
    /**
     * 删除指定注解
     */
    private fun deleteAnnotation(
        value: AnnotatedTextFieldValue,
        onValueChange: (AnnotatedTextFieldValue) -> Unit,
        annotation: FileReferenceAnnotation
    ) {
        // 删除注解对应的文本
        val newText = value.text.removeRange(annotation.startIndex, annotation.endIndex)
        
        // 更新其他注解的位置
        val delta = annotation.startIndex - annotation.endIndex // 负数，表示删除
        val updatedAnnotations = value.annotations.mapNotNull { ann ->
            when {
                ann == annotation -> null // 删除当前注解
                ann.startIndex >= annotation.endIndex -> {
                    // 在删除注解之后的注解，需要前移
                    ann.copy(
                        startIndex = ann.startIndex + delta,
                        endIndex = ann.endIndex + delta
                    )
                }
                ann.endIndex <= annotation.startIndex -> {
                    // 在删除注解之前的注解，不受影响
                    ann
                }
                else -> {
                    // 与删除注解重叠的注解，也删除（这种情况理论上不应该出现）
                    null
                }
            }
        }
        
        // 更新光标位置
        val newCursorPos = annotation.startIndex
        
        onValueChange(
            AnnotatedTextFieldValue(
                text = newText,
                selection = TextRange(newCursorPos),
                annotations = updatedAnnotations
            )
        )
    }
    
    /**
     * 检查是否可以在指定位置插入文本
     * 
     * @param value 当前值
     * @param position 插入位置
     * @return 是否可以插入
     */
    fun canInsertTextAt(value: AnnotatedTextFieldValue, position: Int): Boolean {
        // 不能在注解内部插入文本
        return !value.isInAnnotation(position)
    }
    
    /**
     * 获取建议的光标位置
     * 
     * 当用户尝试在注解内部点击时，返回合适的光标位置
     * 
     * @param value 当前值
     * @param requestedPosition 请求的位置
     * @return 建议的光标位置
     */
    fun getSuggestedCursorPosition(
        value: AnnotatedTextFieldValue,
        requestedPosition: Int
    ): Int {
        val annotation = value.getAnnotationAt(requestedPosition)
        return if (annotation != null) {
            // 在注解内部点击，移动到最近的边界
            val distanceToStart = requestedPosition - annotation.startIndex
            val distanceToEnd = annotation.endIndex - requestedPosition
            
            if (distanceToStart <= distanceToEnd) {
                annotation.startIndex
            } else {
                annotation.endIndex
            }
        } else {
            requestedPosition
        }
    }
}

