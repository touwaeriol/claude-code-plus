/*
 * UnifiedInputArea.kt
 * 
 * 统一输入区域组件 - 使用 Jewel 组件和 AnnotatedString
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.jewel.components.context.*
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.core.interfaces.ProjectService
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import java.text.SimpleDateFormat
import java.util.*

/**
 * 输入区域模式
 */
enum class InputAreaMode {
    INPUT,    // 输入模式
    DISPLAY   // 显示模式
}

/**
 * 统一输入区域组件
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedInputArea(
    modifier: Modifier = Modifier,
    mode: InputAreaMode,
    message: EnhancedMessage? = null,
    contexts: List<ContextReference> = emptyList(),
    onContextAdd: (ContextReference) -> Unit = {},
    onContextRemove: (ContextReference) -> Unit = {},
    onSend: (String) -> Unit = {},
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
    onContextClick: (String) -> Unit = {},
    // 新增：会话状态参数，用于无状态UI
    sessionObject: SessionObject? = null,
    inputResetTrigger: Any? = null
) {
    val focusRequester = remember { FocusRequester() }
    
    // 使用会话状态或回退到局部状态（兼容性）
    val showContextSelector = sessionObject?.showContextSelector ?: false
    val atSymbolPosition = sessionObject?.atSymbolPosition
    val textFieldValue = sessionObject?.inputTextFieldValue ?: TextFieldValue("")
    
    // 输入重置逻辑
    LaunchedEffect(inputResetTrigger) {
        if (inputResetTrigger != null && sessionObject != null) {
            sessionObject.clearInput()
        }
    }
    
    // 启动时请求焦点
    LaunchedEffect(mode) {
        if (mode == InputAreaMode.INPUT) {
            focusRequester.requestFocus()
        }
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    JewelTheme.globalColors.panelBackground,
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    JewelTheme.globalColors.borders.normal,
                    RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
        // 第一行：上下文标签
        if (contexts.isNotEmpty() || mode == InputAreaMode.INPUT) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (mode == InputAreaMode.INPUT) {
                    AddContextButton(
                        onClick = {
                            sessionObject?.let { session ->
                                session.showContextSelector = true
                                session.atSymbolPosition = null
                            }
                        },
                        enabled = enabled && !isGenerating,
                        modifier = Modifier.height(20.dp)
                    )
                }
                
                contexts.forEach { context ->
                    when (mode) {
                        InputAreaMode.INPUT -> {
                            ContextTag(
                                context = context,
                                onRemove = { onContextRemove(context) }
                            )
                        }
                        InputAreaMode.DISPLAY -> {
                            ReadOnlyContextTag(
                                context = context,
                                modifier = Modifier.height(20.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // 第二行：文本内容
        when (mode) {
            InputAreaMode.INPUT -> {
                // 输入模式 - 使用与底部相同的 UnifiedChatInput 组件，但隐藏控制元素
                UnifiedChatInput(
                    contexts = contexts,
                    onContextAdd = onContextAdd,
                    onContextRemove = onContextRemove,
                    onSend = onSend,
                    isGenerating = isGenerating,
                    enabled = enabled,
                    selectedModel = selectedModel,
                    onModelChange = onModelChange,
                    selectedPermissionMode = selectedPermissionMode,
                    onPermissionModeChange = onPermissionModeChange,
                    skipPermissions = skipPermissions,
                    onSkipPermissionsChange = onSkipPermissionsChange,
                    autoCleanupContexts = autoCleanupContexts,
                    onAutoCleanupContextsChange = onAutoCleanupContextsChange,
                    fileIndexService = fileIndexService,
                    projectService = projectService,
                    resetTrigger = inputResetTrigger,
                    sessionObject = sessionObject,
                    // 隐藏用户要求隐藏的UI元素
                    showModelSelector = false,
                    showPermissionControls = false,
                    showContextControls = false,
                    showSendButton = false,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // UnifiedChatInput 已经内置了上下文选择器功能
            }
            
            InputAreaMode.DISPLAY -> {
                // 显示模式
                message?.let { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 检查是否是压缩命令消息
                        val compactStatus = parseCompactCommandStatus(msg.content)
                        
                        if (compactStatus != null) {
                            // 显示压缩命令组件
                            CompactCommandDisplay(
                                status = compactStatus,
                                message = when (compactStatus) {
                                    CompactCommandStatus.INITIATED -> "准备压缩当前会话"
                                    CompactCommandStatus.PROCESSING -> "Claude 正在分析和压缩会话历史"
                                    CompactCommandStatus.COMPLETED -> "新的会话已创建，包含之前对话的摘要"
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (isCompactCommandMessage(msg.content)) {
                            // Caveat 消息，不显示
                            // 这些是本地命令的元数据消息，用户不需要看到
                        } else {
                            // 显示普通用户消息内容（纯文本）
                            if (msg.content.isNotBlank()) {
                                SelectionContainer {
                                    Text(
                                        text = msg.content,
                                        style = JewelTheme.defaultTextStyle.copy(
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = JewelTheme.globalColors.text.normal
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        
                        // 显示上下文引用（如果有）
                        if (msg.contexts.isNotEmpty() && compactStatus == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                msg.contexts.forEach { context ->
                                    ReadOnlyContextTag(
                                        context = context,
                                        modifier = Modifier
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 第三行：底部工具栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：模型信息
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (mode) {
                    InputAreaMode.INPUT -> {
                        // 模型选择器
                        ChatInputModelSelector(
                            currentModel = selectedModel,
                            onModelChange = onModelChange,
                            enabled = enabled && !isGenerating
                        )
                        
                        // 权限模式选择器
                        ChatInputPermissionSelector(
                            currentPermissionMode = selectedPermissionMode,
                            onPermissionModeChange = onPermissionModeChange,
                            enabled = enabled && !isGenerating
                        )
                        
                        // 跳过权限复选框
                        SkipPermissionsCheckbox(
                            checked = skipPermissions,
                            onCheckedChange = onSkipPermissionsChange,
                            enabled = enabled && !isGenerating
                        )
                        
                        // 自动清理上下文复选框
                        AutoCleanupContextsCheckbox(
                            checked = autoCleanupContexts,
                            onCheckedChange = onAutoCleanupContextsChange,
                            enabled = enabled && !isGenerating
                        )
                    }
                    InputAreaMode.DISPLAY -> {
                        message?.model?.let { model ->
                            Text(
                                text = "🤖",
                                style = JewelTheme.defaultTextStyle.copy(fontSize = 8.sp)
                            )
                            Text(
                                text = model.displayName,
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 9.sp,
                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            }
            
            // 右侧：操作按钮或时间戳
            when (mode) {
                InputAreaMode.INPUT -> {
                    SendStopButtonGroup(
                        isGenerating = isGenerating,
                        onSend = {
                            if (textFieldValue.text.isNotBlank()) {
                                onSend(textFieldValue.text)
                                sessionObject?.clearInput()
                            }
                        },
                        onStop = onStop ?: {},
                        hasInput = textFieldValue.text.isNotBlank(),
                        enabled = enabled,
                        currentModel = selectedModel,
                        messageHistory = sessionObject?.messages ?: emptyList(),
                        inputText = textFieldValue.text,
                        contexts = contexts,
                        sessionTokenUsage = sessionObject?.totalSessionTokenUsage
                    )
                }
                InputAreaMode.DISPLAY -> {
                    message?.timestamp?.let { timestamp ->
                        Text(
                            text = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(Date(timestamp)),
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 9.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
    }
    
    // 监听上下文选择器状态，确保焦点管理
    LaunchedEffect(showContextSelector) {
        if (!showContextSelector && mode == InputAreaMode.INPUT) {
            focusRequester.requestFocus()
        }
    }
}

/**
 * 只读上下文标签
 */
@Composable
private fun ReadOnlyContextTag(
    context: ContextReference,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = context.toDisplayString(),
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                color = JewelTheme.globalColors.text.normal
            )
        )
    }
}

/**
 * 将 ContextReference 转换为显示字符串
 */
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

/**
 * 上下文搜索服务实现
 */
private class ContextSearchServiceImpl(
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