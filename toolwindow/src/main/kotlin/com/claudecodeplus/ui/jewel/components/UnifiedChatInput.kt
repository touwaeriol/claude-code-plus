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
import com.claudecodeplus.core.interfaces.ProjectService
import com.claudecodeplus.ui.jewel.components.context.*
import com.claudecodeplus.ui.jewel.components.tools.JumpingDots
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import kotlinx.coroutines.launch

// 导入内联引用系统
import com.claudecodeplus.ui.jewel.components.parseInlineReferences
import com.claudecodeplus.ui.jewel.components.FileReferenceAnnotation

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
    
    // 统一容器 - Cursor 风格简洁设计
    Column(
        modifier = modifier
            .fillMaxWidth()
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
                    
                    // 检测@符号触发文件选择
                    val cursorPos = newTextFieldValue.selection.start
                    if (cursorPos > 0 && newTextFieldValue.text.getOrNull(cursorPos - 1) == '@') {
                        // 检查@符号前是否为空格或行首
                        val beforeAt = if (cursorPos > 1) newTextFieldValue.text[cursorPos - 2] else null
                        if (beforeAt == null || beforeAt in " \n\t") {
                            sessionObject?.let { session ->
                                session.atSymbolPosition = cursorPos - 1
                                session.showSimpleFileSelector = true
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
                                "Message Claude...",
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
                            // Enter 发送消息
                            keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp && !keyEvent.isShiftPressed -> {
                                if (textFieldValue.text.isNotBlank() && !isGenerating) {
                                    onSend(textFieldValue.text)
                                    sessionObject?.clearInput()
                                }
                                true
                            }
                            // Shift+Enter 换行
                            keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp && keyEvent.isShiftPressed -> {
                                false // 让默认处理插入换行
                            }
                            // Alt+Enter 打断并发送
                            keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp && keyEvent.isAltPressed -> {
                                if (textFieldValue.text.isNotBlank() && isGenerating) {
                                    onInterruptAndSend?.invoke(textFieldValue.text)
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
    }
    
    // 上下文选择器弹窗 - 确保互斥显示
    if (showContextSelector && !showSimpleFileSelector) {
        val searchService = remember(fileIndexService, projectService) {
            // 即使服务为 null 也创建一个基本的搜索服务
            UnifiedChatContextSearchService(fileIndexService, projectService)
        }
        
        ChatInputContextSelectorPopup(
            onDismiss = {
                sessionObject?.let { session ->
                    session.showContextSelector = false
                    session.atSymbolPosition = null
                }
                focusRequester.requestFocus()
            },
            onContextSelect = { context ->
                // 文件被选中
                
                // 弹窗已在 onDismiss 回调中关闭，这里不需要额外处理
                // 上下文选择完成
                
                if (atSymbolPosition != null) {
                    // @ 触发：生成Markdown格式的内联引用
                    // @ 触发模式：生成Markdown内联引用
                    val inlineReference = when (context) {
                        is ContextReference.FileReference -> {
                            // 生成 [@文件名](file://绝对路径) 格式
                            val fileName = context.path.takeIf { it.isNotBlank() } ?: context.fullPath.substringAfterLast('/')
                            "[@$fileName](file://${context.fullPath}) "
                        }
                        is ContextReference.WebReference -> "@${context.url} "
                        else -> "@${context.toDisplayString()} "
                    }
                    
                    val currentText = textFieldValue.text
                    val pos = atSymbolPosition!!
                    val newText = currentText.replaceRange(pos, pos + 1, inlineReference)
                    val newPosition = pos + inlineReference.length
                    
                    // 更新输入文本
                    sessionObject?.updateInputText(TextFieldValue(
                        newText,
                        TextRange(newPosition)
                    ))
                } else {
                    // 按钮触发：添加到上下文列表
                    // 按钮触发模式：添加到上下文列表
                    onContextAdd(context)
                }
                
                // 请求输入框焦点
                scope.launch {
                    kotlinx.coroutines.delay(50) // 小延迟确保弹窗完全关闭后再请求焦点
                    focusRequester.requestFocus()
                }
            },
            searchService = searchService
        )
    }
    
    // 简化文件选择器弹窗（Add Context 按钮触发） - 确保互斥显示
    println("[UnifiedChatInput] showSimpleFileSelector=$showSimpleFileSelector, fileIndexService=$fileIndexService")
    if (showSimpleFileSelector && !showContextSelector && fileIndexService != null) {
        var searchResults by remember { mutableStateOf<List<IndexedFileInfo>>(emptyList()) }
        var selectedIndex by remember { mutableStateOf(0) }
        
        // 加载最近文件
        LaunchedEffect(showSimpleFileSelector) {
            if (showSimpleFileSelector) {
                try {
                    println("[UnifiedChatInput] 开始加载最近文件...")
                    val files = fileIndexService.getRecentFiles(10)
                    println("[UnifiedChatInput] 加载到 ${files.size} 个文件")
                    files.forEachIndexed { index, file ->
                        println("[UnifiedChatInput] 文件 $index: ${file.name} - ${file.relativePath}")
                    }
                    searchResults = files
                    selectedIndex = 0
                    println("[UnifiedChatInput] searchResults.size = ${searchResults.size}")
                } catch (e: Exception) {
                    println("[UnifiedChatInput] 加载最近文件失败: ${e.message}")
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
                searchQuery = "",
                scrollState = scrollState,
                popupOffset = buttonCenterPosition, // 传递按钮中心位置作为锚点
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
                enabled = enabled
            )
        }
    }
}

/**
 * 底部工具栏组件
 */
@Composable
private fun BottomToolbar(
    selectedModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    selectedPermissionMode: PermissionMode,
    onPermissionModeChange: (PermissionMode) -> Unit,
    skipPermissions: Boolean,
    onSkipPermissionsChange: (Boolean) -> Unit,
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
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：模型、权限选择器和跳过权限复选框（条件显示）
        if (showModelSelector || showPermissionControls) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showModelSelector) {
                    ModernModelSelector(
                        currentModel = selectedModel,
                        onModelChange = onModelChange,
                        enabled = enabled && !isGenerating
                    )
                }
                
                if (showPermissionControls) {
                    ModernPermissionSelector(
                        currentMode = selectedPermissionMode,
                        onModeChange = onPermissionModeChange,
                        enabled = enabled && !isGenerating
                    )
                    
                    // 跳过权限复选框
                    SkipPermissionsCheckbox(
                        checked = skipPermissions,
                        onCheckedChange = onSkipPermissionsChange,
                        enabled = enabled && !isGenerating
                    )
                }
            }
        } else {
            // 如果不显示左侧控件，用空的Box占位
            Box {}
        }
        
        // 右侧：操作按钮和上下文统计（条件显示）
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

