package com.claudecodeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.ChatSearchEngine
import com.claudecodeplus.ui.services.SearchSuggestion
import com.claudecodeplus.ui.services.SearchOptions
import com.claudecodeplus.ui.services.ChatTabManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * 全局搜索对话框
 */
@Composable
fun GlobalSearchDialog(
    tabManager: ChatTabManager,
    onSelectResult: (chatId: String, messageId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val searchEngine = remember { ChatSearchEngine() }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChatSearchResult>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(0) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    
    // 搜索选项
    var searchOptions by remember { mutableStateOf(SearchOptions()) }
    
    // 搜索函数
    fun performSearch() {
        searchJob?.cancel()
        searchJob = scope.launch {
            if (query.isBlank()) {
                results = emptyList()
                return@launch
            }
            
            isSearching = true
            delay(300) // 防抖
            
            results = searchEngine.search(
                query = query,
                tabs = tabManager.tabs,
                options = searchOptions
            )
            
            isSearching = false
            selectedIndex = 0
        }
    }
    
    DialogWindow(
        onCloseRequest = onDismiss,
        title = "搜索对话",
        undecorated = false
    ) {
        Column(
            modifier = Modifier
                .size(800.dp, 600.dp)
                .padding(16.dp)
                .onKeyEvent { event ->
                    when {
                        event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                            onDismiss()
                            true
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                            if (selectedIndex < results.size - 1) {
                                selectedIndex++
                            }
                            true
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                            if (selectedIndex > 0) {
                                selectedIndex--
                            }
                            true
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                            results.getOrNull(selectedIndex)?.let { result ->
                                val messageId = result.matchedMessages.firstOrNull()?.messageId
                                onSelectResult(result.chatId, messageId)
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            // 搜索栏
            SearchBar(
                query = query,
                onQueryChange = { 
                    query = it
                    suggestions = if (it.length > 2) {
                        searchEngine.getSuggestions(it, tabManager.tabs)
                    } else {
                        emptyList()
                    }
                    performSearch()
                },
                suggestions = suggestions,
                onSuggestionSelect = { suggestion ->
                    query = suggestion.text
                    suggestions = emptyList()
                    performSearch()
                },
                onToggleAdvanced = { showAdvancedOptions = !showAdvancedOptions }
            )
            
            // 高级选项
            if (showAdvancedOptions) {
                AdvancedSearchOptions(
                    options = searchOptions,
                    onOptionsChange = { 
                        searchOptions = it
                        performSearch()
                    }
                )
            }
            
            Divider(orientation = Orientation.Horizontal, modifier = Modifier.padding(vertical = 8.dp))
            
            // 搜索结果
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isSearching -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    query.isNotBlank() && results.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.Gray
                                )
                                Text(
                                    "未找到匹配的结果",
                                    style = JewelTheme.defaultTextStyle,
                                    color = Color.Gray
                                )
                                Text(
                                    "尝试使用不同的关键词或调整搜索选项",
                                    style = JewelTheme.defaultTextStyle,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    else -> {
                        SearchResultsList(
                            results = results,
                            selectedIndex = selectedIndex,
                            onSelectResult = { result ->
                                val messageId = result.matchedMessages.firstOrNull()?.messageId
                                onSelectResult(result.chatId, messageId)
                            },
                            onHoverIndex = { selectedIndex = it }
                        )
                    }
                }
            }
            
            // 底部状态栏
            if (results.isNotEmpty()) {
                SearchStatusBar(
                    resultCount = results.size,
                    selectedIndex = selectedIndex
                )
            }
        }
    }
}

/**
 * 搜索栏
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<SearchSuggestion>,
    onSuggestionSelect: (SearchSuggestion) -> Unit,
    onToggleAdvanced: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 搜索框 - 暂时使用简单实现
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (query.isEmpty()) "搜索对话内容、标题或标签..." else query,
                    color = if (query.isEmpty()) Color.Gray else JewelTheme.defaultTextStyle.color,
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "清除")
                    }
                }
            }
            
            OutlinedButton(onClick = onToggleAdvanced) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("高级")
            }
        }
        
        // 搜索建议
        if (suggestions.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            ) {
                Column(
                    modifier = Modifier.padding(4.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        SuggestionItem(
                            suggestion = suggestion,
                            onClick = { onSuggestionSelect(suggestion) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 搜索建议项
 */
@Composable
private fun SuggestionItem(
    suggestion: SearchSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 类型图标
        val icon = when (suggestion.type) {
            SearchSuggestion.Type.TITLE -> Icons.Default.List
            SearchSuggestion.Type.TAG -> Icons.Default.Star
            SearchSuggestion.Type.KEYWORD -> Icons.Default.Info
            SearchSuggestion.Type.RECENT -> Icons.Default.Refresh
        }
        
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color.Gray
        )
        
        Text(
            text = suggestion.text,
            style = JewelTheme.defaultTextStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 高级搜索选项
 */
@Composable
private fun AdvancedSearchOptions(
    options: SearchOptions,
    onOptionsChange: (SearchOptions) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "搜索范围",
                style = JewelTheme.defaultTextStyle
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CheckboxRow(
                    checked = options.searchInTitles,
                    onCheckedChange = { 
                        onOptionsChange(options.copy(searchInTitles = it))
                    }
                ) {
                    Text("标题")
                }
                
                CheckboxRow(
                    checked = options.searchInContent,
                    onCheckedChange = { 
                        onOptionsChange(options.copy(searchInContent = it))
                    }
                ) {
                    Text("内容")
                }
                
                CheckboxRow(
                    checked = options.searchInTags,
                    onCheckedChange = { 
                        onOptionsChange(options.copy(searchInTags = it))
                    }
                ) {
                    Text("标签")
                }
                
                CheckboxRow(
                    checked = options.searchInContext,
                    onCheckedChange = { 
                        onOptionsChange(options.copy(searchInContext = it))
                    }
                ) {
                    Text("上下文")
                }
            }
        }
    }
}

/**
 * 搜索结果列表
 */
@Composable
private fun SearchResultsList(
    results: List<ChatSearchResult>,
    selectedIndex: Int,
    onSelectResult: (ChatSearchResult) -> Unit,
    onHoverIndex: (Int) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(results.withIndex().toList(), key = { it.value.chatId }) { (index, result) ->
            SearchResultItem(
                result = result,
                isSelected = index == selectedIndex,
                onClick = { onSelectResult(result) },
                onHover = { onHoverIndex(index) }
            )
        }
    }
}

/**
 * 搜索结果项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultItem(
    result: ChatSearchResult,
    isSelected: Boolean,
    onClick: () -> Unit,
    onHover: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {}
            )
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    Color(0xFF2675BF).copy(alpha = 0.3f)
                } else {
                    Color.White
                }
            )
            .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = result.chatTitle,
                    style = JewelTheme.defaultTextStyle,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = formatTime(result.lastModified),
                    style = JewelTheme.defaultTextStyle,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 匹配的内容
            result.matchedMessages.take(3).forEach { match ->
                MatchedMessageItem(match)
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // 如果有更多匹配
            if (result.matchedMessages.size > 3) {
                Text(
                    text = "还有 ${result.matchedMessages.size - 3} 个匹配...",
                    style = JewelTheme.defaultTextStyle,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * 匹配的消息项
 */
@Composable
private fun MatchedMessageItem(
    match: MessageMatch
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 匹配类型图标
        val (icon, color) = when (match.matchType) {
            MessageMatch.MatchType.TITLE -> Icons.Default.List to Color(0xFF4CAF50)
            MessageMatch.MatchType.CONTENT -> Icons.Default.Info to Color(0xFF2196F3)
            MessageMatch.MatchType.TAG -> Icons.Default.Star to Color(0xFFFF9800)
            MessageMatch.MatchType.CONTEXT -> Icons.Default.List to Color(0xFF9C27B0)
            MessageMatch.MatchType.METADATA -> Icons.Default.Info to Color(0xFF607D8B)
        }
        
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        
        // 高亮的文本
        Text(
            text = highlightText(match.snippet, match.highlights),
            style = JewelTheme.defaultTextStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 搜索状态栏
 */
@Composable
private fun SearchStatusBar(
    resultCount: Int,
    selectedIndex: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "找到 $resultCount 个结果",
            style = JewelTheme.defaultTextStyle
        )
        
        Text(
            text = "${selectedIndex + 1} / $resultCount",
            style = JewelTheme.defaultTextStyle
        )
    }
}

/**
 * 高亮文本
 */
@Composable
private fun highlightText(text: String, highlights: List<IntRange>): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var lastEnd = 0
        
        highlights.sortedBy { it.first }.forEach { range ->
            // 添加普通文本
            if (lastEnd < range.first) {
                append(text.substring(lastEnd, range.first))
            }
            
            // 添加高亮文本
            withStyle(
                SpanStyle(
                    background = Color(0x33FFD700),
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(range.first, range.last.coerceAtMost(text.length)))
            }
            
            lastEnd = range.last.coerceAtMost(text.length)
        }
        
        // 添加剩余文本
        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }
}

/**
 * 格式化时间
 */
private fun formatTime(instant: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}