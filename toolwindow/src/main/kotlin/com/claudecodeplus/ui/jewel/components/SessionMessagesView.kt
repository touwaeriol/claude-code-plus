package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.session.models.LazyLoadState
import com.claudecodeplus.session.models.toEnhancedMessage
import com.claudecodeplus.ui.models.EnhancedMessage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 会话消息视图 - 支持懒加载
 */
@Composable
fun SessionMessagesView(
    sessionId: String,
    projectPath: String,
    sessionManager: ClaudeSessionManager,
    onContextClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    pageSize: Int = 30
) {
    var loadState by remember(sessionId) { mutableStateOf(LazyLoadState()) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 加载会话消息
    LaunchedEffect(sessionId) {
        loadState = LazyLoadState(isLoading = true)
        
        // 使用流式加载
        sessionManager.readSessionMessagesFlow(sessionId, projectPath, pageSize)
            .collectLatest { messages ->
                loadState = loadState.copy(
                    loadedMessages = loadState.loadedMessages + messages,
                    isLoading = false,
                    hasMore = messages.size == pageSize
                )
            }
    }
    
    // 监听滚动状态，实现向上滚动加载更多历史消息
    LaunchedEffect(listState, loadState.hasMore) {
        snapshotFlow { 
            listState.firstVisibleItemIndex
        }.collect { firstVisible ->
            // 当滚动到顶部附近时加载更多历史消息
            if (firstVisible <= 5 && loadState.hasMore && !loadState.isLoading) {
                coroutineScope.launch {
                    loadMoreMessages(
                        sessionId = sessionId,
                        projectPath = projectPath,
                        sessionManager = sessionManager,
                        loadState = loadState,
                        onStateUpdate = { loadState = it }
                    )
                }
            }
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        reverseLayout = false
    ) {
        // 顶部加载指示器
        if (loadState.isLoading && loadState.loadedMessages.isNotEmpty()) {
            item(key = "loading-top") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "加载更多历史消息...",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = JewelTheme.globalColors.text.disabled
                            )
                        )
                    }
                }
            }
        }
        
        // 消息列表，按时间分组
        val groupedMessages = loadState.loadedMessages.groupBy { message ->
            formatMessageDate(message.timestamp)
        }
        
        groupedMessages.forEach { (date, messages) ->
            // 日期分隔符
            item(key = "date-$date") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        date,
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled,
                            fontSize = JewelTheme.defaultTextStyle.fontSize * 0.85f
                        )
                    )
                }
            }
            
            // 该日期的消息
            items(
                items = messages,
                key = { it.id }
            ) { message ->
                // 使用 UnifiedInputArea 的显示模式
                UnifiedInputArea(
                    mode = InputAreaMode.DISPLAY,
                    message = message,
                    onContextClick = onContextClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // 初始加载指示器
        if (loadState.isLoading && loadState.loadedMessages.isEmpty()) {
            item(key = "loading-initial") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "加载会话历史...",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = JewelTheme.globalColors.text.disabled
                            )
                        )
                    }
                }
            }
        }
        
        // 空状态
        if (!loadState.isLoading && loadState.loadedMessages.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无会话历史",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
            }
        }
    }
    
    // 滚动到最新消息
    LaunchedEffect(loadState.loadedMessages.size) {
        if (loadState.currentPage == 0 && loadState.loadedMessages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(loadState.loadedMessages.size - 1)
            }
        }
    }
}

/**
 * 加载更多消息
 */
private suspend fun loadMoreMessages(
    sessionId: String,
    projectPath: String,
    sessionManager: ClaudeSessionManager,
    loadState: LazyLoadState,
    onStateUpdate: (LazyLoadState) -> Unit
) {
    if (loadState.isLoading || !loadState.hasMore) return
    
    onStateUpdate(loadState.copy(isLoading = true))
    
    val nextPage = loadState.currentPage + 1
    val (messages, total) = sessionManager.readSessionMessages(
        sessionId = sessionId,
        projectPath = projectPath,
        pageSize = 30,
        page = nextPage
    )
    
    val enhancedMessages = messages.mapNotNull { it.toEnhancedMessage() }
    
    onStateUpdate(
        loadState.copy(
            loadedMessages = enhancedMessages + loadState.loadedMessages,
            totalMessageCount = total,
            isLoading = false,
            hasMore = enhancedMessages.isNotEmpty(),
            currentPage = nextPage
        )
    )
}

/**
 * 格式化消息日期
 */
private fun formatMessageDate(timestamp: Long): String {
    val instant = Instant.ofEpochMilli(timestamp)
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    
    return when {
        date == today -> "今天"
        date == today.minusDays(1) -> "昨天"
        date.year == today.year -> {
            val formatter = DateTimeFormatter.ofPattern("M月d日")
            date.format(formatter)
        }
        else -> {
            val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
            date.format(formatter)
        }
    }
}