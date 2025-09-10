/*
 * ChatInputContextSelectorPopup.kt
 * 
 * 现代化上下文选择器弹出组件
 * 支持文件搜索、Web引用和内联引用
 */

package com.claudecodeplus.ui.jewel.components.context

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.claudecodeplus.ui.models.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 现代化上下文选择器弹出组件
 * 专为聊天输入区域设计，提供文件和网页上下文选择
 */
@Composable
fun ChatInputContextSelectorPopup(
    onDismiss: () -> Unit,
    onContextSelect: (ContextReference) -> Unit,
    searchService: ContextSearchService,
    initialSearchQuery: String = "",  // 新增：初始搜索查询
    modifier: Modifier = Modifier
) {
    var selectionState by remember { mutableStateOf<ContextSelectionState>(
        if (initialSearchQuery.isNotEmpty()) ContextSelectionState.SelectingFile() else ContextSelectionState.SelectingType
    ) }
    var searchQuery by remember { mutableStateOf(TextFieldValue(initialSearchQuery)) }
    var searchResults by remember { mutableStateOf<List<FileSearchResult>>(emptyList()) }
    var webUrl by remember { mutableStateOf(TextFieldValue("")) }
    var selectedIndex by remember { mutableStateOf(-1) }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val config = remember { ContextSelectorConfig() }
    
    // 获取初始文件列表或进行初始搜索
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            if (initialSearchQuery.isNotEmpty()) {
                // 如果有初始搜索查询，直接进行搜索
                val results = searchService.searchFiles(initialSearchQuery, config.maxResults)
                searchResults = results
                selectedIndex = if (results.isNotEmpty()) 0 else -1
            } else {
                // 否则获取根文件列表
                val rootFiles = searchService.getRootFiles(config.maxResults)
                val results = rootFiles.map { 
                    FileSearchResult(it, 0, FileSearchResult.MatchType.PATH_MATCH) 
                }
                searchResults = results
                selectedIndex = if (results.isNotEmpty()) 0 else -1
            }
        } catch (e: Exception) {
            searchResults = emptyList()
            selectedIndex = -1
        } finally {
            isLoading = false
        }
    }
    
    // 搜索去抖动
    LaunchedEffect(searchQuery.text) {
        if (selectionState is ContextSelectionState.SelectingFile) {
            if (searchQuery.text.isNotEmpty()) {
                isLoading = true
                delay(config.searchDelayMs)
                try {
                    val results = searchService.searchFiles(searchQuery.text, config.maxResults)
                    searchResults = results
                    selectedIndex = if (results.isNotEmpty()) 0 else -1
                } catch (e: Exception) {
                    searchResults = emptyList()
                    selectedIndex = -1
                } finally {
                    isLoading = false
                }
            } else {
                // 空查询时显示根目录文件
                val rootFiles = searchService.getRootFiles(config.maxResults)
                val results = rootFiles.map { 
                    FileSearchResult(it, 0, FileSearchResult.MatchType.PATH_MATCH) 
                }
                searchResults = results
                selectedIndex = if (results.isNotEmpty()) 0 else -1
            }
        }
    }
    
    // 键盘导航
    val onKeyEvent: (KeyEvent) -> Boolean = { keyEvent ->
        when {
            keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown -> {
                onDismiss()
                true
            }
            keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                if (searchResults.isNotEmpty()) {
                    selectedIndex = if (selectedIndex <= 0) searchResults.size - 1 else selectedIndex - 1
                }
                true
            }
            keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                if (searchResults.isNotEmpty()) {
                    selectedIndex = if (selectedIndex >= searchResults.size - 1) 0 else selectedIndex + 1
                }
                true
            }
            keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown -> {
                when (selectionState) {
                    is ContextSelectionState.SelectingFile -> {
                        if (searchResults.isNotEmpty() && selectedIndex >= 0 && selectedIndex < searchResults.size) {
                            val selectedItem = searchResults[selectedIndex].item
                            val fileRef = ContextReference.FileReference(
                                path = selectedItem.relativePath,
                                fullPath = selectedItem.absolutePath
                            )
                            // 立即关闭弹窗，然后调用选择回调
                            onDismiss()
                            onContextSelect(fileRef)
                        }
                    }
                    is ContextSelectionState.SelectingWeb -> {
                        if (webUrl.text.isNotEmpty() && searchService.validateUrl(webUrl.text)) {
                            val webRef = ContextReference.WebReference(
                                url = webUrl.text,
                                title = null
                            )
                            // 立即关闭弹窗，然后调用选择回调
                            onDismiss()
                            onContextSelect(webRef)
                        }
                    }
                    else -> { /* 处理类型选择 */ }
                }
                true
            }
            keyEvent.key == Key.Tab && keyEvent.type == KeyEventType.KeyDown -> {
                // Tab 键切换类型
                selectionState = when (selectionState) {
                    is ContextSelectionState.SelectingFile -> ContextSelectionState.SelectingWeb(webUrl.text)
                    is ContextSelectionState.SelectingWeb -> ContextSelectionState.SelectingFile(searchQuery.text)
                    else -> ContextSelectionState.SelectingFile("")
                }
                true
            }
            else -> false
        }
    }
    
    // 请求焦点 - 延迟执行避免组合过程中请求焦点
    LaunchedEffect(Unit) {
        delay(100) // 等待组件完全渲染
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            println("[ChatInputContextSelectorPopup] 焦点请求失败: ${e.message}")
        }
    }
    
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = modifier
                .width(config.popupWidth.dp)
                .heightIn(max = config.popupMaxHeight.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .background(
                    JewelTheme.globalColors.panelBackground,
                    RoundedCornerShape(12.dp)
                )
                .border(
                    1.dp,
                    JewelTheme.globalColors.borders.normal,
                    RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
                .onPreviewKeyEvent(onKeyEvent)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 12.dp, 16.dp, 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Context",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 14.sp,
                            color = JewelTheme.globalColors.text.normal
                        )
                    )
                    
                    // 类型切换标签
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TypeTab(
                            text = "Files",
                            isSelected = selectionState is ContextSelectionState.SelectingFile,
                            onClick = { 
                                selectionState = ContextSelectionState.SelectingFile(searchQuery.text)
                                try {
                                    focusRequester.requestFocus()
                                } catch (e: Exception) {
                                    println("[ChatInputContextSelectorPopup] Files标签焦点请求失败: ${e.message}")
                                }
                            }
                        )
                        TypeTab(
                            text = "Web",
                            isSelected = selectionState is ContextSelectionState.SelectingWeb,
                            onClick = { 
                                selectionState = ContextSelectionState.SelectingWeb(webUrl.text)
                                try {
                                    focusRequester.requestFocus()
                                } catch (e: Exception) {
                                    println("[ChatInputContextSelectorPopup] Web标签焦点请求失败: ${e.message}")
                                }
                            }
                        )
                    }
                }
                
                // 搜索输入框
                SearchInputField(
                    value = when (selectionState) {
                        is ContextSelectionState.SelectingFile -> searchQuery
                        is ContextSelectionState.SelectingWeb -> webUrl
                        else -> TextFieldValue("")
                    },
                    onValueChange = { newValue ->
                        when (selectionState) {
                            is ContextSelectionState.SelectingFile -> {
                                searchQuery = newValue
                                selectionState = ContextSelectionState.SelectingFile(newValue.text)
                            }
                            is ContextSelectionState.SelectingWeb -> {
                                webUrl = newValue
                                selectionState = ContextSelectionState.SelectingWeb(newValue.text)
                            }
                            else -> {}
                        }
                    },
                    placeholder = when (selectionState) {
                        is ContextSelectionState.SelectingFile -> "Search files..."
                        is ContextSelectionState.SelectingWeb -> "Enter URL..."
                        else -> "Search..."
                    },
                    focusRequester = focusRequester,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 结果列表
                when (selectionState) {
                    is ContextSelectionState.SelectingFile -> {
                        FileSearchResults(
                            results = searchResults,
                            selectedIndex = selectedIndex,
                            searchQuery = searchQuery.text,
                            isLoading = isLoading,
                            onItemClick = { item ->
                                val fileRef = ContextReference.FileReference(
                                    path = item.relativePath,
                                    fullPath = item.absolutePath
                                )
                                // 立即关闭弹窗，然后调用选择回调
                                onDismiss()
                                onContextSelect(fileRef)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is ContextSelectionState.SelectingWeb -> {
                        WebInputSection(
                            url = webUrl.text,
                            isValid = searchService.validateUrl(webUrl.text),
                            onConfirm = {
                                if (searchService.validateUrl(webUrl.text)) {
                                    val webRef = ContextReference.WebReference(
                                        url = webUrl.text,
                                        title = null
                                    )
                                    // 立即关闭弹窗，然后调用选择回调
                                    onDismiss()
                                    onContextSelect(webRef)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {
                        // 类型选择（当前不需要）
                    }
                }
            }
        }
    }
}

/**
 * 类型标签组件
 */
@Composable
private fun TypeTab(
    text: String,
    isSelected: Boolean,
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
                    Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                color = if (isSelected) 
                    JewelTheme.globalColors.borders.focused
                else 
                    JewelTheme.globalColors.text.normal
            )
        )
    }
}

/**
 * 搜索输入框组件
 */
@Composable
private fun SearchInputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                JewelTheme.globalColors.borders.normal,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        if (value.text.isEmpty()) {
            Text(
                text = placeholder,
                style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.disabled
                )
            )
        }
        
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = JewelTheme.globalColors.text.normal,
                fontSize = JewelTheme.defaultTextStyle.fontSize
            ),
            cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}

/**
 * 文件搜索结果列表组件
 */
@Composable
private fun FileSearchResults(
    results: List<FileSearchResult>,
    selectedIndex: Int,
    searchQuery: String,
    isLoading: Boolean,
    onItemClick: (FileContextItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.heightIn(max = 300.dp)) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Searching...",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
            }
            results.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No files found",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(results.size) { index ->
                        val result = results[index]
                        FileResultItem(
                            item = result.item,
                            matchType = result.matchType,
                            searchQuery = searchQuery,
                            isSelected = index == selectedIndex,
                            onClick = { onItemClick(result.item) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * 文件结果项组件
 */
@Composable
private fun FileResultItem(
    item: FileContextItem,
    matchType: FileSearchResult.MatchType,
    searchQuery: String,
    isSelected: Boolean,
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
                    Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 文件图标
            Text(
                text = item.getIcon(),
                style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp)
            )
            
            // 文件信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 文件名（带关键词高亮）
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = buildHighlightedText(item.name, searchQuery, isSelected),
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp)
                    )
                } else {
                    Text(
                        text = item.name,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 13.sp,
                            color = if (isSelected) 
                                JewelTheme.globalColors.borders.focused
                            else 
                                JewelTheme.globalColors.text.normal
                        )
                    )
                }
                
                if (item.relativePath.isNotEmpty()) {
                    // 路径（带关键词高亮）
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            text = buildHighlightedText(item.getPathDisplay(), searchQuery, false),
                            style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                        )
                    } else {
                        Text(
                            text = item.getPathDisplay(),
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.disabled
                            )
                        )
                    }
                }
            }
            
            // 匹配类型指示器
            if (matchType != FileSearchResult.MatchType.PATH_MATCH) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            when (matchType) {
                                FileSearchResult.MatchType.EXACT_NAME -> JewelTheme.globalColors.borders.focused
                                FileSearchResult.MatchType.PREFIX_NAME -> JewelTheme.globalColors.borders.focused.copy(alpha = 0.7f)
                                FileSearchResult.MatchType.CONTAINS_NAME -> JewelTheme.globalColors.borders.focused.copy(alpha = 0.5f)
                                else -> Color.Transparent
                            },
                            RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}

/**
 * 获取主题感知的关键词高亮颜色
 * 根据当前主题动态计算高亮背景色和文字色，确保在不同主题下都有良好的对比度
 */
@Composable
private fun getThemeAwareHighlightColors(): Pair<Color, Color> {
    // 获取主题的主要颜色
    val focusedColor = JewelTheme.globalColors.borders.focused
    val backgroundColor = JewelTheme.globalColors.panelBackground
    val textColor = JewelTheme.globalColors.text.normal
    
    // 通过比较背景色的亮度来判断是否为暗色主题
    val bgLuminance = backgroundColor.red * 0.299f + backgroundColor.green * 0.587f + backgroundColor.blue * 0.114f
    val isDarkTheme = bgLuminance < 0.5f
    
    return if (isDarkTheme) {
        // 暗色主题：使用主题强调色的半透明版本作为背景，保持文本对比度
        Pair(
            focusedColor.copy(alpha = 0.3f), // 半透明的强调色背景
            Color.White.copy(alpha = 0.95f)  // 高对比度白色文字
        )
    } else {
        // 亮色主题：使用温和的黄色调背景，深色文字
        Pair(
            Color(0xFFFFF59D).copy(alpha = 0.8f), // 温和黄色背景
            Color(0xFF1A1A1A).copy(alpha = 0.87f)  // 深色文字确保可读性
        )
    }
}

/**
 * 构建带关键词高亮的文本
 * 使用主题感知的关键词高亮方案，在不同主题下自动调整颜色
 */
@Composable
private fun buildHighlightedText(
    text: String, 
    searchQuery: String, 
    isSelected: Boolean
) = buildAnnotatedString {
    if (searchQuery.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    
    val query = searchQuery.lowercase()
    val lowerText = text.lowercase()
    var lastIndex = 0
    
    // 获取主题感知的高亮颜色
    val (highlightBackground, highlightText) = getThemeAwareHighlightColors()
    
    // 查找所有匹配的位置
    var currentIndex = lowerText.indexOf(query, lastIndex)
    while (currentIndex != -1) {
        // 添加匹配前的文本
        if (currentIndex > lastIndex) {
            withStyle(
                SpanStyle(
                    color = if (isSelected) 
                        JewelTheme.globalColors.borders.focused 
                    else 
                        JewelTheme.globalColors.text.normal
                )
            ) {
                append(text.substring(lastIndex, currentIndex))
            }
        }
        
        // 添加高亮的匹配文本 - 使用主题感知的高亮颜色
        withStyle(
            SpanStyle(
                background = highlightBackground,
                color = highlightText
            )
        ) {
            append(text.substring(currentIndex, currentIndex + searchQuery.length))
        }
        
        lastIndex = currentIndex + searchQuery.length
        currentIndex = lowerText.indexOf(query, lastIndex)
    }
    
    // 添加剩余的文本
    if (lastIndex < text.length) {
        withStyle(
            SpanStyle(
                color = if (isSelected) 
                    JewelTheme.globalColors.borders.focused 
                else 
                    JewelTheme.globalColors.text.normal
            )
        ) {
            append(text.substring(lastIndex))
        }
    }
}

/**
 * Web输入区域组件
 */
@Composable
private fun WebInputSection(
    url: String,
    isValid: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "🌐",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp)
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Web Reference",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 13.sp,
                        color = JewelTheme.globalColors.text.normal
                    )
                )
                Text(
                    text = "Enter a URL to add as context",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = JewelTheme.globalColors.text.disabled
                    )
                )
            }
        }
        
        if (url.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isValid) "✅" else "❌",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                )
                
                Text(
                    text = if (isValid) "Valid URL" else "Invalid URL format",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = if (isValid) 
                            JewelTheme.globalColors.borders.focused
                        else 
                            JewelTheme.globalColors.text.disabled
                    )
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (isValid) {
                    Box(
                        modifier = Modifier
                            .clickable { onConfirm() }
                            .background(
                                JewelTheme.globalColors.borders.focused,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Add",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.panelBackground
                            )
                        )
                    }
                }
            }
        }
        
        Text(
            text = "💡 Tip: Press Tab to switch between Files and Web modes",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 10.sp,
                color = JewelTheme.globalColors.text.disabled
            )
        )
    }
}