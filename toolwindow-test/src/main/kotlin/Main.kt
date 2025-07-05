package com.claudecodeplus.test

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.services.ProjectService
import org.jetbrains.jewel.ui.component.Text

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
 * 主函数 - 会话管理功能测试
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Claude Code Plus - 会话管理测试",
        state = rememberWindowState(
            width = 1400.dp,
            height = 900.dp
        )
    ) {
        IntUiTheme {
            // 选择要测试的功能
            var currentTest by remember { mutableStateOf("session") }
            
            Column {
                // 顶部选择器
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { currentTest = "session" }
                    ) {
                        Text("完整会话管理")
                    }
                    Button(
                        onClick = { currentTest = "manager" }
                    ) {
                        Text("会话管理器测试")
                    }
                    Button(
                        onClick = { currentTest = "panel" }
                    ) {
                        Text("会话面板测试")
                    }
                    Button(
                        onClick = { currentTest = "cli" }
                    ) {
                        Text("会话CLI测试")
                    }
                    Button(
                        onClick = { currentTest = "debug" }
                    ) {
                        Text("会话调试")
                    }
                }
                
                Divider()
                
                // 显示选择的测试
                when (currentTest) {
                    "session" -> {
                        // 完整会话管理功能测试
                        val cliWrapper = remember { ClaudeCliWrapper() }
                        val projectPath = System.getProperty("user.dir")
                        val sessionManager = remember { com.claudecodeplus.session.ClaudeSessionManager() }
                        
                        com.claudecodeplus.ui.jewel.ChatAppWithSessions(
                            cliWrapper = cliWrapper,
                            workingDirectory = projectPath,
                            fileIndexService = null, // 会话测试不需要文件索引
                            projectService = remember { TestProjectService(projectPath) },
                            sessionManager = sessionManager,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    "manager" -> {
                        // 会话管理器基础测试
                        Text("请运行 TestSessionManager.kt 进行测试", modifier = Modifier.padding(16.dp))
                    }
                    "panel" -> {
                        // 会话面板组件测试
                        Text("请运行 TestSessionPanelOnly.kt 进行测试", modifier = Modifier.padding(16.dp))
                    }
                    "cli" -> {
                        // CLI功能测试
                        Text("请运行 TestSessionCLI.kt 进行测试", modifier = Modifier.padding(16.dp))
                    }
                    "debug" -> {
                        // 调试工具
                        Text("请运行 TestSessionDebug.kt 进行测试", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}