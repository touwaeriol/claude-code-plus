/*
 * RichTextInputField.kt
 * 
 * 支持富文本显示的输入框组件
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 富文本输入框组件
 * 支持内联引用的富文本显示和编辑
 */
@Composable
fun RichTextInputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    enabled: Boolean = true,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp, max = 120.dp)
    ) {
        if (isEditing || value.text.isEmpty()) {
            // 编辑模式：使用普通的文本输入框
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = JewelTheme.defaultTextStyle.copy(
                    fontSize = 14.sp,
                    color = JewelTheme.globalColors.text.normal
                ),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent)
                            .padding(4.dp)
                    ) {
                        // Placeholder文本
                        if (value.text.isEmpty()) {
                            Text(
                                placeholder,
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = JewelTheme.globalColors.text.disabled,
                                    fontSize = 14.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        // 监听焦点失去事件（不完美，但可以工作）
                        if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                            isEditing = false
                        }
                        onKeyEvent(keyEvent)
                    }
            )
        } else {
            // 显示模式：使用富文本显示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                ClickableInlineText(
                    text = value.text,
                    onReferenceClick = { reference ->
                        // 点击引用时的处理（可以显示工具提示等）
                        // Clicked reference: ${reference.fullPath}
                    },
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 14.sp,
                        color = JewelTheme.globalColors.text.normal
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp)
                )
            }
        }
    }
    
    // 监听文本变化，决定是否进入编辑模式
    LaunchedEffect(value.text) {
        if (value.text.isNotEmpty()) {
            isEditing = false
        }
    }
    
    // 监听焦点状态
    LaunchedEffect(Unit) {
        // 这里可以添加焦点监听逻辑
        isEditing = true
    }
}

/**
 * 检查文本是否包含内联引用
 */
fun hasInlineReferences(text: String): Boolean {
    val pattern = Regex("@(file://[^\\s]+|https?://[^\\s]+)")
    return pattern.containsMatchIn(text)
}