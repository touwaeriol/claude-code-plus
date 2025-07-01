package com.claudecodeplus.ui.jewel.components.context

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * 上下文选择器弹出组件
 * 主要的容器组件，负责管理整个选择流程
 */
@Composable
fun ContextSelectorPopup(
    visible: Boolean,
    anchorPosition: IntOffset,
    state: ContextSelectionState,
    config: ContextSelectorConfig = ContextSelectorConfig(),
    searchService: ContextSearchService,
    onStateChange: (ContextSelectionState) -> Unit,
    onResult: (ContextSelectionResult) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    
    // 弹出器位置计算
    val popupOffset = remember(anchorPosition) {
        IntOffset(
            x = anchorPosition.x - config.popupWidth / 2, // 水平居中
            y = anchorPosition.y + 24 // 向下偏移
        )
    }
    
    Popup(
        offset = popupOffset,
        onDismissRequest = { onResult(ContextSelectionResult.Cancelled) },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = if (config.enableAnimations) {
                fadeIn() + scaleIn(initialScale = 0.95f)
            } else {
                EnterTransition.None
            },
            exit = if (config.enableAnimations) {
                fadeOut() + scaleOut(targetScale = 0.95f)
            } else {
                ExitTransition.None
            }
        ) {
            ContextSelectorContent(
                state = state,
                config = config,
                searchService = searchService,
                onStateChange = onStateChange,
                onResult = onResult,
                modifier = modifier
            )
        }
    }
}

/**
 * 上下文选择器内容组件
 */
@Composable
private fun ContextSelectorContent(
    state: ContextSelectionState,
    config: ContextSelectorConfig,
    searchService: ContextSearchService,
    onStateChange: (ContextSelectionState) -> Unit,
    onResult: (ContextSelectionResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(config.popupWidth.dp)
            .heightIn(max = config.popupMaxHeight.dp)
            .shadow(8.dp, RoundedCornerShape(4.dp))
            .background(
                JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(4.dp)
            )
            .border(
                1.dp,
                JewelTheme.globalColors.borders.normal,
                RoundedCornerShape(4.dp)
            )
            .clip(RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        when (state) {
            is ContextSelectionState.Hidden -> {
                // 不应该显示
            }
            
            is ContextSelectionState.SelectingType -> {
                ContextTypeSelector(
                    config = config,
                    onTypeSelected = { type ->
                        when (type) {
                            ContextType.File -> onStateChange(ContextSelectionState.SelectingFile())
                            ContextType.Web -> onStateChange(ContextSelectionState.SelectingWeb())
                            else -> {
                                // 未来扩展类型的处理
                                onResult(ContextSelectionResult.Cancelled)
                            }
                        }
                    },
                    onCancel = { onResult(ContextSelectionResult.Cancelled) }
                )
            }
            
            is ContextSelectionState.SelectingFile -> {
                FileContextSelector(
                    query = state.query,
                    config = config,
                    searchService = searchService,
                    onQueryChange = { newQuery ->
                        onStateChange(ContextSelectionState.SelectingFile(newQuery))
                    },
                    onFileSelected = { file ->
                        onResult(ContextSelectionResult.FileSelected(file))
                    },
                    onBack = { onStateChange(ContextSelectionState.SelectingType) },
                    onCancel = { onResult(ContextSelectionResult.Cancelled) }
                )
            }
            
            is ContextSelectionState.SelectingWeb -> {
                WebContextSelector(
                    url = state.url,
                    config = config,
                    searchService = searchService,
                    onUrlChange = { newUrl ->
                        onStateChange(ContextSelectionState.SelectingWeb(newUrl))
                    },
                    onWebSelected = { web ->
                        onResult(ContextSelectionResult.WebSelected(web))
                    },
                    onBack = { onStateChange(ContextSelectionState.SelectingType) },
                    onCancel = { onResult(ContextSelectionResult.Cancelled) }
                )
            }
        }
    }
}

/**
 * 检测@符号触发的工具函数
 */
fun detectAtTrigger(text: String, cursorPosition: Int): Boolean {
    if (cursorPosition == 0 || cursorPosition > text.length) return false
    
    val charAtCursor = text.getOrNull(cursorPosition - 1) ?: return false
    if (charAtCursor != '@') return false
    
    // 检查@符号前面的字符（前面必须是空格或在开头）
    val charBefore = text.getOrNull(cursorPosition - 2)
    if (charBefore != null && !charBefore.isWhitespace()) return false
    
    // @符号后面可以是任何字符（包括立即输入的内容）
    // 不需要检查后面的字符，用户刚输入@时光标就在@后面
    
    return true
}

/**
 * 获取@符号在文本中的位置
 */
fun getAtSymbolPosition(text: String, cursorPosition: Int): Int? {
    if (!detectAtTrigger(text, cursorPosition)) return null
    return cursorPosition - 1
}

/**
 * 创建上下文引用字符串
 */
fun createContextReference(result: ContextSelectionResult): String {
    return when (result) {
        is ContextSelectionResult.FileSelected -> "@file://${result.item.relativePath}"
        is ContextSelectionResult.WebSelected -> "@web://${result.item.url}"
        is ContextSelectionResult.Cancelled -> ""
    }
} 