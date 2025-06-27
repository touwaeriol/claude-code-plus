/*
 * EnhancedSmartInputArea.kt
 * 
 * 智能输入框组件 - 包含复杂的键盘事件处理逻辑
 * 
 * 关键问题说明：
 * ===============
 * 
 * 1. 键盘事件处理挑战：
 *    - Compose BasicTextField 默认不支持 Enter 发送 + Shift+Enter 换行的组合
 *    - 事件处理优先级复杂，容易导致事件冲突或重复处理
 *    - 多行输入框的默认行为与聊天输入框需求不匹配
 * 
 * 2. 已解决的问题：
 *    - 使用 onPreviewKeyEvent 确保事件优先级正确
 *    - 手动实现 Shift+Enter 换行逻辑，包括光标定位
 *    - 正确的事件消费策略，避免重复触发
 * 
 * 3. 重要提醒：
 *    - 请勿随意修改键盘事件处理逻辑
 *    - 修改前请先理解现有实现的技术背景
 *    - 任何修改都需要完整测试 Enter 和 Shift+Enter 行为
 * 
 * 测试要求：
 * - Enter 键：发送消息（文本非空时）
 * - Shift+Enter：插入换行符并正确定位光标
 * - 生成期间：Enter 应该换行而不是发送
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
                            
                            // 检测 @ 符号
                            if (newText.length > oldText.length && newText.last() == '@') {
                                handleKeyboardAction(KeyboardAction.OpenContextMenu)
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
                            /*
                             * 键盘事件处理说明 - 请勿随意修改此处逻辑！
                             * 
                             * 问题背景：
                             * 1. 用户期望：Enter键发送消息，Shift+Enter换行
                             * 2. BasicTextField默认行为：Enter键换行，无法区分Shift状态
                             * 3. 事件优先级问题：onKeyEvent优先级低，系统处理在前
                             * 
                             * 解决方案：
                             * 1. 使用onPreviewKeyEvent而非onKeyEvent - 确保在系统处理前拦截
                             * 2. 手动处理Shift+Enter - BasicTextField不支持自动Shift+Enter换行
                             * 3. 消费正确的事件 - 防止重复处理或误触发
                             * 
                             * 测试验证：
                             * - Enter键能正确发送消息（文本非空时）
                             * - Shift+Enter能正确插入换行符并定位光标
                             * - 不会出现事件重复处理或冲突
                             */
                            .onPreviewKeyEvent { event ->
                                println("DEBUG: Key event received - key: ${event.key}, type: ${event.type}, shift: ${event.isShiftPressed}")
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
                                    
                                    // 处理Enter键 - 根据Shift状态决定行为
                                    // 注意：只处理KeyDown事件，避免KeyUp重复触发
                                    event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                                        if (event.isShiftPressed) {
                                            println("DEBUG: Shift+Enter pressed - manually inserting newline")
                                            /*
                                             * 手动换行处理 - 必须手动实现的原因：
                                             * BasicTextField的多行模式下，即使返回false让系统处理Shift+Enter，
                                             * 系统也不会自动插入换行符。因此必须手动处理：
                                             * 1. 获取当前光标位置
                                             * 2. 在光标位置插入\n字符
                                             * 3. 更新光标到换行符后的位置
                                             * 4. 消费事件防止系统进一步处理
                                             */
                                            val selection = textValue.selection
                                            val newText = textValue.text.substring(0, selection.start) + 
                                                         "\n" + 
                                                         textValue.text.substring(selection.end)
                                            val newSelection = TextRange(selection.start + 1)
                                            textValue = TextFieldValue(newText, newSelection)
                                            onTextChange(newText)
                                            true // 消费事件，防止系统处理
                                        } else {
                                            println("DEBUG: Enter key pressed (no shift)")
                                            // 生成期间不允许发送新消息
                                            if (isGenerating) {
                                                println("DEBUG: Currently generating, treating as newline")
                                                false // 让系统处理换行
                                            } else if (textValue.text.isNotBlank() && enabled && !showContextMenu) {
                                                println("DEBUG: Sending message")
                                                handleKeyboardAction(KeyboardAction.SendMessage)
                                                true // 消费事件，阻止默认换行
                                            } else {
                                                println("DEBUG: Text blank or disabled, treating as newline")
                                                false // 让系统处理换行
                                            }
                                        }
                                    }
                                    
                                    else -> {
                                        // 所有其他情况都不拦截
                                        false
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
                                     val mockContext = ContextReference.FileReference(suggestion.title, null, null)
                                     onContextAdd(mockContext)
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
 * 上下文菜单 - 深色主题版本
 */
@Composable
private fun ContextMenu(
    suggestions: List<MockContextSuggestion>,
    onSelect: (MockContextSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(min = 240.dp)
            .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(8.dp))
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        if (suggestions.isEmpty()) {
            Text(
                "没有找到匹配项",
                style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.disabled,
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
                                color = JewelTheme.globalColors.text.normal,
                                fontSize = 12.sp
                            )
                        )
                        suggestion.subtitle?.let {
                            Text(
                                it,
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
    }
}

/**
 * 模拟加载上下文建议
 */
private suspend fun loadMockContextSuggestions(query: String): List<MockContextSuggestion> {
    // 模拟异步加载
    kotlinx.coroutines.delay(100)
    
    val allSuggestions = listOf(
        MockContextSuggestion("📄", "example.kt", "/path/to/example.kt"),
        MockContextSuggestion("📄", "test.java", "/path/to/test.java"),
        MockContextSuggestion("📄", "readme.md", "/path/to/readme.md"),
        MockContextSuggestion("🔷", "MyClass", "CLASS"),
        MockContextSuggestion("🔷", "myFunction", "FUNCTION"),
        MockContextSuggestion("🔷", "variable", "VARIABLE"),
        MockContextSuggestion("💻", "终端输出", "最近的命令"),
        MockContextSuggestion("📁", "工作区", "当前目录"),
        MockContextSuggestion("🔀", "Git状态", "未提交的更改")
    )
    
    return if (query.isBlank()) {
        allSuggestions
    } else {
        allSuggestions.filter { 
            it.title.contains(query, ignoreCase = true) || 
            it.subtitle?.contains(query, ignoreCase = true) == true 
        }
    }
}