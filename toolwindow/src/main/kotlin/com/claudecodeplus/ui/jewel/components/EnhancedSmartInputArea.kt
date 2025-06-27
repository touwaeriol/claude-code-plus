package com.claudecodeplus.ui.jewel.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text

/**
 * 生成状态指示器 - 显示"Generating..."和动画点
 */
@Composable
fun GeneratingIndicator(
    onStop: () -> Unit = {},
    onAcceptAll: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dotCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            dotCount = (dotCount + 1) % 4 // 0, 1, 2, 3 个点循环
            delay(500) // 每500ms变化一次
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
        // 左侧：Generating + 动画点
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
                modifier = Modifier.width(12.dp) // 固定宽度避免跳动
            )
        }
        
        // 右侧：按钮组
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stop 按钮
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
            
            // 接受按钮
            Text(
                "Accept all ⌘↩",
                style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.normal,
                    fontSize = 11.sp
                ),
                modifier = Modifier
                    .background(JewelTheme.globalColors.borders.focused, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .clickable { onAcceptAll() }
            )
        }
    }
}

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
    
    fun registerShortcut(shortcut: KeyboardShortcut) {
        shortcuts.add(shortcut)
    }
    
    fun registerShortcuts(vararg shortcuts: KeyboardShortcut) {
        this.shortcuts.addAll(shortcuts)
    }
    
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
        
        return shortcuts
            .filter { it.key == event.key }
            .filter { it.modifiers == eventModifiers }
            .filter { it.enabled(state) }
            .firstOrNull()
            ?.action
    }
    
    fun getAllShortcuts(): List<KeyboardShortcut> = shortcuts.toList()
    
    fun clear() {
        shortcuts.clear()
    }
}

/**
 * 默认快捷键配置
 */
object DefaultKeyboardShortcuts {
    fun createDefaultManager(): KeyboardShortcutManager {
        val manager = KeyboardShortcutManager()
        
        manager.registerShortcuts(
            // Enter - 发送消息（仅在没有Shift修饰键时）
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
 * 增强的智能输入区域组件
 * 支持多行输入、快捷键系统、动态高度、模型选择等功能
 */
@OptIn(ExperimentalComposeUiApi::class)
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
    shortcutManager: KeyboardShortcutManager = DefaultKeyboardShortcuts.createDefaultManager(),
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf(TextFieldValue(text)) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    
    // 模型选择状态
    var selectedModel by remember { mutableStateOf(ClaudeModel.SONNET) }
    var showModelMenu by remember { mutableStateOf(false) }
    
    // 上下文建议
    var contextSuggestions by remember { mutableStateOf<List<MockContextSuggestion>>(emptyList()) }
    
    // 计算输入框高度 - 根据内容行数动态调整
    val density = LocalDensity.current
    val lineHeight = with(density) { 18.sp.toDp() }
    val minHeight = 32.dp
    val maxHeight = 120.dp
    
    val textLines = textValue.text.count { it == '\n' } + 1
    val additionalHeight = lineHeight * (textLines - 1)
    val dynamicHeight = (minHeight + additionalHeight).coerceIn(minHeight, maxHeight)
    
    // 同步text prop和内部状态
    LaunchedEffect(text) {
        if (text != textValue.text) {
            textValue = TextFieldValue(text)
        }
    }
    
    // 构建当前状态
    val currentState = SmartInputState(
        text = textValue.text,
        isContextMenuOpen = showContextMenu,
        hasContextSuggestions = contextSuggestions.isNotEmpty(),
        isGenerating = isGenerating,
        isEnabled = enabled
    )
    
    // 快捷键动作处理器
    val handleKeyboardAction = { action: KeyboardAction ->
        println("DEBUG: handleKeyboardAction called with action: $action")
        when (action) {
            is KeyboardAction.SendMessage -> {
                println("DEBUG: Processing SendMessage action")
                println("DEBUG: Current text: '${textValue.text}'")
                println("DEBUG: Text is blank: ${textValue.text.isBlank()}")
                if (textValue.text.isNotBlank()) {
                    println("DEBUG: Text is not blank, calling onSend()")
                    onSend()
                    textValue = TextFieldValue("")
                    onTextChange("")
                    println("DEBUG: SendMessage completed")
                } else {
                    println("DEBUG: Text is blank, not sending")
                }
            }
            
            is KeyboardAction.InsertNewLine -> {
                // 由系统处理换行，这里不需要特殊处理
            }
            
                         is KeyboardAction.AcceptSuggestion -> {
                 contextSuggestions.firstOrNull()?.let { suggestion ->
                     val mockContext = ContextReference.FileReference(suggestion.title, null, null)
                     onContextAdd(mockContext)
                     showContextMenu = false
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
                    contextSuggestions = loadMockContextSuggestions("")
                }
            }
            
            is KeyboardAction.ClearInput -> {
                textValue = TextFieldValue("")
                onTextChange("")
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
        // 生成状态指示器 - 只在生成时显示
        if (isGenerating) {
            GeneratingIndicator(
                onStop = { onStop?.invoke() },
                onAcceptAll = { /* TODO: 实现接受所有功能 */ },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 主输入框容器 - 更紧凑的设计
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
                // 第一行：左上角 Add Context 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    // Add Context 按钮 - 左上角
                    Box(
                        modifier = Modifier
                            .background(Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable(enabled = enabled && !isGenerating) {
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
                                    color = JewelTheme.globalColors.text.disabled
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
                    BasicTextField(
                        value = textValue,
                        onValueChange = { newValue ->
                            val oldText = textValue.text
                            val newText = newValue.text
                            textValue = newValue
                            onTextChange(newText)
                            
                            // ============================================================================
                            // 【重要】@ 符号上下文菜单触发逻辑 - 请勿随意修改！！！
                            // ============================================================================
                            // 用户需求：当输入@时，如果@前后均没有字符，则显示上下文选择框
                            // 检测逻辑：
                            // 1. 新增了@字符
                            // 2. @前面没有字符（或前面是空格/换行）
                            // 3. @后面没有字符（光标在@后面）
                            // ============================================================================
                            if (newText.length > oldText.length && newText.last() == '@') {
                                val atPosition = newText.lastIndexOf('@')
                                val beforeAt = if (atPosition > 0) newText[atPosition - 1] else ' '
                                val afterAt = if (atPosition < newText.length - 1) newText[atPosition + 1] else ' '
                                
                                // 检查@前后是否都是空白字符或边界
                                val isValidAtTrigger = (beforeAt.isWhitespace() || atPosition == 0) && 
                                                      (afterAt.isWhitespace() || atPosition == newText.length - 1)
                                
                                if (isValidAtTrigger) {
                                    handleKeyboardAction(KeyboardAction.OpenContextMenu)
                                }
                            }
                            
                            // 更新搜索查询
                            if (showContextMenu && contextMenuPosition > 0) {
                                val atIndex = newText.lastIndexOf('@', contextMenuPosition - 1)
                                if (atIndex >= 0 && atIndex < newValue.selection.start) {
                                    searchQuery = newText.substring(atIndex + 1, newValue.selection.start)
                                    
                                    // 更新建议
                                    scope.launch {
                                        contextSuggestions = loadMockContextSuggestions(searchQuery)
                                    }
                                }
                            }
                        },
                        enabled = enabled, // 允许在生成期间继续输入
                        textStyle = TextStyle(
                            color = JewelTheme.globalColors.text.normal,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        ),
                        cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Default // 改为Default以支持多行
                        ),
                        singleLine = false, // 明确设置为多行模式

                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            // ============================================================================
                            // 【重要】分层键盘事件处理架构 - 请勿随意修改！！！
                            // ============================================================================
                            // 设计原则：按优先级分层处理键盘事件，避免冲突
                            // 1. 第一层：文件搜索弹窗（如果显示） - 最高优先级
                            // 2. 第二层：主输入框的特殊键盘操作（Enter发送、Shift+Enter换行）
                            // 3. 第三层：系统默认行为
                            // 
                            // 优势：
                            // - 清晰的事件优先级
                            // - 避免多个组件争抢同一个键盘事件
                            // - 便于调试和维护
                            // ============================================================================
                            .onPreviewKeyEvent { event ->
                                println("DEBUG: Preview key event - key: ${event.key}, type: ${event.type}, shift: ${event.isShiftPressed}, contextMenu: $showContextMenu")
                                
                                // 第一层：如果文件搜索弹窗打开，所有键盘事件由弹窗处理
                                if (showContextMenu) {
                                    println("DEBUG: Context menu is open, letting it handle keyboard events")
                                    false // 让文件搜索弹窗处理所有键盘事件
                                } else {
                                    // 第二层：主输入框的特殊键盘操作
                                    when {
                                        // 处理Escape键 - 取消生成
                                        event.key == Key.Escape && event.type == KeyEventType.KeyDown -> {
                                            println("DEBUG: Escape key pressed")
                                            if (isGenerating) {
                                                println("DEBUG: Stopping generation")
                                                onStop?.invoke()
                                                true // 消费事件
                                            } else {
                                                false
                                            }
                                        }
                                        
                                        // 【核心逻辑1】处理单独Enter键用于发送消息
                                        // 只有在没有上下文菜单时才处理
                                        event.key == Key.Enter && !event.isShiftPressed && event.type == KeyEventType.KeyDown -> {
                                            println("DEBUG: Enter key pressed (no shift)")
                                            println("DEBUG: Text: '${textValue.text}'")
                                            println("DEBUG: Text is blank: ${textValue.text.isBlank()}")
                                            
                                            // 生成期间不允许发送新消息
                                            if (isGenerating) {
                                                println("DEBUG: Currently generating, treating as newline")
                                                false // 让系统处理换行
                                            } else if (textValue.text.isNotBlank() && enabled) {
                                                println("DEBUG: Sending message")
                                                handleKeyboardAction(KeyboardAction.SendMessage)
                                                true // 消费事件，阻止默认换行
                                            } else {
                                                println("DEBUG: Text blank or disabled, treating as newline")
                                                false // 让系统处理换行
                                            }
                                        }
                                        
                                        // 【核心逻辑2】处理Shift+Enter换行
                                        event.key == Key.Enter && event.isShiftPressed && event.type == KeyEventType.KeyDown -> {
                                            println("DEBUG: Shift+Enter pressed - inserting newline")
                                            // 手动插入换行符到光标位置
                                            val newText = textValue.text.substring(0, textValue.selection.start) +
                                                          "\n" + 
                                                          textValue.text.substring(textValue.selection.end)
                                            val newCursorPos = textValue.selection.start + 1
                                            textValue = TextFieldValue(
                                                text = newText,
                                                selection = TextRange(newCursorPos)
                                            )
                                            onTextChange(newText)
                                            true // 消费事件，防止重复处理
                                        }
                                        
                                        else -> {
                                            // 第三层：所有其他情况都不拦截，让系统正常处理
                                            false
                                        }
                                    }
                                }
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
                    
                    // ============================================================================
                    // 【重要】上下文菜单位置说明 - 请勿随意修改！！！
                    // ============================================================================
                    // 1. 上下文菜单必须显示在输入框上方的原因：
                    //    - 用户需求：菜单显示在上方而不是下方
                    //    - 避免菜单遮挡输入框下方的其他UI元素
                    // 2. offset(y = (-200).dp) 的含义：
                    //    - 负值表示向上偏移200dp
                    //    - 确保菜单显示在输入框上方有足够距离
                    // ============================================================================
                    if (showContextMenu) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-200).dp) // 负值向上偏移，显示在输入框上方
                        ) {
                            ContextMenu(
                                suggestions = contextSuggestions,
                                onSelect = { suggestion ->
                                    // ============================================================================
                                    // 【重要】文件选择后的处理逻辑 - 请勿随意修改！！！
                                    // ============================================================================
                                    // 用户需求：回车选中后在输入框中记录该上下文引用
                                    // 实现：将@替换为文件引用标签，如 @CLAUDE.md
                                    // ============================================================================
                                    
                                    // 找到最后一个@的位置
                                    val atPosition = textValue.text.lastIndexOf('@')
                                    if (atPosition >= 0) {
                                        // 替换@为文件引用
                                        val newText = textValue.text.substring(0, atPosition) + 
                                                     "@${suggestion.title} " + 
                                                     textValue.text.substring(atPosition + 1)
                                        val newCursorPos = atPosition + suggestion.title.length + 2 // @文件名 + 空格
                                        
                                        textValue = TextFieldValue(
                                            text = newText,
                                            selection = TextRange(newCursorPos)
                                        )
                                        onTextChange(newText)
                                        
                                        // 同时添加到上下文中（用于实际处理）
                                        val contextRef = ContextReference.FileReference(suggestion.title, null, null)
                                        onContextAdd(contextRef)
                                    }
                                    
                                    showContextMenu = false
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
                                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(8.dp))
                                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
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
                                                if (selectedModel == model) Color(0xFF4C4C4C) else Color.Transparent,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            model.displayName,
                                            style = JewelTheme.defaultTextStyle.copy(
                                                color = Color.White,
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
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 12.sp
                                )
                            )
                        }
                        
                        // 发送按钮 - 小圆形按钮
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
                                    handleKeyboardAction(KeyboardAction.SendMessage)
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
        }
        
        // 已选择的上下文标签 - 在输入框外部
        if (contexts.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(contexts) { context ->
                    ContextChip(
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
 * 上下文标签组件 - 深色主题版本
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
                JewelTheme.globalColors.panelBackground,
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
        
        // 删除按钮
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
 * 模拟上下文建议
 */
data class MockContextSuggestion(
    val icon: String,
    val title: String,
    val subtitle: String?
)

/**
 * 文件搜索弹窗 - 替代原来的上下文菜单
 * 用户需求：一旦进入上下选择，就弹出一个搜索列表进行搜索，回车选中后在输入框中记录该上下文引用
 */
@Composable
private fun ContextMenu(
    suggestions: List<MockContextSuggestion>,
    onSelect: (MockContextSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    
    // 过滤后的文件列表
    val filteredSuggestions = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            suggestions
        } else {
            suggestions.filter { 
                it.title.contains(searchQuery, ignoreCase = true) || 
                it.subtitle?.contains(searchQuery, ignoreCase = true) == true 
            }
        }
    }
    
    // 确保选中索引在有效范围内
    LaunchedEffect(filteredSuggestions.size) {
        selectedIndex = selectedIndex.coerceIn(0, (filteredSuggestions.size - 1).coerceAtLeast(0))
    }
    
    // 请求搜索框焦点
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Column(
        modifier = Modifier
            .width(400.dp)
            .heightIn(max = 300.dp)
            .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(8.dp))
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 搜索输入框
        BasicTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                selectedIndex = 0 // 重置选择到第一项
            },
            textStyle = TextStyle(
                color = JewelTheme.globalColors.text.normal,
                fontSize = 13.sp
            ),
            cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .background(
                    JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f),
                    RoundedCornerShape(6.dp)
                )
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
                .padding(8.dp)
                // ============================================================================
                // 【重要】文件搜索弹窗键盘事件处理 - 请勿随意修改！！！
                // ============================================================================
                // 使用 onPreviewKeyEvent 确保最高优先级，避免被主输入框拦截
                // 注意：主输入框的 onPreviewKeyEvent 会检查 showContextMenu 状态，
                //      如果为true则不处理任何键盘事件，完全交给这里处理
                // ============================================================================
                .onPreviewKeyEvent { event ->
                    println("DEBUG: File search dialog key event - key: ${event.key}, type: ${event.type}")
                    when {
                        // 处理方向键导航
                        event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                            selectedIndex = (selectedIndex + 1).coerceAtMost(filteredSuggestions.size - 1)
                            println("DEBUG: Navigation down, selectedIndex: $selectedIndex")
                            true
                        }
                        event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                            println("DEBUG: Navigation up, selectedIndex: $selectedIndex")
                            true
                        }
                        // 处理回车选择
                        event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                            println("DEBUG: Enter pressed in file search, selectedIndex: $selectedIndex, listSize: ${filteredSuggestions.size}")
                            if (filteredSuggestions.isNotEmpty() && selectedIndex < filteredSuggestions.size) {
                                println("DEBUG: Selecting file: ${filteredSuggestions[selectedIndex].title}")
                                onSelect(filteredSuggestions[selectedIndex])
                                true
                            } else {
                                println("DEBUG: No valid selection")
                                false
                            }
                        }
                        // 处理Escape取消
                        event.key == Key.Escape && event.type == KeyEventType.KeyDown -> {
                            println("DEBUG: Escape pressed, dismissing file search")
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                },
            decorationBox = { innerTextField ->
                if (searchQuery.isEmpty()) {
                    Text(
                        "搜索文件...",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled,
                            fontSize = 13.sp
                        )
                    )
                }
                innerTextField()
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 文件列表
        if (filteredSuggestions.isEmpty()) {
            Text(
                "没有找到匹配的文件",
                style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.disabled,
                    fontSize = 12.sp
                ),
                modifier = Modifier.padding(8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(filteredSuggestions) { index, suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(suggestion) }
                            .background(
                                if (index == selectedIndex) 
                                    JewelTheme.globalColors.borders.focused.copy(alpha = 0.3f)
                                else 
                                    Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 文件图标
                        Text(
                            suggestion.icon,
                            style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
                        )
                        
                        // 文件信息
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                suggestion.title,
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = JewelTheme.globalColors.text.normal,
                                    fontSize = 13.sp,
                                    fontWeight = if (index == selectedIndex) FontWeight.Medium else FontWeight.Normal
                                )
                            )
                            suggestion.subtitle?.let {
                                Text(
                                    it,
                                    style = JewelTheme.defaultTextStyle.copy(
                                        color = JewelTheme.globalColors.text.disabled,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                        
                        // 选中指示器
                        if (index == selectedIndex) {
                            Text(
                                "↵",
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = JewelTheme.globalColors.text.disabled,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }
        }
        
        // 底部提示
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "↑↓ 导航  ↵ 选择  Esc 取消",
            style = JewelTheme.defaultTextStyle.copy(
                color = JewelTheme.globalColors.text.disabled,
                fontSize = 10.sp
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/**
 * 模拟加载上下文建议 - 主要支持文件选择
 * 用户需求：目前支持 file 就行了
 */
private suspend fun loadMockContextSuggestions(query: String): List<MockContextSuggestion> {
    // 模拟异步加载
    kotlinx.coroutines.delay(100)
    
    // 主要提供文件选择选项，基于当前项目结构
    val allSuggestions = listOf(
        // 核心组件文件
        MockContextSuggestion("📄", "EnhancedSmartInputArea.kt", "toolwindow/src/.../components/"),
        MockContextSuggestion("📄", "JewelChatApp.kt", "toolwindow/src/.../jewel/"),
        MockContextSuggestion("📄", "JewelConversationView.kt", "toolwindow/src/.../jewel/"),
        MockContextSuggestion("📄", "ClaudeCliWrapper.kt", "cli-wrapper/src/.../sdk/"),
        
        // 配置文件
        MockContextSuggestion("📄", "build.gradle.kts", "根目录构建配置"),
        MockContextSuggestion("📄", "plugin.xml", "plugin/src/.../META-INF/"),
        MockContextSuggestion("📄", "README.md", "项目说明文档"),
        
        // 测试文件
        MockContextSuggestion("📄", "JewelChatTestApp.kt", "toolwindow-test/src/.../test/"),
        MockContextSuggestion("📄", "ClaudeCliWrapperTest.kt", "cli-wrapper/src/.../test/"),
        
        // 其他选项（次要）
        MockContextSuggestion("📁", "浏览文件", "打开文件选择器"),
        MockContextSuggestion("🔍", "搜索文件", "按名称搜索文件"),
        MockContextSuggestion("📋", "当前文件", "添加当前打开的文件")
    )
    
    return if (query.isBlank()) {
        // 无查询时显示所有建议，文件优先
        allSuggestions
    } else {
        // 有查询时按文件名和路径过滤
        allSuggestions.filter { 
            it.title.contains(query, ignoreCase = true) || 
            it.subtitle?.contains(query, ignoreCase = true) == true 
        }
    }
}