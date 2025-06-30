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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
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
 * 上下文类型
 */
enum class ContextType(val displayName: String, val icon: String) {
    FILES("Files", "📁"),
    WEB("Web", "🌐"),
    GIT("Git", "🔀"),
    TERMINAL("Terminal", "💻"),
    PROBLEMS("Problems", "⚠️"),
    SYMBOLS("Symbols", "🔷")
}

/**
 * 上下文建议数据类
 */
data class ContextSuggestion(
    val type: ContextType,
    val icon: String,
    val title: String,
    val subtitle: String? = null,
    val path: String? = null,
    val matchedText: String? = null
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
    
    // 同步外部text参数到内部状态
    LaunchedEffect(text) {
        if (text != textValue.text) {
            textValue = TextFieldValue(text, TextRange(text.length))
        }
    }
    var showContextMenu by remember { mutableStateOf(false) }
    var selectedContextType by remember { mutableStateOf(ContextType.FILES) }
    var contextMenuPosition by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // 上下文建议
    var contextSuggestions by remember { mutableStateOf(emptyList<ContextSuggestion>()) }
    
    // 估算@符号位置
    val estimateCursorPosition: () -> Offset = {
        // 如果显示上下文菜单，计算@符号位置；否则计算当前光标位置
        val targetPosition = if (showContextMenu && contextMenuPosition >= 0) {
            contextMenuPosition
        } else {
            textValue.selection.start.coerceAtMost(textValue.text.length)
        }
        
        val textBeforeTarget = textValue.text.substring(0, targetPosition)
        val lines = textBeforeTarget.split('\n')
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
    
    // 加载文件建议
    val loadFileSuggestions: suspend (String) -> List<ContextSuggestion> = { query ->
        delay(50) // 减少延迟
        val commonFiles = listOf(
            Triple("EnhancedSmartInputArea.kt", "智能输入框组件", "toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/EnhancedSmartInputArea.kt"),
            Triple("JewelChatView.kt", "聊天界面组件", "toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/JewelChatView.kt"),
            Triple("ClaudeCliWrapper.kt", "CLI包装器", "cli-wrapper/src/main/kotlin/com/claudecodeplus/sdk/ClaudeCliWrapper.kt"),
            Triple("build.gradle.kts", "构建配置", "build.gradle.kts"),
            Triple("README.md", "项目说明", "README.md"),
            Triple("JewelChatApp.kt", "聊天应用", "toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/JewelChatApp.kt"),
            Triple("MarkdownRenderer.kt", "Markdown渲染器", "toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/MarkdownRenderer.kt"),
            Triple("ToolCallDisplay.kt", "工具调用显示", "toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/ToolCallDisplay.kt"),
            Triple("UnifiedModels.kt", "统一模型", "toolwindow/src/main/kotlin/com/claudecodeplus/ui/models/UnifiedModels.kt"),
            Triple("plugin.xml", "插件配置", "plugin/src/main/resources/META-INF/plugin.xml")
        )
        
        if (query.isBlank()) {
            commonFiles.take(8).map { (name, desc, path) ->
                ContextSuggestion(
                    type = ContextType.FILES,
                    icon = if (name.endsWith(".kt")) "📄" else if (name.endsWith("/")) "📁" else "📄",
                    title = name,
                    subtitle = desc,
                    path = path
                )
            }
        } else {
            commonFiles.filter { (name, desc, _) ->
                name.contains(query, ignoreCase = true) || desc.contains(query, ignoreCase = true)
            }.take(8).map { (name, desc, path) ->
                ContextSuggestion(
                    type = ContextType.FILES,
                    icon = if (name.endsWith(".kt")) "📄" else if (name.endsWith("/")) "📁" else "📄",
                    title = name,
                    subtitle = desc,
                    path = path,
                    matchedText = query
                )
            }
        }
    }
    
    // 检查@符号位置是否有效
    val isValidAtPosition: (String, Int) -> Boolean = { text, position ->
        if (position < 0 || position >= text.length) false
        else {
            val charBefore = if (position > 0) text[position - 1] else ' '
            charBefore.isWhitespace() || charBefore == '\n' || position == 0
        }
    }
    
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
        // 已选择的上下文显示
        if (contexts.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(contexts) { context ->
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
        
        // 主输入框容器
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
                                selectedContextType = ContextType.FILES // 默认选择文件
                                scope.launch {
                                    contextSuggestions = loadFileSuggestions("")
                                }
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
                                    selectedContextType = ContextType.FILES // 默认文件类型
                                    searchQuery = ""
                                    scope.launch {
                                        contextSuggestions = loadFileSuggestions("")
                                    }
                                }
                            }
                            
                            // 更新搜索查询 - 支持@后实时输入搜索
                            if (showContextMenu) {
                                // 找到最近的@符号位置
                                val currentCursor = newValue.selection.start
                                val atIndex = newText.lastIndexOf('@', currentCursor - 1)
                                
                                if (atIndex >= 0 && atIndex < currentCursor) {
                                    // 提取@符号后的查询文本
                                    val queryText = newText.substring(atIndex + 1, currentCursor)
                                    
                                    // 检查是否还在@符号的作用范围内（没有空格或换行）
                                    if (!queryText.contains(' ') && !queryText.contains('\n')) {
                                        searchQuery = queryText
                                        contextMenuPosition = atIndex
                                        
                                        // 实时搜索
                                        scope.launch {
                                            contextSuggestions = when (selectedContextType) {
                                                ContextType.FILES -> loadFileSuggestions(searchQuery)
                                                else -> emptyList()
                                            }
                                        }
                                    } else {
                                        // 如果包含空格或换行，关闭菜单
                                        showContextMenu = false
                                    }
                                } else {
                                    // 光标不在@符号后，关闭菜单
                                    showContextMenu = false
                                }
                            }
                        },
                        enabled = enabled,
                        textStyle = TextStyle(
                            color = JewelTheme.globalColors.text.normal,
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        ),
                        cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Default,
                            keyboardType = KeyboardType.Text
                        ),
                        singleLine = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                when {
                                    keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown -> {
                                        if (keyEvent.isShiftPressed) {
                                            // Shift+Enter: 换行，让系统处理
                                            false
                                        } else if (showContextMenu && contextSuggestions.isNotEmpty()) {
                                            // 选择第一个建议
                                            val suggestion = contextSuggestions.first()
                                            val mockContext = ContextReference.FileReference(
                                                suggestion.path ?: suggestion.title, 
                                                null, 
                                                suggestion.subtitle
                                            )
                                            onContextAdd(mockContext)
                                            showContextMenu = false
                                            
                                            // 更新文本 - 替换@符号及其后的搜索文本
                                            val currentText = textValue.text
                                            val currentCursor = textValue.selection.start
                                            val atIndex = currentText.lastIndexOf('@', currentCursor - 1)
                                            
                                            if (atIndex >= 0) {
                                                val beforeAt = currentText.substring(0, atIndex)
                                                val afterCursor = currentText.substring(currentCursor)
                                                val newText = "$beforeAt${suggestion.title} $afterCursor"
                                                val newCursorPos = beforeAt.length + suggestion.title.length + 1
                                                textValue = TextFieldValue(newText, TextRange(newCursorPos))
                                                onTextChange(newText)
                                            }
                                            true
                                        } else {
                                            // Enter: 发送消息
                                            if (textValue.text.isNotBlank() && enabled && !isGenerating) {
                                                onSend()
                                                // 清空输入框
                                                textValue = TextFieldValue("")
                                                onTextChange("")
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                    }
                                    keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown -> {
                                        if (showContextMenu) {
                                            showContextMenu = false
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            }
                    ) { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopStart
                        ) {
                            if (textValue.text.isEmpty()) {
                                Text(
                                    "输入消息或使用 @ 引用上下文...",
                                    style = JewelTheme.defaultTextStyle.copy(
                                        color = JewelTheme.globalColors.text.disabled,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                }
                
                // 底部按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 发送/停止按钮
                    if (isGenerating && onStop != null) {
                        IconButton(
                            onClick = { onStop() },
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    Color(0xFFE55765),
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Text(
                                "⬛", 
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (textValue.text.isNotBlank() && enabled) {
                                    onSend()
                                    // 清空输入框
                                    textValue = TextFieldValue("")
                                    onTextChange("")
                                }
                            },
                            enabled = enabled && textValue.text.isNotBlank(),
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    if (enabled && textValue.text.isNotBlank()) 
                                        JewelTheme.globalColors.borders.focused 
                                    else 
                                        JewelTheme.globalColors.borders.disabled,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Text(
                                "↗", 
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 14.sp,
                                    color = if (enabled && textValue.text.isNotBlank()) 
                                        Color.White 
                                    else 
                                        JewelTheme.globalColors.text.disabled
                                )
                            )
                        }
                    }
                }
            }
        }
        
        // 上下文选择菜单 - 在@位置弹出
        if (showContextMenu) {
            // 计算@符号位置，菜单显示在上方
            val cursorPos = estimateCursorPosition()
            val (menuOffsetX, menuOffsetY) = with(density) {
                val x = cursorPos.x.toDp().coerceAtLeast(0.dp)
                val y = cursorPos.y.toDp() - 250.dp // 在@符号上方显示
                x to y.coerceAtLeast(0.dp)
            }
            
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .offset(x = menuOffsetX, y = menuOffsetY)
                    .zIndex(100f)
            ) {
                CursorStyleContextMenu(
                    selectedType = selectedContextType,
                    onTypeSelect = { type ->
                        selectedContextType = type
                        scope.launch {
                            contextSuggestions = when (type) {
                                ContextType.FILES -> loadFileSuggestions(searchQuery)
                                else -> emptyList()
                            }
                        }
                    },
                    suggestions = contextSuggestions,
                    searchQuery = searchQuery,
                    onSuggestionSelect = { suggestion ->
                        val mockContext = ContextReference.FileReference(
                            suggestion.path ?: suggestion.title, 
                            null, 
                            suggestion.subtitle
                        )
                        onContextAdd(mockContext)
                        showContextMenu = false
                        
                        // 更新文本 - 替换@符号及其后的搜索文本
                        val currentText = textValue.text
                        val currentCursor = textValue.selection.start
                        val atIndex = currentText.lastIndexOf('@', currentCursor - 1)
                        
                        if (atIndex >= 0) {
                            val beforeAt = currentText.substring(0, atIndex)
                            val afterCursor = currentText.substring(currentCursor)
                            val newText = "$beforeAt${suggestion.title} $afterCursor"
                            val newCursorPos = beforeAt.length + suggestion.title.length + 1
                            textValue = TextFieldValue(newText, TextRange(newCursorPos))
                            onTextChange(newText)
                        }
                    },
                    onClose = { showContextMenu = false }
                )
            }
        }
    }
}

/**
 * Cursor 风格的上下文选择菜单
 */
@Composable
private fun CursorStyleContextMenu(
    selectedType: ContextType,
    onTypeSelect: (ContextType) -> Unit,
    suggestions: List<ContextSuggestion>,
    searchQuery: String,
    onSuggestionSelect: (ContextSuggestion) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(450.dp)
            .background(
                JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                JewelTheme.globalColors.borders.normal,
                RoundedCornerShape(8.dp)
            )
    ) {
        Column(
            modifier = Modifier.width(450.dp)
        ) {
            // 顶部类型选择标签页
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        JewelTheme.globalColors.panelBackground,
                        RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 只显示Files类型，其他类型暂时隐藏
                ContextTypeTab(
                    type = ContextType.FILES,
                    isSelected = selectedType == ContextType.FILES,
                    onClick = { onTypeSelect(ContextType.FILES) }
                )
            }
            
            // 文件列表
            if (selectedType == ContextType.FILES && suggestions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(suggestions) { suggestion ->
                        FileItem(
                            suggestion = suggestion,
                            searchQuery = searchQuery,
                            onClick = { onSuggestionSelect(suggestion) }
                        )
                    }
                }
            } else if (suggestions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "没有找到匹配的文件",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
            }
        }
    }
}

/**
 * 上下文类型标签页
 */
@Composable
private fun ContextTypeTab(
    type: ContextType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (isSelected) 
                    JewelTheme.globalColors.borders.focused 
                else 
                    Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                type.icon,
                style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp)
            )
            Text(
                type.displayName,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) 
                        Color.White 
                    else 
                        JewelTheme.globalColors.text.normal
                )
            )
        }
    }
}

/**
 * 文件条目组件
 */
@Composable
private fun FileItem(
    suggestion: ContextSuggestion,
    searchQuery: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 文件图标
            Text(
                suggestion.icon,
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            
            // 文件信息 - 单行显示文件名和路径
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 文件名
                Text(
                    suggestion.title,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = JewelTheme.globalColors.text.normal
                    )
                )
                
                // 完整路径 - 灰色暗淡显示，支持省略
                suggestion.path?.let { path ->
                    if (path.isNotEmpty()) {
                        Text(
                            path,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 10.sp,
                                color = JewelTheme.globalColors.text.disabled.copy(alpha = 0.6f)
                            ),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
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