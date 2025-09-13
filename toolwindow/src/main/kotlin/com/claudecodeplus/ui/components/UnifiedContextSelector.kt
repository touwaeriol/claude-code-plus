/*
 * UnifiedContextSelector.kt
 * 
 * 统一的上下文选择组件
 * 支持 @ 符号和 Add Context 按钮两种触发模式
 * 内部复用关键词搜索和文件列表组件
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import com.claudecodeplus.ui.models.ContextReference
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.IndexedFileInfo
import com.claudecodeplus.ui.jewel.components.business.*

/**
 * 上下文选择触发模式
 */
enum class ContextTriggerMode {
    AT_SYMBOL,      // @ 符号触发：使用输入框后的文本搜索，不显示搜索输入框
    ADD_CONTEXT     // Add Context 按钮触发：显示搜索输入框并自动聚焦
}

/**
 * 统一的上下文选择组件
 * 
 * 根据触发模式提供不同的交互体验：
 * - AT_SYMBOL: 从输入框提取关键词，直接显示文件列表
 * - ADD_CONTEXT: 显示搜索输入框，用户可以输入关键词搜索
 * 
 * 两种模式内部复用相同的搜索引擎和文件列表组件
 */
@Composable
fun UnifiedContextSelector(
    mode: ContextTriggerMode,
    fileIndexService: FileIndexService?,
    popupOffset: Offset,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    
    // @ 符号模式专用参数
    textFieldValue: TextFieldValue? = null,
    atPosition: Int? = null,
    onTextChange: ((TextFieldValue) -> Unit)? = null,
    textLayoutResult: androidx.compose.ui.text.TextLayoutResult? = null,
    
    // Add Context 模式专用参数
    onContextAdd: ((ContextReference) -> Unit)? = null,
    
    // 通用参数
    enabled: Boolean = true
) {
    println("[UnifiedContextSelector] 模式: $mode, atPosition: $atPosition")
    
    when (mode) {
        ContextTriggerMode.AT_SYMBOL -> {
            // @ 符号模式：使用现有的 SimpleInlineFileReferenceHandler
            if (textFieldValue != null && onTextChange != null) {
                SimpleInlineFileReferenceHandler(
                    textFieldValue = textFieldValue,
                    onTextChange = onTextChange,
                    fileIndexService = fileIndexService,
                    enabled = enabled,
                    textLayoutResult = textLayoutResult,
                    modifier = modifier
                )
            }
        }
        
        ContextTriggerMode.ADD_CONTEXT -> {
            // Add Context 模式：显示带搜索输入框的弹窗
            if (onContextAdd != null) {
                AddContextPopup(
                    fileIndexService = fileIndexService,
                    popupOffset = popupOffset,
                    onContextAdd = onContextAdd,
                    onDismiss = onDismiss,
                    modifier = modifier
                )
            }
        }
    }
}

/**
 * Add Context 弹窗组件
 * 显示搜索输入框和文件列表，支持关键词搜索
 */
@Composable
fun AddContextPopup(
    fileIndexService: FileIndexService?,
    popupOffset: Offset,
    onContextAdd: (ContextReference) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    println("[AddContextPopup] 显示 Add Context 弹窗")
    
    if (fileIndexService == null) return
    
    var searchResults by remember { mutableStateOf<List<IndexedFileInfo>>(emptyList()) }
    var keyboardSelectedIndex by remember { mutableStateOf(0) }
    var mouseHoveredIndex by remember { mutableStateOf(-1) }
    var isIndexing by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    
    // 键盘模式状态：当使用上下键导航时进入键盘模式，鼠标移动时退出
    var isKeyboardMode by remember { mutableStateOf(false) }
    var lastMousePosition by remember { mutableStateOf(Offset.Zero) }
    
    // 计算当前有效选中索引：根据模式优先级决定
    val effectiveSelectedIndex = if (isKeyboardMode) keyboardSelectedIndex 
                               else if (mouseHoveredIndex >= 0) mouseHoveredIndex 
                               else keyboardSelectedIndex
    
    // 管理弹窗状态
    val (scrollState, eventHandler) = rememberFilePopupState()
    
    // 执行搜索
    LaunchedEffect(searchInput) {
        try {
            isIndexing = !fileIndexService.isIndexReady()
            
            val results = if (searchInput.isEmpty()) {
                // 空搜索：显示最近文件
                fileIndexService.getRecentFiles().take(20)
            } else {
                // 关键词搜索
                fileIndexService.searchFiles(searchInput).take(50)
            }
            
            searchResults = results
            keyboardSelectedIndex = 0 // 重置键盘选择索引
            mouseHoveredIndex = -1 // 清除鼠标悬停状态
            
            println("[AddContextPopup] 搜索结果: ${results.size} 个文件，关键词: '$searchInput'")
            
        } catch (e: Exception) {
            println("[AddContextPopup] 搜索失败: ${e.message}")
            searchResults = emptyList()
        }
    }
    
    // 使用 ButtonFilePopup 显示带搜索输入框的弹窗
    ButtonFilePopup(
        results = searchResults,
        selectedIndex = keyboardSelectedIndex,
        hoveredIndex = mouseHoveredIndex,
        searchQuery = searchInput,
        scrollState = scrollState,
        popupOffset = popupOffset,
        isIndexing = isIndexing,
        onItemSelected = { selectedFile ->
            // 将选中文件添加到上下文列表
            val contextReference = ContextReference.FileReference(
                path = selectedFile.relativePath,
                fullPath = selectedFile.absolutePath
            )
            onContextAdd(contextReference)
        },
        onDismiss = onDismiss,
        onKeyEvent = { keyEvent ->
            // 如果是第一次按键且有鼠标悬停，从鼠标位置开始导航
            val startIndex = if (!isKeyboardMode && mouseHoveredIndex >= 0) {
                mouseHoveredIndex
            } else {
                keyboardSelectedIndex
            }
            
            println("🎹 [AddContextPopup] Add Context弹窗接收键盘事件: startIndex=$startIndex, resultsSize=${searchResults.size}")

            val handled = eventHandler.handleKeyEvent(
                keyEvent = keyEvent,
                selectedIndex = startIndex,
                resultsSize = searchResults.size,
                onIndexChange = { newIndex ->
                    println("🎹 [AddContextPopup] ✅ Add Context弹窗更新选中索引: $keyboardSelectedIndex → $newIndex")
                    keyboardSelectedIndex = newIndex
                    // 键盘操作时进入键盘模式
                    isKeyboardMode = true
                },
                onItemSelect = {
                    println("🎹 [AddContextPopup] ✅ Add Context弹窗选择文件: effectiveIndex=$effectiveSelectedIndex")
                    if (effectiveSelectedIndex in searchResults.indices) {
                        val selectedFile = searchResults[effectiveSelectedIndex]
                        println("🎹 [AddContextPopup] 选择的文件: ${selectedFile.relativePath}")
                        val contextReference = ContextReference.FileReference(
                            path = selectedFile.relativePath,
                            fullPath = selectedFile.absolutePath
                        )
                        onContextAdd(contextReference)
                    } else {
                        println("🎹 [AddContextPopup] ❌ 无效选择: effectiveIndex=$effectiveSelectedIndex, resultsSize=${searchResults.size}")
                    }
                },
                onDismiss = {
                    println("🎹 [AddContextPopup] ❌ Add Context弹窗关闭")
                    onDismiss()
                }
            )

            println("🎹 [AddContextPopup] Add Context键盘事件处理结果: $handled")
            handled
        },
        onItemHover = { hoveredIdx ->
            mouseHoveredIndex = hoveredIdx
            // 鼠标悬停时退出键盘模式，恢复鼠标优先级
            if (hoveredIdx >= 0) {
                isKeyboardMode = false
            }
        },
        modifier = modifier,
        onSearchQueryChange = { newQuery ->
            searchInput = newQuery
        },
        searchInputValue = searchInput,
        isKeyboardMode = isKeyboardMode
    )
}