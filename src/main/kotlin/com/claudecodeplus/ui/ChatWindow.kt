package com.claudecodeplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import javax.swing.JComponent

class ChatWindow(private val project: Project) {
    
    fun createComponent(): JComponent {
        return ComposePanel().apply {
            setBounds(0, 0, 400, 600)
            setContent {
                ClaudeCodeTheme {
                    ChatContent()
                }
            }
        }
    }
    
    @Composable
    private fun ChatContent() {
        var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
        var inputText by remember { mutableStateOf("") }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(JBColor.background().rgb))
        ) {
            // 标题栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Claude Code Chat",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // 消息列表
            MessageList(
                messages = messages,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            
            // 输入区域
            InputField(
                value = inputText,
                onValueChange = { inputText = it },
                onSendMessage = {
                    if (inputText.isNotBlank()) {
                        messages = messages + ChatMessage(
                            content = inputText,
                            isUser = true,
                            timestamp = System.currentTimeMillis()
                        )
                        // TODO: 发送消息到 Claude Code
                        inputText = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ClaudeCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (JBColor.isBright()) lightColorScheme() else darkColorScheme(),
        content = content
    )
}

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long
)