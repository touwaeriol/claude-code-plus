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
 * @param text 当前输入文本
 * @param onTextChange 文本变化回调
 * @param onSend 发送消息回调
 * @param onContextAdd 添加上下文回调
 * @param enabled 是否启用输入
 * @param searchService 上下文搜索服务
 * @param inlineReferenceManager 内联引用管理器
 * @param focusRequester 焦点请求器
 * @param modifier 修饰符
 */
@Composable
fun ChatInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onContextAdd: (ContextReference) -> Unit = {},
    enabled: Boolean = true,
    searchService: ContextSearchService? = null,
    inlineReferenceManager: InlineReferenceManager = remember { InlineReferenceManager() },
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf(TextFieldValue(text)) }
    var showContextSelector by remember { mutableStateOf(false) }
    var atSymbolPosition by remember { mutableStateOf<Int?>(null) }
    
    // 同步外部text参数到内部状态
    LaunchedEffect(text) {
        if (text != textValue.text) {
            textValue = TextFieldValue(text, TextRange(text.length))
        }
    }
    
    // 检测@符号输入
    fun detectAtSymbol(newText: String, cursor: Int): Boolean {
        if (cursor == 0) return false
        val beforeCursor = newText.substring(0, cursor)
        return if (beforeCursor.isNotEmpty() && beforeCursor.last() == '@') {
            beforeCursor.length == 1 || beforeCursor[beforeCursor.length - 2].let { 
                it == ' ' || it == '\n' 
            }
        } else {
            false
        }
    }
    
    // 处理@符号触发的上下文选择
    fun handleAtTriggerContext(context: ContextReference) {
        val pos = atSymbolPosition
        if (pos != null) {
            val contextText = when (context) {
                is ContextReference.FileReference -> {
                    val inlineRef = InlineFileReference(
                        displayName = context.path.substringAfterLast('/'),
                        fullPath = context.fullPath,
                        relativePath = context.path
                    )
                    inlineReferenceManager.addReference(inlineRef)
                    inlineRef.getInlineText()
                }
                is ContextReference.WebReference -> "@${context.title ?: context.url.substringAfterLast('/')}"
                is ContextReference.FolderReference -> "@${context.path.substringAfterLast('/')}"
                is ContextReference.SymbolReference -> "@${context.name}"
                is ContextReference.TerminalReference -> "@terminal"
                is ContextReference.ProblemsReference -> "@problems"
                is ContextReference.GitReference -> "@git"
                is ContextReference.ImageReference -> "@${context.filename}"
                is ContextReference.SelectionReference -> "@selection"
                is ContextReference.WorkspaceReference -> "@workspace"
            }
            
            val newText = textValue.text.replaceRange(pos, pos + 1, contextText)
            val newCursor = pos + contextText.length
            textValue = TextFieldValue(newText, TextRange(newCursor))
            onTextChange(newText)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp, max = 120.dp)
    ) {
        BasicTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                onTextChange(newValue.text)
                
                // 检测@符号输入
                if (detectAtSymbol(newValue.text, newValue.selection.start)) {
                    showContextSelector = true
                    atSymbolPosition = newValue.selection.start - 1
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
                    if (textValue.text.isNotBlank() && enabled) {
                        onSend()
                    }
                }
            ),
            cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(4.dp)
                ) {
                    // Placeholder文本
                    if (textValue.text.isEmpty()) {
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
                        keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown -> {
                            if (keyEvent.isShiftPressed) {
                                // Shift+Enter: 换行
                                val currentText = textValue.text
                                val currentSelection = textValue.selection
                                val newText = currentText.substring(0, currentSelection.start) + 
                                             "\n" + 
                                             currentText.substring(currentSelection.end)
                                val newSelection = TextRange(currentSelection.start + 1)
                                textValue = TextFieldValue(newText, newSelection)
                                onTextChange(newText)
                                true
                            } else {
                                // Enter: 发送消息
                                if (textValue.text.isNotBlank() && enabled) {
                                    onSend()
                                    true
                                } else {
                                    true
                                }
                            }
                        }
                        keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown -> {
                            if (showContextSelector) {
                                showContextSelector = false
                                atSymbolPosition = null
                                true
                            } else {
                                false
                            }
                        }
                        keyEvent.key == Key.K && keyEvent.type == KeyEventType.KeyDown && 
                        (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) -> {
                            if (!showContextSelector && enabled) {
                                showContextSelector = true
                                atSymbolPosition = null
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
        )
        
        // 上下文选择器弹出框
        if (showContextSelector && searchService != null) {
            ChatInputContextSelectorPopup(
                onDismiss = { 
                    showContextSelector = false
                    atSymbolPosition = null
                },
                onContextSelect = { context ->
                    if (atSymbolPosition != null) {
                        // @符号触发：内联插入
                        handleAtTriggerContext(context)
                    } else {
                        // 其他触发方式：添加到上下文列表
                        val tagContext = when (context) {
                            is ContextReference.FileReference -> context.copy(displayType = ContextDisplayType.TAG)
                            is ContextReference.WebReference -> context.copy(displayType = ContextDisplayType.TAG)
                            else -> context
                        }
                        onContextAdd(tagContext)
                    }
                    showContextSelector = false
                    atSymbolPosition = null
                },
                searchService = searchService,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
} 