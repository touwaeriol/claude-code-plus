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
import com.claudecodeplus.ui.services.UnifiedSessionService
import com.claudecodeplus.ui.services.UnifiedSessionServiceProvider
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.core.interfaces.ProjectService
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.draw.clip
import com.claudecodeplus.sdk.*

/**
 * 多标签聊天视图 - 已移除标签栏，只显示聊天内容
 */
@Composable
fun MultiTabChatView(
    tabManager: ChatTabManager,
    unifiedSessionServiceProvider: UnifiedSessionServiceProvider,
    workingDirectory: String,
    fileIndexService: FileIndexService,
    projectService: ProjectService,
    sessionManager: ClaudeSessionManager,
    projectManager: com.claudecodeplus.ui.services.ProjectManager? = null,
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration? = null,  // 新增：IDE 集成
    onTabHover: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tabs = tabManager.tabs
    val activeTabId = tabManager.activeTabId
    val scope = rememberCoroutineScope()
    
    // 直接显示当前标签的聊天内容，不显示标签栏
    Box(modifier = modifier) {
        println("=== MultiTabChatView 调试信息 ===")
        println("activeTabId: $activeTabId")
        println("tabs.size: ${tabs.size}")
        println("所有标签详情:")
        tabs.forEachIndexed { index, tab ->
            println("  [$index] id: ${tab.id}")
            println("       title: '${tab.title}'")
            println("       projectId: ${tab.projectId}")
            println("       projectPath: ${tab.projectPath}")
            println("       sessionId: ${tab.sessionId}")
        }
        
        activeTabId?.let { id ->
            println("查找活动标签: $id")
            tabs.find { it.id == id }?.let { tab ->
                println("找到标签: ${tab.title}")
                println("tab.projectPath: ${tab.projectPath}")
                println("tab.projectId: ${tab.projectId}")
                println("tab.sessionId: ${tab.sessionId}")
                
                // 检查标签是否有有效的项目
                if (tab.projectPath == null || tab.projectId == null) {
                    println("标签没有项目信息，显示 NoProjectView")
                    // 没有项目时显示提示
                    NoProjectView()
                } else {
                    println("标签有项目信息，显示 ChatView")
                    // 移除强制重建，让组件自然保持状态
                    // 只有当项目路径发生变化时才重建组件
                    key("${tab.id}_${tab.projectPath}") {
                        // 根据当前标签的项目路径获取对应的 UnifiedSessionService
                        val currentUnifiedSessionService = unifiedSessionServiceProvider.getServiceForProject(tab.projectPath)
                        
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 使用新的事件驱动 ChatView，UI完全相同但底层为事件驱动架构
                            com.claudecodeplus.ui.jewel.ChatViewNew(
                                unifiedSessionService = currentUnifiedSessionService,
                                workingDirectory = tab.projectPath ?: workingDirectory, // 优先使用标签的项目路径，确保与UnifiedSessionService一致
                                fileIndexService = fileIndexService,
                                projectService = projectService,
                                sessionManager = sessionManager,
                                tabId = tab.id,
                                initialMessages = tab.messages,  // 直接使用 EnhancedMessage
                                sessionId = tab.sessionId,
                                tabManager = tabManager,
                                currentTabId = tab.id,
                                currentProject = com.claudecodeplus.ui.models.Project(
                                    id = tab.projectId ?: "",
                                    path = tab.projectPath,
                                    name = tab.projectName ?: tab.projectId?.substringAfterLast("/") ?: "Unknown Project"
                                ),
                                projectManager = projectManager,
                                ideIntegration = ideIntegration,  // 传递 IDE 集成
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
                println("未找到活动标签")
            }
        } ?: run {
            println("没有活动标签，显示 EmptyTabsView")
            println("=== 强制显示聊天输入框测试 ===")
            
            // 临时：显示一个简单的测试界面
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "🧪 临时测试界面 🧪",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = JewelTheme.defaultTextStyle.fontSize * 1.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("如果您看到这个界面，说明：")
                Text("1. ✅ MultiTabChatView 组件正在渲染")
                Text("2. ❌ 但没有活动标签 (activeTabId = null)")
                Text("3. 🔧 需要修复标签创建和事件流程")
                
                Spacer(modifier = Modifier.height(24.dp))
                DefaultButton(
                    onClick = { tabManager.createNewTab() }
                ) {
                    Text("创建测试标签")
                }
            }
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
 * 无项目视图
 */
@Composable
fun NoProjectView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Home,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = JewelTheme.globalColors.text.disabled
            )
            Text(
                text = "请先选择一个项目",
                style = JewelTheme.defaultTextStyle,
                color = JewelTheme.globalColors.text.normal
            )
            Text(
                text = "在左侧项目面板中选择或创建项目",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                ),
                color = JewelTheme.globalColors.text.disabled
            )
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