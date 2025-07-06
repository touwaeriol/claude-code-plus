package com.claudecodeplus.test

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

// 简单的ProjectService实现用于测试
class TestProjectService(private val projectPath: String) : ProjectService {
    override fun getProjectPath(): String = projectPath
    override fun getProjectName(): String = projectPath.substringAfterLast('/')
    override fun openFile(filePath: String, lineNumber: Int?) {
        println("TestProjectService: 打开文件 $filePath:$lineNumber")
    }
    override fun showSettings(settingsId: String?) {
        println("TestProjectService: 显示设置 $settingsId")
    }
}

/**
 * 主函数 - 完整会话管理功能测试
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Claude Code Plus - 简化版",
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
            
            // 显示简化的聊天界面
            com.claudecodeplus.ui.jewel.SimpleChatApp(
                cliWrapper = cliWrapper,
                workingDirectory = projectPath,
                fileIndexService = null, // 会话测试不需要文件索引
                projectService = remember { TestProjectService(projectPath) },
                sessionManager = sessionManager,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}