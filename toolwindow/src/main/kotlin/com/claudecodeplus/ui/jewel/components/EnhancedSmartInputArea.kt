/*
 * EnhancedSmartInputArea.kt
 * 
 * 智能输入框组件 - 包含光标跟随的上下文菜单功能
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.jewel.components.context.*
import com.claudecodeplus.ui.services.FileSearchService
import com.claudecodeplus.ui.services.ProjectService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.theme.textAreaStyle
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.flow.flow
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.Orientation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

/**
 * 构建包含上下文的完整消息
 */
private fun buildFinalMessage(contexts: List<ContextReference>, userMessage: String): String {
    if (contexts.isEmpty()) {
        return userMessage
    }
    
    val contextSection = buildString {
        appendLine("> **上下文资料**")
        appendLine("> ")
        
        contexts.forEach { context ->
            val contextLine = when (context) {
                is ContextReference.FileReference -> {
                    "> - 📄 `${context.path}`"
                }
                is ContextReference.WebReference -> {
                    val title = context.title?.let { " ($it)" } ?: ""
                    "> - 🌐 ${context.url}$title"
                }
                is ContextReference.FolderReference -> {
                    "> - 📁 `${context.path}` (${context.fileCount}个文件)"
                }
                is ContextReference.SymbolReference -> {
                    "> - 🔗 `${context.name}` (${context.type}) - ${context.file}:${context.line}"
                }
                is ContextReference.TerminalReference -> {
                    val errorFlag = if (context.isError) " ⚠️" else ""
                    "> - 💻 终端输出 (${context.lines}行)$errorFlag"
                }
                is ContextReference.ProblemsReference -> {
                    val severityText = context.severity?.let { " [$it]" } ?: ""
                    "> - ⚠️ 问题报告 (${context.problems.size}个)$severityText"
                }
                is ContextReference.GitReference -> {
                    "> - 🔀 Git ${context.type}"
                }
                is ContextReference.ImageReference -> {
                    "> - 🖼 `${context.filename}` (${context.size / 1024}KB)"
                }
                is ContextReference.SelectionReference -> {
                    "> - ✏️ 当前选择内容"
                }
                is ContextReference.WorkspaceReference -> {
                    "> - 🏠 当前工作区"
                }
            }
            appendLine(contextLine)
        }
        
        appendLine()
    }
    
    return contextSection + userMessage
}

/**
 * 增强的智能输入区域组件
 */
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
    selectedModel: AiModel = AiModel.OPUS,
    onModelChange: (AiModel) -> Unit = {},
    fileIndexService: com.claudecodeplus.ui.services.FileIndexService? = null,
    projectService: com.claudecodeplus.ui.services.ProjectService? = null,
    inlineReferenceManager: InlineReferenceManager = remember { InlineReferenceManager() },
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf(TextFieldValue(text)) }
    
    // 调试输出
    println("EnhancedSmartInputArea: selectedModel = ${selectedModel.displayName}")
    
    // 同步外部text参数到内部状态
    LaunchedEffect(text) {
        if (text != textValue.text) {
            textValue = TextFieldValue(text, TextRange(text.length))
        }
    }
    
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // 上下文选择器状态
    var showContextSelector by remember { mutableStateOf(false) }
    var atSymbolPosition by remember { mutableStateOf<Int?>(null) }
    
    // 创建上下文搜索服务
    val contextSearchService = remember(fileIndexService, projectService) {
        RealContextSearchService(fileIndexService, projectService)
    }
    
    // 检测@符号输入
    fun detectAtSymbol(newText: String, cursor: Int): Boolean {
        if (cursor == 0) return false
        
        // 检查光标前是否是@符号
        val beforeCursor = newText.substring(0, cursor)
        
        // 检查@符号前面的字符（如果存在）是否为空格或换行
        return if (beforeCursor.isNotEmpty() && beforeCursor.last() == '@') {
            // @符号前面必须是空格、换行或字符串开头
            beforeCursor.length == 1 || beforeCursor[beforeCursor.length - 2].let { 
                it == ' ' || it == '\n' 
            }
        } else {
            false
        }
    }
    
    // 处理@符号触发的上下文选择
    fun handleAtTriggerContext(context: ContextReference) {
        val pos = atSymbolPosition
        if (pos != null) {
            // 生成内联上下文文本
            val contextText = when (context) {
                is ContextReference.FileReference -> {
                    // 对于文件引用，使用内联引用管理器
                    val inlineRef = InlineFileReference(
                        displayName = context.path.substringAfterLast('/'),
                        fullPath = context.fullPath,
                        relativePath = context.path
                    )
                    // 添加到内联引用管理器
                    inlineReferenceManager.addReference(inlineRef)
                    inlineRef.getInlineText()
                }
                is ContextReference.WebReference -> "@${context.title ?: context.url.substringAfterLast('/')}"
                is ContextReference.FolderReference -> "@${context.path.substringAfterLast('/')}"
                is ContextReference.SymbolReference -> "@${context.name}"
                is ContextReference.TerminalReference -> "@terminal"
                is ContextReference.ProblemsReference -> "@problems"
                is ContextReference.GitReference -> "@git"
                is ContextReference.ImageReference -> "@${context.filename}"
                is ContextReference.SelectionReference -> "@selection"
                is ContextReference.WorkspaceReference -> "@workspace"
            }
            
            // 替换@符号为上下文引用文本
            val currentText = textValue.text
            val newText = currentText.substring(0, pos) + contextText + currentText.substring(pos + 1)
            val newCursorPos = pos + contextText.length
            
            textValue = TextFieldValue(
                text = newText,
                selection = TextRange(newCursorPos)
            )
            onTextChange(newText)
            
            // @符号添加的上下文不添加到标签列表中，只在消息文本中显示
            // 不调用 onContextAdd()
        }
    }
    
    // 动态高度计算
    val lineHeight = with(density) { 18.sp.toDp() }
    val minHeight = 40.dp
    val maxHeight = 120.dp
    val textLines = textValue.text.count { it == '\n' } + 1
    val additionalHeight = lineHeight * (textLines - 1)
    val dynamicHeight = (minHeight + additionalHeight).coerceIn(minHeight, maxHeight)
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 生成状态指示器
        if (isGenerating) {
            GeneratingIndicator(
                onStop = { onStop?.invoke() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 主输入框容器 - 统一背景，包含所有控件
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    JewelTheme.globalColors.panelBackground, // 使用Jewel主题的面板背景色
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 上下文标签显示区域（仅在有上下文时显示）
                if (contexts.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(contexts) { context ->
                            ContextTag(
                                context = context,
                                onRemove = { onContextRemove(context) }
                            )
                        }
                    }
                }
                
                // 主输入框 - 改用支持输入法的组件
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight, max = maxHeight)
                ) {
                    // 使用BasicTextField以获得更好的输入法支持
                    BasicTextField(
                        value = textValue,
                        onValueChange = { newValue ->
                            val oldCursor = textValue.selection.start
                            textValue = newValue
                            onTextChange(newValue.text)
                            
                            // 检测@符号输入
                            if (detectAtSymbol(newValue.text, newValue.selection.start)) {
                                showContextSelector = true
                                atSymbolPosition = newValue.selection.start - 1  // @符号的位置
                            }
                        },
                        enabled = enabled,
                        textStyle = JewelTheme.defaultTextStyle.copy(
                            fontSize = 14.sp,
                            color = JewelTheme.globalColors.text.normal
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textValue.text.isNotBlank() && enabled && !isGenerating) {
                                    // 直接调用发送，不在这里构建消息
                                    onSend()
                                }
                            }
                        ),
                        cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Transparent)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // 输入框区域
                                Box(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Placeholder文本
                                    if (textValue.text.isEmpty()) {
                                        Text(
                                            "输入消息，使用 @ 内联引用文件，或 ⌘K 添加上下文...",
                                            style = JewelTheme.defaultTextStyle.copy(
                                                color = JewelTheme.globalColors.text.disabled,
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                                
                                // 发送按钮区域
                                if (isGenerating && onStop != null) {
                                    // 生成中显示停止按钮
                                    DefaultButton(
                                        onClick = onStop,
                                        enabled = true,
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(32.dp)
                                    ) {
                                        Text("⏹", style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp))
                                    }
                                } else {
                                    // 正常状态显示发送按钮
                                    DefaultButton(
                                        onClick = {
                                            if (textValue.text.isNotBlank() && enabled && !isGenerating) {
                                                onSend()
                                            }
                                        },
                                        enabled = enabled && !isGenerating && textValue.text.isNotBlank(),
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(32.dp)
                                    ) {
                                        Text("↑", style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp))
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                when {
                                    keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown -> {
                                        if (keyEvent.isShiftPressed) {
                                            // Shift+Enter: 我们主动处理换行
                                            val currentText = textValue.text
                                            val currentSelection = textValue.selection
                                            val newText = currentText.substring(0, currentSelection.start) + 
                                                         "\n" + 
                                                         currentText.substring(currentSelection.end)
                                            val newSelection = TextRange(currentSelection.start + 1)
                                            textValue = TextFieldValue(newText, newSelection)
                                            onTextChange(newText)
                                            true // 消费事件，阻止系统处理
                                        } else {
                                            // Enter: 发送消息，阻止系统的换行处理
                                            if (textValue.text.isNotBlank() && enabled && !isGenerating) {
                                                // 直接调用发送，不在这里构建消息
                                                onSend()
                                                true // 消费事件，防止换行
                                            } else {
                                                true // 空内容时也阻止换行
                                            }
                                        }
                                    }
                                    keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown -> {
                                        // ESC键关闭上下文选择器
                                        if (showContextSelector) {
                                            showContextSelector = false
                                            atSymbolPosition = null
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    // Ctrl+K 或 Cmd+K 快捷键打开上下文选择器（用于添加标签）
                                    keyEvent.key == Key.K && keyEvent.type == KeyEventType.KeyDown && 
                                    (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) -> {
                                        if (!showContextSelector && enabled && !isGenerating) {
                                            showContextSelector = true
                                            atSymbolPosition = null // 标记为非@符号触发
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            }
                    )
                    
                    // 上下文选择器弹出框
                    if (showContextSelector) {
                        SimpleContextSelectorPopup(
                            onDismiss = { 
                                showContextSelector = false
                                atSymbolPosition = null
                            },
                            onContextSelect = { context ->
                                if (atSymbolPosition != null) {
                                    // @符号触发：将上下文内联插入到文本中
                                    handleAtTriggerContext(context)
                                } else {
                                    // 其他触发方式：添加到上下文列表（显示为标签）
                                    val tagContext = when (context) {
                                        is ContextReference.FileReference -> context.copy(displayType = ContextDisplayType.TAG)
                                        is ContextReference.WebReference -> context.copy(displayType = ContextDisplayType.TAG)
                                        else -> context
                                    }
                                    onContextAdd(tagContext)
                                }
                                showContextSelector = false
                                atSymbolPosition = null
                            },
                            searchService = contextSearchService,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    }
                }
                
                // 底部区域：仅显示模型选择器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左下角模型选择器
                    CompactModelSelector(
                        currentModel = selectedModel,
                        onModelChange = { model ->
                            println("EnhancedSmartInputArea: Calling onModelChange with ${model.displayName}")
                            onModelChange(model)
                        },
                        enabled = enabled && !isGenerating
                    )
                }
            }
        }
    }
}

/**
 * 生成状态指示器
 */
@Composable
fun GeneratingIndicator(
    onStop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dotCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            dotCount = (dotCount + 1) % 4
            delay(500)
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
                modifier = Modifier.width(12.dp)
            )
        }
        
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
    }
}

/**
 * 紧凑的模型选择器
 */
@Composable
private fun CompactModelSelector(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 内部状态管理 - 当 currentModel 改变时自动同步
    var internalModel by remember(currentModel) { mutableStateOf(currentModel) }
    var showDropdown by remember { mutableStateOf(false) }
    val models = listOf(AiModel.OPUS, AiModel.SONNET)
    
    // 同步外部状态到内部状态
    LaunchedEffect(currentModel) {
        if (internalModel != currentModel) {
            println("DEBUG: Syncing external model ${currentModel.displayName} to internal state")
            internalModel = currentModel
        }
    }
    
    // 添加调试输出
    println("CompactModelSelector: currentModel = ${currentModel.displayName}, internalModel = ${internalModel.displayName}, enabled = $enabled")
    
    Box(modifier = modifier) {
        // 主按钮 - 小巧设计
        Box(
            modifier = Modifier
                .background(
                    JewelTheme.globalColors.panelBackground,
                    RoundedCornerShape(4.dp)
                )
                .clickable(enabled = enabled) {
                    println("DEBUG: Model selector clicked, showDropdown = $showDropdown")
                    showDropdown = !showDropdown
                }
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = internalModel.displayName,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.normal
                    )
                )
                Text(
                    text = if (showDropdown) "▲" else "▼",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 8.sp,
                        color = JewelTheme.globalColors.text.disabled
                    )
                )
            }
        }
        
        // 使用 Popup 向上展开的小巧下拉列表
        if (showDropdown) {
            println("DEBUG: Showing dropdown with ${models.size} models")
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, -8), // 紧贴按钮向上
                onDismissRequest = { showDropdown = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            JewelTheme.globalColors.panelBackground,
                            RoundedCornerShape(4.dp)
                        )
                        .width(120.dp) // 固定小宽度
                        .padding(2.dp)
                ) {
                    models.forEach { model ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    println("DEBUG: Selected model ${model.displayName}")
                                    internalModel = model
                                    showDropdown = false
                                    onModelChange(model)
                                    println("DEBUG: Called onModelChange with ${model.displayName}")
                                }
                                .background(
                                    if (model == internalModel) 
                                        JewelTheme.globalColors.borders.focused.copy(alpha = 0.15f)
                                    else 
                                        Color.Transparent,
                                    RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = model.displayName,
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 10.sp,
                                    color = if (model == internalModel) 
                                        JewelTheme.globalColors.text.normal 
                                    else 
                                        JewelTheme.globalColors.text.disabled
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
 * 真实的上下文搜索服务实现
 * 基于新的FileIndexService接口
 */
class RealContextSearchService(
    private val fileIndexService: com.claudecodeplus.ui.services.FileIndexService? = null,
    private val projectService: com.claudecodeplus.ui.services.ProjectService? = null
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
                
                // 计算搜索权重
                val weight = when {
                    fileInfo.name.equals(query, ignoreCase = true) -> 100
                    fileInfo.name.startsWith(query, ignoreCase = true) -> 80
                    fileInfo.name.contains(query, ignoreCase = true) -> 60
                    fileInfo.relativePath.contains(query, ignoreCase = true) -> 40
                    else -> 20
                }
                
                val matchType = when {
                    fileInfo.name.equals(query, ignoreCase = true) -> FileSearchResult.MatchType.EXACT_NAME
                    fileInfo.name.startsWith(query, ignoreCase = true) -> FileSearchResult.MatchType.PREFIX_NAME
                    fileInfo.name.contains(query, ignoreCase = true) -> FileSearchResult.MatchType.CONTAINS_NAME
                    else -> FileSearchResult.MatchType.PATH_MATCH
                }
                
                FileSearchResult(contextItem, weight, matchType)
            }.sortedByDescending { it.weight }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun searchFilesFlow(query: String, maxResults: Int) = flow {
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
            }.take(maxResults)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun validateUrl(url: String): Boolean {
        return try {
            val urlPattern = Regex("^(https?|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")
            url.matches(urlPattern)
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getWebInfo(url: String): WebContextItem? {
        return if (validateUrl(url)) {
            WebContextItem(
                url = url,
                title = null, // 暂时不获取网页标题
                description = null
            )
        } else {
            null
        }
    }
    
    override suspend fun getFileInfo(relativePath: String): FileContextItem? {
        return try {
            val content = fileIndexService?.getFileContent(relativePath)
            if (content != null) {
                val fileName = relativePath.substringAfterLast('/')
                val absolutePath = projectService?.getProjectPath()?.let { projectPath -> 
                    "$projectPath/$relativePath" 
                } ?: relativePath
                
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

/**
 * 简化的上下文选择器弹出组件
 * 封装了完整的上下文选择流程，直接返回ContextReference
 */
@Composable
fun SimpleContextSelectorPopup(
    onDismiss: () -> Unit,
    onContextSelect: (ContextReference) -> Unit,
    searchService: ContextSearchService,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf<ContextSelectionState>(ContextSelectionState.SelectingType) }
    
    ContextSelectorPopup(
        visible = true,
        anchorPosition = IntOffset.Zero, // 简化版不需要精确位置
        state = state,
        searchService = searchService,
        onStateChange = { newState -> state = newState },
        onResult = { result ->
            when (result) {
                is ContextSelectionResult.FileSelected -> {
                    val contextRef = ContextReference.FileReference(
                        path = result.item.relativePath
                    )
                    onContextSelect(contextRef)
                }
                is ContextSelectionResult.WebSelected -> {
                    val contextRef = ContextReference.WebReference(
                        url = result.item.url
                    )
                    onContextSelect(contextRef)
                }
                is ContextSelectionResult.Cancelled -> {
                    onDismiss()
                }
            }
        },
        modifier = modifier
    )
}