/*
 * SimpleInlineFileReferenceClean.kt
 * 
 * 重构后的简化文件引用组件 - 移除重复代码，使用统一业务组件
 * 完全基于Jewel组件实现
 */

package com.claudecodeplus.ui.jewel.components

import com.claudecodeplus.core.logging.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.claudecodeplus.ui.services.IndexedFileInfo
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.jewel.components.business.*
import com.claudecodeplus.ui.jewel.components.createMarkdownContextLink
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.SimpleListItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 文件项选择类型枚举
 * 用于区分不同的选择状态和相应的视觉样式
 */
enum class FileItemSelectionType {
    NONE,               // 无选中
    PRIMARY,            // 主要选中（键盘或唯一选中）
    SECONDARY           // 次要选中（键盘模式下的鼠标位置）
}

/**
 * 根据选择类型获取对应的背景颜色
 */
@Composable
fun getSelectionBackground(type: FileItemSelectionType): androidx.compose.ui.graphics.Color {
    return when (type) {
        FileItemSelectionType.NONE -> androidx.compose.ui.graphics.Color.Transparent
        // 📌 增强视觉反馈：提高主要选中的透明度，使其更明显
        FileItemSelectionType.PRIMARY -> JewelTheme.globalColors.borders.focused.copy(alpha = 0.35f)     // 主要选中：更明显的高亮
        FileItemSelectionType.SECONDARY -> JewelTheme.globalColors.borders.focused.copy(alpha = 0.18f)  // 次要选中：稍微增强但保持淡化
    }
}

/**
 * 计算文件项的选择类型
 * 根据当前索引、键盘选中索引、鼠标悬停索引和键盘模式状态来判断
 */
fun getItemSelectionType(
    index: Int,
    keyboardIndex: Int,
    mouseIndex: Int,
    isKeyboardMode: Boolean
): FileItemSelectionType {
    return when {
        // 键盘模式下（用户按了上下键）
        isKeyboardMode -> {
            when {
                index == keyboardIndex -> FileItemSelectionType.PRIMARY    // 键盘选中：正常高亮
                index == mouseIndex -> FileItemSelectionType.SECONDARY     // 鼠标位置：淡化显示
                else -> FileItemSelectionType.NONE
            }
        }
        // 鼠标模式下（默认状态或鼠标移动后）
        else -> {
            when {
                // 有鼠标悬停
                index == mouseIndex -> FileItemSelectionType.PRIMARY
                // 只有键盘选择（初始状态，没有鼠标悬停）
                index == keyboardIndex && mouseIndex == -1 -> FileItemSelectionType.PRIMARY
                else -> FileItemSelectionType.NONE
            }
        }
    }
}

/**
 * 简化的内联文件引用处理器 - 使用业务组件封装
 * 
 * 使用新的业务逻辑组件，避免UI中的复杂逻辑
 */
@Composable
fun SimpleInlineFileReferenceHandler(
    textFieldValue: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    fileIndexService: FileIndexService?,
    enabled: Boolean = true,
    textLayoutResult: androidx.compose.ui.text.TextLayoutResult? = null,
    modifier: Modifier = Modifier
) {
    // 使用业务组件管理上下文选择
    val contextManager = rememberContextSelectionManager(
        fileIndexService = fileIndexService,
        onContextAdd = { /* @ 符号模式不需要添加到上下文列表 */ },
        onTextUpdate = onTextChange
    )
    
    // 管理弹窗状态
    val (scrollState, eventHandler) = rememberFilePopupState()
    var selectedIndex by remember { mutableStateOf(0) }
    
    // 使用业务组件检测@ 符号查询
    val selectionState = rememberContextSelectionState(
        textFieldValue = textFieldValue,
        contextManager = contextManager,
        enabled = enabled
    )
    
    // 计算弹窗位置
    val popupOffset = remember(textLayoutResult, selectionState.atPosition) {
        if (selectionState.atPosition != null) {
            TextPositionUtils.safeCalculateCharacterPosition(
                textLayoutResult = textLayoutResult,
                characterPosition = selectionState.atPosition,
                textLength = textFieldValue.text.length
            )
        } else {
            Offset.Zero
        }
    }
    
    // 显示@ 符号弹窗
    if (selectionState.isAtSymbolActive && selectionState.searchResults.isNotEmpty()) {
        SimpleFilePopup(
            results = selectionState.searchResults,
            selectedIndex = selectedIndex,
            searchQuery = selectionState.searchQuery,
            scrollState = scrollState,
            popupOffset = popupOffset,
            onItemSelected = { file ->
                if (selectionState.atPosition != null) {
                    contextManager.handleAtSymbolFileSelection(
                        file = file,
                        currentText = textFieldValue,
                        atPosition = selectionState.atPosition
                    )
                    selectedIndex = 0 // 重置选择索引
                }
            },
            onDismiss = { 
                selectedIndex = 0
            },
            onKeyEvent = { keyEvent ->
                logD("🎹 [SimpleInlineFileReference] @ 符号弹窗接收键盘事件: key=${keyEvent.key}, selectedIndex=$selectedIndex, resultsSize=${selectionState.searchResults.size}")

                val handled = eventHandler.handleKeyEvent(
                    keyEvent = keyEvent,
                    selectedIndex = selectedIndex,
                    resultsSize = selectionState.searchResults.size,
                    onIndexChange = { newIndex ->
                        logD("🎹 [SimpleInlineFileReference] ✅ @ 符号弹窗更新选中索引: $selectedIndex → $newIndex")
                        selectedIndex = newIndex
                    },
                    onItemSelect = {
                        logD("🎹 [SimpleInlineFileReference] ✅ @ 符号弹窗选择文件: index=$selectedIndex")
                        if (selectedIndex in selectionState.searchResults.indices && selectionState.atPosition != null) {
                            val selectedFile = selectionState.searchResults[selectedIndex]
                            logD("🎹 [SimpleInlineFileReference] 选择的文件: ${selectedFile.relativePath}")
                            contextManager.handleAtSymbolFileSelection(
                                file = selectedFile,
                                currentText = textFieldValue,
                                atPosition = selectionState.atPosition
                            )
                            selectedIndex = 0
                        } else {
                            logD("🎹 [SimpleInlineFileReference] ❌ 无效选择: index=$selectedIndex, resultsSize=${selectionState.searchResults.size}, atPosition=${selectionState.atPosition}")
                        }
                    },
                    onDismiss = {
                        logD("🎹 [SimpleInlineFileReference] ❌ @ 符号弹窗关闭")
                        selectedIndex = 0
                    }
                )

                logD("🎹 [SimpleInlineFileReference] @ 符号键盘事件处理结果: $handled")
                handled
            }
        )
    }
}

/**
 * @ 符号专用文件弹窗 - 使用统一业务组件
 */
@Composable
fun SimpleFilePopup(
    results: List<IndexedFileInfo>,
    selectedIndex: Int,
    searchQuery: String,
    scrollState: LazyListState,
    popupOffset: Offset,
    onItemSelected: (IndexedFileInfo) -> Unit,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
    onPopupBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    logD("[SimpleFilePopup] @ 符号弹窗被调用")
    val config = FilePopupConfig(
        type = FilePopupType.AT_SYMBOL,
        anchorOffset = popupOffset
    )
    
    UnifiedFilePopup(
        results = results,
        selectedIndex = selectedIndex,
        searchQuery = searchQuery,
        scrollState = scrollState,
        config = config,
        onItemSelected = onItemSelected,
        onDismiss = onDismiss,
        onKeyEvent = onKeyEvent,
        modifier = modifier,
        onPopupBoundsChanged = onPopupBoundsChanged,
        onSearchQueryChange = null, // 明确指定不要搜索输入框
        searchInputValue = "" // 明确指定空值
    )
}

/**
 * Add Context 按钮专用文件弹窗 - 使用统一业务组件
 */
@Composable
fun ButtonFilePopup(
    results: List<IndexedFileInfo>,
    selectedIndex: Int,
    hoveredIndex: Int = -1,
    searchQuery: String,
    scrollState: LazyListState,
    popupOffset: Offset,
    isIndexing: Boolean = false,
    onItemSelected: (IndexedFileInfo) -> Unit,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    onItemHover: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onPopupBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    // 新增搜索相关参数
    onSearchQueryChange: ((String) -> Unit)? = null,
    searchInputValue: String = searchQuery,
    // 键盘模式状态
    isKeyboardMode: Boolean = false
) {
    logD("[ButtonFilePopup] Add Context 按钮弹窗被调用")
    val config = FilePopupConfig(
        type = FilePopupType.ADD_CONTEXT,
        anchorOffset = popupOffset
    )
    
    UnifiedFilePopup(
        results = results,
        selectedIndex = selectedIndex,
        hoveredIndex = hoveredIndex,
        searchQuery = searchQuery,
        scrollState = scrollState,
        config = config,
        isIndexing = isIndexing,
        onItemSelected = onItemSelected,
        onDismiss = onDismiss,
        onKeyEvent = onKeyEvent,
        onItemHover = onItemHover,
        modifier = modifier,
        onPopupBoundsChanged = onPopupBoundsChanged,
        onSearchQueryChange = onSearchQueryChange,
        searchInputValue = searchInputValue,
        isKeyboardMode = isKeyboardMode
    )
}

/**
 * Jewel 风格的文件项组件 - 使用标准SimpleListItem实现
 */
@Composable
fun JewelFileItem(
    file: IndexedFileInfo,
    selectionType: FileItemSelectionType,
    searchQuery: String,
    onClick: () -> Unit,
    onHover: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    anchorBounds: androidx.compose.ui.geometry.Rect? = null
) {
    // 交互源和悬停状态管理
    val interactionSource = remember { MutableInteractionSource() }
    val isLocallyHovered by interactionSource.collectIsHoveredAsState()
    
    // 监听悬停状态变化
    LaunchedEffect(isLocallyHovered) {
        onHover?.invoke(isLocallyHovered)
    }
    
    // 使用Box支持嵌套的二级悬浮
    Box(modifier = modifier.fillMaxWidth()) {
        // 使用简化的Row布局代替复杂的SimpleListItem
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(
                    color = getSelectionBackground(selectionType)
                )
                // 📌 增强视觉反馈：为主要选中项添加明显的边框
                .then(
                    if (selectionType == FileItemSelectionType.PRIMARY) {
                        Modifier.border(
                            width = 2.dp,
                            color = JewelTheme.globalColors.borders.focused,
                            shape = RoundedCornerShape(4.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .hoverable(interactionSource)
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 文件图标
            Text(
                text = if (file.isDirectory) "📁" else getFileIcon(file.name),
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            
            // 文件信息
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 主文件名（搜索高亮）
                Text(
                    text = buildHighlightedText(file.name, searchQuery),
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 13.sp,
                        color = JewelTheme.globalColors.text.normal // 统一使用正常文本颜色，确保可见性
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                
                // 路径显示（精简版）
                if (file.relativePath.isNotEmpty()) {
                    val displayPath = file.relativePath.removeSuffix("/${file.name}").removeSuffix(file.name)
                    if (displayPath.isNotEmpty()) {
                        Text(
                            text = displayPath.split("/").takeLast(2).joinToString("/"),
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 10.sp,
                                color = JewelTheme.globalColors.text.disabled
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
        
        // 二级层级悬浮弹窗 - 保持原有功能
        val isSelected = selectionType != FileItemSelectionType.NONE
        if (isLocallyHovered && isSelected && anchorBounds != null) {
            FileHierarchyPopup(
                targetFile = file,
                onDismiss = { /* 悬停状态由InteractionSource管理，无需手动设置 */ },
                anchorBounds = anchorBounds
            )
        }
    }
}

/**
 * 获取文件图标 - 使用简单的表情符号代替复杂的图标路径
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
 * 获取主题感知的高亮颜色
 * 根据当前主题选择合适的高亮颜色，确保在不同主题下关键词都清晰可见
 */
@Composable
private fun getThemeAwareHighlightColor(): androidx.compose.ui.graphics.Color {
    val isDarkTheme = JewelTheme.isDark
    return if (isDarkTheme) {
        // 暗色主题：使用亮橙色，在深色背景上清晰可见
        androidx.compose.ui.graphics.Color(0xFFFFA500)
    } else {
        // 亮色主题：使用深蓝色，在浅色背景上明显突出
        androidx.compose.ui.graphics.Color(0xFF0066CC)
    }
}

/**
 * 构建带高亮的文本 - 主题感知版本
 */
@Composable
private fun buildHighlightedText(fileName: String, searchQuery: String): androidx.compose.ui.text.AnnotatedString {
    if (searchQuery.isEmpty()) {
        return androidx.compose.ui.text.AnnotatedString(fileName)
    }
    
    return buildAnnotatedString {
        val index = fileName.indexOf(searchQuery, ignoreCase = true)
        if (index >= 0) {
            append(fileName.substring(0, index))
            withStyle(SpanStyle(color = getThemeAwareHighlightColor())) {
                append(fileName.substring(index, index + searchQuery.length))
            }
            append(fileName.substring(index + searchQuery.length))
        } else {
            append(fileName)
        }
    }
}

// 兼容性函数 - 保持API一致性
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
 * @deprecated 使用 TextPositionUtils.calculateAbsoluteCharacterPosition 替代
 */
fun calculatePrecisePopupOffset(
    textFieldCoordinates: androidx.compose.ui.layout.LayoutCoordinates,
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
 * @deprecated 使用 TextPositionUtils.calculateCharacterPosition 替代
 */
fun calculatePopupOffset(
    textFieldCoordinates: androidx.compose.ui.layout.LayoutCoordinates,
    textFieldValue: TextFieldValue,
    atPosition: Int,
    density: androidx.compose.ui.unit.Density
): Offset {
    return TextPositionUtils.calculateCharacterPosition(null, atPosition)
}
