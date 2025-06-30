package com.claudecodeplus.ui.redesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.ContextProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*

/**
 * 支持的Claude模型
 */
enum class ClaudeModel(val displayName: String, val cliName: String) {
    OPUS("Claude 4 Opus", "opus"),
    SONNET("Claude 4 Sonnet", "sonnet")
}

/**
 * 快捷键动作类型
 */
sealed class KeyboardAction {
    object SendMessage : KeyboardAction()
    object InsertNewLine : KeyboardAction()
    object AcceptSuggestion : KeyboardAction()
    object CloseContextMenu : KeyboardAction()
    object OpenContextMenu : KeyboardAction()
    object ClearInput : KeyboardAction()
    object FocusInput : KeyboardAction()
    data class Custom(val actionName: String, val handler: () -> Unit) : KeyboardAction()
}

/**
 * 快捷键定义
 */
data class KeyboardShortcut(
    val key: Key,
    val modifiers: Set<KeyboardModifier> = emptySet(),
    val action: KeyboardAction,
    val description: String,
    val enabled: (SmartInputState) -> Boolean = { true }
)

/**
 * 键盘修饰键
 */
enum class KeyboardModifier {
    SHIFT, CTRL, ALT, META
}

/**
 * 输入框状态
 */
data class SmartInputState(
    val text: String,
    val isContextMenuOpen: Boolean,
    val hasContextSuggestions: Boolean,
    val isGenerating: Boolean,
    val isEnabled: Boolean
)

/**
 * 快捷键管理器
 */
class KeyboardShortcutManager {
    private val shortcuts = mutableListOf<KeyboardShortcut>()
    
    /**
     * 注册快捷键
     */
    fun registerShortcut(shortcut: KeyboardShortcut) {
        shortcuts.add(shortcut)
    }
    
    /**
     * 批量注册快捷键
     */
    fun registerShortcuts(vararg shortcuts: KeyboardShortcut) {
        this.shortcuts.addAll(shortcuts)
    }
    
    /**
     * 处理键盘事件
     */
    fun handleKeyEvent(
        event: KeyEvent,
        state: SmartInputState
    ): KeyboardAction? {
        if (event.type != KeyEventType.KeyDown) return null
        
        val eventModifiers = mutableSetOf<KeyboardModifier>()
        if (event.isShiftPressed) eventModifiers.add(KeyboardModifier.SHIFT)
        if (event.isCtrlPressed) eventModifiers.add(KeyboardModifier.CTRL)
        if (event.isAltPressed) eventModifiers.add(KeyboardModifier.ALT)
        if (event.isMetaPressed) eventModifiers.add(KeyboardModifier.META)
        
        // 查找匹配的快捷键
        return shortcuts
            .filter { it.key == event.key }
            .filter { it.modifiers == eventModifiers }
            .filter { it.enabled(state) }
            .firstOrNull()
            ?.action
    }
    
    /**
     * 获取所有已注册的快捷键
     */
    fun getAllShortcuts(): List<KeyboardShortcut> = shortcuts.toList()
    
    /**
     * 清除所有快捷键
     */
    fun clear() {
        shortcuts.clear()
    }
}

/**
 * 默认快捷键配置
 */
object DefaultKeyboardShortcuts {
    
    /**
     * 创建默认的快捷键管理器
     */
    fun createDefaultManager(): KeyboardShortcutManager {
        val manager = KeyboardShortcutManager()
        
        // 注册默认快捷键
        manager.registerShortcuts(
            // Enter - 发送消息
            KeyboardShortcut(
                key = Key.Enter,
                modifiers = emptySet(),
                action = KeyboardAction.SendMessage,
                description = "发送消息",
                enabled = { state -> 
                    state.isEnabled && 
                    !state.isGenerating && 
                    state.text.isNotBlank() && 
                    !state.isContextMenuOpen 
                }
            ),
            
            // Shift + Enter - 插入换行
            KeyboardShortcut(
                key = Key.Enter,
                modifiers = setOf(KeyboardModifier.SHIFT),
                action = KeyboardAction.InsertNewLine,
                description = "插入换行",
                enabled = { state -> state.isEnabled && !state.isGenerating }
            ),
            
            // Tab - 接受上下文建议
            KeyboardShortcut(
                key = Key.Tab,
                modifiers = emptySet(),
                action = KeyboardAction.AcceptSuggestion,
                description = "接受上下文建议",
                enabled = { state -> 
                    state.isContextMenuOpen && state.hasContextSuggestions 
                }
            ),
            
            // Escape - 关闭上下文菜单
            KeyboardShortcut(
                key = Key.Escape,
                modifiers = emptySet(),
                action = KeyboardAction.CloseContextMenu,
                description = "关闭上下文菜单",
                enabled = { state -> state.isContextMenuOpen }
            ),
            
            // Ctrl + K - 打开上下文菜单
            KeyboardShortcut(
                key = Key.K,
                modifiers = setOf(KeyboardModifier.CTRL),
                action = KeyboardAction.OpenContextMenu,
                description = "打开上下文菜单",
                enabled = { state -> 
                    state.isEnabled && !state.isGenerating && !state.isContextMenuOpen 
                }
            ),
            
            // Ctrl + L - 清空输入
            KeyboardShortcut(
                key = Key.L,
                modifiers = setOf(KeyboardModifier.CTRL),
                action = KeyboardAction.ClearInput,
                description = "清空输入",
                enabled = { state -> state.isEnabled && state.text.isNotEmpty() }
            ),
            
            // Ctrl + I - 聚焦输入框
            KeyboardShortcut(
                key = Key.I,
                modifiers = setOf(KeyboardModifier.CTRL),
                action = KeyboardAction.FocusInput,
                description = "聚焦输入框",
                enabled = { _ -> true }
            )
        )
        
        return manager
    }
}

/**
 * 智能输入区域 - 完整的输入框容器设计
 * 布局：左上角Add Context，中间输入框，左下角模型选择，右下角引用和发送按钮
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SmartInputArea(
    contextProvider: ContextProvider,
    isEnabled: Boolean,
    onSend: (String, List<ContextReference>) -> Unit,
    onStop: (() -> Unit)? = null,
    isGenerating: Boolean = false,
    shortcutManager: KeyboardShortcutManager = DefaultKeyboardShortcuts.createDefaultManager(),
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    val contexts = remember { mutableStateListOf<ContextReference>() }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    
    // 模型选择状态
    var selectedModel by remember { mutableStateOf(ClaudeModel.SONNET) }
    var showModelMenu by remember { mutableStateOf(false) }
    
    // 上下文建议
    var contextSuggestions by remember { mutableStateOf<List<ContextSuggestion>>(emptyList()) }
    
    // 计算输入框高度 - 根据内容行数动态调整
    val density = LocalDensity.current
    val lineHeight = with(density) { 18.sp.toDp() }
    val minHeight = 32.dp
    val maxHeight = 120.dp
    
    val textLines = textValue.text.count { it == '\n' } + 1
    val additionalHeight = lineHeight * (textLines - 1)
    val dynamicHeight = (minHeight + additionalHeight).coerceIn(minHeight, maxHeight)
    
    // 构建当前状态
    val currentState = SmartInputState(
        text = textValue.text,
        isContextMenuOpen = showContextMenu,
        hasContextSuggestions = contextSuggestions.isNotEmpty(),
        isGenerating = isGenerating,
        isEnabled = isEnabled
    )
    
    // 快捷键动作处理器
    val handleKeyboardAction = { action: KeyboardAction ->
        when (action) {
            is KeyboardAction.SendMessage -> {
                if (textValue.text.isNotBlank()) {
                    onSend(textValue.text, contexts.toList())
                    textValue = TextFieldValue("")
                    contexts.clear()
                }
            }
            
            is KeyboardAction.InsertNewLine -> {
                // 由系统处理换行，这里不需要特殊处理
            }
            
            is KeyboardAction.AcceptSuggestion -> {
                contextSuggestions.firstOrNull()?.let { suggestion ->
                    insertContext(suggestion, textValue, contextMenuPosition) { newText, context ->
                        textValue = newText
                        contexts.add(context)
                        showContextMenu = false
                    }
                }
            }
            
            is KeyboardAction.CloseContextMenu -> {
                showContextMenu = false
            }
            
            is KeyboardAction.OpenContextMenu -> {
                showContextMenu = true
                contextMenuPosition = textValue.selection.start
                searchQuery = ""
                scope.launch {
                    contextSuggestions = loadContextSuggestions(contextProvider, "")
                }
            }
            
            is KeyboardAction.ClearInput -> {
                textValue = TextFieldValue("")
                contexts.clear()
                showContextMenu = false
            }
            
            is KeyboardAction.FocusInput -> {
                focusRequester.requestFocus()
            }
            
            is KeyboardAction.Custom -> {
                action.handler()
            }
        }
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 主输入框容器 - 更紧凑的设计
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFE1E5E9), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 第一行：左上角 Add Context 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    // Add Context 按钮 - 左上角
                    Box(
                        modifier = Modifier
                            .background(Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable(enabled = isEnabled && !isGenerating) {
                                handleKeyboardAction(KeyboardAction.OpenContextMenu)
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
                                    color = Color(0xFF6B7280)
                                )
                            )
                        }
                    }
                }
                
                // 中间：主输入框区域 - 动态高度
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dynamicHeight)
                ) {
                    TextArea(
                        value = textValue.text,
                        onValueChange = { newText ->
                            val newTextFieldValue = TextFieldValue(newText, TextRange(newText.length))
                            textValue = newTextFieldValue
                            onTextChange(newText)
                            
                            // @ 符号检测和上下文处理
                            if (newText.isNotEmpty() && newText.last() == '@') {
                                showContextMenu = true
                                contextMenuPosition = newText.length - 1
                                searchQuery = ""
                                loadContextSuggestions("")
                            } else if (showContextMenu) {
                                // 检查是否仍在@符号后搜索
                                val cursorPos = newText.length
                                val atIndex = newText.lastIndexOf('@', cursorPos - 1)
                                if (atIndex >= 0) {
                                    val searchText = newText.substring(atIndex + 1, cursorPos)
                                    if (!searchText.contains(' ') && !searchText.contains('\n')) {
                                        searchQuery = searchText
                                        loadContextSuggestions(searchText)
                                    } else {
                                        showContextMenu = false
                                    }
                                } else {
                                    showContextMenu = false
                                }
                            }
                        },
                        enabled = isEnabled && !isGenerating,
                        placeholder = "输入消息或使用 @ 引用上下文...",
                        textStyle = TextStyle(
                            color = JewelTheme.globalColors.text.normal,
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                handleKeyEvent(
                                    keyEvent = keyEvent,
                                    textValue = textValue,
                                    showContextMenu = showContextMenu,
                                    onSend = onSend,
                                    onCloseMenu = { showContextMenu = false },
                                    enabled = isEnabled,
                                    isGenerating = isGenerating
                                )
                            }
                    )
                    
                    // 上下文菜单
                    if (showContextMenu) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = 30.dp)
                        ) {
                            ContextMenu(
                                suggestions = contextSuggestions,
                                onSelect = { suggestion ->
                                    insertContext(suggestion, textValue, contextMenuPosition) { newText, context ->
                                        textValue = newText
                                        contexts.add(context)
                                        showContextMenu = false
                                    }
                                },
                                onDismiss = { handleKeyboardAction(KeyboardAction.CloseContextMenu) }
                            )
                        }
                    }
                }
                
                // 底部行：左下角模型选择 + 右下角引用和发送按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左下角：模型选择
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { showModelMenu = !showModelMenu }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                selectedModel.displayName,
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = JewelTheme.globalColors.text.disabled,
                                    fontSize = 11.sp
                                )
                            )
                            
                            Text(
                                if (showModelMenu) "▲" else "▼",
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = JewelTheme.globalColors.text.disabled,
                                    fontSize = 8.sp
                                )
                            )
                        }
                        
                        // 模型选择下拉菜单
                        if (showModelMenu) {
                            Column(
                                modifier = Modifier
                                    .offset(y = (-60).dp)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                ClaudeModel.values().forEach { model ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedModel = model
                                                showModelMenu = false
                                            }
                                            .background(
                                                if (selectedModel == model) Color(0xFFF3F4F6) else Color.Transparent,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            model.displayName,
                                            style = JewelTheme.defaultTextStyle.copy(
                                                color = Color(0xFF1F2937),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // 右下角：引用和发送按钮
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 引用按钮（更多选项）
                        IconButton(
                            onClick = { /* TODO: 实现引用功能 */ },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Text(
                                "⋯",
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = Color(0xFF6B7280),
                                    fontSize = 12.sp
                                )
                            )
                        }
                        
                        // 发送按钮 - 小圆形按钮
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    if (textValue.text.isNotBlank() && isEnabled && !isGenerating) 
                                        Color(0xFF1F2937) 
                                    else 
                                        Color(0xFFF3F4F6),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = textValue.text.isNotBlank() && isEnabled && !isGenerating) {
                                    handleKeyboardAction(KeyboardAction.SendMessage)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "↑",
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = if (textValue.text.isNotBlank() && isEnabled && !isGenerating) 
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
        }
        
        // 已选择的上下文标签 - 在输入框外部
        if (contexts.isNotEmpty()) {
            HorizontallyScrollableContainer(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    contexts.forEach { context ->
                        ContextChip(
                            context = context,
                            onRemove = { contexts.remove(it) }
                        )
                    }
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
 * 上下文标签组件 - 更紧凑版本
 */
@Composable
private fun ContextChip(
    context: ContextReference,
    onRemove: (ContextReference) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                Color(0xFFF3F4F6),
                RoundedCornerShape(8.dp)
            )
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
                        color = Color(0xFF374151),
                        fontSize = 10.sp
                    )
                )
            }
            is ContextReference.SymbolReference -> {
                Text("🔷", style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp))
                Text(
                    context.name,
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFF374151),
                        fontSize = 10.sp
                    )
                )
            }
            is ContextReference.TerminalReference -> {
                Text("💻", style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp))
                Text(
                    "终端",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = Color(0xFF374151),
                        fontSize = 10.sp
                    )
                )
            }
            else -> {}
        }
        
        // 删除按钮
        Text(
            "×",
            style = JewelTheme.defaultTextStyle.copy(
                color = Color(0xFF9CA3AF),
                fontSize = 10.sp
            ),
            modifier = Modifier.clickable { onRemove(context) }
        )
    }
}

/**
 * 上下文菜单 - 紧凑版本
 */
@Composable
private fun ContextMenu(
    suggestions: List<ContextSuggestion>,
    onSelect: (ContextSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(min = 240.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        if (suggestions.isEmpty()) {
            Text(
                "没有找到匹配项",
                style = JewelTheme.defaultTextStyle.copy(
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp
                ),
                modifier = Modifier.padding(8.dp)
            )
        } else {
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(suggestion) }
                        .background(Color.Transparent, RoundedCornerShape(4.dp))
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 图标
                    Text(
                        suggestion.icon,
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                    )
                    
                    // 内容
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            suggestion.title,
                            style = JewelTheme.defaultTextStyle.copy(
                                color = Color(0xFF1F2937),
                                fontSize = 12.sp
                            )
                        )
                        suggestion.subtitle?.let {
                            Text(
                                it,
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = Color(0xFF6B7280),
                                    fontSize = 10.sp
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
                    file = symbol?.file ?: "",
                    line = symbol?.line ?: 0
                )
            }
            ContextType.TERMINAL -> {
                ContextReference.TerminalReference(
                    content = "",
                    lines = 50
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

/**
 * 处理键盘事件
 */
private fun handleKeyEvent(
    keyEvent: KeyEvent,
    textValue: TextFieldValue,
    showContextMenu: Boolean,
    onSend: () -> Unit,
    onCloseMenu: () -> Unit,
    enabled: Boolean,
    isGenerating: Boolean
): Boolean {
    return when {
        // Enter 发送消息（非 Shift + Enter）
        keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown -> {
            if (!keyEvent.isShiftPressed && textValue.text.isNotBlank() && !showContextMenu && enabled && !isGenerating) {
                onSend()
                true
            } else {
                false
            }
        }
        
        // Escape 关闭上下文菜单
        keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown -> {
            if (showContextMenu) {
                onCloseMenu()
                true
            } else {
                false
            }
        }
        
        else -> false
    }
}

/**
 * 加载上下文建议（占位符实现）
 */
private fun loadContextSuggestions(query: String): List<ContextSuggestion> {
    // TODO: 实现真实的上下文建议加载
    return emptyList()
}