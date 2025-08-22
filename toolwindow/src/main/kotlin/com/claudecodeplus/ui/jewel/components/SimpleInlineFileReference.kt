/*
 * SimpleInlineFileReferenceClean.kt
 * 
 * 重构后的简化文件引用组件 - 支持Cursor风格和二级悬浮
 * 完全基于Jewel组件实现
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.claudecodeplus.ui.services.IndexedFileInfo
import com.claudecodeplus.ui.services.FileIndexService
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.runtime.rememberCoroutineScope

/**
 * @ 符号专用的弹窗定位提供器 - 精确字符定位版本
 */
class AtSymbolPopupPositionProvider(
    private val atSymbolOffset: Offset
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        // atSymbolOffset 是相对于输入框的文本坐标
        // anchorBounds 是输入框在屏幕上的边界
        // 需要将文本坐标转换为屏幕坐标
        val absoluteAtX = anchorBounds.left + atSymbolOffset.x.toInt()
        val absoluteAtY = anchorBounds.top + atSymbolOffset.y.toInt()
        
        // @ 符号弹窗定位：显示在 @ 符号正上方，水平居中对齐 @ 符号
        val minSpacing = 4 // 最小间距，让弹窗紧贴 @ 符号上方
        
        // 水平位置：以 @ 符号为中心，弹窗水平居中
        val popupX = (absoluteAtX - popupContentSize.width / 2).coerceIn(
            0, 
            (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        )
        
        // 垂直位置：弹窗底部紧贴 @ 符号上方
        val popupY = (absoluteAtY - popupContentSize.height - minSpacing).coerceAtLeast(0)
        
        return IntOffset(popupX, popupY)
    }
}

/**
 * Add Context 按钮专用的弹窗定位提供器
 */
class ButtonPopupPositionProvider(
    private val buttonOffset: Offset
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        // Add Context 按钮弹窗专用定位：紧贴按钮上方
        val spacing = 2 // 按钮弹窗使用最小间距
        return IntOffset(
            x = (buttonOffset.x - popupContentSize.width / 2).toInt().coerceAtLeast(0), // 以按钮为中心水平居中
            y = (buttonOffset.y - popupContentSize.height - spacing).toInt().coerceAtLeast(0) // 上方，使用实际高度
        )
    }
}

/**
 * 检查光标当前位置是否在 @ 查询字符串中
 * 实时判断，无需延迟机制
 * 
 * 检测条件：
 * 1. @ 前面是行开头或空格
 * 2. 当前光标位置到 @ 之间没有空格分隔符
 * 3. 只在同一行内检测
 */
fun isInAtQuery(text: String, cursorPos: Int): Pair<Int, String>? {
    if (cursorPos <= 0 || text.isEmpty()) return null
    
    // 从光标位置向前逐字符检查
    for (i in (cursorPos - 1) downTo 0) {
        val char = text[i]
        
        when {
            // 找到 @ 符号
            char == '@' -> {
                // 检查 @ 前面的条件：必须是行开头或空格
                val isValidAtStart = i == 0 || text[i - 1].isWhitespace()
                if (!isValidAtStart) return null
                
                // 检查从 @ 到光标位置之间是否有空格（如果有，说明不在同一个查询中）
                val queryPart = text.substring(i + 1, cursorPos)
                if (queryPart.contains(' ')) return null
                
                // 检查是否跨行（不支持跨行查询）
                if (queryPart.contains('\n')) return null
                
                // 返回 @ 位置和查询文本
                return Pair(i, queryPart)
            }
            
            // 遇到换行符：停止向前搜索
            char == '\n' -> return null
        }
    }
    
    return null
}


/**
 * 简化内联文件引用处理器 - 支持动态光标位置计算
 */
@Composable
fun SimpleInlineFileReferenceHandler(
    textFieldValue: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    fileIndexService: FileIndexService?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isPopupVisible by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<IndexedFileInfo>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf(0) }
    var atPosition by remember { mutableStateOf(-1) }
    var searchQuery by remember { mutableStateOf("") }
    var textFieldCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var popupOffset by remember { mutableStateOf(Offset.Zero) }
    var popupBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // 实时检测光标位置是否在 @ 字符串中
    LaunchedEffect(textFieldValue.text, textFieldValue.selection.start) {
        if (!enabled || fileIndexService == null) {
            isPopupVisible = false
            return@LaunchedEffect
        }
        
        val cursorPos = textFieldValue.selection.start
        val atResult = isInAtQuery(textFieldValue.text, cursorPos)
        
        if (atResult != null) {
            val (atPos, query) = atResult
            atPosition = atPos
            searchQuery = query
            selectedIndex = 0
            
            // 立即计算弹窗位置 - 使用简化逻辑
            textFieldCoordinates?.let { coordinates ->
                popupOffset = calculatePopupOffset(
                    coordinates,
                    textFieldValue,
                    atPos,
                    density
                )
            }
            
            // 无延迟，立即搜索
            try {
                val results = if (query.isEmpty()) {
                    fileIndexService.getRecentFiles(10)
                } else {
                    fileIndexService.searchFiles(query, 10)
                }
                searchResults = results
                isPopupVisible = results.isNotEmpty()
            } catch (e: Exception) {
                searchResults = emptyList()
                isPopupVisible = false
            }
        } else {
            isPopupVisible = false
            searchResults = emptyList()
        }
    }
    
    // 增强的键盘事件处理 - 改进焦点管理和导航体验
    val handleKeyEvent: (KeyEvent) -> Boolean = { keyEvent ->
        if (isPopupVisible && keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.DirectionUp -> {
                    selectedIndex = if (selectedIndex <= 0) {
                        searchResults.size - 1  // 循环导航：从开头跳到末尾
                    } else {
                        selectedIndex - 1
                    }
                    // 平滑滚动到选中项，确保可见性
                    coroutineScope.launch {
                        scrollState.animateScrollToItem(
                            index = selectedIndex,
                            scrollOffset = -50 // 增加偏移确保选中项在视窗中央
                        )
                    }
                    true
                }
                Key.DirectionDown -> {
                    selectedIndex = if (selectedIndex >= searchResults.size - 1) {
                        0  // 循环导航：从末尾跳到开头
                    } else {
                        selectedIndex + 1
                    }
                    // 平滑滚动到选中项
                    coroutineScope.launch {
                        scrollState.animateScrollToItem(
                            index = selectedIndex,
                            scrollOffset = -50
                        )
                    }
                    true
                }
                Key.Enter -> {
                    if (selectedIndex in searchResults.indices) {
                        val selectedFile = searchResults[selectedIndex]
                        val currentText = textFieldValue.text
                        val fileName = selectedFile.relativePath.ifEmpty { selectedFile.name }
                        val replacement = "@$fileName"
                        
                        val replaceStart = atPosition
                        val replaceEnd = atPosition + 1 + searchQuery.length
                        
                        val newText = currentText.replaceRange(replaceStart, replaceEnd, replacement)
                        val newPosition = replaceStart + replacement.length
                        
                        onTextChange(TextFieldValue(
                            text = newText,
                            selection = TextRange(newPosition)
                        ))
                        isPopupVisible = false
                    }
                    true
                }
                Key.Escape -> {
                    isPopupVisible = false
                    true
                }
                Key.Tab -> {
                    // Tab键也可以进行导航
                    selectedIndex = if (selectedIndex >= searchResults.size - 1) {
                        0
                    } else {
                        selectedIndex + 1
                    }
                    coroutineScope.launch {
                        scrollState.animateScrollToItem(selectedIndex, -50)
                    }
                    true
                }
                else -> false
            }
        } else {
            false
        }
    }
    
    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                textFieldCoordinates = coordinates
            }
            .onPreviewKeyEvent { keyEvent ->
                // 只有在弹窗可见且是特定导航键时才拦截
                if (isPopupVisible && keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.Escape, Key.Tab -> {
                            handleKeyEvent(keyEvent)
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // 文件列表弹窗
        if (isPopupVisible && searchResults.isNotEmpty()) {
            SimpleFilePopup(
                results = searchResults,
                selectedIndex = selectedIndex,
                searchQuery = searchQuery,
                scrollState = scrollState,
                popupOffset = popupOffset,
                onItemSelected = { selectedFile ->
                    val currentText = textFieldValue.text
                    val fileName = selectedFile.name // 只使用文件名，不使用完整路径
                    val replacement = "@$fileName" // 创建超链接格式
                    
                    val replaceStart = atPosition
                    val replaceEnd = atPosition + 1 + searchQuery.length
                    
                    val newText = currentText.replaceRange(replaceStart, replaceEnd, replacement)
                    val newPosition = replaceStart + replacement.length
                    
                    onTextChange(TextFieldValue(
                        text = newText,
                        selection = TextRange(newPosition)
                    ))
                    
                    // 选择完成后立即关闭弹窗
                    isPopupVisible = false
                    searchResults = emptyList()
                },
                onDismiss = { isPopupVisible = false },
                onKeyEvent = handleKeyEvent,
                onPopupBoundsChanged = { bounds ->
                    popupBounds = bounds
                }
            )
        }
    }
}

/**
 * 基于TextLayoutResult的精确字符位置计算
 * 
 * @deprecated 使用 TextPositionUtils.calculateAbsoluteCharacterPosition 替代
 */
fun calculatePrecisePopupOffset(
    textFieldCoordinates: LayoutCoordinates,
    textLayoutResult: androidx.compose.ui.text.TextLayoutResult?,
    atPosition: Int,
    density: androidx.compose.ui.unit.Density
): Offset {
    return TextPositionUtils.calculateAbsoluteCharacterPosition(
        textLayoutResult = textLayoutResult,
        characterPosition = atPosition,
        inputFieldCoordinates = textFieldCoordinates,
        density = density
    )
}

/**
 * 动态位置计算函数 - 兼容性回退版本
 * 
 * @deprecated 使用 TextPositionUtils.calculateCharacterPosition 替代
 */
fun calculatePopupOffset(
    textFieldCoordinates: LayoutCoordinates,
    textFieldValue: TextFieldValue,
    atPosition: Int,
    density: androidx.compose.ui.unit.Density
): Offset {
    return TextPositionUtils.calculateCharacterPosition(null, atPosition)
}

/**
 * 简化文件弹窗 - 支持动态位置偏移
 */
@Composable
fun SimpleFilePopup(
    results: List<IndexedFileInfo>,
    selectedIndex: Int,
    searchQuery: String,
    scrollState: LazyListState,
    popupOffset: Offset, // 这里的 Offset 现在表示锚点位置
    onItemSelected: (IndexedFileInfo) -> Unit,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
    onPopupBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    // 追踪弹窗边界
    var popupBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false, // 不抢夺焦点，让输入框保持焦点
            dismissOnBackPress = false, // 通过ESC键手动控制
            dismissOnClickOutside = true
        ),
        popupPositionProvider = remember(popupOffset) {
            AtSymbolPopupPositionProvider(popupOffset)
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
                    onPopupBoundsChanged?.invoke(bounds)
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
 * Cursor风格文件项组件 - 支持二级悬浮
 */
@Composable
fun JewelFileItem(
    file: IndexedFileInfo,
    isSelected: Boolean,
    searchQuery: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    anchorBounds: androidx.compose.ui.geometry.Rect? = null
) {
    // 悬停状态管理
    var isHovered by remember { mutableStateOf(false) }
    
    // 使用Box支持嵌套的二级悬浮
    Box(modifier = modifier.fillMaxWidth()) {
        // 主文件项
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(
                    color = when {
                        isSelected -> JewelTheme.globalColors.borders.focused.copy(alpha = 0.25f)
                        isHovered -> JewelTheme.globalColors.borders.normal.copy(alpha = 0.08f)
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    }
                )
                .then(
                    if (isSelected) {
                        Modifier.border(
                            1.dp,
                            JewelTheme.globalColors.borders.focused.copy(alpha = 0.6f),
                            RoundedCornerShape(4.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 4.dp),  // 减小垂直间距
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)  // 减小间距
        ) {
            // 文件图标 - 区分文件夹和文件，缩小图标大小
            Text(
                text = if (file.isDirectory) "📁" else getFileIcon(file.name),
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            
            // 文件信息 - Cursor 风格（水平布局）
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)  // 减小文件名和路径间距
            ) {
                // 主文件名（突出显示 + 搜索高亮）
                val highlightedFileName = if (searchQuery.isNotEmpty()) {
                    buildAnnotatedString {
                        val fileName = file.name
                        val queryIndex = fileName.indexOf(searchQuery, ignoreCase = true)
                        if (queryIndex >= 0) {
                            // 高亮匹配的部分
                            append(fileName.substring(0, queryIndex))
                            withStyle(
                                SpanStyle(
                                    background = JewelTheme.globalColors.borders.focused.copy(alpha = 0.3f),
                                    color = JewelTheme.globalColors.text.normal
                                )
                            ) {
                                append(fileName.substring(queryIndex, queryIndex + searchQuery.length))
                            }
                            append(fileName.substring(queryIndex + searchQuery.length))
                        } else {
                            append(fileName)
                        }
                    }
                } else {
                    buildAnnotatedString { append(file.name) }
                }
                
                Text(
                    text = highlightedFileName,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,  // 缩小字体
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = if (isSelected) 
                            JewelTheme.globalColors.borders.focused
                        else 
                            JewelTheme.globalColors.text.normal
                    )
                )
                
                // 路径信息（水平显示，变小变淡，优先显示结尾）
                if (file.relativePath.isNotEmpty()) {
                    val displayPath = file.relativePath.removeSuffix("/${file.name}").removeSuffix(file.name)
                    if (displayPath.isNotEmpty()) {
                        val truncatedPath = if (displayPath.length > 40) {
                            "..." + displayPath.takeLast(37)
                        } else {
                            displayPath
                        }
                        
                        Text(
                            text = truncatedPath,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 9.sp,  // 进一步缩小路径字体
                                color = JewelTheme.globalColors.text.disabled.copy(alpha = 0.6f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
        
        // 二级悬浮：文件层级树（当选中时显示）
        // 暂时禁用二级弹窗以避免点击冲突，专注于主要功能
        // if (isSelected && file.relativePath.isNotEmpty()) {
        //     FileHierarchyPopup(
        //         targetFile = file,
        //         onDismiss = { /* 自动关闭 */ },
        //         anchorBounds = anchorBounds // 传递主弹窗边界信息
        //     )
        // }
    }
}

/**
 * 获取文件图标 - 优化的文件类型识别
 */
private fun getFileIcon(fileName: String): String {
    return when (fileName.substringAfterLast('.', "")) {
        "kt" -> "🟢"
        "java" -> "☕"
        "js", "ts", "tsx", "jsx" -> "🟨"
        "py" -> "🐍"
        "md" -> "📝"
        "json" -> "📋"
        "xml", "html", "htm" -> "📄"
        "gradle", "kts" -> "🐘"
        "properties", "yml", "yaml" -> "⚙️"
        "css", "scss", "sass" -> "🎨"
        "png", "jpg", "jpeg", "gif", "svg" -> "🖼️"
        "pdf" -> "📕"
        "txt" -> "📄"
        "sh", "bat", "cmd" -> "⚡"
        else -> "📄"
    }
}

/**
 * Add Context 按钮专用文件弹窗 - 使用专用的定位提供器
 */
@Composable
fun ButtonFilePopup(
    results: List<IndexedFileInfo>,
    selectedIndex: Int,
    searchQuery: String,
    scrollState: LazyListState,
    popupOffset: Offset, // 这里的 Offset 现在表示按钮位置
    onItemSelected: (IndexedFileInfo) -> Unit,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
    onPopupBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    // 追踪弹窗边界
    var popupBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false, // 不抢夺焦点，让输入框保持焦点
            dismissOnBackPress = false, // 通过ESC键手动控制
            dismissOnClickOutside = true
        ),
        popupPositionProvider = remember(popupOffset) {
            ButtonPopupPositionProvider(popupOffset)
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
                    onPopupBoundsChanged?.invoke(bounds)
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