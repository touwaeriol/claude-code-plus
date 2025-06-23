package com.claudecodeplus.ui.redesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.ContextProvider
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea

/**
 * 智能输入区域
 * 支持 @ 引用、多行输入、快捷键等功能
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SmartInputArea(
    contextProvider: ContextProvider,
    isEnabled: Boolean,
    onSend: (String, List<ContextReference>) -> Unit,
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    val contexts = remember { mutableStateListOf<ContextReference>() }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    
    // 上下文建议
    var contextSuggestions by remember { mutableStateOf<List<ContextSuggestion>>(emptyList()) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF3C3F41))
            .padding(16.dp)
    ) {
        // 已选择的上下文
        if (contexts.isNotEmpty()) {
            SelectedContexts(
                contexts = contexts,
                onRemove = { contexts.remove(it) },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // 输入区域
        Box {
            TextArea(
                value = textValue,
                onValueChange = { newValue ->
                    val oldText = textValue.text
                    val newText = newValue.text
                    textValue = newValue
                    
                    // 检测 @ 符号
                    if (newText.length > oldText.length && newText.last() == '@') {
                        showContextMenu = true
                        contextMenuPosition = newValue.selection.start
                        searchQuery = ""
                        
                        // 加载初始建议
                        scope.launch {
                            contextSuggestions = loadContextSuggestions(contextProvider, "")
                        }
                    }
                    
                    // 更新搜索查询
                    if (showContextMenu && contextMenuPosition > 0) {
                        val atIndex = newText.lastIndexOf('@', contextMenuPosition - 1)
                        if (atIndex >= 0 && atIndex < newValue.selection.start) {
                            searchQuery = newText.substring(atIndex + 1, newValue.selection.start)
                            
                            // 更新建议
                            scope.launch {
                                contextSuggestions = loadContextSuggestions(contextProvider, searchQuery)
                            }
                        }
                    }
                },
                enabled = isEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 200.dp)
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        when {
                            // Cmd/Ctrl + Enter 发送消息
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.Enter &&
                            (event.isCtrlPressed || event.isMetaPressed) -> {
                                if (textValue.text.isNotBlank()) {
                                    onSend(textValue.text, contexts.toList())
                                    textValue = TextFieldValue("")
                                    contexts.clear()
                                }
                                true
                            }
                            // Tab 接受建议
                            showContextMenu &&
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.Tab -> {
                                contextSuggestions.firstOrNull()?.let { suggestion ->
                                    insertContext(suggestion, textValue, contextMenuPosition) { newText, context ->
                                        textValue = newText
                                        contexts.add(context)
                                        showContextMenu = false
                                    }
                                }
                                true
                            }
                            // Esc 关闭上下文菜单
                            showContextMenu &&
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.Escape -> {
                                showContextMenu = false
                                true
                            }
                            else -> false
                        }
                    },
                placeholder = {
                    Text(
                        "输入消息... 使用 @ 引用上下文",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = Color(0xFF999999)
                        )
                    )
                }
            )
            
            // 上下文菜单
            if (showContextMenu) {
                ContextMenu(
                    suggestions = contextSuggestions,
                    onSelect = { suggestion ->
                        insertContext(suggestion, textValue, contextMenuPosition) { newText, context ->
                            textValue = newText
                            contexts.add(context)
                            showContextMenu = false
                        }
                    },
                    onDismiss = { showContextMenu = false }
                )
            }
        }
        
        // 底部操作栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 快捷提示
            Text(
                "Cmd/Ctrl + Enter 发送 • Shift + Enter 换行 • @ 引用上下文",
                style = JewelTheme.defaultTextStyle.copy(
                    color = Color(0xFF999999),
                    fontSize = JewelTheme.defaultTextStyle.fontSize * 0.8f
                )
            )
            
            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 附件按钮
                IconButton(
                    onClick = {
                        // TODO: 实现文件选择
                    }
                ) {
                    Text("📎")
                }
                
                // 发送按钮
                DefaultButton(
                    onClick = {
                        if (textValue.text.isNotBlank()) {
                            onSend(textValue.text, contexts.toList())
                            textValue = TextFieldValue("")
                            contexts.clear()
                        }
                    },
                    enabled = isEnabled && textValue.text.isNotBlank()
                ) {
                    Text("🚀 发送")
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
 * 已选择的上下文显示
 */
@Composable
private fun SelectedContexts(
    contexts: List<ContextReference>,
    onRemove: (ContextReference) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        contexts.forEach { context ->
            Row(
                modifier = Modifier
                    .background(
                        Color(0xFF3574F0).copy(alpha = 0.2f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (context) {
                    is ContextReference.FileReference -> {
                        Text("📄", style = JewelTheme.defaultTextStyle)
                        Text(
                            context.path.substringAfterLast('/'),
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFF3574F0)
                            )
                        )
                    }
                    is ContextReference.SymbolReference -> {
                        Text("🔷", style = JewelTheme.defaultTextStyle)
                        Text(
                            context.name,
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFF3574F0)
                            )
                        )
                    }
                    is ContextReference.TerminalReference -> {
                        Text("💻", style = JewelTheme.defaultTextStyle)
                        Text(
                            "终端",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFF3574F0)
                            )
                        )
                    }
                    else -> {}
                }
                
                // 删除按钮
                Text(
                    "×",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFF999999)
                    ),
                    modifier = Modifier.clickable { onRemove(context) }
                )
            }
        }
    }
}

/**
 * 上下文菜单
 */
@Composable
private fun ContextMenu(
    suggestions: List<ContextSuggestion>,
    onSelect: (ContextSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    JewelDropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 300.dp)
    ) {
        if (suggestions.isEmpty()) {
            JewelDropdownMenuItem(onClick = {}) {
                Text(
                    "没有找到匹配项",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFF999999)
                    )
                )
            }
        } else {
            suggestions.forEach { suggestion ->
                JewelDropdownMenuItem(
                    onClick = { onSelect(suggestion) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 图标
                        Text(
                            suggestion.icon,
                            style = JewelTheme.defaultTextStyle
                        )
                        
                        // 内容
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                suggestion.title,
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = Color.White
                                )
                            )
                            suggestion.subtitle?.let {
                                Text(
                                    it,
                                    style = JewelTheme.defaultTextStyle.copy(
                                        color = Color(0xFF999999),
                                        fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9f
                                    )
                                )
                            }
                        }
                        
                        // 快捷键提示
                        suggestion.hint?.let {
                            Text(
                                it,
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = Color(0xFF999999),
                                    fontSize = JewelTheme.defaultTextStyle.fontSize * 0.8f
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
 * 上下文建议
 */
data class ContextSuggestion(
    val type: ContextType,
    val icon: String,
    val title: String,
    val subtitle: String? = null,
    val hint: String? = null,
    val data: Any? = null
)

enum class ContextType {
    FILE,
    SYMBOL,
    FOLDER,
    TERMINAL,
    PROBLEMS,
    GIT,
    COMMAND
}

/**
 * 加载上下文建议
 */
private suspend fun loadContextSuggestions(
    provider: ContextProvider,
    query: String
): List<ContextSuggestion> {
    val suggestions = mutableListOf<ContextSuggestion>()
    
    // 如果没有查询，显示类别
    if (query.isEmpty()) {
        suggestions.addAll(listOf(
            ContextSuggestion(
                type = ContextType.FILE,
                icon = "📄",
                title = "文件",
                subtitle = "引用项目中的文件",
                hint = "@file"
            ),
            ContextSuggestion(
                type = ContextType.SYMBOL,
                icon = "🔷",
                title = "符号",
                subtitle = "引用类、函数或变量",
                hint = "@symbol"
            ),
            ContextSuggestion(
                type = ContextType.FOLDER,
                icon = "📁",
                title = "文件夹",
                subtitle = "引用整个文件夹",
                hint = "@folder"
            ),
            ContextSuggestion(
                type = ContextType.TERMINAL,
                icon = "💻",
                title = "终端",
                subtitle = "引用终端输出",
                hint = "@terminal"
            ),
            ContextSuggestion(
                type = ContextType.PROBLEMS,
                icon = "⚠️",
                title = "问题",
                subtitle = "引用 IDE 检测到的问题",
                hint = "@problems"
            ),
            ContextSuggestion(
                type = ContextType.GIT,
                icon = "🔀",
                title = "Git",
                subtitle = "引用版本控制信息",
                hint = "@git"
            )
        ))
    } else {
        // 根据查询搜索
        when {
            query.startsWith("file") || query.all { it.isLetterOrDigit() || it in "._-/" } -> {
                // 搜索文件
                val files = provider.searchFiles(query.removePrefix("file").trim())
                files.take(10).forEach { file ->
                    suggestions.add(
                        ContextSuggestion(
                            type = ContextType.FILE,
                            icon = "📄",
                            title = file.name,
                            subtitle = file.path,
                            data = file
                        )
                    )
                }
            }
            query.startsWith("symbol") -> {
                // 搜索符号
                val symbols = provider.searchSymbols(query.removePrefix("symbol").trim())
                symbols.take(10).forEach { symbol ->
                    suggestions.add(
                        ContextSuggestion(
                            type = ContextType.SYMBOL,
                            icon = "🔷",
                            title = symbol.name,
                            subtitle = "${symbol.type} in ${symbol.file}",
                            data = symbol
                        )
                    )
                }
            }
            // 其他类型的搜索...
        }
    }
    
    return suggestions
}

/**
 * 插入上下文引用
 */
private fun insertContext(
    suggestion: ContextSuggestion,
    currentText: TextFieldValue,
    atPosition: Int,
    onInsert: (TextFieldValue, ContextReference) -> Unit
) {
    // 找到 @ 符号的位置
    val text = currentText.text
    val atIndex = text.lastIndexOf('@', atPosition - 1)
    
    if (atIndex >= 0) {
        // 替换 @ 和后面的内容
        val before = text.substring(0, atIndex)
        val after = text.substring(currentText.selection.start)
        
        // 创建引用文本
        val referenceText = when (suggestion.type) {
            ContextType.FILE -> {
                val file = suggestion.data as? FileContext
                "@${file?.name ?: suggestion.title}"
            }
            ContextType.SYMBOL -> {
                val symbol = suggestion.data as? SymbolContext
                "@${symbol?.name ?: suggestion.title}"
            }
            else -> "@${suggestion.title}"
        }
        
        // 更新文本
        val newText = before + referenceText + " " + after
        val newCursorPosition = before.length + referenceText.length + 1
        
        val newTextFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPosition)
        )
        
        // 创建上下文引用
        val contextReference = when (suggestion.type) {
            ContextType.FILE -> {
                val file = suggestion.data as? FileContext
                ContextReference.FileReference(
                    path = file?.path ?: "",
                    lines = null
                )
            }
            ContextType.SYMBOL -> {
                val symbol = suggestion.data as? SymbolContext
                ContextReference.SymbolReference(
                    name = symbol?.name ?: "",
                    type = symbol?.type ?: SymbolType.VARIABLE,
                    location = symbol?.file
                )
            }
            ContextType.TERMINAL -> {
                ContextReference.TerminalReference(
                    lines = 50,
                    filter = null
                )
            }
            else -> {
                // TODO: 处理其他类型
                ContextReference.FileReference(path = "")
            }
        }
        
        onInsert(newTextFieldValue, contextReference)
    }
}