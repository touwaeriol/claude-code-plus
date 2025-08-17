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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.core.interfaces.ProjectService
import com.claudecodeplus.ui.jewel.components.context.*
import com.claudecodeplus.ui.jewel.components.tools.JumpingDots
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import kotlinx.coroutines.launch

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
    selectedPermissionMode: PermissionMode = PermissionMode.BYPASS_PERMISSIONS,
    onPermissionModeChange: (PermissionMode) -> Unit = {},
    skipPermissions: Boolean = true,
    onSkipPermissionsChange: (Boolean) -> Unit = {},
    fileIndexService: FileIndexService? = null,
    projectService: ProjectService? = null,
    resetTrigger: Any? = null,  // 添加重置触发器
    sessionObject: SessionObject? = null  // 新增会话对象参数
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // 使用会话状态或回退到局部状态（兼容性）
    val textFieldValue = sessionObject?.inputTextFieldValue ?: TextFieldValue("")
    val showContextSelector = sessionObject?.showContextSelector ?: false
    val atSymbolPosition = sessionObject?.atSymbolPosition
    
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
        println("[UnifiedChatInput] 请求焦点")
    }
    
    // 监听enabled状态变化时重新请求焦点
    LaunchedEffect(enabled) {
        if (enabled) {
            kotlinx.coroutines.delay(50)
            focusRequester.requestFocus()
            println("[UnifiedChatInput] enabled状态变化，重新请求焦点: $enabled")
        }
    }
    
    // 统一容器
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = shadowElevation.dp,
                shape = RoundedCornerShape(8.dp),
                clip = false
            )
            .background(
                JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focusState ->
                isFocused = focusState.hasFocus
            }
    ) {
        // 顶部工具栏：上下文管理
        if (contexts.isNotEmpty() || enabled) {
            TopToolbar(
                contexts = contexts,
                onContextAdd = {
                    sessionObject?.let { session ->
                        session.showContextSelector = true
                        session.atSymbolPosition = null
                    }
                },
                onContextRemove = onContextRemove,
                enabled = enabled && !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            
            // 分隔线（使用间距而非实线）
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        // 中间输入区：纯净的文本输入
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp, max = 320.dp)  // 限制高度范围，约15行（每行约20dp）
        ) {
            // 移除输入框内的生成状态显示，现在统一在工具调用区域显示
            
            ChatInputField(
                value = textFieldValue,
                onValueChange = { sessionObject?.updateInputText(it) },
                onSend = {
                    if (textFieldValue.text.isNotBlank()) {
                        onSend(textFieldValue.text)
                        sessionObject?.clearInput()
                    }
                },
                onInterruptAndSend = if (onInterruptAndSend != null) {
                    {
                        if (textFieldValue.text.isNotBlank()) {
                            onInterruptAndSend(textFieldValue.text)
                            sessionObject?.clearInput()
                        }
                    }
                } else null,
                enabled = enabled,  // 移除 !isGenerating 限制
                focusRequester = focusRequester,
                onShowContextSelector = { position ->
                    sessionObject?.let { session ->
                        session.showContextSelector = true
                        session.atSymbolPosition = position
                    }
                },
                showPreview = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                maxHeight = 300  // 传递最大高度参数
            )
        }
        
        // 底部选项栏：模型、权限、操作按钮
        Spacer(modifier = Modifier.height(4.dp))
        
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
                if (textFieldValue.text.isNotBlank()) {
                    onSend(textFieldValue.text)
                    sessionObject?.clearInput()
                }
            },
            onStop = onStop ?: {},
            onInterruptAndSend = if (onInterruptAndSend != null) {
                {
                    if (textFieldValue.text.isNotBlank()) {
                        onInterruptAndSend(textFieldValue.text)
                        sessionObject?.clearInput()
                    }
                }
            } else null,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            // 传递上下文统计所需的参数
            messageHistory = sessionObject?.messages ?: emptyList(),
            inputText = textFieldValue.text,
            contexts = contexts
        )
    }
    
    // 上下文选择器弹窗
    if (showContextSelector) {
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
                sessionObject?.let { session ->
                    session.showContextSelector = false
                }
                
                if (atSymbolPosition != null) {
                    // @ 触发：生成内联引用
                    val markdownLink = createMarkdownContextLink(
                        displayName = context.toDisplayString(),
                        uri = context.uri
                    )
                    
                    val currentText = textFieldValue.text
                    val pos = atSymbolPosition!!
                    val newText = currentText.replaceRange(pos, pos + 1, markdownLink)
                    val newPosition = pos + markdownLink.length
                    
                    sessionObject?.updateInputText(TextFieldValue(
                        newText,
                        androidx.compose.ui.text.TextRange(newPosition)
                    ))
                } else {
                    // 按钮触发：添加到上下文列表
                    onContextAdd(context)
                }
                
                sessionObject?.atSymbolPosition = null
                focusRequester.requestFocus()
            },
            searchService = searchService
        )
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
    modifier: Modifier = Modifier
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
            modifier = Modifier.height(20.dp)
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
    contexts: List<ContextReference> = emptyList()
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：模型、权限选择器和跳过权限复选框
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModernModelSelector(
                currentModel = selectedModel,
                onModelChange = onModelChange,
                enabled = enabled && !isGenerating
            )
            
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
        
        // 右侧：操作按钮和上下文统计
        SendStopButtonGroup(
            isGenerating = isGenerating,
            onSend = onSend,
            onStop = onStop,
            hasInput = hasInput,
            enabled = enabled,
            currentModel = selectedModel,
            messageHistory = messageHistory,
            inputText = inputText,
            contexts = contexts
        )
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

private val ContextReference.uri: String
    get() = when (this) {
        is ContextReference.FileReference -> "file://${this.fullPath}"
        is ContextReference.WebReference -> url
        is ContextReference.FolderReference -> "file://${this.path}"
        is ContextReference.SymbolReference -> "claude-context://symbol/${this.name}"
        is ContextReference.ImageReference -> "file://$path"
        else -> "claude-context://unknown"
    }

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

