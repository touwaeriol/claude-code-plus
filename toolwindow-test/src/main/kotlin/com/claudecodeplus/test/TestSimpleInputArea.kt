/*
 * TestSimpleInputArea.kt
 * 
 * 测试简化的输入区域组件
 */

package com.claudecodeplus.test

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.claudecodeplus.ui.jewel.components.*
import com.claudecodeplus.ui.models.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "测试简化输入区域",
        state = rememberWindowState(
            width = 800.dp,
            height = 600.dp
        )
    ) {
        IntUiTheme(isDark = true) {
            TestSimpleInputAreaContent()
        }
    }
}

@Composable
private fun TestSimpleInputAreaContent() {
    var messages by remember { mutableStateOf(listOf<EnhancedMessage>()) }
    var contexts by remember { mutableStateOf(listOf<ContextReference>()) }
    var selectedModel by remember { mutableStateOf(AiModel.OPUS) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 测试数据按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DefaultButton(
                onClick = {
                    // 添加测试消息
                    val testMessage = EnhancedMessage(
                        id = System.currentTimeMillis().toString(),
                        role = MessageRole.USER,
                        content = "这是一个测试消息，包含 [@Main.kt](file:///path/to/Main.kt) 文件引用和 [@GitHub](https://github.com) 链接。",
                        timestamp = System.currentTimeMillis(),
                        model = selectedModel,
                        contexts = emptyList()
                    )
                    messages = messages + testMessage
                }
            ) {
                Text("添加测试消息")
            }
            
            DefaultButton(
                onClick = {
                    // 添加测试上下文
                    contexts = contexts + ContextReference.FileReference(
                        path = "src/main/kotlin/Main.kt",
                        fullPath = "/project/src/main/kotlin/Main.kt"
                    )
                }
            ) {
                Text("添加文件上下文")
            }
            
            DefaultButton(
                onClick = {
                    messages = emptyList()
                    contexts = emptyList()
                }
            ) {
                Text("清空")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 消息列表
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            messages.forEach { message ->
                UnifiedInputArea(
                    mode = InputAreaMode.DISPLAY,
                    message = message,
                    onContextClick = { uri ->
                        println("点击了上下文: $uri")
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 输入区域
        UnifiedInputArea(
            mode = InputAreaMode.INPUT,
            contexts = contexts,
            onContextAdd = { context ->
                contexts = contexts + context
            },
            onContextRemove = { context ->
                contexts = contexts - context
            },
            onSend = { markdownText ->
                println("发送消息: $markdownText")
                
                // 创建新消息
                val newMessage = EnhancedMessage(
                    id = System.currentTimeMillis().toString(),
                    role = MessageRole.USER,
                    content = markdownText,
                    timestamp = System.currentTimeMillis(),
                    model = selectedModel,
                    contexts = contexts
                )
                
                messages = messages + newMessage
                contexts = emptyList() // 清空上下文
            },
            selectedModel = selectedModel,
            onModelChange = { selectedModel = it },
            enabled = true,
            isGenerating = false
        )
    }
}

/**
 * 测试 Markdown 解析
 */
@Composable
private fun TestMarkdownParsing() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Markdown 解析测试", style = JewelTheme.defaultTextStyle)
        
        val testCases = listOf(
            "简单文本没有引用",
            "包含一个 [@文件引用](file:///path/to/file.txt) 的文本",
            "多个引用：[@Main.kt](file:///Main.kt) 和 [@GitHub](https://github.com)",
            "复杂路径 [@very/long/path/to/file.txt](file:///very/long/path/to/file.txt)"
        )
        
        testCases.forEach { testCase ->
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("原始文本:", style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.disabled
                ))
                Text(testCase)
                
                Text("解析后:", style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.disabled
                ))
                
                AnnotatedMessageDisplay(
                    message = testCase,
                    onContextClick = { uri ->
                        println("点击: $uri")
                    }
                )
            }
        }
    }
}