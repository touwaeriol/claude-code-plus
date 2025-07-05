package com.claudecodeplus.test

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.session.models.SessionInfo
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text as JewelText

fun mainSessionTest() = application {
    IntUiTheme(isDark = true) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Session Manager Test"
        ) {
            TestSessionManagerContent()
        }
    }
}

@Composable
private fun TestSessionManagerContent() {
    val sessionManager = remember { ClaudeSessionManager() }
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // 使用当前项目路径
    val projectPath = "/Users/erio/codes/idea/claude-code-plus"
    
    // 自动加载会话
    LaunchedEffect(Unit) {
        println("Auto-loading sessions...")
        isLoading = true
        error = null
        try {
            sessions = sessionManager.getSessionList(projectPath)
            println("Auto-load complete: Found ${sessions.size} sessions")
        } catch (e: Exception) {
            error = e.message
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        JewelText("Session Manager Test")
        JewelText("Project Path: $projectPath")
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DefaultButton(
                onClick = {
                    scope.launch {
                        isLoading = true
                        error = null
                        try {
                            sessions = sessionManager.getSessionList(projectPath)
                            println("Found ${sessions.size} sessions")
                        } catch (e: Exception) {
                            error = e.message
                            e.printStackTrace()
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                JewelText("Load Sessions")
            }
            
            DefaultButton(
                onClick = {
                    scope.launch {
                        isLoading = true
                        error = null
                        try {
                            val newSessionId = sessionManager.createSession()
                            println("Created new session: $newSessionId")
                            sessions = sessionManager.getSessionList(projectPath)
                        } catch (e: Exception) {
                            error = e.message
                            e.printStackTrace()
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                JewelText("Create Session")
            }
        }
        
        if (isLoading) {
            JewelText("Loading...")
        }
        
        error?.let { 
            JewelText("Error: $it", color = JewelTheme.globalColors.text.error)
        }
        
        JewelText("Sessions: ${sessions.size}")
        
        sessions.forEach { session ->
            Column(
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                JewelText("ID: ${session.sessionId}")
                JewelText("Modified: ${java.time.Instant.ofEpochMilli(session.lastModified).toString()}")
                JewelText("Messages: ${session.messageCount}")
                session.lastMessage?.let {
                    JewelText("Last: $it")
                }
            }
        }
    }
}