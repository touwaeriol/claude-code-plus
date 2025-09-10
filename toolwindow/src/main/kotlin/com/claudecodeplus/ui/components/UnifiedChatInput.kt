/*
 * UnifiedChatInput.kt
 * 
 * 统一的聊天输入组件 - 现代化设计
 * 参考 Cursor 的输入框设计，提供统一容器和清晰的三层布局
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import com.claudecodeplus.ui.theme.Dimensions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.IndexedFileInfo
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.stringResource
import com.claudecodeplus.core.interfaces.ProjectService
import com.claudecodeplus.ui.jewel.components.context.*
import com.claudecodeplus.ui.jewel.components.tools.JumpingDots
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect

// 导入内联引用系统
import com.claudecodeplus.ui.jewel.components.parseInlineReferences
import com.claudecodeplus.ui.jewel.components.FileReferenceAnnotation

// Removed plugin-specific imports since toolwindow module should not depend on plugin module

/**
 * 统一的聊天输入组件
 * 
 * 整合所有输入相关元素到一个统一容器中，实现现代化的视觉效果
 * 
 * @param modifier 修饰符
 * @param contexts 当前选择的上下文列表
 * @param onContextAdd 添加上下文回调
 * @param onContextRemove 移除上下文回调
 * @param onSend 发送消息回调
 * @param onStop 停止生成回调
 * @param isGenerating 是否正在生成响应
 * @param enabled 是否启用输入
 * @param selectedModel 当前选择的AI模型
 * @param onModelChange 模型变更回调
 * @param selectedPermissionMode 当前权限模式
 * @param onPermissionModeChange 权限模式变更回调
 * @param skipPermissions 是否跳过权限确认
 * @param onSkipPermissionsChange 跳过权限变更回调
 * @param fileIndexService 文件索引服务（可选）
 * @param projectService 项目服务（可选）
 */
@Composable
fun UnifiedChatInput(
    modifier: Modifier = Modifier,
    contexts: List<ContextReference> = emptyList(),
    onContextAdd: (ContextReference) -> Unit = {},
    onContextRemove: (ContextReference) -> Unit = {},
    onSend: (String) -> Unit = {},
    onInterruptAndSend: ((String) -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    isGenerating: Boolean = false,
    enabled: Boolean = true,
    selectedModel: AiModel = AiModel.OPUS,
    onModelChange: (AiModel) -> Unit = {},
    selectedPermissionMode: PermissionMode = PermissionMode.BYPASS,
    onPermissionModeChange: (PermissionMode) -> Unit = {},
    skipPermissions: Boolean = true,
    onSkipPermissionsChange: (Boolean) -> Unit = {},
    autoCleanupContexts: Boolean = false,
    onAutoCleanupContextsChange: (Boolean) -> Unit = {},
    fileIndexService: FileIndexService? = null,
    projectService: ProjectService? = null,
    resetTrigger: Any? = null,  // 添加重置触发器
    sessionObject: SessionObject? = null,  // 新增会话对象参数
    // UI元素显示控制参数
    showModelSelector: Boolean = true,
    showPermissionControls: Boolean = true,
    showContextControls: Boolean = true,
    showSendButton: Boolean = true
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // Add Context 按钮坐标追踪
    var addContextButtonCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    
    // 使用会话状态或回退到局部状态（兼容性）
    val textFieldValue = sessionObject?.inputTextFieldValue ?: TextFieldValue("")
    val showContextSelector = sessionObject?.showContextSelector ?: false
    val showSimpleFileSelector = sessionObject?.showSimpleFileSelector ?: false
    val atSymbolPosition = sessionObject?.atSymbolPosition
    
    // 监控状态变化
    LaunchedEffect(showContextSelector, showSimpleFileSelector, atSymbolPosition) {
        // 状态变化已记录
    }
    
    // 完全简化：直接使用 TextFieldValue，不需要任何注解包装
    
    // 监听重置触发器，清空输入框
    LaunchedEffect(resetTrigger) {
        if (resetTrigger != null) {
            sessionObject?.clearInput()
        }
    }
    
    // 官方快捷键动作集成
    // 注意：由于Compose组件与Swing组件系统差异，我们继续使用onKeyEvent方式处理
    // AnAction系统更适合全局IDE快捷键，而聊天输入框的快捷键应该是局部的
    
    // 动画状态
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) 
            JewelTheme.globalColors.borders.focused 
        else 
            JewelTheme.globalColors.borders.normal,
        animationSpec = tween(200),
        label = "border color"
    )
    
    val shadowElevation by animateFloatAsState(
        targetValue = if (isFocused) 2f else 0f,
        animationSpec = tween(200),
        label = "shadow elevation"
    )
    
    // 启动时请求焦点 - 增强版
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100) // 等待组件完全渲染
        focusRequester.requestFocus()
        // 请求焦点
    }
    
    // 监听enabled状态变化时重新请求焦点
    LaunchedEffect(enabled) {
        if (enabled) {
            kotlinx.coroutines.delay(50)
            focusRequester.requestFocus()
            // enabled状态变化，重新请求焦点
        }
    }
    
    // 使用 BoxWithConstraints 检测窗口宽度并应用最小宽度保护
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val currentWidth = maxWidth
        
        // 初始化默认宽度（首次渲染时捕获）
        LaunchedEffect(currentWidth) {
            if (currentWidth > 0.dp) {
                Dimensions.MinWidth.initializeDefaultWidth(currentWidth)
            }
        }
        
        // 计算内容宽度：使用当前宽度和最小宽度的较大值
        val contentWidth = maxOf(currentWidth, Dimensions.MinWidth.INPUT_AREA)
        
        // 统一容器 - Cursor 风格简洁设计
        Column(
            modifier = Modifier
                .width(contentWidth)  // 使用计算出的内容宽度
                .background(
                    JewelTheme.globalColors.panelBackground,
                    RoundedCornerShape(12.dp)  // 增大圆角，更现代
                )
                .border(
                    width = if (isFocused) 1.5.dp else 1.dp,  // 聚焦时稍微加粗边框
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
                .onFocusChanged { focusState ->
                    isFocused = focusState.hasFocus
                }
        ) {
        // 顶部工具栏：上下文管理（条件显示）
        if (showContextControls && (contexts.isNotEmpty() || enabled)) {
            TopToolbar(
                contexts = contexts,
                onContextAdd = {
                    // Add Context 按钮被点击 - 显示简化文件列表
                    sessionObject?.let { session ->
                        // 直接显示简化的文件选择器，而不是完整的上下文选择器
                        session.showSimpleFileSelector = true
                        // showSimpleFileSelector 已设置为 true
                    } ?: Unit // sessionObject 为 null
                },
                onContextRemove = onContextRemove,
                enabled = enabled && !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),  // 增加水平内边距，减少垂直内边距
                onAddContextButtonPositioned = { coordinates ->
                    addContextButtonCoordinates = coordinates
                }
            )
            
            // 分隔线（更细致的间距）
            Spacer(modifier = Modifier.height(2.dp))
        }
        
        // 中间输入区：纯净的文本输入
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp, max = 300.dp)  // 减少最小高度，更紧凑
                .clickable { focusRequester.requestFocus() }  // 🔑 关键修复：点击整个区域都能聚焦
        ) {
            // 使用简单的 BasicTextField 避免复杂的 TextArea API
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newTextFieldValue ->
                    sessionObject?.updateInputText(newTextFieldValue)
                    
                    // 检测@符号触发上下文选择器
                    val cursorPos = newTextFieldValue.selection.start
                    val text = newTextFieldValue.text
                    
                    // 查找光标前最近的@符号
                    var atPos: Int? = null
                    for (i in (cursorPos - 1) downTo 0) {
                        when (text[i]) {
                            '@' -> {
                                // 检查@符号前是否为空格或行首
                                val beforeAt = if (i > 0) text[i - 1] else null
                                if (beforeAt == null || beforeAt in " \n\t") {
                                    atPos = i
                                    break
                                }
                            }
                            ' ', '\n', '\t' -> break // 遇到空白字符停止搜索
                        }
                    }
                    
                    sessionObject?.let { session ->
                        if (atPos != null) {
                            // 找到了有效的@符号，显示简化文件选择器（与Add Context按钮相同）
                            session.atSymbolPosition = atPos
                            session.showSimpleFileSelector = true  // 显示简化文件选择器
                            session.showContextSelector = false  // 确保完整上下文选择器关闭
                        } else {
                            // 没有找到有效的@符号，关闭选择器
                            if (session.showSimpleFileSelector && session.atSymbolPosition != null) {
                                session.showSimpleFileSelector = false
                                session.atSymbolPosition = null
                            }
                        }
                    }
                },
                enabled = enabled,
                textStyle = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.normal
                ),
                cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable { focusRequester.requestFocus() }  // 🔑 内部区域也可点击聚焦
                    ) {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                stringResource("chat_input_placeholder"),
                                color = JewelTheme.globalColors.text.disabled,
                                style = JewelTheme.defaultTextStyle
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        when {
                            // Alt+Enter 打断并发送 (优先级最高)
                            keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp && keyEvent.isAltPressed -> {
                                if (textFieldValue.text.isNotBlank() && isGenerating) {
                                    onInterruptAndSend?.invoke(textFieldValue.text)
                                    sessionObject?.clearInput()
                                }
                                true
                            }
                            // Shift+Enter 或 Ctrl+J 换行 (中等优先级)
                            (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp && keyEvent.isShiftPressed) ||
                            (keyEvent.key == Key.J && keyEvent.type == KeyEventType.KeyUp && keyEvent.isCtrlPressed) -> {
                                val currentPos = textFieldValue.selection.start
                                val newText = textFieldValue.text.substring(0, currentPos) + "\n" + 
                                              textFieldValue.text.substring(currentPos)
                                val newPosition = currentPos + 1
                                sessionObject?.updateInputText(
                                    TextFieldValue(
                                        text = newText,
                                        selection = TextRange(newPosition)
                                    )
                                )
                                true // 阻止默认处理
                            }
                            // Ctrl+U 清空光标位置到行首 (中等优先级)
                            keyEvent.key == Key.U && keyEvent.type == KeyEventType.KeyUp && keyEvent.isCtrlPressed -> {
                                val currentText = textFieldValue.text
                                val cursorPos = textFieldValue.selection.start
                                
                                // 找到当前行的开始位置
                                val lineStart = if (cursorPos == 0) 0 else {
                                    val lineBreakPos = currentText.lastIndexOf('\n', cursorPos - 1)
                                    if (lineBreakPos == -1) 0 else lineBreakPos + 1
                                }
                                
                                // 删除从行首到光标位置的文本
                                val newText = currentText.substring(0, lineStart) + 
                                              currentText.substring(cursorPos)
                                
                                // 更新光标位置到行首
                                sessionObject?.updateInputText(
                                    TextFieldValue(
                                        text = newText,
                                        selection = TextRange(lineStart)
                                    )
                                )
                                true // 阻止默认处理
                            }
                            // Enter 发送消息 (最低优先级)
                            keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp && !keyEvent.isShiftPressed && !keyEvent.isAltPressed -> {
                                if (textFieldValue.text.isNotBlank() && !isGenerating) {
                                    onSend(textFieldValue.text)
                                    sessionObject?.clearInput()
                                }
                                true
                            }
                            else -> false
                        }
                    }
            )
        }
        
        // 底部选项栏：模型、权限、操作按钮
        Spacer(modifier = Modifier.height(2.dp))  // 减少间距
        
        BottomToolbar(
            selectedModel = selectedModel,
            onModelChange = onModelChange,
            selectedPermissionMode = selectedPermissionMode,
            onPermissionModeChange = onPermissionModeChange,
            skipPermissions = skipPermissions,
            onSkipPermissionsChange = onSkipPermissionsChange,
            autoCleanupContexts = autoCleanupContexts,
            onAutoCleanupContextsChange = onAutoCleanupContextsChange,
            isGenerating = isGenerating,
            hasInput = textFieldValue.text.isNotBlank(),
            onSend = {
                // 发送按钮逻辑：只有在非生成状态下才能发送
                if (textFieldValue.text.isNotBlank() && !isGenerating) {
                    onSend(textFieldValue.text)
                    sessionObject?.clearInput()
                }
            },
            onStop = onStop ?: {},
            onInterruptAndSend = if (onInterruptAndSend != null) {
                {
                    // 打断发送逻辑：只有在生成状态下才能打断
                    if (textFieldValue.text.isNotBlank() && isGenerating) {
                        onInterruptAndSend?.invoke(textFieldValue.text)
                        sessionObject?.clearInput()
                    }
                }
            } else null,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),  // 与顶部工具栏一致
            // 传递上下文统计所需的参数
            messageHistory = sessionObject?.messages ?: emptyList(),
            inputText = textFieldValue.text,
            contexts = contexts,
            sessionObject = sessionObject,
            // 传递显示控制参数
            showModelSelector = showModelSelector,
            showPermissionControls = showPermissionControls,
            showSendButton = showSendButton
        )
        }  // 关闭 Column
    }  // 关闭 BoxWithConstraints
    
    // 注释掉错误的完整上下文选择器弹窗 - @符号现在使用简化文件选择器
    
    // 简化文件选择器弹窗（Add Context 按钮或@符号触发） - 统一使用简化文件选择器
    println("[UnifiedChatInput] showSimpleFileSelector=$showSimpleFileSelector, atSymbolPosition=$atSymbolPosition, fileIndexService=$fileIndexService")
    if (showSimpleFileSelector && fileIndexService != null) {
        var searchResults by remember { mutableStateOf<List<IndexedFileInfo>>(emptyList()) }
        var selectedIndex by remember { mutableStateOf(0) }
        var isIndexing by remember { mutableStateOf(false) }
        var currentSearchQuery by remember { mutableStateOf("") }
        
        // 提取@符号后的搜索关键词
        val searchQuery = remember(textFieldValue.text, atSymbolPosition) {
            if (atSymbolPosition != null) {
                val text = textFieldValue.text
                val cursorPos = textFieldValue.selection.start
                if (cursorPos > atSymbolPosition) {
                    // 提取@符号后到光标位置的文本作为搜索关键词
                    val rawQuery = text.substring(atSymbolPosition + 1, cursorPos).trim()
                    // 清理中文标点符号，替换为英文对应符号
                    val query = rawQuery
                        .replace("。", ".")  // 中文句号转英文句号
                        .replace("，", ",")  // 中文逗号转英文逗号
                        .replace("：", ":")  // 中文冒号转英文冒号
                        .replace("；", ";")  // 中文分号转英文分号
                    println("[UnifiedChatInput] 提取搜索关键词: '$query' (原始: '$rawQuery')")
                    query
                } else {
                    ""
                }
            } else {
                ""
            }
        }
        
        // 根据是否有搜索关键词来决定加载策略
        LaunchedEffect(showSimpleFileSelector, searchQuery) {
            if (showSimpleFileSelector) {
                try {
                    // 检查索引状态
                    isIndexing = !fileIndexService.isIndexReady()
                    
                    if (isIndexing) {
                        println("[UnifiedChatInput] 项目正在建立索引，使用基础文件搜索...")
                    }
                    
                    val files = if (searchQuery.isBlank()) {
                        // 没有搜索关键词时显示最近文件
                        println("[UnifiedChatInput] 显示最近文件...")
                        fileIndexService.getRecentFiles(10)
                    } else {
                        // 有搜索关键词时进行文件搜索
                        println("[UnifiedChatInput] 搜索文件，关键词: '$searchQuery'")
                        fileIndexService.searchFiles(searchQuery, 10, emptyList())
                    }
                    
                    println("[UnifiedChatInput] 加载到 ${files.size} 个文件")
                    files.forEachIndexed { index, file ->
                        println("[UnifiedChatInput] 文件 $index: ${file.name} - ${file.relativePath}")
                    }
                    searchResults = files
                    selectedIndex = 0
                    currentSearchQuery = searchQuery
                    println("[UnifiedChatInput] searchResults.size = ${searchResults.size}")
                } catch (e: Exception) {
                    println("[UnifiedChatInput] 文件搜索失败: ${e.message}")
                    e.printStackTrace()
                    searchResults = emptyList()
                }
            }
        }
        
        println("[UnifiedChatInput] searchResults.isNotEmpty() = ${searchResults.isNotEmpty()}")
        if (searchResults.isNotEmpty()) {
            println("[UnifiedChatInput] 渲染 SimpleFilePopup，searchResults.size=${searchResults.size}")
            val scrollState = rememberLazyListState()
            
            // 计算按钮的绝对位置传给弹窗
            val buttonCenterPosition = remember(addContextButtonCoordinates) {
                addContextButtonCoordinates?.let { coords ->
                    val position = coords.positionInRoot()
                    val size = coords.size
                    // 返回按钮中心位置
                    Offset(
                        x = position.x + size.width / 2,
                        y = position.y
                    )
                } ?: Offset.Zero
            }
            
            ButtonFilePopup(
                results = searchResults,
                selectedIndex = selectedIndex,
                searchQuery = currentSearchQuery,
                scrollState = scrollState,
                popupOffset = buttonCenterPosition, // 传递按钮中心位置作为锚点
                isIndexing = isIndexing, // 传递索引状态
                onItemSelected = { selectedFile ->
                    // 根据触发方式决定处理逻辑
                    val currentAtPosition = sessionObject?.atSymbolPosition
                    if (currentAtPosition != null) {
                        // @符号触发：插入@相对路径到文本中
                        val currentText = sessionObject?.inputTextFieldValue ?: androidx.compose.ui.text.input.TextFieldValue("")
                        val simpleReference = "@${selectedFile.relativePath}"
                        
                        // 计算替换范围（从@符号开始到当前光标位置）
                        val replaceEndPos = currentText.selection.start
                        
                        // 检查是否需要添加空格
                        val needsSpace = replaceEndPos >= currentText.text.length || 
                                        (replaceEndPos < currentText.text.length && currentText.text[replaceEndPos] !in " \n\t")
                        
                        val finalReference = if (needsSpace) "$simpleReference " else simpleReference
                        
                        val newText = currentText.text.replaceRange(
                            currentAtPosition,
                            replaceEndPos,
                            finalReference
                        )
                        val newPosition = currentAtPosition + finalReference.length
                        
                        sessionObject?.updateInputText(
                            androidx.compose.ui.text.input.TextFieldValue(
                                text = newText,
                                selection = androidx.compose.ui.text.TextRange(newPosition)
                            )
                        )
                        
                        // 清除@符号位置
                        sessionObject?.atSymbolPosition = null
                    } else {
                        // Add Context按钮：将文件添加到上下文列表（胶囊标签）
                        val contextReference = ContextReference.FileReference(
                            path = selectedFile.relativePath,
                            fullPath = selectedFile.absolutePath
                        )
                        onContextAdd(contextReference)
                    }
                    
                    // 关闭弹窗
                    sessionObject?.showSimpleFileSelector = false
                    focusRequester.requestFocus()
                },
                onDismiss = {
                    sessionObject?.showSimpleFileSelector = false
                    focusRequester.requestFocus()
                },
                onKeyEvent = { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionUp -> {
                                selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                true
                            }
                            Key.DirectionDown -> {
                                selectedIndex = (selectedIndex + 1).coerceAtMost(searchResults.size - 1)
                                true
                            }
                            Key.Enter -> {
                                if (selectedIndex in searchResults.indices) {
                                    val selectedFile = searchResults[selectedIndex]
                                    
                                    // 根据触发方式决定处理逻辑
                                    val currentAtPosition = sessionObject?.atSymbolPosition
                                    if (currentAtPosition != null) {
                                        // @符号触发：插入@相对路径到文本中
                                        val currentText = sessionObject?.inputTextFieldValue ?: androidx.compose.ui.text.input.TextFieldValue("")
                                        val simpleReference = "@${selectedFile.relativePath}"
                                        
                                        // 计算替换范围（从@符号开始到当前光标位置）
                                        val replaceEndPos = currentText.selection.start
                                        
                                        // 检查是否需要添加空格
                                        val needsSpace = replaceEndPos >= currentText.text.length || 
                                                        (replaceEndPos < currentText.text.length && currentText.text[replaceEndPos] !in " \n\t")
                                        
                                        val finalReference = if (needsSpace) "$simpleReference " else simpleReference
                                        
                                        val newText = currentText.text.replaceRange(
                                            currentAtPosition,
                                            replaceEndPos,
                                            finalReference
                                        )
                                        val newPosition = currentAtPosition + finalReference.length
                                        
                                        sessionObject?.updateInputText(
                                            androidx.compose.ui.text.input.TextFieldValue(
                                                text = newText,
                                                selection = androidx.compose.ui.text.TextRange(newPosition)
                                            )
                                        )
                                        
                                        // 清除@符号位置
                                        sessionObject?.atSymbolPosition = null
                                    } else {
                                        // Add Context按钮：将文件添加到上下文列表（胶囊标签）
                                        val contextReference = ContextReference.FileReference(
                                            path = selectedFile.relativePath,
                                            fullPath = selectedFile.absolutePath
                                        )
                                        onContextAdd(contextReference)
                                    }
                                    
                                    sessionObject?.showSimpleFileSelector = false
                                }
                                true
                            }
                            Key.Escape -> {
                                sessionObject?.showSimpleFileSelector = false
                                true
                            }
                            else -> false
                        }
                    } else false
                }
            )
        }
    }
}

/**
 * 顶部工具栏组件
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopToolbar(
    contexts: List<ContextReference>,
    onContextAdd: () -> Unit,
    onContextRemove: (ContextReference) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onAddContextButtonPositioned: (androidx.compose.ui.layout.LayoutCoordinates?) -> Unit = {}
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 添加上下文按钮
        AddContextButton(
            onClick = onContextAdd,
            enabled = enabled,
            modifier = Modifier
                .height(20.dp)
                .onGloballyPositioned { coordinates ->
                    onAddContextButtonPositioned(coordinates)
                }
        )
        
        // 上下文标签
        contexts.forEach { context ->
            PillContextTag(
                context = context,
                onRemove = { onContextRemove(context) },
                enabled = true  // 始终允许移除上下文，即使在生成期间
            )
        }
    }
}

/**
 * 底部工具栏组件 - 响应式布局版本
 */
@Composable
private fun BottomToolbar(
    selectedModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    selectedPermissionMode: PermissionMode,
    onPermissionModeChange: (PermissionMode) -> Unit,
    skipPermissions: Boolean,
    onSkipPermissionsChange: (Boolean) -> Unit,
    autoCleanupContexts: Boolean,
    onAutoCleanupContextsChange: (Boolean) -> Unit,
    isGenerating: Boolean,
    hasInput: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onInterruptAndSend: (() -> Unit)? = null,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    // 新增参数，用于上下文统计
    messageHistory: List<EnhancedMessage> = emptyList(),
    inputText: String = "",
    contexts: List<ContextReference> = emptyList(),
    sessionObject: SessionObject? = null,  // 会话对象
    // UI元素显示控制参数
    showModelSelector: Boolean = true,
    showPermissionControls: Boolean = true,
    showSendButton: Boolean = true
) {
    // 响应式布局：使用 BoxWithConstraints 获取实际可用宽度
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val availableWidth = maxWidth
        
        // 计算工具栏宽度：使用当前宽度和最小宽度的较大值
        val toolbarWidth = maxOf(availableWidth, Dimensions.MinWidth.BOTTOM_TOOLBAR)
        
        Row(
            modifier = Modifier.width(toolbarWidth),  // 应用最小宽度保护
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：响应式控件组
            ResponsiveControlsGroup(
                availableWidth = toolbarWidth,  // 使用计算后的工具栏宽度
                selectedModel = selectedModel,
                onModelChange = onModelChange,
                selectedPermissionMode = selectedPermissionMode,
                onPermissionModeChange = onPermissionModeChange,
                skipPermissions = skipPermissions,
                onSkipPermissionsChange = onSkipPermissionsChange,
                autoCleanupContexts = autoCleanupContexts,
                onAutoCleanupContextsChange = onAutoCleanupContextsChange,
                enabled = enabled,  // 保持控件在生成期间可用，允许用户修改设置
                showModelSelector = showModelSelector,
                showPermissionControls = showPermissionControls,
                modifier = Modifier.weight(1f, fill = false)
            )
            
            // 右侧：发送按钮（固定位置）
            if (showSendButton) {
                SendStopButtonGroup(
                    isGenerating = isGenerating,
                    onSend = onSend,
                    onStop = onStop,
                    hasInput = hasInput,
                    enabled = enabled,
                    currentModel = selectedModel,
                    messageHistory = messageHistory,
                    inputText = inputText,
                    contexts = contexts,
                    sessionTokenUsage = sessionObject?.totalSessionTokenUsage
                )
            }
        }
    }
}

/**
 * 左侧控件组 - 恢复简洁美观设计，所有宽度下都显示三个控件
 */
@Composable
private fun ResponsiveControlsGroup(
    availableWidth: androidx.compose.ui.unit.Dp,
    selectedModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    selectedPermissionMode: PermissionMode,
    onPermissionModeChange: (PermissionMode) -> Unit,
    skipPermissions: Boolean,
    onSkipPermissionsChange: (Boolean) -> Unit,
    autoCleanupContexts: Boolean,
    onAutoCleanupContextsChange: (Boolean) -> Unit,
    enabled: Boolean,
    showModelSelector: Boolean,
    showPermissionControls: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (showModelSelector) {
            ChatInputModelSelector(
                currentModel = selectedModel,
                onModelChange = onModelChange,
                enabled = enabled,
                compact = false, // 使用标准模式确保正确显示模型名称
                modifier = Modifier.widthIn(max = 140.dp) // 与权限选择器宽度统一
            )
        }
        
        if (showPermissionControls) {
            ChatInputPermissionSelector(
                currentPermissionMode = selectedPermissionMode,
                onPermissionModeChange = onPermissionModeChange,
                enabled = enabled,
                compact = false, // 使用标准模式显示完整权限名称
                modifier = Modifier.widthIn(max = 140.dp) // 与模型选择器宽度统一
            )
            
            // 跳过权限复选框 - 标准样式
            SkipPermissionsCheckbox(
                checked = skipPermissions,
                onCheckedChange = onSkipPermissionsChange,
                enabled = enabled
            )
            
            // 自动清理上下文复选框 - 暂时隐藏，默认不自动清理
            // TODO: 后续需要时再显示此功能
            // AutoCleanupContextsCheckbox(
            //     checked = autoCleanupContexts,
            //     onCheckedChange = onAutoCleanupContextsChange,
            //     enabled = enabled
            // )
        }
    }
}

// 辅助函数和扩展
private fun ContextReference.toDisplayString(): String {
    return when (this) {
        is ContextReference.FileReference -> path.substringAfterLast('/')
        is ContextReference.WebReference -> title ?: url
        is ContextReference.FolderReference -> path.substringAfterLast('/')
        is ContextReference.SymbolReference -> name
        is ContextReference.ImageReference -> filename
        else -> "context"
    }
}

// URI 属性已在 ContextReference 模型中定义，移除重复扩展

// 内部的 ContextSearchService 实现
private class UnifiedChatContextSearchService(
    private val fileIndexService: FileIndexService?,
    private val projectService: ProjectService?
) : ContextSearchService {
    
    override suspend fun searchFiles(query: String, maxResults: Int): List<FileSearchResult> {
        return try {
            val files = fileIndexService?.searchFiles(query, maxResults) ?: emptyList()
            files.map { fileInfo ->
                val contextItem = FileContextItem(
                    name = fileInfo.name,
                    relativePath = fileInfo.relativePath,
                    absolutePath = fileInfo.absolutePath,
                    isDirectory = fileInfo.isDirectory,
                    fileType = fileInfo.fileType
                )
                
                val weight = when {
                    fileInfo.name.equals(query, ignoreCase = true) -> 100
                    fileInfo.name.startsWith(query, ignoreCase = true) -> 80
                    fileInfo.name.contains(query, ignoreCase = true) -> 60
                    else -> 40
                }
                
                FileSearchResult(contextItem, weight, FileSearchResult.MatchType.CONTAINS_NAME)
            }.sortedByDescending { it.weight }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun searchFilesFlow(query: String, maxResults: Int) = kotlinx.coroutines.flow.flow {
        emit(searchFiles(query, maxResults))
    }
    
    override suspend fun getRootFiles(maxResults: Int): List<FileContextItem> {
        return try {
            val files = fileIndexService?.getRecentFiles(maxResults) ?: emptyList()
            files.map { fileInfo ->
                FileContextItem(
                    name = fileInfo.name,
                    relativePath = fileInfo.relativePath,
                    absolutePath = fileInfo.absolutePath,
                    isDirectory = fileInfo.isDirectory,
                    fileType = fileInfo.fileType
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun validateUrl(url: String): Boolean {
        return url.matches(Regex("^(https?|file)://.*"))
    }
    
    override suspend fun getWebInfo(url: String): WebContextItem? {
        return if (validateUrl(url)) {
            WebContextItem(url = url, title = null, description = null)
        } else {
            null
        }
    }
    
    override suspend fun getFileInfo(relativePath: String): FileContextItem? {
        return try {
            val content = fileIndexService?.getFileContent(relativePath)
            if (content != null) {
                val fileName = relativePath.substringAfterLast('/')
                val absolutePath = projectService?.getProjectPath()?.let { "$it/$relativePath" } ?: relativePath
                
                FileContextItem(
                    name = fileName,
                    relativePath = relativePath,
                    absolutePath = absolutePath,
                    isDirectory = false,
                    fileType = fileName.substringAfterLast('.', ""),
                    size = content.length.toLong()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

