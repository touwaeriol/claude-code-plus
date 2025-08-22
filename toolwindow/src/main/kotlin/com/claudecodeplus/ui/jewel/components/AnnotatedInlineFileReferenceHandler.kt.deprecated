/*
 * AnnotatedInlineFileReferenceHandler.kt
 * 
 * 集成@符号文件选择与注解系统的处理组件
 * 负责将文件选择转换为文件引用注解
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.IndexedFileInfo

/**
 * 注解内联文件引用处理器
 * 
 * 处理@符号触发的文件选择，将选中的文件转换为注解
 * 
 * @param value 当前文本字段值
 * @param onValueChange 值变化回调
 * @param fileIndexService 文件索引服务
 * @param enabled 是否启用
 * @param textLayoutResult 文本布局结果，用于精确字符定位
 */
@Composable
fun AnnotatedInlineFileReferenceHandler(
    value: AnnotatedTextFieldValue,
    onValueChange: (AnnotatedTextFieldValue) -> Unit,
    fileIndexService: FileIndexService,
    enabled: Boolean = true,
    textLayoutResult: androidx.compose.ui.text.TextLayoutResult? = null
) {
    // 检测@符号查询
    val atQuery = remember(value.text, value.selection) {
        findAtSymbolQuery(value.text, value.selection.start)
    }
    
    // 使用搜索结果显示文件选择弹窗
    if (enabled && atQuery != null) {
        var searchResults by remember { mutableStateOf<List<IndexedFileInfo>>(emptyList()) }
        var selectedIndex by remember { mutableStateOf(0) }
        
        // 执行搜索
        LaunchedEffect(atQuery.query) {
            val results = if (atQuery.query.isEmpty()) {
                fileIndexService.getRecentFiles(10)
            } else {
                fileIndexService.searchFiles(atQuery.query, 10)
            }
            searchResults = results
            selectedIndex = 0
        }
        
        if (searchResults.isNotEmpty()) {
            val scrollState = rememberLazyListState()
            
            // 使用改进的SimpleFilePopup，传递计算好的offset
            SimpleFilePopup(
                results = searchResults,
                selectedIndex = selectedIndex,
                searchQuery = atQuery.query,
                scrollState = scrollState,
                popupOffset = remember(textLayoutResult, atQuery.position) {
                    // 使用工具类计算 @ 符号的精确位置
                    TextPositionUtils.safeCalculateCharacterPosition(
                        textLayoutResult = textLayoutResult,
                        characterPosition = atQuery.position,
                        textLength = value.text.length
                    )
                },
                onItemSelected = { file ->
                    insertFileAnnotation(
                        value = value,
                        onValueChange = onValueChange,
                        file = file,
                        atPosition = atQuery.position,
                        queryLength = atQuery.totalLength
                    )
                },
                onDismiss = { /* 弹窗关闭，不需要特殊处理 */ },
                onKeyEvent = { keyEvent ->
                    // 基本的键盘导航
                    false
                }
            )
        }
    }
}

/**
 * @符号查询信息
 */
private data class AtSymbolQuery(
    val position: Int,      // @符号的位置
    val query: String,      // 查询文本
    val totalLength: Int    // @符号+查询文本的总长度
)

/**
 * 查找@符号查询
 */
private fun findAtSymbolQuery(text: String, cursorPos: Int): AtSymbolQuery? {
    if (cursorPos <= 0) return null
    
    // 向前查找@符号
    var atPos = -1
    for (i in cursorPos - 1 downTo 0) {
        val char = text[i]
        when {
            char == '@' -> {
                atPos = i
                break
            }
            // 遇到空格、换行等分隔符，停止查找
            char.isWhitespace() || char in "(){}[]<>\"'`" -> {
                break
            }
        }
    }
    
    if (atPos == -1) return null
    
    // 检查@符号前是否是有效的触发位置
    val beforeAt = if (atPos > 0) text[atPos - 1] else ' '
    if (!beforeAt.isWhitespace() && beforeAt != '(' && beforeAt != '[') {
        return null
    }
    
    // 提取查询文本
    val queryStart = atPos + 1
    val queryEnd = cursorPos
    val query = if (queryStart < queryEnd) {
        text.substring(queryStart, queryEnd)
    } else {
        ""
    }
    
    // 检查查询是否有效（不包含换行符等）
    if (query.contains('\n') || query.contains('\t')) {
        return null
    }
    
    return AtSymbolQuery(
        position = atPos,
        query = query.trim(),
        totalLength = queryEnd - atPos
    )
}

/**
 * 插入文件注解
 */
private fun insertFileAnnotation(
    value: AnnotatedTextFieldValue,
    onValueChange: (AnnotatedTextFieldValue) -> Unit,
    file: IndexedFileInfo,
    atPosition: Int,
    queryLength: Int
) {
    // 生成显示文本
    val displayText = "@${file.name}"
    
    // 替换@查询文本为文件引用
    val beforeAt = value.text.substring(0, atPosition)
    val afterQuery = value.text.substring(atPosition + queryLength)
    val newText = beforeAt + displayText + afterQuery
    
    // 创建文件引用注解
    val annotation = FileReferenceAnnotation(
        startIndex = atPosition,
        endIndex = atPosition + displayText.length,
        file = file,
        displayText = displayText
    )
    
    // 更新其他注解的位置
    val lengthDelta = displayText.length - queryLength
    val updatedAnnotations = value.annotations.map { existingAnnotation ->
        when {
            existingAnnotation.startIndex > atPosition + queryLength -> {
                // 在插入位置之后的注解，需要调整位置
                existingAnnotation.copy(
                    startIndex = existingAnnotation.startIndex + lengthDelta,
                    endIndex = existingAnnotation.endIndex + lengthDelta
                )
            }
            else -> existingAnnotation
        }
    } + annotation
    
    // 设置新的光标位置（在插入的注解之后）
    val newCursorPos = atPosition + displayText.length
    
    // 更新值
    onValueChange(
        AnnotatedTextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos),
            annotations = updatedAnnotations
        )
    )
}

/**
 * 检查文件引用是否有效
 */
suspend fun validateFileReference(
    annotation: FileReferenceAnnotation,
    fileIndexService: FileIndexService
): Boolean {
    // 检查文件是否仍然存在于索引中
    return try {
        fileIndexService.searchFiles(annotation.file.name, 1).isNotEmpty()
    } catch (e: Exception) {
        false
    }
}

/**
 * 更新无效的文件引用
 */
suspend fun updateInvalidFileReferences(
    value: AnnotatedTextFieldValue,
    fileIndexService: FileIndexService,
    onValueChange: (AnnotatedTextFieldValue) -> Unit
) {
    val validAnnotations = value.annotations.filter { annotation ->
        validateFileReference(annotation, fileIndexService)
    }
    
    if (validAnnotations.size != value.annotations.size) {
        onValueChange(value.copy(annotations = validAnnotations))
    }
}