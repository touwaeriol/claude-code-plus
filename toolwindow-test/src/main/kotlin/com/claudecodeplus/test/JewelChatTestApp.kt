package com.claudecodeplus.test

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.jewel.JewelConversationView
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling

/**
 * Jewel Chat 测试应用
 * 完全基于 Compose Desktop 和 Jewel UI
 */
fun main() = application {
    // 获取工作目录
    val workingDirectory = System.getProperty("project.root", System.getProperty("user.dir"))
    
    // 创建 CLI Wrapper
    println("DEBUG: Creating ClaudeCliWrapper...")
    val cliWrapper = ClaudeCliWrapper()
    println("DEBUG: ClaudeCliWrapper created successfully")
    
    // 创建文件索引服务
    val fileIndexService = CustomFileIndexService()
    
    // 索引工作目录
    fileIndexService.indexFiles(workingDirectory)
    
    // 窗口状态
    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 800.dp
    )
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Claude Code Plus - Test",
        state = windowState
    ) {
        // 使用 Jewel 主题
        IntUiTheme(
            theme = JewelTheme.darkThemeDefinition(),
            styling = ComponentStyling.provide()
        ) {
            // 使用新的对话视图
            JewelConversationView(
                cliWrapper = cliWrapper,
                workingDirectory = workingDirectory,
                fileIndexService = fileIndexService
            )
        }
    }
}