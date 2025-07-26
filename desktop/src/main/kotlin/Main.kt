package com.claudecodeplus.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
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
import com.claudecodeplus.ui.services.ChatTabManager
import com.claudecodeplus.ui.models.ExportFormat
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.components.ProjectListPanel
import com.claudecodeplus.ui.components.SessionListPanel
import com.claudecodeplus.ui.components.ProjectTabBar
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.claudecodeplus.ui.services.SessionHistoryService
import com.claudecodeplus.ui.services.SessionLoader
import com.claudecodeplus.ui.services.MessageProcessor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import javax.swing.JFileChooser
import java.io.File
import org.jetbrains.jewel.ui.component.CircularProgressIndicator

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
 * 增强版应用主组件
 */
@Composable
fun EnhancedClaudeApp() {
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
    val scope = rememberCoroutineScope()
    
    // 创建流式加载服务
    val sessionHistoryService = remember { SessionHistoryService() }
    val messageProcessor = remember { MessageProcessor() }
    val sessionLoader = remember { SessionLoader(sessionHistoryService, messageProcessor) }
    
    // 在首次加载时，确保加载当前项目的会话
    LaunchedEffect(Unit) {
        println("[DEBUG] LaunchedEffect - 准备加载当前工作目录项目")
        projectManager.loadCurrentWorkingDirectoryProject()
        println("[DEBUG] LaunchedEffect - 调用 loadCurrentWorkingDirectoryProject 完成")
    }
    
    // 监听会话加载事件
    LaunchedEffect(projectManager) {
        projectManager.sessionLoadEvent.collect { event ->
            println("收到会话加载事件: ${event.session.id} - ${event.session.name}")
            // 加载会话历史
            val session = event.session
            val currentProject = projectManager.currentProject.value
            if (currentProject != null) {
                try {
                    println("开始加载会话消息: sessionId=${session.id}, projectPath=${currentProject.path}")
                    
                    // 尝试获取会话文件
                    println("查找会话文件，项目路径: ${currentProject.path}")
                    val sessionFiles = sessionHistoryService.getSessionFiles(currentProject.path)
                    println("找到 ${sessionFiles.size} 个会话文件")
                    sessionFiles.forEach { 
                        println("  - ${it.name} (${it.size} bytes)")
                    }
                    
                    val sessionFile = session.id?.let { id ->
                        sessionFiles.find { it.name.startsWith(id) }?.file
                    }
                    
                    if (sessionFile != null) {
                        println("使用流式加载会话文件: ${sessionFile.name}")
                        
                        // 收集所有消息
                        val allMessages = mutableListOf<EnhancedMessage>()
                        
                        // 使用新的流式加载
                        sessionLoader.loadSessionAsMessageFlow(sessionFile, maxMessages = 50)
                            .collect { result ->
                                when (result) {
                                    is SessionLoader.LoadResult.MessageCompleted -> {
                                        allMessages.add(result.message)
                                        println("加载消息: ${result.message.role} - ${result.message.content.take(50)}...")
                                        
                                        // 如果是助手消息，打印工具调用信息
                                        if (result.message.toolCalls.isNotEmpty()) {
                                            println("  工具调用: ${result.message.toolCalls.map { it.name }}")
                                        }
                                    }
                                    is SessionLoader.LoadResult.LoadComplete -> {
                                        println("历史会话加载完成，共 ${result.messages.size} 条消息")
                                    }
                                    is SessionLoader.LoadResult.Error -> {
                                        println("加载历史会话出错: ${result.error}")
                                    }
                                    else -> {}
                                }
                            }
                        
                        println("加载了 ${allMessages.size} 条消息")
                        println("DEBUG: 传递的会话对象 - ID: ${session.id}, Name: '${session.name}', ProjectId: ${session.projectId}")
                        
                        // 创建或切换到该会话的标签
                        if (allMessages.isNotEmpty()) {
                            println("准备创建标签，传递的 session 对象:")
                            println("  - session.id: ${session.id}")
                            println("  - session.name: '${session.name}'")
                            println("  - session.projectId: ${session.projectId}")
                            println("  - allMessages.size: ${allMessages.size}")
                            
                            val tabId = tabManager.createOrSwitchToSessionTab(session, allMessages, currentProject)
                            println("创建或切换到标签: $tabId")
                        } else {
                            println("会话没有消息")
                        }
                    } else {
                        println("未找到会话文件，使用原方法加载")
                        // 使用原方法作为后备（仅当session.id不为null时）
                        val sessionId = session.id
                        if (sessionId != null) {
                            val sessionMessages = sessionManager.readSessionMessagesFlow(
                                sessionId = sessionId,
                                projectPath = currentProject.path,
                                pageSize = 50
                            )
                        
                            val allMessages = mutableListOf<EnhancedMessage>()
                            sessionMessages.collect { messages ->
                                allMessages.addAll(messages)
                            }
                            
                            if (allMessages.isNotEmpty()) {
                                val tabId = tabManager.createOrSwitchToSessionTab(session, allMessages, currentProject)
                                println("创建或切换到标签: $tabId")
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("加载会话历史失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
    
    // 监听标签管理器事件
    LaunchedEffect(tabManager) {
        tabManager.events.collect { event ->
            when (event) {
                is ChatTabManager.TabEvent.SessionSwitchRequested -> {
                    println("收到会话切换请求: ${event.sessionId}")
                    // 查找对应的会话并更新 ProjectManager
                    val currentProject = projectManager.currentProject.value
                    if (currentProject != null) {
                        val sessions = projectManager.sessions.value[currentProject.id]
                        val session = sessions?.find { it.id == event.sessionId }
                        if (session != null) {
                            println("切换到会话: ${session.name}")
                            projectManager.setCurrentSession(session, loadHistory = true)
                        }
                    }
                }
                else -> {
                    // 忽略其他事件
                }
            }
        }
    }
    
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
                    // Ctrl+R: 刷新会话名称
                    event.type == KeyEventType.KeyDown && 
                    event.key == Key.R && 
                    event.isCtrlPressed && 
                    !event.isShiftPressed -> {
                        println("刷新所有会话名称...")
                        scope.launch {
                            projectManager.refreshAllSessionNames()
                            
                            // 等待刷新完成后，更新标签标题
                            kotlinx.coroutines.delay(500)
                            
                            // 遍历所有标签，更新标题
                            tabManager.tabs.forEach { tab ->
                                if (tab.sessionId != null) {
                                    // 查找对应的会话
                                    val sessions = projectManager.sessions.value[tab.projectId]
                                    val session = sessions?.find { it.id == tab.sessionId }
                                    if (session != null) {
                                        val project = projectManager.projects.value.find { it.id == tab.projectId }
                                        val projectShortName = project?.name?.let { name ->
                                            if (name.length <= 4) name
                                            else name.split(" ", "-", "_").map { it.firstOrNull()?.uppercaseChar() ?: "" }.joinToString("")
                                                .ifEmpty { name.take(4) }
                                        }
                                        
                                        val newTitle = if (projectShortName != null) {
                                            "[$projectShortName] ${session.name}"
                                        } else {
                                            session.name
                                        }
                                        
                                        if (tab.title != newTitle) {
                                            println("更新标签标题: '${tab.title}' -> '$newTitle'")
                                            tabManager.renameTab(tab.id, newTitle)
                                        }
                                    }
                                }
                            }
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
                onExport = { uiState.isExportDialogVisible = true },
                onSettings = { uiState.isSettingsVisible = true }
            )
            
            Divider(orientation = Orientation.Horizontal)
            
            // 项目标签栏
            val projects by projectManager.projects.collectAsState()
            val sessions by projectManager.sessions.collectAsState()
            val currentProject by projectManager.currentProject
            
            // 计算每个项目的会话数量
            val sessionCounts = remember(sessions) {
                sessions.mapValues { (_, sessionList) -> sessionList.size }
            }
            
            ProjectTabBar(
                projects = projects,
                currentProjectId = currentProject?.id,
                sessionCounts = sessionCounts,
                onProjectSelect = { project -> projectManager.setCurrentProject(project) },
                onProjectClose = if (projects.size > 1) {
                    { project ->
                        scope.launch {
                            projectManager.deleteProject(project)
                        }
                    }
                } else null,
                onNewProject = {
                    // 直接弹出文件夹选择器
                    val fileChooser = JFileChooser().apply {
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        dialogTitle = "选择项目文件夹"
                        currentDirectory = File(System.getProperty("user.home"))
                    }
                    
                    val result = fileChooser.showOpenDialog(null)
                    if (result == JFileChooser.APPROVE_OPTION) {
                        val selectedPath = fileChooser.selectedFile.absolutePath
                        val projectName = fileChooser.selectedFile.name
                        
                        scope.launch {
                            val project = projectManager.createProject(projectName, selectedPath)
                            // 如果是新项目，创建新标签
                            if (tabManager.tabs.none { it.projectId == project.id }) {
                                tabManager.createNewTab(
                                    title = "新对话",
                                    project = project
                                )
                            }
                        }
                    }
                }
            )
            
            Divider(orientation = Orientation.Horizontal)
            
            // 主内容区域：左侧会话面板 + 右侧聊天区域
            Row(modifier = Modifier.fillMaxSize()) {
                val currentSession by projectManager.currentSession
                
                // 悬停状态管理
                var hoveredSessionId by remember { mutableStateOf<String?>(null) }

                // 只显示当前项目的会话
                println("[DEBUG] SessionListPanel - currentProject: ${currentProject?.id} (${currentProject?.name})")
                val project = currentProject
                if (project != null) {
                    println("[DEBUG] 创建 SessionListPanel - project: ${project.id}, onCreateSession 不为 null: ${true}")
                    SessionListPanel(
                        projectManager = projectManager,
                        tabManager = tabManager,
                        currentProject = project,
                        selectedSession = currentSession,
                        hoveredSessionId = hoveredSessionId,
                        onSessionSelect = { session -> projectManager.setCurrentSession(session, loadHistory = true) },
                        onSessionHover = { sessionId -> hoveredSessionId = sessionId },
                        onCreateSession = { 
                            scope.launch {
                                // 创建占位会话
                                val newSession = projectManager.createPlaceholderSession(project.id)
                                // 创建新标签并关联会话
                                tabManager.createNewTab(
                                    title = newSession.name,
                                    sessionId = newSession.id,
                                    project = project
                                )
                                // 设置为当前会话
                                projectManager.setCurrentSession(newSession, loadHistory = false)
                            }
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(280.dp) // 固定宽度，更适合会话列表
                    )

                    Divider(orientation = Orientation.Vertical)
                } else {
                    // 项目加载中的占位面板
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(280.dp)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "正在加载项目...",
                            style = JewelTheme.defaultTextStyle,
                            color = JewelTheme.globalColors.text.disabled
                        )
                    }
                    
                    Divider(orientation = Orientation.Vertical)
                }

                // 主聊天区域
                MultiTabChatView(
                    tabManager = tabManager,
                    cliWrapper = cliWrapper,
                    workingDirectory = currentProject?.path ?: projectService.getProjectPath(),
                    fileIndexService = fileIndexService,
                    projectService = projectService,
                    sessionManager = sessionManager,
                    projectManager = projectManager,
                    onTabHover = { sessionId -> hoveredSessionId = sessionId },
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
    
    if (uiState.isExportDialogVisible) {
        SimpleExportDialog(
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
    onExport: () -> Unit,
    onSettings: () -> Unit
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
                
                IconButton(onClick = onSettings) {
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
 * 简化的导出对话框
 */
@Composable
private fun SimpleExportDialog(
    tabs: List<com.claudecodeplus.ui.models.ChatTab>,
    exportService: ChatExportService,
    onDismiss: () -> Unit
) {
    var selectedTabs by remember { mutableStateOf(setOf<String>()) }
    var exportFormat by remember { mutableStateOf(ExportFormat.MARKDOWN) }
    
    // 默认选择所有标签
    LaunchedEffect(tabs) {
        selectedTabs = tabs.map { it.id }.toSet()
    }
    
    androidx.compose.ui.window.DialogWindow(
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
            
            // 导出格式选择
            Text("导出格式", style = JewelTheme.defaultTextStyle)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                        println("导出 ${selectedTabs.size} 个标签，格式: $exportFormat")
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