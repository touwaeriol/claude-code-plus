/*
 * SimpleInlineFileReference.kt
 * 
 * 简化的内联文件引用组件 - 直接在 @ 符号上方悬浮显示文件列表
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.IndexedFileInfo
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import kotlinx.coroutines.delay

/**
 * 简化内联文件引用处理器
 * 直接在 @ 符号上方悬浮显示文件列表，无需二次选择
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
    
    // 检测 @ 符号和搜索查询
    LaunchedEffect(textFieldValue.text, textFieldValue.selection.start) {
        if (!enabled || fileIndexService == null) {
            isPopupVisible = false
            return@LaunchedEffect
        }
        
        val cursorPos = textFieldValue.selection.start
        val atResult = findAtSymbolWithQuery(textFieldValue.text, cursorPos)
        
        if (atResult != null) {
            val (atPos, query) = atResult
            atPosition = atPos
            searchQuery = query
            selectedIndex = 0
            
            // 防抖延迟
            delay(100)
            
            try {
                val results = if (query.isEmpty()) {
                    // 显示最近文件
                    fileIndexService.getRecentFiles(10)
                } else {
                    // 搜索文件
                    fileIndexService.searchFiles(query, 10)
                }
                searchResults = results
                isPopupVisible = results.isNotEmpty()
            } catch (e: Exception) {
                println("[SimpleInlineFileReference] 搜索失败: ${e.message}")
                searchResults = emptyList()
                isPopupVisible = false
            }
        } else {
            isPopupVisible = false
            searchResults = emptyList()
        }
    }
    
    // 键盘事件处理
    val handleKeyEvent: (KeyEvent) -> Boolean = { keyEvent ->
        if (isPopupVisible && keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.DirectionUp -> {
                    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                    true
                }
                Key.DirectionDown -> {
                    selectedIndex = (selectedIndex + 1).coerceAtMost(searchResults.size - 1)
                    true
                }
                Key.Enter -> {
                    if (selectedIndex in searchResults.indices) {
                        val selectedFile = searchResults[selectedIndex]
                        insertFileReference(
                            textFieldValue = textFieldValue,
                            onTextChange = onTextChange,
                            file = selectedFile,
                            atPosition = atPosition,
                            queryLength = searchQuery.length
                        )
                        isPopupVisible = false
                    }
                    true
                }
                Key.Escape -> {
                    isPopupVisible = false
                    true
                }
                else -> false
            }
        } else {
            false
        }
    }
    
    Box(modifier = modifier) {
        // 文件列表弹窗
        if (isPopupVisible && searchResults.isNotEmpty()) {
            SimpleFilePopup(
                results = searchResults,
                selectedIndex = selectedIndex,
                searchQuery = searchQuery,
                onItemSelected = { selectedFile ->
                    insertFileReference(
                        textFieldValue = textFieldValue,
                        onTextChange = onTextChange,
                        file = selectedFile,
                        atPosition = atPosition,
                        queryLength = searchQuery.length
                    )
                    isPopupVisible = false
                },
                onDismiss = { isPopupVisible = false },
                onKeyEvent = handleKeyEvent
            )
        }
    }
}

/**
 * 简化文件弹窗 - 悬浮在 @ 符号上方，不覆盖输入内容
 */
@Composable
fun SimpleFilePopup(
    results: List<IndexedFileInfo>,
    selectedIndex: Int,
    searchQuery: String,
    onItemSelected: (IndexedFileInfo) -> Unit,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        alignment = Alignment.TopStart,
        offset = androidx.compose.ui.unit.IntOffset(0, -330) // 向上偏移，避免覆盖输入框
    ) {
        Box(
            modifier = modifier
                .width(360.dp)
                .heightIn(max = 320.dp)
                .shadow(12.dp, RoundedCornerShape(8.dp))
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
                .onPreviewKeyEvent(onKeyEvent)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(results) { index, file ->
                    SimpleFileItem(
                        file = file,
                        isSelected = index == selectedIndex,
                        searchQuery = searchQuery,
                        onClick = { onItemSelected(file) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 简化文件项组件 - Cursor 风格
 */
@Composable
fun SimpleFileItem(
    file: IndexedFileInfo,
    isSelected: Boolean,
    searchQuery: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPathHover by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .clickable { onClick() }
            .background(
                if (isSelected) 
                    JewelTheme.globalColors.borders.focused.copy(alpha = 0.1f)
                else 
                    JewelTheme.globalColors.panelBackground
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 文件图标
            Text(
                text = file.getIcon(),
                style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp)
            )
            
            // 文件信息 - Cursor 风格
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 主文件名（突出显示 + 搜索高亮）
                val highlightedFileName = if (searchQuery.isNotEmpty()) {
                    createHighlightedText(file.name, searchQuery)
                } else {
                    buildAnnotatedString { append(file.name) }
                }
                
                Text(
                    text = highlightedFileName,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 14.sp,
                        color = if (isSelected) 
                            JewelTheme.globalColors.borders.focused
                        else 
                            JewelTheme.globalColors.text.normal
                    )
                )
                
                // 路径信息（缩小显示）
                if (file.relativePath.isNotEmpty()) {
                    val displayPath = formatPathForDisplay(file.relativePath, file.name)
                    if (displayPath.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .hoverable(MutableInteractionSource())
                        ) {
                            Text(
                                text = displayPath,
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 11.sp,
                                    color = JewelTheme.globalColors.text.disabled
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 查找 @ 符号及其后的查询文本
 */
private fun findAtSymbolWithQuery(text: String, cursorPos: Int): Pair<Int, String>? {
    if (cursorPos <= 0 || text.isEmpty()) return null
    
    // 从光标位置向前搜索 @ 符号
    for (i in (cursorPos - 1) downTo 0) {
        val char = text[i]
        
        when {
            char == '@' -> {
                // 检查 @ 前是否是空白或文本开头
                val isValidAtStart = i == 0 || text[i - 1].isWhitespace()
                if (!isValidAtStart) continue
                
                // 提取查询文本
                val queryText = if (cursorPos > i + 1) {
                    text.substring(i + 1, cursorPos)
                } else {
                    ""
                }
                
                // 验证查询文本不包含空白字符
                if (queryText.any { it.isWhitespace() }) {
                    return null
                }
                
                return Pair(i, queryText)
            }
            char.isWhitespace() -> {
                // 遇到空白字符，停止搜索
                return null
            }
        }
    }
    
    return null
}

/**
 * 插入文件引用到文本中
 */
private fun insertFileReference(
    textFieldValue: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    file: IndexedFileInfo,
    atPosition: Int,
    queryLength: Int
) {
    val currentText = textFieldValue.text
    val fileName = file.relativePath.ifEmpty { file.name }
    val replacement = "@$fileName"
    
    // 计算替换范围：从 @ 位置到 @ + 查询长度
    val replaceStart = atPosition
    val replaceEnd = atPosition + 1 + queryLength
    
    val newText = currentText.replaceRange(replaceStart, replaceEnd, replacement)
    val newPosition = replaceStart + replacement.length
    
    onTextChange(TextFieldValue(
        text = newText,
        selection = androidx.compose.ui.text.TextRange(newPosition)
    ))
}

/**
 * 创建高亮文本
 */
private fun createHighlightedText(text: String, searchQuery: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = searchQuery.lowercase()
        
        var currentIndex = 0
        var searchIndex = lowerText.indexOf(lowerQuery, currentIndex)
        
        while (searchIndex != -1) {
            // 添加前面的普通文本
            if (searchIndex > currentIndex) {
                append(text.substring(currentIndex, searchIndex))
            }
            
            // 添加高亮的匹配文本
            withStyle(
                style = SpanStyle(
                    background = JewelTheme.globalColors.borders.focused.copy(alpha = 0.3f),
                    color = JewelTheme.globalColors.text.normal
                )
            ) {
                append(text.substring(searchIndex, searchIndex + searchQuery.length))
            }
            
            currentIndex = searchIndex + searchQuery.length
            searchIndex = lowerText.indexOf(lowerQuery, currentIndex)
        }
        
        // 添加剩余的普通文本
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}

/**
 * 格式化路径显示 - 优化版，优先显示路径结尾
 * 将文件路径转换为适合显示的格式
 */
private fun formatPathForDisplay(relativePath: String, fileName: String): String {
    if (relativePath.isEmpty()) return ""
    
    // 移除文件名本身，只保留目录路径
    val directoryPath = relativePath.removeSuffix("/$fileName").removeSuffix(fileName)
    if (directoryPath.isEmpty()) return ""
    
    // 将路径分割成层级
    val pathParts = directoryPath.split("/").filter { it.isNotEmpty() }
    if (pathParts.isEmpty()) return ""
    
    // 根据路径长度决定显示方式 - 优化版，优先显示路径结尾
    return when {
        pathParts.size <= 2 -> {
            // 短路径：直接显示完整路径
            pathParts.joinToString(" > ")
        }
        pathParts.size <= 4 -> {
            // 中等路径：显示最后几层
            pathParts.takeLast(3).joinToString(" > ")
        }
        else -> {
            // 长路径：优先显示结尾部分，隐藏开头
            "... > ${pathParts.takeLast(3).joinToString(" > ")}"
        }
    }
}