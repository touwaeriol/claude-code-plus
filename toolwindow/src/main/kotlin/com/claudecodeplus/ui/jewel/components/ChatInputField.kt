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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

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
    enabled: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onShowContextSelector: (Int?) -> Unit,
    showPreview: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 输入框 - 使用 BasicTextField 实现透明背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 120.dp)
        ) {
            // 占位符
            if (value.text.isEmpty()) {
                Text(
                    "输入消息，使用 [@名称](uri) 格式添加引用，或按 ⌘K...",
                    color = JewelTheme.globalColors.text.disabled,
                    style = JewelTheme.defaultTextStyle,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            // 输入框
            BasicTextField(
                value = value,
                onValueChange = { newValue ->
                    onValueChange(newValue)
                    
                    // 检测 @ 符号输入
                    if (detectAtSymbol(newValue.text, newValue.selection.start)) {
                        onShowContextSelector(newValue.selection.start - 1)
                    }
                },
                enabled = enabled,
                textStyle = TextStyle(
                    color = JewelTheme.globalColors.text.normal,
                    fontSize = JewelTheme.defaultTextStyle.fontSize
                ),
                cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Default
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .focusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    when {
                        // Enter 发送，Shift+Enter 换行
                        keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown -> {
                            if (keyEvent.isShiftPressed) {
                                // Shift+Enter: 插入换行
                                val currentText = value.text
                                val currentSelection = value.selection
                                val newText = currentText.substring(0, currentSelection.start) + 
                                             "\n" + 
                                             currentText.substring(currentSelection.end)
                                val newSelection = androidx.compose.ui.text.TextRange(currentSelection.start + 1)
                                onValueChange(TextFieldValue(newText, newSelection))
                                true
                            } else {
                                // Enter: 发送消息
                                if (value.text.isNotBlank() && enabled) {
                                    onSend()
                                }
                                true
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