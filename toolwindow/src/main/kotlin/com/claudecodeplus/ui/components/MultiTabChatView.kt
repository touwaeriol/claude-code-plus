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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.draw.clip

/**
 * 多标签聊天视图 - 已移除标签栏，只显示聊天内容
 */
@Composable
fun MultiTabChatView(
    tabManager: ChatTabManager,
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String,
    fileIndexService: FileIndexService,
    projectService: ProjectService,
    sessionManager: ClaudeSessionManager,
    projectManager: com.claudecodeplus.ui.services.ProjectManager? = null,
    onTabHover: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tabs = tabManager.tabs
    val activeTabId = tabManager.activeTabId
    val scope = rememberCoroutineScope()
    
    // 直接显示当前标签的聊天内容，不显示标签栏
    Box(modifier = modifier) {
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
                            tabManager = tabManager,
                            currentTabId = tab.id,
                            currentProject = if (tab.projectId != null) {
                                com.claudecodeplus.ui.models.Project(
                                    id = tab.projectId,
                                    path = tab.projectPath ?: tab.projectId,
                                    name = tab.projectName ?: tab.projectId.substringAfterLast("/")
                                )
                            } else null,
                            projectManager = projectManager,
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
 * 空标签视图
 */
@Composable
fun EmptyTabsView(
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