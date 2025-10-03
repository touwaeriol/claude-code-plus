package com.claudecodeplus.ui.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import java.util.UUID

/**
 * 简化的懒加载会话演示
 * 展示如何实现会话历史的懒加载
 */
@Composable
fun SimpleLazySessionDemo(
    modifier: Modifier = Modifier
) {
    // 模拟的消息数据
    var allMessages by remember { mutableStateOf(generateMockMessages(100)) }
    var displayedMessages by remember { mutableStateOf<List<EnhancedMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }
    val pageSize = 20
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 初始加载
    LaunchedEffect(Unit) {
        loadPage(
            allMessages = allMessages,
            page = 0,
            pageSize = pageSize,
            onUpdate = { messages ->
                displayedMessages = messages
                currentPage = 0
            }
        )
    }
    
    // 监听滚动状态实现懒加载
    LaunchedEffect(listState) {
        snapshotFlow { 
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            
            // 当滚动到底部附近时加载更多
            lastVisibleItemIndex >= totalItemsCount - 5
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && !isLoading && (currentPage + 1) * pageSize < allMessages.size) {
                isLoading = true
                delay(500) // 模拟网络延迟
                
                loadPage(
                    allMessages = allMessages,
                    page = currentPage + 1,
                    pageSize = pageSize,
                    onUpdate = { newMessages ->
                        displayedMessages = displayedMessages + newMessages
                        currentPage++
                        isLoading = false
                    }
                )
            }
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.panelBackground)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "懒加载会话演示",
                style = JewelTheme.defaultTextStyle
            )
            Spacer(Modifier.weight(1f))
            Text(
                "已加载: ${displayedMessages.size} / ${allMessages.size}",
                style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.info
                )
            )
        }
        
        Divider(orientation = org.jetbrains.jewel.ui.Orientation.Horizontal)
        
        // 消息列表
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = displayedMessages,
                    key = { it.id }
                ) { message ->
                    MessageCard(message)
                }
                
                // 加载指示器
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("加载更多消息...")
                            }
                        }
                    }
                }
                
                // 提示信息
                if (!isLoading && displayedMessages.size < allMessages.size) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "向下滚动加载更多",
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = JewelTheme.globalColors.text.disabled
                                )
                            )
                        }
                    }
                }
            }
        }
        
        Divider(orientation = org.jetbrains.jewel.ui.Orientation.Horizontal)
        
        // 输入区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DefaultButton(
                    onClick = {
                        // 添加新消息到顶部
                        val newMessage = EnhancedMessage.create(

                            id = UUID.randomUUID().toString(),
                            role = MessageRole.USER,
                            text = "新消息 #${allMessages.size + 1}",
                            timestamp = System.currentTimeMillis()
                        )
                        allMessages = listOf(newMessage) + allMessages
                        displayedMessages = listOf(newMessage) + displayedMessages
                        
                        // 滚动到顶部
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                ) {
                    Text("添加新消息")
                }
                
                DefaultButton(
                    onClick = {
                        // 清空并重新加载
                        displayedMessages = emptyList()
                        currentPage = 0
                        coroutineScope.launch {
                            loadPage(
                                allMessages = allMessages,
                                page = 0,
                                pageSize = pageSize,
                                onUpdate = { messages ->
                                    displayedMessages = messages
                                }
                            )
                        }
                    }
                ) {
                    Text("重新加载")
                }
            }
        }
    }
}

/**
 * 消息卡片组件
 */
@Composable
private fun MessageCard(message: EnhancedMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.Gray.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (message.role == MessageRole.USER) {
                        Color(0xFFE8F0FE)
                    } else {
                        Color(0xFFF5F5F5)
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (message.role == MessageRole.USER) "用户" else "助手",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = if (message.role == MessageRole.USER) {
                            JewelTheme.globalColors.text.normal
                        } else {
                            JewelTheme.globalColors.text.info
                        }
                    )
                )
                Text(
                    text = formatTime(message.timestamp),
                    style = JewelTheme.defaultTextStyle.copy(
                        color = JewelTheme.globalColors.text.disabled,
                        fontSize = JewelTheme.defaultTextStyle.fontSize * 0.85f
                    )
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = message.content,
                style = JewelTheme.defaultTextStyle
            )
        }
    }
}

/**
 * 生成模拟消息
 */
private fun generateMockMessages(count: Int): List<EnhancedMessage> {
    return (1..count).map { index ->
        EnhancedMessage.create(
            id = UUID.randomUUID().toString(),
            role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
            text = "这是第 $index 条消息。${if (index % 2 == 0) "用户提问..." else "AI 回答..."}",
            timestamp = System.currentTimeMillis() - (count - index) * 60000L // 每条消息相隔1分钟
        )
    }
}

/**
 * 加载指定页的数据
 */
private suspend fun loadPage(
    allMessages: List<EnhancedMessage>,
    page: Int,
    pageSize: Int,
    onUpdate: (List<EnhancedMessage>) -> Unit
) {
    val startIndex = page * pageSize
    val endIndex = minOf(startIndex + pageSize, allMessages.size)
    
    if (startIndex < allMessages.size) {
        val pageMessages = allMessages.subList(startIndex, endIndex)
        onUpdate(pageMessages)
    }
}

/**
 * 格式化时间
 */
private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        else -> "${diff / 86400_000}天前"
    }
}