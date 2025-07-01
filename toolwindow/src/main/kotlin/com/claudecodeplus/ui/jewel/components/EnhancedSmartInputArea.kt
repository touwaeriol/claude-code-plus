/*
 * EnhancedSmartInputArea.kt
 * 
 * 智能输入框组件 - 包含光标跟随的上下文菜单功能
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.theme.textAreaStyle
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import androidx.compose.foundation.rememberScrollState

/**
 * 增强的智能输入区域组件
 */
@Composable
fun EnhancedSmartInputArea(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: (() -> Unit)? = null,
    contexts: List<ContextReference> = emptyList(),
    onContextAdd: (ContextReference) -> Unit = {},
    onContextRemove: (ContextReference) -> Unit = {},
    isGenerating: Boolean = false,
    enabled: Boolean = true,
    selectedModel: AiModel = AiModel.OPUS,
    onModelChange: (AiModel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf(TextFieldValue(text)) }
    
    // 调试输出
    println("EnhancedSmartInputArea: selectedModel = ${selectedModel.displayName}")
    
    // 同步外部text参数到内部状态
    LaunchedEffect(text) {
        if (text != textValue.text) {
            textValue = TextFieldValue(text, TextRange(text.length))
        }
    }
    
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // 动态高度计算
    val lineHeight = with(density) { 18.sp.toDp() }
    val minHeight = 40.dp
    val maxHeight = 120.dp
    val textLines = textValue.text.count { it == '\n' } + 1
    val additionalHeight = lineHeight * (textLines - 1)
    val dynamicHeight = (minHeight + additionalHeight).coerceIn(minHeight, maxHeight)
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 生成状态指示器
        if (isGenerating) {
            GeneratingIndicator(
                onStop = { onStop?.invoke() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 已选择的上下文显示
        if (contexts.isNotEmpty()) {
            HorizontallyScrollableContainer(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    contexts.forEach { context ->
                        Box(
                            modifier = Modifier
                                .background(
                                    JewelTheme.globalColors.borders.focused,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .clickable { onContextRemove(context) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    when (context) {
                                        is ContextReference.FileReference -> "📄"
                                        is ContextReference.GitReference -> "🔀"
                                        else -> "📎"
                                    },
                                    style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp)
                                )
                                Text(
                                    when (context) {
                                        is ContextReference.FileReference -> context.path.substringAfterLast('/')
                                        is ContextReference.GitReference -> context.content
                                        else -> "未知"
                                    },
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = 11.sp,
                                        color = JewelTheme.globalColors.text.normal
                                    )
                                )
                                Text(
                                    "×",
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
        }
        
        // 主输入框容器 - 统一背景，包含所有控件
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    JewelTheme.globalColors.panelBackground, // 使用Jewel主题的面板背景色
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 顶部工具栏：Add Context按钮（左）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add Context 按钮
                    Box(
                        modifier = Modifier
                            .background(Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable(enabled = enabled && !isGenerating) {
                                // 添加上下文功能
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("📎", style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp))
                            Text(
                                "Add Context",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 12.sp,
                                    color = JewelTheme.globalColors.text.disabled
                                )
                            )
                        }
                    }
                }
                
                // 主输入框 - 去掉边框，使用透明背景
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight, max = maxHeight)
                ) {
                    TextArea(
                        value = textValue,
                        onValueChange = { newValue ->
                            textValue = newValue
                            onTextChange(newValue.text)
                        },
                        enabled = enabled,
                        undecorated = true, // 去掉边框和装饰
                        maxLines = Int.MAX_VALUE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent) // 透明背景
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                when {
                                    keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown -> {
                                        if (keyEvent.isShiftPressed) {
                                            // Shift+Enter: 我们主动处理换行
                                            val currentText = textValue.text
                                            val currentSelection = textValue.selection
                                            val newText = currentText.substring(0, currentSelection.start) + 
                                                         "\n" + 
                                                         currentText.substring(currentSelection.end)
                                            val newSelection = TextRange(currentSelection.start + 1)
                                            textValue = TextFieldValue(newText, newSelection)
                                            onTextChange(newText)
                                            true // 消费事件，阻止系统处理
                                        } else {
                                            // Enter: 发送消息，阻止系统的换行处理
                                            if (textValue.text.isNotBlank() && enabled && !isGenerating) {
                                                onSend()
                                                // 清空输入框
                                                textValue = TextFieldValue("")
                                                onTextChange("")
                                                true // 消费事件，防止换行
                                            } else {
                                                true // 空内容时也阻止换行
                                            }
                                        }
                                    }
                                    else -> false
                                }
                            }
                    )
                    
                    // 手动实现placeholder - 调整位置与光标对齐
                    if (textValue.text.isEmpty()) {
                        Text(
                            "输入消息或使用 @ 引用上下文...",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = JewelTheme.globalColors.text.disabled,
                                fontSize = 14.sp
                            ),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp) // 减少内边距以匹配无装饰的TextArea
                        )
                    }
                }
                
                // 底部区域：模型选择器（左）+ 发送按钮（右）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左下角模型选择器
                    CompactModelSelector(
                        currentModel = selectedModel,
                        onModelChange = { model ->
                            println("EnhancedSmartInputArea: Calling onModelChange with ${model.displayName}")
                            onModelChange(model)
                        },
                        enabled = enabled && !isGenerating
                    )
                    
                    // 右下角发送按钮
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                if (textValue.text.isNotBlank() && enabled && !isGenerating) 
                                    JewelTheme.globalColors.borders.focused // 使用主题的焦点颜色作为发送按钮激活色
                                else 
                                    JewelTheme.globalColors.borders.disabled, // 使用主题的禁用边框色
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = textValue.text.isNotBlank() && enabled && !isGenerating) {
                                onSend()
                                textValue = TextFieldValue("")
                                onTextChange("")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "↑",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = if (textValue.text.isNotBlank() && enabled && !isGenerating) 
                                    JewelTheme.globalColors.text.normal // 正常文本颜色
                                else 
                                    JewelTheme.globalColors.text.disabled, // 禁用文本颜色
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 生成状态指示器
 */
@Composable
fun GeneratingIndicator(
    onStop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dotCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            dotCount = (dotCount + 1) % 4
            delay(500)
        }
    }
    
    Row(
        modifier = modifier
            .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(6.dp))
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "Generating",
                style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.normal,
                    fontSize = 12.sp
                )
            )
            
            Text(
                ".".repeat(dotCount),
                style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.normal,
                    fontSize = 12.sp
                ),
                modifier = Modifier.width(12.dp)
            )
        }
        
        Text(
            "Stop",
            style = JewelTheme.defaultTextStyle.copy(
                color = JewelTheme.globalColors.text.normal,
                fontSize = 11.sp
            ),
            modifier = Modifier
                .clickable { onStop() }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 紧凑的模型选择器
 */
@Composable
private fun CompactModelSelector(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 内部状态管理 - 当 currentModel 改变时自动同步
    var internalModel by remember(currentModel) { mutableStateOf(currentModel) }
    var showDropdown by remember { mutableStateOf(false) }
    val models = listOf(AiModel.OPUS, AiModel.SONNET)
    
    // 同步外部状态到内部状态
    LaunchedEffect(currentModel) {
        if (internalModel != currentModel) {
            println("DEBUG: Syncing external model ${currentModel.displayName} to internal state")
            internalModel = currentModel
        }
    }
    
    // 添加调试输出
    println("CompactModelSelector: currentModel = ${currentModel.displayName}, internalModel = ${internalModel.displayName}, enabled = $enabled")
    
    Box(modifier = modifier) {
        // 主按钮 - 小巧设计
        Box(
            modifier = Modifier
                .background(
                    JewelTheme.globalColors.panelBackground,
                    RoundedCornerShape(4.dp)
                )
                .clickable(enabled = enabled) {
                    println("DEBUG: Model selector clicked, showDropdown = $showDropdown")
                    showDropdown = !showDropdown
                }
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = internalModel.displayName,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.normal
                    )
                )
                Text(
                    text = if (showDropdown) "▲" else "▼",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 8.sp,
                        color = JewelTheme.globalColors.text.disabled
                    )
                )
            }
        }
        
        // 使用 Popup 向上展开的小巧下拉列表
        if (showDropdown) {
            println("DEBUG: Showing dropdown with ${models.size} models")
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, -8), // 紧贴按钮向上
                onDismissRequest = { showDropdown = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            JewelTheme.globalColors.panelBackground,
                            RoundedCornerShape(4.dp)
                        )
                        .width(120.dp) // 固定小宽度
                        .padding(2.dp)
                ) {
                    models.forEach { model ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    println("DEBUG: Selected model ${model.displayName}")
                                    internalModel = model
                                    showDropdown = false
                                    onModelChange(model)
                                    println("DEBUG: Called onModelChange with ${model.displayName}")
                                }
                                .background(
                                    if (model == internalModel) 
                                        JewelTheme.globalColors.borders.focused.copy(alpha = 0.15f)
                                    else 
                                        Color.Transparent,
                                    RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = model.displayName,
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 10.sp,
                                    color = if (model == internalModel) 
                                        JewelTheme.globalColors.text.normal 
                                    else 
                                        JewelTheme.globalColors.text.disabled
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}