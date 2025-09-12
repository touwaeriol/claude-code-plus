/*
 * PreciseAtSymbolFilePopup.kt
 * 
 * 精确定位的@符号文件弹窗组件
 * 使用TextLayoutResult.getBoundingBox来精确定位@符号位置
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.claudecodeplus.ui.services.IndexedFileInfo
import com.claudecodeplus.ui.jewel.components.business.*
import com.claudecodeplus.ui.jewel.components.JewelFileItem
import com.claudecodeplus.ui.jewel.components.FileItemSelectionType
import com.claudecodeplus.ui.jewel.components.getItemSelectionType
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * 精确定位的@符号文件弹窗
 * 
 * 使用TextLayoutResult.getBoundingBox来精确定位@符号位置
 */
@Composable
fun PreciseAtSymbolFilePopup(
    results: List<IndexedFileInfo>,
    selectedIndex: Int,
    searchQuery: String,
    scrollState: LazyListState,
    atPosition: Int,
    textLayoutResult: androidx.compose.ui.text.TextLayoutResult?,
    onItemSelected: (IndexedFileInfo) -> Unit,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    // 计算@符号的精确位置
    val atSymbolOffset = remember(textLayoutResult, atPosition) {
        textLayoutResult?.let { layoutResult ->
            try {
                // 使用getBoundingBox获取@符号的精确位置
                val atCharRect = layoutResult.getBoundingBox(atPosition)
                
                // 返回@符号的位置，这里只是相对于文本的坐标
                // 需要输入框坐标来转换为屏幕坐标
                Offset(
                    x = atCharRect.left,
                    y = atCharRect.top
                )
            } catch (e: Exception) {
                // 如果getBoundingBox失败，使用默认位置
                Offset(50f, 30f)
            }
        } ?: Offset(50f, 30f) // 默认位置
    }
    
    // 追踪弹窗边界
    var popupBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    
    Popup(
            onDismissRequest = onDismiss,
            properties = PopupProperties(
                focusable = false, // 不抢夺焦点，让输入框保持焦点
                dismissOnBackPress = false, // 通过ESC键手动控制
                dismissOnClickOutside = true
            ),
            popupPositionProvider = remember(atSymbolOffset) {
                AtSymbolPositionProvider(
                    FilePopupConfig(
                        type = FilePopupType.AT_SYMBOL,
                        anchorOffset = atSymbolOffset
                    )
                )
            }
        ) {
            // 使用基础的背景容器替代Panel
            Box(
                modifier = modifier
                    .width(360.dp)
                    .heightIn(max = 320.dp)
                    .background(
                        JewelTheme.globalColors.panelBackground,
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        JewelTheme.globalColors.borders.normal,
                        RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .onGloballyPositioned { coordinates ->
                        // 追踪弹窗边界
                        val position = coordinates.positionInRoot()
                        val size = coordinates.size
                        val bounds = androidx.compose.ui.geometry.Rect(
                            position.x,
                            position.y,
                            position.x + size.width,
                            position.y + size.height
                        )
                        popupBounds = bounds
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        // 只拦截导航相关的键盘事件，让其他输入正常通过
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.Escape, Key.Tab -> {
                                    onKeyEvent(keyEvent)
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        itemsIndexed(results) { index, file ->
                            // @ 符号模式始终不使用键盘模式，保持简单的选择逻辑
                            val selectionType = if (index == selectedIndex) {
                                FileItemSelectionType.PRIMARY
                            } else {
                                FileItemSelectionType.NONE
                            }
                            JewelFileItem(
                                file = file,
                                selectionType = selectionType,
                                searchQuery = searchQuery,
                                onClick = { onItemSelected(file) },
                                modifier = Modifier.fillMaxWidth(),
                                anchorBounds = popupBounds
                            )
                        }
                    }
                }
            }
        }
}