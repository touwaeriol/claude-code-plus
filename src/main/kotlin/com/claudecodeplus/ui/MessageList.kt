package com.claudecodeplus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 自动滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages) { message ->
            MessageItem(message)
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(
                    start = if (message.isUser) 48.dp else 0.dp,
                    end = if (message.isUser) 0.dp else 48.dp
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = if (message.isUser) "You" else "Claude",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // TODO: 添加 Markdown 渲染支持
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (message.content.contains("```")) FontFamily.Monospace else FontFamily.Default
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
