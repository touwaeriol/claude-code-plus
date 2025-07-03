/*
 * ChatInputField.kt
 * 
 * 聊天输入框组件 - 支持多行输入、快捷键、内联引用等功能
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
import com.claudecodeplus.ui.jewel.components.context.ChatInputContextSelectorPopup
import com.claudecodeplus.ui.jewel.components.context.ContextSearchService
import com.claudecodeplus.ui.models.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 聊天输入框组件
 * 
 * @param value 当前输入文本
 * @param onValueChange 文本变化回调
 * @param onSend 发送消息回调
 * @param onContextAdd 添加上下文回调
 * @param enabled 是否启用输入
 * @param searchService 上下文搜索服务
 * @param inlineReferenceManager 内联引用管理器
 * @param focusRequester 焦点请求器
 * @param onShowContextSelectorRequest 显示上下文选择器请求回调
 * @param modifier 修饰符
 */
@Composable
fun ChatInputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onContextAdd: (ContextReference) -> Unit = {},
    enabled: Boolean = true,
    searchService: ContextSearchService? = null,
    inlineReferenceManager: InlineReferenceManager = remember { InlineReferenceManager() },
    focusRequester: FocusRequester = remember { FocusRequester() },
    onShowContextSelectorRequest: (Int?) -> Unit,
    onAtTriggerContext: (ContextReference, Int) -> Unit = { _, _ -> },
    showContextSelector: Boolean = false,
    onShowContextSelectorChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var atSymbolPosition by remember { mutableStateOf<Int?>(null) }
    var justSelectedContext by remember { mutableStateOf(false) }
    // 检测@符号输入 - 使用可扩展的检测系统
    fun detectAtSymbol(newText: String, cursor: Int): Boolean {
        return InlineReferenceDetector.shouldTriggerContextSelector(newText, cursor)
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp, max = 120.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                val oldValue = value
                println("DEBUG: ChatInputField - onValueChange called")
                println("  Old text: '${oldValue.text}'")
                println("  New text: '${newValue.text}'")
                println("  Cursor position: ${newValue.selection.start}")
                println("  justSelectedContext: $justSelectedContext")
                
                onValueChange(newValue)
                
                // 如果刚选择了上下文，跳过这次检测
                if (justSelectedContext) {
                    println("DEBUG: Skipping @ detection because justSelectedContext=true")
                    justSelectedContext = false
                    return@BasicTextField
                }
                
                // 只在新输入@符号时触发，避免在已有引用时重复触发
                val isNewAtSymbol = newValue.text.length > oldValue.text.length && 
                                   newValue.selection.start > 0 &&
                                   newValue.text.getOrNull(newValue.selection.start - 1) == '@'
                
                println("DEBUG: isNewAtSymbol = $isNewAtSymbol")
                
                if (isNewAtSymbol) {
                    val shouldTrigger = detectAtSymbol(newValue.text, newValue.selection.start)
                    println("DEBUG: detectAtSymbol returned: $shouldTrigger")
                    
                    if (shouldTrigger) {
                        println("DEBUG: Triggering context selector!")
                        onShowContextSelectorChange(true)
                        atSymbolPosition = newValue.selection.start - 1
                        onShowContextSelectorRequest(newValue.selection.start - 1)
                    }
                }
            },
            enabled = enabled,
            textStyle = JewelTheme.defaultTextStyle.copy(
                fontSize = 14.sp,
                color = JewelTheme.globalColors.text.normal
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (value.text.isNotBlank() && enabled) {
                        onSend()
                    }
                }
            ),
            visualTransformation = InlineReferenceVisualTransformation(),
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
                            "输入消息，使用 @ 内联引用文件，或 ⌘K 添加上下文...",
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
                    when {
                        // 处理退格键 - 整体删除引用
                        keyEvent.key == Key.Backspace && keyEvent.type == KeyEventType.KeyDown -> {
                            val processedValue = InlineReferenceInputProcessor.handleDelete(value, true)
                            if (processedValue != null) {
                                onValueChange(processedValue)
                                true
                            } else {
                                false
                            }
                        }
                        // 处理Delete键 - 整体删除引用
                        keyEvent.key == Key.Delete && keyEvent.type == KeyEventType.KeyDown -> {
                            val processedValue = InlineReferenceInputProcessor.handleDelete(value, false)
                            if (processedValue != null) {
                                onValueChange(processedValue)
                                true
                            } else {
                                false
                            }
                        }
                        // 处理左箭头 - 跳过引用内部
                        keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown -> {
                            if (!keyEvent.isShiftPressed) {
                                val processedValue = InlineReferenceInputProcessor.handleCursorMovement(
                                    value, 
                                    InlineReferenceInputProcessor.CursorDirection.LEFT
                                )
                                if (processedValue != null) {
                                    onValueChange(processedValue)
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        }
                        // 处理右箭头 - 跳过引用内部
                        keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown -> {
                            if (!keyEvent.isShiftPressed) {
                                val processedValue = InlineReferenceInputProcessor.handleCursorMovement(
                                    value, 
                                    InlineReferenceInputProcessor.CursorDirection.RIGHT
                                )
                                if (processedValue != null) {
                                    onValueChange(processedValue)
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        }
                        keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown -> {
                            if (keyEvent.isShiftPressed) {
                                // Shift+Enter: 换行
                                val currentText = value.text
                                val currentSelection = value.selection
                                val newText = currentText.substring(0, currentSelection.start) + 
                                             "\n" + 
                                             currentText.substring(currentSelection.end)
                                val newSelection = TextRange(currentSelection.start + 1)
                                val newValue = TextFieldValue(newText, newSelection)
                                onValueChange(newValue)
                                true
                            } else {
                                // Enter: 发送消息
                                if (value.text.isNotBlank() && enabled) {
                                    onSend()
                                    true
                                } else {
                                    true
                                }
                            }
                        }
                        keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown -> {
                            // Escape is handled by the popup's onDismissRequest, so we don't consume it here.
                            false
                        }
                        keyEvent.key == Key.K && keyEvent.type == KeyEventType.KeyDown && 
                        (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) -> {
                            if (enabled) {
                                onShowContextSelectorChange(true)
                                atSymbolPosition = null
                                onShowContextSelectorRequest(null) // Request to show popup, position is null for non-@ trigger
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
        )
        
        // 上下文选择器弹出框 - 只在需要时显示
        println("DEBUG: Context selector check - showContextSelector=$showContextSelector, searchService=${searchService != null}")
        if (showContextSelector && searchService != null) {
            println("DEBUG: Showing ChatInputContextSelectorPopup")
            ChatInputContextSelectorPopup(
                onDismiss = { 
                    println("DEBUG: onDismiss called in ChatInputField")
                    onShowContextSelectorChange(false)
                    atSymbolPosition = null
                    onShowContextSelectorRequest(null)
                },
                onContextSelect = { context ->
                    println("DEBUG: onContextSelect called with context: $context")
                    println("  atSymbolPosition: $atSymbolPosition")
                    justSelectedContext = true  // 标记刚选择了上下文
                    
                    if (atSymbolPosition != null) {
                        // @符号触发：生成内联引用格式，并添加空格
                        val inlineRef = generateInlineReference(context)
                        val inlineRefWithSpace = "$inlineRef "  // 添加空格
                        val currentText = value.text
                        val pos = atSymbolPosition!!
                        
                        println("DEBUG: Generating inline reference: $inlineRefWithSpace")
                        println("  Current text: '$currentText'")
                        println("  Position: $pos")
                        
                        // 替换@符号为内联引用（带空格）
                        val newText = currentText.substring(0, pos) + inlineRefWithSpace + currentText.substring(pos + 1)
                        val newCursor = pos + inlineRefWithSpace.length
                        
                        println("DEBUG: New text after replacement: '$newText'")
                        
                        onValueChange(TextFieldValue(newText, TextRange(newCursor)))
                        onAtTriggerContext(context, pos)
                    } else {
                        // 其他触发方式：添加到上下文列表
                        val tagContext = when (context) {
                            is ContextReference.FileReference -> context.copy(displayType = ContextDisplayType.TAG)
                            is ContextReference.WebReference -> context.copy(displayType = ContextDisplayType.TAG)
                            else -> context
                        }
                        onContextAdd(tagContext)
                    }
                    
                    println("DEBUG: Setting showContextSelector = false via onShowContextSelectorChange")
                    onShowContextSelectorChange(false)
                    atSymbolPosition = null
                    onShowContextSelectorRequest(null)
                },
                searchService = searchService,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
} 