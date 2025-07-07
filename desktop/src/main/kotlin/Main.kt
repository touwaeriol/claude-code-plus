package com.claudecodeplus.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.services.ProjectService
import com.claudecodeplus.ui.services.FileIndexService
import kotlinx.coroutines.launch

// 简单的ProjectService实现用于桌面应用
class DesktopProjectService(private val projectPath: String) : ProjectService {
    override fun getProjectPath(): String = projectPath
    override fun getProjectName(): String = projectPath.substringAfterLast('/')
    override fun openFile(filePath: String, lineNumber: Int?) {
        println("DesktopProjectService: 打开文件 $filePath:$lineNumber")
    }
    override fun showSettings(settingsId: String?) {
        println("DesktopProjectService: 显示设置 $settingsId")
    }
}

/**
 * Claude Code Plus 桌面应用主函数
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Claude Code Plus",
        state = rememberWindowState(
            width = 1000.dp,
            height = 800.dp
        )
    ) {
        IntUiTheme {
            // 创建必要的服务和管理器
            val cliWrapper = remember { ClaudeCliWrapper() }
            val projectPath = System.getProperty("user.dir")
            val sessionManager = remember { com.claudecodeplus.session.ClaudeSessionManager() }
            
            // 创建文件索引服务并初始化
            val fileIndexService = remember { SimpleFileIndexService() }
            val coroutineScope = rememberCoroutineScope()
            
            // 初始化文件索引
            LaunchedEffect(projectPath) {
                coroutineScope.launch {
                    fileIndexService.initialize(projectPath)
                }
            }
            
            // 显示简化的聊天界面
            com.claudecodeplus.ui.jewel.ChatView(
                cliWrapper = cliWrapper,
                workingDirectory = projectPath,
                fileIndexService = fileIndexService,
                projectService = remember { DesktopProjectService(projectPath) },
                sessionManager = sessionManager,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}