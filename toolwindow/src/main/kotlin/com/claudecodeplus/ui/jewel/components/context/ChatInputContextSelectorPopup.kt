/*
 * ChatInputContextSelectorPopup.kt
 * 
 * 为聊天输入区域设计的简化版上下文选择器弹出组件
 */

package com.claudecodeplus.ui.jewel.components.context

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.claudecodeplus.ui.models.*

/**
 * 简化的上下文选择器弹出组件
 * 专为聊天输入区域设计，提供文件和网页上下文选择
 * 
 * @param onDismiss 关闭回调
 * @param onContextSelect 上下文选择回调
 * @param searchService 搜索服务
 * @param modifier 修饰符
 */
@Composable
fun ChatInputContextSelectorPopup(
    onDismiss: () -> Unit,
    onContextSelect: (ContextReference) -> Unit,
    searchService: ContextSearchService,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf<ContextSelectionState>(ContextSelectionState.SelectingType) }
    
    ContextSelectorPopup(
        visible = true,
        anchorPosition = IntOffset.Zero,
        state = state,
        searchService = searchService,
        onStateChange = { newState -> state = newState },
        onResult = { result ->
            when (result) {
                is ContextSelectionResult.FileSelected -> {
                    val contextRef = ContextReference.FileReference(
                        path = result.item.relativePath
                    )
                    onContextSelect(contextRef)
                }
                is ContextSelectionResult.WebSelected -> {
                    val contextRef = ContextReference.WebReference(
                        url = result.item.url
                    )
                    onContextSelect(contextRef)
                }
                is ContextSelectionResult.Cancelled -> {
                    onDismiss()
                }
            }
        },
        modifier = modifier
    )
} 