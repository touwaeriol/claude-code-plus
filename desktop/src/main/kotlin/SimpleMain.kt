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
import com.claudecodeplus.ui.components.MultiTabChatView

/**
 * 简化版桌面应用启动器
 */
fun main() = application {
    val projectPath = System.getProperty("user.dir")
    
    // 初始化服务
    ServiceContainer.initialize(projectPath)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Claude Code Plus - Desktop",
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp
        )
    ) {
        IntUiTheme {
            SimpleClaudeApp()
        }
    }
}

/**
 * 简化版应用主组件
 */
@Composable
fun SimpleClaudeApp() {
    // 从服务容器获取服务
    val unifiedSessionServiceProvider = ServiceContainer.unifiedSessionServiceProvider
    val tabManager = ServiceContainer.tabManager
    val fileIndexService = ServiceContainer.fileIndexService
    val projectService = ServiceContainer.projectService
    val sessionManager = ServiceContainer.sessionManager
    val scope = rememberCoroutineScope()
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            // 简单的标题栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Claude Code Plus - Desktop",
                    style = JewelTheme.defaultTextStyle
                )
            }
            
            Divider(orientation = Orientation.Horizontal)
            
            // 主聊天区域
            MultiTabChatView(
                tabManager = tabManager,
                unifiedSessionServiceProvider = unifiedSessionServiceProvider,
                workingDirectory = ServiceContainer.projectService.getProjectPath(),
                fileIndexService = fileIndexService,
                projectService = projectService,
                sessionManager = sessionManager,
                modifier = Modifier.weight(1f)
            )
        }
        
        // 简单的状态栏
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "标签数: ${tabManager.tabs.size}",
                style = JewelTheme.defaultTextStyle
            )
        }
    }
}