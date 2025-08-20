/*
 * InlineFileReference.kt
 * 
 * Cursor 风格的内联文件引用组件
 * 支持 @file/path 实时搜索和键盘导航
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.IndexedFileInfo
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 内联文件引用状态
 */
data class InlineReferenceState(
    val isActive: Boolean = false,
    val atPosition: Int = -1,
    val searchQuery: String = "",
    val selectedIndex: Int = 0
)

/**
 * 内联文件引用结果
 */
data class InlineFileReferenceResult(
    val file: IndexedFileInfo,
    val replaceStart: Int,
    val replaceEnd: Int
)

/**
 * Cursor 风格的内联文件引用组件
 * 
 * @param textFieldValue 当前输入框的值
 * @param onTextChange 文本变化回调
 * @param fileIndexService 文件索引服务
 * @param onFileSelected 文件选择回调
 * @param enabled 是否启用
 */
@Composable
fun InlineFileReferenceHandler(
    textFieldValue: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    fileIndexService: FileIndexService?,
    onFileSelected: (InlineFileReferenceResult) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var referenceState by remember { mutableStateOf(InlineReferenceState()) }
    var searchResults by remember { mutableStateOf<List<IndexedFileInfo>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    
    // 检测 @ 符号和搜索查询
    LaunchedEffect(textFieldValue.text, textFieldValue.selection.start) {
        if (!enabled || fileIndexService == null) {
            referenceState = InlineReferenceState()
            return@LaunchedEffect
        }
        
        val cursorPos = textFieldValue.selection.start
        val atResult = findAtSymbolWithQuery(textFieldValue.text, cursorPos)
        
        if (atResult != null) {
            val (atPos, query) = atResult
            
            // 更新引用状态
            referenceState = InlineReferenceState(
                isActive = true,
                atPosition = atPos,
                searchQuery = query,
                selectedIndex = 0
            )
            
            // 执行搜索
            if (query.isNotEmpty()) {
                isSearching = true
                delay(150) // 防抖
                try {
                    val results = fileIndexService.searchFiles(
                        query = query,
                        maxResults = 10
                    )
                    searchResults = results
                } catch (e: Exception) {
                    searchResults = emptyList()
                    println("[InlineFileReference] 搜索失败: ${e.message}")
                } finally {
                    isSearching = false
                }
            } else {
                // 空查询时显示最近文件
                try {
                    val results = fileIndexService.getRecentFiles(10)
                    searchResults = results
                } catch (e: Exception) {
                    searchResults = emptyList()
                }
            }
        } else {
            // 没有检测到 @ 符号，重置状态
            referenceState = InlineReferenceState()
            searchResults = emptyList()
        }
    }
    
    Box(modifier = modifier) {
        // 显示搜索结果下拉框
        if (referenceState.isActive && searchResults.isNotEmpty()) {
            InlineReferencePopup(
                results = searchResults,
                selectedIndex = referenceState.selectedIndex,
                isSearching = isSearching,
                searchQuery = referenceState.searchQuery,
                onItemSelected = { selectedFile ->
                    val result = InlineFileReferenceResult(
                        file = selectedFile,
                        replaceStart = referenceState.atPosition,
                        replaceEnd = referenceState.atPosition + 1 + referenceState.searchQuery.length
                    )
                    onFileSelected(result)
                    referenceState = InlineReferenceState()
                },
                onDismiss = {
                    referenceState = InlineReferenceState()
                },
                onNavigate = { direction ->
                    val newIndex = when (direction) {
                        NavigationDirection.UP -> 
                            (referenceState.selectedIndex - 1).coerceAtLeast(0)
                        NavigationDirection.DOWN -> 
                            (referenceState.selectedIndex + 1).coerceAtMost(searchResults.size - 1)
                    }
                    referenceState = referenceState.copy(selectedIndex = newIndex)
                }
            )
        }
    }
}

/**
 * 导航方向
 */
enum class NavigationDirection {
    UP, DOWN
}

/**
 * 内联引用弹出框
 */
@Composable
private fun InlineReferencePopup(
    results: List<IndexedFileInfo>,
    selectedIndex: Int,
    isSearching: Boolean,
    searchQuery: String,
    onItemSelected: (IndexedFileInfo) -> Unit,
    onDismiss: () -> Unit,
    onNavigate: (NavigationDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    // 键盘事件处理
    val keyEventHandler: (KeyEvent) -> Boolean = { keyEvent ->
        when {
            keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown -> {
                onDismiss()
                true
            }
            keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                onNavigate(NavigationDirection.UP)
                true
            }
            keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                onNavigate(NavigationDirection.DOWN)
                true
            }
            keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown -> {
                if (selectedIndex in results.indices) {
                    onItemSelected(results[selectedIndex])
                }
                true
            }
            else -> false
        }
    }
    
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = modifier
                .width(320.dp)
                .heightIn(max = 240.dp)
                .shadow(8.dp, RoundedCornerShape(8.dp))
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
                .onPreviewKeyEvent(keyEventHandler)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 标题栏
                if (searchQuery.isNotEmpty() || isSearching) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isSearching) "搜索中..." else "搜索: @$searchQuery",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 12.sp,
                                color = JewelTheme.globalColors.text.disabled
                            )
                        )
                        
                        if (results.isNotEmpty()) {
                            Text(
                                text = "(${results.size})",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 11.sp,
                                    color = JewelTheme.globalColors.text.disabled
                                )
                            )
                        }
                    }
                    
                    // 分隔线 - 使用 Jewel 的 Divider 替代
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(JewelTheme.globalColors.borders.normal)
                    )
                }
                
                // 结果列表
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    itemsIndexed(results) { index, file ->
                        InlineFileResultItem(
                            file = file,
                            isSelected = index == selectedIndex,
                            searchQuery = searchQuery,
                            onClick = { onItemSelected(file) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // 空状态
                if (results.isEmpty() && !isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "输入文件名开始搜索" else "未找到匹配文件",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 12.sp,
                                color = JewelTheme.globalColors.text.disabled
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 文件结果项
 */
@Composable
private fun InlineFileResultItem(
    file: IndexedFileInfo,
    isSelected: Boolean,
    searchQuery: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable { onClick() }
            .background(
                if (isSelected) 
                    JewelTheme.globalColors.borders.focused.copy(alpha = 0.1f)
                else 
                    JewelTheme.globalColors.panelBackground,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 文件图标
            Text(
                text = file.getIcon(),
                style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
            )
            
            // 文件信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = file.name,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 13.sp,
                        color = if (isSelected) 
                            JewelTheme.globalColors.borders.focused
                        else 
                            JewelTheme.globalColors.text.normal
                    )
                )
                
                if (file.relativePath.isNotEmpty()) {
                    Text(
                        text = file.relativePath,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
            }
        }
    }
}

/**
 * 查找 @ 符号及其后的查询文本
 * 
 * @param text 文本内容
 * @param cursorPos 光标位置
 * @return Pair(atPosition, queryText) 或 null
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
                val queryStart = i + 1
                val queryEnd = cursorPos
                val queryText = if (queryEnd > queryStart) {
                    text.substring(queryStart, queryEnd)
                } else {
                    ""
                }
                
                // 验证查询文本是否有效（不包含空白字符）
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