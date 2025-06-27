/*
 * EnhancedSmartInputArea.kt
 * 
 * 智能输入框组件 - 包含光标跟随的上下文菜单功能
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text

/**
 * 上下文建议数据类
 */
data class ContextSuggestion(
    val icon: String,
    val title: String,
    val subtitle: String? = null
)

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
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf(TextFieldValue(text)) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // 上下文建议
    var contextSuggestions by remember { mutableStateOf(emptyList<ContextSuggestion>()) }
    
    // 简单的光标位置估算
    val estimateCursorPosition: () -> Offset = {
        val textBeforeCursor = textValue.text.substring(0, textValue.selection.start.coerceAtMost(textValue.text.length))
        val lines = textBeforeCursor.split('\n')
        val currentLineIndex = lines.size - 1
        val currentLineText = lines.lastOrNull() ?: ""
        
        with(density) {
            val charWidth = 8.dp.toPx()
            val lineHeight = 20.dp.toPx()
            val x = currentLineText.length * charWidth
            val y = currentLineIndex * lineHeight
            Offset(x, y)
        }
    }
    
    // 加载建议的函数
    val loadSuggestions: suspend (String) -> List<ContextSuggestion> = { query ->
        delay(100)
        val allSuggestions = listOf(
            ContextSuggestion("📁", "toolwindow", "toolwindow/"),
            ContextSuggestion("📁", "src", "src/"),
            ContextSuggestion("📁", "main", "src/main/"),
            ContextSuggestion("📄", "EnhancedSmartInputArea.kt", "智能输入框组件"),
            ContextSuggestion("📄", "JewelChatView.kt", "聊天界面组件"),
            ContextSuggestion("🔷", "EnhancedSmartInputArea", "Composable function"),
            ContextSuggestion("🔷", "ContextReference", "Sealed class"),
            ContextSuggestion("💻", "Terminal", "引用终端输出"),
            ContextSuggestion("🔍", "Search", "搜索文件和符号")
        )
        
        if (query.isBlank()) {
            allSuggestions.take(6)
        } else {
            allSuggestions.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.subtitle?.contains(query, ignoreCase = true) == true 
            }.take(6)
        }
    }
    
    // 检查@符号位置是否有效
    val isValidAtPosition: (String, Int) -> Boolean = { text, position ->
        if (position < 0 || position >= text.length) false
        else {
            val charBefore = if (position > 0) text[position - 1] else ' '
            val charAfter = if (position < text.length - 1) text[position + 1] else ' '
            val validBefore = charBefore.isWhitespace() || charBefore == '\n' || position == 0
            val validAfter = charAfter.isWhitespace() || charAfter == '\n' || position == text.length - 1
            validBefore && validAfter
        }
    }
    
    // 动态高度计算
    val lineHeight = with(density) { 18.sp.toDp() }
    val minHeight = 32.dp
    val maxHeight = 120.dp
    val textLines = textValue.text.count { it == '\n' } + 1
    val additionalHeight = lineHeight * (textLines - 1)
    val dynamicHeight = (minHeight + additionalHeight).coerceIn(minHeight, maxHeight)
    
    // 同步text prop
    LaunchedEffect(text) {
        if (text != textValue.text) {
            textValue = TextFieldValue(text)
        }
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 生成状态指示器
        if (isGenerating) {
            GeneratingIndicator(
                onStop = { onStop?.invoke() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 主输入框容器
        Box(modifier = Modifier.fillMaxWidth()) {
            // 输入框背景
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(12.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Add Context 按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable(enabled = enabled && !isGenerating) {
                                    showContextMenu = true
                                    contextMenuPosition = textValue.selection.start
                                    searchQuery = ""
                                    scope.launch {
                                        contextSuggestions = loadSuggestions("")
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text("📎", style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp))
                                Text(
                                    "Add Context",
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = 11.sp,
                                        color = JewelTheme.globalColors.text.disabled
                                    )
                                )
                            }
                        }
                    }
                    
                    // 主输入框
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dynamicHeight)
                    ) {
                        BasicTextField(
                            value = textValue,
                            onValueChange = { newValue ->
                                val oldText = textValue.text
                                val newText = newValue.text
                                textValue = newValue
                                onTextChange(newText)
                                
                                // 检测@符号
                                if (newText.length > oldText.length && newText.last() == '@') {
                                    val cursorPosition = newValue.selection.start
                                    val shouldTrigger = isValidAtPosition(newText, cursorPosition - 1)
                                    if (shouldTrigger) {
                                        contextMenuPosition = cursorPosition - 1
                                        showContextMenu = true
                                        scope.launch {
                                            contextSuggestions = loadSuggestions("")
                                        }
                                    }
                                }
                                
                                // 更新搜索查询
                                if (showContextMenu && contextMenuPosition > 0) {
                                    val atIndex = newText.lastIndexOf('@', contextMenuPosition - 1)
                                    if (atIndex >= 0 && atIndex < newValue.selection.start) {
                                        searchQuery = newText.substring(atIndex + 1, newValue.selection.start)
                                        scope.launch {
                                            contextSuggestions = loadSuggestions(searchQuery)
                                        }
                                    }
                                }
                            },
                            enabled = enabled,
                            textStyle = TextStyle(
                                color = JewelTheme.globalColors.text.normal,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                            singleLine = false,
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { event ->
                                    when {
                                        event.key == Key.Escape && event.type == KeyEventType.KeyDown -> {
                                            if (isGenerating) {
                                                onStop?.invoke()
                                                true
                                            } else if (showContextMenu) {
                                                showContextMenu = false
                                                true
                                            } else false
                                        }
                                        
                                        event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                                            if (event.isShiftPressed) {
                                                val selection = textValue.selection
                                                val newText = textValue.text.substring(0, selection.start) + 
                                                             "\n" + 
                                                             textValue.text.substring(selection.end)
                                                val newSelection = TextRange(selection.start + 1)
                                                textValue = TextFieldValue(newText, newSelection)
                                                onTextChange(newText)
                                                true
                                            } else {
                                                if (isGenerating) {
                                                    false
                                                } else if (textValue.text.isNotBlank() && enabled && !showContextMenu) {
                                                    onSend()
                                                    textValue = TextFieldValue("")
                                                    onTextChange("")
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                        }
                                        
                                        else -> false
                                    }
                                },
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    if (textValue.text.isEmpty()) {
                                        Text(
                                            "Plan, search, build anything",
                                            style = JewelTheme.defaultTextStyle.copy(
                                                color = JewelTheme.globalColors.text.disabled,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                    
                    // 底部发送按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    if (textValue.text.isNotBlank() && enabled && !isGenerating) 
                                        Color(0xFF4CAF50) 
                                    else 
                                        Color(0xFF5C5C5C),
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
                                        Color.White 
                                    else 
                                        Color(0xFF9CA3AF),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }
            
            // 上下文菜单
            if (showContextMenu) {
                val cursorPos = estimateCursorPosition()
                val (menuOffsetX, menuOffsetY) = with(density) {
                    val x = cursorPos.x.toDp().coerceAtLeast(0.dp)
                    val y = (cursorPos.y.toDp() - 8.dp).coerceAtMost(0.dp)
                    x to y
                }
                
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .offset(x = menuOffsetX, y = menuOffsetY)
                        .zIndex(10f)
                ) {
                    SimpleContextMenu(
                        suggestions = contextSuggestions,
                        onSelect = { suggestion ->
                            val mockContext = ContextReference.FileReference(suggestion.title, null, null)
                            onContextAdd(mockContext)
                            showContextMenu = false
                            
                            val currentText = textValue.text
                            val atIndex = currentText.lastIndexOf('@')
                            if (atIndex >= 0) {
                                val beforeAt = currentText.substring(0, atIndex)
                                val newText = "$beforeAt@${suggestion.title} "
                                textValue = TextFieldValue(newText, TextRange(newText.length))
                                onTextChange(newText)
                            }
                        },
                        onDismiss = { showContextMenu = false }
                    )
                }
            }
        }
        
        // 已选择的上下文标签
        if (contexts.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(contexts) { context ->
                    SimpleContextChip(
                        context = context,
                        onRemove = { onContextRemove(it) }
                    )
                }
            }
        }
    }
    
    // 请求初始焦点
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * 简单的上下文菜单
 */
@Composable
private fun SimpleContextMenu(
    suggestions: List<ContextSuggestion>,
    onSelect: (ContextSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(min = 300.dp, max = 400.dp)
            .heightIn(max = 300.dp)
            .background(Color(0xFF2B2B2B), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF404040), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp)
    ) {
        if (suggestions.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔍", style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp))
                Text(
                    "没有找到匹配项",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = JewelTheme.globalColors.text.disabled,
                        fontSize = 13.sp
                    )
                )
            }
        } else {
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(suggestion) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        suggestion.icon,
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp),
                        modifier = Modifier.width(20.dp)
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            suggestion.title,
                            style = JewelTheme.defaultTextStyle.copy(
                                color = JewelTheme.globalColors.text.normal,
                                fontSize = 13.sp
                            )
                        )
                        suggestion.subtitle?.let { subtitle ->
                            Text(
                                subtitle,
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = JewelTheme.globalColors.text.disabled,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 简单的上下文标签
 */
@Composable
private fun SimpleContextChip(
    context: ContextReference,
    onRemove: (ContextReference) -> Unit
) {
    Row(
        modifier = Modifier
            .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        when (context) {
            is ContextReference.FileReference -> {
                Text("📄", style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp))
                Text(
                    context.path.substringAfterLast('/'),
                    style = JewelTheme.defaultTextStyle.copy(
                        color = JewelTheme.globalColors.text.normal,
                        fontSize = 10.sp
                    )
                )
            }
            is ContextReference.SymbolReference -> {
                Text("🔷", style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp))
                Text(
                    context.name,
                    style = JewelTheme.defaultTextStyle.copy(
                        color = JewelTheme.globalColors.text.normal,
                        fontSize = 10.sp
                    )
                )
            }
            is ContextReference.TerminalReference -> {
                Text("💻", style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp))
                Text(
                    "终端",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = JewelTheme.globalColors.text.normal,
                        fontSize = 10.sp
                    )
                )
            }
            else -> {}
        }
        
        Text(
            "×",
            style = JewelTheme.defaultTextStyle.copy(
                color = JewelTheme.globalColors.text.disabled,
                fontSize = 10.sp
            ),
            modifier = Modifier.clickable { onRemove(context) }
        )
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