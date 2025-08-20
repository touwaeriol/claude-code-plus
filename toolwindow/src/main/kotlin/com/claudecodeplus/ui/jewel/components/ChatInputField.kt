/*
 * ChatInputField.kt
 * 
 * 聊天输入框组件 - 使用 Jewel TextField
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import com.claudecodeplus.ui.services.FileIndexService

/**
 * 聊天输入框组件
 * 
 * @param value 当前输入的 TextFieldValue
 * @param onValueChange 文本变化回调
 * @param onSend 发送消息回调
 * @param enabled 是否启用输入
 * @param focusRequester 焦点请求器
 * @param onShowContextSelector 显示上下文选择器回调
 * @param showPreview 是否显示预览
 * @param modifier 修饰符
 */
@Composable
fun ChatInputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onInterruptAndSend: (() -> Unit)? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onShowContextSelector: (Int?) -> Unit,
    showPreview: Boolean = false,
    modifier: Modifier = Modifier,
    maxHeight: Int = 200,  // 增加最大高度
    fileIndexService: FileIndexService? = null  // 新增参数
) {
    var isFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    
    // 计算是否需要滚动
    val lineHeight = 20.dp  // 每行高度
    val padding = 16.dp     // 上下内边距
    val lineCount = value.text.lines().size
    val estimatedHeight = lineHeight * lineCount + padding
    val needsScroll = estimatedHeight > maxHeight.dp
    
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
        // 输入框容器 - 使用 Box 包装以支持滚动条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = maxHeight.dp)
        ) {
            // 内部滚动容器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (needsScroll) {
                            Modifier.verticalScroll(scrollState)
                        } else {
                            Modifier
                        }
                    )
            ) {
            // 占位符 - 简洁版本
            if (value.text.isEmpty()) {
                Text(
                    "Message Claude...",
                    color = JewelTheme.globalColors.text.disabled,
                    style = JewelTheme.defaultTextStyle,
                    modifier = Modifier.padding(vertical = 12.dp)  // 增加垂直内边距
                )
            }
            
            // 输入框
            BasicTextField(
                value = value,
                onValueChange = { newValue ->
                    onValueChange(newValue)
                    
                    // @ 符号检测已移至 SimpleInlineFileReferenceHandler 中处理
                    // 不再使用旧的复杂上下文选择器
                },
                enabled = enabled,
                textStyle = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.normal,
                    lineHeight = JewelTheme.defaultTextStyle.fontSize * 1.5
                ),
                cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Default
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)  // 增加垂直内边距，与占位符一致
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                    }
                .onPreviewKeyEvent { keyEvent ->
                    when {
                        // Enter 发送，Shift+Enter 换行，Alt+Enter 打断并发送
                        keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown -> {
                            when {
                                keyEvent.isShiftPressed -> {
                                    // Shift+Enter: 插入换行
                                    val currentText = value.text
                                    val currentSelection = value.selection
                                    val newText = currentText.substring(0, currentSelection.start) + 
                                                 "\n" + 
                                                 currentText.substring(currentSelection.end)
                                    val newSelection = androidx.compose.ui.text.TextRange(currentSelection.start + 1)
                                    onValueChange(TextFieldValue(newText, newSelection))
                                    true
                                }
                                keyEvent.isAltPressed -> {
                                    // Alt+Enter: 打断并发送
                                    if (value.text.isNotBlank() && onInterruptAndSend != null) {
                                        onInterruptAndSend()
                                    }
                                    true
                                }
                                else -> {
                                    // Enter: 普通发送
                                    if (value.text.isNotBlank()) {
                                        onSend()
                                    }
                                    true
                                }
                            }
                        }
                        // Cmd+K / Ctrl+K 打开上下文选择器
                        keyEvent.key == Key.K && keyEvent.type == KeyEventType.KeyDown && 
                        (keyEvent.isMetaPressed || keyEvent.isCtrlPressed) -> {
                            if (enabled) {
                                onShowContextSelector(null)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
            )
            }
            
            // 滚动条 - 仅在聚焦且内容超过最大高度时显示
            if (isFocused && needsScroll) {
                VerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp)
                )
            }
        }
        
            // 预览区域（可选）
            if (showPreview && value.text.isNotBlank()) {
                MessagePreview(
                    markdown = value.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        }
        
        // 简化内联文件引用处理器 - 悬浮在输入框上方
        if (fileIndexService != null) {
            SimpleInlineFileReferenceHandler(
                textFieldValue = value,
                onTextChange = onValueChange,
                fileIndexService = fileIndexService,
                enabled = enabled
            )
        }
    }
}

/**
 * 消息预览组件
 */
@Composable
private fun MessagePreview(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val annotatedText = remember(markdown) {
        parseMarkdownToAnnotatedString(markdown)
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "预览:",
            style = JewelTheme.defaultTextStyle.copy(
                color = JewelTheme.globalColors.text.disabled
            )
        )
        
        Text(
            text = annotatedText,
            style = JewelTheme.defaultTextStyle
        )
    }
}

/**
 * 检测是否输入了 @ 符号（用于触发上下文选择器）
 */
private fun detectAtSymbol(text: String, cursorPos: Int): Boolean {
    if (cursorPos == 0) return false
    
    val beforeCursor = text.substring(0, cursorPos)
    return if (beforeCursor.isNotEmpty() && beforeCursor.last() == '@') {
        // @ 符号前必须是空白字符或文本开头
        beforeCursor.length == 1 || beforeCursor[beforeCursor.length - 2].isWhitespace()
    } else {
        false
    }
}