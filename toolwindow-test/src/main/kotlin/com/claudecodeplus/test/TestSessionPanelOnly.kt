package com.claudecodeplus.test

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.jewel.components.SessionListPanel
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

fun main() = application {
    IntUiTheme(isDark = true) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Session Panel Test"
        ) {
            val sessionManager = remember { ClaudeSessionManager() }
            val projectPath = "/Users/erio/codes/idea/claude-code-plus"
            
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                SessionListPanel(
                    projectPath = projectPath,
                    sessionManager = sessionManager,
                    currentSessionId = null,
                    onSessionSelect = { session ->
                        println("Selected session: ${session.sessionId}")
                    },
                    onNewSession = {
                        println("New session requested")
                    },
                    onDeleteSession = { session ->
                        println("Delete session: ${session.sessionId}")
                    }
                )
            }
        }
    }
}