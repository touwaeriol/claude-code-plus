package com.claudecodeplus.test

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.jewel.*
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.ComponentStyling

/**
 * Jewel Chat 测试应用
 * 
 * 注意：测试应用不包含业务逻辑，所有业务逻辑都在 toolwindow 模块的 JewelChatApp 组件中实现。
 * 这个测试应用只负责创建窗口和提供基本的测试环境。
 */
fun main() = application {
    // 获取工作目录
    val workingDirectory = System.getProperty("project.root", System.getProperty("user.dir"))
    
    // 创建 CLI Wrapper
    println("DEBUG: Creating ClaudeCliWrapper...")
    val cliWrapper = ClaudeCliWrapper()
    println("DEBUG: ClaudeCliWrapper created successfully")
    
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
        // 创建主题提供者
        val themeProvider = remember { DefaultJewelThemeProvider() }
        var currentThemeStyle by remember { mutableStateOf(JewelThemeStyle.LIGHT) }
        
        // 使用 Jewel 主题
        IntUiTheme(
            theme = JewelThemeUtils.getThemeDefinition(themeProvider),
            styling = ComponentStyling.provide()
        ) {
            // 使用 toolwindow 模块中的完整聊天应用组件
            // 这里不包含任何业务逻辑，只是简单地使用现成的组件
            JewelChatApp(
                cliWrapper = cliWrapper,
                workingDirectory = workingDirectory,
                themeProvider = themeProvider,
                showToolbar = true, // 测试应用显示工具栏用于主题切换
                onThemeChange = { newTheme ->
                    currentThemeStyle = newTheme
                    (themeProvider as DefaultJewelThemeProvider).updateThemeStyle(newTheme)
                }
            )
        }
    }
} 