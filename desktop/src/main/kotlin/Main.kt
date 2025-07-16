package com.claudecodeplus.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.FrameWindowScope
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import com.claudecodeplus.desktop.di.ServiceContainer
import com.claudecodeplus.desktop.state.AppUiState
import com.claudecodeplus.ui.components.MultiTabChatView
import com.claudecodeplus.ui.components.GlobalSearchDialog
import com.claudecodeplus.ui.components.ChatOrganizer
import com.claudecodeplus.ui.services.ChatExportService
import com.claudecodeplus.ui.models.ExportFormat
import com.claudecodeplus.ui.components.ProjectListPanel
import com.claudecodeplus.desktop.dialogs.NewProjectDialog
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

/**
 * Claude Code Plus 桌面应用主函数
 */
fun main() = application {
    val projectPath = System.getProperty("user.dir")
    
    // 在组合前直接初始化服务
    ServiceContainer.initialize(projectPath)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Claude Code Plus",
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp
        )
    ) {
        IntUiTheme {
            EnhancedClaudeApp()
        }
    }
}

/**
 * 增强版应用主组件。
 *
 * 该组件是应用的核心UI入口，整合了所有主要功能，包括：
 * - **服务集成**: 从 `ServiceContainer` 获取所有必要的后端服务，如会话管理、项目管理等。
 * - **状态管理**: 管理应用的UI状态 (`AppUiState`) 和数据状态（如当前项目、会话、索引状态等）。
 * - **多标签聊天视图**: 使用 `MultiTabChatView` 来展示和管理多个聊天会话。
 * - **全局快捷键**: 通过 `onKeyEvent` 修饰符处理整个窗口的快捷键，如新建/关闭标签页、打开搜索等。
 * - **对话框管理**: 控制各种功能对话框（如项目组织器、全局搜索、导出）的显示和隐藏。
 * - **布局构建**: 使用 `Column` 和 `Row` 构建了经典的 IDE 式布局（左侧面板、主编辑区、顶部工具栏、底部状态栏）。
 */
@Composable
fun FrameWindowScope.EnhancedClaudeApp() {
    // 从服务容器获取服务
    val cliWrapper = ServiceContainer.cliWrapper
    val sessionManager = ServiceContainer.sessionManager
    val projectManager = ServiceContainer.projectManager
    val tabManager = ServiceContainer.tabManager
    val exportService = ServiceContainer.exportService
    val fileIndexService = ServiceContainer.fileIndexService
    val projectService = ServiceContainer.projectService
    
    // UI 状态
    val uiState = remember { AppUiState() }
    
    // 数据状态
    val currentProject by projectManager.currentProject
    val currentSession by projectManager.currentSession
    val isIndexing by fileIndexService.isIndexing.collectAsState()
    val statusMessage by fileIndexService.statusMessage.collectAsState()
    
    // 全局快捷键
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                when {
                    // Ctrl+T: 新建标签
                    event.type == KeyEventType.KeyDown && 
                    event.key == Key.T && 
                    event.isCtrlPressed -> {
                        tabManager.createNewTab()
                        true
                    }
                    // Ctrl+W: 关闭当前标签
                    event.type == KeyEventType.KeyDown && 
                    event.key == Key.W && 
                    event.isCtrlPressed -> {
                        tabManager.activeTabId?.let { tabManager.closeTab(it) }
                        true
                    }
                    // Ctrl+Shift+O: 打开组织器
                    event.type == KeyEventType.KeyDown && 
                    event.key == Key.O && 
                    event.isCtrlPressed && 
                    event.isShiftPressed -> {
                        uiState.isOrganizerVisible = true
                        true
                    }
                    // Ctrl+F: 全局搜索
                    event.type == KeyEventType.KeyDown && 
                    event.key == Key.F && 
                    event.isCtrlPressed -> {
                        uiState.isSearchVisible = true
                        true
                    }
                    // Ctrl+P: 提示词模板
                    event.type == KeyEventType.KeyDown && 
                    event.key == Key.P && 
                    event.isCtrlPressed -> {
                        uiState.isTemplatesVisible = true
                        true
                    }
                    // Ctrl+E: 导出
                    event.type == KeyEventType.KeyDown && 
                    event.key == Key.E && 
                    event.isCtrlPressed -> {
                        uiState.isExportDialogVisible = true
                        true
                    }
                    // Ctrl+Tab: 切换标签
                    event.type == KeyEventType.KeyDown && 
                    event.key == Key.Tab && 
                    event.isCtrlPressed -> {
                        val tabs = tabManager.tabs
                        val currentIndex = tabs.indexOfFirst { it.id == tabManager.activeTabId }
                        if (currentIndex != -1 && tabs.size > 1) {
                            val nextIndex = (currentIndex + 1) % tabs.size
                            tabManager.setActiveTab(tabs[nextIndex].id)
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Column {
            // 顶部工具栏
            AppToolbar(
                onNewTab = { tabManager.createNewTab() },
                onOrganize = { uiState.isOrganizerVisible = true },
                onSearch = { uiState.isSearchVisible = true },
                onTemplates = { uiState.isTemplatesVisible = true },
                onExport = { uiState.isExportDialogVisible = true }
            )
            
            Divider(orientation = Orientation.Horizontal)
            
            // 主内容区域：左侧项目面板 + 右侧聊天区域
            Row(modifier = Modifier.fillMaxSize()) {
                val currentProject by projectManager.currentProject
                val currentSession by projectManager.currentSession

                ProjectListPanel(
                    projectManager = projectManager,
                    selectedProject = currentProject,
                    selectedSession = currentSession,
                    onProjectSelect = { project -> projectManager.setCurrentProject(project) },
                    onSessionSelect = { session -> projectManager.setCurrentSession(session) },
                    modifier = Modifier.fillMaxHeight()
                )

                Divider(orientation = Orientation.Vertical)

                MultiTabChatView(
                    tabManager = tabManager,
                    cliWrapper = cliWrapper,
                    workingDirectory = currentProject?.path ?: projectService.getProjectPath(),
                    fileIndexService = fileIndexService,
                    projectService = projectService,
                    sessionManager = sessionManager,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // 状态栏
        StatusBar(
            tabCount = tabManager.tabs.size,
            activeTabTitle = tabManager.tabs.find { it.id == tabManager.activeTabId }?.title,
            statusMessage = statusMessage,
            isIndexing = isIndexing,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    
    // 新建项目对话框
    if (uiState.isNewProjectDialogVisible) {
        // NewProjectDialog is disabled
    }
    
    // 对话框
    if (uiState.isOrganizerVisible) {
        ChatOrganizer(
            tabManager = tabManager,
            onTabSelect = { tabId ->
                tabManager.setActiveTab(tabId)
                uiState.isOrganizerVisible = false
            },
            onClose = { uiState.isOrganizerVisible = false }
        )
    }
    
    if (uiState.isSearchVisible) {
        GlobalSearchDialog(
            tabManager = tabManager,
            onSelectResult = { chatId, messageId ->
                tabManager.setActiveTab(chatId)
                uiState.isSearchVisible = false
                // TODO: 滚动到特定消息
            },
            onDismiss = { uiState.isSearchVisible = false }
        )
    }
    
    // TODO: PromptTemplateDialog
    
    if (uiState.isExportDialogVisible) {
        ExportDialog(
            tabs = tabManager.tabs,
            exportService = exportService,
            onDismiss = { uiState.isExportDialogVisible = false }
        )
    }
}

/**
 * 顶部工具栏
 */
@Composable
private fun AppToolbar(
    onNewTab: () -> Unit,
    onOrganize: () -> Unit,
    onSearch: () -> Unit,
    onTemplates: () -> Unit,
    onExport: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧操作
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNewTab) {
                    Icon(Icons.Default.Add, contentDescription = "新建对话")
                }
                
                IconButton(onClick = onOrganize) {
                    Icon(Icons.Default.Menu, contentDescription = "管理对话")
                }
                
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
            }
            
            // 中间标题
            Text(
                "Claude Code Plus",
                style = JewelTheme.defaultTextStyle
            )
            
            // 右侧操作
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onTemplates) {
                    Icon(Icons.Default.Menu, contentDescription = "模板")
                }
                
                IconButton(onClick = onExport) {
                    Icon(Icons.Default.Share, contentDescription = "导出")
                }
                
                IconButton(onClick = { /* TODO: 设置 */ }) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            }
        }
    }
}

/**
 * 状态栏
 */
@Composable
private fun StatusBar(
    tabCount: Int,
    activeTabTitle: String?,
    statusMessage: String,
    isIndexing: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "标签数: $tabCount",
                style = JewelTheme.defaultTextStyle
            )
            
            activeTabTitle?.let {
                Text(
                    text = it,
                    style = JewelTheme.defaultTextStyle,
                    maxLines = 1
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isIndexing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = statusMessage,
                    style = JewelTheme.defaultTextStyle
                )
            }
        }
    }
}

/**
 * 导出对话框
 */
@Composable
private fun ExportDialog(
    tabs: List<com.claudecodeplus.ui.models.ChatTab>,
    exportService: ChatExportService,
    onDismiss: () -> Unit
) {
    var selectedTabs by remember { mutableStateOf(setOf<String>()) }
    var exportFormat by remember { mutableStateOf(ExportFormat.MARKDOWN) }
    var includeContext by remember { mutableStateOf(true) }
    var includeTimestamps by remember { mutableStateOf(true) }
    
    DialogWindow(
        onCloseRequest = onDismiss,
        title = "导出对话"
    ) {
        Column(
            modifier = Modifier
                .size(600.dp, 500.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 选择要导出的对话
            Text("选择要导出的对话", style = JewelTheme.defaultTextStyle)
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(tabs) { tab ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(4.dp)
                    ) {
                        Checkbox(
                            checked = tab.id in selectedTabs,
                            onCheckedChange = { checked ->
                                selectedTabs = if (checked) {
                                    selectedTabs + tab.id
                                } else {
                                    selectedTabs - tab.id
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tab.title)
                    }
                }
            }
            
            Divider(orientation = Orientation.Horizontal)
            
            // 导出选项
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text("导出格式", style = JewelTheme.defaultTextStyle)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = exportFormat == ExportFormat.MARKDOWN,
                            onClick = { exportFormat = ExportFormat.MARKDOWN }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Markdown")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = exportFormat == ExportFormat.HTML,
                            onClick = { exportFormat = ExportFormat.HTML }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("HTML")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = exportFormat == ExportFormat.JSON,
                            onClick = { exportFormat = ExportFormat.JSON }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("JSON")
                    }
                }
                
                Column {
                    Text("包含内容", style = JewelTheme.defaultTextStyle)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeContext,
                            onCheckedChange = { includeContext = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("上下文")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeTimestamps,
                            onCheckedChange = { includeTimestamps = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("时间戳")
                    }
                }
            }
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                DefaultButton(
                    onClick = {
                        // TODO: 实现导出功能
                        onDismiss()
                    },
                    enabled = selectedTabs.isNotEmpty()
                ) {
                    Text("导出")
                }
            }
        }
    }
}