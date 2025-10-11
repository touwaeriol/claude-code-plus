/*
 * UnifiedChatInput.kt
 * 
 * 统一的聊天输入组件 - 现代化设计
 * 参考 Cursor 的输入框设计，提供统一容器和清晰的三层布局
 */

package com.claudecodeplus.ui.jewel.components

import com.claudecodeplus.core.logging.*
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.IndexedFileInfo
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.stringResource
import com.claudecodeplus.core.services.ProjectService
import com.claudecodeplus.ui.jewel.components.context.*
import com.claudecodeplus.ui.jewel.components.tools.JumpingDots
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.runtime.DisposableEffect
import com.claudecodeplus.ui.viewmodels.PendingTask
import com.claudecodeplus.ui.viewmodels.TaskStatus
import com.claudecodeplus.ui.viewmodels.TaskType

// 导入内联引用系统
import com.claudecodeplus.ui.jewel.components.parseInlineReferences
import com.claudecodeplus.ui.jewel.components.FileReferenceAnnotation
import com.claudecodeplus.ui.jewel.components.UnifiedContextSelector
import com.claudecodeplus.ui.jewel.components.ContextTriggerMode

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
    pendingTasks: List<com.claudecodeplus.ui.viewmodels.PendingTask> = emptyList(),
    contexts: List<ContextReference> = emptyList(),
    onContextAdd: (ContextReference) -> Unit = {},
    onContextRemove: (ContextReference) -> Unit = {},
    onSend: (String) -> Unit = {},
    onInterruptAndSend: ((String) -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    onImageSelected: (File) -> Unit = {},
    isGenerating: Boolean = false,
    enabled: Boolean = true,
    selectedModel: AiModel = AiModel.OPUS,
    actualModelId: String? = null, // 实际使用的模型ID（从 systemInit 获取）
    onModelChange: (AiModel) -> Unit = {},
    selectedPermissionMode: PermissionMode = PermissionMode.DEFAULT,
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
    
    // textLayoutResult 状态（移动到函数顶部以便在下方使用）
    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    
    // Add Context 按钮状态管理
    var showAddContextPopup by remember { mutableStateOf(false) }
    
    // 监控状态变化
    LaunchedEffect(showContextSelector) {
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
    
    // 启动时请求焦点 - 简化版，避免过度焦点管理
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200) // 增加延迟，确保界面完全稳定
        focusRequester.requestFocus()
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
            if (pendingTasks.isNotEmpty()) {
                PendingTaskBar(
                    tasks = pendingTasks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        // 顶部工具栏：上下文管理（条件显示）
        if (showContextControls && (contexts.isNotEmpty() || enabled)) {
            TopToolbar(
                contexts = contexts,
                onContextAdd = {
                    // Add Context 按钮被点击 - 显示统一上下文选择器
                    showAddContextPopup = true
                },
                onContextRemove = onContextRemove,
                enabled = enabled, // 允许AI生成期间添加上下文
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
                .clip(RoundedCornerShape(8.dp))
                .background(JewelTheme.globalColors.panelBackground)
                .clickable { focusRequester.requestFocus() }  // 🔑 关键修复：点击整个区域都能聚焦
        ) {
            // 使用简单的 BasicTextField 避免复杂的 TextArea API
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newTextFieldValue ->
                    logD("[UnifiedChatInput] 📝 主输入框变化: '${textFieldValue.text}' -> '${newTextFieldValue.text}', 长度: ${textFieldValue.text.length} -> ${newTextFieldValue.text.length}")
                    // 直接更新文本，避免复杂的处理逻辑干扰输入
                    sessionObject?.updateInputText(newTextFieldValue)
                },
                enabled = enabled,
                textStyle = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.normal,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                // 改进输入法支持
                singleLine = false,
                onTextLayout = { textLayoutResult = it },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(JewelTheme.globalColors.panelBackground)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clickable { focusRequester.requestFocus() }
                    ) {
                        // 先显示 placeholder，如果有内容则被覆盖
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                stringResource("chat_input_placeholder"),
                                color = JewelTheme.globalColors.text.disabled,
                                style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // 然后显示实际输入的文字，确保在最上层
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)  // 增加小的内边距
                        ) {
                            innerTextField()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        logD("[UnifiedChatInput] 🎯 主输入框焦点变化: isFocused=${focusState.isFocused}, hasFocus=${focusState.hasFocus}")
                        isFocused = focusState.isFocused
                    }
                    .onKeyEvent { keyEvent ->
    logD("[UnifiedChatInput] ⌨️ 主输入框键盘事件: ${keyEvent.key}, type=${keyEvent.type}, isAltPressed=${keyEvent.isAltPressed}, isShiftPressed=${keyEvent.isShiftPressed}")
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
                                if (textFieldValue.text.isNotBlank() && enabled) {
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
            actualModelId = actualModelId, // 传递实际模型ID
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
            onImageSelected = onImageSelected,
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
    
    // @ 符号上下文选择器（使用统一组件）
    UnifiedContextSelector(
        mode = ContextTriggerMode.AT_SYMBOL,
        fileIndexService = fileIndexService,
        popupOffset = Offset.Zero, // @ 模式不需要预设偏移
        onDismiss = { /* @ 模式由 SimpleInlineFileReferenceHandler 自己管理 */ },
        textFieldValue = textFieldValue,
        onTextChange = { newValue ->
            sessionObject?.updateInputText(newValue)
        },
        textLayoutResult = textLayoutResult,
        enabled = enabled // 允许AI生成期间继续输入新的提示
    )
    
    // Add Context 按钮触发的上下文选择器（使用统一组件）
    if (showAddContextPopup && fileIndexService != null) {
        // 计算 Add Context 按钮的位置，传给统一上下文选择器
        val buttonCenterPosition = remember(addContextButtonCoordinates) {
            addContextButtonCoordinates?.let { coords ->
                val position = coords.positionInRoot()
                val size = coords.size
                Offset(
                    x = position.x + size.width / 2,
                    y = position.y
                )
            } ?: Offset.Zero
        }
        
        // 使用统一上下文选择器显示 Add Context 弹窗
        UnifiedContextSelector(
            mode = ContextTriggerMode.ADD_CONTEXT,
            fileIndexService = fileIndexService,
            popupOffset = buttonCenterPosition,
            onContextAdd = onContextAdd,
            onDismiss = {
                showAddContextPopup = false
                // 延迟恢复焦点，避免与弹窗关闭冲突
                scope.launch {
                    kotlinx.coroutines.delay(100)
                    focusRequester.requestFocus()
                }
            }
        )
    }
}

@Composable
private fun PendingTaskBar(
    tasks: List<PendingTask>,
    modifier: Modifier = Modifier
) {
    val visibleTasks = tasks.filter { it.status == TaskStatus.PENDING || it.status == TaskStatus.RUNNING }
    if (visibleTasks.isEmpty()) {
        return
    }

    Column(modifier = modifier) {
        Text(
            text = "任务队列 (${visibleTasks.size})",
            style = JewelTheme.defaultTextStyle,
            color = JewelTheme.globalColors.text.info,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        visibleTasks.forEach { task ->
            val label = when (task.type) {
                TaskType.SWITCH_MODEL -> "/model ${task.alias}"
                TaskType.QUERY -> task.text.trim()
            }

            val (statusText, statusColor) = when (task.status) {
                TaskStatus.PENDING -> "排队中" to JewelTheme.globalColors.text.disabled
                TaskStatus.RUNNING -> "执行中" to JewelTheme.globalColors.text.selected
                else -> "" to JewelTheme.globalColors.text.normal
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(JewelTheme.globalColors.panelBackground)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = JewelTheme.defaultTextStyle,
                    color = JewelTheme.globalColors.text.normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = true)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = statusText,
                    style = JewelTheme.defaultTextStyle,
                    color = statusColor
                )
            }
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
    actualModelId: String? = null, // 实际模型ID
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
    onImageSelected: (File) -> Unit = {},
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
                actualModelId = actualModelId, // 传递实际模型ID
                onModelChange = onModelChange,
                selectedPermissionMode = selectedPermissionMode,
                onPermissionModeChange = onPermissionModeChange,
                skipPermissions = skipPermissions,
                onSkipPermissionsChange = onSkipPermissionsChange,
                autoCleanupContexts = autoCleanupContexts,
                onAutoCleanupContextsChange = onAutoCleanupContextsChange,
                isGenerating = isGenerating,  // 传递生成状态，在生成期间禁用控件
                enabled = enabled,
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
                    onInterruptAndSend = onInterruptAndSend,
                    onImageSelected = onImageSelected,
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
    actualModelId: String? = null, // 实际模型ID
    onModelChange: (AiModel) -> Unit,
    selectedPermissionMode: PermissionMode,
    onPermissionModeChange: (PermissionMode) -> Unit,
    skipPermissions: Boolean,
    onSkipPermissionsChange: (Boolean) -> Unit,
    autoCleanupContexts: Boolean,
    onAutoCleanupContextsChange: (Boolean) -> Unit,
    isGenerating: Boolean,  // 新增：生成状态
    enabled: Boolean,
    showModelSelector: Boolean,
    showPermissionControls: Boolean,
    modifier: Modifier = Modifier
) {
    // 计算实际的可用状态：在生成期间禁用所有控件
    val actuallyEnabled = enabled && !isGenerating

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (showModelSelector) {
            ChatInputModelSelector(
                currentModel = selectedModel,
                actualModelId = actualModelId, // 传递实际模型ID
                onModelChange = onModelChange,
                enabled = actuallyEnabled,  // 生成时禁用
                compact = false, // 使用标准模式确保正确显示模型名称
                modifier = Modifier.widthIn(max = 140.dp) // 与权限选择器宽度统一
            )
        }

        if (showPermissionControls) {
            ChatInputPermissionSelector(
                currentPermissionMode = selectedPermissionMode,
                onPermissionModeChange = onPermissionModeChange,
                enabled = actuallyEnabled,  // 生成时禁用
                compact = false, // 使用标准模式显示完整权限名称
                modifier = Modifier.widthIn(max = 140.dp) // 与模型选择器宽度统一
            )

            // 跳过权限复选框 - 标准样式
            SkipPermissionsCheckbox(
                checked = skipPermissions,
                onCheckedChange = onSkipPermissionsChange,
                enabled = actuallyEnabled  // 生成时禁用
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
                // TODO: Add getProjectPath method to ProjectService or pass working directory
                val absolutePath = relativePath
                
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
