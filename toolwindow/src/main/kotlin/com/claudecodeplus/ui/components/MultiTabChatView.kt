package com.claudecodeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.Orientation
import com.claudecodeplus.ui.models.ChatTab
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.services.ChatTabManager
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.ProjectService
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close

/**
 * 多标签聊天视图
 */
@Composable
fun MultiTabChatView(
    tabManager: ChatTabManager,
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String,
    fileIndexService: FileIndexService,
    projectService: ProjectService,
    sessionManager: ClaudeSessionManager,
    modifier: Modifier = Modifier
) {
    val tabs = tabManager.tabs
    val activeTabId = tabManager.activeTabId
    val scope = rememberCoroutineScope()
    
    Column(modifier = modifier) {
        // 标签栏
        ChatTabStrip(
            tabs = tabs,
            activeTabId = activeTabId,
            onTabSelect = { tabManager.setActiveTab(it) },
            onTabClose = { tabManager.closeTab(it) },
            onNewTab = { tabManager.createNewTab() },
            onTabRightClick = { tabId ->
                // TODO: 显示右键菜单
            }
        )
        
        Divider(orientation = Orientation.Horizontal)
        
        // 当前标签的聊天内容
        activeTabId?.let { id ->
            tabs.find { it.id == id }?.let { tab ->
                // 使用 key 确保切换标签时重新创建 ChatView
                key(tab.id) {
                    Box(modifier = Modifier.fillMaxSize()) {
                    // 将 ChatMessage 转换为 EnhancedMessage
                    val enhancedMessages = tab.messages.map { chatMessage ->
                        EnhancedMessage(
                            id = chatMessage.id,
                            role = chatMessage.role,
                            content = chatMessage.content,
                            timestamp = chatMessage.timestamp.toEpochMilli(),
                            model = AiModel.OPUS, // 默认模型
                            contexts = emptyList()
                        )
                    }
                    
                    com.claudecodeplus.ui.jewel.ChatView(
                        cliWrapper = cliWrapper,
                        workingDirectory = workingDirectory,
                        fileIndexService = fileIndexService,
                        projectService = projectService,
                        sessionManager = sessionManager,
                        initialMessages = enhancedMessages,
                        sessionId = tab.sessionId,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // 标签状态指示器
                    if (tab.status == ChatTab.TabStatus.INTERRUPTED) {
                        Banner(
                            message = "对话已中断",
                            modifier = Modifier.align(Alignment.TopCenter)
                        ) {
                            OutlinedButton(onClick = {
                                // TODO: 恢复对话
                            }) {
                                Text("继续对话")
                            }
                        }
                    }
                }
                }
            }
        } ?: run {
            // 无标签时的空状态
            EmptyTabsView(
                onCreateTab = { tabManager.createNewTab() }
            )
        }
    }
    
    // 监听标签事件
    LaunchedEffect(Unit) {
        tabManager.events.collect { event ->
            when (event) {
                is ChatTabManager.TabEvent.CloseConfirmationNeeded -> {
                    // TODO: 显示确认对话框
                }
                else -> {}
            }
        }
    }
}

/**
 * 标签栏组件
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatTabStrip(
    tabs: List<ChatTab>,
    activeTabId: String?,
    onTabSelect: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onTabRightClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color.White.copy(alpha = 0.95f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 标签列表
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(tabs, key = { it.id }) { tab ->
                TabItem(
                    tab = tab,
                    isActive = tab.id == activeTabId,
                    onSelect = { onTabSelect(tab.id) },
                    onClose = { onTabClose(tab.id) },
                    onRightClick = { onTabRightClick(tab.id) }
                )
            }
        }
        
        // 新建标签按钮
        IconButton(
            onClick = onNewTab,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "新建标签"
            )
        }
    }
}

/**
 * 标签项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabItem(
    tab: ChatTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onRightClick: () -> Unit
) {
    val backgroundColor = if (isActive) {
        Color(0xFF2675BF).copy(alpha = 0.3f)
    } else {
        Color.White
    }
    
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .background(backgroundColor)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onRightClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 状态指示器
        if (tab.status == ChatTab.TabStatus.INTERRUPTED) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        Color(0xFFFF9800),
                        shape = RoundedCornerShape(3.dp)
                    )
            )
        }
        
        // 标签标题
        Text(
            text = tab.title,
            style = JewelTheme.defaultTextStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 150.dp) // 限制最大宽度
                .weight(1f, fill = false)
        )
        
        // 消息计数
        if (tab.messages.isNotEmpty()) {
            Text(
                text = "${tab.messages.size}",
                style = JewelTheme.defaultTextStyle,
                color = Color.Gray
            )
        }
        
        // 关闭按钮
        IconButton(
            onClick = { onClose() },
            modifier = Modifier.size(16.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭标签",
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * 空标签视图
 */
@Composable
private fun EmptyTabsView(
    onCreateTab: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "暂无对话",
                style = JewelTheme.defaultTextStyle,
                color = Color.Gray
            )
            
            DefaultButton(onClick = onCreateTab) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建新对话")
            }
        }
    }
}

/**
 * 横幅组件
 */
@Composable
fun Banner(
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF64B5F6).copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = JewelTheme.defaultTextStyle,
                color = Color.Gray
            )
            
            action?.invoke()
        }
    }
}