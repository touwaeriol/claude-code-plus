package com.claudecodeplus.ui.jewel.components.context

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * 文件上下文选择器
 * 第二步：选择具体的文件
 */
@OptIn(FlowPreview::class)
@Composable
fun FileContextSelector(
    query: String,
    config: ContextSelectorConfig,
    searchService: ContextSearchService,
    onQueryChange: (String) -> Unit,
    onFileSelected: (FileContextItem) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchResults by remember { mutableStateOf<List<FileSearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    
    // 搜索逻辑
    LaunchedEffect(query) {
        if (query.isEmpty()) {
            // 显示根目录文件
            isLoading = true
            try {
                val rootFiles = searchService.getRootFiles(config.maxResults)
                searchResults = rootFiles.map { 
                    FileSearchResult(it, 100, FileSearchResult.MatchType.EXACT_NAME) 
                }
                selectedIndex = 0
            } catch (e: Exception) {
                searchResults = emptyList()
            } finally {
                isLoading = false
            }
        } else {
            // 执行搜索（带去抖动）
            flow { emit(query) }
                .debounce(config.searchDelayMs)
                .distinctUntilChanged()
                .onStart { isLoading = true }
                .flatMapLatest { searchQuery ->
                    flow {
                        try {
                            val results = searchService.searchFiles(searchQuery, config.maxResults)
                            emit(results)
                        } catch (e: Exception) {
                            emit(emptyList<FileSearchResult>())
                        }
                    }
                }
                .onEach { results ->
                    searchResults = results
                    selectedIndex = 0
                    isLoading = false
                }
                .launchIn(scope)
        }
    }
    
    // 自动聚焦到搜索框
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // 头部：搜索框和返回按钮
        FileSearchHeader(
            query = query,
            onQueryChange = onQueryChange,
            onBack = onBack,
            onCancel = onCancel,
            focusRequester = focusRequester,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 文件列表
        if (isLoading) {
            LoadingIndicator(modifier = Modifier.fillMaxWidth())
        } else if (searchResults.isEmpty()) {
            EmptyResultsIndicator(
                query = query,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            FileResultsList(
                results = searchResults,
                selectedIndex = selectedIndex,
                config = config,
                onSelectedIndexChange = { selectedIndex = it },
                onFileSelected = onFileSelected,
                listState = listState,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 底部提示
        FileSearchFooter(
            resultCount = searchResults.size,
            maxResults = config.maxResults,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 文件搜索头部组件
 */
@Composable
private fun FileSearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // 返回按钮
        Text(
            text = "←",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 14.sp,
                color = JewelTheme.globalColors.borders.focused
            ),
            modifier = Modifier
                .clickable { onBack() }
                .padding(end = 8.dp)
        )
        
        // 搜索框
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                color = JewelTheme.globalColors.text.normal
            ),
            cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .background(
                            JewelTheme.globalColors.panelBackground,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(6.dp)
                ) {
                    if (query.isEmpty()) {
                        Text(
                            "搜索文件...",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.disabled
                            )
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Escape -> {
                                if (query.isEmpty()) {
                                    onCancel()
                                } else {
                                    onQueryChange("")
                                }
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        )
    }
}

/**
 * 文件结果列表组件
 */
@Composable
private fun FileResultsList(
    results: List<FileSearchResult>,
    selectedIndex: Int,
    config: ContextSelectorConfig,
    onSelectedIndexChange: (Int) -> Unit,
    onFileSelected: (FileContextItem) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier
            .heightIn(max = (config.popupMaxHeight - 100).dp)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            onSelectedIndexChange((selectedIndex - 1).coerceAtLeast(0))
                            true
                        }
                        Key.DirectionDown -> {
                            onSelectedIndexChange((selectedIndex + 1).coerceAtMost(results.size - 1))
                            true
                        }
                        Key.Enter -> {
                            if (results.isNotEmpty() && selectedIndex < results.size) {
                                onFileSelected(results[selectedIndex].item)
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        items(results) { result ->
            val isSelected = results.indexOf(result) == selectedIndex
            FileResultItem(
                result = result,
                isSelected = isSelected,
                config = config,
                onClick = { onFileSelected(result.item) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 文件结果项组件
 */
@Composable
private fun FileResultItem(
    result: FileSearchResult,
    isSelected: Boolean,
    config: ContextSelectorConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(config.itemHeight.dp)
            .clip(RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .then(
                if (isSelected) {
                    Modifier.background(
                        JewelTheme.globalColors.borders.focused.copy(alpha = 0.15f),
                        RoundedCornerShape(3.dp)
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // 文件图标
        Text(
            text = result.item.getIcon(),
            style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp),
            modifier = Modifier.padding(end = 8.dp)
        )
        
        // 文件信息
        Column(modifier = Modifier.weight(1f)) {
            // 文件名（主显示）
            Text(
                text = result.item.name,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.normal
                ),
                maxLines = 1
            )
            
            // 相对路径（副显示）
            if (result.item.relativePath.isNotEmpty()) {
                Text(
                    text = result.item.getPathDisplay(),
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 9.sp,
                        color = JewelTheme.globalColors.text.disabled
                    ),
                    maxLines = 1
                )
            }
        }
        
        // 匹配类型指示器（调试时显示）
        if (isSelected) {
            Text(
                text = when (result.matchType) {
                    FileSearchResult.MatchType.EXACT_NAME -> "="
                    FileSearchResult.MatchType.PREFIX_NAME -> "^"
                    FileSearchResult.MatchType.CONTAINS_NAME -> "~"
                    FileSearchResult.MatchType.PATH_MATCH -> "/"
                },
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 9.sp,
                    color = JewelTheme.globalColors.text.disabled
                ),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * 加载指示器组件
 */
@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(64.dp)
    ) {
        Text(
            text = "搜索中...",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 10.sp,
                color = JewelTheme.globalColors.text.disabled
            )
        )
    }
}

/**
 * 空结果指示器组件
 */
@Composable
private fun EmptyResultsIndicator(
    query: String,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(64.dp)
    ) {
        Text(
            text = if (query.isEmpty()) "项目文件为空" else "未找到匹配的文件",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 10.sp,
                color = JewelTheme.globalColors.text.disabled
            )
        )
    }
}

/**
 * 文件搜索底部组件
 */
@Composable
private fun FileSearchFooter(
    resultCount: Int,
    maxResults: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = when {
            resultCount == 0 -> ""
            resultCount >= maxResults -> "显示前 $maxResults 个结果（可能有更多）"
            else -> "共 $resultCount 个结果"
        },
        style = JewelTheme.defaultTextStyle.copy(
            fontSize = 9.sp,
            color = JewelTheme.globalColors.text.disabled
        ),
        modifier = modifier.padding(top = 4.dp)
    )
}

 