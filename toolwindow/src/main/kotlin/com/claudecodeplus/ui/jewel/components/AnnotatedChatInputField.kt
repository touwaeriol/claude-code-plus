/*
 * AnnotatedChatInputField.kt
 * 
 * 支持文件引用注解的聊天输入框组件
 * 替换 BasicTextField 提供超链接样式的文件引用显示
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.services.FileIndexService
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalScrollbar

/**
 * 带注解的聊天输入框组件
 * 
 * @param value 当前输入的文本字段值（带注解）
 * @param onValueChange 文本变化回调
 * @param onSend 发送消息回调
 * @param onInterruptAndSend 打断并发送回调
 * @param enabled 是否启用输入
 * @param focusRequester 焦点请求器
 * @param onShowContextSelector 显示上下文选择器回调
 * @param showPreview 是否显示预览
 * @param fileIndexService 文件索引服务
 * @param maxHeight 最大高度
 * @param onFileReferenceClick 文件引用点击回调
 * @param modifier 修饰符
 */
@Composable
fun AnnotatedChatInputField(
    value: AnnotatedTextFieldValue,
    onValueChange: (AnnotatedTextFieldValue) -> Unit,
    onSend: () -> Unit,
    onInterruptAndSend: (() -> Unit)? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onShowContextSelector: (Int?) -> Unit = {},
    showPreview: Boolean = false,
    modifier: Modifier = Modifier,
    maxHeight: Int = 200,
    fileIndexService: FileIndexService? = null,
    onFileReferenceClick: ((FileReferenceAnnotation) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    
    // 计算是否需要滚动
    val lineHeight = 20.dp
    val padding = 16.dp
    val lineCount = value.text.lines().size
    val estimatedHeight = lineHeight * lineCount + padding
    val needsScroll = estimatedHeight > maxHeight.dp
    
    // 构建带注解的字符串
    val annotatedString = buildSimpleFileReferenceAnnotatedString(
        text = value.text,
        annotations = value.annotations
    )
    
    // 创建 TextFieldValue 用于 BasicTextField
    val textFieldValue = remember(annotatedString, value.selection) {
        TextFieldValue(
            annotatedString = annotatedString,
            selection = value.selection
        )
    }
    
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 输入框容器
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
                    // 占位符
                    if (value.text.isEmpty()) {
                        Text(
                            "Message Claude...",
                            color = JewelTheme.globalColors.text.disabled,
                            style = JewelTheme.defaultTextStyle,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    
                    // 带注解的输入框
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newTextFieldValue ->
                            // 处理文本变化，更新注解
                            handleTextChange(
                                oldValue = value,
                                newTextFieldValue = newTextFieldValue,
                                onValueChange = onValueChange
                            )
                        },
                        onTextLayout = { layoutResult ->
                            // 保存TextLayoutResult用于精确字符定位
                            textLayoutResult = layoutResult
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
                            .padding(vertical = 12.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                isFocused = focusState.isFocused
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                // 首先处理注解相关的键盘事件
                                val annotationHandled = AnnotationKeyboardHandler.handleKeyEvent(
                                    keyEvent = keyEvent,
                                    value = value,
                                    onValueChange = onValueChange,
                                    onAnnotationClick = onFileReferenceClick
                                )
                                
                                if (annotationHandled) {
                                    true
                                } else {
                                    // 处理普通的键盘事件
                                    handleNormalKeyEvent(
                                        keyEvent = keyEvent,
                                        value = value,
                                        onSend = onSend,
                                        onInterruptAndSend = onInterruptAndSend,
                                        onValueChange = onValueChange,
                                        onShowContextSelector = onShowContextSelector,
                                        enabled = enabled
                                    )
                                }
                            }
                    )
                }
                
                // 滚动条
                if (isFocused && needsScroll) {
                    VerticalScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp)
                    )
                }
            }
            
            // 预览区域
            if (showPreview && value.text.isNotBlank()) {
                AnnotatedMessagePreview(
                    value = value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        }
        
        // 简化内联文件引用处理器 - 传递TextLayoutResult
        if (fileIndexService != null) {
            AnnotatedInlineFileReferenceHandler(
                value = value,
                onValueChange = onValueChange,
                fileIndexService = fileIndexService,
                enabled = enabled,
                textLayoutResult = textLayoutResult
            )
        }
    }
}

/**
 * 处理文本变化
 */
private fun handleTextChange(
    oldValue: AnnotatedTextFieldValue,
    newTextFieldValue: TextFieldValue,
    onValueChange: (AnnotatedTextFieldValue) -> Unit
) {
    // 使用注解更新逻辑处理文本变化
    val updateResult = AnnotationUpdater.updateAnnotationsOnTextChange(
        oldValue = oldValue,
        newTextFieldValue = newTextFieldValue
    )
    
    when (updateResult) {
        is AnnotationUpdateResult.Success -> {
            onValueChange(updateResult.updatedValue)
        }
        is AnnotationUpdateResult.AnnotationDestroyed -> {
            // 注解被破坏时，仍然应用更新，但可以添加日志或通知
            println("注解被破坏: ${updateResult.destroyedAnnotations.map { it.displayText }}")
            onValueChange(updateResult.updatedValue)
        }
    }
}

/**
 * 处理普通键盘事件
 */
private fun handleNormalKeyEvent(
    keyEvent: KeyEvent,
    value: AnnotatedTextFieldValue,
    onSend: () -> Unit,
    onInterruptAndSend: (() -> Unit)?,
    onValueChange: (AnnotatedTextFieldValue) -> Unit,
    onShowContextSelector: (Int?) -> Unit,
    enabled: Boolean
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false
    
    return when {
        // Enter 发送，Shift+Enter 换行，Alt+Enter 打断并发送
        keyEvent.key == Key.Enter -> {
            when {
                keyEvent.isShiftPressed -> {
                    // Shift+Enter: 插入换行
                    insertNewline(value, onValueChange)
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
        keyEvent.key == Key.K && (keyEvent.isMetaPressed || keyEvent.isCtrlPressed) -> {
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

/**
 * 插入换行符
 */
private fun insertNewline(
    value: AnnotatedTextFieldValue,
    onValueChange: (AnnotatedTextFieldValue) -> Unit
) {
    val currentSelection = value.selection
    val newText = value.text.substring(0, currentSelection.start) + 
                  "\n" + 
                  value.text.substring(currentSelection.end)
    val newSelection = TextRange(currentSelection.start + 1)
    
    // 更新注解位置
    val updatedAnnotations = value.annotations.map { annotation ->
        when {
            annotation.startIndex >= currentSelection.start -> {
                annotation.copy(
                    startIndex = annotation.startIndex + 1,
                    endIndex = annotation.endIndex + 1
                )
            }
            else -> annotation
        }
    }
    
    onValueChange(
        AnnotatedTextFieldValue(
            text = newText,
            selection = newSelection,
            annotations = updatedAnnotations
        )
    )
}

/**
 * 注解消息预览组件
 */
@Composable
private fun AnnotatedMessagePreview(
    value: AnnotatedTextFieldValue,
    modifier: Modifier = Modifier
) {
    val annotatedString = buildSimpleFileReferenceAnnotatedString(
        text = value.text,
        annotations = value.annotations
    )
    
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
            text = annotatedString,
            style = JewelTheme.defaultTextStyle
        )
    }
}