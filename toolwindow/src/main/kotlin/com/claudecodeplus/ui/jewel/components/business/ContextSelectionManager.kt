/*
 * ContextSelectionManager.kt
 * 
 * 上下文选择管理业务组件
 * 封装上下文选择相关的业务逻辑
 */

package com.claudecodeplus.ui.jewel.components.business

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.claudecodeplus.ui.models.ContextReference
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.IndexedFileInfo
import kotlinx.coroutines.delay
import com.claudecodeplus.ui.jewel.components.createMarkdownContextLink

/**
 * 上下文选择状态
 */
data class ContextSelectionState(
    val isAtSymbolActive: Boolean = false,
    val isAddContextActive: Boolean = false,
    val atPosition: Int? = null,
    val searchQuery: String = "",
    val searchResults: List<IndexedFileInfo> = emptyList(),
    val selectedIndex: Int = 0,
    val isLoading: Boolean = false
)

/**
 * 上下文选择管理器
 * 
 * 职责：
 * 1. 管理@ 符号和 Add Context 两种选择模式
 * 2. 处理文件搜索逻辑
 * 3. 统一处理选择结果
 * 4. 管理状态同步
 */
class ContextSelectionManager(
    private val fileIndexService: FileIndexService?,
    private val onContextAdd: (ContextReference) -> Unit,
    private val onTextUpdate: (TextFieldValue) -> Unit
) {
    
    /**
     * 检查光标位置是否在@ 查询中
     */
    fun isInAtQuery(text: String, cursorPosition: Int): Pair<Int, String>? {
        if (cursorPosition <= 0 || cursorPosition > text.length) return null
        
        // 向前查找最近的@符号
        var atPosition = -1
        for (i in cursorPosition - 1 downTo 0) {
            when (text[i]) {
                '@' -> {
                    atPosition = i
                    break
                }
                ' ', '\n', '\t' -> break // 遇到空白字符停止搜索
            }
        }
        
        if (atPosition == -1) return null
        
        // 向前查找空白字符或开始位置
        val queryStart = atPosition + 1
        val queryEnd = run {
            for (i in cursorPosition until text.length) {
                if (text[i] in " \n\t") return@run i
            }
            text.length
        }
        
        val query = text.substring(queryStart, queryEnd)
        return if (queryEnd >= cursorPosition) {
            Pair(atPosition, query)
        } else {
            null
        }
    }
    
    /**
     * 处理@ 符号查询
     */
    suspend fun handleAtQuery(
        textFieldValue: TextFieldValue,
        enabled: Boolean
    ): ContextSelectionState? {
        if (!enabled || fileIndexService == null) {
            return null
        }
        
        val cursorPos = textFieldValue.selection.start
        val atResult = isInAtQuery(textFieldValue.text, cursorPos)
        
        return if (atResult != null) {
            val (atPos, query) = atResult
            
            try {
                val results = if (query.isEmpty()) {
                    fileIndexService.getRecentFiles(10)
                } else {
                    fileIndexService.searchFiles(query, 10)
                }
                
                ContextSelectionState(
                    isAtSymbolActive = true,
                    atPosition = atPos,
                    searchQuery = query,
                    searchResults = results,
                    selectedIndex = 0,
                    isLoading = false
                )
            } catch (e: Exception) {
                ContextSelectionState(
                    isAtSymbolActive = false,
                    searchResults = emptyList(),
                    isLoading = false
                )
            }
        } else {
            null
        }
    }
    
    /**
     * 处理 Add Context 按钮触发的文件选择
     */
    suspend fun handleAddContextSelection(): ContextSelectionState? {
        if (fileIndexService == null) return null
        
        return try {
            val results = fileIndexService.getRecentFiles(10)
            ContextSelectionState(
                isAddContextActive = true,
                searchResults = results,
                selectedIndex = 0,
                isLoading = false
            )
        } catch (e: Exception) {
            ContextSelectionState(
                isAddContextActive = false,
                searchResults = emptyList(),
                isLoading = false
            )
        }
    }
    
    /**
     * 处理@ 符号文件选择
     */
    fun handleAtSymbolFileSelection(
        file: IndexedFileInfo,
        currentText: TextFieldValue,
        atPosition: Int
    ) {
        val displayName = file.name
        val markdownLink = createMarkdownContextLink(
            displayName = displayName,
            uri = "file://${file.absolutePath}"
        )
        
        // 计算替换范围：找到@ 查询的结束位置
        val queryStart = atPosition + 1
        var queryEnd = currentText.selection.start
        for (i in queryStart until currentText.text.length) {
            if (currentText.text[i] in " \n\t") {
                queryEnd = i
                break
            }
        }
        
        val newText = currentText.text.replaceRange(atPosition, queryEnd, markdownLink)
        val newPosition = atPosition + markdownLink.length
        
        onTextUpdate(TextFieldValue(
            text = newText,
            selection = TextRange(newPosition)
        ))
    }
    
    /**
     * 处理 Add Context 文件选择
     */
    fun handleAddContextFileSelection(file: IndexedFileInfo) {
        val contextReference = ContextReference.FileReference(
            path = file.relativePath,
            fullPath = file.absolutePath
        )
        onContextAdd(contextReference)
    }
}

/**
 * 上下文选择组合函数
 * 
 * 提供完整的上下文选择功能，包括状态管理和业务逻辑
 */
@Composable
fun rememberContextSelectionManager(
    fileIndexService: FileIndexService?,
    onContextAdd: (ContextReference) -> Unit,
    onTextUpdate: (TextFieldValue) -> Unit
): ContextSelectionManager {
    return remember(fileIndexService) {
        ContextSelectionManager(
            fileIndexService = fileIndexService,
            onContextAdd = onContextAdd,
            onTextUpdate = onTextUpdate
        )
    }
}

/**
 * 上下文选择状态管理
 */
@Composable
fun rememberContextSelectionState(
    textFieldValue: TextFieldValue,
    contextManager: ContextSelectionManager,
    enabled: Boolean
): ContextSelectionState {
    var state by remember { mutableStateOf(ContextSelectionState()) }
    
    // @ 符号查询检测
    LaunchedEffect(textFieldValue.text, textFieldValue.selection.start, enabled) {
        if (!enabled) {
            state = ContextSelectionState()
            return@LaunchedEffect
        }
        
        val newState = contextManager.handleAtQuery(textFieldValue, enabled)
        state = newState ?: ContextSelectionState()
    }
    
    return state
}

/**
 * 文件弹窗状态管理组合函数
 */
@Composable
fun rememberFilePopupState(): Pair<LazyListState, FilePopupEventHandler> {
    val scrollState = rememberLazyListState()
    val eventHandler = remember { FilePopupEventHandler() }
    
    return Pair(scrollState, eventHandler)
}