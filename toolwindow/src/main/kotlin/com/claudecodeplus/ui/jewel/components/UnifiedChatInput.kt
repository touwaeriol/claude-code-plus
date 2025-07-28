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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.core.interfaces.ProjectService
import com.claudecodeplus.ui.jewel.components.context.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
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
    projectService: ProjectService? = null
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var showContextSelector by remember { mutableStateOf(false) }
    var atSymbolPosition by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    
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
    
    // 启动时请求焦点
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
                    showContextSelector = true
                    atSymbolPosition = null
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
                .heightIn(min = 60.dp, max = 200.dp)  // 限制高度范围
                .padding(horizontal = 12.dp)
        ) {
            ChatInputField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                onSend = {
                    if (textFieldValue.text.isNotBlank()) {
                        onSend(textFieldValue.text)
                        textFieldValue = TextFieldValue("")
                    }
                },
                enabled = enabled && !isGenerating,
                focusRequester = focusRequester,
                onShowContextSelector = { position ->
                    showContextSelector = true
                    atSymbolPosition = position
                },
                showPreview = false,
                modifier = Modifier.fillMaxWidth()
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
                    textFieldValue = TextFieldValue("")
                }
            },
            onStop = onStop ?: {},
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
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
                showContextSelector = false
                atSymbolPosition = null
                focusRequester.requestFocus()
            },
            onContextSelect = { context ->
                showContextSelector = false
                
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
                    
                    textFieldValue = TextFieldValue(
                        newText,
                        androidx.compose.ui.text.TextRange(newPosition)
                    )
                } else {
                    // 按钮触发：添加到上下文列表
                    onContextAdd(context)
                }
                
                atSymbolPosition = null
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
    enabled: Boolean,
    modifier: Modifier = Modifier
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
        
        // 右侧：操作按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图片选择按钮（可选）
            ImagePickerButton(
                onImageSelected = { /* TODO: 处理图片选择 */ },
                enabled = enabled && !isGenerating,
                modifier = Modifier.size(24.dp)
            )
            
            // 发送/停止按钮
            SendStopButton(
                isGenerating = isGenerating,
                onSend = onSend,
                onStop = onStop,
                hasInput = hasInput,
                enabled = enabled,
                modifier = Modifier.size(24.dp)
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