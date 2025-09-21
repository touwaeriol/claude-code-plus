/*
 * FilePopupManager.kt
 * 
 * 统一的文件弹窗管理业务组件
 * 封装弹窗显示逻辑，避免UI组件中的复杂逻辑
 */

package com.claudecodeplus.ui.jewel.components.business

import com.claudecodeplus.core.logging.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import com.claudecodeplus.ui.jewel.components.FileItemSelectionType
import com.claudecodeplus.ui.jewel.components.getItemSelectionType
import com.claudecodeplus.ui.services.IndexedFileInfo
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.runtime.LaunchedEffect
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

/**
 * 创建透明的空光标，用于键盘模式时隐藏鼠标指针
 */
private fun createEmptyCursor(): Cursor {
    return Toolkit.getDefaultToolkit().createCustomCursor(
        BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
        Point(0, 0),
        "Empty Cursor"
    )
}

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
    hoveredIndex: Int = -1,
    searchQuery: String,
    scrollState: LazyListState,
    config: FilePopupConfig,
    isIndexing: Boolean = false,
    onItemSelected: (IndexedFileInfo) -> Unit,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    onItemHover: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onPopupBoundsChanged: ((Rect) -> Unit)? = null,
    // 新增参数：搜索相关
    onSearchQueryChange: ((String) -> Unit)? = null,
    searchInputValue: String = searchQuery,
    // 键盘模式状态
    isKeyboardMode: Boolean = false
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
            // 根据弹窗类型决定是否可聚焦
            // 📌 修改：@ 符号弹窗也需要能接收键盘事件
            // 之前设为false导致键盘导航失效
            focusable = true, // 统一设为true，让所有弹窗都能接收键盘事件
            dismissOnBackPress = true,  // 允许返回键关闭
            dismissOnClickOutside = true, // 点击外部关闭
            clippingEnabled = false  // 允许弹窗超出边界
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
                .focusable(true) // 📌 关键：让Box可聚焦，能接收键盘事件
                .pointerHoverIcon(
                    // 键盘模式时隐藏鼠标指针，鼠标模式时显示正常指针
                    if (isKeyboardMode) PointerIcon(createEmptyCursor())
                    else PointerIcon.Default
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
                    logD("🎹 [UnifiedFilePopup] onPreviewKeyEvent接收: key=${keyEvent.key}, type=${keyEvent.type}")

                    // 只拦截导航相关的键盘事件，且不会抢夺搜索输入框的焦点
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionUp, Key.DirectionDown -> {
                                // 上下键始终用于导航，即使搜索框有焦点
                                logD("🎹 [UnifiedFilePopup] ✅ 拦截导航键: ${keyEvent.key}")
                                val handled = onKeyEvent(keyEvent)
                                logD("🎹 [UnifiedFilePopup] 导航键处理结果: $handled")
                                handled
                            }
                            Key.Enter -> {
                                // Enter键仅在有结果时处理
                                if (results.isNotEmpty()) {
                                    logD("🎹 [UnifiedFilePopup] ✅ 拦截Enter键 (有结果)")
                                    val handled = onKeyEvent(keyEvent)
                                    logD("🎹 [UnifiedFilePopup] Enter键处理结果: $handled")
                                    handled
                                } else {
                                    logD("🎹 [UnifiedFilePopup] ❌ 忽略Enter键 (无结果)")
                                    false
                                }
                            }
                            Key.Escape -> {
                                // Escape键始终用于关闭弹窗
                                logD("🎹 [UnifiedFilePopup] ✅ 拦截Escape键")
                                val handled = onKeyEvent(keyEvent)
                                logD("🎹 [UnifiedFilePopup] Escape键处理结果: $handled")
                                handled
                            }
                            else -> {
                                logD("🎹 [UnifiedFilePopup] ❌ 忽略非导航键: ${keyEvent.key}")
                                false
                            }
                        }
                    } else {
                        logD("🎹 [UnifiedFilePopup] ❌ 忽略非KeyDown事件: ${keyEvent.type}")
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
    logD("[UnifiedFilePopup] 检查是否显示搜索输入框: type=${config.type}, onSearchQueryChange=${onSearchQueryChange != null}")
                if (config.type == FilePopupType.ADD_CONTEXT && onSearchQueryChange != null) {
    logD("[UnifiedFilePopup] ✅ 显示搜索输入框")
                    SearchInputField(
                        value = searchInputValue,
                        onValueChange = onSearchQueryChange,
                        placeholder = "搜索文件...",
                        autoFocus = true, // 添加自动聚焦
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else {
    logD("[UnifiedFilePopup] ❌ 不显示搜索输入框 - type=${config.type}, onSearchQueryChange=${onSearchQueryChange != null}")
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
                        val selectionType = getItemSelectionType(
                            index = index,
                            keyboardIndex = selectedIndex,
                            mouseIndex = hoveredIndex,
                            isKeyboardMode = isKeyboardMode
                        )
                        JewelFileItem(
                            file = file,
                            selectionType = selectionType,
                            searchQuery = searchQuery,
                            onClick = { onItemSelected(file) },
                            onHover = { isHovering ->
                                if (isHovering) {
                                    onItemHover?.invoke(index)
                                } else {
                                    // 只有在当前项是悬停状态时才清除，避免误清除其他项的悬停
                                    if (index == hoveredIndex) {
                                        onItemHover?.invoke(-1)
                                    }
                                }
                            },
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
        logD("🎹 [FilePopupManager] 键盘事件接收: key=${keyEvent.key}, type=${keyEvent.type}, selectedIndex=$selectedIndex, resultsSize=$resultsSize")

        if (keyEvent.type != KeyEventType.KeyDown) {
            logD("🎹 [FilePopupManager] ❌ 忽略非KeyDown事件: ${keyEvent.type}")
            return false
        }

        return when (keyEvent.key) {
            Key.DirectionUp -> {
                logD("🎹 [FilePopupManager] ⬆️ 上箭头按下")
                if (resultsSize > 0) {
                    val newIndex = (selectedIndex - 1).coerceAtLeast(0)
                    logD("🎹 [FilePopupManager] ✅ 更新选中索引: $selectedIndex → $newIndex")
                    onIndexChange(newIndex)
                } else {
                    logD("🎹 [FilePopupManager] ❌ 无结果，忽略上箭头")
                }
                true
            }
            Key.DirectionDown -> {
                logD("🎹 [FilePopupManager] ⬇️ 下箭头按下")
                if (resultsSize > 0) {
                    val newIndex = (selectedIndex + 1).coerceAtMost(resultsSize - 1)
                    logD("🎹 [FilePopupManager] ✅ 更新选中索引: $selectedIndex → $newIndex")
                    onIndexChange(newIndex)
                } else {
                    logD("🎹 [FilePopupManager] ❌ 无结果，忽略下箭头")
                }
                true
            }
            Key.Enter -> {
                logD("🎹 [FilePopupManager] ⏎ Enter按下")
                if (selectedIndex in 0 until resultsSize) {
                    logD("🎹 [FilePopupManager] ✅ 选择项目: index=$selectedIndex")
                    onItemSelect()
                } else {
                    logD("🎹 [FilePopupManager] ❌ 无效选中索引: $selectedIndex (范围: 0-${resultsSize-1})")
                }
                true
            }
            Key.Escape -> {
                logD("🎹 [FilePopupManager] ⎋ Escape按下 - 关闭弹窗")
                onDismiss()
                true
            }
            else -> {
                logD("🎹 [FilePopupManager] ❓ 未处理的键: ${keyEvent.key}")
                false
            }
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
 * 专为 Add Context 弹窗设计的搜索输入框，支持自动聚焦
 * 使用改进的 BasicTextField 实现，添加了输入法支持
 */
@Composable
fun SearchInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    autoFocus: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 使用 FocusRequester 来管理焦点
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    
    // 当 autoFocus 为 true 时，自动请求焦点
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            logD("[SearchInputField] 🎯 尝试自动聚焦，autoFocus=$autoFocus")
            // 延迟一帧确保组件已完全初始化
            kotlinx.coroutines.delay(16)
            try {
                focusRequester.requestFocus()
    logD("[SearchInputField] ✅ 自动聚焦成功")
            } catch (e: IllegalStateException) {
    logD("[SearchInputField] ❌ 自动聚焦失败: ${e.message}")
                // 忽略焦点请求失败的异常，这是正常的竞争条件
            }
        }
    }
    
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
            onValueChange = { newValue ->
                logD("[SearchInputField] 📝 输入变化: '$value' -> '$newValue', 长度: ${value.length} -> ${newValue.length}")
                onValueChange(newValue)
            },
            textStyle = JewelTheme.defaultTextStyle.copy(
                fontSize = 13.sp,
                color = JewelTheme.globalColors.text.normal
            ),
            cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
            singleLine = true,
            enabled = true,
            readOnly = false,
            // 添加 KeyboardOptions 来确保支持所有输入类型
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            ),
            // 添加 KeyboardActions 
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = {
                    // 搜索动作 - 可以添加搜索逻辑
                }
            ),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester) // 添加焦点请求器
                .onPreviewKeyEvent { keyEvent ->
    logD("[SearchInputField] ⌨️  键盘事件: ${keyEvent.key}, type=${keyEvent.type}, isCtrlPressed=${keyEvent.isCtrlPressed}, isMetaPressed=${keyEvent.isMetaPressed}")
                    false // 不拦截，让BasicTextField正常处理
                }
                .onFocusChanged { focusState ->
                    logD("[SearchInputField] 🎯 焦点状态变化: isFocused=${focusState.isFocused}, hasFocus=${focusState.hasFocus}")
                }
                // 完全移除 onPreviewKeyEvent，让 BasicTextField 正常处理所有输入
                // 导航键已经在外层的 UnifiedFilePopup 中统一处理
        ) { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // 先显示 placeholder，如果有内容则被覆盖
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 13.sp,
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
                
                // 然后显示实际输入的文字，确保在最上层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp)  // 增加小的内边距
                ) {
                    innerTextField()
                }
            }
        }
    }
}
