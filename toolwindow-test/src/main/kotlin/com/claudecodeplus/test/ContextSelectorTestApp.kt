package com.claudecodeplus.test

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.material.Button
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.claudecodeplus.ui.jewel.components.context.*
import com.claudecodeplus.ui.models.ContextReference
import com.claudecodeplus.ui.models.ContextDisplayType
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp

/**
 * 上下文选择器测试应用
 * 用于独立测试上下文选择功能
 */
@Composable
fun ContextSelectorTestApp() {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var contextState by remember { mutableStateOf<ContextSelectionState>(ContextSelectionState.Hidden) }
    var selectedResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedContexts by remember { mutableStateOf<List<ContextReference>>(emptyList()) }
    var atSymbolPosition by remember { mutableStateOf<IntOffset?>(null) }
    
    val searchService = remember { MockContextSearchService() }
    val config = remember { ContextSelectorConfig() }
    
    // 检测@符号触发
    LaunchedEffect(textFieldValue) {
        val text = textFieldValue.text
        val cursorPos = textFieldValue.selection.start
        
        if (detectAtTrigger(text, cursorPos)) {
            contextState = ContextSelectionState.SelectingType
            // 在实际应用中，这里需要计算@符号在屏幕上的真实位置
            // 这里使用模拟位置
            atSymbolPosition = IntOffset(300, 200)
        } else if (contextState !is ContextSelectionState.Hidden) {
            // 如果当前正在选择但@触发条件不满足，检查是否应该继续
            val atPos = getAtSymbolPosition(text, cursorPos)
            if (atPos == null) {
                contextState = ContextSelectionState.Hidden
                atSymbolPosition = null
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Text(
                text = "上下文选择器测试",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 18.sp
                )
            )
            
            // 说明
            Text(
                text = "在下方输入框中输入 @ 符号（前后有空格或在开头）来触发上下文选择功能",
                style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.disabled
                )
            )
            
            // 输入框
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                },
                textStyle = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.normal
                ),
                cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .background(
                                JewelTheme.globalColors.panelBackground,
                                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp)
                    ) {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                "在这里输入 @ 来测试上下文选择...",
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = JewelTheme.globalColors.text.disabled
                                )
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            // 上下文标签测试区域
            ContextTagTestArea(
                contexts = selectedContexts,
                onRemove = { context ->
                    selectedContexts = selectedContexts - context
                },
                onAddTest = { context ->
                    selectedContexts = selectedContexts + context
                }
            )
            
            // 状态显示
            TestStatusDisplay(
                textFieldValue = textFieldValue,
                contextState = contextState,
                atSymbolPosition = atSymbolPosition
            )
            
            // 选择结果历史
            SelectedResultsHistory(
                results = selectedResults,
                modifier = Modifier.weight(1f)
            )
        }
        
        // 上下文选择器弹出框
        if (atSymbolPosition != null) {
            ContextSelectorPopup(
                visible = contextState !is ContextSelectionState.Hidden,
                anchorPosition = atSymbolPosition!!,
                state = contextState,
                config = config,
                searchService = searchService,
                onStateChange = { newState ->
                    contextState = newState
                },
                onResult = { result ->
                    when (result) {
                        is ContextSelectionResult.FileSelected -> {
                            val reference = createContextReference(result)
                            selectedResults = selectedResults + "文件: ${result.item.name} -> $reference"
                            
                            // 替换文本中的@符号
                            val atPos = getAtSymbolPosition(textFieldValue.text, textFieldValue.selection.start)
                            if (atPos != null) {
                                val newText = textFieldValue.text.replaceRange(atPos, atPos + 1, reference)
                                textFieldValue = textFieldValue.copy(
                                    text = newText,
                                    selection = androidx.compose.ui.text.TextRange(atPos + reference.length)
                                )
                            }
                        }
                        is ContextSelectionResult.WebSelected -> {
                            val reference = createContextReference(result)
                            selectedResults = selectedResults + "网页: ${result.item.title ?: result.item.url} -> $reference"
                            
                            // 替换文本中的@符号
                            val atPos = getAtSymbolPosition(textFieldValue.text, textFieldValue.selection.start)
                            if (atPos != null) {
                                val newText = textFieldValue.text.replaceRange(atPos, atPos + 1, reference)
                                textFieldValue = textFieldValue.copy(
                                    text = newText,
                                    selection = androidx.compose.ui.text.TextRange(atPos + reference.length)
                                )
                            }
                        }
                        is ContextSelectionResult.Cancelled -> {
                            selectedResults = selectedResults + "操作已取消"
                        }
                    }
                    
                    // 重置状态
                    contextState = ContextSelectionState.Hidden
                    atSymbolPosition = null
                }
            )
        }
    }
}

/**
 * 测试状态显示组件
 */
@Composable
private fun TestStatusDisplay(
    textFieldValue: TextFieldValue,
    contextState: ContextSelectionState,
    atSymbolPosition: IntOffset?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                JewelTheme.globalColors.panelBackground,
                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "调试信息",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.disabled
            )
        )
        
        Text(text = "文本内容: \"${textFieldValue.text}\"")
        Text(text = "光标位置: ${textFieldValue.selection.start}")
        Text(text = "选择状态: ${contextState::class.simpleName}")
        Text(text = "@符号位置: $atSymbolPosition")
        
        val cursorPos = textFieldValue.selection.start
        val isAtTriggerDetected = detectAtTrigger(textFieldValue.text, cursorPos)
        Text(
            text = "@触发检测: $isAtTriggerDetected",
            style = JewelTheme.defaultTextStyle.copy(
                color = if (isAtTriggerDetected) {
                    Color(0xFF4CAF50)
                } else {
                    JewelTheme.globalColors.text.normal
                }
            )
        )
    }
}

/**
 * 上下文标签测试区域
 */
@Composable
private fun ContextTagTestArea(
    contexts: List<ContextReference>,
    onRemove: (ContextReference) -> Unit,
    onAddTest: (ContextReference) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                JewelTheme.globalColors.panelBackground,
                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "上下文标签测试 (${contexts.size})",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.disabled
            )
        )
        
        // 测试按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onAddTest(ContextReference.FileReference(
                        path = "src/main/kotlin/example/Example.kt",
                        fullPath = "/absolute/path/to/src/main/kotlin/example/Example.kt",
                        displayType = ContextDisplayType.TAG
                    ))
                }
            ) {
                androidx.compose.material.Text("添加文件(TAG)")
            }
            
            Button(
                onClick = {
                    onAddTest(ContextReference.FileReference(
                        path = "src/main/kotlin/example/InlineExample.kt",
                        fullPath = "/absolute/path/to/src/main/kotlin/example/InlineExample.kt",
                        displayType = ContextDisplayType.INLINE
                    ))
                }
            ) {
                androidx.compose.material.Text("添加文件(INLINE)")
            }
            
            Button(
                onClick = {
                    onAddTest(ContextReference.WebReference(
                        url = "https://github.com/JetBrains/compose-jb",
                        title = "Compose Multiplatform | JetBrains",
                        displayType = ContextDisplayType.TAG
                    ))
                }
            ) {
                androidx.compose.material.Text("添加Web(TAG)")
            }
        }
        
        // 显示上下文分类统计
        val tagContexts = contexts.filter { it.displayType == ContextDisplayType.TAG }
        val inlineContexts = contexts.filter { it.displayType == ContextDisplayType.INLINE }
        
        Text(
            text = "TAG类型: ${tagContexts.size}个, INLINE类型: ${inlineContexts.size}个",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 10.sp,
                color = JewelTheme.globalColors.text.disabled
            )
        )
        
        // 上下文标签显示
        ContextTagList(
            contexts = contexts,
            onRemove = onRemove,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 选择结果历史组件
 */
@Composable
private fun SelectedResultsHistory(
    results: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "选择历史 (${results.size})",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.disabled
            )
        )
        
        if (results.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        JewelTheme.globalColors.panelBackground,
                        androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
            ) {
                Text(
                    text = "尚无选择结果",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = JewelTheme.globalColors.text.disabled
                    )
                )
            }
        } else {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            JewelTheme.globalColors.panelBackground,
                            androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    results.reversed().forEachIndexed { index, result ->
                        Text(
                            text = "${results.size - index}. $result",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 预览组件
 */
@Preview
@Composable
fun ContextSelectorTestAppPreview() {
    IntUiTheme {
        ContextSelectorTestApp()
    }
}

/**
 * 主函数 - 独立运行测试应用
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "上下文选择器测试",
        state = androidx.compose.ui.window.rememberWindowState(
            width = 800.dp,
            height = 600.dp
        )
    ) {
        IntUiTheme {
            ContextSelectorTestApp()
        }
    }
} 