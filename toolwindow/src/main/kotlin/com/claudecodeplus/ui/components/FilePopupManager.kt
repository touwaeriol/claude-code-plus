/*
 * FilePopupManager.kt
 * 
 * 统一的文件弹窗管理业务组件
 * 封装弹窗显示逻辑，避免UI组件中的复杂逻辑
 */

package com.claudecodeplus.ui.jewel.components.business

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.claudecodeplus.ui.jewel.components.JewelFileItem
import com.claudecodeplus.ui.services.IndexedFileInfo
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable

/**
 * 弹窗类型枚举
 */
enum class FilePopupType {
    AT_SYMBOL,      // @ 符号触发的弹窗
    ADD_CONTEXT     // Add Context 按钮触发的弹窗
}

/**
 * 文件弹窗配置数据类
 */
data class FilePopupConfig(
    val type: FilePopupType,
    val anchorOffset: Offset,
    val width: Float = 360f,
    val maxHeight: Float = 320f,
    val spacing: Float = 4f
)

/**
 * 统一的文件弹窗管理器
 * 
 * 职责：
 * 1. 管理弹窗的显示和隐藏
 * 2. 处理弹窗定位逻辑
 * 3. 统一键盘事件处理
 * 4. 提供一致的文件列表渲染
 */
@Composable
fun UnifiedFilePopup(
    results: List<IndexedFileInfo>,
    selectedIndex: Int,
    searchQuery: String,
    scrollState: LazyListState,
    config: FilePopupConfig,
    isIndexing: Boolean = false,
    onItemSelected: (IndexedFileInfo) -> Unit,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
    onPopupBoundsChanged: ((Rect) -> Unit)? = null,
    // 新增参数：搜索相关
    onSearchQueryChange: ((String) -> Unit)? = null,
    searchInputValue: String = searchQuery
) {
    // 追踪弹窗边界
    var popupBounds by remember { mutableStateOf<Rect?>(null) }
    
    // 根据弹窗类型创建不同的定位提供器
    val positionProvider = remember(config) {
        when (config.type) {
            FilePopupType.AT_SYMBOL -> AtSymbolPositionProvider(config)
            FilePopupType.ADD_CONTEXT -> ButtonPositionProvider(config)
        }
    }
    
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false, // 不抢夺焦点，让输入框保持焦点
            dismissOnBackPress = false, // 通过ESC键手动控制
            dismissOnClickOutside = true
        ),
        popupPositionProvider = positionProvider
    ) {
        Box(
            modifier = modifier
                .width(config.width.dp)
                .heightIn(max = config.maxHeight.dp)
                .background(
                    JewelTheme.globalColors.panelBackground,
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    JewelTheme.globalColors.borders.normal,
                    RoundedCornerShape(8.dp)
                )
                .onGloballyPositioned { coordinates ->
                    // 追踪弹窗边界
                    val position = coordinates.positionInRoot()
                    val size = coordinates.size
                    val bounds = Rect(
                        position.x,
                        position.y,
                        position.x + size.width,
                        position.y + size.height
                    )
                    popupBounds = bounds
                    onPopupBoundsChanged?.invoke(bounds)
                }
                .onPreviewKeyEvent { keyEvent ->
                    // 只拦截导航相关的键盘事件
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
                // 为 ADD_CONTEXT 类型添加搜索输入框
                if (config.type == FilePopupType.ADD_CONTEXT && onSearchQueryChange != null) {
                    SearchInputField(
                        value = searchInputValue,
                        onValueChange = onSearchQueryChange,
                        placeholder = "搜索文件...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
                
                // 索引状态提示
                if (isIndexing) {
                    IndexingStatusBanner()
                }
                
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (config.maxHeight - 20).dp), // 减去padding
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    itemsIndexed(results) { index, file ->
                        JewelFileItem(
                            file = file,
                            isSelected = index == selectedIndex,
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

/**
 * @ 符号弹窗定位提供器
 */
class AtSymbolPositionProvider(
    private val config: FilePopupConfig
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize
    ): androidx.compose.ui.unit.IntOffset {
        val absoluteAtX = anchorBounds.left + config.anchorOffset.x.toInt()
        val absoluteAtY = anchorBounds.top + config.anchorOffset.y.toInt()
        val spacing = config.spacing.toInt()
        
        val popupX = (absoluteAtX - popupContentSize.width / 2).coerceIn(
            0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        )
        val popupY = (absoluteAtY - popupContentSize.height - spacing).coerceAtLeast(0)
        
        return androidx.compose.ui.unit.IntOffset(popupX, popupY)
    }
}

/**
 * Add Context 按钮弹窗定位提供器
 */
class ButtonPositionProvider(
    private val config: FilePopupConfig
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize
    ): androidx.compose.ui.unit.IntOffset {
        val buttonCenterX = config.anchorOffset.x.toInt()
        val buttonY = config.anchorOffset.y.toInt()
        val spacing = config.spacing.toInt()
        
        // 水平居中对齐按钮
        val popupX = (buttonCenterX - popupContentSize.width / 2).coerceIn(
            0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        )
        
        // 修复：显示在按钮上方，而不是下方
        val popupY = (buttonY - popupContentSize.height - spacing).coerceAtLeast(0)
        
        return androidx.compose.ui.unit.IntOffset(popupX, popupY)
    }
}

/**
 * 文件弹窗事件处理器
 * 
 * 职责：
 * 1. 统一处理键盘导航事件
 * 2. 提供一致的选择和关闭逻辑
 */
class FilePopupEventHandler {
    
    /**
     * 处理键盘事件
     */
    fun handleKeyEvent(
        keyEvent: KeyEvent,
        selectedIndex: Int,
        resultsSize: Int,
        onIndexChange: (Int) -> Unit,
        onItemSelect: () -> Unit,
        onDismiss: () -> Unit
    ): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        
        return when (keyEvent.key) {
            Key.DirectionUp -> {
                if (resultsSize > 0) {
                    val newIndex = (selectedIndex - 1).coerceAtLeast(0)
                    onIndexChange(newIndex)
                }
                true
            }
            Key.DirectionDown -> {
                if (resultsSize > 0) {
                    val newIndex = (selectedIndex + 1).coerceAtMost(resultsSize - 1)
                    onIndexChange(newIndex)
                }
                true
            }
            Key.Enter -> {
                if (selectedIndex in 0 until resultsSize) {
                    onItemSelect()
                }
                true
            }
            Key.Escape -> {
                onDismiss()
                true
            }
            else -> false
        }
    }
}

/**
 * 索引状态横幅提示
 * 在文件索引期间显示提示信息
 */
@Composable
fun IndexingStatusBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                JewelTheme.globalColors.borders.focused.copy(alpha = 0.1f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 索引进度图标
            Text(
                text = "⏳",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            
            // 提示文字
            Text(
                text = "正在建立索引，文件搜索功能受限",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.info
                )
            )
        }
    }
}

/**
 * 文件搜索输入框组件
 * 专为 Add Context 弹窗设计的搜索输入框
 * 使用基础的 BasicTextField 实现，确保兼容性
 */
@Composable
fun SearchInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .background(
                JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(4.dp)
            )
            .border(
                1.dp,
                JewelTheme.globalColors.borders.normal,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = JewelTheme.defaultTextStyle.copy(
                fontSize = 13.sp,
                color = JewelTheme.globalColors.text.normal
            ),
            cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
            singleLine = true,
            modifier = Modifier.fillMaxSize()
        ) { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 13.sp,
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
                innerTextField()
            }
        }
    }
}