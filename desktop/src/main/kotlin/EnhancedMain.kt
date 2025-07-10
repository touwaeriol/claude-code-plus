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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.components.MultiTabChatView
import com.claudecodeplus.ui.components.GlobalSearchDialog
import com.claudecodeplus.ui.components.ChatOrganizer
import com.claudecodeplus.ui.services.ChatTabManager
import com.claudecodeplus.ui.services.ChatExportService
import com.claudecodeplus.ui.services.PromptTemplateManager
import com.claudecodeplus.ui.services.ContextTemplateManager
import com.claudecodeplus.ui.models.ExportFormat
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

/**
 * 增强版桌面应用主函数 - 包含所有新功能
 */
fun main() = application {
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
 * 增强版应用主组件
 */
@Composable
fun EnhancedClaudeApp() {
    // 核心服务
    val cliWrapper = remember { ClaudeCliWrapper() }
    val sessionManager = remember { ClaudeSessionManager() }
    val projectPath = remember { System.getProperty("user.dir") }
    
    // 新增服务
    val tabManager = remember { ChatTabManager() }
    val exportService = remember { ChatExportService() }
    val templateManager = remember { PromptTemplateManager() }
    val contextTemplateManager = remember { ContextTemplateManager() }
    
    // 文件索引服务
    val fileIndexService = remember { SimpleFileIndexService() }
    val projectService = remember { DesktopProjectService(projectPath) }
    
    // UI 状态
    var showOrganizer by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // 初始化
    LaunchedEffect(projectPath) {
        scope.launch {
            fileIndexService.initialize(projectPath)
            // 创建默认标签
            if (tabManager.tabs.isEmpty()) {
                tabManager.createNewTab("欢迎使用 Claude Code Plus")
            }
        }
    }
    
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
                        showOrganizer = true
                        true
                    }
                    // Ctrl+F: 全局搜索
                    event.type == KeyEventType.KeyDown && 
                    event.key == Key.F && 
                    event.isCtrlPressed -> {
                        showSearch = true
                        true
                    }
                    // Ctrl+P: 提示词模板
                    event.type == KeyEventType.KeyDown && 
                    event.key == Key.P && 
                    event.isCtrlPressed -> {
                        showTemplates = true
                        true
                    }
                    // Ctrl+E: 导出
                    event.type == KeyEventType.KeyDown && 
                    event.key == Key.E && 
                    event.isCtrlPressed -> {
                        showExportDialog = true
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
                onOrganize = { showOrganizer = true },
                onSearch = { showSearch = true },
                onTemplates = { showTemplates = true },
                onExport = { showExportDialog = true }
            )
            
            Divider(orientation = Orientation.Horizontal)
            
            // 主内容区域
            MultiTabChatView(
                tabManager = tabManager,
                cliWrapper = cliWrapper,
                workingDirectory = projectPath,
                fileIndexService = fileIndexService,
                projectService = projectService,
                sessionManager = sessionManager,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // 状态栏
        StatusBar(
            tabCount = tabManager.tabs.size,
            activeTabTitle = tabManager.tabs.find { it.id == tabManager.activeTabId }?.title,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    
    // 对话框
    if (showOrganizer) {
        ChatOrganizer(
            tabManager = tabManager,
            onTabSelect = { tabId ->
                tabManager.setActiveTab(tabId)
                showOrganizer = false
            },
            onClose = { showOrganizer = false }
        )
    }
    
    if (showSearch) {
        GlobalSearchDialog(
            tabManager = tabManager,
            onSelectResult = { chatId, messageId ->
                tabManager.setActiveTab(chatId)
                showSearch = false
                // TODO: 滚动到特定消息
            },
            onDismiss = { showSearch = false }
        )
    }
    
    // TODO: PromptTemplateDialog 已被移动，暂时注释
    /*if (showTemplates) {
        PromptTemplateDialog(
            templateManager = templateManager,
            onSelectTemplate = { template ->
                // TODO: 应用模板到当前输入
                showTemplates = false
            },
            onDismiss = { showTemplates = false }
        )
    }*/
    
    if (showExportDialog) {
        ExportDialog(
            tabs = tabManager.tabs,
            exportService = exportService,
            onDismiss = { showExportDialog = false }
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
            
            activeTabTitle?.let { title ->
                Text(
                    text = title,
                    style = JewelTheme.defaultTextStyle,
                    maxLines = 1
                )
            }
            
            Text(
                text = "就绪",
                style = JewelTheme.defaultTextStyle
            )
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