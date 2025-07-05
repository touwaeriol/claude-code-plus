package com.claudecodeplus.test

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.jewel.ChatAppWithSessions
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

fun main() = application {
    IntUiTheme(isDark = true) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Claude Code Plus - 会话管理测试"
        ) {
            val cliWrapper = ClaudeCliWrapper()
            val workingDirectory = "/Users/erio/codes/idea/claude-code-plus"
            
            // 打印调试信息
            LaunchedEffect(Unit) {
                println("=== Starting Session Management Test ===")
                println("Working Directory: $workingDirectory")
            }
            
            // 使用带会话管理的聊天应用
            ChatAppWithSessions(
                cliWrapper = cliWrapper,
                workingDirectory = workingDirectory
            )
        }
    }
}