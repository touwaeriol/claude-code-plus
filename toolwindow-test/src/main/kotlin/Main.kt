package com.claudecodeplus.test

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.ComponentStyling
import com.claudecodeplus.ui.jewel.JewelChatApp
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.test.ContextSelectorTestApp
import com.claudecodeplus.test.SimpleFileIndexService
import com.claudecodeplus.ui.services.ProjectService
import com.claudecodeplus.ui.jewel.components.EnhancedSmartInputArea
import com.claudecodeplus.ui.models.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import kotlinx.coroutines.runBlocking

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

@Composable
@Preview
fun ModelSwitchTest() {
    var selectedModel by remember { mutableStateOf(AiModel.SONNET) }
    
    println("ModelSwitchTest: selectedModel = ${selectedModel.displayName}")
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("模型切换测试", style = MaterialTheme.typography.h4)
        
        Text("当前模型: ${selectedModel.displayName}")
        
        Button(onClick = {
            println("Button clicked - before: ${selectedModel.displayName}")
            selectedModel = when (selectedModel) {
                AiModel.SONNET -> AiModel.OPUS
                AiModel.OPUS -> AiModel.SONNET
            }
            println("Button clicked - after: ${selectedModel.displayName}")
        }) {
            Text("切换模型")
        }
        
        // 测试回调机制
        TestCallback(
            currentModel = selectedModel,
            onModelChange = { model ->
                println("TestCallback: onModelChange called with ${model.displayName}")
                selectedModel = model
            }
        )
    }
}

@Composable
fun TestCallback(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit
) {
    println("TestCallback: currentModel = ${currentModel.displayName}")
    
    Button(onClick = {
        println("TestCallback: Button clicked")
        val nextModel = when (currentModel) {
            AiModel.SONNET -> AiModel.OPUS
            AiModel.OPUS -> AiModel.SONNET
        }
        println("TestCallback: Calling onModelChange with ${nextModel.displayName}")
        onModelChange(nextModel)
    }) {
        Text("回调测试: ${currentModel.displayName}")
    }
}

/**
 * 测试新的上下文区分功能
 */
@Composable
fun ContextDifferentiationTestApp() {
    var inputText by remember { mutableStateOf("") }
    var contexts by remember { mutableStateOf(listOf<ContextReference>()) }
    val inlineReferenceManager = remember { InlineReferenceManager() }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "上下文区分测试",
            style = JewelTheme.defaultTextStyle.copy(fontSize = 18.sp)
        )
        
        Text(
            text = "使用方法:\n1. 点击 'Add Context' 按钮添加的上下文会显示为标签并在消息头部显示\n2. 在输入框中输入 @ 符号添加的上下文只在消息文本中显示，不会成为标签\n3. 两种方式提供不同的引用体验",
            style = JewelTheme.defaultTextStyle.copy(
                color = JewelTheme.globalColors.text.disabled
            )
        )
        
        // 统计显示
        val inlineRefs = inlineReferenceManager.getAllReferences()
        Text(
            text = "当前标签上下文: ${contexts.size}个（Add Context 添加的）\n内联引用: ${inlineRefs.size}个（@ 符号添加的）",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.disabled
            )
        )
        
        // 增强输入区域
        EnhancedSmartInputArea(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                println("==== 发送的完整消息 ====")
                println("原始消息文本: $inputText")
                
                // 展开内联引用
                val expandedText = inlineReferenceManager.expandInlineReferences(inputText)
                println("展开内联引用后: $expandedText")
                
                // 测试消息构建逻辑
                val finalMessage = buildFinalMessage(contexts, expandedText)
                println("构建后的完整消息:")
                println(finalMessage)
                println("========================")
                println("标签上下文: ${contexts.size}个（Add Context 添加的）")
                val inlineRefs = inlineReferenceManager.getAllReferences()
                println("内联引用: ${inlineRefs.size}个（@ 符号添加的）")
                inlineRefs.forEach { (displayText, ref) ->
                    println("  $displayText -> ${ref.relativePath}")
                }
                contexts.forEachIndexed { index, context ->
                    println("  ${index + 1}. ${context::class.simpleName}")
                }
                println("========================")
                
                // 重置输入和清空内联引用
                inputText = ""
                inlineReferenceManager.clear()
            },
            contexts = contexts,
            onContextAdd = { context ->
                contexts = contexts + context
                println("添加上下文: ${context::class.simpleName} (${context.displayType})")
            },
            onContextRemove = { context ->
                contexts = contexts - context
                println("移除上下文: ${context::class.simpleName}")
            },
            fileIndexService = remember { 
                runBlocking {
                    SimpleFileIndexService().apply {
                        initialize(System.getProperty("user.dir"))
                    }
                }
            },
            inlineReferenceManager = inlineReferenceManager,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        
        // 调试信息
        if (contexts.isNotEmpty() || inlineRefs.isNotEmpty()) {
            Text(
                text = "调试信息:",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                contexts.forEach { context ->
                    Text(
                        text = "• ${context::class.simpleName} (标签)",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 10.sp,
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
                inlineRefs.forEach { (displayText, ref) ->
                    Text(
                        text = "• $displayText -> ${ref.relativePath} (内联)",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 10.sp,
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
            }
        }
    }
}

/**
 * 测试消息格式构建功能
 */
fun testMessageFormat() {
    println("\n=== 测试消息格式构建功能 ===")
    
    // 测试用例1：无上下文
    val message1 = "你好，这是一个测试消息"
    val contexts1 = emptyList<ContextReference>()
    val result1 = buildFinalMessage(contexts1, message1)
    println("测试用例1 (无上下文):")
    println(result1)
    println()
    
    // 测试用例2：包含文件和网页上下文
    val contexts2 = listOf(
        ContextReference.FileReference(
            path = "src/main/kotlin/Main.kt",
            displayType = ContextDisplayType.TAG
        ),
        ContextReference.WebReference(
            url = "https://github.com/JetBrains/compose-multiplatform",
            title = "Compose Multiplatform",
            displayType = ContextDisplayType.TAG
        ),
        ContextReference.FolderReference(
            path = "src/main/kotlin",
            fileCount = 15,
            displayType = ContextDisplayType.TAG
        )
    )
    val message2 = "请帮我分析这些代码文件的结构"
    val result2 = buildFinalMessage(contexts2, message2)
    println("测试用例2 (多种上下文):")
    println(result2)
    println()
    
    println("=== 消息格式测试完成 ===\n")
}

/**
 * 构建包含上下文的完整消息 - 复制自toolwindow模块用于测试
 */
private fun buildFinalMessage(contexts: List<ContextReference>, userMessage: String): String {
    if (contexts.isEmpty()) {
        return userMessage
    }
    
    val contextSection = buildString {
        appendLine("> **上下文资料**")
        appendLine("> ")
        
        contexts.forEach { context ->
            val contextLine = when (context) {
                is ContextReference.FileReference -> {
                    "> - 📄 `${context.path}`"
                }
                is ContextReference.WebReference -> {
                    val title = context.title?.let { " ($it)" } ?: ""
                    "> - 🌐 ${context.url}$title"
                }
                is ContextReference.FolderReference -> {
                    "> - 📁 `${context.path}` (${context.fileCount}个文件)"
                }
                is ContextReference.SymbolReference -> {
                    "> - 🔗 `${context.name}` (${context.type}) - ${context.file}:${context.line}"
                }
                is ContextReference.TerminalReference -> {
                    val errorFlag = if (context.isError) " ⚠️" else ""
                    "> - 💻 终端输出 (${context.lines}行)$errorFlag"
                }
                is ContextReference.ProblemsReference -> {
                    val severityText = context.severity?.let { " [$it]" } ?: ""
                    "> - ⚠️ 问题报告 (${context.problems.size}个)$severityText"
                }
                is ContextReference.GitReference -> {
                    "> - 🔀 Git ${context.type}"
                }
                is ContextReference.ImageReference -> {
                    "> - 🖼 `${context.filename}` (${context.size / 1024}KB)"
                }
                is ContextReference.SelectionReference -> {
                    "> - ✏️ 当前选择内容"
                }
                is ContextReference.WorkspaceReference -> {
                    "> - 🏠 当前工作区"
                }
            }
            appendLine(contextLine)
        }
        
        appendLine()
    }
    
    return contextSection + userMessage
}

/**
 * 主函数
 */
fun main() {
    // 运行消息格式测试
    testMessageFormat()
    
    // 启动GUI应用
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Claude Code Plus 测试",
            state = rememberWindowState(
                width = 1200.dp,
                height = 800.dp
            )
        ) {
            IntUiTheme {
                // 选择要测试的功能
                var currentTest by remember { mutableStateOf("chat") }
                
                Column {
                    // 顶部选择器
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { currentTest = "chat" }
                        ) {
                            Text("聊天功能测试")
                        }
                        Button(
                            onClick = { currentTest = "context" }
                        ) {
                            Text("上下文功能测试")
                        }
                        Button(
                            onClick = { currentTest = "model" }
                        ) {
                            Text("模型切换测试")
                        }
                    }
                    
                    Divider()
                    
                    // 显示选择的测试
                    when (currentTest) {
                        "chat" -> {
                            // 主要聊天功能测试
                            val cliWrapper = remember { ClaudeCliWrapper() }
                            val projectPath = System.getProperty("user.dir")
                            
                            JewelChatApp(
                                cliWrapper = cliWrapper,
                                workingDirectory = projectPath,
                                fileIndexService = remember { 
                                    runBlocking {
                                        SimpleFileIndexService().apply {
                                            initialize(projectPath)
                                        }
                                    }
                                },
                                projectService = remember { TestProjectService(projectPath) },
                                showToolbar = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        "context" -> {
                            ContextDifferentiationTestApp()
                        }
                        "model" -> {
                            ModelSwitchTest()
                        }
                    }
                }
            }
        }
    }
} 